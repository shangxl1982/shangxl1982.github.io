package org.hyperkv.lsmplus.bplustree;

import org.hyperkv.lsmplus.api.model.IndexKey;
import org.hyperkv.lsmplus.bplustree.page.IndexPair;
import org.hyperkv.lsmplus.bplustree.page.Page;
import org.hyperkv.lsmplus.storage.SegmentLocation;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;

import java.util.List;
import java.util.Map;
import java.util.Set;
import java.util.UUID;

import static org.junit.jupiter.api.Assertions.*;

class LevelWriteBufferTest {

    private LevelWriteBuffer writeBuffer;

    @BeforeEach
    void setUp() {
        writeBuffer = new LevelWriteBuffer(100);
    }

    @Test
    void testPutAndGetPage() {
        Page page = createTestLeafPage(1L, "key1");
        writeBuffer.put(0, page);

        Page retrieved = writeBuffer.get(0, 1L);
        assertNotNull(retrieved);
        assertEquals(1L, retrieved.getPageId());
    }

    @Test
    void testContainsPage() {
        Page page = createTestLeafPage(1L, "key1");
        writeBuffer.put(0, page);

        assertTrue(writeBuffer.contains(0, 1L));
        assertFalse(writeBuffer.contains(0, 2L));
    }

    @Test
    void testSetAndGetPageLocation() {
        Page page = createTestLeafPage(1L, "key1");
        writeBuffer.put(0, page);

        SegmentLocation location = new SegmentLocation(UUID.randomUUID(), 100L, 200);
        writeBuffer.setPageLocation(0, 1L, location);

        SegmentLocation retrieved = writeBuffer.getPageLocation(0, 1L);
        assertNotNull(retrieved);
        assertEquals(location, retrieved);
    }

    @Test
    void testFindPageLocation() {
        Page page = createTestLeafPage(1L, "key1");
        writeBuffer.put(0, page);

        SegmentLocation location = new SegmentLocation(UUID.randomUUID(), 100L, 200);
        writeBuffer.setPageLocation(0, 1L, location);

        SegmentLocation found = writeBuffer.findPageLocation(1L);
        assertNotNull(found);
        assertEquals(location, found);
    }

    @Test
    void testFindPage() {
        Page page = createTestLeafPage(1L, "key1");
        writeBuffer.put(0, page);

        Page found = writeBuffer.findPage(1L);
        assertNotNull(found);
        assertEquals(1L, found.getPageId());
    }

    @Test
    void testGetFlushablePageIds() {
        Page page1 = createTestLeafPage(1L, "key1");
        Page page2 = createTestLeafPage(2L, "key3");

        writeBuffer.put(0, page1);
        writeBuffer.put(0, page2);

        writeBuffer.setCurrentKey(IndexKey.orderedBytes("key2".getBytes()));

        List<Long> flushable = writeBuffer.getFlushablePageIds(0);
        assertEquals(1, flushable.size());
        assertTrue(flushable.contains(1L));
    }

    @Test
    void testGetPages() {
        Page page1 = createTestLeafPage(1L, "key1");
        Page page2 = createTestLeafPage(2L, "key2");

        writeBuffer.put(0, page1);
        writeBuffer.put(0, page2);

        Map<Long, Page> pages = writeBuffer.getPages(0);
        assertEquals(2, pages.size());
        assertTrue(pages.containsKey(1L));
        assertTrue(pages.containsKey(2L));
    }

    @Test
    void testRemovePage() {
        Page page = createTestLeafPage(1L, "key1");
        writeBuffer.put(0, page);

        writeBuffer.remove(0, 1L);

        assertFalse(writeBuffer.contains(0, 1L));
    }

    @Test
    void testClearLevel() {
        Page page1 = createTestLeafPage(1L, "key1");
        Page page2 = createTestLeafPage(2L, "key2");

        writeBuffer.put(0, page1);
        writeBuffer.put(0, page2);

        writeBuffer.clearLevel(0);

        assertTrue(writeBuffer.isLevelEmpty(0));
    }

    @Test
    void testClearAll() {
        Page leafPage = createTestLeafPage(1L, "key1");
        Page indexPage = createTestIndexPage(-1L, "key1");

        writeBuffer.put(0, leafPage);
        writeBuffer.put(1, indexPage);

        writeBuffer.clearAll();

        assertEquals(0, writeBuffer.getTotalPageCount());
    }

