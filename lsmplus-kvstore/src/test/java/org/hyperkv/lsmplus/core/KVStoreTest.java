package org.hyperkv.lsmplus.core;

import org.hyperkv.lsmplus.api.model.IndexKey;
import org.hyperkv.lsmplus.api.model.IndexValue;
import org.hyperkv.lsmplus.exception.KVStoreRuntimeException;
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

class KVStoreTest {

    @TempDir
    File tempDir;

    private KVStore kvStore;

    @BeforeEach
    void setUp() throws IOException {
        kvStore = new KVStore(tempDir, UUID.randomUUID(), UUID.randomUUID(), 1024, 5);
    }

    @AfterEach
    void tearDown() throws IOException {
        if (kvStore != null && kvStore.getState() == KVStoreState.RUNNING) {
            kvStore.shutdown();
        }
    }

    @Test
    void testInitialState() {
        assertEquals(KVStoreState.CREATED, kvStore.getState());
    }

    @Test
    void testStart() throws IOException {
        kvStore.start();
        assertEquals(KVStoreState.RUNNING, kvStore.getState());
    }

    @Test
    void testStartTwice() throws IOException {
        kvStore.start();
        assertThrows(KVStoreRuntimeException.class, () -> kvStore.start());
    }

    @Test
    void testShutdown() throws IOException {
        kvStore.start();
        kvStore.shutdown();
        assertEquals(KVStoreState.STOPPED, kvStore.getState());
    }

    @Test
    void testShutdownWithoutStart() {
        assertThrows(KVStoreRuntimeException.class, () -> kvStore.shutdown());
    }

    @Test
    void testPutAndGet() throws IOException {
        kvStore.start();

        IndexKey key = IndexKey.orderedBytes("testKey".getBytes(StandardCharsets.UTF_8));
        IndexValue value = IndexValue.normal("testValue".getBytes(StandardCharsets.UTF_8));

        kvStore.put(key, value);

        IndexValue retrieved = kvStore.get(key);
        assertNotNull(retrieved);
        assertArrayEquals(value.getValueData(), retrieved.getValueData());
        assertFalse(retrieved.isTombstone());
    }

    @Test
    void testGetNonExistentKey() throws IOException {
        kvStore.start();

        IndexKey key = IndexKey.orderedBytes("nonExistent".getBytes(StandardCharsets.UTF_8));
        IndexValue retrieved = kvStore.get(key);
        assertNull(retrieved);
    }

    @Test
    void testDelete() throws IOException {
        kvStore.start();

        IndexKey key = IndexKey.orderedBytes("testKey".getBytes(StandardCharsets.UTF_8));
        IndexValue value = IndexValue.normal("testValue".getBytes(StandardCharsets.UTF_8));

        kvStore.put(key, value);
        assertNotNull(kvStore.get(key));

        kvStore.delete(key);
        assertNull(kvStore.get(key));
    }

    @Test
    void testBatchOperations() throws IOException {
        kvStore.start();

        List<BatchOperation> operations = new ArrayList<>();
        for (int i = 0; i < 5; i++) {
            IndexKey key = IndexKey.orderedBytes(("key" + i).getBytes(StandardCharsets.UTF_8));
            IndexValue value = IndexValue.normal(("value" + i).getBytes(StandardCharsets.UTF_8));
            operations.add(BatchOperation.put(key, value));
        }

        kvStore.batch(operations);

        for (int i = 0; i < 5; i++) {
            IndexKey key = IndexKey.orderedBytes(("key" + i).getBytes(StandardCharsets.UTF_8));
            IndexValue retrieved = kvStore.get(key);
            assertNotNull(retrieved);
        }
    }

