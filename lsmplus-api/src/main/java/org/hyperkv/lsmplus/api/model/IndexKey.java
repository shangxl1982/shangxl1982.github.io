package org.hyperkv.lsmplus.api.model;

import org.hyperkv.lsmplus.proto.Common.KeyType;
import org.hyperkv.lsmplus.proto.Keyvalue.KeyProto;

import java.util.Arrays;

/**
 * Represents a sortable key in the LSM tree key-value store.
 * <p>
 * IndexKey provides type-safe key representation with support for ordered byte comparison
 * and custom key formats. Keys are immutable and support protobuf serialization for
 * cross-component communication.
 * </p>
 * 
 * <h2>Key Types</h2>
 * <ul>
 *   <li>{@link KeyType#ORDERED_BYTES}: Keys that support byte-by-byte comparison for sorting</li>
 *   <li>{@link KeyType#CUSTOM}: User-defined key formats with custom comparison logic</li>
 * </ul>
 * 
 * <h2>Usage Example</h2>
 * <pre>{@code
 * // Create an ordered bytes key
 * IndexKey key = IndexKey.orderedBytes("user:12345".getBytes());
 * 
 * // Serialize to protobuf
 * KeyProto proto = key.toProto();
 * 
 * // Deserialize from protobuf
 * IndexKey restored = IndexKey.fromProto(proto);
 * 
 * // Compare keys
 * int result = key1.compareTo(key2);
 * }</pre>
 * 
 * <h2>Thread Safety</h2>
 * <p>
 * This class is immutable and thread-safe. All methods can be safely called from multiple threads.
 * </p>
 * 
 * @see IndexValue
 * @see KeyType
 */
public final class IndexKey implements Comparable<IndexKey> {

    private final KeyType keyType;
    private final byte[] keyData;

    /**
     * Constructs a new IndexKey with the specified type and data.
     * 
     * @param keyType the type of the key (must not be null)
     * @param keyData the byte array containing the key data (must not be null)
     * @throws IllegalArgumentException if keyType or keyData is null
     */
    public IndexKey(KeyType keyType, byte[] keyData) {
        if (keyType == null) {
            throw new IllegalArgumentException("keyType must not be null");
        }
        if (keyData == null) {
            throw new IllegalArgumentException("keyData must not be null");
        }
        this.keyType = keyType;
        this.keyData = keyData.clone();
    }

    /**
     * Creates an IndexKey with {@link KeyType#ORDERED_BYTES} type.
     * <p>
     * Ordered bytes keys support byte-by-byte comparison for sorting operations.
     * </p>
     * 
     * @param keyData the byte array containing the key data
     * @return a new IndexKey instance with ORDERED_BYTES type
     * @throws IllegalArgumentException if keyData is null
     */
    public static IndexKey orderedBytes(byte[] keyData) {
        return new IndexKey(KeyType.ORDERED_BYTES, keyData);
    }

    /**
     * Creates an IndexKey with {@link KeyType#CUSTOM} type.
     * <p>
     * Custom keys allow user-defined key formats with custom comparison logic.
     * </p>
     * 
     * @param keyData the byte array containing the key data
     * @return a new IndexKey instance with CUSTOM type
     * @throws IllegalArgumentException if keyData is null
     */
    public static IndexKey custom(byte[] keyData) {
        return new IndexKey(KeyType.CUSTOM, keyData);
    }

    /**
     * Returns the type of this key.
     * 
     * @return the key type
     */
    public KeyType getKeyType() {
        return keyType;
    }

    /**
     * Returns a defensive copy of the key data.
     * <p>
     * Modifications to the returned array will not affect this IndexKey instance.
     * </p>
     * 
     * @return a copy of the key data byte array
     */
    public byte[] getKeyData() {
        return keyData.clone();
    }

    /**
     * Serializes this IndexKey to a protobuf message.
     * 
     * @return the protobuf representation of this key
     */
    public KeyProto toProto() {
        return KeyProto.newBuilder()
                .setKeyType(keyType)
                .setKeyData(com.google.protobuf.ByteString.copyFrom(keyData))
                .build();
    }

    /**
     * Deserializes an IndexKey from a protobuf message.
     * 
     * @param proto the protobuf message to deserialize
     * @return a new IndexKey instance
     * @throws IllegalArgumentException if proto is null
     */
    public static IndexKey fromProto(KeyProto proto) {
        return new IndexKey(
                proto.getKeyType(),
                proto.getKeyData().toByteArray()
        );
    }

    /**
     * Compares this key with another key for ordering.
     * <p>
     * Comparison rules:
     * </p>
     * <ul>
     *   <li>If both keys are ORDERED_BYTES, performs byte-by-byte comparison</li>
     *   <li>If key types differ, compares by key type ordinal</li>
     *   <li>Otherwise, performs byte-by-byte comparison</li>
     * </ul>
     * 
     * @param other the key to compare to
     * @return a negative integer, zero, or a positive integer as this key is less than,
     *         equal to, or greater than the specified key
     */
    @Override
    public int compareTo(IndexKey other) {
        if (keyType == KeyType.ORDERED_BYTES && other.keyType == KeyType.ORDERED_BYTES) {
            return compareBytes(keyData, other.keyData);
        }
        if (keyType != other.keyType) {
            return Integer.compare(keyType.getNumber(), other.keyType.getNumber());
        }
        return compareBytes(keyData, other.keyData);
    }

    private static int compareBytes(byte[] a, byte[] b) {
        int minLen = Math.min(a.length, b.length);
        for (int i = 0; i < minLen; i++) {
            int cmp = Byte.compare(a[i], b[i]);
            if (cmp != 0) {
                return cmp;
            }
        }
        return Integer.compare(a.length, b.length);
    }

    @Override
    public boolean equals(Object o) {
        if (this == o) return true;
        if (!(o instanceof IndexKey other)) return false;
        return keyType == other.keyType && Arrays.equals(keyData, other.keyData);
    }

    @Override
    public int hashCode() {
        return 31 * keyType.hashCode() + Arrays.hashCode(keyData);
    }

    @Override
    public String toString() {
        return "IndexKey{keyType=" + keyType + ", keyData=" + Arrays.toString(keyData) + '}';
    }
}