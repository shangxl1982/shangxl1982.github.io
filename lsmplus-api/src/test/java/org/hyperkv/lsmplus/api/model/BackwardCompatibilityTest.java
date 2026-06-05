package org.hyperkv.lsmplus.api.model;

import com.google.protobuf.InvalidProtocolBufferException;
import org.hyperkv.lsmplus.proto.Common.KeyType;
import org.hyperkv.lsmplus.proto.Common.ValueType;
import org.hyperkv.lsmplus.proto.Keyvalue.KeyProto;
import org.hyperkv.lsmplus.proto.Keyvalue.ValueProto;
import org.junit.jupiter.api.Test;

import static org.junit.jupiter.api.Assertions.*;

class BackwardCompatibilityTest {

    @Test
    void testKeyProtoBackwardCompatibility() throws InvalidProtocolBufferException {
        byte[] originalData = "test-key-data".getBytes();
        KeyProto original = KeyProto.newBuilder()
                .setKeyType(KeyType.ORDERED_BYTES)
                .setKeyData(com.google.protobuf.ByteString.copyFrom(originalData))
                .build();

        byte[] serialized = original.toByteArray();
        KeyProto restored = KeyProto.parseFrom(serialized);

        assertEquals(original.getKeyType(), restored.getKeyType());
        assertArrayEquals(originalData, restored.getKeyData().toByteArray());
    }

    @Test
    void testValueProtoBackwardCompatibility() throws InvalidProtocolBufferException {
        byte[] originalData = "test-value-data".getBytes();
        ValueProto original = ValueProto.newBuilder()
                .setValueType(ValueType.NORMAL)
                .setValueData(com.google.protobuf.ByteString.copyFrom(originalData))
                .build();

        byte[] serialized = original.toByteArray();
        ValueProto restored = ValueProto.parseFrom(serialized);

        assertEquals(original.getValueType(), restored.getValueType());
        assertArrayEquals(originalData, restored.getValueData().toByteArray());
    }

    @Test
    void testTombstoneBackwardCompatibility() throws InvalidProtocolBufferException {
        ValueProto tombstone = ValueProto.newBuilder()
                .setValueType(ValueType.TOMBSTONE)
                .build();

        byte[] serialized = tombstone.toByteArray();
        ValueProto restored = ValueProto.parseFrom(serialized);

        assertEquals(ValueType.TOMBSTONE, restored.getValueType());
        assertEquals(0, restored.getValueData().size());
    }

    @Test
    void testKeyProtoUnknownFieldsPreserved() throws InvalidProtocolBufferException {
        KeyProto original = KeyProto.newBuilder()
                .setKeyType(KeyType.ORDERED_BYTES)
                .setKeyData(com.google.protobuf.ByteString.copyFrom("key".getBytes()))
                .setUnknownFields(com.google.protobuf.UnknownFieldSet.newBuilder()
                        .addField(999, com.google.protobuf.UnknownFieldSet.Field.getDefaultInstance())
                        .build())
                .build();

        byte[] serialized = original.toByteArray();
        KeyProto restored = KeyProto.parseFrom(serialized);

        assertEquals(original.getKeyType(), restored.getKeyType());
    }

    @Test
    void testEnumValueCompatibility() throws InvalidProtocolBufferException {
        KeyType[] allKeyTypes = KeyType.values();
        for (KeyType keyType : allKeyTypes) {
            if (keyType == KeyType.UNRECOGNIZED) {
                continue;
            }
            KeyProto proto = KeyProto.newBuilder()
                    .setKeyType(keyType)
                    .setKeyData(com.google.protobuf.ByteString.copyFrom("data".getBytes()))
                    .build();

            byte[] serialized = proto.toByteArray();
            KeyProto restored = KeyProto.parseFrom(serialized);

            assertEquals(keyType, restored.getKeyType());
        }

        ValueType[] allValueTypes = ValueType.values();
        for (ValueType valueType : allValueTypes) {
            if (valueType == ValueType.UNRECOGNIZED) {
                continue;
            }
            ValueProto proto = ValueProto.newBuilder()
                    .setValueType(valueType)
                    .build();

            byte[] serialized = proto.toByteArray();
            ValueProto restored = ValueProto.parseFrom(serialized);

            assertEquals(valueType, restored.getValueType());
        }
    }

    @Test
    void testIndexKeyRoundTripPreservesData() throws InvalidProtocolBufferException {
        byte[] keyData = new byte[]{1, 2, 3, 4, 5, 6, 7, 8};
        IndexKey original = IndexKey.orderedBytes(keyData);

        KeyProto proto = original.toProto();
        byte[] serialized = proto.toByteArray();

        KeyProto restoredProto = KeyProto.parseFrom(serialized);
        IndexKey restored = IndexKey.fromProto(restoredProto);

        assertEquals(original, restored);
        assertArrayEquals(keyData, restored.getKeyData());
    }

    @Test
    void testIndexValueRoundTripPreservesData() throws InvalidProtocolBufferException {
        byte[] valueData = "complex-value-data-with-special-chars-!@#$%".getBytes();
        IndexValue original = IndexValue.normal(valueData);

        ValueProto proto = original.toProto();
        byte[] serialized = proto.toByteArray();

        ValueProto restoredProto = ValueProto.parseFrom(serialized);
        IndexValue restored = IndexValue.fromProto(restoredProto);

        assertEquals(original, restored);
        assertArrayEquals(valueData, restored.getValueData());
    }

    @Test
    void testEmptyKeyValueCompatibility() throws InvalidProtocolBufferException {
        IndexKey emptyKey = IndexKey.orderedBytes(new byte[0]);
        KeyProto keyProto = emptyKey.toProto();
        byte[] keyBytes = keyProto.toByteArray();
        IndexKey restoredKey = IndexKey.fromProto(KeyProto.parseFrom(keyBytes));
        assertEquals(emptyKey, restoredKey);

        IndexValue emptyValue = IndexValue.normal(new byte[0]);
        ValueProto valueProto = emptyValue.toProto();
        byte[] valueBytes = valueProto.toByteArray();
        IndexValue restoredValue = IndexValue.fromProto(ValueProto.parseFrom(valueBytes));
        assertEquals(emptyValue, restoredValue);
    }

    @Test
    void testLargeKeyValueCompatibility() throws InvalidProtocolBufferException {
        byte[] largeKeyData = new byte[64 * 1024];
        for (int i = 0; i < largeKeyData.length; i++) {
            largeKeyData[i] = (byte) (i % 256);
        }

        IndexKey largeKey = IndexKey.orderedBytes(largeKeyData);
        KeyProto keyProto = largeKey.toProto();
        byte[] keyBytes = keyProto.toByteArray();
        IndexKey restoredKey = IndexKey.fromProto(KeyProto.parseFrom(keyBytes));
        assertEquals(largeKey, restoredKey);

        byte[] largeValueData = new byte[4 * 1024 * 1024];
        for (int i = 0; i < largeValueData.length; i++) {
            largeValueData[i] = (byte) (i % 256);
        }

        IndexValue largeValue = IndexValue.normal(largeValueData);
        ValueProto valueProto = largeValue.toProto();
        byte[] valueBytes = valueProto.toByteArray();
        IndexValue restoredValue = IndexValue.fromProto(ValueProto.parseFrom(valueBytes));
        assertEquals(largeValue, restoredValue);
    }
}
