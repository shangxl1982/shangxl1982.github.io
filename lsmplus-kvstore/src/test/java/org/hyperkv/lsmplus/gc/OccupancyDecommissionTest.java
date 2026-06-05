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

class OccupancyDecommissionTest {

    @TempDir
    Path tempDir;

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
    void testOccupancyWithDeletesAndUpdates() throws Exception {
        MemoryTableManager memTableManager = new MemoryTableManager(1024 * 1024);
        PageManager pageManager = createPageManager();
        ChunkManager chunkManager = pageManager.getChunkManager();
        
        MNSTracker mnsTracker = new MNSTracker();
        OccupancyManager occupancyManager = new OccupancyManager(
            tempDir.toFile().getAbsolutePath(),
            chunkManager,
            mnsTracker
        );
        
        BPlusTree tree = new BPlusTree(pageManager, PageCapacityConfig.entryCountBased(8,4));
        TreeDumper treeDumper = new TreeDumper(tree, pageManager, occupancyManager);

        System.out.println("=== Version 1: Initial inserts ===");
        MemoryTable table1 = memTableManager.getActiveTable();
        for (int i = 0; i < 100; i++) {
            table1.put(IndexKey.orderedBytes(("key" + String.format("%03d", i)).getBytes()),
                      IndexValue.normal(("value" + i).getBytes()), null);
        }
        memTableManager.sealActiveTable();

        List<Map.Entry<IndexKey, IndexValue>> merged1 = mergeTables(memTableManager.getSealedTables());
        treeDumper.dump(merged1, null);
        treeDumper.promoteRoot();
        memTableManager.clearSealedTables();

        OccupancyFile occupancyFile1 = occupancyManager.loadOccupancyRecord(1);
        assertNotNull(occupancyFile1);
        System.out.println("Version 1 deltas: " + occupancyFile1.getDeltas().size());
        for (OccupancyFile.OccupancyDeltaEntry delta : occupancyFile1.getDeltas()) {
            System.out.println("  Chunk: " + delta.getChunkId() + ", Delta: " + delta.getDeltaSize() + " bytes");
        }
        
        System.out.println("\n=== Version 2: Updates (replace existing values) ===");
        MemoryTable table2 = memTableManager.getActiveTable();
        for (int i = 0; i < 50; i++) {
            table2.put(IndexKey.orderedBytes(("key" + String.format("%03d", i)).getBytes()),
                      IndexValue.normal(("updated-value" + i).getBytes()), null);
        }
        memTableManager.sealActiveTable();

        List<Map.Entry<IndexKey, IndexValue>> merged2 = mergeTables(memTableManager.getSealedTables());
        treeDumper.dump(merged2, null);
        treeDumper.promoteRoot();
        memTableManager.clearSealedTables();

        OccupancyFile occupancyFile2 = occupancyManager.loadOccupancyRecord(2);
        assertNotNull(occupancyFile2);
        System.out.println("Version 2 deltas: " + occupancyFile2.getDeltas().size());
        boolean hasNegativeDelta = false;
        for (OccupancyFile.OccupancyDeltaEntry delta : occupancyFile2.getDeltas()) {
            System.out.println("  Chunk: " + delta.getChunkId() + ", Delta: " + delta.getDeltaSize() + " bytes" + 
                (delta.getDeltaSize() < 0 ? " (DECOMMISSION)" : ""));
            if (delta.getDeltaSize() < 0) {
                hasNegativeDelta = true;
            }
        }
        
        System.out.println("\n=== Version 3: Deletes (tombstones) ===");
        MemoryTable table3 = memTableManager.getActiveTable();
        for (int i = 0; i < 30; i++) {
            table3.put(IndexKey.orderedBytes(("key" + String.format("%03d", i)).getBytes()),
                      IndexValue.tombstone(), null);
        }
        memTableManager.sealActiveTable();

        List<Map.Entry<IndexKey, IndexValue>> merged3 = mergeTables(memTableManager.getSealedTables());
        treeDumper.dump(merged3, null);
        treeDumper.promoteRoot();

        OccupancyFile occupancyFile3 = occupancyManager.loadOccupancyRecord(3);
        assertNotNull(occupancyFile3);
        System.out.println("Version 3 deltas: " + occupancyFile3.getDeltas().size());
        for (OccupancyFile.OccupancyDeltaEntry delta : occupancyFile3.getDeltas()) {
            System.out.println("  Chunk: " + delta.getChunkId() + ", Delta: " + delta.getDeltaSize() + " bytes" + 
                (delta.getDeltaSize() < 0 ? " (DECOMMISSION)" : ""));
            if (delta.getDeltaSize() < 0) {
                hasNegativeDelta = true;
            }
        }

        System.out.println("\n=== Summary ===");
        System.out.println("Has decommissions: " + hasNegativeDelta);
        
        if (hasNegativeDelta) {
            System.out.println("✓ Decommissions detected - pages were replaced or removed");
        } else {
            System.out.println("✗ No decommissions - all operations were appends");
        }
    }
}
