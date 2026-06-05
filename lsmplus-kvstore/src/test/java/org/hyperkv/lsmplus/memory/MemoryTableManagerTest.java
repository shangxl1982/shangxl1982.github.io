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

class MemoryTableManagerTest {

    private MemoryTableManager manager;
    private JournalReplayPoint replayPoint;

    @BeforeEach
    void setUp() {
        manager = new MemoryTableManager(1024 * 1024);
        replayPoint = new JournalReplayPoint(1, 0, 4096);
    }

    @Test
    void testPutAndGet() {
        IndexKey key = IndexKey.orderedBytes("test-key".getBytes(StandardCharsets.UTF_8));
        IndexValue value = IndexValue.normal("test-value".getBytes(StandardCharsets.UTF_8));

        manager.put(key, value, replayPoint);

        IndexValue retrieved = manager.get(key);
        assertNotNull(retrieved);
        assertArrayEquals(value.getValueData(), retrieved.getValueData());
    }

    @Test
    void testDelete() {
        IndexKey key = IndexKey.orderedBytes("delete-key".getBytes(StandardCharsets.UTF_8));
        IndexValue value = IndexValue.normal("delete-value".getBytes(StandardCharsets.UTF_8));

        manager.put(key, value, replayPoint);
        assertNotNull(manager.get(key));

        manager.delete(key, replayPoint);

        IndexValue retrieved = manager.get(key);
        assertNotNull(retrieved);
        assertTrue(retrieved.isTombstone());
    }

    @Test
    void testSealActiveTable() {
        assertEquals(0, manager.getSealedTableCount());

        manager.sealActiveTable();

        assertEquals(1, manager.getSealedTableCount());
        assertFalse(manager.getActiveTable().isSealed());
    }

    @Test
    void testMultipleTables() {
        IndexKey key1 = IndexKey.orderedBytes("key1".getBytes(StandardCharsets.UTF_8));
        IndexValue value1 = IndexValue.normal("value1".getBytes(StandardCharsets.UTF_8));
        manager.put(key1, value1, replayPoint);

        manager.sealActiveTable();

        IndexKey key2 = IndexKey.orderedBytes("key2".getBytes(StandardCharsets.UTF_8));
        IndexValue value2 = IndexValue.normal("value2".getBytes(StandardCharsets.UTF_8));
        manager.put(key2, value2, replayPoint);

        assertNotNull(manager.get(key1));
        assertNotNull(manager.get(key2));
    }

    @Test
    void testRangeQuery() {
        for (int i = 0; i < 10; i++) {
            IndexKey key = IndexKey.orderedBytes(("key-" + String.format("%02d", i)).getBytes(StandardCharsets.UTF_8));
            IndexValue value = IndexValue.normal(("value-" + i).getBytes(StandardCharsets.UTF_8));
            manager.put(key, value, replayPoint);
        }

        IndexKey start = IndexKey.orderedBytes("key-03".getBytes(StandardCharsets.UTF_8));
        IndexKey end = IndexKey.orderedBytes("key-07".getBytes(StandardCharsets.UTF_8));

        List<Map.Entry<IndexKey, IndexValue>> results = manager.rangeQuery(start, end);

        assertEquals(4, results.size());
    }

    @Test
    void testAutoSeal() {
        MemoryTableManager smallManager = new MemoryTableManager(100);

        IndexKey key = IndexKey.orderedBytes("key".getBytes(StandardCharsets.UTF_8));
        IndexValue value = IndexValue.normal(new byte[200]);

        smallManager.put(key, value, replayPoint);

        assertEquals(1, smallManager.getSealedTableCount());
    }

    @Test
    void testGetAllTables() {
        manager.sealActiveTable();
        manager.sealActiveTable();

        List<MemoryTable> allTables = manager.getAllTables();

        assertEquals(3, allTables.size());
    }

    @Test
    void testGetActiveTableSize() {
        assertEquals(0, manager.getActiveTableSize());

        IndexKey key = IndexKey.orderedBytes("key".getBytes(StandardCharsets.UTF_8));
        IndexValue value = IndexValue.normal(new byte[100]);
        manager.put(key, value, replayPoint);

        assertTrue(manager.getActiveTableSize() > 0);
    }

    @Test
    void testRemoveSealedTable() {
        manager.sealActiveTable();
        assertEquals(1, manager.getSealedTableCount());

        MemoryTable sealed = manager.getSealedTables().get(0);
        manager.removeSealedTable(sealed);

        assertEquals(0, manager.getSealedTableCount());
    }

    @Test
    void testReadFromSealedTable() {
        IndexKey key = IndexKey.orderedBytes("sealed-key".getBytes(StandardCharsets.UTF_8));
        IndexValue value = IndexValue.normal("sealed-value".getBytes(StandardCharsets.UTF_8));
        manager.put(key, value, replayPoint);

        manager.sealActiveTable();

        IndexValue retrieved = manager.get(key);
        assertNotNull(retrieved);
        assertArrayEquals(value.getValueData(), retrieved.getValueData());
    }

    @Test
    void testOverwriteInNewTable() {
        IndexKey key = IndexKey.orderedBytes("overwrite-key".getBytes(StandardCharsets.UTF_8));
        IndexValue value1 = IndexValue.normal("value1".getBytes(StandardCharsets.UTF_8));
        manager.put(key, value1, replayPoint);

        manager.sealActiveTable();

        IndexValue value2 = IndexValue.normal("value2".getBytes(StandardCharsets.UTF_8));
        manager.put(key, value2, replayPoint);

        IndexValue retrieved = manager.get(key);
        assertArrayEquals(value2.getValueData(), retrieved.getValueData());
    }

    @Test
    void testTotalEntryCount() {
        assertEquals(0, manager.getTotalEntryCount());

        for (int i = 0; i < 5; i++) {
            IndexKey key = IndexKey.orderedBytes(("key-" + i).getBytes(StandardCharsets.UTF_8));
            IndexValue value = IndexValue.normal(("value-" + i).getBytes(StandardCharsets.UTF_8));
            manager.put(key, value, replayPoint);
        }

        assertEquals(5, manager.getTotalEntryCount());
    }

    @Test
    void testGetSealedTables() {
        manager.sealActiveTable();
        manager.sealActiveTable();

        List<MemoryTable> sealed = manager.getSealedTables();

        assertEquals(2, sealed.size());
        assertTrue(sealed.get(0).isSealed());
        assertTrue(sealed.get(1).isSealed());
    }
}