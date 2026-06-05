package org.hyperkv.lsmplus.bplustree;

import org.hyperkv.lsmplus.api.model.IndexKey;
import org.hyperkv.lsmplus.api.model.IndexValue;
import org.hyperkv.lsmplus.exception.KVStoreException;
import org.hyperkv.lsmplus.memory.MemoryTable;
import org.hyperkv.lsmplus.memory.MemoryTableManager;
import org.hyperkv.lsmplus.storage.ChunkManager;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.io.TempDir;

import java.io.File;
import java.nio.file.Path;
import java.util.*;

import static org.junit.jupiter.api.Assertions.*;

class BPlusTreeFullIntegrationTest {

    @TempDir
    Path tempDir;

    private PageManager pageManager;
    private BPlusTree tree;
    private MemoryTableManager memTableManager;

    @BeforeEach
    void setUp() throws Exception {
        File storageDir = tempDir.toFile();
        UUID ownerId = UUID.randomUUID();
        UUID namespaceId = UUID.randomUUID();
        ChunkManager chunkManager = new ChunkManager(storageDir.getAbsolutePath(), ownerId, namespaceId);
        pageManager = new PageManager(chunkManager, PageCapacityConfig.DEFAULT);
        tree = new BPlusTree(pageManager, new PageCapacityConfig(
                65536,65536, 10, 20 ));
        memTableManager = new MemoryTableManager(10 * 1024 * 1024);
    }

    private List<Map.Entry<IndexKey, IndexValue>> mergeTables(List<MemoryTable> sealedTables) {
        TreeMap<IndexKey, IndexValue> merged = new TreeMap<>();
        for (MemoryTable table : sealedTables) {
            merged.putAll(table.getData());
        }
        return new ArrayList<>(merged.entrySet());
    }

    private void dumpSealedTablesAndPromoteTreeRoot(BPlusTree tree, TreeDumper treeDumper) throws KVStoreException {
        var tables = memTableManager.getSealedTables();
        tables.forEach(MemoryTable::setForDump);
        List<Map.Entry<IndexKey, IndexValue>> merged = mergeTables(tables);
        treeDumper.dump(merged, null);
        tree.promoteRoot();
        tables.forEach(MemoryTable::setForClear);
        memTableManager.clearSealedTables();
    }

