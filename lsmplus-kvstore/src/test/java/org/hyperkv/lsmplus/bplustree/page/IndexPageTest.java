package org.hyperkv.lsmplus.bplustree.page;
import org.hyperkv.lsmplus.bplustree.PageCapacityConfig;

import org.hyperkv.lsmplus.api.model.IndexKey;
import org.hyperkv.lsmplus.storage.SegmentLocation;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;

import java.nio.charset.StandardCharsets;
import java.util.List;
import java.util.UUID;

import static org.junit.jupiter.api.Assertions.*;

class IndexPageTest {

    private Page page;
    private SegmentLocation testLocation;

    @BeforeEach
    void setUp() {
        page = Page.createPage(Page.PageType.BRANCH, 1, PageCapacityConfig.DEFAULT, null);
        testLocation = new SegmentLocation(UUID.randomUUID(), 4096, 100);
    }

    @Test
    void testPutAndGetChildLocation() {
        IndexKey key = IndexKey.orderedBytes("test-key".getBytes(StandardCharsets.UTF_8));
        SegmentLocation location = new SegmentLocation(UUID.randomUUID(), 8192, 200);

        page.put(key, location);

        SegmentLocation retrieved = page.getChildLocation(key);
        assertEquals(location, retrieved);
    }

    @Test
    void testGetChildLocationFindsFloor() {
        IndexKey key1 = IndexKey.orderedBytes("key-10".getBytes(StandardCharsets.UTF_8));
        IndexKey key2 = IndexKey.orderedBytes("key-20".getBytes(StandardCharsets.UTF_8));
        IndexKey key3 = IndexKey.orderedBytes("key-30".getBytes(StandardCharsets.UTF_8));

        SegmentLocation loc1 = new SegmentLocation(UUID.randomUUID(), 1000, 100);
        SegmentLocation loc2 = new SegmentLocation(UUID.randomUUID(), 2000, 100);
        SegmentLocation loc3 = new SegmentLocation(UUID.randomUUID(), 3000, 100);

        page.put(key1, loc1);
        page.put(key2, loc2);
        page.put(key3, loc3);

        assertEquals(loc1, page.getChildLocation(IndexKey.orderedBytes("key-15".getBytes(StandardCharsets.UTF_8))));
        assertEquals(loc2, page.getChildLocation(IndexKey.orderedBytes("key-25".getBytes(StandardCharsets.UTF_8))));
        assertEquals(loc3, page.getChildLocation(IndexKey.orderedBytes("key-35".getBytes(StandardCharsets.UTF_8))));
    }

    @Test
    void testGetChildLocationReturnsFirstEntry() {
        IndexKey key1 = IndexKey.orderedBytes("key-10".getBytes(StandardCharsets.UTF_8));
        SegmentLocation loc1 = new SegmentLocation(UUID.randomUUID(), 1000, 100);
        page.put(key1, loc1);

        IndexKey key2 = IndexKey.orderedBytes("key-20".getBytes(StandardCharsets.UTF_8));
        SegmentLocation loc2 = new SegmentLocation(UUID.randomUUID(), 8192, 200);
        page.put(key2, loc2);

        IndexKey searchKey = IndexKey.orderedBytes("key-05".getBytes(StandardCharsets.UTF_8));
        assertEquals(loc1, page.getChildLocation(searchKey));
    }

    @Test
    void testRangeQuery() {
        for (int i = 0; i < 10; i++) {
            IndexKey key = IndexKey.orderedBytes(("key-" + String.format("%02d", i)).getBytes(StandardCharsets.UTF_8));
            SegmentLocation location = new SegmentLocation(UUID.randomUUID(), i * 1000, 100);
            page.put(key, location);
        }

        IndexKey start = IndexKey.orderedBytes("key-03".getBytes(StandardCharsets.UTF_8));
        IndexKey end = IndexKey.orderedBytes("key-07".getBytes(StandardCharsets.UTF_8));

        List<IndexPair> results = page.rangeQuery(start, end);

        assertEquals(4, results.size());
    }

    @Test
    void testMultipleEntries() {
        for (int i = 0; i < 100; i++) {
            IndexKey key = IndexKey.orderedBytes(("key-" + String.format("%03d", i)).getBytes(StandardCharsets.UTF_8));
            SegmentLocation location = new SegmentLocation(UUID.randomUUID(), i * 1000, 100);
            page.put(key, location);
        }

        assertEquals(100, page.getEntryCount());

        for (int i = 0; i < 100; i++) {
            IndexKey key = IndexKey.orderedBytes(("key-" + String.format("%03d", i)).getBytes(StandardCharsets.UTF_8));
            SegmentLocation retrieved = page.getChildLocation(key);
            assertNotNull(retrieved);
        }
    }

