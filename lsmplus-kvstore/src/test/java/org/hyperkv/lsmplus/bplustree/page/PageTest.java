package org.hyperkv.lsmplus.bplustree.page;
import org.hyperkv.lsmplus.bplustree.PageCapacityConfig;

import org.hyperkv.lsmplus.api.model.IndexKey;
import org.hyperkv.lsmplus.api.model.IndexValue;
import org.hyperkv.lsmplus.proto.Common;
import org.hyperkv.lsmplus.storage.SegmentLocation;
import org.junit.jupiter.api.Test;

import java.nio.charset.StandardCharsets;
import java.util.UUID;

import static org.junit.jupiter.api.Assertions.*;

class PageTest {

    @Test
    void testPageTypeEnum() {
        assertEquals(Common.PageType.PAGE_LEAF, Page.PageType.LEAF.toProtoType());
        assertEquals(Common.PageType.PAGE_BRANCH, Page.PageType.BRANCH.toProtoType());
        assertEquals(Common.PageType.PAGE_ROOT, Page.PageType.ROOT.toProtoType());
        assertEquals(Page.PageType.LEAF, Page.PageType.fromProto(Common.PageType.PAGE_LEAF));
        assertEquals(Page.PageType.BRANCH, Page.PageType.fromProto(Common.PageType.PAGE_BRANCH));
        assertEquals(Page.PageType.ROOT, Page.PageType.fromProto(Common.PageType.PAGE_ROOT));
    }

    @Test
    void testPageTypeFromInvalidValue() {
        assertThrows(IllegalArgumentException.class, () -> Page.PageType.fromProto(Common.PageType.UNRECOGNIZED));
    }

    @Test
    void testCreateLeaf() {
        Page page = Page.createPage(Page.PageType.LEAF, 1, PageCapacityConfig.DEFAULT, null);

        assertTrue(page.isLeaf());
        assertFalse(page.isIndex());
        assertEquals(1, page.getPageId());
        assertEquals(8 * 1024, page.getMaxSize());
        assertEquals(0, page.getEntryCount());
        assertTrue(page.isEmpty());
    }

    @Test
    void testCreateBranch() {
        Page page = Page.createPage(Page.PageType.BRANCH, 1, PageCapacityConfig.DEFAULT, null);

        assertTrue(page.isIndex());
        assertTrue(page.isBranch());
        assertFalse(page.isRoot());
        assertFalse(page.isLeaf());
        assertEquals(1, page.getPageId());
        assertEquals(64 * 1024, page.getMaxSize());
        assertEquals(0, page.getEntryCount());
        assertTrue(page.isEmpty());
    }

    @Test
    void testCreateRoot() {
        Page page = Page.createPage(Page.PageType.ROOT, 1, PageCapacityConfig.DEFAULT, null);

        assertTrue(page.isIndex());
        assertTrue(page.isRoot());
        assertFalse(page.isBranch());
        assertFalse(page.isLeaf());
        assertEquals(1, page.getPageId());
        assertEquals(64 * 1024, page.getMaxSize());
        assertEquals(0, page.getEntryCount());
        assertTrue(page.isEmpty());
    }

    @Test
    void testLeafPagePutAndGet() {
        Page page = Page.createPage(Page.PageType.LEAF, 1, PageCapacityConfig.DEFAULT, null);
        IndexKey key = IndexKey.orderedBytes("test-key".getBytes(StandardCharsets.UTF_8));
        IndexValue value = IndexValue.normal("test-value".getBytes(StandardCharsets.UTF_8));

        page.put(key, value);

        assertEquals(1, page.getEntryCount());
        IndexValue retrieved = page.get(key);
        assertNotNull(retrieved);
        assertArrayEquals(value.getValueData(), retrieved.getValueData());
    }

    @Test
    void testIndexPagePutAndGet() {
        Page page = Page.createPage(Page.PageType.BRANCH, 1, PageCapacityConfig.DEFAULT, null);
        IndexKey key = IndexKey.orderedBytes("test-key".getBytes(StandardCharsets.UTF_8));
        SegmentLocation location = new SegmentLocation(UUID.randomUUID(), 1000, 100);

        page.put(key, location);

        assertEquals(1, page.getEntryCount());
        SegmentLocation retrieved = page.getChildLocation(key);
        assertNotNull(retrieved);
        assertEquals(location, retrieved);
    }

