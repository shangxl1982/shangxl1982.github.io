package org.hyperkv.lsmplus.memory;

import org.hyperkv.lsmplus.api.model.IndexKey;
import org.hyperkv.lsmplus.api.model.IndexValue;
import org.hyperkv.lsmplus.journal.JournalReplayPoint;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;

import java.nio.charset.StandardCharsets;
import java.util.List;
import java.util.Map;

import static org.junit.jupiter.api.Assertions.*;

class TombstoneSupportTest {

    private MemoryTable table;
    private MemoryTableManager manager;
    private JournalReplayPoint replayPoint;

    @BeforeEach
    void setUp() {
        table = new MemoryTable(1024 * 1024);
        manager = new MemoryTableManager(1024 * 1024);
        replayPoint = new JournalReplayPoint(1, 0, 4096);
    }

    @Test
    void testDeleteCreatesTombstone() {
        IndexKey key = IndexKey.orderedBytes("tombstone-key".getBytes(StandardCharsets.UTF_8));
        IndexValue value = IndexValue.normal("value".getBytes(StandardCharsets.UTF_8));

        table.put(key, value, replayPoint);
        table.delete(key, replayPoint);

        IndexValue retrieved = table.get(key);
        assertNotNull(retrieved);
        assertTrue(retrieved.isTombstone());
    }

    @Test
    void testGetReturnsTombstone() {
        IndexKey key = IndexKey.orderedBytes("deleted-key".getBytes(StandardCharsets.UTF_8));
        IndexValue value = IndexValue.normal("value".getBytes(StandardCharsets.UTF_8));

        manager.put(key, value, replayPoint);
        manager.delete(key, replayPoint);

        IndexValue retrieved = manager.get(key);
        assertNotNull(retrieved);
        assertTrue(retrieved.isTombstone());
    }

    @Test
    void testRangeQueryIncludesTombstones() {
        IndexKey key1 = IndexKey.orderedBytes("key-1".getBytes(StandardCharsets.UTF_8));
        IndexKey key2 = IndexKey.orderedBytes("key-2".getBytes(StandardCharsets.UTF_8));
        IndexKey key3 = IndexKey.orderedBytes("key-3".getBytes(StandardCharsets.UTF_8));

        table.put(key1, IndexValue.normal("value-1".getBytes(StandardCharsets.UTF_8)), replayPoint);
        table.delete(key2, replayPoint);
        table.put(key3, IndexValue.normal("value-3".getBytes(StandardCharsets.UTF_8)), replayPoint);

        List<Map.Entry<IndexKey, IndexValue>> results = table.rangeQuery(null, null);

        assertEquals(3, results.size());
        assertTrue(results.get(1).getValue().isTombstone());
    }

    @Test
    void testTombstonePreservedInSeal() {
        IndexKey key = IndexKey.orderedBytes("seal-tombstone".getBytes(StandardCharsets.UTF_8));
        IndexValue value = IndexValue.normal("value".getBytes(StandardCharsets.UTF_8));

        manager.put(key, value, replayPoint);
        manager.delete(key, replayPoint);
        manager.sealActiveTable();

        IndexValue retrieved = manager.get(key);
        assertNotNull(retrieved);
        assertTrue(retrieved.isTombstone());
    }

    @Test
    void testMultipleDeletes() {
        IndexKey key = IndexKey.orderedBytes("multi-delete".getBytes(StandardCharsets.UTF_8));
        IndexValue value = IndexValue.normal("value".getBytes(StandardCharsets.UTF_8));

        manager.put(key, value, replayPoint);
        manager.delete(key, replayPoint);
        manager.delete(key, replayPoint);

        IndexValue retrieved = manager.get(key);
        assertNotNull(retrieved);
        assertTrue(retrieved.isTombstone());
        assertEquals(1, manager.getTotalEntryCount());
    }

    @Test
    void testTombstoneOverwritesValue() {
        IndexKey key = IndexKey.orderedBytes("overwrite".getBytes(StandardCharsets.UTF_8));
        IndexValue value = IndexValue.normal("original".getBytes(StandardCharsets.UTF_8));

        table.put(key, value, replayPoint);
        table.delete(key, replayPoint);

        IndexValue retrieved = table.get(key);
        assertTrue(retrieved.isTombstone());
        assertEquals(1, table.getEntryCount());
    }

    @Test
    void testPutOverwritesTombstone() {
        IndexKey key = IndexKey.orderedBytes("restore".getBytes(StandardCharsets.UTF_8));
        IndexValue value1 = IndexValue.normal("original".getBytes(StandardCharsets.UTF_8));
        IndexValue value2 = IndexValue.normal("restored".getBytes(StandardCharsets.UTF_8));

        table.put(key, value1, replayPoint);
        table.delete(key, replayPoint);
        table.put(key, value2, replayPoint);

        IndexValue retrieved = table.get(key);
        assertFalse(retrieved.isTombstone());
        assertArrayEquals(value2.getValueData(), retrieved.getValueData());
    }

    @Test
    void testTombstoneInSealedTable() {
        IndexKey key = IndexKey.orderedBytes("sealed-tombstone".getBytes(StandardCharsets.UTF_8));

        manager.delete(key, replayPoint);
        manager.sealActiveTable();

        IndexValue retrieved = manager.get(key);
        assertNotNull(retrieved);
        assertTrue(retrieved.isTombstone());
    }

    @Test
    void testTombstoneFromSealedTableHiddenByNewValue() {
        IndexKey key = IndexKey.orderedBytes("hidden-tombstone".getBytes(StandardCharsets.UTF_8));

        manager.delete(key, replayPoint);
        manager.sealActiveTable();

        IndexValue newValue = IndexValue.normal("new-value".getBytes(StandardCharsets.UTF_8));
        manager.put(key, newValue, replayPoint);

        IndexValue retrieved = manager.get(key);
        assertFalse(retrieved.isTombstone());
        assertArrayEquals(newValue.getValueData(), retrieved.getValueData());
    }
}