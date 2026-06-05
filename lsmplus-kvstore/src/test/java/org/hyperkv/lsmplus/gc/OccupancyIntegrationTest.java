package org.hyperkv.lsmplus.gc;

import org.hyperkv.lsmplus.api.model.IndexKey;
import org.hyperkv.lsmplus.api.model.IndexValue;
import org.hyperkv.lsmplus.bplustree.BPlusTree;
import org.hyperkv.lsmplus.bplustree.PageCapacityConfig;
import org.hyperkv.lsmplus.bplustree.PageManager;
import org.hyperkv.lsmplus.bplustree.TreeDumper;
import org.hyperkv.lsmplus.memory.MemoryTable;
import org.hyperkv.lsmplus.memory.MemoryTableManager;
import org.hyperkv.lsmplus.storage.ChunkManager;
import org.hyperkv.lsmplus.storage.OccupancyFile;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.io.TempDir;

import java.io.File;
import java.nio.file.Path;
import java.util.ArrayList;
import java.util.List;
import java.util.Map;
import java.util.TreeMap;
import java.util.UUID;

import static org.junit.jupiter.api.Assertions.*;

class OccupancyIntegrationTest {

    @TempDir
    Path tempDir;

    private PageCapacityConfig capacityConfig = PageCapacityConfig.entryCountBased(8,4);

    private PageManager createPageManager() throws Exception {
        File storageDir = tempDir.toFile();
        UUID ownerId = UUID.randomUUID();
        UUID namespaceId = UUID.randomUUID();
        ChunkManager chunkManager = new ChunkManager(storageDir.getAbsolutePath(), ownerId, namespaceId);
        return new PageManager(chunkManager, PageCapacityConfig.DEFAULT);
    }

    private List<Map.Entry<IndexKey, IndexValue>> mergeTables(List<MemoryTable> sealedTables) {
        TreeMap<IndexKey, IndexValue> merged = new TreeMap<>();
        for (MemoryTable table : sealedTables) {
            merged.putAll(table.getData());
        }
        return new ArrayList<>(merged.entrySet());
    }

    @Test
    void testOccupancyTrackingDuringDump() throws Exception {
        MemoryTableManager memTableManager = new MemoryTableManager(1024 * 1024);
        PageManager pageManager = createPageManager();
        ChunkManager chunkManager = pageManager.getChunkManager();
        
        MNSTracker mnsTracker = new MNSTracker();
        OccupancyManager occupancyManager = new OccupancyManager(
            tempDir.toFile().getAbsolutePath(),
            chunkManager,
            mnsTracker
        );
        
        BPlusTree tree = new BPlusTree(pageManager, capacityConfig);;
        TreeDumper treeDumper = new TreeDumper(tree, pageManager, occupancyManager);

        MemoryTable table = memTableManager.getActiveTable();
        for (int i = 0; i < 100; i++) {
            table.put(IndexKey.orderedBytes(("key" + i).getBytes()),
                      IndexValue.normal(("value" + i).getBytes()), null);
        }
        memTableManager.sealActiveTable();

        List<Map.Entry<IndexKey, IndexValue>> merged = mergeTables(memTableManager.getSealedTables());
        treeDumper.dump(merged, null);
        treeDumper.promoteRoot();

        assertNotNull(tree.getWriterRootLocation());
        
        OccupancyFile occupancyFile = occupancyManager.loadOccupancyRecord(1);
        assertNotNull(occupancyFile);
        assertEquals(1, occupancyFile.getVersion());
        assertTrue(occupancyFile.getMns() >= 0);
        assertFalse(occupancyFile.getDeltas().isEmpty());
        
        for (OccupancyFile.OccupancyDeltaEntry delta : occupancyFile.getDeltas()) {
            assertNotNull(delta.getChunkId());
            assertTrue(delta.getDeltaSize() > 0);
        }
    }

    @Test
    void testMultipleDumpsWithOccupancyTracking() throws Exception {
        MemoryTableManager memTableManager = new MemoryTableManager(1024 * 1024);
        PageManager pageManager = createPageManager();
        ChunkManager chunkManager = pageManager.getChunkManager();
        
        MNSTracker mnsTracker = new MNSTracker();
        OccupancyManager occupancyManager = new OccupancyManager(
            tempDir.toFile().getAbsolutePath(),
            chunkManager,
            mnsTracker
        );
        
        BPlusTree tree = new BPlusTree(pageManager, capacityConfig);;
        TreeDumper treeDumper = new TreeDumper(tree, pageManager, occupancyManager);

        MemoryTable table1 = memTableManager.getActiveTable();
        for (int i = 0; i < 50; i++) {
            table1.put(IndexKey.orderedBytes(("key" + i).getBytes()),
                      IndexValue.normal(("value" + i).getBytes()), null);
        }
        memTableManager.sealActiveTable();

        List<Map.Entry<IndexKey, IndexValue>> merged1 = mergeTables(memTableManager.getSealedTables());
        treeDumper.dump(merged1, null);
        treeDumper.promoteRoot();
        memTableManager.clearSealedTables();

        OccupancyFile occupancyFile1 = occupancyManager.loadOccupancyRecord(1);
        assertNotNull(occupancyFile1);
        assertFalse(occupancyFile1.getDeltas().isEmpty());

        MemoryTable table2 = memTableManager.getActiveTable();
        for (int i = 50; i < 100; i++) {
            table2.put(IndexKey.orderedBytes(("key" + i).getBytes()),
                      IndexValue.normal(("value" + i).getBytes()), null);
        }
        memTableManager.sealActiveTable();

        List<Map.Entry<IndexKey, IndexValue>> merged2 = mergeTables(memTableManager.getSealedTables());
        treeDumper.dump(merged2, null);
        treeDumper.promoteRoot();

        OccupancyFile occupancyFile2 = occupancyManager.loadOccupancyRecord(2);
        assertNotNull(occupancyFile2);
        assertFalse(occupancyFile2.getDeltas().isEmpty());

        assertTrue(mnsTracker.getCurrentMNS() >= 0);
        assertEquals(2, mnsTracker.getCurrentVersion());
    }

    @Test
    void testOccupancyManagerWithoutOccupancyManager() throws Exception {
        MemoryTableManager memTableManager = new MemoryTableManager(1024 * 1024);
        PageManager pageManager = createPageManager();
        
        BPlusTree tree = new BPlusTree(pageManager, capacityConfig);;
        TreeDumper treeDumper = new TreeDumper(tree, pageManager);

        MemoryTable table = memTableManager.getActiveTable();
        for (int i = 0; i < 10; i++) {
            table.put(IndexKey.orderedBytes(("key" + i).getBytes()),
                      IndexValue.normal(("value" + i).getBytes()), null);
        }
        memTableManager.sealActiveTable();

        List<Map.Entry<IndexKey, IndexValue>> merged = mergeTables(memTableManager.getSealedTables());
        treeDumper.dump(merged, null);
        treeDumper.promoteRoot();

        assertNotNull(tree.getWriterRootLocation());
    }
}
