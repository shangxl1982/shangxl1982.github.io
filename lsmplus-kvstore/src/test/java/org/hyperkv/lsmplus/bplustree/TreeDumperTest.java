package org.hyperkv.lsmplus.bplustree;

import org.hyperkv.lsmplus.api.model.IndexKey;
import org.hyperkv.lsmplus.api.model.IndexValue;
import org.hyperkv.lsmplus.bplustree.page.IndexPair;
import org.hyperkv.lsmplus.bplustree.page.Page;
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

class TreeDumperTest {

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
    void testDumpWithEmptySealedTables() throws Exception {
        MemoryTableManager memTableManager = new MemoryTableManager(1024 * 1024);
        PageManager pageManager = createPageManager();
        BPlusTree tree = new BPlusTree(pageManager);
        TreeDumper treeDumper = new TreeDumper(tree, pageManager);

        List<Map.Entry<IndexKey, IndexValue>> merged = mergeTables(memTableManager.getSealedTables());
        treeDumper.dump(merged, null);

        assertNull(tree.getWriterRootLocation());
    }

    @Test
    void testBuildNewTreeFromSealedTables() throws Exception {
        MemoryTableManager memTableManager = new MemoryTableManager(1024 * 1024);
        PageManager pageManager = createPageManager();
        BPlusTree tree = new BPlusTree(pageManager);

        MemoryTable table = memTableManager.getActiveTable();
        for (int i = 0; i < 10; i++) {
            table.put(IndexKey.orderedBytes(("key" + i).getBytes()),
                      IndexValue.normal(("value" + i).getBytes()), null);
        }
        memTableManager.sealActiveTable();

        TreeDumper treeDumper = new TreeDumper(tree, pageManager);
        List<Map.Entry<IndexKey, IndexValue>> merged = mergeTables(memTableManager.getSealedTables());
        treeDumper.dump(merged, null);
        memTableManager.clearSealedTables();

        assertNotNull(tree.getWriterRootLocation());
    }

    @Test
    void testUpdateExistingTreeWithNewEntries() throws Exception {
        MemoryTableManager memTableManager = new MemoryTableManager(1024 * 1024);
        PageManager pageManager = createPageManager();
        BPlusTree tree = new BPlusTree(pageManager);

        IndexKey key1 = IndexKey.orderedBytes("key1".getBytes());
        IndexValue value1 = IndexValue.normal("value1".getBytes());

        MemoryTable table1 = memTableManager.getActiveTable();
        table1.put(key1, value1, null);
        memTableManager.sealActiveTable();

        TreeDumper treeDumper = new TreeDumper(tree, pageManager);
        List<Map.Entry<IndexKey, IndexValue>> merged1 = mergeTables(memTableManager.getSealedTables());
        treeDumper.dump(merged1, null);
        memTableManager.clearSealedTables();

        MemoryTable table2 = memTableManager.getActiveTable();
        IndexKey key2 = IndexKey.orderedBytes("key2".getBytes());
        IndexValue value2 = IndexValue.normal("value2".getBytes());
        table2.put(key2, value2, null);
        memTableManager.sealActiveTable();

        List<Map.Entry<IndexKey, IndexValue>> merged2 = mergeTables(memTableManager.getSealedTables());
        treeDumper.dump(merged2, null);
        treeDumper.promoteRoot();

        memTableManager.clearSealedTables();

        IndexValue retrieved1 = tree.search(key1);
        IndexValue retrieved2 = tree.search(key2);
        assertNotNull(retrieved1);
        assertNotNull(retrieved2);
    }

    @Test
    void testTombstoneHandlingDuringTreeDump() throws Exception {
        MemoryTableManager memTableManager = new MemoryTableManager(1024 * 1024);
        PageManager pageManager = createPageManager();
        BPlusTree tree = new BPlusTree(pageManager);

        IndexKey key1 = IndexKey.orderedBytes("key1".getBytes());
        IndexValue value1 = IndexValue.normal("value1".getBytes());

        MemoryTable table1 = memTableManager.getActiveTable();
        table1.put(key1, value1, null);
        memTableManager.sealActiveTable();

        TreeDumper treeDumper = new TreeDumper(tree, pageManager);
        List<Map.Entry<IndexKey, IndexValue>> merged1 = mergeTables(memTableManager.getSealedTables());
        treeDumper.dump(merged1, null);
        memTableManager.clearSealedTables();

        MemoryTable table2 = memTableManager.getActiveTable();
        table2.put(key1, IndexValue.tombstone(), null);
        memTableManager.sealActiveTable();

        List<Map.Entry<IndexKey, IndexValue>> merged2 = mergeTables(memTableManager.getSealedTables());
        treeDumper.dump(merged2, null);
        memTableManager.clearSealedTables();

        IndexValue retrieved = tree.search(key1);
        assertNull(retrieved);
    }

    @Test
    void testVirtualLocationResolution() throws Exception {
        MemoryTableManager memTableManager = new MemoryTableManager(1024 * 1024);
        PageManager pageManager = createPageManager();
        BPlusTree tree = new BPlusTree(pageManager);

        MemoryTable table1 = memTableManager.getActiveTable();
        for (int i = 0; i < 100; i++) {
            table1.put(IndexKey.orderedBytes(("key" + i).getBytes()),
                      IndexValue.normal(("value" + i).getBytes()), null);
        }
        memTableManager.sealActiveTable();

        TreeDumper treeDumper = new TreeDumper(tree, pageManager);
        var tables = memTableManager.getSealedTables();
        List<Map.Entry<IndexKey, IndexValue>> merged1 = mergeTables(tables);
        treeDumper.dump(merged1, null);
        treeDumper.promoteRoot();
        tables.forEach(MemoryTable::setForClear);
        memTableManager.clearSealedTables();

        MemoryTable table2 = memTableManager.getActiveTable();
        for (int i = 100; i < 200; i++) {
            table2.put(IndexKey.orderedBytes(("key" + i).getBytes()),
                      IndexValue.normal(("value" + i).getBytes()), null);
        }
        memTableManager.sealActiveTable();
        tables = memTableManager.getSealedTables();
        List<Map.Entry<IndexKey, IndexValue>> merged2 = mergeTables(tables);
        treeDumper.dump(merged2, null);
        treeDumper.promoteRoot();
        tables.forEach(MemoryTable::setForClear);
        memTableManager.clearSealedTables();

        for (int i = 0; i < 200; i++) {
            IndexValue value = tree.search(IndexKey.orderedBytes(("key" + i).getBytes()));
            assertNotNull(value, "Value for key" + i + " should not be null");
        }
    }

