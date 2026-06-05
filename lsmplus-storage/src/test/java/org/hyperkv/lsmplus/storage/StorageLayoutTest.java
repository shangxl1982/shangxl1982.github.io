package org.hyperkv.lsmplus.storage;

import org.hyperkv.lsmplus.proto.Common.ChunkType;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.io.TempDir;

import java.io.File;
import java.nio.file.Path;
import java.util.UUID;

import static org.junit.jupiter.api.Assertions.*;

class StorageLayoutTest {

    @TempDir
    Path tempDir;

    @Test
    void testInitializeCreatesDirectories() {
        File base = new File(tempDir.toFile(), "kvstore");
        StorageLayout layout = new StorageLayout(base);

        assertFalse(layout.exists());
        layout.initialize();
        assertTrue(layout.exists());
    }

    @Test
    void testAllDirectoriesCreated() {
        File base = new File(tempDir.toFile(), "kvstore");
        StorageLayout layout = new StorageLayout(base);
        layout.initialize();

        assertTrue(layout.getBaseDir().exists());
        assertTrue(layout.getDataDir().exists());
        assertTrue(layout.getJournalDir().exists());
        assertTrue(layout.getOccupancyDir().exists());
        assertTrue(layout.getMetadataDir().exists());
        assertTrue(layout.getBackupDir().exists());
    }

    @Test
    void testMetadataFilePaths() {
        File base = new File(tempDir.toFile(), "kvstore");
        StorageLayout layout = new StorageLayout(base);

        assertEquals(new File(base, "tree-metadata.pb"), layout.getTreeMetadataFile());
        assertEquals(new File(base, "journal-region.pb"), layout.getJournalRegionIndexFile());
        assertEquals(new File(base, "chunk-metadata.pb"), layout.getChunkMetadataFile());
    }

    @Test
    void testOccupancyFilePaths() {
        File base = new File(tempDir.toFile(), "kvstore");
        StorageLayout layout = new StorageLayout(base);

        assertEquals(new File(base, "occupancy/1.pb"), layout.getOccupancyFile(1));
        assertEquals(new File(base, "occupancy/42.pb"), layout.getOccupancyFile(42));
    }

    @Test
    void testChunkFileForIndexType() {
        File base = new File(tempDir.toFile(), "kvstore");
        StorageLayout layout = new StorageLayout(base);
        UUID chunkId = UUID.randomUUID();

        File chunkFile = layout.getChunkFile(chunkId, ChunkType.CHUNK_INDEX);
        assertEquals("data", chunkFile.getParentFile().getName());
        assertTrue(chunkFile.getName().startsWith("chunk_"));
        assertTrue(chunkFile.getName().endsWith(".dat"));
    }

    @Test
    void testChunkFileForLeafType() {
        File base = new File(tempDir.toFile(), "kvstore");
        StorageLayout layout = new StorageLayout(base);
        UUID chunkId = UUID.randomUUID();

        File chunkFile = layout.getChunkFile(chunkId, ChunkType.CHUNK_LEAF);
        assertEquals("data", chunkFile.getParentFile().getName());
    }

    @Test
    void testChunkFileForJournalType() {
        File base = new File(tempDir.toFile(), "kvstore");
        StorageLayout layout = new StorageLayout(base);
        UUID chunkId = UUID.randomUUID();

        File chunkFile = layout.getChunkFile(chunkId, ChunkType.CHUNK_JOURNAL);
        assertEquals("journal", chunkFile.getParentFile().getName());
    }

    @Test
    void testNullBaseDirThrows() {
        assertThrows(IllegalArgumentException.class, () -> new StorageLayout(null));
    }

    @Test
    void testDirectoryStructureMatchesDesign() {
        File base = new File(tempDir.toFile(), "kvstore");
        StorageLayout layout = new StorageLayout(base);
        layout.initialize();

        assertTrue(new File(base, "data").isDirectory());
        assertTrue(new File(base, "journal").isDirectory());
        assertTrue(new File(base, "occupancy").isDirectory());
        assertTrue(new File(base, "metadata").isDirectory());
        assertTrue(new File(base, "backup").isDirectory());
    }
}