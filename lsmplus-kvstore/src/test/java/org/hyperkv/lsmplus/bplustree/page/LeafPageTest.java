package org.hyperkv.lsmplus.bplustree.page;
import org.hyperkv.lsmplus.bplustree.PageCapacityConfig;

import org.hyperkv.lsmplus.api.model.IndexKey;
import org.hyperkv.lsmplus.api.model.IndexValue;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;

import java.nio.charset.StandardCharsets;
import java.util.List;

import static org.junit.jupiter.api.Assertions.*;

class LeafPageTest {

    private Page page;

    @BeforeEach
    void setUp() {
        page = Page.createPage(Page.PageType.LEAF, 1, PageCapacityConfig.DEFAULT, null);
    }

    @Test
    void testPutAndGet() {
        IndexKey key = IndexKey.orderedBytes("test-key".getBytes(StandardCharsets.UTF_8));
        IndexValue value = IndexValue.normal("test-value".getBytes(StandardCharsets.UTF_8));

        page.put(key, value);

        IndexValue retrieved = page.get(key);
        assertNotNull(retrieved);
        assertArrayEquals(value.getValueData(), retrieved.getValueData());
    }

    @Test
    void testDelete() {
        IndexKey key = IndexKey.orderedBytes("delete-key".getBytes(StandardCharsets.UTF_8));
        IndexValue value = IndexValue.normal("delete-value".getBytes(StandardCharsets.UTF_8));

        page.put(key, value);
        assertNotNull(page.get(key));

        page.delete(key);

        assertNull(page.get(key));
        assertEquals(0, page.getEntryCount());
    }

    @Test
    void testRangeQuery() {
        for (int i = 0; i < 10; i++) {
            IndexKey key = IndexKey.orderedBytes(("key-" + String.format("%02d", i)).getBytes(StandardCharsets.UTF_8));
            IndexValue value = IndexValue.normal(("value-" + i).getBytes(StandardCharsets.UTF_8));
            page.put(key, value);
        }

        IndexKey start = IndexKey.orderedBytes("key-03".getBytes(StandardCharsets.UTF_8));
        IndexKey end = IndexKey.orderedBytes("key-07".getBytes(StandardCharsets.UTF_8));

        List<IndexPair> results = page.rangeQuery(start, end);

        assertEquals(4, results.size());
    }

    @Test
    void testRangeQueryAll() {
        for (int i = 0; i < 5; i++) {
            IndexKey key = IndexKey.orderedBytes(("key-" + i).getBytes(StandardCharsets.UTF_8));
            IndexValue value = IndexValue.normal(("value-" + i).getBytes(StandardCharsets.UTF_8));
            page.put(key, value);
        }

        List<IndexPair> results = page.rangeQuery(null, null);

        assertEquals(5, results.size());
    }

    @Test
    void testMultipleEntries() {
        for (int i = 0; i < 100; i++) {
            IndexKey key = IndexKey.orderedBytes(("key-" + String.format("%03d", i)).getBytes(StandardCharsets.UTF_8));
            IndexValue value = IndexValue.normal(("value-" + i).getBytes(StandardCharsets.UTF_8));
            page.put(key, value);
        }

        assertEquals(100, page.getEntryCount());

        for (int i = 0; i < 100; i++) {
            IndexKey key = IndexKey.orderedBytes(("key-" + String.format("%03d", i)).getBytes(StandardCharsets.UTF_8));
            IndexValue retrieved = page.get(key);
            assertNotNull(retrieved);
        }
    }

    @Test
    void testSerialization() {
        for (int i = 0; i < 10; i++) {
            IndexKey key = IndexKey.orderedBytes(("key-" + String.format("%02d", i)).getBytes(StandardCharsets.UTF_8));
            IndexValue value = IndexValue.normal(("value-" + i).getBytes(StandardCharsets.UTF_8));
            page.put(key, value);
        }

        byte[] bytes = page.toByteArray();
        Page restored = Page.deserialize(bytes, PageCapacityConfig.DEFAULT);

        assertEquals(page.getPageId(), restored.getPageId());
        assertEquals(page.getEntryCount(), restored.getEntryCount());

        for (int i = 0; i < 10; i++) {
            IndexKey key = IndexKey.orderedBytes(("key-" + String.format("%02d", i)).getBytes(StandardCharsets.UTF_8));
            IndexValue original = page.get(key);
            IndexValue restoredValue = restored.get(key);
            assertNotNull(restoredValue);
            assertArrayEquals(original.getValueData(), restoredValue.getValueData());
        }
    }