    @Test
    void testLevelBasedFlushOrdering() throws Exception {
        MemoryTableManager memTableManager = new MemoryTableManager(1024 * 1024);
        PageManager pageManager = createPageManager();
        BPlusTree tree = new BPlusTree(pageManager);

        MemoryTable table1 = memTableManager.getActiveTable();
        for (int i = 0; i < 500; i++) {
            table1.put(IndexKey.orderedBytes(String.format("key%05d", i).getBytes()),
                      IndexValue.normal(("value" + i).getBytes()), null);
        }
        memTableManager.sealActiveTable();

        TreeDumper treeDumper = new TreeDumper(tree, pageManager);
        List<Map.Entry<IndexKey, IndexValue>> merged1 = mergeTables(memTableManager.getSealedTables());
        treeDumper.dump(merged1, null);
        memTableManager.clearSealedTables();

        MemoryTable table2 = memTableManager.getActiveTable();
        for (int i = 500; i < 1000; i++) {
            table2.put(IndexKey.orderedBytes(String.format("key%05d", i).getBytes()),
                      IndexValue.normal(("value" + i).getBytes()), null);
        }
        memTableManager.sealActiveTable();

        List<Map.Entry<IndexKey, IndexValue>> merged2 = mergeTables(memTableManager.getSealedTables());
        treeDumper.dump(merged2, null);
        memTableManager.clearSealedTables();

        assertNotNull(tree.getWriterRootLocation());
    }

    @Test
    void testMultipleSealedTablesMerging() throws Exception {
        MemoryTableManager memTableManager = new MemoryTableManager(1024 * 1024);
        PageManager pageManager = createPageManager();
        BPlusTree tree = new BPlusTree(pageManager);

        MemoryTable table1 = memTableManager.getActiveTable();
        table1.put(IndexKey.orderedBytes("key1".getBytes()), IndexValue.normal("value1".getBytes()), null);
        table1.put(IndexKey.orderedBytes("key2".getBytes()), IndexValue.normal("value2".getBytes()), null);
        memTableManager.sealActiveTable();

        TreeDumper treeDumper = new TreeDumper(tree, pageManager);
        List<Map.Entry<IndexKey, IndexValue>> merged1 = mergeTables(memTableManager.getSealedTables());
        treeDumper.dump(merged1, null);
        memTableManager.clearSealedTables();

        MemoryTable table2 = memTableManager.getActiveTable();
        table2.put(IndexKey.orderedBytes("key2".getBytes()), IndexValue.normal("updated_value2".getBytes()), null);
        table2.put(IndexKey.orderedBytes("key3".getBytes()), IndexValue.normal("value3".getBytes()), null);
        memTableManager.sealActiveTable();

        List<Map.Entry<IndexKey, IndexValue>> merged2 = mergeTables(memTableManager.getSealedTables());
        treeDumper.dump(merged2, null);
        treeDumper.promoteRoot();

        memTableManager.clearSealedTables();

        IndexValue value2 = tree.search(IndexKey.orderedBytes("key2".getBytes()));
        assertNotNull(value2);
        assertArrayEquals("updated_value2".getBytes(), value2.getValueData());
    }

    @Test
    void testCrashConsistencyWithVirtualLocations() throws Exception {
        MemoryTableManager memTableManager = new MemoryTableManager(1024 * 1024);
        PageManager pageManager = createPageManager();
        BPlusTree tree = new BPlusTree(pageManager);

        MemoryTable table1 = memTableManager.getActiveTable();
        for (int i = 0; i < 100; i++) {
            table1.put(IndexKey.orderedBytes(String.format("key%05d", i).getBytes()),
                      IndexValue.normal(("value" + i).getBytes()), null);
        }
        memTableManager.sealActiveTable();

        TreeDumper treeDumper = new TreeDumper(tree, pageManager);
        var tables = memTableManager.getSealedTables();
        tables.forEach(MemoryTable::setForDump);
        List<Map.Entry<IndexKey, IndexValue>> merged1 = mergeTables(tables);
        treeDumper.dump(merged1, null);
        treeDumper.promoteRoot();
        tables.forEach(MemoryTable::setForClear);
        memTableManager.clearSealedTables();

        for (int i = 0; i < 100; i++) {
            IndexValue value = tree.search(IndexKey.orderedBytes(String.format("key%05d", i).getBytes()));
            assertNotNull(value, "Value for key" + i + " should not be null after first dump");
        }

        MemoryTable table2 = memTableManager.getActiveTable();
        for (int i = 100; i < 200; i++) {
            table2.put(IndexKey.orderedBytes(String.format("key%05d", i).getBytes()),
                      IndexValue.normal(("value" + i).getBytes()), null);
        }
        memTableManager.sealActiveTable();
        tables = memTableManager.getSealedTables();
        tables.forEach(MemoryTable::setForDump);
        List<Map.Entry<IndexKey, IndexValue>> merged2 = mergeTables(tables);
        treeDumper.dump(merged2, null);
        treeDumper.promoteRoot();
        tables.forEach(MemoryTable::setForClear);
        memTableManager.clearSealedTables();

        for (int i = 0; i < 200; i++) {
            IndexValue value = tree.search(IndexKey.orderedBytes(String.format("key%05d", i).getBytes()));
            assertNotNull(value, "Value for key" + i + " should not be null after second dump");
        }
    }

    @Test
    void testNoDanglingPointersAfterCrash() throws Exception {
        MemoryTableManager memTableManager = new MemoryTableManager(1024 * 1024);
        PageManager pageManager = createPageManager();
        BPlusTree tree = new BPlusTree(pageManager);

        MemoryTable table1 = memTableManager.getActiveTable();
        for (int i = 0; i < 300; i++) {
            table1.put(IndexKey.orderedBytes(String.format("key%05d", i).getBytes()),
                      IndexValue.normal(("value" + i).getBytes()), null);
        }
        memTableManager.sealActiveTable();

        TreeDumper treeDumper = new TreeDumper(tree, pageManager);
        List<Map.Entry<IndexKey, IndexValue>> merged1 = mergeTables(memTableManager.getSealedTables());
        treeDumper.dump(merged1, null);
        memTableManager.clearSealedTables();

        MemoryTable table2 = memTableManager.getActiveTable();
        for (int i = 300; i < 600; i++) {
            table2.put(IndexKey.orderedBytes(String.format("key%05d", i).getBytes()),
                      IndexValue.normal(("value" + i).getBytes()), null);
        }
        memTableManager.sealActiveTable();

        List<Map.Entry<IndexKey, IndexValue>> merged2 = mergeTables(memTableManager.getSealedTables());
        treeDumper.dump(merged2, null);
        memTableManager.clearSealedTables();

        SegmentLocation rootLocation = tree.getWriterRootLocation();
        assertNotNull(rootLocation);
        assertFalse(VirtualSegmentLocation.isVirtual(rootLocation));
    }

