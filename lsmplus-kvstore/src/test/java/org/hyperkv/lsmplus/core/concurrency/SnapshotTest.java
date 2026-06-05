package org.hyperkv.lsmplus.core.concurrency;

import org.hyperkv.lsmplus.api.model.IndexKey;
import org.hyperkv.lsmplus.api.model.IndexValue;
import org.hyperkv.lsmplus.api.model.RangeQueryOptions;
import org.hyperkv.lsmplus.api.model.RangeQueryResult;
import org.hyperkv.lsmplus.bplustree.BPlusTree;
import org.hyperkv.lsmplus.core.KVStore;
import org.hyperkv.lsmplus.core.KVStoreState;
import org.hyperkv.lsmplus.memory.MemoryTable;
import org.hyperkv.lsmplus.memory.MemoryTableManager;
import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.io.TempDir;

import java.io.File;
import java.io.IOException;
import java.nio.charset.StandardCharsets;
import java.util.ArrayList;
import java.util.List;
import java.util.Map;
import java.util.UUID;

import static org.junit.jupiter.api.Assertions.*;

class SnapshotTest {

    @TempDir
    File tempDir;

    private KVStore kvStore;

    @BeforeEach
    void setUp() throws IOException {
        kvStore = new KVStore(tempDir, UUID.randomUUID(), UUID.randomUUID(), 1024, 5);
        kvStore.start();
    }

    @AfterEach
    void tearDown() throws IOException {
        if (kvStore != null && kvStore.getState() == KVStoreState.RUNNING) {
            kvStore.shutdown();
        }
    }

    @Test
    void testCreateSnapshot() {
        Snapshot snapshot = kvStore.createSnapshot();
        assertNotNull(snapshot);
        assertNotNull(snapshot.getActiveTable());
        assertNotNull(snapshot.getSealedTables());
        assertNotNull(snapshot.getBPlusTree());
    }

    @Test
    void testSnapshotGet() {
        IndexKey key = IndexKey.orderedBytes("testKey".getBytes(StandardCharsets.UTF_8));
        IndexValue value = IndexValue.normal("testValue".getBytes(StandardCharsets.UTF_8));

        kvStore.put(key, value);

        Snapshot snapshot = kvStore.createSnapshot();
        IndexValue retrieved = snapshot.get(key);

        assertNotNull(retrieved);
        assertArrayEquals(value.getValueData(), retrieved.getValueData());
    }

    @Test
    void testSnapshotGetNonExistent() {
        Snapshot snapshot = kvStore.createSnapshot();
        IndexKey key = IndexKey.orderedBytes("nonExistent".getBytes(StandardCharsets.UTF_8));
        IndexValue retrieved = snapshot.get(key);
        assertNull(retrieved);
    }

    @Test
    void testSnapshotConsistency() {
        IndexKey key = IndexKey.orderedBytes("testKey".getBytes(StandardCharsets.UTF_8));
        IndexValue value1 = IndexValue.normal("value1".getBytes(StandardCharsets.UTF_8));
        IndexValue value2 = IndexValue.normal("value2".getBytes(StandardCharsets.UTF_8));

        kvStore.put(key, value1);

        Snapshot snapshot = kvStore.createSnapshot();

        IndexValue snapshotValue = snapshot.get(key);
        assertNotNull(snapshotValue);
        assertArrayEquals(value1.getValueData(), snapshotValue.getValueData());

        kvStore.put(key, value2);

        IndexValue currentValue = kvStore.get(key);
        assertNotNull(currentValue);
        assertArrayEquals(value2.getValueData(), currentValue.getValueData());
    }

    @Test
    void testSnapshotWithDelete() {
        IndexKey key = IndexKey.orderedBytes("testKey".getBytes(StandardCharsets.UTF_8));
        IndexValue value = IndexValue.normal("testValue".getBytes(StandardCharsets.UTF_8));

        kvStore.put(key, value);

        Snapshot snapshot1 = kvStore.createSnapshot();
        assertNotNull(snapshot1.get(key));

        kvStore.delete(key);

        assertNull(kvStore.get(key));
    }

    @Test
    void testSnapshotRangeQuery() {
        for (int i = 0; i < 10; i++) {
            IndexKey key = IndexKey.orderedBytes(
                ("key" + String.format("%02d", i)).getBytes(StandardCharsets.UTF_8));
            IndexValue value = IndexValue.normal(("value" + i).getBytes(StandardCharsets.UTF_8));
            kvStore.put(key, value);
        }

        Snapshot snapshot = kvStore.createSnapshot();

        IndexKey start = IndexKey.orderedBytes("key03".getBytes(StandardCharsets.UTF_8));
        IndexKey end = IndexKey.orderedBytes("key07".getBytes(StandardCharsets.UTF_8));

        List<Map.Entry<IndexKey, IndexValue>> results = snapshot.rangeQuery(start, end);
        assertTrue(results.size() >= 3);
    }

