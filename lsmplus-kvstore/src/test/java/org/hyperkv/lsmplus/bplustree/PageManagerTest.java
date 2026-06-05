package org.hyperkv.lsmplus.bplustree;

import org.hyperkv.lsmplus.api.model.IndexKey;
import org.hyperkv.lsmplus.api.model.IndexValue;
import org.hyperkv.lsmplus.bplustree.page.Page;
import org.hyperkv.lsmplus.storage.ChunkManager;
import org.hyperkv.lsmplus.storage.SegmentLocation;
import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.io.TempDir;

import java.io.File;
import java.io.IOException;
import java.nio.charset.StandardCharsets;
import java.util.List;
import java.util.UUID;

import static org.junit.jupiter.api.Assertions.*;

class PageManagerTest {

    @TempDir
    File tempDir;

    private ChunkManager chunkManager;
    private PageManager pageManager;

    @BeforeEach
    void setUp() throws IOException {
        UUID ownerId = UUID.randomUUID();
        UUID namespaceId = UUID.randomUUID();
        chunkManager = new ChunkManager(tempDir.getAbsolutePath(), ownerId, namespaceId);
        pageManager = new PageManager(chunkManager, PageCapacityConfig.DEFAULT, 100);
    }

    @AfterEach
    void tearDown() throws IOException {
        if (chunkManager != null) {
            chunkManager.close();
        }
    }

    @Test
    void testSavePageAsync() throws Exception {
        Page leafPage = Page.createPage(Page.PageType.LEAF, 1, PageCapacityConfig.DEFAULT, null);
        
        IndexKey key = IndexKey.orderedBytes("asyncKey".getBytes(StandardCharsets.UTF_8));
        IndexValue value = IndexValue.normal("asyncValue".getBytes(StandardCharsets.UTF_8));
        leafPage.put(key, value);
        leafPage.setLifecycle(Page.PageLifecycle.FLUSHABLE);

        var future = pageManager.savePageAsync(leafPage);
        assertNotNull(future);
        
        pageManager.flushAsyncWrites();
        
        SegmentLocation location = future.get();
        assertNotNull(location);
        assertNotNull(location.getChunkId());
        
        Page loadedPage = pageManager.getPage(location);
        assertNotNull(loadedPage);
        assertTrue(loadedPage.isLeaf());
        
        IndexValue loadedValue = loadedPage.get(key);
        assertNotNull(loadedValue);
        assertArrayEquals(value.getValueData(), loadedValue.getValueData());
    }

    @Test
    void testSaveIndexPageAsync() throws Exception {
        Page indexPage = Page.createPage(Page.PageType.BRANCH, 1, PageCapacityConfig.DEFAULT, null);
        
        SegmentLocation childLocation = new SegmentLocation(UUID.randomUUID(), 100, 200);
        IndexKey key = IndexKey.orderedBytes("asyncIndexKey".getBytes(StandardCharsets.UTF_8));
        indexPage.put(key, childLocation);
        indexPage.setLifecycle(Page.PageLifecycle.FLUSHABLE);

        var future = pageManager.savePageAsync(indexPage);
        assertNotNull(future);
        
        pageManager.flushAsyncWrites();
        
        SegmentLocation location = future.get();
        assertNotNull(location);

        Page loadedPage = pageManager.getPage(location);
        assertNotNull(loadedPage);
        assertTrue(loadedPage.isIndex());
    }