    @Test
    void testVirtualLocationResolutionOrder() throws Exception {
        MemoryTableManager memTableManager = new MemoryTableManager(1024 * 1024);
        PageManager pageManager = createPageManager();
        BPlusTree tree = new BPlusTree(pageManager);

        MemoryTable table1 = memTableManager.getActiveTable();
        for (int i = 0; i < 100; i++) {
            table1.put(IndexKey.orderedBytes(String.format("key%05d", i).getBytes()),
                      IndexValue.normal(("value" + i).getBytes()), null);
        }
        memTableManager.sealActiveTable();

        TreeDumper treeDumper = new TreeDumper(tree, pageManager);
        List<Map.Entry<IndexKey, IndexValue>> merged1 = mergeTables(memTableManager.getSealedTables());
        treeDumper.dump(merged1, null);
        treeDumper.promoteRoot();
        memTableManager.getAllTables().forEach(MemoryTable::setForClear);
        memTableManager.clearSealedTables();

        for (int i = 0; i < 100; i++) {
            IndexValue value = tree.search(IndexKey.orderedBytes(String.format("key%05d", i).getBytes()));
            assertNotNull(value, "Value for key" + i + " should not be null after first dump");
        }

        MemoryTable table2 = memTableManager.getActiveTable();
        for (int i = 100; i < 200; i++) {
            table2.put(IndexKey.orderedBytes(String.format("key%05d", i).getBytes()),
                      IndexValue.normal(("value" + i).getBytes()), null);
        }
        memTableManager.sealActiveTable();

        List<Map.Entry<IndexKey, IndexValue>> merged2 = mergeTables(memTableManager.getSealedTables());
        treeDumper.dump(merged2, null);
        treeDumper.promoteRoot();
        memTableManager.clearSealedTables();

        for (int i = 0; i < 200; i++) {
            IndexValue value = tree.search(IndexKey.orderedBytes(String.format("key%05d", i).getBytes()));
            assertNotNull(value, "Value for key" + i + " should not be null after second dump");
        }
    }

    @Test
    void testValidationForUnresolvedVirtualLocations() throws Exception {
        MemoryTableManager memTableManager = new MemoryTableManager(1024 * 1024);
        PageManager pageManager = createPageManager();
        BPlusTree tree = new BPlusTree(pageManager);

        MemoryTable table1 = memTableManager.getActiveTable();
        for (int i = 0; i < 150; i++) {
            table1.put(IndexKey.orderedBytes(String.format("key%05d", i).getBytes()),
                      IndexValue.normal(("value" + i).getBytes()), null);
        }
        memTableManager.sealActiveTable();

        TreeDumper treeDumper = new TreeDumper(tree, pageManager);
        List<Map.Entry<IndexKey, IndexValue>> merged1 = mergeTables(memTableManager.getSealedTables());
        treeDumper.dump(merged1, null);
        memTableManager.clearSealedTables();

        MemoryTable table2 = memTableManager.getActiveTable();
        for (int i = 150; i < 300; i++) {
            table2.put(IndexKey.orderedBytes(String.format("key%05d", i).getBytes()),
                      IndexValue.normal(("value" + i).getBytes()), null);
        }
        memTableManager.sealActiveTable();

        List<Map.Entry<IndexKey, IndexValue>> merged2 = mergeTables(memTableManager.getSealedTables());
        treeDumper.dump(merged2, null);
        memTableManager.clearSealedTables();

        SegmentLocation rootLocation = tree.getWriterRootLocation();
        assertNotNull(rootLocation);
        assertFalse(VirtualSegmentLocation.isVirtual(rootLocation));
    }

    @Test
    void testEmptyPageCleanupAfterTombstoneDeletion() throws Exception {
        MemoryTableManager memTableManager = new MemoryTableManager(1024 * 1024);
        PageManager pageManager = createPageManager();
        BPlusTree tree = new BPlusTree(pageManager);

        IndexKey key1 = IndexKey.orderedBytes("key1".getBytes());
        IndexValue value1 = IndexValue.normal("value1".getBytes());

        MemoryTable table1 = memTableManager.getActiveTable();
        table1.put(key1, value1, null);
        memTableManager.sealActiveTable();

        TreeDumper treeDumper = new TreeDumper(tree, pageManager);
        List<Map.Entry<IndexKey, IndexValue>> merged1 = mergeTables(memTableManager.getSealedTables());
        treeDumper.dump(merged1, null);
        memTableManager.clearSealedTables();

        MemoryTable table2 = memTableManager.getActiveTable();
        table2.put(key1, IndexValue.tombstone(), null);
        memTableManager.sealActiveTable();

        List<Map.Entry<IndexKey, IndexValue>> merged2 = mergeTables(memTableManager.getSealedTables());
        treeDumper.dump(merged2, null);
        memTableManager.clearSealedTables();

        IndexValue retrieved = tree.search(key1);
        assertNull(retrieved);
    }

    @Test
    void testMixedPutAndDeleteOperations() throws Exception {
        MemoryTableManager memTableManager = new MemoryTableManager(1024 * 1024);
        PageManager pageManager = createPageManager();
        BPlusTree tree = new BPlusTree(pageManager);
        TreeDumper treeDumper = new TreeDumper(tree, pageManager);

        MemoryTable table1 = memTableManager.getActiveTable();
        for (int i = 0; i < 50; i++) {
            table1.put(IndexKey.orderedBytes(String.format("key%05d", i).getBytes()),
                      IndexValue.normal(("value" + i).getBytes()), null);
        }
        memTableManager.sealActiveTable();

        List<Map.Entry<IndexKey, IndexValue>> merged1 = mergeTables(memTableManager.getSealedTables());
        treeDumper.dump(merged1, null);
        treeDumper.promoteRoot();
        memTableManager.clearSealedTables();

        MemoryTable table2 = memTableManager.getActiveTable();
        for (int i = 0; i < 25; i++) {
            table2.put(IndexKey.orderedBytes(String.format("key%05d", i).getBytes()),
                      IndexValue.tombstone(), null);
        }
        for (int i = 50; i < 75; i++) {
            table2.put(IndexKey.orderedBytes(String.format("key%05d", i).getBytes()),
                      IndexValue.normal(("value" + i).getBytes()), null);
        }
        memTableManager.sealActiveTable();

        List<Map.Entry<IndexKey, IndexValue>> merged2 = mergeTables(memTableManager.getSealedTables());
        treeDumper.dump(merged2, null);
        treeDumper.promoteRoot();
        memTableManager.clearSealedTables();

        for (int i = 0; i < 25; i++) {
            IndexValue value = tree.search(IndexKey.orderedBytes(String.format("key%05d", i).getBytes()));
            assertNull(value, "Key " + i + " should be deleted");
        }
        for (int i = 25; i < 50; i++) {
            IndexValue value = tree.search(IndexKey.orderedBytes(String.format("key%05d", i).getBytes()));
            assertNotNull(value, "Key " + i + " should exist");
        }
        for (int i = 50; i < 75; i++) {
            IndexValue value = tree.search(IndexKey.orderedBytes(String.format("key%05d", i).getBytes()));
            assertNotNull(value, "Key " + i + " should exist");
        }
    }

