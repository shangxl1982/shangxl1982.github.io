package org.hyperkv.lsmplus.bplustree;

import org.hyperkv.lsmplus.api.model.IndexKey;
import org.hyperkv.lsmplus.api.model.IndexValue;
import org.hyperkv.lsmplus.bplustree.page.Page;
import org.hyperkv.lsmplus.storage.SegmentLocation;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;

import java.nio.charset.StandardCharsets;
import java.util.List;
import java.util.Map;
import java.util.UUID;

import static org.junit.jupiter.api.Assertions.*;

class WriteBufferTest {

    private WriteBuffer writeBuffer;

    @BeforeEach
    void setUp() {
        writeBuffer = new WriteBuffer(10);
    }

    @Test
    void testPutAndGet() {
        Page page = Page.createPage(Page.PageType.LEAF, 1, PageCapacityConfig.DEFAULT, null);
        IndexKey key = IndexKey.orderedBytes("testKey".getBytes(StandardCharsets.UTF_8));
        IndexValue value = IndexValue.normal("testValue".getBytes(StandardCharsets.UTF_8));
        page.put(key, value);

        writeBuffer.put(1, page);

        assertTrue(writeBuffer.contains(1));
        Page retrieved = writeBuffer.get(1);
        assertNotNull(retrieved);
        assertEquals(1, writeBuffer.size());
    }

    @Test
    void testPutWithMaxKey() {
        Page page = Page.createPage(Page.PageType.LEAF, 1, PageCapacityConfig.DEFAULT, null);
        IndexKey key = IndexKey.orderedBytes("testKey".getBytes(StandardCharsets.UTF_8));
        IndexValue value = IndexValue.normal("testValue".getBytes(StandardCharsets.UTF_8));
        page.put(key, value);

        writeBuffer.put(1, page, key);

        assertEquals(key, writeBuffer.getPageMaxKey(1));
    }

    @Test
    void testRemove() {
        Page page = Page.createPage(Page.PageType.LEAF, 1, PageCapacityConfig.DEFAULT, null);
        IndexKey key = IndexKey.orderedBytes("testKey".getBytes(StandardCharsets.UTF_8));
        IndexValue value = IndexValue.normal("testValue".getBytes(StandardCharsets.UTF_8));
        page.put(key, value);

        writeBuffer.put(1, page);
        assertTrue(writeBuffer.contains(1));

        writeBuffer.remove(1);
        assertFalse(writeBuffer.contains(1));
        assertEquals(0, writeBuffer.size());
    }

    @Test
    void testIsFull() {
        WriteBuffer smallBuffer = new WriteBuffer(2);

        for (int i = 0; i < 2; i++) {
            Page page = Page.createPage(Page.PageType.LEAF, i, PageCapacityConfig.DEFAULT, null);
            smallBuffer.put(i, page);
        }

        assertTrue(smallBuffer.isFull());
    }

    @Test
    void testGetFlushablePageIds() {
        IndexKey key1 = IndexKey.orderedBytes("key1".getBytes(StandardCharsets.UTF_8));
        IndexKey key2 = IndexKey.orderedBytes("key2".getBytes(StandardCharsets.UTF_8));
        IndexKey key3 = IndexKey.orderedBytes("key3".getBytes(StandardCharsets.UTF_8));

        Page page1 = Page.createPage(Page.PageType.LEAF, 1, PageCapacityConfig.DEFAULT, null);
        page1.put(key1, IndexValue.normal("value1".getBytes(StandardCharsets.UTF_8)));
        writeBuffer.put(1, page1, key1);

        Page page2 = Page.createPage(Page.PageType.LEAF, 2, PageCapacityConfig.DEFAULT, null);
        page2.put(key2, IndexValue.normal("value2".getBytes(StandardCharsets.UTF_8)));
        writeBuffer.put(2, page2, key2);

        writeBuffer.setCurrentKey(key3);

        List<Integer> flushable = writeBuffer.getFlushablePageIds();
        assertEquals(2, flushable.size());
        assertTrue(flushable.contains(1));
        assertTrue(flushable.contains(2));
    }

    @Test
    void testGetFlushablePages() {
        IndexKey key1 = IndexKey.orderedBytes("key1".getBytes(StandardCharsets.UTF_8));
        IndexKey key3 = IndexKey.orderedBytes("key3".getBytes(StandardCharsets.UTF_8));

        Page page1 = Page.createPage(Page.PageType.LEAF, 1, PageCapacityConfig.DEFAULT, null);
        page1.put(key1, IndexValue.normal("value1".getBytes(StandardCharsets.UTF_8)));
        writeBuffer.put(1, page1, key1);

        writeBuffer.setCurrentKey(key3);

        List<Page> flushable = writeBuffer.getFlushablePages();
        assertEquals(1, flushable.size());
    }

    @Test
    void testSetPageLocation() {
        Page page = Page.createPage(Page.PageType.LEAF, 1, PageCapacityConfig.DEFAULT, null);
        writeBuffer.put(1, page);

        SegmentLocation location = new SegmentLocation(UUID.randomUUID(), 100, 200);
        writeBuffer.setPageLocation(1, location);

        assertEquals(location, writeBuffer.getPageLocation(1));
    }

    @Test
    void testClear() {
        for (int i = 0; i < 5; i++) {
            Page page = Page.createPage(Page.PageType.LEAF, i, PageCapacityConfig.DEFAULT, null);
            writeBuffer.put(i, page);
        }

        assertEquals(5, writeBuffer.size());
        writeBuffer.clear();
        assertEquals(0, writeBuffer.size());
        assertTrue(writeBuffer.isEmpty());
    }

    @Test
    void testGetPages() {
        for (int i = 0; i < 3; i++) {
            Page page = Page.createPage(Page.PageType.LEAF, i, PageCapacityConfig.DEFAULT, null);
            writeBuffer.put(i, page);
        }

        Map<Integer, Page> pages = writeBuffer.getPages();
        assertEquals(3, pages.size());
    }

    @Test
    void testUpdatePageMaxKey() {
        Page page = Page.createPage(Page.PageType.LEAF, 1, PageCapacityConfig.DEFAULT, null);
        writeBuffer.put(1, page);

        IndexKey newMaxKey = IndexKey.orderedBytes("newMaxKey".getBytes(StandardCharsets.UTF_8));
        writeBuffer.updatePageMaxKey(1, newMaxKey);

        assertEquals(newMaxKey, writeBuffer.getPageMaxKey(1));
    }

    @Test
    void testDefaultConstructor() {
        WriteBuffer defaultBuffer = new WriteBuffer();
        assertEquals(WriteBuffer.DEFAULT_MAX_SIZE, defaultBuffer.getMaxSize());
    }
}
