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

class MNSDebugTest {

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
    void testMNSDuringTreeDump() throws Exception {
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

        System.out.println("=== Initial State ===");
        System.out.println("ChunkManager MNS: " + chunkManager.getMNS());
        System.out.println("MNSTracker MNS: " + mnsTracker.getCurrentMNS());

        System.out.println("\n=== Version 1: Initial inserts ===");
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

        System.out.println("\nAfter Version 1 dump:");
        System.out.println("ChunkManager MNS: " + chunkManager.getMNS());
        System.out.println("MNSTracker MNS: " + mnsTracker.getCurrentMNS());
        
        OccupancyFile occupancyFile1 = occupancyManager.loadOccupancyRecord(1);
        if (occupancyFile1 != null) {
            System.out.println("Occupancy file MNS: " + occupancyFile1.getMns());
        }
        
        System.out.println("\nChunk info:");
        chunkManager.getOpenChunkInfos().forEach(info -> {
            System.out.println("  Chunk: " + info.getChunkId().toString().substring(0, 8) + 
                             ", number=" + info.getChunkNumber() + 
                             ", status=" + info.getStatus() + 
                             ", type=" + info.getChunkType());
        });

        System.out.println("\n=== Version 2: More inserts ===");
        MemoryTable table2 = memTableManager.getActiveTable();
        for (int i = 100; i < 200; i++) {
            table2.put(IndexKey.orderedBytes(("key" + String.format("%03d", i)).getBytes()),
                      IndexValue.normal(("value" + i).getBytes()), null);
        }
        memTableManager.sealActiveTable();

        List<Map.Entry<IndexKey, IndexValue>> merged2 = mergeTables(memTableManager.getSealedTables());
        treeDumper.dump(merged2, null);
        treeDumper.promoteRoot();
        memTableManager.clearSealedTables();

        System.out.println("\nAfter Version 2 dump:");
        System.out.println("ChunkManager MNS: " + chunkManager.getMNS());
        System.out.println("MNSTracker MNS: " + mnsTracker.getCurrentMNS());
        
        OccupancyFile occupancyFile2 = occupancyManager.loadOccupancyRecord(2);
        if (occupancyFile2 != null) {
            System.out.println("Occupancy file MNS: " + occupancyFile2.getMns());
        }
        
        System.out.println("\nChunk info:");
        chunkManager.getOpenChunkInfos().forEach(info -> {
            System.out.println("  Chunk: " + info.getChunkId().toString().substring(0, 8) + 
                             ", number=" + info.getChunkNumber() + 
                             ", status=" + info.getStatus() + 
                             ", type=" + info.getChunkType());
        });
        
        assertTrue(chunkManager.getMNS() > 0, "MNS should be > 0 when there are open chunks");
    }
}