    @Test
    void testSerialization() {
        for (int i = 0; i < 10; i++) {
            IndexKey key = IndexKey.orderedBytes(("key-" + String.format("%02d", i)).getBytes(StandardCharsets.UTF_8));
            SegmentLocation location = new SegmentLocation(UUID.randomUUID(), i * 1000, 100);
            page.put(key, location);
        }

        byte[] bytes = page.toByteArray();
        Page restored = Page.deserialize(bytes, PageCapacityConfig.DEFAULT);

        assertEquals(page.getPageId(), restored.getPageId());
        assertEquals(page.getEntryCount(), restored.getEntryCount());

        for (int i = 0; i < 10; i++) {
            IndexKey key = IndexKey.orderedBytes(("key-" + String.format("%02d", i)).getBytes(StandardCharsets.UTF_8));
            SegmentLocation original = page.getChildLocation(key);
            SegmentLocation restoredLocation = restored.getChildLocation(key);
            assertEquals(original, restoredLocation);
        }
    }

    @Test
    void testHasSpaceForEntry() {
        PageCapacityConfig smallConfig = PageCapacityConfig.sizeBased(50, 50);
        Page smallPage = Page.createPage(Page.PageType.BRANCH, 1, smallConfig, null);

        IndexKey key = IndexKey.orderedBytes("key".getBytes(StandardCharsets.UTF_8));
        SegmentLocation location = new SegmentLocation(UUID.randomUUID(), 1000, 100);

        assertTrue(smallPage.hasSpaceForEntry(IndexPair.of(key, location)));

        smallPage.put(key, location);

        IndexKey key2 = IndexKey.orderedBytes("key2".getBytes(StandardCharsets.UTF_8));
        assertFalse(smallPage.hasSpaceForEntry(IndexPair.of(key2, location)));
    }

    @Test
    void testGetMaxKey() {
        assertNull(page.getMaxKey());

        IndexKey key1 = IndexKey.orderedBytes("key-a".getBytes(StandardCharsets.UTF_8));
        IndexKey key2 = IndexKey.orderedBytes("key-b".getBytes(StandardCharsets.UTF_8));
        IndexKey key3 = IndexKey.orderedBytes("key-c".getBytes(StandardCharsets.UTF_8));

        page.put(key2, testLocation);
        assertEquals(key2, page.getMaxKey());

        page.put(key1, testLocation);
        assertEquals(key2, page.getMaxKey());

        page.put(key3, testLocation);
        assertEquals(key3, page.getMaxKey());
    }

    @Test
    void testSplit() {
        for (int i = 0; i < 10; i++) {
            IndexKey key = IndexKey.orderedBytes(("key-" + String.format("%02d", i)).getBytes(StandardCharsets.UTF_8));
            SegmentLocation location = new SegmentLocation(UUID.randomUUID(), i * 1000, 100);
            page.put(key, location);
        }

        Page rightPage = page.split(2);

        assertTrue(page.getEntryCount() >= 5);
        assertTrue(rightPage.getEntryCount() >= 5);

        for (int i = 0; i < 5; i++) {
            IndexKey key = IndexKey.orderedBytes(("key-" + String.format("%02d", i)).getBytes(StandardCharsets.UTF_8));
            assertNotNull(page.getChildLocation(key));
        }

        for (int i = 5; i < 10; i++) {
            IndexKey key = IndexKey.orderedBytes(("key-" + String.format("%02d", i)).getBytes(StandardCharsets.UTF_8));
            assertNotNull(rightPage.getChildLocation(key));
        }
    }

    @Test
    void testDelete() {
        IndexKey key = IndexKey.orderedBytes("delete-key".getBytes(StandardCharsets.UTF_8));
        SegmentLocation location = new SegmentLocation(UUID.randomUUID(), 1000, 100);

        page.put(key, location);
        assertNotNull(page.getChildLocation(key));

        page.delete(key);

        assertNull(page.getChildLocation(key));
    }

    @Test
    void testNullKeyThrows() {
        assertThrows(IllegalArgumentException.class, () -> page.put(null, testLocation));
    }

    @Test
    void testNullLocationThrows() {
        IndexKey key = IndexKey.orderedBytes("key".getBytes(StandardCharsets.UTF_8));
        assertThrows(IllegalArgumentException.class, () -> page.put(key, (SegmentLocation) null));
    }

    @Test
    void testIsEmpty() {
        assertTrue(page.isEmpty());

        IndexKey key = IndexKey.orderedBytes("key".getBytes(StandardCharsets.UTF_8));
        page.put(key, testLocation);

        assertFalse(page.isEmpty());
    }

    @Test
    void testIsLeafIsIndex() {
        assertFalse(page.isLeaf());
        assertTrue(page.isIndex());
    }
}
