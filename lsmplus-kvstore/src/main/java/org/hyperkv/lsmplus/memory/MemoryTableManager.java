package org.hyperkv.lsmplus.memory;

import org.hyperkv.lsmplus.api.model.IndexKey;
import org.hyperkv.lsmplus.api.model.IndexValue;
import org.hyperkv.lsmplus.api.model.RangeQueryOptions;
import org.hyperkv.lsmplus.api.model.RangeQueryResult;
import org.hyperkv.lsmplus.journal.JournalReplayPoint;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

import java.util.ArrayList;
import java.util.Collections;
import java.util.HashSet;
import java.util.List;
import java.util.Map;
import java.util.Set;
import java.util.concurrent.CopyOnWriteArrayList;
import java.util.concurrent.locks.ReentrantReadWriteLock;

public class MemoryTableManager {

    private static final Logger log = LoggerFactory.getLogger(MemoryTableManager.class);

    private final int tableMaxSize;
    private final List<MemoryTable> sealedTables;
    private volatile MemoryTable activeTable;
    private final ReentrantReadWriteLock lock;
    private volatile DumpCallback dumpCallback;

    public MemoryTableManager(int tableMaxSize) {
        if (tableMaxSize <= 0) {
            throw new IllegalArgumentException("tableMaxSize must be positive");
        }
        this.tableMaxSize = tableMaxSize;
        this.sealedTables = new CopyOnWriteArrayList<>();
        this.activeTable = new MemoryTable(tableMaxSize);
        this.lock = new ReentrantReadWriteLock();
        log.info("MemoryTableManager initialized with tableMaxSize={}", tableMaxSize);
    }

    public MemoryTableManager() {
        this(MemoryTable.DEFAULT_MAX_SIZE);
    }

    public void setDumpCallback(DumpCallback callback) {
        this.dumpCallback = callback;
        log.debug("Dump callback registered");
    }

    public void put(IndexKey key, IndexValue value, JournalReplayPoint replayPoint) {
        lock.writeLock().lock();
        try {
            ensureActiveTable();
            activeTable.put(key, value, replayPoint);
            checkAndSeal();
        } finally {
            lock.writeLock().unlock();
        }
    }

    public void delete(IndexKey key, JournalReplayPoint replayPoint) {
        lock.writeLock().lock();
        try {
            ensureActiveTable();
            activeTable.delete(key, replayPoint);
            checkAndSeal();
        } finally {
            lock.writeLock().unlock();
        }
    }

    public IndexValue get(IndexKey key) {
        lock.readLock().lock();
        try {
            IndexValue value = activeTable.get(key);
            if (value != null) {
                return value;
            }

            for (int i = sealedTables.size() - 1; i >= 0; i--) {
                MemoryTable table = sealedTables.get(i);
                value = table.get(key);
                if (value != null) {
                    return value;
                }
            }

            return null;
        } finally {
            lock.readLock().unlock();
        }
    }

    public List<Map.Entry<IndexKey, IndexValue>> rangeQuery(IndexKey start, IndexKey end) {
        lock.readLock().lock();
        try {
            List<Map.Entry<IndexKey, IndexValue>> result = new ArrayList<>();
            result.addAll(activeTable.rangeQuery(start, end));

            for (int i = sealedTables.size() - 1; i >= 0; i--) {
                MemoryTable table = sealedTables.get(i);
                result.addAll(table.rangeQuery(start, end));
            }

            return result;
        } finally {
            lock.readLock().unlock();
        }
    }

    public RangeQueryResult rangeQuery(RangeQueryOptions options) {
        if (options == null) {
            options = RangeQueryOptions.DEFAULT;
        }

        lock.readLock().lock();
        try {
            IndexKey effectiveStart = options.getEffectiveStart();
            IndexKey effectiveEnd = options.getEffectiveEnd();
            int limit = options.getLimit();

            Set<IndexKey> tombstonesFromMemory = new HashSet<>();
            List<Map.Entry<IndexKey, IndexValue>> mergedResults = new ArrayList<>();
            Set<IndexKey> seenKeys = new HashSet<>();

            if (activeTable != null) {
                RangeQueryResult activeResult = activeTable.rangeQuery(options);
                for (Map.Entry<IndexKey, IndexValue> entry : activeResult.getEntries()) {
                    IndexKey key = entry.getKey();
                    IndexValue value = entry.getValue();

                    if (value.isTombstone()) {
                        tombstonesFromMemory.add(key);
                    } else {
                        if (seenKeys.add(key)) {
                            mergedResults.add(entry);
                        }
                    }

                    if (limit > 0 && mergedResults.size() >= limit) {
                        IndexKey token = mergedResults.get(mergedResults.size() - 1).getKey();
                        return RangeQueryResult.of(mergedResults, mergedResults.size(), true, token);
                    }
                }
            }

            for (int i = sealedTables.size() - 1; i >= 0; i--) {
                MemoryTable table = sealedTables.get(i);
                RangeQueryOptions tableOptions = RangeQueryOptions.builder()
                        .start(effectiveStart)
                        .end(effectiveEnd)
                        .prefix(options.getPrefix())
                        .build();

                RangeQueryResult tableResult = table.rangeQuery(tableOptions);
                for (Map.Entry<IndexKey, IndexValue> entry : tableResult.getEntries()) {
                    IndexKey key = entry.getKey();
                    IndexValue value = entry.getValue();

                    if (seenKeys.contains(key)) {
                        continue;
                    }

                    if (value.isTombstone()) {
                        tombstonesFromMemory.add(key);
                    } else {
                        if (seenKeys.add(key)) {
                            mergedResults.add(entry);
                        }
                    }

                    if (limit > 0 && mergedResults.size() >= limit) {
                        IndexKey token = mergedResults.get(mergedResults.size() - 1).getKey();
                        return RangeQueryResult.of(mergedResults, mergedResults.size(), true, token);
                    }
                }
            }

            return RangeQueryResult.of(mergedResults);
        } finally {
            lock.readLock().unlock();
        }
    }

