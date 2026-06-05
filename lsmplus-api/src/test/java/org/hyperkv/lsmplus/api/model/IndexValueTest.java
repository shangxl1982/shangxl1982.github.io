package org.hyperkv.lsmplus.api.model;

import org.hyperkv.lsmplus.proto.Common.ValueType;
import org.hyperkv.lsmplus.proto.Keyvalue.ValueProto;
import org.junit.jupiter.api.Test;

import static org.junit.jupiter.api.Assertions.*;

class IndexValueTest {

    @Test
    void testNormalValue() {
        byte[] data = "hello world".getBytes();
        IndexValue value = IndexValue.normal(data);

        assertEquals(ValueType.NORMAL, value.getValueType());
        assertArrayEquals(data, value.getValueData());
        assertFalse(value.isTombstone());
        assertTrue(value.isNormal());
    }

    @Test
    void testTombstoneValue() {
        IndexValue value = IndexValue.tombstone();

        assertEquals(ValueType.TOMBSTONE, value.getValueType());
        assertEquals(0, value.getValueData().length);
        assertTrue(value.isTombstone());
        assertFalse(value.isNormal());
    }

    @Test
    void testValueDataIsDefensiveCopy() {
        byte[] data = new byte[]{1, 2, 3};
        IndexValue value = IndexValue.normal(data);

        data[0] = 99;
        assertEquals(1, value.getValueData()[0]);

        byte[] retrieved = value.getValueData();
        retrieved[0] = 99;
        assertEquals(1, value.getValueData()[0]);
    }

    @Test
    void testRoundTripNormal() {
        byte[] data = "test-value-data".getBytes();
        IndexValue original = IndexValue.normal(data);

        ValueProto proto = original.toProto();
        IndexValue restored = IndexValue.fromProto(proto);

        assertEquals(original, restored);
        assertEquals(ValueType.NORMAL, restored.getValueType());
        assertArrayEquals(data, restored.getValueData());
    }

    @Test
    void testRoundTripTombstone() {
        IndexValue original = IndexValue.tombstone();

        ValueProto proto = original.toProto();
        IndexValue restored = IndexValue.fromProto(proto);

        assertEquals(original, restored);
        assertTrue(restored.isTombstone());
        assertEquals(0, restored.getValueData().length);
    }

    @Test
    void testEqualsAndHashCode() {
        IndexValue val1 = IndexValue.normal("data".getBytes());
        IndexValue val2 = IndexValue.normal("data".getBytes());
        IndexValue val3 = IndexValue.normal("other".getBytes());
        IndexValue tomb1 = IndexValue.tombstone();
        IndexValue tomb2 = IndexValue.tombstone();

        assertEquals(val1, val2);
        assertEquals(val1.hashCode(), val2.hashCode());
        assertNotEquals(val1, val3);
        assertEquals(tomb1, tomb2);
        assertNotEquals(val1, tomb1);
    }
}