    @Test
    void testHasSpaceForEntry() {
        PageCapacityConfig smallConfig = PageCapacityConfig.sizeBased(100, 100);
        Page smallPage = Page.createPage(Page.PageType.LEAF, 1, smallConfig, null);

        IndexKey key = IndexKey.orderedBytes("key".getBytes(StandardCharsets.UTF_8));
        IndexValue value = IndexValue.normal(new byte[50]);

        assertTrue(smallPage.hasSpaceForEntry(IndexPair.of(key, value)));

        smallPage.put(key, value);

        IndexKey key2 = IndexKey.orderedBytes("key2".getBytes(StandardCharsets.UTF_8));
        IndexValue value2 = IndexValue.normal(new byte[100]);

        assertFalse(smallPage.hasSpaceForEntry(IndexPair.of(key2, value2)));
    }

    @Test
    void testGetMaxKey() {
        assertNull(page.getMaxKey());

        IndexKey key1 = IndexKey.orderedBytes("key-a".getBytes(StandardCharsets.UTF_8));
        IndexKey key2 = IndexKey.orderedBytes("key-b".getBytes(StandardCharsets.UTF_8));
        IndexKey key3 = IndexKey.orderedBytes("key-c".getBytes(StandardCharsets.UTF_8));

        page.put(key2, IndexValue.normal("v2".getBytes(StandardCharsets.UTF_8)));
        assertEquals(key2, page.getMaxKey());

        page.put(key1, IndexValue.normal("v1".getBytes(StandardCharsets.UTF_8)));
        assertEquals(key2, page.getMaxKey());

        page.put(key3, IndexValue.normal("v3".getBytes(StandardCharsets.UTF_8)));
        assertEquals(key3, page.getMaxKey());
    }

    @Test
    void testGetMinKey() {
        assertNull(page.getMinKey());

        IndexKey key1 = IndexKey.orderedBytes("key-a".getBytes(StandardCharsets.UTF_8));
        IndexKey key2 = IndexKey.orderedBytes("key-b".getBytes(StandardCharsets.UTF_8));

        page.put(key2, IndexValue.normal("v2".getBytes(StandardCharsets.UTF_8)));
        assertEquals(key2, page.getMinKey());

        page.put(key1, IndexValue.normal("v1".getBytes(StandardCharsets.UTF_8)));
        assertEquals(key1, page.getMinKey());
    }

    @Test
    void testSplit() {
        for (int i = 0; i < 10; i++) {
            IndexKey key = IndexKey.orderedBytes(("key-" + String.format("%02d", i)).getBytes(StandardCharsets.UTF_8));
            IndexValue value = IndexValue.normal(("value-" + i).getBytes(StandardCharsets.UTF_8));
            page.put(key, value);
        }

        Page rightPage = page.split(2);

        assertEquals(5, page.getEntryCount());
        assertEquals(5, rightPage.getEntryCount());

        for (int i = 0; i < 5; i++) {
            IndexKey key = IndexKey.orderedBytes(("key-" + String.format("%02d", i)).getBytes(StandardCharsets.UTF_8));
            assertNotNull(page.get(key));
            assertNull(rightPage.get(key));
        }

        for (int i = 5; i < 10; i++) {
            IndexKey key = IndexKey.orderedBytes(("key-" + String.format("%02d", i)).getBytes(StandardCharsets.UTF_8));
            assertNull(page.get(key));
            assertNotNull(rightPage.get(key));
        }
    }

    @Test
    void testOverwrite() {
        IndexKey key = IndexKey.orderedBytes("overwrite-key".getBytes(StandardCharsets.UTF_8));
        IndexValue value1 = IndexValue.normal("value1".getBytes(StandardCharsets.UTF_8));
        IndexValue value2 = IndexValue.normal("value2".getBytes(StandardCharsets.UTF_8));

        page.put(key, value1);
        page.put(key, value2);

        assertEquals(1, page.getEntryCount());
        IndexValue retrieved = page.get(key);
        assertArrayEquals(value2.getValueData(), retrieved.getValueData());
    }

    @Test
    void testTombstoneThrows() {
        IndexKey key = IndexKey.orderedBytes("tombstone-key".getBytes(StandardCharsets.UTF_8));
        IndexValue tombstone = IndexValue.tombstone();

        assertThrows(IllegalArgumentException.class, () -> page.put(key, tombstone));
    }

    @Test
    void testNullKeyThrows() {
        IndexValue value = IndexValue.normal("value".getBytes(StandardCharsets.UTF_8));
        assertThrows(IllegalArgumentException.class, () -> page.put(null, value));
    }

    @Test
    void testNullValueThrows() {
        IndexKey key = IndexKey.orderedBytes("key".getBytes(StandardCharsets.UTF_8));
        assertThrows(IllegalArgumentException.class, () -> page.put(key, (IndexValue) null));
    }

    @Test
    void testIsEmpty() {
        assertTrue(page.isEmpty());

        IndexKey key = IndexKey.orderedBytes("key".getBytes(StandardCharsets.UTF_8));
        IndexValue value = IndexValue.normal("value".getBytes(StandardCharsets.UTF_8));
        page.put(key, value);

        assertFalse(page.isEmpty());
    }

    @Test
    void testIsLeafIsIndex() {
        assertTrue(page.isLeaf());
        assertFalse(page.isIndex());
    }
}
