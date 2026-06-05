package org.hyperkv.lsmplus.bplustree;

import org.hyperkv.lsmplus.api.model.IndexKey;
import org.hyperkv.lsmplus.api.model.IndexValue;
import org.hyperkv.lsmplus.bplustree.page.IndexPair;
import org.hyperkv.lsmplus.memory.MemoryTable;
import org.hyperkv.lsmplus.memory.MemoryTableManager;
import org.hyperkv.lsmplus.storage.ChunkManager;
import org.hyperkv.lsmplus.storage.SegmentLocation;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.io.TempDir;

import java.io.File;
import java.nio.file.Path;
import java.util.*;

import static org.junit.jupiter.api.Assertions.*;

class TreeDumperPALMTest {

    @TempDir
    Path tempDir;

    private PageIdManager pageIdManager;

    @BeforeEach
    void setUp() {
        pageIdManager = new PageIdManager();
    }

    private PageManager createPageManager() throws Exception {
        File storageDir = tempDir.toFile();
        java.util.UUID ownerId = java.util.UUID.randomUUID();
        java.util.UUID namespaceId = java.util.UUID.randomUUID();
        ChunkManager chunkManager = new ChunkManager(storageDir.getAbsolutePath(), ownerId, namespaceId);
        return new PageManager(chunkManager, PageCapacityConfig.entryCountBased(12, 4));
    }

    private List<Map.Entry<IndexKey, IndexValue>> mergeTables(List<MemoryTable> sealedTables) {
        TreeMap<IndexKey, IndexValue> merged = new TreeMap<>();
        for (MemoryTable table : sealedTables) {
            merged.putAll(table.getData());
        }
        return new ArrayList<>(merged.entrySet());
    }

    @Test
    void testDeltaQueueBasicOperations() {
        DeltaQueue queue = new DeltaQueue();
        
        SegmentLocation loc1 = new SegmentLocation(UUID.randomUUID(), 100, 500);
        SegmentLocation loc2 = new SegmentLocation(UUID.randomUUID(), 200, 500);
        
        IndexKey key1 = IndexKey.orderedBytes("key1".getBytes());
        IndexKey key2 = IndexKey.orderedBytes("key2".getBytes());
        IndexValue value1 = IndexValue.normal("value1".getBytes());
        
        ChangeDelta delta1 = ChangeDelta.put(key1, value1);
        ChangeDelta delta2 = ChangeDelta.update(key1, IndexPair.of(key2, loc1));
        ChangeDelta delta3 = ChangeDelta.delete(key2);
        
        assertTrue(queue.isEmpty());
        assertEquals(0, queue.getTotalDeltaCount());
        assertEquals(0, queue.getPageCount());
        
        queue.addDelta(loc1, delta1, 0);
        assertFalse(queue.isEmpty());
        assertEquals(1, queue.getTotalDeltaCount());
        assertEquals(1, queue.getPageCount());
        assertTrue(queue.hasDeltasAtLevel(0));
        
        queue.addDelta(loc1, delta2, 0);
        assertEquals(2, queue.getTotalDeltaCount());
        assertEquals(1, queue.getPageCount());
        
        List<ChangeDelta> deltas = queue.getDeltasForLocation(loc1);
        assertEquals(2, deltas.size());
        assertEquals(delta1, deltas.get(0));
        assertEquals(delta2, deltas.get(1));
        
        queue.addDelta(loc2, delta3, 1);
        assertEquals(3, queue.getTotalDeltaCount());
        assertEquals(2, queue.getPageCount());
        assertTrue(queue.hasDeltasAtLevel(1));
        
        Set<Integer> levels = queue.getLevels();
        assertEquals(2, levels.size());
        assertTrue(levels.contains(0));
        assertTrue(levels.contains(1));
        
        var locationsAtLevel0 = queue.getSortedLocationsAtLevel(0);
        assertEquals(1, locationsAtLevel0.size());
        assertTrue(locationsAtLevel0.contains(loc1));
        
        queue.removeDeltasForLocation(loc1);
        assertEquals(1, queue.getTotalDeltaCount());
        assertEquals(1, queue.getPageCount());
        assertFalse(queue.hasDeltasAtLevel(0));
        
        queue.clear();
        assertTrue(queue.isEmpty());
        assertEquals(0, queue.getTotalDeltaCount());
    }

