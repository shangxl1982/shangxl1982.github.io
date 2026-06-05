package org.hyperkv.lsmplus.service;

public class BatchOperationItem {

    public enum Type {
        PUT,
        DELETE
    }

    private final Type type;
    private final String key;
    private final byte[] value;

    private BatchOperationItem(Type type, String key, byte[] value) {
        this.type = type;
        this.key = key;
        this.value = value;
    }

    public static BatchOperationItem put(String key, byte[] value) {
        if (key == null) {
            throw new IllegalArgumentException("key must not be null");
        }
        if (value == null) {
            throw new IllegalArgumentException("value must not be null for PUT operation");
        }
        return new BatchOperationItem(Type.PUT, key, value);
    }

    public static BatchOperationItem delete(String key) {
        if (key == null) {
            throw new IllegalArgumentException("key must not be null");
        }
        return new BatchOperationItem(Type.DELETE, key, null);
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

    public boolean isPut() {
        return type == Type.PUT;
    }

    public boolean isDelete() {
        return type == Type.DELETE;
    }

    @Override
    public String toString() {
        return "BatchOperationItem{type=" + type + ", key='" + key + "'}";
    }
}