    @Test
    void testBatchWithDelete() throws IOException {
        kvStore.start();

        IndexKey key1 = IndexKey.orderedBytes("key1".getBytes(StandardCharsets.UTF_8));
        IndexKey key2 = IndexKey.orderedBytes("key2".getBytes(StandardCharsets.UTF_8));
        IndexValue value1 = IndexValue.normal("value1".getBytes(StandardCharsets.UTF_8));
        IndexValue value2 = IndexValue.normal("value2".getBytes(StandardCharsets.UTF_8));

        kvStore.put(key1, value1);
        kvStore.put(key2, value2);

        List<BatchOperation> operations = new ArrayList<>();
        operations.add(BatchOperation.delete(key1));
        IndexKey key3 = IndexKey.orderedBytes("key3".getBytes(StandardCharsets.UTF_8));
        operations.add(BatchOperation.put(key3, IndexValue.normal("value3".getBytes(StandardCharsets.UTF_8))));

        kvStore.batch(operations);

        assertNull(kvStore.get(key1));
        assertNotNull(kvStore.get(key2));
        assertNotNull(kvStore.get(key3));
    }

    @Test
    void testRangeQuery() throws IOException {
        kvStore.start();

        for (int i = 0; i < 10; i++) {
            IndexKey key = IndexKey.orderedBytes(("key" + String.format("%02d", i)).getBytes(StandardCharsets.UTF_8));
            IndexValue value = IndexValue.normal(("value" + i).getBytes(StandardCharsets.UTF_8));
            kvStore.put(key, value);
        }

        IndexKey start = IndexKey.orderedBytes("key03".getBytes(StandardCharsets.UTF_8));
        IndexKey end = IndexKey.orderedBytes("key07".getBytes(StandardCharsets.UTF_8));

        List<Map.Entry<IndexKey, IndexValue>> results = kvStore.rangeQuery(start, end);
        assertTrue(results.size() >= 3);
    }

    @Test
    void testPutBeforeStart() {
        IndexKey key = IndexKey.orderedBytes("testKey".getBytes(StandardCharsets.UTF_8));
        IndexValue value = IndexValue.normal("testValue".getBytes(StandardCharsets.UTF_8));

        assertThrows(KVStoreRuntimeException.class, () -> kvStore.put(key, value));
    }

    @Test
    void testGetBeforeStart() {
        IndexKey key = IndexKey.orderedBytes("testKey".getBytes(StandardCharsets.UTF_8));

        assertThrows(KVStoreRuntimeException.class, () -> kvStore.get(key));
    }

    @Test
    void testDeleteBeforeStart() {
        IndexKey key = IndexKey.orderedBytes("testKey".getBytes(StandardCharsets.UTF_8));

        assertThrows(KVStoreRuntimeException.class, () -> kvStore.delete(key));
    }

    @Test
    void testBatchBeforeStart() {
        List<BatchOperation> operations = new ArrayList<>();
        IndexKey key = IndexKey.orderedBytes("testKey".getBytes(StandardCharsets.UTF_8));
        IndexValue value = IndexValue.normal("testValue".getBytes(StandardCharsets.UTF_8));
        operations.add(BatchOperation.put(key, value));

        assertThrows(KVStoreRuntimeException.class, () -> kvStore.batch(operations));
    }

    @Test
    void testSealActiveTable() throws IOException {
        kvStore.start();

        assertEquals(0, kvStore.getSealedTableCount());

        kvStore.sealActiveTable();

        assertEquals(1, kvStore.getSealedTableCount());
    }

    @Test
    void testShouldDump() throws IOException {
        kvStore.start();

        for (int i = 0; i < 5; i++) {
            kvStore.sealActiveTable();
        }

        assertTrue(kvStore.shouldDump());
    }

    @Test
    void testGetters() throws IOException {
        kvStore.start();

        assertNotNull(kvStore.getDataDir());
        assertNotNull(kvStore.getOwnerId());
        assertNotNull(kvStore.getNamespaceId());
        assertTrue(kvStore.getMemoryTableMaxSize() > 0);
        assertTrue(kvStore.getMaxSealedTables() > 0);
        assertNotNull(kvStore.getBPlusTree());
        assertNotNull(kvStore.getMemoryTableManager());
        assertNotNull(kvStore.getJournalRegionManager());
        assertNotNull(kvStore.getChunkManager());
    }
}
