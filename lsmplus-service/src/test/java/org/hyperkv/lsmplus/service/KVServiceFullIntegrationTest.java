package org.hyperkv.lsmplus.service;

import org.hyperkv.lsmplus.api.model.IndexKey;
import org.hyperkv.lsmplus.api.model.IndexValue;
import org.hyperkv.lsmplus.bplustree.BPlusTree;
import org.hyperkv.lsmplus.bplustree.PageCapacityConfig;
import org.hyperkv.lsmplus.core.KVStore;
import org.hyperkv.lsmplus.proto.Common.ChunkType;
import org.hyperkv.lsmplus.storage.Chunk;
import org.hyperkv.lsmplus.storage.ChunkInfo;
import org.hyperkv.lsmplus.storage.ChunkManager;
import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.io.TempDir;

import java.io.File;
import java.io.IOException;
import java.nio.file.Files;
import java.nio.file.Path;
import java.util.ArrayList;
import java.util.List;
import java.util.Map;
import java.util.TreeMap;
import java.util.UUID;
import java.util.stream.Collectors;
import java.util.stream.Stream;

import static org.junit.jupiter.api.Assertions.*;

class KVServiceFullIntegrationTest {

    @TempDir
    File tempDir;

    private File dataDir;
    private final int leafMaxEntries = 10;
    private final int indexMaxEntries = 20;
    private final int numKeys = 1000;

    private PageCapacityConfig pageCapacityConfig;

    @BeforeEach
    void setUp() {
        dataDir = new File(tempDir, "kvstore-data");
        dataDir.mkdirs();
        pageCapacityConfig = PageCapacityConfig.entryCountBased(leafMaxEntries, indexMaxEntries);
    }

