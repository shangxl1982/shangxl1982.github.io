package org.hyperkv.lsmplus.bplustree;

import org.hyperkv.lsmplus.api.model.IndexKey;
import org.hyperkv.lsmplus.api.model.IndexValue;
import org.hyperkv.lsmplus.api.model.RangeQueryOptions;
import org.hyperkv.lsmplus.api.model.RangeQueryResult;
import org.hyperkv.lsmplus.bplustree.page.IndexPair;
import org.hyperkv.lsmplus.bplustree.page.Page;
import org.hyperkv.lsmplus.monitoring.MetricsRegistry;
import org.hyperkv.lsmplus.monitoring.PerformanceCounter;
import org.hyperkv.lsmplus.storage.SegmentLocation;

import java.util.AbstractMap;
import java.util.ArrayList;
import java.util.Collections;
import java.util.Iterator;
import java.util.List;
import java.util.Map;
import java.util.NoSuchElementException;
import java.util.Stack;
import java.util.concurrent.atomic.AtomicReference;

public class ReaderTree {

    private final PageManager pageManager;
    private final AtomicReference<Page> readerRoot = new AtomicReference<>();
    
    private final PerformanceCounter searchCounter;
    private final PerformanceCounter rangeQueryCounter;

    public ReaderTree(PageManager pageManager) {
        if (pageManager == null) {
            throw new IllegalArgumentException("pageManager must not be null");
        }
        this.pageManager = pageManager;
        this.searchCounter = MetricsRegistry.getCounter("tree_search", "B+Tree search latency");
        this.rangeQueryCounter = MetricsRegistry.getCounter("tree_range_query", "B+Tree range query latency");
    }

    public IndexValue search(IndexKey key) {
        if (key == null) {
            throw new IllegalArgumentException("key must not be null");
        }

        if (readerRoot.get() == null) {
            return null;
        }

        long startTime = System.nanoTime();
        boolean success = false;
        try {
            IndexValue result = searchInTree(key, readerRoot.get());
            success = true;
            return result;
        } finally {
            long latencyMicros = (System.nanoTime() - startTime) / 1000;
            if (success) {
                searchCounter.recordSuccess(latencyMicros);
            } else {
                searchCounter.recordError();
            }
        }
    }

    private IndexValue searchInTree(IndexKey key, Page page) {
        if (page == null) {
            return null;
        }

        if (page.isLeaf()) {
            return page.get(key);
        } else {
            SegmentLocation childLocation = page.getChildLocation(key);
            if (childLocation == null) {
                return null;
            }
            var childPage = pageManager.getPage(childLocation);
            return searchInTree(key, childPage);
        }
    }

    public List<Map.Entry<IndexKey, IndexValue>> rangeQuery(IndexKey start, IndexKey end) {
        long startTime = System.nanoTime();
        boolean success = false;
        try {
            List<Map.Entry<IndexKey, IndexValue>> results = new ArrayList<>();

            if (readerRoot.get() == null) {
                return results;
            }

            rangeQueryInTree(start, end, readerRoot.get(), results);
            success = true;
            return results;
        } finally {
            long latencyMicros = (System.nanoTime() - startTime) / 1000;
            if (success) {
                rangeQueryCounter.recordSuccess(latencyMicros);
            } else {
                rangeQueryCounter.recordError();
            }
        }
    }

    public RangeQueryResult rangeQuery(RangeQueryOptions options) {
        if (options == null) {
            options = RangeQueryOptions.DEFAULT;
        }

        long startTime = System.nanoTime();
        boolean success = false;
        try {
            if (readerRoot.get() == null) {
                return RangeQueryResult.empty();
            }

            IndexKey effectiveStart = options.getEffectiveStart();
            IndexKey effectiveEnd = options.getEffectiveEnd();
            int limit = options.getLimit();

            List<Map.Entry<IndexKey, IndexValue>> results = new ArrayList<>();
            RangeQueryContext context = new RangeQueryContext(limit, options);

            rangeQueryInTreeOptimized(effectiveStart, effectiveEnd, readerRoot.get(), results, context);

            success = true;

            if (limit > 0 && results.size() > limit) {
                List<Map.Entry<IndexKey, IndexValue>> limited = new ArrayList<>(results.subList(0, limit));
                IndexKey token = limited.get(limited.size() - 1).getKey();
                return RangeQueryResult.of(limited, results.size(), true, token);
            }

            return RangeQueryResult.of(results);
        } finally {
            long latencyMicros = (System.nanoTime() - startTime) / 1000;
            if (success) {
                rangeQueryCounter.recordSuccess(latencyMicros);
            } else {
                rangeQueryCounter.recordError();
            }
        }
    }