    @Test
    void testInsertAtLeftEdge() throws Exception {
        MemoryTableManager memTableManager = new MemoryTableManager(1024 * 1024);
        PageManager pageManager = createPageManager();
        BPlusTree tree = new BPlusTree(pageManager);
        TreeDumper treeDumper = new TreeDumper(tree, pageManager);

        MemoryTable table1 = memTableManager.getActiveTable();
        for (int i = 50; i < 100; i++) {
            table1.put(IndexKey.orderedBytes(String.format("key%05d", i).getBytes()),
                      IndexValue.normal(("value" + i).getBytes()), null);
        }
        memTableManager.sealActiveTable();

        List<Map.Entry<IndexKey, IndexValue>> merged1 = mergeTables(memTableManager.getSealedTables());
        treeDumper.dump(merged1, null);
        treeDumper.promoteRoot();
        memTableManager.clearSealedTables();

        MemoryTable table2 = memTableManager.getActiveTable();
        for (int i = 0; i < 50; i++) {
            table2.put(IndexKey.orderedBytes(String.format("key%05d", i).getBytes()),
                      IndexValue.normal(("value" + i).getBytes()), null);
        }
        memTableManager.sealActiveTable();

        List<Map.Entry<IndexKey, IndexValue>> merged2 = mergeTables(memTableManager.getSealedTables());
        treeDumper.dump(merged2, null);
        treeDumper.promoteRoot();
        memTableManager.clearSealedTables();

        for (int i = 0; i < 100; i++) {
            IndexValue value = tree.search(IndexKey.orderedBytes(String.format("key%05d", i).getBytes()));
            assertNotNull(value, "Key " + i + " should exist");
            assertArrayEquals(("value" + i).getBytes(), value.getValueData());
        }
    }

    @Test
    void testInsertAtRightEdge() throws Exception {
        MemoryTableManager memTableManager = new MemoryTableManager(1024 * 1024);
        PageManager pageManager = createPageManager();
        BPlusTree tree = new BPlusTree(pageManager);
        TreeDumper treeDumper = new TreeDumper(tree, pageManager);

        MemoryTable table1 = memTableManager.getActiveTable();
        for (int i = 0; i < 50; i++) {
            table1.put(IndexKey.orderedBytes(String.format("key%05d", i).getBytes()),
                      IndexValue.normal(("value" + i).getBytes()), null);
        }
        memTableManager.sealActiveTable();

        List<Map.Entry<IndexKey, IndexValue>> merged1 = mergeTables(memTableManager.getSealedTables());
        treeDumper.dump(merged1, null);
        treeDumper.promoteRoot();
        memTableManager.clearSealedTables();

        MemoryTable table2 = memTableManager.getActiveTable();
        for (int i = 50; i < 100; i++) {
            table2.put(IndexKey.orderedBytes(String.format("key%05d", i).getBytes()),
                      IndexValue.normal(("value" + i).getBytes()), null);
        }
        memTableManager.sealActiveTable();

        List<Map.Entry<IndexKey, IndexValue>> merged2 = mergeTables(memTableManager.getSealedTables());
        treeDumper.dump(merged2, null);
        treeDumper.promoteRoot();
        memTableManager.clearSealedTables();

        for (int i = 0; i < 100; i++) {
            IndexValue value = tree.search(IndexKey.orderedBytes(String.format("key%05d", i).getBytes()));
            assertNotNull(value, "Key " + i + " should exist");
            assertArrayEquals(("value" + i).getBytes(), value.getValueData());
        }
    }

    @Test
    void testDeleteSmallestKey() throws Exception {
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

        MemoryTable table2 = memTableManager.getActiveTable();
        table2.put(IndexKey.orderedBytes("key00000".getBytes()), IndexValue.tombstone(), null);
        table2.put(IndexKey.orderedBytes("key00001".getBytes()), IndexValue.tombstone(), null);
        table2.put(IndexKey.orderedBytes("key00002".getBytes()), IndexValue.tombstone(), null);
        memTableManager.sealActiveTable();

        List<Map.Entry<IndexKey, IndexValue>> merged2 = mergeTables(memTableManager.getSealedTables());
        treeDumper.dump(merged2, null);
        treeDumper.promoteRoot();
        memTableManager.clearSealedTables();

        for (int i = 0; i < 3; i++) {
            IndexValue value = tree.search(IndexKey.orderedBytes(String.format("key%05d", i).getBytes()));
            assertNull(value, "Key " + i + " should be deleted");
        }
        for (int i = 3; i < 100; i++) {
            IndexValue value = tree.search(IndexKey.orderedBytes(String.format("key%05d", i).getBytes()));
            assertNotNull(value, "Key " + i + " should exist");
        }
    }

    @Test
    void testDeleteLargestKey() throws Exception {
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

        MemoryTable table2 = memTableManager.getActiveTable();
        table2.put(IndexKey.orderedBytes("key00099".getBytes()), IndexValue.tombstone(), null);
        table2.put(IndexKey.orderedBytes("key00098".getBytes()), IndexValue.tombstone(), null);
        table2.put(IndexKey.orderedBytes("key00097".getBytes()), IndexValue.tombstone(), null);
        memTableManager.sealActiveTable();

        List<Map.Entry<IndexKey, IndexValue>> merged2 = mergeTables(memTableManager.getSealedTables());
        treeDumper.dump(merged2, null);
        treeDumper.promoteRoot();
        memTableManager.clearSealedTables();

        for (int i = 97; i < 100; i++) {
            IndexValue value = tree.search(IndexKey.orderedBytes(String.format("key%05d", i).getBytes()));
            assertNull(value, "Key " + i + " should be deleted");
        }
        for (int i = 0; i < 97; i++) {
            IndexValue value = tree.search(IndexKey.orderedBytes(String.format("key%05d", i).getBytes()));
            assertNotNull(value, "Key " + i + " should exist");
        }
    }

    @Test
    void testInsertKeySmallerThanExistingMin() throws Exception {
        MemoryTableManager memTableManager = new MemoryTableManager(1024 * 1024);
        PageManager pageManager = createPageManager();
        BPlusTree tree = new BPlusTree(pageManager);
        TreeDumper treeDumper = new TreeDumper(tree, pageManager);

        MemoryTable table1 = memTableManager.getActiveTable();
        for (int i = 50; i < 100; i++) {
            table1.put(IndexKey.orderedBytes(String.format("key%05d", i).getBytes()),
                      IndexValue.normal(("value" + i).getBytes()), null);
        }
        memTableManager.sealActiveTable();

        List<Map.Entry<IndexKey, IndexValue>> merged1 = mergeTables(memTableManager.getSealedTables());
        treeDumper.dump(merged1, null);
        treeDumper.promoteRoot();
        memTableManager.clearSealedTables();

        MemoryTable table2 = memTableManager.getActiveTable();
        table2.put(IndexKey.orderedBytes("key00000".getBytes()), IndexValue.normal("value0".getBytes()), null);
        memTableManager.sealActiveTable();

        List<Map.Entry<IndexKey, IndexValue>> merged2 = mergeTables(memTableManager.getSealedTables());
        treeDumper.dump(merged2, null);
        treeDumper.promoteRoot();
        memTableManager.clearSealedTables();

        IndexValue value0 = tree.search(IndexKey.orderedBytes("key00000".getBytes()));
        assertNotNull(value0, "Key 0 should exist");
        assertArrayEquals("value0".getBytes(), value0.getValueData());

        for (int i = 50; i < 100; i++) {
            IndexValue value = tree.search(IndexKey.orderedBytes(String.format("key%05d", i).getBytes()));
            assertNotNull(value, "Key " + i + " should exist");
        }
    }

