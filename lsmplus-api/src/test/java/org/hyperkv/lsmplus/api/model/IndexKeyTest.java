package org.hyperkv.lsmplus.api.model;

import org.hyperkv.lsmplus.proto.Common.KeyType;
import org.hyperkv.lsmplus.proto.Keyvalue.KeyProto;
import org.junit.jupiter.api.Test;

import static org.junit.jupiter.api.Assertions.*;

class IndexKeyTest {

    @Test
    void testOrderedBytesKey() {
        byte[] data = "user:12345".getBytes();
        IndexKey key = IndexKey.orderedBytes(data);

        assertEquals(KeyType.ORDERED_BYTES, key.getKeyType());
        assertArrayEquals(data, key.getKeyData());
    }

    @Test
    void testCustomKey() {
        byte[] data = new byte[]{1, 2, 3, 4};
        IndexKey key = IndexKey.custom(data);

        assertEquals(KeyType.CUSTOM, key.getKeyType());
        assertArrayEquals(data, key.getKeyData());
    }

    @Test
    void testKeyDataIsDefensiveCopy() {
        byte[] data = new byte[]{1, 2, 3};
        IndexKey key = IndexKey.orderedBytes(data);

        data[0] = 99;
        assertEquals(1, key.getKeyData()[0]);

        byte[] retrieved = key.getKeyData();
        retrieved[0] = 99;
        assertEquals(1, key.getKeyData()[0]);
    }

    @Test
    void testNullKeyTypeThrows() {
        assertThrows(IllegalArgumentException.class, () -> new IndexKey(null, new byte[]{1}));
    }

    @Test
    void testNullKeyDataThrows() {
        assertThrows(IllegalArgumentException.class, () -> new IndexKey(KeyType.ORDERED_BYTES, null));
    }

    @Test
    void testComparisonOrderedBytes() {
        IndexKey key1 = IndexKey.orderedBytes("a".getBytes());
        IndexKey key2 = IndexKey.orderedBytes("b".getBytes());
        IndexKey key3 = IndexKey.orderedBytes("a".getBytes());

        assertTrue(key1.compareTo(key2) < 0);
        assertTrue(key2.compareTo(key1) > 0);
        assertEquals(0, key1.compareTo(key3));
    }

    @Test
    void testComparisonDifferentTypes() {
        IndexKey orderedKey = IndexKey.orderedBytes("z".getBytes());
        IndexKey customKey = IndexKey.custom("a".getBytes());

        assertTrue(orderedKey.compareTo(customKey) < 0);
    }

    @Test
    void testRoundTrip() {
        byte[] data = "test-key-data".getBytes();
        IndexKey original = IndexKey.orderedBytes(data);

        KeyProto proto = original.toProto();
        IndexKey restored = IndexKey.fromProto(proto);

        assertEquals(original, restored);
        assertEquals(original.getKeyType(), restored.getKeyType());
        assertArrayEquals(original.getKeyData(), restored.getKeyData());
    }

    @Test
    void testEqualsAndHashCode() {
        IndexKey key1 = IndexKey.orderedBytes("key".getBytes());
        IndexKey key2 = IndexKey.orderedBytes("key".getBytes());
        IndexKey key3 = IndexKey.orderedBytes("other".getBytes());

        assertEquals(key1, key2);
        assertEquals(key1.hashCode(), key2.hashCode());
        assertNotEquals(key1, key3);
    }
}