    private static class RangeQueryContext {
        final int limit;
        final RangeQueryOptions options;
        final boolean startInclusive;
        boolean stopped = false;

        RangeQueryContext(int limit, RangeQueryOptions options) {
            this.limit = limit;
            this.options = options;
            this.startInclusive = options != null ? options.isEffectiveStartInclusive() : true;
        }

        boolean shouldStop() {
            return stopped || (limit > 0 && limitReached());
        }

        boolean limitReached() {
            return limit > 0;
        }

        void stop() {
            this.stopped = true;
        }
    }

    private void rangeQueryInTreeOptimized(IndexKey start, IndexKey end, Page page,
                                           List<Map.Entry<IndexKey, IndexValue>> results,
                                           RangeQueryContext context) {
        if (page == null || context.shouldStop()) {
            return;
        }

        if (page.isLeaf()) {
            List<IndexPair> entries = page.rangeQuery(start, end, context.startInclusive);
            for (IndexPair pair : entries) {
                if (context.shouldStop()) {
                    break;
                }
                if (pair instanceof IndexPair.ValueEntry ve) {
                    if (!optionsMatchPrefix(ve.key(), context.options)) {
                        continue;
                    }
                    results.add(new AbstractMap.SimpleEntry<>(ve.key(), ve.value()));
                }
            }
        } else {
            List<IndexPair> entries = page.getAllEntries();
            IndexKey prevKey = null;

            for (int i = 0; i < entries.size(); i++) {
                if (context.shouldStop()) {
                    break;
                }

                IndexPair pair = entries.get(i);
                if (pair instanceof IndexPair.LocationEntry le) {
                    IndexKey childMinKey = le.key();

                    if (end != null && childMinKey != null && childMinKey.compareTo(end) >= 0) {
                        break;
                    }

                    if (start != null && prevKey != null) {
                        if (prevKey.compareTo(start) < 0 && (childMinKey == null || childMinKey.compareTo(start) <= 0)) {
                            prevKey = childMinKey;
                            continue;
                        }
                    }

                    var childPage = pageManager.getPage(le.location());
                    rangeQueryInTreeOptimized(start, end, childPage, results, context);

                    prevKey = childMinKey;
                }
            }
        }
    }

    private boolean optionsMatchPrefix(IndexKey key, RangeQueryOptions options) {
        if (options == null || !options.hasPrefix()) {
            return true;
        }
        return options.matchesPrefix(key);
    }

    private void rangeQueryInTree(IndexKey start, IndexKey end, Page page,
                                 List<Map.Entry<IndexKey, IndexValue>> results) {
        if (page == null) {
            return;
        }

        if (page.isLeaf()) {
            List<IndexPair> entries = page.rangeQuery(start, end);
            for (IndexPair pair : entries) {
                if (pair instanceof IndexPair.ValueEntry ve) {
                    results.add(new AbstractMap.SimpleEntry<>(ve.key(), ve.value()));
                }
            }
        } else {
            List<IndexPair> entries = page.getAllEntries();
            for (IndexPair pair : entries) {
                if (pair instanceof IndexPair.LocationEntry le) {
                    var childPage = pageManager.getPage(le.location());
                    rangeQueryInTree(start, end, childPage, results);
                }
            }
        }
    }

    public Iterator<Map.Entry<IndexKey, IndexValue>> rangeIterator(RangeQueryOptions options) {
        if (options == null) {
            options = RangeQueryOptions.DEFAULT;
        }

        if (readerRoot.get() == null) {
            return Collections.emptyIterator();
        }

        return new BPlusTreeIterator(readerRoot.get(), options, pageManager);
    }

    public boolean isEmpty() {
        return readerRoot.get() == null;
    }

    public void setReaderRoot(Page page) {
        readerRoot.set(page);
    }