    @Test
    void testInsert1000KeysAndVerifyAllPresent() throws Exception {
        int numKeys = 1000;
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
        dumpSealedTablesAndPromoteTreeRoot(tree, treeDumper);

        assertNotNull(tree.getWriterRootLocation(), "Tree should have a root after dump");

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
    void testInsert2000KeysWithDeletesAndVerify() throws Exception {
        int numKeys = 2000;
        int deleteStart = 500;
        int deleteEnd = 1000;
        TreeMap<String, String> expectedData = new TreeMap<>();

        MemoryTable table1 = memTableManager.getActiveTable();
        for (int i = 0; i < numKeys; i++) {
            String key = String.format("key-%05d", i);
            String value = "value-" + i;
            expectedData.put(key, value);
            table1.put(
                IndexKey.orderedBytes(key.getBytes()),
                IndexValue.normal(value.getBytes()),
                null
            );
        }
        memTableManager.sealActiveTable();

        TreeDumper treeDumper = new TreeDumper(tree, pageManager);
        dumpSealedTablesAndPromoteTreeRoot(tree, treeDumper);

        for (int i = deleteStart; i < deleteEnd; i++) {
            String key = String.format("key-%05d", i);
            expectedData.remove(key);
        }

        MemoryTable table2 = memTableManager.getActiveTable();
        for (int i = deleteStart; i < deleteEnd; i++) {
            String key = String.format("key-%05d", i);
            table2.put(
                IndexKey.orderedBytes(key.getBytes()),
                IndexValue.tombstone(),
                null
            );
        }
        memTableManager.sealActiveTable();

        dumpSealedTablesAndPromoteTreeRoot(tree, treeDumper);

        for (int i = 0; i < numKeys; i++) {
            String key = String.format("key-%05d", i);
            IndexKey indexKey = IndexKey.orderedBytes(key.getBytes());
            IndexValue value = tree.search(indexKey);

            if (i >= deleteStart && i < deleteEnd) {
                assertNull(value, "Deleted key should not be found: " + key);
            } else {
                assertNotNull(value, "Key should be found: " + key);
                assertArrayEquals(
                    expectedData.get(key).getBytes(),
                    value.getValueData(),
                    "Value mismatch for key: " + key
                );
            }
        }
    }

    @Test
    void testMultipleDumpsWithUpdatesAndDeletes() throws Exception {
        int numKeys = 1500;
        TreeMap<String, String> expectedData = new TreeMap<>();

        MemoryTable insertTable = memTableManager.getActiveTable();
        for (int i = 0; i < numKeys; i++) {
            String key = String.format("key-%05d", i);
            String value = "value-" + i;
            expectedData.put(key, value);
            insertTable.put(
                IndexKey.orderedBytes(key.getBytes()),
                IndexValue.normal(value.getBytes()),
                null
            );
        }
        memTableManager.sealActiveTable();

        TreeDumper treeDumper = new TreeDumper(tree, pageManager);
        dumpSealedTablesAndPromoteTreeRoot(tree, treeDumper);

        int deleteStart = 200;
        int deleteEnd = 700;
        Set<String> deletedKeys = new HashSet<>();
        for (int i = deleteStart; i < deleteEnd; i++) {
            String key = String.format("key-%05d", i);
            deletedKeys.add(key);
            expectedData.remove(key);
        }

        MemoryTable deleteTable = memTableManager.getActiveTable();
        for (int i = deleteStart; i < deleteEnd; i++) {
            String key = String.format("key-%05d", i);
            deleteTable.put(
                IndexKey.orderedBytes(key.getBytes()),
                IndexValue.tombstone(),
                null
            );
        }
        memTableManager.sealActiveTable();

        dumpSealedTablesAndPromoteTreeRoot(tree, treeDumper);

        for (int i = 0; i < numKeys; i++) {
            String key = String.format("key-%05d", i);
            IndexKey indexKey = IndexKey.orderedBytes(key.getBytes());
            IndexValue value = tree.search(indexKey);

            if (deletedKeys.contains(key)) {
                assertNull(value, "Deleted key should not be found: " + key);
            } else {
                assertNotNull(value, "Key should be found: " + key);
                assertArrayEquals(
                    expectedData.get(key).getBytes(),
                    value.getValueData(),
                    "Value mismatch for key: " + key
                );
            }
        }
    }

    @Test
    void testRandomKeyOrderInsertionAndVerification() throws Exception {
        int numKeys = 1500;
        TreeMap<String, String> expectedData = new TreeMap<>();
        List<Integer> keyIndices = new ArrayList<>();
        for (int i = 0; i < numKeys; i++) {
            keyIndices.add(i);
        }
        Collections.shuffle(keyIndices, new Random(42));

        MemoryTable table = memTableManager.getActiveTable();
        for (int idx : keyIndices) {
            String key = String.format("random-key-%05d", idx);
            String value = "random-value-" + idx + "-" + UUID.randomUUID().toString().substring(0, 8);
            expectedData.put(key, value);
            table.put(
                IndexKey.orderedBytes(key.getBytes()),
                IndexValue.normal(value.getBytes()),
                null
            );
        }
        memTableManager.sealActiveTable();

        TreeDumper treeDumper = new TreeDumper(tree, pageManager);
        dumpSealedTablesAndPromoteTreeRoot(tree, treeDumper);

        assertNotNull(tree.getWriterRootLocation(), "Tree should have a root after dump");

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
    void testSequentialKeyInsertionWithRangeVerification() throws Exception {
        int numKeys = 1000;
        TreeMap<String, String> expectedData = new TreeMap<>();

        MemoryTable table = memTableManager.getActiveTable();
        for (int i = 0; i < numKeys; i++) {
            String key = String.format("seq-key-%05d", i);
            String value = "seq-value-" + i;
            expectedData.put(key, value);
            table.put(
                IndexKey.orderedBytes(key.getBytes()),
                IndexValue.normal(value.getBytes()),
                null
            );
        }
        memTableManager.sealActiveTable();

        TreeDumper treeDumper = new TreeDumper(tree, pageManager);
        dumpSealedTablesAndPromoteTreeRoot(tree, treeDumper);

        IndexKey startKey = IndexKey.orderedBytes("seq-key-00200".getBytes());
        IndexKey endKey = IndexKey.orderedBytes("seq-key-00300".getBytes());
        List<Map.Entry<IndexKey, IndexValue>> rangeResults = tree.rangeQuery(startKey, endKey);

        assertTrue(rangeResults.size() >= 100, "Range query should return at least 100 results");

        for (Map.Entry<IndexKey, IndexValue> result : rangeResults) {
            String keyStr = new String(result.getKey().getKeyData());
            assertTrue(
                keyStr.compareTo("seq-key-00200") >= 0 && keyStr.compareTo("seq-key-00300") < 0,
                "Key should be in range: " + keyStr
            );
        }
    }

    @Test
    void testUpdateExistingKeysAcrossDumps() throws Exception {
        int numKeys = 800;
        int numDumps = 3;
        TreeMap<String, String> expectedData = new TreeMap<>();
        TreeDumper treeDumper = new TreeDumper(tree, pageManager);

        for (int dump = 0; dump < numDumps; dump++) {
            MemoryTable table = memTableManager.getActiveTable();

            for (int i = 0; i < numKeys; i++) {
                String key = String.format("update-key-%05d", i);
                String value = "updated-value-dump" + dump + "-key" + i;
                expectedData.put(key, value);
                table.put(
                    IndexKey.orderedBytes(key.getBytes()),
                    IndexValue.normal(value.getBytes()),
                    null
                );
            }

            memTableManager.sealActiveTable();
            dumpSealedTablesAndPromoteTreeRoot(tree, treeDumper);
        }

        for (int i = 0; i < numKeys; i++) {
            String key = String.format("update-key-%05d", i);
            IndexKey indexKey = IndexKey.orderedBytes(key.getBytes());
            IndexValue value = tree.search(indexKey);

            assertNotNull(value, "Key should be found: " + key);
            String expectedValue = expectedData.get(key);
            assertArrayEquals(
                expectedValue.getBytes(),
                value.getValueData(),
                "Value should be from last dump for key: " + key
            );
        }
    }

    @Test
    void testLargeValueInsertionAndVerification() throws Exception {
        int numKeys = 500;
        TreeMap<String, String> expectedData = new TreeMap<>();
        Random random = new Random(12345);

        MemoryTable table = memTableManager.getActiveTable();
        for (int i = 0; i < numKeys; i++) {
            String key = String.format("large-key-%05d", i);
            int valueSize = 100 + random.nextInt(500);
            byte[] valueBytes = new byte[valueSize];
            random.nextBytes(valueBytes);
            String value = Base64.getEncoder().encodeToString(valueBytes);
            expectedData.put(key, value);
            table.put(
                IndexKey.orderedBytes(key.getBytes()),
                IndexValue.normal(value.getBytes()),
                null
            );
        }
        memTableManager.sealActiveTable();

        TreeDumper treeDumper = new TreeDumper(tree, pageManager);
        dumpSealedTablesAndPromoteTreeRoot(tree, treeDumper);

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
    void testInterleavedInsertDeleteOperations() throws Exception {
        int numKeys = 1000;
        TreeMap<String, String> expectedData = new TreeMap<>();
        Set<String> deletedKeys = new HashSet<>();

        MemoryTable insertTable = memTableManager.getActiveTable();
        for (int i = 0; i < numKeys; i++) {
            String key = String.format("inter-key-%05d", i);
            String value = "inter-value-" + i;
            expectedData.put(key, value);
            insertTable.put(
                IndexKey.orderedBytes(key.getBytes()),
                IndexValue.normal(value.getBytes()),
                null
            );
        }
        memTableManager.sealActiveTable();

        TreeDumper treeDumper = new TreeDumper(tree, pageManager);
        dumpSealedTablesAndPromoteTreeRoot(tree, treeDumper);

        Random random = new Random(999);
        List<String> allKeys = new ArrayList<>();
        for (int i = 0; i < numKeys; i++) {
            allKeys.add(String.format("inter-key-%05d", i));
        }
        Collections.shuffle(allKeys, random);
        
        int deleteCount = 300;
        for (int i = 0; i < deleteCount; i++) {
            String keyToDelete = allKeys.get(i);
            deletedKeys.add(keyToDelete);
            expectedData.remove(keyToDelete);
        }

        MemoryTable deleteTable = memTableManager.getActiveTable();
        for (String keyToDelete : deletedKeys) {
            deleteTable.put(
                IndexKey.orderedBytes(keyToDelete.getBytes()),
                IndexValue.tombstone(),
                null
            );
        }
        memTableManager.sealActiveTable();

        dumpSealedTablesAndPromoteTreeRoot(tree, treeDumper);

        for (int i = 0; i < numKeys; i++) {
            String key = String.format("inter-key-%05d", i);
            IndexKey indexKey = IndexKey.orderedBytes(key.getBytes());
            IndexValue value = tree.search(indexKey);

            if (deletedKeys.contains(key)) {
                assertNull(value, "Deleted key should not be found: " + key);
            } else {
                assertNotNull(value, "Key should be found: " + key);
                assertArrayEquals(
                    expectedData.get(key).getBytes(),
                    value.getValueData(),
                    "Value mismatch for key: " + key
                );
            }
        }
    }

    @Test
    void testTreeHeightGrowthWithManyKeys() throws Exception {
        int numKeys = 5000;
        TreeMap<String, String> expectedData = new TreeMap<>();

        MemoryTable table = memTableManager.getActiveTable();
        for (int i = 0; i < numKeys; i++) {
            String key = String.format("height-key-%05d", i);
            String value = "height-value-" + i;
            expectedData.put(key, value);
            table.put(
                IndexKey.orderedBytes(key.getBytes()),
                IndexValue.normal(value.getBytes()),
                null
            );
        }
        memTableManager.sealActiveTable();

        TreeDumper treeDumper = new TreeDumper(tree, pageManager);
        dumpSealedTablesAndPromoteTreeRoot(tree, treeDumper);

        assertTrue(tree.getHeight() >= 1, "Tree height should be at least 1");
        assertNotNull(tree.getWriterRootLocation(), "Tree should have a root");

        int verifiedCount = 0;
        for (Map.Entry<String, String> entry : expectedData.entrySet()) {
            IndexKey key = IndexKey.orderedBytes(entry.getKey().getBytes());
            IndexValue value = tree.search(key);
            assertNotNull(value, "Key should be found: " + entry.getKey());
            assertArrayEquals(
                entry.getValue().getBytes(),
                value.getValueData(),
                "Value mismatch for key: " + entry.getKey()
            );
            verifiedCount++;
        }
        assertEquals(numKeys, verifiedCount, "All keys should be verified");
    }

    @Test
    void testDeleteAllKeysLeavesEmptyTree() throws Exception {
        int numKeys = 500;
        TreeDumper treeDumper = new TreeDumper(tree, pageManager);

        MemoryTable table1 = memTableManager.getActiveTable();
        for (int i = 0; i < numKeys; i++) {
            String key = String.format("delall-key-%05d", i);
            table1.put(
                IndexKey.orderedBytes(key.getBytes()),
                IndexValue.normal(("value-" + i).getBytes()),
                null
            );
        }
        memTableManager.sealActiveTable();

        dumpSealedTablesAndPromoteTreeRoot(tree, treeDumper);

        MemoryTable table2 = memTableManager.getActiveTable();
        for (int i = 0; i < numKeys; i++) {
            String key = String.format("delall-key-%05d", i);
            table2.put(
                IndexKey.orderedBytes(key.getBytes()),
                IndexValue.tombstone(),
                null
            );
        }
        memTableManager.sealActiveTable();

        dumpSealedTablesAndPromoteTreeRoot(tree, treeDumper);

        for (int i = 0; i < numKeys; i++) {
            String key = String.format("delall-key-%05d", i);
            IndexValue value = tree.search(IndexKey.orderedBytes(key.getBytes()));
            assertNull(value, "All keys should be deleted: " + key);
        }
    }
}
