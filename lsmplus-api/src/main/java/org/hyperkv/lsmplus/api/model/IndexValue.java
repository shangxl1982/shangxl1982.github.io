package org.hyperkv.lsmplus.api.model;

import org.hyperkv.lsmplus.proto.Common.ValueType;
import org.hyperkv.lsmplus.proto.Keyvalue.ValueProto;

import java.util.Arrays;

/**
 * Represents a value in the LSM tree key-value store.
 * <p>
 * IndexValue provides type-safe value representation with support for normal values
 * and tombstone markers for deletions. Values are immutable and support protobuf
 * serialization for cross-component communication.
 * </p>
 * 
 * <h2>Value Types</h2>
 * <ul>
 *   <li>{@link ValueType#NORMAL}: Regular data values</li>
 *   <li>{@link ValueType#TOMBSTONE}: Deletion markers</li>
 * </ul>
 * 
 * <h2>Usage Example</h2>
 * <pre>{@code
 * // Create a normal value
 * IndexValue value = IndexValue.normal("data".getBytes());
 * 
 * // Create a tombstone for deletion
 * IndexValue tombstone = IndexValue.tombstone();
 * 
 * // Serialize to protobuf
 * ValueProto proto = value.toProto();
 * 
 * // Deserialize from protobuf
 * IndexValue restored = IndexValue.fromProto(proto);
 * 
 * // Check value type
 * if (value.isTombstone()) {
 *     // Handle deletion
 * }
 * }</pre>
 * 
 * <h2>Thread Safety</h2>
 * <p>
 * This class is immutable and thread-safe. All methods can be safely called from multiple threads.
 * </p>
 * 
 * @see IndexKey
 * @see ValueType
 */
public final class IndexValue {

    private static final byte[] EMPTY_DATA = new byte[0];

    private final ValueType valueType;
    private final byte[] valueData;

    /**
     * Constructs a new IndexValue with the specified type and data.
     * 
     * @param valueType the type of the value (must not be null)
     * @param valueData the byte array containing the value data (may be null, will be converted to empty array)
     */
    private IndexValue(ValueType valueType, byte[] valueData) {
        if (valueType == null) {
            throw new IllegalArgumentException("valueType must not be null");
        }
        this.valueType = valueType;
        this.valueData = valueData == null ? EMPTY_DATA : valueData.clone();
    }

    /**
     * Creates an IndexValue with {@link ValueType#NORMAL} type.
     * <p>
     * Normal values represent regular data stored in the key-value store.
     * </p>
     * 
     * @param valueData the byte array containing the value data (may be null)
     * @return a new IndexValue instance with NORMAL type
     */
    public static IndexValue normal(byte[] valueData) {
        return new IndexValue(ValueType.NORMAL, valueData);
    }

    /**
     * Creates an IndexValue with {@link ValueType#TOMBSTONE} type.
     * <p>
     * Tombstones represent deletion markers in the LSM tree.
     * </p>
     * 
     * @return a new IndexValue instance with TOMBSTONE type and empty data
     */
    public static IndexValue tombstone() {
        return new IndexValue(ValueType.TOMBSTONE, EMPTY_DATA);
    }

    /**
     * Returns the type of this value.
     * 
     * @return the value type
     */
    public ValueType getValueType() {
        return valueType;
    }

    /**
     * Returns a defensive copy of the value data.
     * <p>
     * Modifications to the returned array will not affect this IndexValue instance.
     * </p>
     * 
     * @return a copy of the value data byte array (empty for tombstones)
     */
    public byte[] getValueData() {
        return valueData.clone();
    }

    /**
     * Checks if this value is a tombstone (deletion marker).
     * 
     * @return true if this is a tombstone, false otherwise
     */
    public boolean isTombstone() {
        return valueType == ValueType.TOMBSTONE;
    }

    /**
     * Checks if this value is a normal (non-tombstone) value.
     * 
     * @return true if this is a normal value, false otherwise
     */
    public boolean isNormal() {
        return valueType == ValueType.NORMAL;
    }

    /**
     * Serializes this IndexValue to a protobuf message.
     * 
     * @return the protobuf representation of this value
     */
    public ValueProto toProto() {
        ValueProto.Builder builder = ValueProto.newBuilder()
                .setValueType(valueType);
        if (valueData.length > 0) {
            builder.setValueData(com.google.protobuf.ByteString.copyFrom(valueData));
        }
        return builder.build();
    }

    /**
     * Deserializes an IndexValue from a protobuf message.
     * 
     * @param proto the protobuf message to deserialize
     * @return a new IndexValue instance
     * @throws IllegalArgumentException if proto is null
     */
    public static IndexValue fromProto(ValueProto proto) {
        return new IndexValue(
                proto.getValueType(),
                proto.getValueData().toByteArray()
        );
    }

    @Override
    public boolean equals(Object o) {
        if (this == o) return true;
        if (!(o instanceof IndexValue other)) return false;
        return valueType == other.valueType && Arrays.equals(valueData, other.valueData);
    }

    @Override
    public int hashCode() {
        return 31 * valueType.hashCode() + Arrays.hashCode(valueData);
    }

    @Override
    public String toString() {
        return "IndexValue{valueType=" + valueType +
                (isTombstone() ? "" : ", valueData=" + Arrays.toString(valueData)) + '}';
    }
}