    public Page getReaderRoot() {
        return readerRoot.get();
    }

    public String debugListAllEntries() {
        StringBuilder sb = new StringBuilder();
        sb.append("=== ReaderTree Debug: All Entries ===\n");
        
        if (isEmpty()) {
            sb.append("Tree is empty\n");
            return sb.toString();
        }
        
        Iterator<Map.Entry<IndexKey, IndexValue>> it = rangeIterator(RangeQueryOptions.DEFAULT);
        int count = 0;
        
        while (it.hasNext()) {
            Map.Entry<IndexKey, IndexValue> entry = it.next();
            sb.append(String.format("[%d] key=%s, value=%s%n", count, entry.getKey(), entry.getValue()));
            count++;
        }
        
        sb.append(String.format("Total entries: %d%n", count));
        sb.append("=====================================\n");
        
        return sb.toString();
    }

    private static class BPlusTreeIterator implements Iterator<Map.Entry<IndexKey, IndexValue>> {
        private final RangeQueryOptions options;
        private final PageManager pageManager;
        private final Stack<IteratorState> stack;
        private Map.Entry<IndexKey, IndexValue> next;
        private boolean initialized = false;

        BPlusTreeIterator(Page root, RangeQueryOptions options, PageManager pageManager) {
            this.options = options;
            this.pageManager = pageManager;
            this.stack = new Stack<>();
            
            IndexKey start = options.getEffectiveStart();
            IndexKey end = options.getEffectiveEnd();
            boolean startInclusive = options.isEffectiveStartInclusive();
            
            pushPage(root, start, end, startInclusive, true);
        }

        private void pushPage(Page page, IndexKey start, IndexKey end, boolean startInclusive, boolean isRoot) {
            if (page == null) {
                return;
            }

            if (page.isLeaf()) {
                List<IndexPair> entries = page.rangeQuery(start, end, startInclusive);
                Iterator<IndexPair> it = entries.iterator();
                stack.push(new IteratorState(it, true));
            } else {
                List<IndexPair> entries = page.getAllEntries();
                stack.push(new IteratorState(entries.iterator(), false, start, end));
            }
        }

        private void advance() {
            next = null;
            
            while (!stack.isEmpty() && next == null) {
                IteratorState state = stack.peek();
                
                if (state.isLeaf) {
                    while (state.iterator.hasNext()) {
                        IndexPair pair = state.iterator.next();
                        if (pair instanceof IndexPair.ValueEntry ve) {
                            if (options.hasPrefix() && !options.matchesPrefix(ve.key())) {
                                continue;
                            }
                            next = new AbstractMap.SimpleEntry<>(ve.key(), ve.value());
                            return;
                        }
                    }
                    stack.pop();
                } else {
                    if (state.iterator.hasNext()) {
                        IndexPair pair = state.iterator.next();
                        if (pair instanceof IndexPair.LocationEntry le) {
                            IndexKey childKey = le.key();
                            
                            if (state.end != null && childKey != null && childKey.compareTo(state.end) >= 0) {
                                stack.pop();
                                continue;
                            }
                            
                            Page childPage = pageManager.getPage(le.location());
                            pushPage(childPage, state.start, state.end, true, false);
                        }
                    } else {
                        stack.pop();
                    }
                }
            }
        }

        @Override
        public boolean hasNext() {
            if (!initialized) {
                advance();
                initialized = true;
            }
            return next != null;
        }

        @Override
        public Map.Entry<IndexKey, IndexValue> next() {
            if (!hasNext()) {
                throw new NoSuchElementException();
            }
            Map.Entry<IndexKey, IndexValue> result = next;
            advance();
            return result;
        }

        private static class IteratorState {
            final Iterator<IndexPair> iterator;
            final boolean isLeaf;
            final IndexKey start;
            final IndexKey end;

            IteratorState(Iterator<IndexPair> iterator, boolean isLeaf) {
                this.iterator = iterator;
                this.isLeaf = isLeaf;
                this.start = null;
                this.end = null;
            }

            IteratorState(Iterator<IndexPair> iterator, boolean isLeaf, IndexKey start, IndexKey end) {
                this.iterator = iterator;
                this.isLeaf = isLeaf;
                this.start = start;
                this.end = end;
            }
        }
    }
}
