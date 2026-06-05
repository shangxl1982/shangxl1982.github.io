package org.hyperkv.lsmplus.core.concurrency;

import org.hyperkv.lsmplus.api.model.IndexKey;
import org.hyperkv.lsmplus.api.model.IndexValue;
import org.hyperkv.lsmplus.api.model.RangeQueryOptions;
import org.hyperkv.lsmplus.api.model.RangeQueryResult;
import org.hyperkv.lsmplus.bplustree.BPlusTree;
import org.hyperkv.lsmplus.memory.MemoryTable;
import org.hyperkv.lsmplus.memory.MemoryTableManager;

import java.util.*;

public class Snapshot {

    private final MemoryTable activeTable;
    private final List<MemoryTable> sealedTables;
    private final BPlusTree bPlusTree;
    private final long version;

    public Snapshot(MemoryTable activeTable, List<MemoryTable> sealedTables, BPlusTree bPlusTree, long version) {
        this.activeTable = activeTable;
        this.sealedTables = sealedTables;
        this.bPlusTree = bPlusTree;
        this.version = version;
    }

    public static Snapshot create(MemoryTableManager memoryTableManager, BPlusTree bPlusTree) {
        MemoryTable active = memoryTableManager.getActiveTable();
        List<MemoryTable> sealed = memoryTableManager.getSealedTables();
        long version = bPlusTree.getVersion();
        return new Snapshot(active, sealed, bPlusTree, version);
    }

    public IndexValue get(IndexKey key) {
        if (activeTable != null) {
            IndexValue value = activeTable.get(key);
            if (value != null) {
                if (value.isTombstone()) {
                    return null;
                }
                return value;
            }
        }

        if (sealedTables != null) {
            for (MemoryTable table : sealedTables) {
                IndexValue value = table.get(key);
                if (value != null) {
                    if (value.isTombstone()) {
                        return null;
                    }
                    return value;
                }
            }
        }

        if (bPlusTree != null && !bPlusTree.isEmpty()) {
            IndexValue value = bPlusTree.search(key);
            if (value != null && !value.isTombstone()) {
                return value;
            }
        }

        return null;
    }

    public List<Map.Entry<IndexKey, IndexValue>> rangeQuery(IndexKey start, IndexKey end) {
        RangeQueryOptions options = RangeQueryOptions.builder()
                .start(start)
                .end(end)
                .build();
        return rangeQuery(options).getEntries();
    }

    public RangeQueryResult rangeQuery(RangeQueryOptions options) {
        if (options == null) {
            options = RangeQueryOptions.DEFAULT;
        }

        IndexKey effectiveStart = options.getEffectiveStart();
        IndexKey effectiveEnd = options.getEffectiveEnd();
        boolean startInclusive = options.isEffectiveStartInclusive();
        int limit = options.getLimit();

        RangeQueryOptions queryOptions = RangeQueryOptions.builder()
                .start(effectiveStart)
                .end(effectiveEnd)
                .prefix(options.getPrefix())
                .startInclusive(startInclusive)
                .build();

        List<Map.Entry<IndexKey, IndexValue>> results = new ArrayList<>();
        Set<IndexKey> tombstones = new HashSet<>();
        
        int effectiveLimit = limit > 0 ? limit : Integer.MAX_VALUE;
        
        nWayMergeAllSources(queryOptions, effectiveLimit, results, tombstones);

        if (limit > 0 && results.size() >= limit) {
            IndexKey token = results.get(results.size() - 1).getKey();
            return RangeQueryResult.of(results, results.size(), true, token);
        }

        return RangeQueryResult.of(results);
    }

    private void nWayMergeAllSources(RangeQueryOptions options, int limit,
                                     List<Map.Entry<IndexKey, IndexValue>> results,
                                     Set<IndexKey> tombstones) {
        List<PeekableIterator> iterators = new ArrayList<>();
        
        int priority = 0;
        
        if (activeTable != null) {
            Iterator<Map.Entry<IndexKey, IndexValue>> it = activeTable.rangeIterator(options);
            PeekableIterator peekable = new PeekableIterator(it, priority++);
            if (peekable.hasNext()) {
                iterators.add(peekable);
            }
        }

        if (sealedTables != null) {
            for (int i = sealedTables.size() - 1; i >= 0; i--) {
                MemoryTable table = sealedTables.get(i);
                Iterator<Map.Entry<IndexKey, IndexValue>> it = table.rangeIterator(options);
                PeekableIterator peekable = new PeekableIterator(it, priority++);
                if (peekable.hasNext()) {
                    iterators.add(peekable);
                }
            }
        }

        if (bPlusTree != null && !bPlusTree.isEmpty()) {
            Iterator<Map.Entry<IndexKey, IndexValue>> it = bPlusTree.rangeIterator(options);
            PeekableIterator peekable = new PeekableIterator(it, priority);
            if (peekable.hasNext()) {
                iterators.add(peekable);
            }
        }

        if (iterators.isEmpty()) {
            return;
        }

        PriorityQueue<PeekableIterator> pq = new PriorityQueue<>(iterators);
        Set<IndexKey> seenKeys = new HashSet<>();

        while (!pq.isEmpty() && results.size() < limit) {
            PeekableIterator minIterator = pq.poll();
            Map.Entry<IndexKey, IndexValue> entry = minIterator.next();
            IndexKey key = entry.getKey();
            IndexValue value = entry.getValue();

            if (seenKeys.add(key)) {
                if (value.isTombstone()) {
                    tombstones.add(key);
                } else {
                    if (options.hasPrefix() && !options.matchesPrefix(key)) {
                        // skip - prefix filter
                    } else {
                        results.add(entry);
                    }
                }
            }

            if (minIterator.hasNext()) {
                pq.add(minIterator);
            }
        }
    }

    private static class PeekableIterator implements Comparable<PeekableIterator> {
        private final Iterator<Map.Entry<IndexKey, IndexValue>> iterator;
        private Map.Entry<IndexKey, IndexValue> current;
        private final int priority;
        private boolean hasCurrent = false;

        PeekableIterator(Iterator<Map.Entry<IndexKey, IndexValue>> iterator, int priority) {
            this.iterator = iterator;
            this.priority = priority;
            advance();
        }

        private void advance() {
            if (iterator.hasNext()) {
                current = iterator.next();
                hasCurrent = true;
            } else {
                current = null;
                hasCurrent = false;
            }
        }

        boolean hasNext() {
            return hasCurrent;
        }

        Map.Entry<IndexKey, IndexValue> next() {
            if (!hasCurrent) {
                throw new NoSuchElementException();
            }
            Map.Entry<IndexKey, IndexValue> result = current;
            advance();
            return result;
        }

        Map.Entry<IndexKey, IndexValue> peek() {
            if (!hasCurrent) {
                throw new NoSuchElementException();
            }
            return current;
        }

        @Override
        public int compareTo(PeekableIterator other) {
            int cmp = this.current.getKey().compareTo(other.current.getKey());
            if (cmp != 0) {
                return cmp;
            }
            return Integer.compare(this.priority, other.priority);
        }
    }

    public long getVersion() {
        return version;
    }

    public MemoryTable getActiveTable() {
        return activeTable;
    }

    public List<MemoryTable> getSealedTables() {
        return sealedTables;
    }

    public BPlusTree getBPlusTree() {
        return bPlusTree;
    }
}