    @Test
    void testGetLevelCount() {
        assertEquals(0, writeBuffer.getLevelCount());

        Page leafPage = createTestLeafPage(1L, "key1");
        writeBuffer.put(0, leafPage);
        assertEquals(1, writeBuffer.getLevelCount());

        Page indexPage = createTestIndexPage(-1L, "key1");
        writeBuffer.put(1, indexPage);
        assertEquals(2, writeBuffer.getLevelCount());
    }

    @Test
    void testGetTotalPageCount() {
        assertEquals(0, writeBuffer.getTotalPageCount());

        Page page1 = createTestLeafPage(1L, "key1");
        Page page2 = createTestLeafPage(2L, "key2");
        writeBuffer.put(0, page1);
        writeBuffer.put(0, page2);

        assertEquals(2, writeBuffer.getTotalPageCount());
    }

    @Test
    void testGetMaxLevel() {
        assertEquals(-1, writeBuffer.getMaxLevel());

        writeBuffer.put(0, createTestLeafPage(1L, "key1"));
        assertEquals(0, writeBuffer.getMaxLevel());

        writeBuffer.put(2, createTestIndexPage(-1L, "key1"));
        assertEquals(2, writeBuffer.getMaxLevel());
    }

    @Test
    void testGetLevelsInFlushOrder() {
        writeBuffer.put(2, createTestIndexPage(-1L, "key1"));
        writeBuffer.put(0, createTestLeafPage(1L, "key1"));
        writeBuffer.put(1, createTestIndexPage(-2L, "key1"));

        List<Integer> levels = writeBuffer.getLevelsInFlushOrder();
        assertEquals(List.of(0, 1, 2), levels);
    }

    @Test
    void testContainsPageAcrossLevels() {
        Page leafPage = createTestLeafPage(1L, "key1");
        Page indexPage = createTestIndexPage(-1L, "key1");

        writeBuffer.put(0, leafPage);
        writeBuffer.put(1, indexPage);

        assertTrue(writeBuffer.containsPage(1L));
        assertTrue(writeBuffer.containsPage(-1L));
        assertFalse(writeBuffer.containsPage(999L));
    }

    @Test
    void testGetAllPageIds() {
        Page leafPage = createTestLeafPage(1L, "key1");
        Page indexPage = createTestIndexPage(-1L, "key1");

        writeBuffer.put(0, leafPage);
        writeBuffer.put(1, indexPage);

        Set<Long> allIds = writeBuffer.getAllPageIds();
        assertEquals(2, allIds.size());
        assertTrue(allIds.contains(1L));
        assertTrue(allIds.contains(-1L));
    }

    @Test
    void testHandleSplitPages() {
        Page originalPage = createTestLeafPage(1L, "key2");
        writeBuffer.put(0, originalPage);

        Page splitPage = createTestLeafPage(2L, "key2");

        writeBuffer.handleSplitPages(0, 1L, originalPage, 2L, splitPage);

        assertTrue(writeBuffer.contains(0, 1L));
        assertTrue(writeBuffer.contains(0, 2L));
    }

    @Test
    void testNullPageIgnored() {
        writeBuffer.put(0, null);
        assertEquals(0, writeBuffer.getTotalPageCount());
    }

    @Test
    void testConstructorWithInvalidSizeThrows() {
        assertThrows(IllegalArgumentException.class, () -> new LevelWriteBuffer(0));
        assertThrows(IllegalArgumentException.class, () -> new LevelWriteBuffer(-1));
    }

    private Page createTestLeafPage(long pageId, String maxKey) {
        Page page = Page.createPage(Page.PageType.LEAF, pageId, PageCapacityConfig.DEFAULT, null);
        page.put(IndexKey.orderedBytes(maxKey.getBytes()), 
                 org.hyperkv.lsmplus.api.model.IndexValue.normal("value".getBytes()));
        return page;
    }

    private Page createTestIndexPage(long pageId, String maxKey) {
        Page page = Page.createPage(Page.PageType.BRANCH, pageId, PageCapacityConfig.DEFAULT, null);
        SegmentLocation childLoc = VirtualSegmentLocation.create(1L);
        page.put(IndexKey.orderedBytes(maxKey.getBytes()), childLoc);
        return page;
    }
}
