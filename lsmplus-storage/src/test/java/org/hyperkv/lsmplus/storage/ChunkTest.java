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

class ChunkTest {

    @TempDir
    Path tempDir;

    private Chunk chunk;

    @BeforeEach
    void setUp() throws IOException {
        UUID chunkId = UUID.randomUUID();
        File file = tempDir.resolve("chunk_" + chunkId + ".dat").toFile();
        chunk = new Chunk(VirtualDataPath.file(file.getAbsolutePath()), ChunkType.CHUNK_INDEX, UUID.randomUUID(), UUID.randomUUID());
    }

    @AfterEach
    void tearDown() throws IOException {
        if (chunk != null) {
            chunk.close();
        }
    }

    @Test
    void testCreateChunk() {
        assertEquals(ChunkStatus.OPEN, chunk.getStatus());
        assertEquals(ChunkType.CHUNK_INDEX, chunk.getChunkType());
        assertNotNull(chunk.getChunkId());
    }

    @Test
    void testChunkFileExists() {
        assertTrue(chunk.getFile().exists());
        assertTrue(chunk.getFile().length() >= ChunkHeader.HEADER_SIZE);
    }

    @Test
    void testWriteAndRead() throws IOException {
        byte[] data = "hello chunk".getBytes();
        SegmentLocation location = chunk.write(data, WriteItem.TYPE_PAGE_DATA);

        assertNotNull(location);
        assertEquals(chunk.getChunkId(), location.getChunkId());
        assertTrue(location.getOffset() >= ChunkHeader.HEADER_SIZE);

        byte[] readData = chunk.read(location);
        assertArrayEquals(data, readData);
    }

    @Test
    void testMultipleWrites() throws IOException {
        byte[] data1 = "first write".getBytes();
        byte[] data2 = "second write".getBytes();
        byte[] data3 = "third write".getBytes();

        SegmentLocation loc1 = chunk.write(data1, WriteItem.TYPE_JOURNAL_ENTRY);
        SegmentLocation loc2 = chunk.write(data2, WriteItem.TYPE_PAGE_DATA);
        SegmentLocation loc3 = chunk.write(data3, WriteItem.TYPE_JOURNAL_ENTRY);

        assertArrayEquals(data1, chunk.read(loc1));
        assertArrayEquals(data2, chunk.read(loc2));
        assertArrayEquals(data3, chunk.read(loc3));
    }

    @Test
    void testSeal() throws IOException {
        chunk.seal();
        assertEquals(ChunkStatus.SEALED, chunk.getStatus());
    }

    @Test
    void testWriteAfterSealThrows() throws IOException {
        chunk.seal();
        assertThrows(IllegalStateException.class,
                () -> chunk.write("data".getBytes(), WriteItem.TYPE_PAGE_DATA));
    }

    @Test
    void testSealTwiceThrows() throws IOException {
        chunk.seal();
        assertThrows(IllegalStateException.class, () -> chunk.seal());
    }

    @Test
    void testReadAfterSeal() throws IOException {
        byte[] data = "sealed data".getBytes();
        SegmentLocation loc = chunk.write(data, WriteItem.TYPE_PAGE_DATA);

        chunk.seal();

        byte[] readData = chunk.read(loc);
        assertArrayEquals(data, readData);
    }

    @Test
    void testReadWrongChunkIdThrows() throws IOException {
        byte[] data = "test data".getBytes();
        SegmentLocation loc = chunk.write(data, WriteItem.TYPE_PAGE_DATA);

        SegmentLocation wrongLoc = new SegmentLocation(UUID.randomUUID(), loc.getOffset(), loc.getLength());
        assertThrows(IllegalArgumentException.class, () -> chunk.read(wrongLoc));
    }

    @Test
    void testWriteNullDataThrows() {
        assertThrows(IllegalArgumentException.class,
                () -> chunk.write(null, WriteItem.TYPE_PAGE_DATA));
    }

    @Test
    void testReadNullLocationThrows() {
        assertThrows(IllegalArgumentException.class, () -> chunk.read(null));
    }

    @Test
    void testGetValidDataSize() throws IOException {
        assertEquals(0, chunk.getValidDataSize());

        byte[] data = "some data".getBytes();
        chunk.write(data, WriteItem.TYPE_PAGE_DATA);
        
        assertEquals(0, chunk.getValidDataSize());
        
        chunk.seal();
        assertTrue(chunk.getValidDataSize() > 0);
    }

    @Test
    void testOpenExistingChunk() throws IOException {
        byte[] data = "persistent data".getBytes();
        SegmentLocation loc = chunk.write(data, WriteItem.TYPE_PAGE_DATA);
        File chunkFile = chunk.getFile();
        chunk.close();

        Chunk reopened = Chunk.openExisting(VirtualDataPath.file(chunkFile.getAbsolutePath()));
        assertEquals(ChunkType.CHUNK_INDEX, reopened.getChunkType());
        byte[] readData = reopened.read(loc);
        assertArrayEquals(data, readData);
        reopened.close();
    }

    @Test
    void testCreateChunkWithDifferentTypes() throws IOException {
        for (ChunkType type : new ChunkType[]{ChunkType.CHUNK_INDEX, ChunkType.CHUNK_LEAF, ChunkType.CHUNK_JOURNAL}) {
            File file = tempDir.resolve("chunk_" + UUID.randomUUID() + ".dat").toFile();
            Chunk c = new Chunk(VirtualDataPath.file(file.getAbsolutePath()), type, UUID.randomUUID(), UUID.randomUUID());
            assertEquals(type, c.getChunkType());
            c.close();
        }
    }
}