    @Test
    void testSnapshotVersion() {
        Snapshot snapshot1 = kvStore.createSnapshot();
        long version1 = snapshot1.getVersion();

        IndexKey key = IndexKey.orderedBytes("testKey".getBytes(StandardCharsets.UTF_8));
        IndexValue value = IndexValue.normal("testValue".getBytes(StandardCharsets.UTF_8));
        kvStore.put(key, value);

        Snapshot snapshot2 = kvStore.createSnapshot();
        long version2 = snapshot2.getVersion();

        assertEquals(version1, version2);
    }

    @Test
    void testSnapshotGetWithTombstone() {
        IndexKey key = IndexKey.orderedBytes("testKey".getBytes(StandardCharsets.UTF_8));
        IndexValue value = IndexValue.normal("testValue".getBytes(StandardCharsets.UTF_8));

        kvStore.put(key, value);

        kvStore.delete(key);

        Snapshot snapshot = kvStore.createSnapshot();
        IndexValue retrieved = snapshot.get(key);
        assertNull(retrieved);
    }

    @Test
    void testKVStoreSnapshotGet() {
        IndexKey key = IndexKey.orderedBytes("testKey".getBytes(StandardCharsets.UTF_8));
        IndexValue value = IndexValue.normal("testValue".getBytes(StandardCharsets.UTF_8));

        kvStore.put(key, value);

        Snapshot snapshot = kvStore.createSnapshot();
        IndexValue retrieved = kvStore.snapshotGet(snapshot, key);

        assertNotNull(retrieved);
        assertArrayEquals(value.getValueData(), retrieved.getValueData());
    }

    @Test
    void testRangeQueryMergesAllLayers() {
        IndexKey keyA1 = IndexKey.orderedBytes("A1".getBytes(StandardCharsets.UTF_8));
        IndexKey keyA2 = IndexKey.orderedBytes("A2".getBytes(StandardCharsets.UTF_8));
        IndexKey keyA3 = IndexKey.orderedBytes("A3".getBytes(StandardCharsets.UTF_8));
        IndexKey keyA4 = IndexKey.orderedBytes("A4".getBytes(StandardCharsets.UTF_8));
        IndexKey keyB1 = IndexKey.orderedBytes("B1".getBytes(StandardCharsets.UTF_8));
        IndexKey keyB2 = IndexKey.orderedBytes("B2".getBytes(StandardCharsets.UTF_8));
        IndexKey keyB3 = IndexKey.orderedBytes("B3".getBytes(StandardCharsets.UTF_8));
        IndexKey keyC1 = IndexKey.orderedBytes("C1".getBytes(StandardCharsets.UTF_8));
        IndexKey keyC3 = IndexKey.orderedBytes("C3".getBytes(StandardCharsets.UTF_8));
        IndexKey keyC5 = IndexKey.orderedBytes("C5".getBytes(StandardCharsets.UTF_8));

        kvStore.put(keyB1, IndexValue.normal("B1_from_tree".getBytes(StandardCharsets.UTF_8)));
        kvStore.put(keyC5, IndexValue.normal("C5_from_tree".getBytes(StandardCharsets.UTF_8)));
        kvStore.put(keyA4, IndexValue.normal("A4_from_tree".getBytes(StandardCharsets.UTF_8)));

        kvStore.sealActiveTable();
        kvStore.dump();

        kvStore.put(keyA3, IndexValue.normal("A3_from_sealed".getBytes(StandardCharsets.UTF_8)));
        kvStore.put(keyB2, IndexValue.normal("B2_from_sealed".getBytes(StandardCharsets.UTF_8)));
        kvStore.put(keyC1, IndexValue.normal("C1_from_sealed".getBytes(StandardCharsets.UTF_8)));

        kvStore.sealActiveTable();

        kvStore.put(keyA1, IndexValue.normal("A1_from_active".getBytes(StandardCharsets.UTF_8)));
        kvStore.put(keyA2, IndexValue.normal("A2_from_active".getBytes(StandardCharsets.UTF_8)));
        kvStore.put(keyB1, IndexValue.tombstone());
        kvStore.put(keyB3, IndexValue.normal("B3_from_active".getBytes(StandardCharsets.UTF_8)));
        kvStore.put(keyC3, IndexValue.normal("C3_from_active".getBytes(StandardCharsets.UTF_8)));

        IndexKey start = IndexKey.orderedBytes("A".getBytes(StandardCharsets.UTF_8));
        IndexKey end = IndexKey.orderedBytes("D".getBytes(StandardCharsets.UTF_8));

        RangeQueryOptions options = RangeQueryOptions.builder()
                .start(start)
                .end(end)
                .build();

        Snapshot snapshot = kvStore.createSnapshot();
        RangeQueryResult result = snapshot.rangeQuery(options);

        List<String> expectedKeys = List.of("A1", "A2", "A3", "A4", "B2", "B3", "C1", "C3", "C5");
        List<String> actualKeys = new ArrayList<>();
        for (Map.Entry<IndexKey, IndexValue> entry : result.getEntries()) {
            actualKeys.add(new String(entry.getKey().getKeyData(), StandardCharsets.UTF_8));
        }

        assertEquals(expectedKeys, actualKeys, 
            "Range query should merge all layers correctly and exclude tombstones");
        
        assertEquals(9, result.getCount());
        assertFalse(result.hasMore());
    }

