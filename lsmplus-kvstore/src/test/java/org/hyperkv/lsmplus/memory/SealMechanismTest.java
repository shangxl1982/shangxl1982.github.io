package org.hyperkv.lsmplus.memory;

import org.hyperkv.lsmplus.api.model.IndexKey;
import org.hyperkv.lsmplus.api.model.IndexValue;
import org.hyperkv.lsmplus.journal.JournalReplayPoint;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;

import java.nio.charset.StandardCharsets;

import static org.junit.jupiter.api.Assertions.*;

class SealMechanismTest {

    private MemoryTableManager manager;
    private JournalReplayPoint replayPoint;

    @BeforeEach
    void setUp() {
        manager = new MemoryTableManager(1024 * 1024);
        replayPoint = new JournalReplayPoint(1, 0, 4096);
    }

    @Test
    void testShouldSealSizeThreshold() {
        MemoryTable smallTable = new MemoryTable(100);

        assertFalse(smallTable.shouldSeal());

        IndexKey key = IndexKey.orderedBytes("key".getBytes(StandardCharsets.UTF_8));
        IndexValue value = IndexValue.normal(new byte[200]);
        smallTable.put(key, value, replayPoint);

        assertTrue(smallTable.shouldSeal());
    }

    @Test
    void testSealActiveTable() {
        assertEquals(0, manager.getSealedTableCount());

        manager.sealActiveTable();

        assertEquals(1, manager.getSealedTableCount());
    }

    @Test
    void testNewActiveTableCreated() {
        MemoryTable original = manager.getActiveTable();

        manager.sealActiveTable();

        MemoryTable newActive = manager.getActiveTable();
        assertNotSame(original, newActive);
        assertFalse(newActive.isSealed());
    }

    @Test
    void testSealedTableReadOnly() {
        IndexKey key = IndexKey.orderedBytes("key".getBytes(StandardCharsets.UTF_8));
        IndexValue value = IndexValue.normal("value".getBytes(StandardCharsets.UTF_8));
        manager.put(key, value, replayPoint);

        manager.sealActiveTable();

        MemoryTable sealed = manager.getSealedTables().get(0);
        assertTrue(sealed.isSealed());

        assertThrows(IllegalStateException.class, () ->
                sealed.put(IndexKey.orderedBytes("new-key".getBytes(StandardCharsets.UTF_8)),
                        IndexValue.normal("new-value".getBytes(StandardCharsets.UTF_8)), replayPoint));
    }

    @Test
    void testMultipleSeals() {
        for (int i = 0; i < 5; i++) {
            manager.sealActiveTable();
        }

        assertEquals(5, manager.getSealedTableCount());
    }

    @Test
    void testAutoSealOnThreshold() {
        MemoryTableManager smallManager = new MemoryTableManager(100);

        IndexKey key = IndexKey.orderedBytes("key".getBytes(StandardCharsets.UTF_8));
        IndexValue value = IndexValue.normal(new byte[200]);

        smallManager.put(key, value, replayPoint);

        assertEquals(1, smallManager.getSealedTableCount());
        assertEquals(0, smallManager.getActiveTableSize());
    }

    @Test
    void testSealPreservesData() {
        IndexKey key = IndexKey.orderedBytes("preserve-key".getBytes(StandardCharsets.UTF_8));
        IndexValue value = IndexValue.normal("preserve-value".getBytes(StandardCharsets.UTF_8));
        manager.put(key, value, replayPoint);

        manager.sealActiveTable();

        IndexValue retrieved = manager.get(key);
        assertNotNull(retrieved);
        assertArrayEquals(value.getValueData(), retrieved.getValueData());
    }

    @Test
    void testSealTwice() {
        manager.sealActiveTable();
        int count = manager.getSealedTableCount();

        MemoryTable active = manager.getActiveTable();
        manager.sealActiveTable();

        assertEquals(count + 1, manager.getSealedTableCount());
    }

    @Test
    void testSealEmptyTable() {
        manager.sealActiveTable();

        assertEquals(1, manager.getSealedTableCount());
        assertEquals(0, manager.getSealedTables().get(0).getEntryCount());
    }
}