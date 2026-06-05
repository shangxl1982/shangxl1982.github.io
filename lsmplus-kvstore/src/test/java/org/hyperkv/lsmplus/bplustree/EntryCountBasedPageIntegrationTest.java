package org.hyperkv.lsmplus.bplustree;

import org.hyperkv.lsmplus.api.model.IndexKey;
import org.hyperkv.lsmplus.api.model.IndexValue;
import org.hyperkv.lsmplus.bplustree.page.Page;
import org.hyperkv.lsmplus.exception.KVStoreException;
import org.hyperkv.lsmplus.memory.MemoryTable;
import org.hyperkv.lsmplus.memory.MemoryTableManager;
import org.hyperkv.lsmplus.storage.ChunkManager;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.io.TempDir;

import java.io.File;
import java.nio.file.Path;
import java.util.*;

import static org.junit.jupiter.api.Assertions.*;

class EntryCountBasedPageIntegrationTest {

    @TempDir
    Path tempDir;

    private PageManager pageManager;
    private BPlusTree tree;
    private MemoryTableManager memTableManager;

    private void setUpWithConfig(PageCapacityConfig config) throws Exception {
        File storageDir = tempDir.toFile();
        UUID ownerId = UUID.randomUUID();
        UUID namespaceId = UUID.randomUUID();
        ChunkManager chunkManager = new ChunkManager(storageDir.getAbsolutePath(), ownerId, namespaceId);
        pageManager = new PageManager(chunkManager, PageCapacityConfig.DEFAULT);
        tree = new BPlusTree(pageManager, config);
        memTableManager = new MemoryTableManager(10 * 1024 * 1024);
    }

    private List<Map.Entry<IndexKey, IndexValue>> mergeTables(List<MemoryTable> sealedTables) {
        TreeMap<IndexKey, IndexValue> merged = new TreeMap<>();
        for (MemoryTable table : sealedTables) {
            merged.putAll(table.getData());
        }
        return new ArrayList<>(merged.entrySet());
    }

    private void dumpSealedTables(TreeDumper treeDumper) throws KVStoreException {
        List<Map.Entry<IndexKey, IndexValue>> merged = mergeTables(memTableManager.getSealedTables());
        treeDumper.dump(merged, null);
        treeDumper.promoteRoot();
        memTableManager.clearSealedTables();
    }

    @Test
    void testEntryCountBasedPages3000Keys() throws Exception {
        int leafMaxEntries = 10;
        int indexMaxEntries = 20;
        int numKeys = 3000;
        
        PageCapacityConfig config = PageCapacityConfig.entryCountBased(leafMaxEntries, indexMaxEntries);
        setUpWithConfig(config);

        TreeMap<String, String> expectedData = new TreeMap<>();

        MemoryTable table = memTableManager.getActiveTable();
        for (int i = 0; i < numKeys; i++) {
            String key = String.format("key-%05d", i);
            String value = "value-" + i + "-" + UUID.randomUUID().toString().substring(0, 8);
            expectedData.put(key, value);
            table.put(
                IndexKey.orderedBytes(key.getBytes()),
                IndexValue.normal(value.getBytes()),
                null
            );
        }
        memTableManager.sealActiveTable();

        TreeDumper treeDumper = new TreeDumper(tree, pageManager);
        dumpSealedTables(treeDumper);

        assertNotNull(tree.getWriterRootLocation(), "Tree should have a root after dump");
        assertTrue(tree.getHeight() >= 2, "Tree height should be at least 2 with 3000 keys and 10 entries per leaf");

        for (Map.Entry<String, String> entry : expectedData.entrySet()) {
            IndexKey key = IndexKey.orderedBytes(entry.getKey().getBytes());
            IndexValue value = tree.search(key);
            assertNotNull(value, "Key should be found: " + entry.getKey());
            assertArrayEquals(
                entry.getValue().getBytes(),
                value.getValueData(),
                "Value mismatch for key: " + entry.getKey()
            );
        }
    }

