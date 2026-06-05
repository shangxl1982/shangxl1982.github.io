package org.hyperkv.lsmplus.bplustree;

import org.hyperkv.lsmplus.api.model.IndexKey;
import org.hyperkv.lsmplus.api.model.IndexValue;
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
import java.util.Map;
import java.util.UUID;

import static org.junit.jupiter.api.Assertions.*;

class BPlusTreeTest {

    @TempDir
    File tempDir;

    private ChunkManager chunkManager;
    private PageManager pageManager;
    private BPlusTree tree;

    @BeforeEach
    void setUp() throws IOException {
        UUID ownerId = UUID.randomUUID();
        UUID namespaceId = UUID.randomUUID();
        chunkManager = new ChunkManager(tempDir.getAbsolutePath(), ownerId, namespaceId);
        pageManager = new PageManager(chunkManager, PageCapacityConfig.entryCountBased(4,2));
        tree = new BPlusTree(pageManager, PageCapacityConfig.entryCountBased(4,2));
    }

    @AfterEach
    void tearDown() throws IOException {
        if (chunkManager != null) {
            chunkManager.close();
        }
    }

    @Test
    void testSearchOnEmptyTree() {
        IndexKey key = IndexKey.orderedBytes("testKey".getBytes(StandardCharsets.UTF_8));
        IndexValue result = tree.search(key);
        assertNull(result);
    }

    @Test
    void testRangeQueryOnEmptyTree() {
        IndexKey start = IndexKey.orderedBytes("a".getBytes(StandardCharsets.UTF_8));
        IndexKey end = IndexKey.orderedBytes("z".getBytes(StandardCharsets.UTF_8));
        
        List<Map.Entry<IndexKey, IndexValue>> results = tree.rangeQuery(start, end);
        assertTrue(results.isEmpty());
    }

    @Test
    void testGetVersion() {
        assertEquals(0, tree.getVersion());
        
        tree.incrementVersion();
        assertEquals(1, tree.getVersion());
        
        tree.incrementVersion();
        assertEquals(2, tree.getVersion());
    }

    @Test
    void testSetHeight() {
        tree.setHeight(3);
        assertEquals(3, tree.getHeight());
    }

    @Test
    void testSetVersion() {
        tree.setVersion(100);
        assertEquals(100, tree.getVersion());
    }


    @Test
    void testCustomPageSizes() {
        BPlusTree customTree = new BPlusTree(pageManager, 1024, 2048);
        assertEquals(1024, customTree.getLeafPageMaxSize());
        assertEquals(2048, customTree.getIndexPageMaxSize());
    }

    @Test
    void testGetPageManager() {
        assertEquals(pageManager, tree.getPageManager());
    }

    @Test
    void testToString() {
        String str = tree.toString();
        assertTrue(str.contains("version=0"));
        assertTrue(str.contains("height=0"));
    }

    @Test
    void testNullPageManager() {
        assertThrows(IllegalArgumentException.class, () -> new BPlusTree(null, PageCapacityConfig.DEFAULT));
    }

    @Test
    void testSearchNullKey() {
        assertThrows(IllegalArgumentException.class, () -> tree.search(null));
    }
}