    @Test
    void testChangeDeltaOperations() {
        IndexKey key1 = IndexKey.orderedBytes("key1".getBytes());
        IndexKey key2 = IndexKey.orderedBytes("key2".getBytes());
        IndexValue value1 = IndexValue.normal("value1".getBytes());
        SegmentLocation loc = new SegmentLocation(UUID.randomUUID(), 100, 500);
        
        ChangeDelta putDelta = ChangeDelta.put(key1, value1);
        assertEquals(ChangeDelta.Operation.PUT, putDelta.getOperation());
        assertEquals(key1, putDelta.getTargetKey());
        assertNotNull(putDelta.getNewIndexPair());
        
        ChangeDelta putDeltaWithLoc = ChangeDelta.put(key1, IndexPair.of(key2, loc));
        assertEquals(ChangeDelta.Operation.PUT, putDeltaWithLoc.getOperation());
        assertEquals(key1, putDeltaWithLoc.getTargetKey());
        
        ChangeDelta updateDelta = ChangeDelta.update(key1, IndexPair.of(key2, loc));
        assertEquals(ChangeDelta.Operation.UPDATE, updateDelta.getOperation());
        assertEquals(key1, updateDelta.getTargetKey());
        assertNotNull(updateDelta.getNewIndexPair());
        
        ChangeDelta deleteDelta = ChangeDelta.delete(key1);
        assertEquals(ChangeDelta.Operation.DELETE, deleteDelta.getOperation());
        assertEquals(key1, deleteDelta.getTargetKey());
        assertNull(deleteDelta.getNewIndexPair());
    }

    @Test
    void testDeltaQueueLevelClearing() {
        DeltaQueue queue = new DeltaQueue();
        
        SegmentLocation loc1 = new SegmentLocation(UUID.randomUUID(), 100, 500);
        SegmentLocation loc2 = new SegmentLocation(UUID.randomUUID(), 200, 500);
        SegmentLocation loc3 = new SegmentLocation(UUID.randomUUID(), 300, 500);
        
        IndexKey key = IndexKey.orderedBytes("key".getBytes());
        IndexValue value = IndexValue.normal("value".getBytes());
        
        queue.addDelta(loc1, ChangeDelta.put(key, value), 0);
        queue.addDelta(loc2, ChangeDelta.put(key, value), 0);
        queue.addDelta(loc3, ChangeDelta.put(key, value), 1);
        
        assertEquals(3, queue.getTotalDeltaCount());
        assertEquals(2, queue.getLevels().size());
        
        queue.clearLevel(0);
        assertEquals(1, queue.getTotalDeltaCount());
        assertEquals(1, queue.getLevels().size());
        assertFalse(queue.hasDeltasAtLevel(0));
        assertTrue(queue.hasDeltasAtLevel(1));
    }

    @Test
    void testPALMStyleSingleLevelPropagation() throws Exception {
        MemoryTableManager memTableManager = new MemoryTableManager(1024 * 1024);
        PageManager pageManager = createPageManager();
        BPlusTree tree = new BPlusTree(pageManager);
        TreeDumper treeDumper = new TreeDumper(tree, pageManager);

        MemoryTable table1 = memTableManager.getActiveTable();
        for (int i = 0; i < 20; i++) {
            table1.put(IndexKey.orderedBytes(String.format("key%03d", i).getBytes()),
                    IndexValue.normal(("value" + i).getBytes()), null);
        }
        memTableManager.sealActiveTable();

        List<Map.Entry<IndexKey, IndexValue>> merged1 = mergeTables(memTableManager.getSealedTables());
        treeDumper.dump(merged1, null);
        treeDumper.promoteRoot();
        memTableManager.clearSealedTables();

        int initialHeight = tree.getHeight();
        assertTrue(initialHeight >= 2);

        MemoryTable table2 = memTableManager.getActiveTable();
        for (int i = 20; i < 40; i++) {
            table2.put(IndexKey.orderedBytes(String.format("key%03d", i).getBytes()),
                    IndexValue.normal(("value" + i).getBytes()), null);
        }
        memTableManager.sealActiveTable();

        List<Map.Entry<IndexKey, IndexValue>> merged2 = mergeTables(memTableManager.getSealedTables());
        treeDumper.dump(merged2, null);
        treeDumper.promoteRoot();
        memTableManager.clearSealedTables();

        for (int i = 0; i < 40; i++) {
            IndexValue value = tree.search(IndexKey.orderedBytes(String.format("key%03d", i).getBytes()));
            assertNotNull(value, "Value for key" + i + " should not be null");
            assertArrayEquals(("value" + i).getBytes(), value.getValueData());
        }
    }

