package org.hyperkv.lsmplus.memory;

import org.hyperkv.lsmplus.api.model.IndexKey;
import org.hyperkv.lsmplus.api.model.IndexValue;
import org.hyperkv.lsmplus.api.model.RangeQueryOptions;
import org.hyperkv.lsmplus.api.model.RangeQueryResult;
import org.hyperkv.lsmplus.journal.JournalReplayPoint;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

import java.util.ArrayList;
import java.util.Iterator;
import java.util.List;
import java.util.Map;
import java.util.NavigableMap;
import java.util.concurrent.ConcurrentSkipListMap;

public class MemoryTable {

    private static final Logger log = LoggerFactory.getLogger(MemoryTable.class);

    public static final int DEFAULT_MAX_SIZE = 64 * 1024 * 1024;

    private final int maxSize;
    private int currentSize;
    private final ConcurrentSkipListMap<IndexKey, IndexValue> data;
    private volatile boolean sealed;
    private JournalReplayPoint firstReplayPoint;
    private JournalReplayPoint lastReplayPoint;

    public Status getStatus() {
        return status;
    }

    public enum Status {
        DUMPING,
        SEALED,
        CLEARED,
        OPEN
    }

    private volatile Status status = Status.OPEN;

    public MemoryTable(int maxSize) {
        if (maxSize <= 0) {
            throw new IllegalArgumentException("maxSize must be positive");
        }
        this.maxSize = maxSize;
        this.currentSize = 0;
        this.data = new ConcurrentSkipListMap<>();
        this.sealed = false;
        this.firstReplayPoint = null;
        this.lastReplayPoint = null;
    }

    public MemoryTable() {
        this(DEFAULT_MAX_SIZE);
    }

    public void setForDump() {
        status = Status.DUMPING;
    }

    public void setForClear() {
        status = Status.CLEARED;
    }

    public void resetFromDumping() {
        if (status == Status.DUMPING) {
            status = Status.SEALED;
        }
    }

    public synchronized void put(IndexKey key, IndexValue value, JournalReplayPoint replayPoint) {
        checkNotSealed();
        if (key == null) {
            throw new IllegalArgumentException("key must not be null");
        }
        if (value == null) {
            throw new IllegalArgumentException("value must not be null");
        }

        IndexValue oldValue = data.put(key, value);
        updateSize(key, oldValue, value);
        updateReplayPoints(replayPoint);
    }

    public synchronized void delete(IndexKey key, JournalReplayPoint replayPoint) {
        checkNotSealed();
        if (key == null) {
            throw new IllegalArgumentException("key must not be null");
        }

        IndexValue tombstone = IndexValue.tombstone();
        IndexValue oldValue = data.put(key, tombstone);
        updateSize(key, oldValue, tombstone);
        updateReplayPoints(replayPoint);
    }

    public IndexValue get(IndexKey key) {
        if (key == null) {
            throw new IllegalArgumentException("key must not be null");
        }
        return data.get(key);
    }

    public List<Map.Entry<IndexKey, IndexValue>> rangeQuery(IndexKey start, IndexKey end) {
        NavigableMap<IndexKey, IndexValue> subMap;
        if (start == null && end == null) {
            subMap = data;
        } else if (start == null) {
            subMap = data.headMap(end, false);
        } else if (end == null) {
            subMap = data.tailMap(start, true);
        } else {
            subMap = data.subMap(start, true, end, false);
        }

        return new ArrayList<>(subMap.entrySet());
    }