    @Test
    void testSavePagesAsync() throws Exception {
        List<Page> pages = new java.util.ArrayList<>();
        
        for (int i = 0; i < 5; i++) {
            Page leafPage = Page.createPage(Page.PageType.LEAF, i, PageCapacityConfig.DEFAULT, null);
            IndexKey key = IndexKey.orderedBytes(("asyncKey" + i).getBytes(StandardCharsets.UTF_8));
            IndexValue value = IndexValue.normal(("asyncValue" + i).getBytes(StandardCharsets.UTF_8));
            leafPage.put(key, value);
            leafPage.setLifecycle(Page.PageLifecycle.FLUSHABLE);
            pages.add(leafPage);
        }

        List<java.util.concurrent.CompletableFuture<SegmentLocation>> futures = 
            pageManager.savePagesAsync(pages);
        
        assertEquals(5, futures.size());
        
        pageManager.flushAsyncWrites();
        
        for (int i = 0; i < 5; i++) {
            SegmentLocation location = futures.get(i).get();
            assertNotNull(location);
            assertNotNull(location.getChunkId());
            
            Page loadedPage = pageManager.getPage(location);
            assertNotNull(loadedPage);
        }
    }

    @Test
    void testSavePageAsyncNullPage() {
        var future = pageManager.savePageAsync(null);
        assertNotNull(future);
        assertThrows(Exception.class, () -> future.get());
    }

    @Test
    void testSavePageAsyncInvalidLifecycle() {
        Page leafPage = Page.createPage(Page.PageType.LEAF, 1, PageCapacityConfig.DEFAULT, null);
        
        var future = pageManager.savePageAsync(leafPage);
        assertNotNull(future);
        assertThrows(Exception.class, () -> future.get());
    }

    @Test
    void testAsyncPageCacheUpdate() throws Exception {
        Page leafPage = Page.createPage(Page.PageType.LEAF, 1, PageCapacityConfig.DEFAULT, null);
        
        IndexKey key = IndexKey.orderedBytes("cacheKey".getBytes(StandardCharsets.UTF_8));
        IndexValue value = IndexValue.normal("cacheValue".getBytes(StandardCharsets.UTF_8));
        leafPage.put(key, value);
        leafPage.setLifecycle(Page.PageLifecycle.FLUSHABLE);

        int initialCacheSize = pageManager.getCacheSize();
        
        var future = pageManager.savePageAsync(leafPage);
        pageManager.flushAsyncWrites();
        
        SegmentLocation location = future.get();
        
        assertTrue(pageManager.getCacheSize() > initialCacheSize);
        
        Page cachedPage = pageManager.getPage(location);
        assertNotNull(cachedPage);
        assertSame(leafPage, cachedPage);
    }

    @Test
    void testGetNullLocation() {
        Page page = pageManager.getPage(null);
        assertNull(page);
    }

    @Test
    void testInvalidate() throws Exception {
        Page leafPage = Page.createPage(Page.PageType.LEAF, 1, PageCapacityConfig.DEFAULT, null);
        
        IndexKey key = IndexKey.orderedBytes("testKey".getBytes(StandardCharsets.UTF_8));
        IndexValue value = IndexValue.normal("testValue".getBytes(StandardCharsets.UTF_8));
        leafPage.put(key, value);
        leafPage.setLifecycle(Page.PageLifecycle.FLUSHABLE);

        var future = pageManager.savePageAsync(leafPage);
        pageManager.flushAsyncWrites();
        SegmentLocation location = future.get();
        
        assertEquals(1, pageManager.getCacheSize());

        pageManager.invalidate(location);
        assertEquals(0, pageManager.getCacheSize());
    }

    @Test
    void testClearCache() throws Exception {
        for (int i = 0; i < 5; i++) {
            Page leafPage = Page.createPage(Page.PageType.LEAF, i, PageCapacityConfig.DEFAULT, null);
            IndexKey key = IndexKey.orderedBytes(("key" + i).getBytes(StandardCharsets.UTF_8));
            IndexValue value = IndexValue.normal(("value" + i).getBytes(StandardCharsets.UTF_8));
            leafPage.put(key, value);
            leafPage.setLifecycle(Page.PageLifecycle.FLUSHABLE);
            pageManager.savePageAsync(leafPage);
        }
        
        pageManager.flushAsyncWrites();

        assertTrue(pageManager.getCacheSize() > 0);
        pageManager.clearCache();
        assertEquals(0, pageManager.getCacheSize());
    }
}