    @Test
    void testPALMStyleMultipleDeltasSamePage() throws Exception {
        MemoryTableManager memTableManager = new MemoryTableManager(1024 * 1024);
        PageManager pageManager = createPageManager();
        BPlusTree tree = new BPlusTree(pageManager);
        TreeDumper treeDumper = new TreeDumper(tree, pageManager);

        MemoryTable table1 = memTableManager.getActiveTable();
        for (int i = 0; i < 15; i++) {
            table1.put(IndexKey.orderedBytes(String.format("key%03d", i).getBytes()),
                    IndexValue.normal(("value" + i).getBytes()), null);
        }
        memTableManager.sealActiveTable();

        List<Map.Entry<IndexKey, IndexValue>> merged1 = mergeTables(memTableManager.getSealedTables());
        treeDumper.dump(merged1, null);
        treeDumper.promoteRoot();
        memTableManager.clearSealedTables();

        MemoryTable table2 = memTableManager.getActiveTable();
        for (int i = 5; i < 25; i++) {
            table2.put(IndexKey.orderedBytes(String.format("key%03d", i).getBytes()),
                    IndexValue.normal(("updated" + i).getBytes()), null);
        }
        memTableManager.sealActiveTable();

        List<Map.Entry<IndexKey, IndexValue>> merged2 = mergeTables(memTableManager.getSealedTables());
        treeDumper.dump(merged2, null);
        treeDumper.promoteRoot();
        memTableManager.clearSealedTables();

        for (int i = 0; i < 5; i++) {
            IndexValue value = tree.search(IndexKey.orderedBytes(String.format("key%03d", i).getBytes()));
            assertNotNull(value);
            assertArrayEquals(("value" + i).getBytes(), value.getValueData());
        }
        
        for (int i = 5; i < 15; i++) {
            IndexValue value = tree.search(IndexKey.orderedBytes(String.format("key%03d", i).getBytes()));
            assertNotNull(value);
            assertArrayEquals(("updated" + i).getBytes(), value.getValueData());
        }
        
        for (int i = 15; i < 25; i++) {
            IndexValue value = tree.search(IndexKey.orderedBytes(String.format("key%03d", i).getBytes()));
            assertNotNull(value);
            assertArrayEquals(("updated" + i).getBytes(), value.getValueData());
        }
    }

    @Test
    void testPALMStyleIndexPageSplit() throws Exception {
        MemoryTableManager memTableManager = new MemoryTableManager(1024 * 1024);
        PageManager pageManager = createPageManager();
        BPlusTree tree = new BPlusTree(pageManager);
        TreeDumper treeDumper = new TreeDumper(tree, pageManager);

        MemoryTable table1 = memTableManager.getActiveTable();
        for (int i = 0; i < 100; i++) {
            table1.put(IndexKey.orderedBytes(String.format("key%05d", i).getBytes()),
                    IndexValue.normal(("value" + i).getBytes()), null);
        }
        memTableManager.sealActiveTable();

        List<Map.Entry<IndexKey, IndexValue>> merged1 = mergeTables(memTableManager.getSealedTables());
        treeDumper.dump(merged1, null);
        treeDumper.promoteRoot();
        memTableManager.clearSealedTables();

        int initialHeight = tree.getHeight();
        long initialIndexPages = tree.getIndexPageCount();

        MemoryTable table2 = memTableManager.getActiveTable();
        for (int i = 100; i < 300; i++) {
            table2.put(IndexKey.orderedBytes(String.format("key%05d", i).getBytes()),
                    IndexValue.normal(("value" + i).getBytes()), null);
        }
        memTableManager.sealActiveTable();

        List<Map.Entry<IndexKey, IndexValue>> merged2 = mergeTables(memTableManager.getSealedTables());
        treeDumper.dump(merged2, null);
        treeDumper.promoteRoot();
        memTableManager.clearSealedTables();

        assertNotNull(tree.getWriterRoot());

        for (int i = 0; i < 300; i++) {
            IndexValue value = tree.search(IndexKey.orderedBytes(String.format("key%05d", i).getBytes()));
            assertNotNull(value, "Value for key" + i + " should not be null");
        }
    }