    public RangeQueryResult rangeQuery(RangeQueryOptions options) {
        if (options == null) {
            options = RangeQueryOptions.DEFAULT;
        }

        IndexKey effectiveStart = options.getEffectiveStart();
        IndexKey effectiveEnd = options.getEffectiveEnd();
        boolean startInclusive = options.isEffectiveStartInclusive();
        int limit = options.getLimit();

        NavigableMap<IndexKey, IndexValue> subMap;
        if (effectiveStart == null && effectiveEnd == null) {
            subMap = data;
        } else if (effectiveStart == null) {
            subMap = data.headMap(effectiveEnd, false);
        } else if (effectiveEnd == null) {
            subMap = data.tailMap(effectiveStart, startInclusive);
        } else {
            subMap = data.subMap(effectiveStart, startInclusive, effectiveEnd, false);
        }

        List<Map.Entry<IndexKey, IndexValue>> results = new ArrayList<>();
        for (Map.Entry<IndexKey, IndexValue> entry : subMap.entrySet()) {
            if (options.hasPrefix() && !options.matchesPrefix(entry.getKey())) {
                continue;
            }
            results.add(entry);
            if (limit > 0 && results.size() >= limit) {
                IndexKey token = results.get(results.size() - 1).getKey();
                return RangeQueryResult.of(results, subMap.size(), true, token);
            }
        }

        return RangeQueryResult.of(results);
    }

    public Map.Entry<IndexKey, IndexValue> getFirstEntry() {
        return data.firstEntry();
    }

    public Map.Entry<IndexKey, IndexValue> getLastEntry() {
        return data.lastEntry();
    }

    public NavigableMap<IndexKey, IndexValue> getRange(IndexKey start, IndexKey end) {
        if (start == null && end == null) {
            return data;
        } else if (start == null) {
            return data.headMap(end, false);
        } else if (end == null) {
            return data.tailMap(start, true);
        } else {
            return data.subMap(start, true, end, false);
        }
    }

    public Iterator<Map.Entry<IndexKey, IndexValue>> rangeIterator(RangeQueryOptions options) {
        if (options == null) {
            options = RangeQueryOptions.DEFAULT;
        }

        IndexKey effectiveStart = options.getEffectiveStart();
        IndexKey effectiveEnd = options.getEffectiveEnd();
        boolean startInclusive = options.isEffectiveStartInclusive();

        NavigableMap<IndexKey, IndexValue> subMap;
        if (effectiveStart == null && effectiveEnd == null) {
            subMap = data;
        } else if (effectiveStart == null) {
            subMap = data.headMap(effectiveEnd, false);
        } else if (effectiveEnd == null) {
            subMap = data.tailMap(effectiveStart, startInclusive);
        } else {
            subMap = data.subMap(effectiveStart, startInclusive, effectiveEnd, false);
        }

        return subMap.entrySet().iterator();
    }

    public boolean shouldSeal() {
        return currentSize >= maxSize;
    }

    public synchronized void seal() {
        if (!sealed) {
            sealed = true;
            status = Status.SEALED;
            log.debug("Sealed MemoryTable with {} entries, size={}", data.size(), currentSize);
        }
    }

    public boolean isSealed() {
        return sealed;
    }

    public int getCurrentSize() {
        return currentSize;
    }

    public int getMaxSize() {
        return maxSize;
    }

    public int getEntryCount() {
        return data.size();
    }

    public JournalReplayPoint getFirstReplayPoint() {
        return firstReplayPoint;
    }

    public JournalReplayPoint getLastReplayPoint() {
        return lastReplayPoint;
    }

    public NavigableMap<IndexKey, IndexValue> getData() {
        return data;
    }

    private void checkNotSealed() {
        if (sealed) {
            throw new IllegalStateException("MemoryTable is sealed and cannot be modified");
        }
    }

    private void updateSize(IndexKey key, IndexValue oldValue, IndexValue newValue) {
        int keySize = estimateKeySize(key);
        int newValueSize = estimateValueSize(newValue);

        if (oldValue == null) {
            currentSize += keySize + newValueSize;
        } else {
            int oldValueSize = estimateValueSize(oldValue);
            currentSize += newValueSize - oldValueSize;
        }
    }

    private void updateReplayPoints(JournalReplayPoint replayPoint) {
        if (replayPoint != null) {
            if (firstReplayPoint == null) {
                firstReplayPoint = replayPoint;
            }
            lastReplayPoint = replayPoint;
        }
    }

    private int estimateKeySize(IndexKey key) {
        return key.getKeyData().length + 16;
    }

    private int estimateValueSize(IndexValue value) {
        return value.getValueData().length + 16;
    }

    @Override
    public String toString() {
        return "MemoryTable{entries=" + data.size() +
               ", size=" + currentSize + "/" + maxSize +
               ", sealed=" + sealed + '}';
    }
}