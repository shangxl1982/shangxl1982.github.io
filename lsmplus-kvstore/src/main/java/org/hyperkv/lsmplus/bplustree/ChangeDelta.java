package org.hyperkv.lsmplus.bplustree;

import org.hyperkv.lsmplus.api.model.IndexKey;
import org.hyperkv.lsmplus.api.model.IndexValue;
import org.hyperkv.lsmplus.bplustree.page.IndexPair;

/**
 * Represents a change that needs to be applied to a page.
 * 
 * <p>This is a triple of:
 * <ul>
 *   <li>operation: PUT, UPDATE, or DELETE</li>
 *   <li>targetKey: the IndexKey being affected</li>
 *   <li>newIndexPair: the new key-location/value pair (for PUT/UPDATE, null for DELETE)</li>
 * </ul>
 * 
 * <p>Used in PALM-style batch processing to propagate changes from one level
 * to the next without immediate recursive updates.
 * 
 * <p>For leaf pages: newIndexPair contains IndexValue
 * <p>For index pages: newIndexPair contains SegmentLocation
 */
public final class ChangeDelta {
    
    /**
     * Operation types for page changes.
     */
    public enum Operation {
        /**
         * Insert or replace the value for the key.
         * If the key exists, replace it; otherwise insert it.
         */
        PUT,
        
        /**
         * Replace the target key/value with a new index pair.
         * The targetKey is the old key to find, newIndexPair contains the new key and value.
         * Used when a child's minKey changes.
         */
        UPDATE,
        
        /**
         * Delete the key from the page.
         */
        DELETE
    }
    
    private final Operation operation;
    private final IndexKey targetKey;
    private final IndexPair newIndexPair;
    
    private ChangeDelta(Operation operation, IndexKey targetKey, IndexPair newIndexPair) {
        if (operation == null) {
            throw new IllegalArgumentException("operation must not be null");
        }
        if (targetKey == null) {
            throw new IllegalArgumentException("targetKey must not be null");
        }
        if (operation != Operation.DELETE && newIndexPair == null) {
            throw new IllegalArgumentException("newIndexPair must not be null for PUT/UPDATE operations");
        }
        
        this.operation = operation;
        this.targetKey = targetKey;
        this.newIndexPair = newIndexPair;
    }
    
    /**
     * Creates a PUT delta to add or replace a key-value pair (for leaf pages).
     * 
     * @param key the key to add or replace
     * @param value the value to insert
     * @return a new ChangeDelta
     */
    public static ChangeDelta put(IndexKey key, IndexValue value) {
        return new ChangeDelta(Operation.PUT, key, IndexPair.of(key, value));
    }
    
    /**
     * Creates a PUT delta to add or replace a key-location pair (for index pages).
     * 
     * @param targetKey the key to add or replace
     * @param newIndexPair the key-location pair to insert
     * @return a new ChangeDelta
     */
    public static ChangeDelta put(IndexKey targetKey, IndexPair newIndexPair) {
        return new ChangeDelta(Operation.PUT, targetKey, newIndexPair);
    }
    
    /**
     * Creates an UPDATE delta to replace a target key with a new index pair.
     * 
     * <p>Used when a child's minKey changes - the old key is deleted and
     * replaced with the new key-location pair.
     * 
     * @param targetKey the old key to find and replace
     * @param newIndexPair the new key-location pair
     * @return a new ChangeDelta
     */
    public static ChangeDelta update(IndexKey targetKey, IndexPair newIndexPair) {
        return new ChangeDelta(Operation.UPDATE, targetKey, newIndexPair);
    }
    
    /**
     * Creates a DELETE delta to remove a key from the page.
     * 
     * @param targetKey the key to delete
     * @return a new ChangeDelta
     */
    public static ChangeDelta delete(IndexKey targetKey) {
        return new ChangeDelta(Operation.DELETE, targetKey, null);
    }
    
    public Operation getOperation() {
        return operation;
    }
    
    public IndexKey getTargetKey() {
        return targetKey;
    }
    
    public IndexPair getNewIndexPair() {
        return newIndexPair;
    }
    
    @Override
    public String toString() {
        return String.format("ChangeDelta{op=%s, key=%s, pair=%s}", 
            operation, targetKey, newIndexPair);
    }
}
