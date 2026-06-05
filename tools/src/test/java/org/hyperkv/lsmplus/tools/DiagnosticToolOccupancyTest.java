package org.hyperkv.lsmplus.tools;

import org.hyperkv.lsmplus.proto.Metadata;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.io.TempDir;

import java.io.File;
import java.io.FileOutputStream;
import java.io.IOException;
import java.nio.file.Files;
import java.nio.file.Path;
import java.util.UUID;

import static org.junit.jupiter.api.Assertions.*;

class DiagnosticToolOccupancyTest {

    @TempDir
    Path tempDir;

    @Test
    void testOccupancyCommandWithNoData() throws Exception {
        File dataDir = tempDir.toFile();
        
        String[] args = {"occupancy", dataDir.getAbsolutePath()};
        int exitCode = new picocli.CommandLine(new DiagnosticTool()).execute(args);
        
        assertEquals(1, exitCode, "Should return 1 when occupancy directory doesn't exist");
    }

    @Test
    void testOccupancyCommandWithEmptyDirectory() throws Exception {
        File dataDir = tempDir.toFile();
        File occupancyDir = new File(dataDir, "occupancy");
        occupancyDir.mkdirs();
        
        String[] args = {"occupancy", dataDir.getAbsolutePath()};
        int exitCode = new picocli.CommandLine(new DiagnosticTool()).execute(args);
        
        assertEquals(1, exitCode, "Should return 1 when no occupancy files found");
    }

    @Test
    void testOccupancyCommandWithData() throws Exception {
        File dataDir = tempDir.toFile();
        File occupancyDir = new File(dataDir, "occupancy");
        occupancyDir.mkdirs();
        
        createTestOccupancyFile(occupancyDir, 1);
        createTestOccupancyFile(occupancyDir, 2);
        
        String[] args = {"occupancy", dataDir.getAbsolutePath()};
        int exitCode = new picocli.CommandLine(new DiagnosticTool()).execute(args);
        
        assertEquals(0, exitCode, "Should return 0 when occupancy files are read successfully");
    }

    @Test
    void testOccupancyCommandWithSpecificVersion() throws Exception {
        File dataDir = tempDir.toFile();
        File occupancyDir = new File(dataDir, "occupancy");
        occupancyDir.mkdirs();
        
        createTestOccupancyFile(occupancyDir, 1);
        createTestOccupancyFile(occupancyDir, 2);
        createTestOccupancyFile(occupancyDir, 3);
        
        String[] args = {"occupancy", dataDir.getAbsolutePath(), "-v", "2"};
        int exitCode = new picocli.CommandLine(new DiagnosticTool()).execute(args);
        
        assertEquals(0, exitCode, "Should return 0 when specific version is found");
    }

    @Test
    void testOccupancyCommandWithJsonOutput() throws Exception {
        File dataDir = tempDir.toFile();
        File occupancyDir = new File(dataDir, "occupancy");
        occupancyDir.mkdirs();
        
        createTestOccupancyFile(occupancyDir, 1);
        
        String[] args = {"occupancy", dataDir.getAbsolutePath(), "-j"};
        int exitCode = new picocli.CommandLine(new DiagnosticTool()).execute(args);
        
        assertEquals(0, exitCode, "Should return 0 with JSON output");
    }

    private void createTestOccupancyFile(File occupancyDir, long version) throws IOException {
        UUID chunkId1 = UUID.randomUUID();
        UUID chunkId2 = UUID.randomUUID();
        
        Metadata.OccupancyRecord record = Metadata.OccupancyRecord.newBuilder()
                .setVersion(version)
                .setMns(1000L * version)
                .setTimestamp(System.currentTimeMillis())
                .addDeltas(Metadata.OccupancyDelta.newBuilder()
                        .setChunkIdMostSig(chunkId1.getMostSignificantBits())
                        .setChunkIdLeastSig(chunkId1.getLeastSignificantBits())
                        .setDeltaSize(4096))
                .addDeltas(Metadata.OccupancyDelta.newBuilder()
                        .setChunkIdMostSig(chunkId2.getMostSignificantBits())
                        .setChunkIdLeastSig(chunkId2.getLeastSignificantBits())
                        .setDeltaSize(-2048))
                .addDecommissionPages(Metadata.DecommissionPage.newBuilder()
                        .setChunkIdMostSig(chunkId1.getMostSignificantBits())
                        .setChunkIdLeastSig(chunkId1.getLeastSignificantBits())
                        .setOffset(100)
                        .setLength(512))
                .build();
        
        File outputFile = new File(occupancyDir, version + ".pb");
        try (FileOutputStream fos = new FileOutputStream(outputFile)) {
            record.writeTo(fos);
        }
    }
}
