package org.hyperkv.lsmplus.core;

import org.hyperkv.lsmplus.api.model.IndexKey;
import org.hyperkv.lsmplus.api.model.IndexValue;

/**
 * Represents a single operation within a batch.
 * Uses internal BatchOpType to distinguish put vs delete operations.
 */
public class BatchOperation {

    private final BatchOpType type;
    private final IndexKey key;
    private final IndexValue value;

    private BatchOperation(BatchOpType type, IndexKey key, IndexValue value) {
        this.type = type;
        this.key = key;
        this.value = value;
    }

    public static BatchOperation put(IndexKey key, IndexValue value) {
        if (key == null || value == null) {
            throw new IllegalArgumentException("key and value must not be null");
        }
        return new BatchOperation(BatchOpType.PUT, key, value);
    }

    public static BatchOperation delete(IndexKey key) {
        if (key == null) {
            throw new IllegalArgumentException("key must not be null");
        }
        return new BatchOperation(BatchOpType.DELETE, key, null);
    }

    public BatchOpType getType() {
        return type;
    }

    public IndexKey getKey() {
        return key;
    }

    public IndexValue getValue() {
        return value;
    }

    public boolean isPut() {
        return type == BatchOpType.PUT;
    }

    public boolean isDelete() {
        return type == BatchOpType.DELETE;
    }

    /**
     * Internal enum for batch operation types.
     * Distinct from proto OperationType which includes BATCH as a type.
     */
    public enum BatchOpType {
        PUT,
        DELETE
    }
}