    @Test
    void testFullLifecycleWithPersistence() throws Exception {
        StringBuilder report = new StringBuilder();
        report.append("\n========== KVStore Full Integration Test Report ==========\n\n");

        long testStartTime = System.currentTimeMillis();

        TreeMap<String, String> expectedData = new TreeMap<>();
        List<String> keysToDelete = new ArrayList<>();

        report.append("Configuration:\n");
        report.append("  - Data directory: ").append(dataDir.getAbsolutePath()).append("\n");
        report.append("  - Leaf max entries: ").append(leafMaxEntries).append("\n");
        report.append("  - Index max entries: ").append(indexMaxEntries).append("\n");
        report.append("  - Number of keys per phase: ").append(numKeys).append("\n\n");

        report.append("========== Phase 1: Create KVStore and Insert 1000 K-V ==========\n");
        long phase1Start = System.currentTimeMillis();

        KVStore store1 = new KVStore(dataDir, pageCapacityConfig);
        KVService service1 = new KVService(store1);
        service1.start();

        for (int i = 0; i < numKeys; i++) {
            String key = String.format("phase1-key-%05d", i);
            String value = "phase1-value-" + i + "-" + UUID.randomUUID().toString().substring(0, 8);
            expectedData.put(key, value);
            service1.handle(KVRequest.put(key, value.getBytes()));
        }

        service1.stop();

        long phase1Duration = System.currentTimeMillis() - phase1Start;
        report.append("  - Inserted ").append(numKeys).append(" key-values\n");
        report.append("  - Duration: ").append(phase1Duration).append(" ms\n");
        report.append("  - KVStore closed\n\n");

        report.append("========== Phase 2: Reopen KVStore, Insert 1000 K-V with Deletions ==========\n");
        long phase2Start = System.currentTimeMillis();

        KVStore store2 = new KVStore(dataDir, pageCapacityConfig);
        KVService service2 = new KVService(store2);
        service2.start();

        for (int i = 0; i < numKeys; i++) {
            String key = String.format("phase2-key-%05d", i);
            String value = "phase2-value-" + i + "-" + UUID.randomUUID().toString().substring(0, 8);
            expectedData.put(key, value);
            service2.handle(KVRequest.put(key, value.getBytes()));
        }

        int deleteCount = 0;
        for (int i = 0; i < numKeys; i += 10) {
            String keyToDelete = String.format("phase1-key-%05d", i);
            if (expectedData.containsKey(keyToDelete)) {
                expectedData.remove(keyToDelete);
                keysToDelete.add(keyToDelete);
                service2.handle(KVRequest.delete(keyToDelete));
                deleteCount++;
            }
        }

        store2.sealActiveTable();
        store2.dump();

        service2.stop();

        long phase2Duration = System.currentTimeMillis() - phase2Start;
        report.append("  - Inserted ").append(numKeys).append(" new key-values\n");
        report.append("  - Deleted ").append(deleteCount).append(" key-values\n");
        report.append("  - Duration: ").append(phase2Duration).append(" ms\n");
        report.append("  - KVStore closed\n\n");

        report.append("========== Phase 3: Reopen and Verify All Data ==========\n");
        long phase3Start = System.currentTimeMillis();

        KVStore store3 = new KVStore(dataDir, pageCapacityConfig);
        KVService service3 = new KVService(store3);
        service3.start();

        int verifiedCount = 0;
        int notFoundCount = 0;
        int mismatchCount = 0;
        int deletedButFoundCount = 0;

        for (Map.Entry<String, String> entry : expectedData.entrySet()) {
            KVResponse response = service3.handle(KVRequest.get(entry.getKey()));
            if (!response.isSuccess()) {
                notFoundCount++;
                report.append("  - ERROR: Key not found: ").append(entry.getKey()).append("\n");
            } else {
                String retrievedValue = new String(response.getValue());
                if (!retrievedValue.equals(entry.getValue())) {
                    mismatchCount++;
                    report.append("  - ERROR: Value mismatch for key: ").append(entry.getKey()).append("\n");
                } else {
                    verifiedCount++;
                }
            }
        }

        for (String deletedKey : keysToDelete) {
            KVResponse response = service3.handle(KVRequest.get(deletedKey));
            if (response.isSuccess()) {
                deletedButFoundCount++;
                report.append("  - ERROR: Deleted key still found: ").append(deletedKey).append("\n");
            }
        }

        BPlusTree tree = store3.getBPlusTree();
        ChunkManager chunkManager = store3.getChunkManager();

        long treeVersion = tree.getVersion();
        int treeHeight = tree.getHeight();
        int totalEntries = tree.getTotalEntryCount();

        List<ChunkInfo> leafChunks = chunkManager.listChunkInfos(ChunkType.CHUNK_LEAF);
        List<ChunkInfo> indexChunks = chunkManager.listChunkInfos(ChunkType.CHUNK_INDEX);
        List<ChunkInfo> journalChunks = chunkManager.listChunkInfos(ChunkType.CHUNK_JOURNAL);

        long totalLeafSize = leafChunks.stream().mapToLong(ChunkInfo::getUsedSize).sum();
        long totalIndexSize = indexChunks.stream().mapToLong(ChunkInfo::getUsedSize).sum();
        long totalJournalSize = journalChunks.stream().mapToLong(ChunkInfo::getUsedSize).sum();

        service3.stop();

        long phase3Duration = System.currentTimeMillis() - phase3Start;

        report.append("  - Verified ").append(verifiedCount).append(" key-values successfully\n");
        report.append("  - Not found errors: ").append(notFoundCount).append("\n");
        report.append("  - Value mismatch errors: ").append(mismatchCount).append("\n");
        report.append("  - Deleted but found errors: ").append(deletedButFoundCount).append("\n");
        report.append("  - Duration: ").append(phase3Duration).append(" ms\n\n");

        report.append("========== Storage Statistics ==========\n");
        report.append("  - Tree version: ").append(treeVersion).append("\n");
        report.append("  - Tree height: ").append(treeHeight).append("\n");
        report.append("  - Total entries in tree: ").append(totalEntries).append("\n");
        report.append("  - Leaf chunks: ").append(leafChunks.size()).append(" (total ").append(totalLeafSize).append(" bytes)\n");
        report.append("  - Index chunks: ").append(indexChunks.size()).append(" (total ").append(totalIndexSize).append(" bytes)\n");
        report.append("  - Journal chunks: ").append(journalChunks.size()).append(" (total ").append(totalJournalSize).append(" bytes)\n\n");

        report.append("========== File System Verification ==========\n");
        int chunkFileCount = 0;
        int metadataFileCount = 0;
        try (Stream<Path> paths = Files.walk(dataDir.toPath())) {
            for (Path path : paths.collect(Collectors.toList())) {
                if (Files.isRegularFile(path)) {
                    String fileName = path.getFileName().toString();
                    if (fileName.endsWith(".dat")) {
                        chunkFileCount++;
                    } else if (fileName.endsWith(".pb") || fileName.endsWith(".json")) {
                        metadataFileCount++;
                    }
                }
            }
        }
        report.append("  - Chunk files (.dat): ").append(chunkFileCount).append("\n");
        report.append("  - Metadata files (.pb/.json): ").append(metadataFileCount).append("\n\n");

        report.append("========== Test Assertions ==========\n");

        assertEquals(0, notFoundCount, "All expected keys should be found");
        report.append("  - PASS: All expected keys found\n");

        assertEquals(0, mismatchCount, "All values should match");
        report.append("  - PASS: All values match\n");

        assertEquals(0, deletedButFoundCount, "Deleted keys should not be found");
        report.append("  - PASS: Deleted keys not found\n");

        assertTrue(leafChunks.size() > 0, "Should have at least one leaf chunk");
        report.append("  - PASS: Leaf chunks exist (").append(leafChunks.size()).append(")\n");

        assertTrue(treeVersion >= 0, "Tree version should be non-negative");
        report.append("  - PASS: Tree version is valid (actual: ").append(treeVersion).append(")\n");

        assertTrue(chunkFileCount > 0, "Should have chunk files created");
        report.append("  - PASS: Chunk files created (").append(chunkFileCount).append(")\n");

        report.append("\n========== Summary ==========\n");
        long totalDuration = System.currentTimeMillis() - testStartTime;
        report.append("  - Total test duration: ").append(totalDuration).append(" ms\n");
        report.append("  - Total key-values in final state: ").append(expectedData.size()).append("\n");
        report.append("  - Keys deleted: ").append(keysToDelete.size()).append("\n");
        report.append("  - All tests PASSED\n");

        report.append("\n========== End of Report ==========\n");

        System.out.println(report);
    }

