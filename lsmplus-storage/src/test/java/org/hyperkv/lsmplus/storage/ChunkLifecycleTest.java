package org.hyperkv.lsmplus.storage;

import org.hyperkv.lsmplus.proto.Common.ChunkStatus;
import org.hyperkv.lsmplus.proto.Common.ChunkType;
import org.hyperkv.lsmplus.storage.io.VirtualDataPath;
import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.io.TempDir;

import java.io.File;
import java.io.IOException;
import java.nio.file.Path;
import java.util.UUID;

import static org.junit.jupiter.api.Assertions.*;

class ChunkLifecycleTest {

    @TempDir
    Path tempDir;

    private Chunk chunk;

    @BeforeEach
    void setUp() throws IOException {
        UUID chunkId = UUID.randomUUID();
        File file = tempDir.resolve("chunk_" + chunkId + ".dat").toFile();
        chunk = new Chunk(VirtualDataPath.file(file.getAbsolutePath()), ChunkType.CHUNK_LEAF, UUID.randomUUID(), UUID.randomUUID());
    }

    @AfterEach
    void tearDown() throws IOException {
        if (chunk != null && chunk.getStatus() != ChunkStatus.DELETED) {
            chunk.close();
        }
    }

    @Test
    void testInitialStatusIsOpen() {
        assertEquals(ChunkStatus.OPEN, chunk.getStatus());
    }

    @Test
    void testOpenToSealed() throws IOException {
        chunk.seal();
        assertEquals(ChunkStatus.SEALED, chunk.getStatus());
    }

    @Test
    void testSealedToDeleting() throws IOException {
        chunk.seal();
        chunk.delete();
        assertEquals(ChunkStatus.DELETING, chunk.getStatus());
    }

    @Test
    void testDeletingToDeleted() throws IOException {
        chunk.seal();
        chunk.delete();
        chunk.cleanup();
        assertEquals(ChunkStatus.DELETED, chunk.getStatus());
        assertFalse(chunk.getFile().exists());
    }

    @Test
    void testFullLifecycle() throws IOException {
        assertEquals(ChunkStatus.OPEN, chunk.getStatus());

        byte[] data = "lifecycle data".getBytes();
        SegmentLocation loc = chunk.write(data, WriteItem.TYPE_PAGE_DATA);
        assertArrayEquals(data, chunk.read(loc));

        chunk.seal();
        assertEquals(ChunkStatus.SEALED, chunk.getStatus());
        assertArrayEquals(data, chunk.read(loc));

        chunk.delete();
        assertEquals(ChunkStatus.DELETING, chunk.getStatus());

        chunk.cleanup();
        assertEquals(ChunkStatus.DELETED, chunk.getStatus());
        assertFalse(chunk.getFile().exists());
    }

    @Test
    void testCannotSealTwice() throws IOException {
        chunk.seal();
        assertThrows(IllegalStateException.class, () -> chunk.seal());
    }

    @Test
    void testCannotSealFromDeleting() throws IOException {
        chunk.seal();
        chunk.delete();
        assertThrows(IllegalStateException.class, () -> chunk.seal());
    }

    @Test
    void testCannotDeleteFromOpen() {
        assertThrows(IllegalStateException.class, () -> chunk.delete());
    }

    @Test
    void testCannotDeleteFromDeleting() throws IOException {
        chunk.seal();
        chunk.delete();
        assertThrows(IllegalStateException.class, () -> chunk.delete());
    }

    @Test
    void testCannotCleanupFromOpen() throws IOException {
        assertThrows(IllegalStateException.class, () -> chunk.cleanup());
    }

    @Test
    void testCannotCleanupFromSealed() throws IOException {
        chunk.seal();
        assertThrows(IllegalStateException.class, () -> chunk.cleanup());
    }

    @Test
    void testCannotWriteToSealedChunk() throws IOException {
        chunk.seal();
        assertThrows(IllegalStateException.class,
                () -> chunk.write("data".getBytes(), WriteItem.TYPE_PAGE_DATA));
    }

    @Test
    void testCannotWriteToDeletingChunk() throws IOException {
        chunk.seal();
        chunk.delete();
        assertThrows(IllegalStateException.class,
                () -> chunk.write("data".getBytes(), WriteItem.TYPE_PAGE_DATA));
    }

    @Test
    void testCanReadFromSealedChunk() throws IOException {
        byte[] data = "sealed read test".getBytes();
        SegmentLocation loc = chunk.write(data, WriteItem.TYPE_PAGE_DATA);
        chunk.seal();
        assertArrayEquals(data, chunk.read(loc));
    }
}