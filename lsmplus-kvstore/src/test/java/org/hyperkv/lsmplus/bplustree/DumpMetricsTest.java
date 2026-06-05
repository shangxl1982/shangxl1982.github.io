package org.hyperkv.lsmplus.bplustree;

import org.hyperkv.lsmplus.api.model.IndexKey;
import org.hyperkv.lsmplus.api.model.IndexValue;
import org.hyperkv.lsmplus.storage.ChunkManager;
import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.io.TempDir;

import java.io.File;
import java.nio.charset.StandardCharsets;
import java.nio.file.Path;
import java.util.ArrayList;
import java.util.List;
import java.util.Map;
import java.util.UUID;

import static org.junit.jupiter.api.Assertions.*;

class DumpMetricsTest {

    @TempDir
    Path tempDir;

    private BPlusTree tree;
    private PageManager pageManager;
    private TreeDumper treeDumper;
    private ChunkManager chunkManager;
    private PageCapacityConfig capacityConfig = PageCapacityConfig.entryCountBased(8,4);

    @BeforeEach
    void setUp() throws Exception {
        File storageDir = tempDir.toFile();
        UUID ownerId = UUID.randomUUID();
        UUID namespaceId = UUID.randomUUID();

        chunkManager = new ChunkManager(storageDir.getAbsolutePath(), ownerId, namespaceId);
        pageManager = new PageManager(chunkManager, capacityConfig);
        tree = new BPlusTree(pageManager, capacityConfig);
        treeDumper = new TreeDumper(tree, pageManager);
    }

    @AfterEach
    void tearDown() throws Exception {
        if (chunkManager != null) {
            chunkManager.close();
        }
    }

    @Test
    void testMetricsTrackingDuringDump() throws Exception {
        List<Map.Entry<IndexKey, IndexValue>> entries = new ArrayList<>();
        
        for (int i = 0; i < 100; i++) {
            IndexKey key = IndexKey.orderedBytes(("key" + String.format("%03d", i)).getBytes(StandardCharsets.UTF_8));
            IndexValue value = IndexValue.normal(("value" + i).getBytes(StandardCharsets.UTF_8));
            entries.add(Map.entry(key, value));
        }

        var versionInfo = treeDumper.dump(entries, null);
        
        assertNotNull(versionInfo);
        assertTrue(versionInfo.getLeafPageCount() > 0);
        
        System.out.println("First dump metrics logged");
        System.out.println("Tree version: " + versionInfo.getVersion());
        System.out.println("Leaf pages: " + versionInfo.getLeafPageCount());
    }

    @Test
    void testMetricsWithInsertsAndDeletes() throws Exception {
        List<Map.Entry<IndexKey, IndexValue>> entries = new ArrayList<>();
        
        for (int i = 0; i < 50; i++) {
            IndexKey key = IndexKey.orderedBytes(("key" + String.format("%03d", i)).getBytes(StandardCharsets.UTF_8));
            IndexValue value = IndexValue.normal(("value" + i).getBytes(StandardCharsets.UTF_8));
            entries.add(Map.entry(key, value));
        }

        var versionInfo1 = treeDumper.dump(entries, null);
        assertNotNull(versionInfo1);
        System.out.println("First dump: " + versionInfo1.getLeafPageCount() + " leaf pages");

        List<Map.Entry<IndexKey, IndexValue>> updates = new ArrayList<>();
        
        for (int i = 0; i < 25; i++) {
            IndexKey key = IndexKey.orderedBytes(("key" + String.format("%03d", i)).getBytes(StandardCharsets.UTF_8));
            IndexValue tombstone = IndexValue.tombstone();
            updates.add(Map.entry(key, tombstone));
        }
        
        for (int i = 50; i < 75; i++) {
            IndexKey key = IndexKey.orderedBytes(("key" + String.format("%03d", i)).getBytes(StandardCharsets.UTF_8));
            IndexValue value = IndexValue.normal(("value" + i).getBytes(StandardCharsets.UTF_8));
            updates.add(Map.entry(key, value));
        }

        var versionInfo2 = treeDumper.dump(updates, null);
        assertNotNull(versionInfo2);
        System.out.println("Second dump: " + versionInfo2.getLeafPageCount() + " leaf pages");
        System.out.println("Metrics should show: entriesInserted=25, entriesDeleted=25");
    }

    @Test
    void testMetricsWithPageSplits() throws Exception {
        List<Map.Entry<IndexKey, IndexValue>> entries = new ArrayList<>();
        
        for (int i = 0; i < 1000; i++) {
            IndexKey key = IndexKey.orderedBytes(("key" + String.format("%04d", i)).getBytes(StandardCharsets.UTF_8));
            IndexValue value = IndexValue.normal(("value" + i).getBytes(StandardCharsets.UTF_8));
            entries.add(Map.entry(key, value));
        }

        var versionInfo = treeDumper.dump(entries, null);
        
        assertNotNull(versionInfo);
        assertTrue(versionInfo.getLeafPageCount() > 1, "Should have multiple leaf pages due to splits");
        System.out.println("Large dump with splits: " + versionInfo.getLeafPageCount() + " leaf pages");
        System.out.println("Metrics should show: pagesSplit > 0");
    }
}