    @Test
    void testPALMStyleIndexPageMerge() throws Exception {
        MemoryTableManager memTableManager = new MemoryTableManager(1024 * 1024);
        PageManager pageManager = createPageManager();
        BPlusTree tree = new BPlusTree(pageManager);
        TreeDumper treeDumper = new TreeDumper(tree, pageManager);

        MemoryTable table1 = memTableManager.getActiveTable();
        for (int i = 0; i < 200; i++) {
            table1.put(IndexKey.orderedBytes(String.format("key%05d", i).getBytes()),
                    IndexValue.normal(("value" + i).getBytes()), null);
        }
        memTableManager.sealActiveTable();

        List<Map.Entry<IndexKey, IndexValue>> merged1 = mergeTables(memTableManager.getSealedTables());
        treeDumper.dump(merged1, null);
        treeDumper.promoteRoot();
        memTableManager.clearSealedTables();

        long initialIndexPages = tree.getIndexPageCount();

        MemoryTable table2 = memTableManager.getActiveTable();
        for (int i = 0; i < 150; i++) {
            table2.put(IndexKey.orderedBytes(String.format("key%05d", i).getBytes()),
                    IndexValue.tombstone(), null);
        }
        memTableManager.sealActiveTable();

        List<Map.Entry<IndexKey, IndexValue>> merged2 = mergeTables(memTableManager.getSealedTables());
        treeDumper.dump(merged2, null);
        treeDumper.promoteRoot();
        memTableManager.clearSealedTables();

        assertNotNull(tree.getWriterRoot());

        for (int i = 0; i < 150; i++) {
            IndexValue value = tree.search(IndexKey.orderedBytes(String.format("key%05d", i).getBytes()));
            assertNull(value);
        }
        
        for (int i = 150; i < 200; i++) {
            IndexValue value = tree.search(IndexKey.orderedBytes(String.format("key%05d", i).getBytes()));
            assertNotNull(value);
        }
    }

    @Test
    void testPALMStyleCascadingUpdates() throws Exception {
        MemoryTableManager memTableManager = new MemoryTableManager(1024 * 1024);
        PageManager pageManager = createPageManager();
        BPlusTree tree = new BPlusTree(pageManager);
        TreeDumper treeDumper = new TreeDumper(tree, pageManager);

        MemoryTable table1 = memTableManager.getActiveTable();
        for (int i = 0; i < 500; i++) {
            table1.put(IndexKey.orderedBytes(String.format("key%05d", i).getBytes()),
                    IndexValue.normal(("value" + i).getBytes()), null);
        }
        memTableManager.sealActiveTable();

        List<Map.Entry<IndexKey, IndexValue>> merged1 = mergeTables(memTableManager.getSealedTables());
        treeDumper.dump(merged1, null);
        treeDumper.promoteRoot();
        memTableManager.clearSealedTables();

        int initialHeight = tree.getHeight();

        MemoryTable table2 = memTableManager.getActiveTable();
        for (int i = 500; i < 1000; i++) {
            table2.put(IndexKey.orderedBytes(String.format("key%05d", i).getBytes()),
                    IndexValue.normal(("value" + i).getBytes()), null);
        }
        memTableManager.sealActiveTable();

        List<Map.Entry<IndexKey, IndexValue>> merged2 = mergeTables(memTableManager.getSealedTables());
        treeDumper.dump(merged2, null);
        treeDumper.promoteRoot();
        memTableManager.clearSealedTables();

        int finalHeight = tree.getHeight();
        assertTrue(finalHeight >= initialHeight);

        for (int i = 0; i < 1000; i++) {
            IndexValue value = tree.search(IndexKey.orderedBytes(String.format("key%05d", i).getBytes()));
            assertNotNull(value, "Value for key" + i + " should not be null");
        }
    }