    @Test
    void testRangeQueryWithLimitMergesAllLayers() {
        IndexKey keyA1 = IndexKey.orderedBytes("A1".getBytes(StandardCharsets.UTF_8));
        IndexKey keyA2 = IndexKey.orderedBytes("A2".getBytes(StandardCharsets.UTF_8));
        IndexKey keyA3 = IndexKey.orderedBytes("A3".getBytes(StandardCharsets.UTF_8));
        IndexKey keyB1 = IndexKey.orderedBytes("B1".getBytes(StandardCharsets.UTF_8));
        IndexKey keyB2 = IndexKey.orderedBytes("B2".getBytes(StandardCharsets.UTF_8));

        kvStore.put(keyB1, IndexValue.normal("B1_from_tree".getBytes(StandardCharsets.UTF_8)));
        kvStore.sealActiveTable();
        kvStore.dump();

        kvStore.put(keyA3, IndexValue.normal("A3_from_sealed".getBytes(StandardCharsets.UTF_8)));
        kvStore.put(keyB2, IndexValue.normal("B2_from_sealed".getBytes(StandardCharsets.UTF_8)));
        kvStore.sealActiveTable();

        kvStore.put(keyA1, IndexValue.normal("A1_from_active".getBytes(StandardCharsets.UTF_8)));
        kvStore.put(keyA2, IndexValue.normal("A2_from_active".getBytes(StandardCharsets.UTF_8)));

        IndexKey start = IndexKey.orderedBytes("A".getBytes(StandardCharsets.UTF_8));
        IndexKey end = IndexKey.orderedBytes("C".getBytes(StandardCharsets.UTF_8));

        RangeQueryOptions options = RangeQueryOptions.builder()
                .start(start)
                .end(end)
                .limit(3)
                .build();

        Snapshot snapshot = kvStore.createSnapshot();
        RangeQueryResult result = snapshot.rangeQuery(options);

        assertEquals(3, result.getCount());
        assertTrue(result.hasMore());

        List<String> actualKeys = new ArrayList<>();
        for (Map.Entry<IndexKey, IndexValue> entry : result.getEntries()) {
            actualKeys.add(new String(entry.getKey().getKeyData(), StandardCharsets.UTF_8));
        }
        assertEquals(List.of("A1", "A2", "A3"), actualKeys);

        assertNotNull(result.getContinuationToken());
    }

    @Test
    void testRangeQuerySameKeyPriorityActiveOverSealedOverTree() {
        IndexKey keyX = IndexKey.orderedBytes("X".getBytes(StandardCharsets.UTF_8));

        kvStore.put(keyX, IndexValue.normal("X_from_tree".getBytes(StandardCharsets.UTF_8)));
        kvStore.sealActiveTable();
        kvStore.dump();

        kvStore.put(keyX, IndexValue.normal("X_from_sealed".getBytes(StandardCharsets.UTF_8)));
        kvStore.sealActiveTable();

        kvStore.put(keyX, IndexValue.normal("X_from_active".getBytes(StandardCharsets.UTF_8)));

        IndexKey start = IndexKey.orderedBytes("A".getBytes(StandardCharsets.UTF_8));
        IndexKey end = IndexKey.orderedBytes("Z".getBytes(StandardCharsets.UTF_8));

        RangeQueryOptions options = RangeQueryOptions.builder()
                .start(start)
                .end(end)
                .build();

        Snapshot snapshot = kvStore.createSnapshot();
        RangeQueryResult result = snapshot.rangeQuery(options);

        assertEquals(1, result.getCount());
        
        Map.Entry<IndexKey, IndexValue> entry = result.getEntries().get(0);
        assertArrayEquals("X_from_active".getBytes(StandardCharsets.UTF_8), entry.getValue().getValueData());
    }

    @Test
    void testRangeQuerySameKeyPrioritySealedOverTree() {
        IndexKey keyY = IndexKey.orderedBytes("Y".getBytes(StandardCharsets.UTF_8));

        kvStore.put(keyY, IndexValue.normal("Y_from_tree".getBytes(StandardCharsets.UTF_8)));
        kvStore.sealActiveTable();
        kvStore.dump();

        kvStore.put(keyY, IndexValue.normal("Y_from_sealed".getBytes(StandardCharsets.UTF_8)));

        IndexKey start = IndexKey.orderedBytes("A".getBytes(StandardCharsets.UTF_8));
        IndexKey end = IndexKey.orderedBytes("Z".getBytes(StandardCharsets.UTF_8));

        RangeQueryOptions options = RangeQueryOptions.builder()
                .start(start)
                .end(end)
                .build();

        Snapshot snapshot = kvStore.createSnapshot();
        RangeQueryResult result = snapshot.rangeQuery(options);

        assertEquals(1, result.getCount());
        
        Map.Entry<IndexKey, IndexValue> entry = result.getEntries().get(0);
        assertArrayEquals("Y_from_sealed".getBytes(StandardCharsets.UTF_8), entry.getValue().getValueData());
    }
}
