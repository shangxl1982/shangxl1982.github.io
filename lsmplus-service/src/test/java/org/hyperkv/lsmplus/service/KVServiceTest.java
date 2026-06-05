package org.hyperkv.lsmplus.service;

import org.hyperkv.lsmplus.core.KVStore;
import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.io.TempDir;

import java.io.File;
import java.io.IOException;
import java.util.ArrayList;
import java.util.List;
import java.util.Map;

import static org.junit.jupiter.api.Assertions.*;

class KVServiceTest {

    @TempDir
    File tempDir;

    private KVStore store;
    private KVService service;

    @BeforeEach
    void setUp() throws IOException {
        store = new KVStore(tempDir);
        service = new KVService(store);
    }

    @AfterEach
    void tearDown() throws IOException {
        if (service != null && service.isRunning()) {
            service.stop();
        }
    }

    @Test
    void testStart() throws IOException {
        assertFalse(service.isRunning());
        service.start();
        assertTrue(service.isRunning());
    }

    @Test
    void testStop() throws IOException {
        service.start();
        assertTrue(service.isRunning());
        service.stop();
        assertFalse(service.isRunning());
    }

    @Test
    void testHandleBeforeStart() {
        KVRequest request = KVRequest.put("key1", "value1".getBytes());
        KVResponse response = service.handle(request);

        assertFalse(response.isSuccess());
        assertEquals("SERVICE_NOT_RUNNING", response.getError());
    }

    @Test
    void testPutAndGet() throws IOException {
        service.start();

        KVRequest putRequest = KVRequest.put("key1", "value1".getBytes());
        KVResponse putResponse = service.handle(putRequest);
        assertTrue(putResponse.isSuccess());

        KVRequest getRequest = KVRequest.get("key1");
        KVResponse getResponse = service.handle(getRequest);
        assertTrue(getResponse.isSuccess());
        assertArrayEquals("value1".getBytes(), getResponse.getValue());
    }

    @Test
    void testGetNotFound() throws IOException {
        service.start();

        KVRequest getRequest = KVRequest.get("nonexistent");
        KVResponse response = service.handle(getRequest);

        assertFalse(response.isSuccess());
        assertEquals("NOT_FOUND", response.getError());
    }

    @Test
    void testDelete() throws IOException {
        service.start();

        KVRequest putRequest = KVRequest.put("key1", "value1".getBytes());
        service.handle(putRequest);

        KVRequest deleteRequest = KVRequest.delete("key1");
        KVResponse deleteResponse = service.handle(deleteRequest);
        assertTrue(deleteResponse.isSuccess());

        KVRequest getRequest = KVRequest.get("key1");
        KVResponse getResponse = service.handle(getRequest);
        assertFalse(getResponse.isSuccess());
    }

    @Test
    void testBatchPut() throws IOException {
        service.start();

        Map<String, byte[]> batch = Map.of(
                "key1", "value1".getBytes(),
                "key2", "value2".getBytes()
        );

        KVRequest request = KVRequest.batchPut(batch);
        KVResponse response = service.handle(request);
        assertTrue(response.isSuccess());

        KVResponse response1 = service.handle(KVRequest.get("key1"));
        assertTrue(response1.isSuccess());
        assertArrayEquals("value1".getBytes(), response1.getValue());

        KVResponse response2 = service.handle(KVRequest.get("key2"));
        assertTrue(response2.isSuccess());
        assertArrayEquals("value2".getBytes(), response2.getValue());
    }

    @Test
    void testGetStats() throws IOException {
        service.start();

        Map<String, Object> stats = service.getStats();

        assertNotNull(stats);
        assertTrue((Boolean) stats.get("running"));
        assertNotNull(stats.get("storeState"));
    }

    @Test
    void testNullStore() {
        assertThrows(IllegalArgumentException.class, () -> new KVService(null));
    }

    @Test
    void testDoubleStart() throws IOException {
        service.start();
        assertThrows(IllegalStateException.class, () -> service.start());
    }

    @Test
    void testMixedBatch() throws IOException {
        service.start();

        List<BatchOperationItem> operations = new ArrayList<>();
        operations.add(BatchOperationItem.put("key1", "value1".getBytes()));
        operations.add(BatchOperationItem.put("key2", "value2".getBytes()));
        operations.add(BatchOperationItem.delete("key1"));
        operations.add(BatchOperationItem.put("key3", "value3".getBytes()));

        KVRequest request = KVRequest.batch(operations);
        KVResponse response = service.handle(request);
        assertTrue(response.isSuccess());

        KVResponse response1 = service.handle(KVRequest.get("key1"));
        assertFalse(response1.isSuccess());

        KVResponse response2 = service.handle(KVRequest.get("key2"));
        assertTrue(response2.isSuccess());
        assertArrayEquals("value2".getBytes(), response2.getValue());

        KVResponse response3 = service.handle(KVRequest.get("key3"));
        assertTrue(response3.isSuccess());
        assertArrayEquals("value3".getBytes(), response3.getValue());
    }

    @Test
    void testMixedBatchWithExistingData() throws IOException {
        service.start();

        service.handle(KVRequest.put("existing", "oldValue".getBytes()));
        service.handle(KVRequest.put("toDelete", "willBeDeleted".getBytes()));

        List<BatchOperationItem> operations = new ArrayList<>();
        operations.add(BatchOperationItem.put("existing", "newValue".getBytes()));
        operations.add(BatchOperationItem.delete("toDelete"));
        operations.add(BatchOperationItem.put("new", "brandNew".getBytes()));

        KVRequest request = KVRequest.batch(operations);
        KVResponse response = service.handle(request);
        assertTrue(response.isSuccess());

        KVResponse existingResponse = service.handle(KVRequest.get("existing"));
        assertTrue(existingResponse.isSuccess());
        assertArrayEquals("newValue".getBytes(), existingResponse.getValue());

        KVResponse deletedResponse = service.handle(KVRequest.get("toDelete"));
        assertFalse(deletedResponse.isSuccess());

        KVResponse newResponse = service.handle(KVRequest.get("new"));
        assertTrue(newResponse.isSuccess());
        assertArrayEquals("brandNew".getBytes(), newResponse.getValue());
    }
}