    @Test
    void testPALMStyleMinKeyChangePropagation() throws Exception {
        MemoryTableManager memTableManager = new MemoryTableManager(1024 * 1024);
        PageManager pageManager = createPageManager();
        BPlusTree tree = new BPlusTree(pageManager);
        TreeDumper treeDumper = new TreeDumper(tree, pageManager);

        MemoryTable table1 = memTableManager.getActiveTable();
        table1.put(IndexKey.orderedBytes("keyA".getBytes()), IndexValue.normal("valueA".getBytes()), null);
        table1.put(IndexKey.orderedBytes("keyB".getBytes()), IndexValue.normal("valueB".getBytes()), null);
        table1.put(IndexKey.orderedBytes("keyC".getBytes()), IndexValue.normal("valueC".getBytes()), null);
        memTableManager.sealActiveTable();

        List<Map.Entry<IndexKey, IndexValue>> merged1 = mergeTables(memTableManager.getSealedTables());
        treeDumper.dump(merged1, null);
        treeDumper.promoteRoot();
        memTableManager.clearSealedTables();

        MemoryTable table2 = memTableManager.getActiveTable();
        table2.put(IndexKey.orderedBytes("keyA".getBytes()), IndexValue.tombstone(), null);
        memTableManager.sealActiveTable();

        List<Map.Entry<IndexKey, IndexValue>> merged2 = mergeTables(memTableManager.getSealedTables());
        treeDumper.dump(merged2, null);
        treeDumper.promoteRoot();
        memTableManager.clearSealedTables();

        IndexValue valueA = tree.search(IndexKey.orderedBytes("keyA".getBytes()));
        assertNull(valueA);

        IndexValue valueB = tree.search(IndexKey.orderedBytes("keyB".getBytes()));
        assertNotNull(valueB);
        assertArrayEquals("valueB".getBytes(), valueB.getValueData());

        IndexValue valueC = tree.search(IndexKey.orderedBytes("keyC".getBytes()));
        assertNotNull(valueC);
        assertArrayEquals("valueC".getBytes(), valueC.getValueData());
    }

    @Test
    void testPALMStyleEmptyPageHandling() throws Exception {
        MemoryTableManager memTableManager = new MemoryTableManager(1024 * 1024);
        PageManager pageManager = createPageManager();
        BPlusTree tree = new BPlusTree(pageManager);
        TreeDumper treeDumper = new TreeDumper(tree, pageManager);

        MemoryTable table1 = memTableManager.getActiveTable();
        table1.put(IndexKey.orderedBytes("key1".getBytes()), IndexValue.normal("value1".getBytes()), null);
        table1.put(IndexKey.orderedBytes("key2".getBytes()), IndexValue.normal("value2".getBytes()), null);
        memTableManager.sealActiveTable();

        List<Map.Entry<IndexKey, IndexValue>> merged1 = mergeTables(memTableManager.getSealedTables());
        treeDumper.dump(merged1, null);
        treeDumper.promoteRoot();
        memTableManager.clearSealedTables();

        MemoryTable table2 = memTableManager.getActiveTable();
        table2.put(IndexKey.orderedBytes("key1".getBytes()), IndexValue.tombstone(), null);
        table2.put(IndexKey.orderedBytes("key2".getBytes()), IndexValue.tombstone(), null);
        memTableManager.sealActiveTable();

        List<Map.Entry<IndexKey, IndexValue>> merged2 = mergeTables(memTableManager.getSealedTables());
        treeDumper.dump(merged2, null);
        treeDumper.promoteRoot();
        memTableManager.clearSealedTables();

        assertNull(tree.search(IndexKey.orderedBytes("key1".getBytes())));
        assertNull(tree.search(IndexKey.orderedBytes("key2".getBytes())));
    }