    @Test
    void testInsertKeyLargerThanExistingMax() throws Exception {
        MemoryTableManager memTableManager = new MemoryTableManager(1024 * 1024);
        PageManager pageManager = createPageManager();
        BPlusTree tree = new BPlusTree(pageManager);
        TreeDumper treeDumper = new TreeDumper(tree, pageManager);

        MemoryTable table1 = memTableManager.getActiveTable();
        for (int i = 0; i < 50; i++) {
            table1.put(IndexKey.orderedBytes(String.format("key%05d", i).getBytes()),
                      IndexValue.normal(("value" + i).getBytes()), null);
        }
        memTableManager.sealActiveTable();

        List<Map.Entry<IndexKey, IndexValue>> merged1 = mergeTables(memTableManager.getSealedTables());
        treeDumper.dump(merged1, null);
        treeDumper.promoteRoot();
        memTableManager.clearSealedTables();

        MemoryTable table2 = memTableManager.getActiveTable();
        table2.put(IndexKey.orderedBytes("key00099".getBytes()), IndexValue.normal("value99".getBytes()), null);
        memTableManager.sealActiveTable();

        List<Map.Entry<IndexKey, IndexValue>> merged2 = mergeTables(memTableManager.getSealedTables());
        treeDumper.dump(merged2, null);
        treeDumper.promoteRoot();
        memTableManager.clearSealedTables();

        IndexValue value99 = tree.search(IndexKey.orderedBytes("key00099".getBytes()));
        assertNotNull(value99, "Key 99 should exist");
        assertArrayEquals("value99".getBytes(), value99.getValueData());

        for (int i = 0; i < 50; i++) {
            IndexValue value = tree.search(IndexKey.orderedBytes(String.format("key%05d", i).getBytes()));
            assertNotNull(value, "Key " + i + " should exist");
        }
    }

    @Test
    void testMixedOperationsAtEdges() throws Exception {
        MemoryTableManager memTableManager = new MemoryTableManager(1024 * 1024);
        PageManager pageManager = createPageManager();
        BPlusTree tree = new BPlusTree(pageManager);
        TreeDumper treeDumper = new TreeDumper(tree, pageManager);

        MemoryTable table1 = memTableManager.getActiveTable();
        for (int i = 20; i < 80; i++) {
            table1.put(IndexKey.orderedBytes(String.format("key%05d", i).getBytes()),
                      IndexValue.normal(("value" + i).getBytes()), null);
        }
        memTableManager.sealActiveTable();

        List<Map.Entry<IndexKey, IndexValue>> merged1 = mergeTables(memTableManager.getSealedTables());
        treeDumper.dump(merged1, null);
        treeDumper.promoteRoot();
        memTableManager.clearSealedTables();

        MemoryTable table2 = memTableManager.getActiveTable();
        for (int i = 0; i < 20; i++) {
            table2.put(IndexKey.orderedBytes(String.format("key%05d", i).getBytes()),
                      IndexValue.normal(("value" + i).getBytes()), null);
        }
        for (int i = 80; i < 100; i++) {
            table2.put(IndexKey.orderedBytes(String.format("key%05d", i).getBytes()),
                      IndexValue.normal(("value" + i).getBytes()), null);
        }
        table2.put(IndexKey.orderedBytes("key00020".getBytes()), IndexValue.tombstone(), null);
        table2.put(IndexKey.orderedBytes("key00079".getBytes()), IndexValue.tombstone(), null);
        memTableManager.sealActiveTable();

        List<Map.Entry<IndexKey, IndexValue>> merged2 = mergeTables(memTableManager.getSealedTables());
        treeDumper.dump(merged2, null);
        treeDumper.promoteRoot();
        memTableManager.clearSealedTables();

        for (int i = 0; i < 20; i++) {
            IndexValue value = tree.search(IndexKey.orderedBytes(String.format("key%05d", i).getBytes()));
            assertNotNull(value, "Key " + i + " should exist");
        }
        
        IndexValue value20 = tree.search(IndexKey.orderedBytes("key00020".getBytes()));
        assertNull(value20, "Key 20 should be deleted");
        
        IndexValue value79 = tree.search(IndexKey.orderedBytes("key00079".getBytes()));
        assertNull(value79, "Key 79 should be deleted");
        
        for (int i = 21; i < 79; i++) {
            IndexValue value = tree.search(IndexKey.orderedBytes(String.format("key%05d", i).getBytes()));
            assertNotNull(value, "Key " + i + " should exist");
        }
        
        for (int i = 80; i < 100; i++) {
            IndexValue value = tree.search(IndexKey.orderedBytes(String.format("key%05d", i).getBytes()));
            assertNotNull(value, "Key " + i + " should exist");
        }
    }

    @Test
    void testPageSplitWithMinKeyChange() throws Exception {
        MemoryTableManager memTableManager = new MemoryTableManager(1024 * 1024);
        PageManager pageManager = createPageManager();
        BPlusTree tree = new BPlusTree(pageManager, 512, 512);
        TreeDumper treeDumper = new TreeDumper(tree, pageManager);

        MemoryTable table1 = memTableManager.getActiveTable();
        for (int i = 0; i < 10; i++) {
            table1.put(IndexKey.orderedBytes(String.format("key%05d", i).getBytes()),
                      IndexValue.normal(new byte[100]), null);
        }
        memTableManager.sealActiveTable();

        List<Map.Entry<IndexKey, IndexValue>> merged1 = mergeTables(memTableManager.getSealedTables());
        treeDumper.dump(merged1, null);
        treeDumper.promoteRoot();
        memTableManager.clearSealedTables();

        MemoryTable table2 = memTableManager.getActiveTable();
        table2.put(IndexKey.orderedBytes("key00000".getBytes()), IndexValue.tombstone(), null);
        table2.put(IndexKey.orderedBytes("key00001".getBytes()), IndexValue.tombstone(), null);
        for (int i = 10; i < 20; i++) {
            table2.put(IndexKey.orderedBytes(String.format("key%05d", i).getBytes()),
                      IndexValue.normal(new byte[100]), null);
        }
        memTableManager.sealActiveTable();

        List<Map.Entry<IndexKey, IndexValue>> merged2 = mergeTables(memTableManager.getSealedTables());
        treeDumper.dump(merged2, null);
        treeDumper.promoteRoot();
        memTableManager.clearSealedTables();

        assertNull(tree.search(IndexKey.orderedBytes("key00000".getBytes())));
        assertNull(tree.search(IndexKey.orderedBytes("key00001".getBytes())));
        
        for (int i = 2; i < 20; i++) {
            IndexValue value = tree.search(IndexKey.orderedBytes(String.format("key%05d", i).getBytes()));
            assertNotNull(value, "Key " + i + " should exist");
        }
    }