    @Test
    void testLeafPageSerialization() {
        Page page = Page.createPage(Page.PageType.LEAF, 1, PageCapacityConfig.DEFAULT, null);
        IndexKey key1 = IndexKey.orderedBytes("key1".getBytes(StandardCharsets.UTF_8));
        IndexValue value1 = IndexValue.normal("value1".getBytes(StandardCharsets.UTF_8));
        page.put(key1, value1);

        byte[] serialized = page.toByteArray();
        Page deserialized = Page.deserialize(serialized, PageCapacityConfig.DEFAULT);

        assertTrue(deserialized.isLeaf());
        assertEquals(1, deserialized.getPageId());

        IndexValue retrieved = deserialized.get(key1);
        assertNotNull(retrieved);
        assertArrayEquals("value1".getBytes(StandardCharsets.UTF_8), retrieved.getValueData());
    }

    @Test
    void testIndexPageSerialization() {
        Page page = Page.createPage(Page.PageType.BRANCH, 1, PageCapacityConfig.DEFAULT, null);
        
        IndexKey key1 = IndexKey.orderedBytes("key1".getBytes(StandardCharsets.UTF_8));
        SegmentLocation loc1 = new SegmentLocation(UUID.randomUUID(), 0, 100);
        page.put(key1, loc1);

        IndexKey key2 = IndexKey.orderedBytes("key2".getBytes(StandardCharsets.UTF_8));
        SegmentLocation loc2 = new SegmentLocation(UUID.randomUUID(), 100, 200);
        page.put(key2, loc2);

        byte[] serialized = page.toByteArray();
        Page deserialized = Page.deserialize(serialized, PageCapacityConfig.DEFAULT);

        assertTrue(deserialized.isIndex());
        assertEquals(1, deserialized.getPageId());

        assertEquals(loc1, deserialized.getChildLocation(key1));
        assertEquals(loc2, deserialized.getChildLocation(key2));
    }

    @Test
    void testGetFreeSpace() {
        Page page = Page.createPage(Page.PageType.LEAF, 1, PageCapacityConfig.DEFAULT, null);

        assertEquals(8 * 1024, page.getFreeSpace());
    }

    @Test
    void testToString() {
        Page page = Page.createPage(Page.PageType.LEAF, 123, PageCapacityConfig.DEFAULT, null);

        String str = page.toString();
        assertTrue(str.contains("LEAF"));
        assertTrue(str.contains("123"));
    }

    @Test
    void testNullPageTypeThrows() {
        assertThrows(IllegalArgumentException.class, () -> new Page(null, 1, PageCapacityConfig.DEFAULT, null));
    }

    @Test
    void testLeafPageCannotPutLocation() {
        Page page = Page.createPage(Page.PageType.LEAF, 1, PageCapacityConfig.DEFAULT, null);
        IndexKey key = IndexKey.orderedBytes("key".getBytes(StandardCharsets.UTF_8));
        SegmentLocation location = new SegmentLocation(UUID.randomUUID(), 0, 100);

        assertThrows(IllegalArgumentException.class, () -> page.put(key, location));
    }

    @Test
    void testIndexPageCannotPutValue() {
        Page page = Page.createPage(Page.PageType.BRANCH, 1, PageCapacityConfig.DEFAULT, null);
        IndexKey key = IndexKey.orderedBytes("key".getBytes(StandardCharsets.UTF_8));
        IndexValue value = IndexValue.normal("value".getBytes(StandardCharsets.UTF_8));

        assertThrows(IllegalArgumentException.class, () -> page.put(key, value));
    }

    @Test
    void testLeafPageCannotStoreTombstone() {
        Page page = Page.createPage(Page.PageType.LEAF, 1, PageCapacityConfig.DEFAULT, null);
        IndexKey key = IndexKey.orderedBytes("key".getBytes(StandardCharsets.UTF_8));
        IndexValue tombstone = IndexValue.tombstone();

        assertThrows(IllegalArgumentException.class, () -> page.put(key, tombstone));
    }
}