    @Test
    void testPALMStyleMultipleLevelSplitMerge() throws Exception {
        MemoryTableManager memTableManager = new MemoryTableManager(1024 * 1024);
        PageManager pageManager = createPageManager();
        BPlusTree tree = new BPlusTree(pageManager);
        TreeDumper treeDumper = new TreeDumper(tree, pageManager);

        MemoryTable table1 = memTableManager.getActiveTable();
        for (int i = 0; i < 1000; i++) {
            table1.put(IndexKey.orderedBytes(String.format("key%05d", i).getBytes()),
                    IndexValue.normal(("value" + i).getBytes()), null);
        }
        memTableManager.sealActiveTable();

        List<Map.Entry<IndexKey, IndexValue>> merged1 = mergeTables(memTableManager.getSealedTables());
        treeDumper.dump(merged1, null);
        treeDumper.promoteRoot();
        memTableManager.clearSealedTables();

        int heightAfterFirstDump = tree.getHeight();
        long indexPagesAfterFirstDump = tree.getIndexPageCount();

        MemoryTable table2 = memTableManager.getActiveTable();
        for (int i = 1000; i < 2000; i++) {
            table2.put(IndexKey.orderedBytes(String.format("key%05d", i).getBytes()),
                    IndexValue.normal(("value" + i).getBytes()), null);
        }
        memTableManager.sealActiveTable();

        List<Map.Entry<IndexKey, IndexValue>> merged2 = mergeTables(memTableManager.getSealedTables());
        treeDumper.dump(merged2, null);
        treeDumper.promoteRoot();
        memTableManager.clearSealedTables();

        assertNotNull(tree.getWriterRoot());

        MemoryTable table3 = memTableManager.getActiveTable();
        for (int i = 0; i < 1500; i++) {
            table3.put(IndexKey.orderedBytes(String.format("key%05d", i).getBytes()),
                    IndexValue.tombstone(), null);
        }
        memTableManager.sealActiveTable();

        List<Map.Entry<IndexKey, IndexValue>> merged3 = mergeTables(memTableManager.getSealedTables());
        treeDumper.dump(merged3, null);
        treeDumper.promoteRoot();
        memTableManager.clearSealedTables();

        assertNotNull(tree.getWriterRoot());

        for (int i = 0; i < 1500; i++) {
            IndexValue value = tree.search(IndexKey.orderedBytes(String.format("key%05d", i).getBytes()));
            assertNull(value);
        }
        
        for (int i = 1500; i < 2000; i++) {
            IndexValue value = tree.search(IndexKey.orderedBytes(String.format("key%05d", i).getBytes()));
            assertNotNull(value);
        }
    }

    @Test
    void testPALMStyleConcurrentPageModifications() throws Exception {
        MemoryTableManager memTableManager = new MemoryTableManager(1024 * 1024);
        PageManager pageManager = createPageManager();
        BPlusTree tree = new BPlusTree(pageManager);
        TreeDumper treeDumper = new TreeDumper(tree, pageManager);

        MemoryTable table1 = memTableManager.getActiveTable();
        for (int i = 0; i < 50; i++) {
            table1.put(IndexKey.orderedBytes(String.format("key%03d", i).getBytes()),
                    IndexValue.normal(("value" + i).getBytes()), null);
        }
        memTableManager.sealActiveTable();

        List<Map.Entry<IndexKey, IndexValue>> merged1 = mergeTables(memTableManager.getSealedTables());
        treeDumper.dump(merged1, null);
        treeDumper.promoteRoot();
        memTableManager.clearSealedTables();

        MemoryTable table2 = memTableManager.getActiveTable();
        for (int i = 0; i < 50; i++) {
            table2.put(IndexKey.orderedBytes(String.format("key%03d", i).getBytes()),
                    IndexValue.normal(("updated" + i).getBytes()), null);
        }
        for (int i = 50; i < 100; i++) {
            table2.put(IndexKey.orderedBytes(String.format("key%03d", i).getBytes()),
                    IndexValue.normal(("value" + i).getBytes()), null);
        }
        memTableManager.sealActiveTable();

        List<Map.Entry<IndexKey, IndexValue>> merged2 = mergeTables(memTableManager.getSealedTables());
        treeDumper.dump(merged2, null);
        treeDumper.promoteRoot();
        memTableManager.clearSealedTables();

        for (int i = 0; i < 50; i++) {
            IndexValue value = tree.search(IndexKey.orderedBytes(String.format("key%03d", i).getBytes()));
            assertNotNull(value);
            assertArrayEquals(("updated" + i).getBytes(), value.getValueData());
        }
        
        for (int i = 50; i < 100; i++) {
            IndexValue value = tree.search(IndexKey.orderedBytes(String.format("key%03d", i).getBytes()));
            assertNotNull(value);
            assertArrayEquals(("value" + i).getBytes(), value.getValueData());
        }
    }