    @Test
    void testTenDumpCyclesWithUpdates() throws Exception {
        int leafMaxEntries = 10;
        int indexMaxEntries = 20;
        int numKeys = 300;
        int numCycles = 10;
        
        PageCapacityConfig config = PageCapacityConfig.entryCountBased(leafMaxEntries, indexMaxEntries);
        setUpWithConfig(config);

        TreeMap<String, String> expectedData = new TreeMap<>();
        Map<String, Integer> keyUpdateCount = new HashMap<>();
        TreeDumper treeDumper = new TreeDumper(tree, pageManager);

        for (int cycle = 0; cycle < numCycles; cycle++) {
            MemoryTable table = memTableManager.getActiveTable();
            
            for (int i = 0; i < numKeys; i++) {
                String key = String.format("cycle-key-%05d", i);
                String value = "cycle-" + cycle + "-value-" + i + "-" + UUID.randomUUID().toString().substring(0, 8);
                expectedData.put(key, value);
                keyUpdateCount.merge(key, 1, Integer::sum);
                table.put(
                    IndexKey.orderedBytes(key.getBytes()),
                    IndexValue.normal(value.getBytes()),
                    null
                );
            }

            memTableManager.sealActiveTable();
            dumpSealedTables(treeDumper);

            for (Map.Entry<String, String> entry : expectedData.entrySet()) {
                IndexKey key = IndexKey.orderedBytes(entry.getKey().getBytes());
                IndexValue value = tree.search(key);
                assertNotNull(value, "Key should be found after cycle " + cycle + ": " + entry.getKey());
                assertArrayEquals(
                    entry.getValue().getBytes(),
                    value.getValueData(),
                    "Value mismatch after cycle " + cycle + " for key: " + entry.getKey()
                );
            }
        }

        assertEquals(numKeys, expectedData.size(), "Should have " + numKeys + " unique keys");
        
        for (Map.Entry<String, String> entry : expectedData.entrySet()) {
            IndexKey key = IndexKey.orderedBytes(entry.getKey().getBytes());
            IndexValue value = tree.search(key);
            assertNotNull(value, "Key should be found after all cycles: " + entry.getKey());
            assertArrayEquals(
                entry.getValue().getBytes(),
                value.getValueData(),
                "Final value mismatch for key: " + entry.getKey()
            );
        }
    }

    @Test
    void testTenDumpCyclesWithInsertsDeletesAndUpdates() throws Exception {
        int leafMaxEntries = 10;
        int indexMaxEntries = 20;
        int numKeys = 200;
        int numCycles = 10;
        
        PageCapacityConfig config = PageCapacityConfig.entryCountBased(leafMaxEntries, indexMaxEntries);
        setUpWithConfig(config);

        TreeMap<String, String> expectedData = new TreeMap<>();
        Set<String> deletedKeys = new HashSet<>();
        Random random = new Random(42);
        TreeDumper treeDumper = new TreeDumper(tree, pageManager);

        for (int cycle = 0; cycle < numCycles; cycle++) {
            MemoryTable table = memTableManager.getActiveTable();
            
            if (cycle % 3 == 0) {
                for (int i = 0; i < numKeys; i++) {
                    String key = String.format("mixed-key-%05d", i);
                    String value = "cycle-" + cycle + "-value-" + i;
                    expectedData.put(key, value);
                    deletedKeys.remove(key);
                    table.put(
                        IndexKey.orderedBytes(key.getBytes()),
                        IndexValue.normal(value.getBytes()),
                        null
                    );
                }
            } else if (cycle % 3 == 1) {
                List<String> keysToDelete = new ArrayList<>(expectedData.keySet());
                Collections.shuffle(keysToDelete, random);
                int deleteCount = Math.min(50, keysToDelete.size());
                
                for (int i = 0; i < deleteCount; i++) {
                    String keyToDelete = keysToDelete.get(i);
                    table.put(
                        IndexKey.orderedBytes(keyToDelete.getBytes()),
                        IndexValue.tombstone(),
                        null
                    );
                    expectedData.remove(keyToDelete);
                    deletedKeys.add(keyToDelete);
                }
            } else {
                for (int i = 0; i < numKeys; i++) {
                    String key = String.format("mixed-key-%05d", i);
                    String value = "cycle-" + cycle + "-updated-" + i;
                    expectedData.put(key, value);
                    deletedKeys.remove(key);
                    table.put(
                        IndexKey.orderedBytes(key.getBytes()),
                        IndexValue.normal(value.getBytes()),
                        null
                    );
                }
            }

            memTableManager.sealActiveTable();
            dumpSealedTables(treeDumper);

            for (int i = 0; i < numKeys; i++) {
                String key = String.format("mixed-key-%05d", i);
                IndexKey indexKey = IndexKey.orderedBytes(key.getBytes());
                IndexValue value = tree.search(indexKey);

                if (deletedKeys.contains(key)) {
                    assertNull(value, "Deleted key should not be found after cycle " + cycle + ": " + key);
                } else if (expectedData.containsKey(key)) {
                    assertNotNull(value, "Key should be found after cycle " + cycle + ": " + key);
                    assertArrayEquals(
                        expectedData.get(key).getBytes(),
                        value.getValueData(),
                        "Value mismatch after cycle " + cycle + " for key: " + key
                    );
                }
            }
        }

        for (int i = 0; i < numKeys; i++) {
            String key = String.format("mixed-key-%05d", i);
            IndexKey indexKey = IndexKey.orderedBytes(key.getBytes());
            IndexValue value = tree.search(indexKey);

            if (deletedKeys.contains(key)) {
                assertNull(value, "Deleted key should not be found after all cycles: " + key);
            } else if (expectedData.containsKey(key)) {
                assertNotNull(value, "Key should be found after all cycles: " + key);
                assertArrayEquals(
                    expectedData.get(key).getBytes(),
                    value.getValueData(),
                    "Final value mismatch for key: " + key
                );
            }
        }
    }