    @Test
    void testMultiplePageSplitsWithEdgeInserts() throws Exception {
        MemoryTableManager memTableManager = new MemoryTableManager(1024 * 1024);
        PageManager pageManager = createPageManager();
        BPlusTree tree = new BPlusTree(pageManager, 1024, 1024);
        TreeDumper treeDumper = new TreeDumper(tree, pageManager);

        MemoryTable table1 = memTableManager.getActiveTable();
        for (int i = 100; i < 200; i++) {
            table1.put(IndexKey.orderedBytes(String.format("key%05d", i).getBytes()),
                      IndexValue.normal(new byte[50]), null);
        }
        memTableManager.sealActiveTable();

        List<Map.Entry<IndexKey, IndexValue>> merged1 = mergeTables(memTableManager.getSealedTables());
        treeDumper.dump(merged1, null);
        treeDumper.promoteRoot();
        memTableManager.clearSealedTables();

        MemoryTable table2 = memTableManager.getActiveTable();
        for (int i = 0; i < 100; i++) {
            table2.put(IndexKey.orderedBytes(String.format("key%05d", i).getBytes()),
                      IndexValue.normal(new byte[50]), null);
        }
        for (int i = 200; i < 300; i++) {
            table2.put(IndexKey.orderedBytes(String.format("key%05d", i).getBytes()),
                      IndexValue.normal(new byte[50]), null);
        }
        memTableManager.sealActiveTable();

        List<Map.Entry<IndexKey, IndexValue>> merged2 = mergeTables(memTableManager.getSealedTables());
        treeDumper.dump(merged2, null);
        treeDumper.promoteRoot();
        memTableManager.clearSealedTables();

        for (int i = 0; i < 300; i++) {
            IndexValue value = tree.search(IndexKey.orderedBytes(String.format("key%05d", i).getBytes()));
            assertNotNull(value, "Key " + i + " should exist");
        }
    }

    @Test
    void testDeleteAllKeysInPage() throws Exception {
        MemoryTableManager memTableManager = new MemoryTableManager(1024 * 1024);
        PageManager pageManager = createPageManager();
        BPlusTree tree = new BPlusTree(pageManager);
        TreeDumper treeDumper = new TreeDumper(tree, pageManager);

        MemoryTable table1 = memTableManager.getActiveTable();
        for (int i = 0; i < 10; i++) {
            table1.put(IndexKey.orderedBytes(String.format("key%05d", i).getBytes()),
                      IndexValue.normal(("value" + i).getBytes()), null);
        }
        memTableManager.sealActiveTable();

        List<Map.Entry<IndexKey, IndexValue>> merged1 = mergeTables(memTableManager.getSealedTables());
        treeDumper.dump(merged1, null);
        treeDumper.promoteRoot();
        memTableManager.clearSealedTables();

        MemoryTable table2 = memTableManager.getActiveTable();
        for (int i = 0; i < 10; i++) {
            table2.put(IndexKey.orderedBytes(String.format("key%05d", i).getBytes()),
                      IndexValue.tombstone(), null);
        }
        memTableManager.sealActiveTable();

        List<Map.Entry<IndexKey, IndexValue>> merged2 = mergeTables(memTableManager.getSealedTables());
        treeDumper.dump(merged2, null);
        memTableManager.clearSealedTables();

        tree.promoteRoot();
        for (int i = 0; i < 10; i++) {
            IndexValue value = tree.search(IndexKey.orderedBytes(String.format("key%05d", i).getBytes()));
            assertNull(value, "Key " + i + " should be deleted");
        }
    }

    @Test
    void testUpdateValueAtEdgeKeys() throws Exception {
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

        MemoryTable table2 = memTableManager.getActiveTable();
        table2.put(IndexKey.orderedBytes("key00000".getBytes()), 
                   IndexValue.normal("updated_value0".getBytes()), null);
        table2.put(IndexKey.orderedBytes("key00099".getBytes()), 
                   IndexValue.normal("updated_value99".getBytes()), null);
        memTableManager.sealActiveTable();

        List<Map.Entry<IndexKey, IndexValue>> merged2 = mergeTables(memTableManager.getSealedTables());
        treeDumper.dump(merged2, null);
        treeDumper.promoteRoot();
        memTableManager.clearSealedTables();

        IndexValue value0 = tree.search(IndexKey.orderedBytes("key00000".getBytes()));
        assertNotNull(value0);
        assertArrayEquals("updated_value0".getBytes(), value0.getValueData());

        IndexValue value99 = tree.search(IndexKey.orderedBytes("key00099".getBytes()));
        assertNotNull(value99);
        assertArrayEquals("updated_value99".getBytes(), value99.getValueData());

        for (int i = 1; i < 99; i++) {
            IndexValue value = tree.search(IndexKey.orderedBytes(String.format("key%05d", i).getBytes()));
            assertNotNull(value, "Key " + i + " should exist");
            assertArrayEquals(("value" + i).getBytes(), value.getValueData());
        }
    }

    @Test
    void testNoDuplicateEntriesAfterPageSplit() throws Exception {
        MemoryTableManager memTableManager = new MemoryTableManager(1024 * 1024);
        PageManager pageManager = createPageManager();
        BPlusTree tree = new BPlusTree(pageManager, 1024, 1024);
        TreeDumper treeDumper = new TreeDumper(tree, pageManager);

        MemoryTable table1 = memTableManager.getActiveTable();
        for (int i = 0; i < 50; i++) {
            table1.put(IndexKey.orderedBytes(String.format("key%05d", i).getBytes()),
                      IndexValue.normal(new byte[100]), null);
        }
        memTableManager.sealActiveTable();

        List<Map.Entry<IndexKey, IndexValue>> merged1 = mergeTables(memTableManager.getSealedTables());
        treeDumper.dump(merged1, null);
        treeDumper.promoteRoot();
        memTableManager.clearSealedTables();

        MemoryTable table2 = memTableManager.getActiveTable();
        for (int i = 50; i < 150; i++) {
            table2.put(IndexKey.orderedBytes(String.format("key%05d", i).getBytes()),
                      IndexValue.normal(new byte[100]), null);
        }
        memTableManager.sealActiveTable();

        List<Map.Entry<IndexKey, IndexValue>> merged2 = mergeTables(memTableManager.getSealedTables());
        treeDumper.dump(merged2, null);
        treeDumper.promoteRoot();
        memTableManager.clearSealedTables();

        Page root = tree.getWriterRoot();
        assertNotNull(root, "Root page should exist");
        assertFalse(root.isLeaf(), "Root should be an index page");
        
        List<IndexPair> rootEntries = root.getAllEntries();
        Set<IndexKey> uniqueKeys = new HashSet<>();
        for (IndexPair entry : rootEntries) {
            IndexKey key = entry.key();
            assertFalse(uniqueKeys.contains(key), 
                "Duplicate key found in root page: " + key + ". Total entries: " + rootEntries.size());
            uniqueKeys.add(key);
        }

        for (int i = 0; i < 150; i++) {
            IndexValue value = tree.search(IndexKey.orderedBytes(String.format("key%05d", i).getBytes()));
            assertNotNull(value, "Key " + i + " should exist");
        }
    }

