package org.hyperkv.lsmplus.core;

import org.hyperkv.lsmplus.api.model.IndexKey;
import org.hyperkv.lsmplus.api.model.IndexValue;
import org.hyperkv.lsmplus.journal.JournalReplayPoint;
import org.hyperkv.lsmplus.memory.MemoryTable;

import java.util.AbstractMap;
import java.util.ArrayList;
import java.util.Comparator;
import java.util.Iterator;
import java.util.List;
import java.util.Map;
import java.util.PriorityQueue;

public final class SealedTablesMerger {

    private SealedTablesMerger() {
    }

    public static class MergeResult {
        private final List<Map.Entry<IndexKey, IndexValue>> entries;
        private final JournalReplayPoint replayPoint;
        private final int tableCount;

        public MergeResult(List<Map.Entry<IndexKey, IndexValue>> entries, 
                          JournalReplayPoint replayPoint, int tableCount) {
            this.entries = entries;
            this.replayPoint = replayPoint;
            this.tableCount = tableCount;
        }

        public List<Map.Entry<IndexKey, IndexValue>> getEntries() {
            return entries;
        }

        public JournalReplayPoint getReplayPoint() {
            return replayPoint;
        }

        public int getTableCount() {
            return tableCount;
        }

        public boolean isEmpty() {
            return entries == null || entries.isEmpty();
        }
    }

    public static MergeResult merge(List<MemoryTable> sealedTables) {
        if (sealedTables == null || sealedTables.isEmpty()) {
            return new MergeResult(new ArrayList<>(), null, 0);
        }

        List<Map.Entry<IndexKey, IndexValue>> merged = mergeSortSealedTables(sealedTables);
        JournalReplayPoint maxReplayPoint = findMaxReplayPoint(sealedTables);

        return new MergeResult(merged, maxReplayPoint, sealedTables.size());
    }

    private static List<Map.Entry<IndexKey, IndexValue>> mergeSortSealedTables(List<MemoryTable> sealedTables) {
        List<Map.Entry<IndexKey, IndexValue>> result = new ArrayList<>();
        
        if (sealedTables.size() == 1) {
            result.addAll(sealedTables.get(0).getData().entrySet());
            return result;
        }

        PriorityQueue<IteratorEntry> minHeap = new PriorityQueue<>(
            Comparator.comparing((IteratorEntry e) -> e.currentKey)
        );

        for (MemoryTable table : sealedTables) {
            Iterator<Map.Entry<IndexKey, IndexValue>> iterator = 
                table.getData().entrySet().iterator();
            if (iterator.hasNext()) {
                minHeap.offer(new IteratorEntry(iterator));
            }
        }

        IndexKey lastKey = null;
        IndexValue lastValue = null;

        while (!minHeap.isEmpty()) {
            IteratorEntry entry = minHeap.poll();
            IndexKey key = entry.currentKey;
            IndexValue value = entry.currentValue;

            if (lastKey == null || key.compareTo(lastKey) != 0) {
                if (lastKey != null && lastValue != null) {
                    result.add(new AbstractMap.SimpleEntry<>(lastKey, lastValue));
                }
                lastKey = key;
                lastValue = value;
            } else {
                lastValue = value;
            }

            if (entry.iterator.hasNext()) {
                entry.advance();
                minHeap.offer(entry);
            }
        }

        if (lastKey != null && lastValue != null) {
            result.add(new AbstractMap.SimpleEntry<>(lastKey, lastValue));
        }

        return result;
    }

    public static JournalReplayPoint findMaxReplayPoint(List<MemoryTable> sealedTables) {
        if (sealedTables == null || sealedTables.isEmpty()) {
            return null;
        }

        JournalReplayPoint maxReplayPoint = null;
        for (MemoryTable table : sealedTables) {
            JournalReplayPoint tableReplayPoint = table.getLastReplayPoint();
            if (tableReplayPoint != null) {
                if (maxReplayPoint == null || tableReplayPoint.compareTo(maxReplayPoint) > 0) {
                    maxReplayPoint = tableReplayPoint;
                }
            }
        }
        return maxReplayPoint;
    }

    private static class IteratorEntry {
        final Iterator<Map.Entry<IndexKey, IndexValue>> iterator;
        IndexKey currentKey;
        IndexValue currentValue;

        IteratorEntry(Iterator<Map.Entry<IndexKey, IndexValue>> iterator) {
            this.iterator = iterator;
            advance();
        }

        void advance() {
            if (iterator.hasNext()) {
                Map.Entry<IndexKey, IndexValue> entry = iterator.next();
                this.currentKey = entry.getKey();
                this.currentValue = entry.getValue();
            }
        }
    }
}