    @Test
    void testLargeTreeWithSmallPages() throws Exception {
        int leafMaxEntries = 5;
        int indexMaxEntries = 10;
        int numKeys = 1000;
        
        PageCapacityConfig config = PageCapacityConfig.entryCountBased(leafMaxEntries, indexMaxEntries);
        setUpWithConfig(config);

        TreeMap<String, String> expectedData = new TreeMap<>();

        MemoryTable table = memTableManager.getActiveTable();
        for (int i = 0; i < numKeys; i++) {
            String key = String.format("large-key-%05d", i);
            String value = "large-value-" + i;
            expectedData.put(key, value);
            table.put(
                IndexKey.orderedBytes(key.getBytes()),
                IndexValue.normal(value.getBytes()),
                null
            );
        }
        memTableManager.sealActiveTable();

        TreeDumper treeDumper = new TreeDumper(tree, pageManager);
        dumpSealedTables(treeDumper);

        assertTrue(tree.getHeight() >= 3, "Tree height should be at least 3 with 1000 keys and 5 entries per leaf");

        for (Map.Entry<String, String> entry : expectedData.entrySet()) {
            IndexKey key = IndexKey.orderedBytes(entry.getKey().getBytes());
            IndexValue value = tree.search(key);
            assertNotNull(value, "Key should be found: " + entry.getKey());
            assertArrayEquals(
                entry.getValue().getBytes(),
                value.getValueData(),
                "Value mismatch for key: " + entry.getKey()
            );
        }
    }

    @Test
    void testRangeQueryWithEntryCountBasedPages() throws Exception {
        int leafMaxEntries = 10;
        int indexMaxEntries = 20;
        int numKeys = 500;
        
        PageCapacityConfig config = PageCapacityConfig.entryCountBased(leafMaxEntries, indexMaxEntries);
        setUpWithConfig(config);

        TreeMap<String, String> expectedData = new TreeMap<>();

        MemoryTable table = memTableManager.getActiveTable();
        for (int i = 0; i < numKeys; i++) {
            String key = String.format("range-key-%05d", i);
            String value = "range-value-" + i;
            expectedData.put(key, value);
            table.put(
                IndexKey.orderedBytes(key.getBytes()),
                IndexValue.normal(value.getBytes()),
                null
            );
        }
        memTableManager.sealActiveTable();

        TreeDumper treeDumper = new TreeDumper(tree, pageManager);
        dumpSealedTables(treeDumper);

        IndexKey startKey = IndexKey.orderedBytes("range-key-00100".getBytes());
        IndexKey endKey = IndexKey.orderedBytes("range-key-00200".getBytes());
        List<Map.Entry<IndexKey, IndexValue>> rangeResults = tree.rangeQuery(startKey, endKey);

        assertTrue(rangeResults.size() >= 100, "Range query should return at least 100 results");

        for (Map.Entry<IndexKey, IndexValue> result : rangeResults) {
            String keyStr = new String(result.getKey().getKeyData());
            assertTrue(
                keyStr.compareTo("range-key-00100") >= 0 && keyStr.compareTo("range-key-00200") < 0,
                "Key should be in range: " + keyStr
            );
        }
    }

    @Test
    void testMixedSizeAndEntryCountConfigs() throws Exception {
        PageCapacityConfig sizeConfig = PageCapacityConfig.sizeBased(8 * 1024, 16 * 1024);
        setUpWithConfig(sizeConfig);

        assertEquals(8 * 1024, sizeConfig.getMaxSize(Page.PageType.LEAF));
        assertEquals(16 * 1024, sizeConfig.getMaxSize(Page.PageType.ROOT));

        PageCapacityConfig entryConfig = PageCapacityConfig.entryCountBased(10, 20);
        assertEquals(10, entryConfig.getMaxEntries(Page.PageType.LEAF));
        assertEquals(20, entryConfig.getMaxEntries(Page.PageType.ROOT));
    }
}