    @Test
    void testNoDuplicateEntriesAfterMultipleSplits() throws Exception {
        MemoryTableManager memTableManager = new MemoryTableManager(1024 * 1024);
        PageManager pageManager = createPageManager();
        BPlusTree tree = new BPlusTree(pageManager, 512, 512);
        TreeDumper treeDumper = new TreeDumper(tree, pageManager);

        MemoryTable table1 = memTableManager.getActiveTable();
        for (int i = 0; i < 20; i++) {
            table1.put(IndexKey.orderedBytes(String.format("key%05d", i).getBytes()),
                      IndexValue.normal(new byte[100]), null);
        }
        memTableManager.sealActiveTable();

        List<Map.Entry<IndexKey, IndexValue>> merged1 = mergeTables(memTableManager.getSealedTables());
        treeDumper.dump(merged1, null);
        treeDumper.promoteRoot();
        memTableManager.clearSealedTables();

        for (int batch = 0; batch < 5; batch++) {
            MemoryTable table = memTableManager.getActiveTable();
            for (int i = 20 + batch * 30; i < 20 + (batch + 1) * 30; i++) {
                table.put(IndexKey.orderedBytes(String.format("key%05d", i).getBytes()),
                          IndexValue.normal(new byte[100]), null);
            }
            memTableManager.sealActiveTable();

            List<Map.Entry<IndexKey, IndexValue>> merged = mergeTables(memTableManager.getSealedTables());
            treeDumper.dump(merged, null);
            treeDumper.promoteRoot();
//            memTableManager.getSealedTables().forEach(MemoryTable::setForClear);
            memTableManager.clearSealedTables();

            verifyNoDuplicateEntries(tree);
        }

        int totalKeys = 20 + 5 * 30;
        for (int i = 0; i < totalKeys; i++) {
            IndexValue value = tree.search(IndexKey.orderedBytes(String.format("key%05d", i).getBytes()));
            assertNotNull(value, "Key " + i + " should exist");
        }
    }

    @Test
    void testNoDuplicateEntriesAfterMinKeyChange() throws Exception {
        MemoryTableManager memTableManager = new MemoryTableManager(1024 * 1024);
        PageManager pageManager = createPageManager();
        BPlusTree tree = new BPlusTree(pageManager, 512, 512);
        TreeDumper treeDumper = new TreeDumper(tree, pageManager);

        MemoryTable table1 = memTableManager.getActiveTable();
        for (int i = 10; i < 30; i++) {
            table1.put(IndexKey.orderedBytes(String.format("key%05d", i).getBytes()),
                      IndexValue.normal(new byte[100]), null);
        }
        memTableManager.sealActiveTable();

        List<Map.Entry<IndexKey, IndexValue>> merged1 = mergeTables(memTableManager.getSealedTables());
        treeDumper.dump(merged1, null);
        treeDumper.promoteRoot();
        memTableManager.clearSealedTables();

        MemoryTable table2 = memTableManager.getActiveTable();
        for (int i = 0; i < 10; i++) {
            table2.put(IndexKey.orderedBytes(String.format("key%05d", i).getBytes()),
                      IndexValue.normal(new byte[100]), null);
        }
        for (int i = 30; i < 50; i++) {
            table2.put(IndexKey.orderedBytes(String.format("key%05d", i).getBytes()),
                      IndexValue.normal(new byte[100]), null);
        }
        memTableManager.sealActiveTable();

        List<Map.Entry<IndexKey, IndexValue>> merged2 = mergeTables(memTableManager.getSealedTables());
        treeDumper.dump(merged2, null);
        treeDumper.promoteRoot();
        memTableManager.clearSealedTables();

        verifyNoDuplicateEntries(tree);

        for (int i = 0; i < 50; i++) {
            IndexValue value = tree.search(IndexKey.orderedBytes(String.format("key%05d", i).getBytes()));
            assertNotNull(value, "Key " + i + " should exist");
        }
    }

    private void verifyNoDuplicateEntries(BPlusTree tree) {
        Page root = tree.getWriterRoot();
        if (root == null || root.isLeaf()) {
            return;
        }

        verifyNoDuplicateEntriesInPage(root, tree.getHeight() - 1);
    }

    private void verifyNoDuplicateEntriesInPage(Page page, int level) {
        if (page == null || page.isLeaf()) {
            return;
        }

        List<IndexPair> entries = page.getAllEntries();
        Set<IndexKey> uniqueKeys = new HashSet<>();
        
        for (IndexPair entry : entries) {
            IndexKey key = entry.key();
            assertFalse(uniqueKeys.contains(key), 
                String.format("Duplicate key found in index page at level %d: %s. Total entries: %d, Unique keys: %d",
                    level, key, entries.size(), uniqueKeys.size()));
            uniqueKeys.add(key);
        }

        assertEquals(entries.size(), uniqueKeys.size(), 
            String.format("Page at level %d has duplicate entries. Total: %d, Unique: %d",
                level, entries.size(), uniqueKeys.size()));
    }

    @Test
    void testMergeAfterMassiveDeletion() throws Exception {
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

        for (int i = 0; i < 1000; i++) {
            IndexValue value = tree.search(IndexKey.orderedBytes(String.format("key%05d", i).getBytes()));
            assertNotNull(value, "Key " + i + " should exist after initial dump");
        }

        List<Integer> allKeys = new ArrayList<>();
        for (int i = 0; i < 1000; i++) {
            allKeys.add(i);
        }
        Collections.shuffle(allKeys, new Random(42));
        
        Set<Integer> deletedKeys = new HashSet<>();
        Set<Integer> remainingKeys = new HashSet<>();
        
        for (int i = 0; i < 750; i++) {
            deletedKeys.add(allKeys.get(i));
        }
        for (int i = 750; i < 1000; i++) {
            remainingKeys.add(allKeys.get(i));
        }

        MemoryTable table2 = memTableManager.getActiveTable();
        for (Integer keyIndex : deletedKeys) {
            table2.put(IndexKey.orderedBytes(String.format("key%05d", keyIndex).getBytes()),
                      IndexValue.tombstone(), null);
        }
        memTableManager.sealActiveTable();

        List<Map.Entry<IndexKey, IndexValue>> merged2 = mergeTables(memTableManager.getSealedTables());
        treeDumper.dump(merged2, null);
        treeDumper.promoteRoot();
        memTableManager.clearSealedTables();

        for (Integer keyIndex : deletedKeys) {
            IndexValue value = tree.search(IndexKey.orderedBytes(String.format("key%05d", keyIndex).getBytes()));
            assertNull(value, "Key " + keyIndex + " should be deleted");
        }

        for (Integer keyIndex : remainingKeys) {
            IndexValue value = tree.search(IndexKey.orderedBytes(String.format("key%05d", keyIndex).getBytes()));
            assertNotNull(value, "Key " + keyIndex + " should still exist");
        }

        verifyPageMergeEfficiency(tree, pageManager);
    }

    private void verifyPageMergeEfficiency(BPlusTree tree, PageManager pageManager) {
        Page root = tree.getWriterRoot();
        if (root == null) {
            return;
        }

        int height = tree.getHeight();
        int maxEntries = tree.getCapacityConfig().getMaxEntries(Page.PageType.LEAF);
        
        if (maxEntries == Integer.MAX_VALUE) {
            int maxSize = tree.getCapacityConfig().getMaxSize(Page.PageType.LEAF);
            int mergeThreshold = maxSize / 3;
            verifyPageMergeBySize(root, height - 1, mergeThreshold, tree, pageManager);
        } else {
            int mergeThreshold = maxEntries / 3;
            verifyPageMergeByEntryCount(root, height - 1, mergeThreshold, tree, pageManager);
        }
    }

