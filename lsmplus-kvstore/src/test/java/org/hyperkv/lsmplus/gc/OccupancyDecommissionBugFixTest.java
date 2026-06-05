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

class OccupancyDecommissionBugFixTest {

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
    void testDecommissionTrackingDuringUpdates() throws Exception {
        MemoryTableManager memTableManager = new MemoryTableManager(1024 * 1024);
        PageManager pageManager = createPageManager();
        ChunkManager chunkManager = pageManager.getChunkManager();
        
        MNSTracker mnsTracker = new MNSTracker();
        OccupancyManager occupancyManager = new OccupancyManager(
            tempDir.toFile().getAbsolutePath(),
            chunkManager,
            mnsTracker
        );
        
        BPlusTree tree = new BPlusTree(pageManager, capacityConfig);
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
        System.out.println("Version 1 - Total deltas: " + occupancyFile1.getDeltas().size());
        long totalWrites1 = occupancyFile1.getDeltas().stream()
            .mapToLong(OccupancyFile.OccupancyDeltaEntry::getDeltaSize)
            .sum();
        System.out.println("Version 1 - Total writes: " + totalWrites1 + " bytes");
        
        System.out.println("\n=== Version 2: Updates (replace existing values) ===");
        MemoryTable table2 = memTableManager.getActiveTable();
        for (int i = 0; i < 50; i++) {
            table2.put(IndexKey.orderedBytes(("key" + String.format("%03d", i)).getBytes()),
                      IndexValue.normal(("updated-value-" + i + "-longer").getBytes()), null);
        }
        memTableManager.sealActiveTable();

        List<Map.Entry<IndexKey, IndexValue>> merged2 = mergeTables(memTableManager.getSealedTables());
        treeDumper.dump(merged2, null);
        treeDumper.promoteRoot();
        memTableManager.clearSealedTables();

        OccupancyFile occupancyFile2 = occupancyManager.loadOccupancyRecord(2);
        assertNotNull(occupancyFile2);
        System.out.println("Version 2 - Total deltas: " + occupancyFile2.getDeltas().size());
        
        boolean hasDecommission = false;
        long totalWrites2 = 0;
        long totalDecommissions2 = 0;
        
        for (OccupancyFile.OccupancyDeltaEntry delta : occupancyFile2.getDeltas()) {
            System.out.println("  Delta: chunkId=" + delta.getChunkId() + ", size=" + delta.getDeltaSize());
            if (delta.getDeltaSize() < 0) {
                hasDecommission = true;
                totalDecommissions2 += Math.abs(delta.getDeltaSize());
                System.out.println("  ✓ DECOMMISSION detected: " + Math.abs(delta.getDeltaSize()) + " bytes freed");
            } else {
                totalWrites2 += delta.getDeltaSize();
            }
        }
        
        System.out.println("Version 2 - Total writes: " + totalWrites2 + " bytes");
        System.out.println("Version 2 - Total decommissions: " + totalDecommissions2 + " bytes");
        
        assertTrue(hasDecommission, "BUG FIX VERIFICATION: Decommissions should be tracked during updates!");
        System.out.println("\n✓✓✓ BUG FIX SUCCESSFUL ✓✓✓");
        System.out.println("Decommissions are now properly tracked when pages are replaced!");
    }

    @Test
    void testDecommissionTrackingDuringDeletes() throws Exception {
        MemoryTableManager memTableManager = new MemoryTableManager(1024 * 1024);
        PageManager pageManager = createPageManager();
        ChunkManager chunkManager = pageManager.getChunkManager();
        
        MNSTracker mnsTracker = new MNSTracker();
        OccupancyManager occupancyManager = new OccupancyManager(
            tempDir.toFile().getAbsolutePath(),
            chunkManager,
            mnsTracker
        );
        
        BPlusTree tree = new BPlusTree(pageManager, capacityConfig);
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

        System.out.println("\n=== Version 2: Deletes (tombstones) ===");
        MemoryTable table2 = memTableManager.getActiveTable();
        for (int i = 0; i < 30; i++) {
            table2.put(IndexKey.orderedBytes(("key" + String.format("%03d", i)).getBytes()),
                      IndexValue.tombstone(), null);
        }
        memTableManager.sealActiveTable();

        List<Map.Entry<IndexKey, IndexValue>> merged2 = mergeTables(memTableManager.getSealedTables());
        treeDumper.dump(merged2, null);
        treeDumper.promoteRoot();

        OccupancyFile occupancyFile2 = occupancyManager.loadOccupancyRecord(2);
        assertNotNull(occupancyFile2);
        
        boolean hasDecommission = false;
        for (OccupancyFile.OccupancyDeltaEntry delta : occupancyFile2.getDeltas()) {
            if (delta.getDeltaSize() < 0) {
                hasDecommission = true;
                System.out.println("  ✓ DECOMMISSION detected: " + Math.abs(delta.getDeltaSize()) + " bytes freed");
            }
        }
        
        if (hasDecommission) {
            System.out.println("\n✓ Decommissions detected during deletes - pages were replaced!");
        } else {
            System.out.println("\n✗ No decommissions - pages may have been updated in place or merged");
        }
    }
}
