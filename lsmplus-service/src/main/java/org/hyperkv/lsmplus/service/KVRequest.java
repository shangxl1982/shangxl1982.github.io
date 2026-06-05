package org.hyperkv.lsmplus.service;

import java.util.List;
import java.util.Map;
import java.util.Objects;

public class KVRequest {

    public enum Type {
        PUT,
        GET,
        DELETE,
        BATCH_PUT,
        BATCH_DELETE,
        BATCH
    }

    private final Type type;
    private final String key;
    private final byte[] value;
    private final Map<String, byte[]> batch;
    private final List<BatchOperationItem> operations;

    private KVRequest(Type type, String key, byte[] value, Map<String, byte[]> batch, List<BatchOperationItem> operations) {
        this.type = type;
        this.key = key;
        this.value = value;
        this.batch = batch;
        this.operations = operations;
    }

    public static KVRequest put(String key, byte[] value) {
        return new KVRequest(Type.PUT, key, value, null, null);
    }

    public static KVRequest get(String key) {
        return new KVRequest(Type.GET, key, null, null, null);
    }

    public static KVRequest delete(String key) {
        return new KVRequest(Type.DELETE, key, null, null, null);
    }

    public static KVRequest batchPut(Map<String, byte[]> batch) {
        return new KVRequest(Type.BATCH_PUT, null, null, batch, null);
    }

    public static KVRequest batchDelete(List<String> keys) {
        Map<String, byte[]> batch = new java.util.HashMap<>();
        for (String key : keys) {
            batch.put(key, null);
        }
        return new KVRequest(Type.BATCH_DELETE, null, null, batch, null);
    }

    public static KVRequest batch(List<BatchOperationItem> operations) {
        return new KVRequest(Type.BATCH, null, null, null, operations);
    }

    public Type getType() {
        return type;
    }

    public String getKey() {
        return key;
    }

    public byte[] getValue() {
        return value;
    }

    public Map<String, byte[]> getBatch() {
        return batch;
    }

    public List<BatchOperationItem> getOperations() {
        return operations;
    }

    @Override
    public boolean equals(Object o) {
        if (this == o) return true;
        if (o == null || getClass() != o.getClass()) return false;
        KVRequest kvRequest = (KVRequest) o;
        return type == kvRequest.type &&
                Objects.equals(key, kvRequest.key) &&
                java.util.Arrays.equals(value, kvRequest.value) &&
                Objects.equals(batch, kvRequest.batch) &&
                Objects.equals(operations, kvRequest.operations);
    }

    @Override
    public int hashCode() {
        int result = Objects.hash(type, key, batch, operations);
        result = 31 * result + java.util.Arrays.hashCode(value);
        return result;
    }
}