    @Test
    void testMultipleDumpCyclesWithVerification() throws Exception {
        StringBuilder report = new StringBuilder();
        report.append("\n========== Multiple Dump Cycles Test Report ==========\n\n");

        TreeMap<String, String> expectedData = new TreeMap<>();
        int numCycles = 5;
        int keysPerCycle = 200;

        KVStore store = new KVStore(dataDir, pageCapacityConfig);
        KVService service = new KVService(store);
        service.start();

        report.append("Configuration:\n");
        report.append("  - Cycles: ").append(numCycles).append("\n");
        report.append("  - Keys per cycle: ").append(keysPerCycle).append("\n\n");

        for (int cycle = 0; cycle < numCycles; cycle++) {
            report.append("Cycle ").append(cycle + 1).append(":\n");

            for (int i = 0; i < keysPerCycle; i++) {
                String key = String.format("cycle%d-key-%05d", cycle, i);
                String value = "cycle" + cycle + "-value-" + i + "-" + UUID.randomUUID().toString().substring(0, 8);
                expectedData.put(key, value);
                service.handle(KVRequest.put(key, value.getBytes()));
            }

            int deleteCount = 0;
            if (cycle > 0) {
                for (int i = 0; i < keysPerCycle; i += 5) {
                    String keyToDelete = String.format("cycle%d-key-%05d", cycle - 1, i);
                    if (expectedData.containsKey(keyToDelete)) {
                        expectedData.remove(keyToDelete);
                        service.handle(KVRequest.delete(keyToDelete));
                        deleteCount++;
                    }
                }
            }

            report.append("  - Inserted: ").append(keysPerCycle).append(", Deleted: ").append(deleteCount).append("\n");

            store.sealActiveTable();
            store.dump();
        }

        report.append("\nVerifying all data...\n");
        int verifiedCount = 0;
        int errorCount = 0;

        for (Map.Entry<String, String> entry : expectedData.entrySet()) {
            KVResponse response = service.handle(KVRequest.get(entry.getKey()));
            if (response.isSuccess() && new String(response.getValue()).equals(entry.getValue())) {
                verifiedCount++;
            } else {
                errorCount++;
                if (errorCount <= 5) {
                    report.append("  - ERROR: Key ").append(entry.getKey()).append(" verification failed\n");
                }
            }
        }

        BPlusTree tree = store.getBPlusTree();
        report.append("\nTree Statistics:\n");
        report.append("  - Version: ").append(tree.getVersion()).append("\n");
        report.append("  - Height: ").append(tree.getHeight()).append("\n");
        report.append("  - Total entries: ").append(tree.getTotalEntryCount()).append("\n");

        service.stop();

        report.append("\nResults:\n");
        report.append("  - Verified: ").append(verifiedCount).append("/").append(expectedData.size()).append("\n");
        report.append("  - Errors: ").append(errorCount).append("\n");

        assertEquals(expectedData.size(), verifiedCount, "All keys should be verified");
        report.append("  - PASS: All keys verified successfully\n");

        System.out.println(report);
    }
}