    @Test
    void testPALMStyleRootPageHandling() throws Exception {
        MemoryTableManager memTableManager = new MemoryTableManager(1024 * 1024);
        PageManager pageManager = createPageManager();
        BPlusTree tree = new BPlusTree(pageManager);
        TreeDumper treeDumper = new TreeDumper(tree, pageManager);

        MemoryTable table1 = memTableManager.getActiveTable();
        for (int i = 0; i < 10; i++) {
            table1.put(IndexKey.orderedBytes(String.format("key%d", i).getBytes()),
                    IndexValue.normal(("value" + i).getBytes()), null);
        }
        memTableManager.sealActiveTable();

        List<Map.Entry<IndexKey, IndexValue>> merged1 = mergeTables(memTableManager.getSealedTables());
        treeDumper.dump(merged1, null);
        treeDumper.promoteRoot();
        memTableManager.clearSealedTables();

        assertNotNull(tree.getWriterRoot());
        int initialHeight = tree.getHeight();

        MemoryTable table2 = memTableManager.getActiveTable();
        for (int i = 10; i < 100; i++) {
            table2.put(IndexKey.orderedBytes(String.format("key%d", i).getBytes()),
                    IndexValue.normal(("value" + i).getBytes()), null);
        }
        memTableManager.sealActiveTable();

        List<Map.Entry<IndexKey, IndexValue>> merged2 = mergeTables(memTableManager.getSealedTables());
        treeDumper.dump(merged2, null);
        treeDumper.promoteRoot();
        memTableManager.clearSealedTables();

        assertNotNull(tree.getWriterRoot());
        int finalHeight = tree.getHeight();
        assertTrue(finalHeight >= initialHeight);

        for (int i = 0; i < 100; i++) {
            IndexValue value = tree.search(IndexKey.orderedBytes(String.format("key%d", i).getBytes()));
            assertNotNull(value, "Value for key" + i + " should not be null");
        }
    }

    @Test
    void testPALMStyleBatchUpdates() throws Exception {
        MemoryTableManager memTableManager = new MemoryTableManager(1024 * 1024);
        PageManager pageManager = createPageManager();
        BPlusTree tree = new BPlusTree(pageManager);
        TreeDumper treeDumper = new TreeDumper(tree, pageManager);

        MemoryTable table1 = memTableManager.getActiveTable();
        for (int i = 0; i < 200; i++) {
            table1.put(IndexKey.orderedBytes(String.format("key%05d", i).getBytes()),
                    IndexValue.normal(("value" + i).getBytes()), null);
        }
        memTableManager.sealActiveTable();

        List<Map.Entry<IndexKey, IndexValue>> merged1 = mergeTables(memTableManager.getSealedTables());
        treeDumper.dump(merged1, null);
        treeDumper.promoteRoot();
        memTableManager.clearSealedTables();

        MemoryTable table2 = memTableManager.getActiveTable();
        for (int i = 0; i < 200; i += 2) {
            table2.put(IndexKey.orderedBytes(String.format("key%05d", i).getBytes()),
                    IndexValue.tombstone(), null);
        }
        for (int i = 200; i < 400; i++) {
            table2.put(IndexKey.orderedBytes(String.format("key%05d", i).getBytes()),
                    IndexValue.normal(("value" + i).getBytes()), null);
        }
        memTableManager.sealActiveTable();

        List<Map.Entry<IndexKey, IndexValue>> merged2 = mergeTables(memTableManager.getSealedTables());
        treeDumper.dump(merged2, null);
        treeDumper.promoteRoot();
        memTableManager.clearSealedTables();

        for (int i = 0; i < 200; i += 2) {
            IndexValue value = tree.search(IndexKey.orderedBytes(String.format("key%05d", i).getBytes()));
            assertNull(value);
        }
        
        for (int i = 1; i < 200; i += 2) {
            IndexValue value = tree.search(IndexKey.orderedBytes(String.format("key%05d", i).getBytes()));
            assertNotNull(value);
        }
        
        for (int i = 200; i < 400; i++) {
            IndexValue value = tree.search(IndexKey.orderedBytes(String.format("key%05d", i).getBytes()));
            assertNotNull(value);
        }
    }