    public Set<IndexKey> getTombstonesInRange(RangeQueryOptions options) {
        if (options == null) {
            options = RangeQueryOptions.DEFAULT;
        }

        lock.readLock().lock();
        try {
            Set<IndexKey> tombstones = new HashSet<>();
            IndexKey effectiveStart = options.getEffectiveStart();
            IndexKey effectiveEnd = options.getEffectiveEnd();

            if (activeTable != null) {
                List<Map.Entry<IndexKey, IndexValue>> entries = activeTable.rangeQuery(effectiveStart, effectiveEnd);
                for (Map.Entry<IndexKey, IndexValue> entry : entries) {
                    if (entry.getValue().isTombstone()) {
                        if (!options.hasPrefix() || options.matchesPrefix(entry.getKey())) {
                            tombstones.add(entry.getKey());
                        }
                    }
                }
            }

            for (MemoryTable table : sealedTables) {
                List<Map.Entry<IndexKey, IndexValue>> entries = table.rangeQuery(effectiveStart, effectiveEnd);
                for (Map.Entry<IndexKey, IndexValue> entry : entries) {
                    if (entry.getValue().isTombstone()) {
                        if (!options.hasPrefix() || options.matchesPrefix(entry.getKey())) {
                            tombstones.add(entry.getKey());
                        }
                    }
                }
            }

            return tombstones;
        } finally {
            lock.readLock().unlock();
        }
    }

    public void sealActiveTable() {
        lock.writeLock().lock();
        try {
            if (activeTable != null && !activeTable.isSealed()) {
                int entryCount = activeTable.getEntryCount();
                activeTable.seal();
                sealedTables.add(activeTable);
                log.info("Sealed active table with {} entries, total sealed tables: {}", 
                    entryCount, sealedTables.size());
                activeTable = new MemoryTable(tableMaxSize);
                notifyDumpCallback();
            } else {
                log.debug("sealActiveTable called but no active table to seal");
            }
        } finally {
            lock.writeLock().unlock();
        }
    }

    public void removeSealedTable(MemoryTable table) {
        lock.writeLock().lock();
        try {
            sealedTables.remove(table);
            log.debug("Removed sealed table, remaining sealed tables: {}", sealedTables.size());
        } finally {
            lock.writeLock().unlock();
        }
    }

    public void clearSealedTables() {
        lock.writeLock().lock();
        try {
            int count = sealedTables.size();
            sealedTables.removeIf(table -> table.getStatus() == MemoryTable.Status.CLEARED);
            log.info("Cleared {} sealed tables. remaining {}.", count - sealedTables.size(), sealedTables.size());
        } finally {
            lock.writeLock().unlock();
        }
    }

    public MemoryTable getActiveTable() {
        return activeTable;
    }

    public List<MemoryTable> getSealedTables() {
        return Collections.unmodifiableList(new ArrayList<>(sealedTables));
    }

    public List<MemoryTable> getAllTables() {
        List<MemoryTable> all = new ArrayList<>();
        all.addAll(sealedTables);
        if (activeTable != null) {
            all.add(activeTable);
        }
        return all;
    }

    public int getActiveTableSize() {
        return activeTable != null ? activeTable.getCurrentSize() : 0;
    }

    public int getSealedTableCount() {
        return sealedTables.size();
    }

    public int getTotalEntryCount() {
        int count = 0;
        if (activeTable != null) {
            count += activeTable.getEntryCount();
        }
        for (MemoryTable table : sealedTables) {
            count += table.getEntryCount();
        }
        return count;
    }

    public int getTableMaxSize() {
        return tableMaxSize;
    }

    private void ensureActiveTable() {
        if (activeTable == null) {
            activeTable = new MemoryTable(tableMaxSize);
        }
    }

    private void checkAndSeal() {
        if (activeTable != null && activeTable.shouldSeal()) {
            int entryCount = activeTable.getEntryCount();
            activeTable.seal();
            sealedTables.add(activeTable);
            log.info("Auto-sealed active table with {} entries (size limit reached), total sealed tables: {}", 
                entryCount, sealedTables.size());
            activeTable = new MemoryTable(tableMaxSize);
            notifyDumpCallback();
        }
    }

    private void notifyDumpCallback() {
        if (dumpCallback != null) {
            log.debug("Notifying dump callback: sealedTableCount={}", sealedTables.size());
            dumpCallback.onTableSealed(sealedTables.size());
        }
    }

    @Override
    public String toString() {
        return "MemoryTableManager{sealed=" + sealedTables.size() +
               ", activeSize=" + getActiveTableSize() + "}";
    }
}