    private void verifyPageMergeBySize(Page page, int level, int mergeThreshold, BPlusTree tree, PageManager pageManager) {
        if (page == null) {
            return;
        }

        if (page.isLeaf()) {
            int usedSize = page.getUsedSize();
            assertTrue(usedSize >= mergeThreshold || page.getPageId() == 0,
                String.format("Leaf page at level %d is underfull: usedSize=%d, threshold=%d",
                    level, usedSize, mergeThreshold));
        } else {
            List<IndexPair> entries = page.getAllEntries();
            for (IndexPair entry : entries) {
                if (entry instanceof IndexPair.LocationEntry locationEntry) {
                    SegmentLocation location = locationEntry.location();
                    if (location != null && !VirtualSegmentLocation.isVirtual(location)) {
                        Page childPage = pageManager.getPage(location);
                        verifyPageMergeBySize(childPage, level - 1, mergeThreshold, tree, pageManager);
                    }
                }
            }
        }
    }

    private void verifyPageMergeByEntryCount(Page page, int level, int mergeThreshold, BPlusTree tree, PageManager pageManager) {
        if (page == null) {
            return;
        }

        if (page.isLeaf()) {
            int entryCount = page.getEntryCount();
            if (entryCount < mergeThreshold && page.getPageId() != 0) {
                System.out.println(String.format(
                    "WARNING: Leaf page at level %d is underfull: entryCount=%d, threshold=%d, pageId=%d",
                    level, entryCount, mergeThreshold, page.getPageId()));
            }
        } else {
            List<IndexPair> entries = page.getAllEntries();
            for (IndexPair entry : entries) {
                if (entry instanceof IndexPair.LocationEntry locationEntry) {
                    SegmentLocation location = locationEntry.location();
                    if (location != null && !VirtualSegmentLocation.isVirtual(location)) {
                        Page childPage = pageManager.getPage(location);
                        verifyPageMergeByEntryCount(childPage, level - 1, mergeThreshold, tree, pageManager);
                    }
                }
            }
        }
    }

    @Test
    void testMultiLevelTreePropagation() throws Exception {
        PageManager pageManager = createPageManager();
        BPlusTree tree = new BPlusTree(pageManager);
        TreeDumper treeDumper = new TreeDumper(tree, pageManager);

        List<Map.Entry<IndexKey, IndexValue>> entries = new ArrayList<>();
        for (int i = 0; i < 100; i++) {
            IndexKey key = IndexKey.orderedBytes(String.format("key%04d", i).getBytes());
            IndexValue value = IndexValue.normal(String.format("value%04d", i).getBytes());
            entries.add(new AbstractMap.SimpleEntry<>(key, value));
        }

        treeDumper.dump(entries, null);
        treeDumper.promoteRoot();

        assertTrue(tree.getHeight() >= 2, "Tree should have at least 2 levels for 100 entries");
        
        for (int i = 0; i < 100; i++) {
            IndexKey key = IndexKey.orderedBytes(String.format("key%04d", i).getBytes());
            IndexValue retrieved = tree.search(key);
            assertNotNull(retrieved, "Should find key" + String.format("%04d", i));
            assertArrayEquals(String.format("value%04d", i).getBytes(), retrieved.getValueData());
        }
    }

    @Test
    void testMultiLevelTreeWithSplits() throws Exception {
        PageManager pageManager = createPageManager();
        BPlusTree tree = new BPlusTree(pageManager);
        TreeDumper treeDumper = new TreeDumper(tree, pageManager);

        List<Map.Entry<IndexKey, IndexValue>> batch1 = new ArrayList<>();
        for (int i = 0; i < 50; i++) {
            IndexKey key = IndexKey.orderedBytes(String.format("key%04d", i).getBytes());
            IndexValue value = IndexValue.normal(String.format("value%04d", i).getBytes());
            batch1.add(new AbstractMap.SimpleEntry<>(key, value));
        }

        treeDumper.dump(batch1, null);
        treeDumper.promoteRoot();
        int heightAfterBatch1 = tree.getHeight();

        List<Map.Entry<IndexKey, IndexValue>> batch2 = new ArrayList<>();
        for (int i = 50; i < 150; i++) {
            IndexKey key = IndexKey.orderedBytes(String.format("key%04d", i).getBytes());
            IndexValue value = IndexValue.normal(String.format("value%04d", i).getBytes());
            batch2.add(new AbstractMap.SimpleEntry<>(key, value));
        }

        treeDumper.dump(batch2, null);
        treeDumper.promoteRoot();
        int heightAfterBatch2 = tree.getHeight();

        assertTrue(heightAfterBatch2 >= heightAfterBatch1, 
            "Tree height should not decrease after adding more entries");

        for (int i = 0; i < 150; i++) {
            IndexKey key = IndexKey.orderedBytes(String.format("key%04d", i).getBytes());
            IndexValue retrieved = tree.search(key);
            assertNotNull(retrieved, "Should find key" + String.format("%04d", i));
            assertArrayEquals(String.format("value%04d", i).getBytes(), retrieved.getValueData());
        }
    }

    @Test
    void testMultiLevelTreeWithDeletes() throws Exception {
        PageManager pageManager = createPageManager();
        BPlusTree tree = new BPlusTree(pageManager);
        TreeDumper treeDumper = new TreeDumper(tree, pageManager);

        List<Map.Entry<IndexKey, IndexValue>> entries = new ArrayList<>();
        for (int i = 0; i < 100; i++) {
            IndexKey key = IndexKey.orderedBytes(String.format("key%04d", i).getBytes());
            IndexValue value = IndexValue.normal(String.format("value%04d", i).getBytes());
            entries.add(new AbstractMap.SimpleEntry<>(key, value));
        }

        treeDumper.dump(entries, null);
        treeDumper.promoteRoot();

        List<Map.Entry<IndexKey, IndexValue>> deletes = new ArrayList<>();
        for (int i = 0; i < 50; i++) {
            IndexKey key = IndexKey.orderedBytes(String.format("key%04d", i).getBytes());
            deletes.add(new AbstractMap.SimpleEntry<>(key, IndexValue.tombstone()));
        }

        treeDumper.dump(deletes, null);
        treeDumper.promoteRoot();

        for (int i = 0; i < 50; i++) {
            IndexKey key = IndexKey.orderedBytes(String.format("key%04d", i).getBytes());
            IndexValue retrieved = tree.search(key);
            assertNull(retrieved, "Should not find deleted key" + String.format("%04d", i));
        }

        for (int i = 50; i < 100; i++) {
            IndexKey key = IndexKey.orderedBytes(String.format("key%04d", i).getBytes());
            IndexValue retrieved = tree.search(key);
            assertNotNull(retrieved, "Should find key" + String.format("%04d", i));
            assertArrayEquals(String.format("value%04d", i).getBytes(), retrieved.getValueData());
        }
    }
}
