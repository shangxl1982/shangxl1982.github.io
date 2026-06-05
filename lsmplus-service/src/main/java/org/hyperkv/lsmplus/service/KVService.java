package org.hyperkv.lsmplus.service;

import org.hyperkv.lsmplus.api.model.IndexKey;
import org.hyperkv.lsmplus.api.model.IndexValue;
import org.hyperkv.lsmplus.core.BatchOperation;
import org.hyperkv.lsmplus.core.KVStore;
import org.hyperkv.lsmplus.core.KVStoreState;

import java.io.IOException;
import java.util.ArrayList;
import java.util.HashMap;
import java.util.List;
import java.util.Map;

public class KVService {

    private final KVStore store;
    private volatile boolean running;

    public KVService(KVStore store) {
        if (store == null) {
            throw new IllegalArgumentException("store must not be null");
        }
        this.store = store;
        this.running = false;
    }

    public synchronized void start() throws IOException {
        if (running) {
            throw new IllegalStateException("Service already running");
        }
        store.start();
        running = true;
    }

    public synchronized void stop() throws IOException {
        if (!running) {
            return;
        }
        store.shutdown();
        running = false;
    }

    public boolean isRunning() {
        return running;
    }

    public KVResponse handle(KVRequest request) {
        if (!running) {
            return KVResponse.error("SERVICE_NOT_RUNNING", "Service is not running");
        }

        try {
            return switch (request.getType()) {
                case PUT -> handlePut(request);
                case GET -> handleGet(request);
                case DELETE -> handleDelete(request);
                case BATCH_PUT -> handleBatchPut(request);
                case BATCH_DELETE -> handleBatchDelete(request);
                case BATCH -> handleBatch(request);
            };
        } catch (Exception e) {
            return KVResponse.error("INTERNAL_ERROR", "Internal error", e);
        }
    }

    private IndexKey toIndexKey(String key) {
        return IndexKey.orderedBytes(key.getBytes());
    }

    private KVResponse handlePut(KVRequest request) {
        IndexKey key = toIndexKey(request.getKey());
        IndexValue value = IndexValue.normal(request.getValue());
        store.put(key, value);
        return KVResponse.success();
    }

    private KVResponse handleGet(KVRequest request) {
        IndexKey key = toIndexKey(request.getKey());
        IndexValue value = store.get(key);
        if (value == null || value.isTombstone()) {
            return KVResponse.notFound(request.getKey());
        }
        return KVResponse.success(value.getValueData());
    }

    private KVResponse handleDelete(KVRequest request) {
        IndexKey key = toIndexKey(request.getKey());
        store.delete(key);
        return KVResponse.success();
    }

    private KVResponse handleBatchPut(KVRequest request) {
        Map<String, byte[]> batch = request.getBatch();
        if (batch == null || batch.isEmpty()) {
            return KVResponse.success();
        }

        List<BatchOperation> operations = new ArrayList<>();
        for (Map.Entry<String, byte[]> entry : batch.entrySet()) {
            IndexKey key = toIndexKey(entry.getKey());
            IndexValue value = IndexValue.normal(entry.getValue());
            operations.add(BatchOperation.put(key, value));
        }

        store.batch(operations);
        return KVResponse.success();
    }

    private KVResponse handleBatchDelete(KVRequest request) {
        Map<String, byte[]> batch = request.getBatch();
        if (batch == null || batch.isEmpty()) {
            return KVResponse.success();
        }

        List<BatchOperation> operations = new ArrayList<>();
        for (String keyStr : batch.keySet()) {
            IndexKey key = toIndexKey(keyStr);
            operations.add(BatchOperation.delete(key));
        }

        store.batch(operations);
        return KVResponse.success();
    }

    private KVResponse handleBatch(KVRequest request) {
        List<BatchOperationItem> items = request.getOperations();
        if (items == null || items.isEmpty()) {
            return KVResponse.success();
        }

        List<BatchOperation> operations = new ArrayList<>();
        for (BatchOperationItem item : items) {
            IndexKey key = toIndexKey(item.getKey());
            if (item.isPut()) {
                IndexValue value = IndexValue.normal(item.getValue());
                operations.add(BatchOperation.put(key, value));
            } else {
                operations.add(BatchOperation.delete(key));
            }
        }

        store.batch(operations);
        return KVResponse.success();
    }

    public Map<String, Object> getStats() {
        Map<String, Object> stats = new HashMap<>();
        stats.put("running", running);
        stats.put("storeState", getStoreState().name());
        return stats;
    }

    public KVStoreState getStoreState() {
        return running ? KVStoreState.RUNNING : KVStoreState.STOPPED;
    }
}