    @Test
    void testPALMStyleDeltaOrdering() throws Exception {
        MemoryTableManager memTableManager = new MemoryTableManager(1024 * 1024);
        PageManager pageManager = createPageManager();
        BPlusTree tree = new BPlusTree(pageManager);
        TreeDumper treeDumper = new TreeDumper(tree, pageManager);

        MemoryTable table1 = memTableManager.getActiveTable();
        for (int i = 0; i < 300; i++) {
            table1.put(IndexKey.orderedBytes(String.format("key%05d", i).getBytes()),
                    IndexValue.normal(("value" + i).getBytes()), null);
        }
        memTableManager.sealActiveTable();

        List<Map.Entry<IndexKey, IndexValue>> merged1 = mergeTables(memTableManager.getSealedTables());
        treeDumper.dump(merged1, null);
        treeDumper.promoteRoot();
        memTableManager.clearSealedTables();

        MemoryTable table2 = memTableManager.getActiveTable();
        for (int i = 100; i < 200; i++) {
            table2.put(IndexKey.orderedBytes(String.format("key%05d", i).getBytes()),
                    IndexValue.normal(("updated" + i).getBytes()), null);
        }
        memTableManager.sealActiveTable();

        List<Map.Entry<IndexKey, IndexValue>> merged2 = mergeTables(memTableManager.getSealedTables());
        treeDumper.dump(merged2, null);
        treeDumper.promoteRoot();
        memTableManager.clearSealedTables();

        for (int i = 0; i < 100; i++) {
            IndexValue value = tree.search(IndexKey.orderedBytes(String.format("key%05d", i).getBytes()));
            assertNotNull(value);
            assertArrayEquals(("value" + i).getBytes(), value.getValueData());
        }
        
        for (int i = 100; i < 200; i++) {
            IndexValue value = tree.search(IndexKey.orderedBytes(String.format("key%05d", i).getBytes()));
            assertNotNull(value);
            assertArrayEquals(("updated" + i).getBytes(), value.getValueData());
        }
        
        for (int i = 200; i < 300; i++) {
            IndexValue value = tree.search(IndexKey.orderedBytes(String.format("key%05d", i).getBytes()));
            assertNotNull(value);
            assertArrayEquals(("value" + i).getBytes(), value.getValueData());
        }
    }

    @Test
    void testPALMStyleStressTest() throws Exception {
        MemoryTableManager memTableManager = new MemoryTableManager(1024 * 1024);
        PageManager pageManager = createPageManager();
        BPlusTree tree = new BPlusTree(pageManager);
        TreeDumper treeDumper = new TreeDumper(tree, pageManager);

        for (int round = 0; round < 5; round++) {
            MemoryTable table = memTableManager.getActiveTable();
            int startKey = round * 500;
            int endKey = startKey + 500;
            
            for (int i = startKey; i < endKey; i++) {
                table.put(IndexKey.orderedBytes(String.format("key%05d", i).getBytes()),
                        IndexValue.normal(("value" + i).getBytes()), null);
            }
            memTableManager.sealActiveTable();

            List<Map.Entry<IndexKey, IndexValue>> merged = mergeTables(memTableManager.getSealedTables());
            treeDumper.dump(merged, null);
            treeDumper.promoteRoot();
            memTableManager.clearSealedTables();

            assertNotNull(tree.getWriterRoot());

            for (int i = 0; i < endKey; i++) {
                IndexValue value = tree.search(IndexKey.orderedBytes(String.format("key%05d", i).getBytes()));
                assertNotNull(value, "Value for key" + i + " should not be null after round " + round);
            }
        }

        assertTrue(tree.getTotalEntryCount() > 0);
    }
}
