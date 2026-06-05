package org.hyperkv.lsmplus.storage;

import org.hyperkv.lsmplus.exception.KVStoreRuntimeException;
import org.hyperkv.lsmplus.proto.Common.ChunkType;
import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.io.TempDir;

import java.io.File;
import java.io.IOException;
import java.nio.file.Path;
import java.util.List;
import java.util.UUID;

import static org.junit.jupiter.api.Assertions.*;

class ChunkManagerTest {

    @TempDir
    Path tempDir;

    private ChunkManager manager;
    private UUID ownerId;
    private UUID namespaceId;

    @BeforeEach
    void setUp() throws IOException {
        ownerId = UUID.randomUUID();
        namespaceId = UUID.randomUUID();
        manager = new ChunkManager(tempDir.toFile().getAbsolutePath(), ownerId, namespaceId);
    }

    @AfterEach
    void tearDown() throws IOException {
        if (manager != null) {
            manager.close();
        }
    }

    @Test
    void testDirectoryStructureCreated() {
        assertTrue(new File(tempDir.toFile(), "data").exists());
        assertTrue(new File(tempDir.toFile(), "journal").exists());
    }

    @Test
    void testWriteLeafPageAsync() throws Exception {
        byte[] data = "leaf page data".getBytes();
        var future = manager.writeLeafPageAsync(data);
        assertNotNull(future);
        
        manager.flushAsyncWrites();
        
        SegmentLocation loc = future.get();
        assertNotNull(loc);
        assertNotNull(loc.getChunkId());
        assertTrue(loc.getOffset() >= ChunkHeader.HEADER_SIZE);
    }

    @Test
    void testWriteIndexPageAsync() throws Exception {
        byte[] data = "index page data".getBytes();
        var future = manager.writeIndexPageAsync(data);
        assertNotNull(future);
        
        manager.flushAsyncWrites();
        
        SegmentLocation loc = future.get();
        assertNotNull(loc);
        assertNotNull(loc.getChunkId());
    }

    @Test
    void testWriteJournalAsync() throws Exception {
        byte[] data = "journal entry data".getBytes();
        var future = manager.writeJournalAsync(data);
        assertNotNull(future);
        
        manager.flushAsyncWrites();
        
        SegmentLocation loc = future.get();
        assertNotNull(loc);
        assertNotNull(loc.getChunkId());
    }

    @Test
    void testWriteAndRead() throws Exception {
        byte[] data = "test read write".getBytes();
        var future = manager.writeLeafPageAsync(data);
        manager.flushAsyncWrites();
        SegmentLocation loc = future.get();

        Chunk chunk = manager.getChunk(loc.getChunkId());
        assertNotNull(chunk);

        byte[] readData = chunk.read(loc);
        assertArrayEquals(data, readData);
    }

    @Test
    void testListChunks() throws Exception {
        var f1 = manager.writeLeafPageAsync("leaf1".getBytes());
        var f2 = manager.writeIndexPageAsync("index1".getBytes());
        var f3 = manager.writeJournalAsync("journal1".getBytes());
        manager.flushAsyncWrites();
        f1.get(); f2.get(); f3.get();

        List<ChunkInfo> leafChunks = manager.listChunkInfos(ChunkType.CHUNK_LEAF);
        List<ChunkInfo> indexChunks = manager.listChunkInfos(ChunkType.CHUNK_INDEX);
        List<ChunkInfo> journalChunks = manager.listChunkInfos(ChunkType.CHUNK_JOURNAL);

        assertTrue(leafChunks.size() >= 1);
        assertTrue(indexChunks.size() >= 1);
        assertTrue(journalChunks.size() >= 1);
    }

    @Test
    void testDeleteChunk() throws Exception {
        byte[] data = "to be deleted".getBytes();
        var future = manager.writeLeafPageAsync(data);
        manager.flushAsyncWrites();
        SegmentLocation loc = future.get();
        UUID chunkId = loc.getChunkId();

        assertNotNull(manager.getChunk(chunkId));
        manager.deleteChunk(chunkId);
        assertNull(manager.getChunkInfo(chunkId));
    }

    @Test
    void testMultipleWritesSameChunk() throws Exception {
        byte[] data1 = "write one".getBytes();
        byte[] data2 = "write two".getBytes();
        byte[] data3 = "write three".getBytes();

        var f1 = manager.writeLeafPageAsync(data1);
        var f2 = manager.writeLeafPageAsync(data2);
        var f3 = manager.writeLeafPageAsync(data3);
        manager.flushAsyncWrites();
        
        SegmentLocation loc1 = f1.get();
        SegmentLocation loc2 = f2.get();
        SegmentLocation loc3 = f3.get();

        assertEquals(loc1.getChunkId(), loc2.getChunkId());
        assertEquals(loc2.getChunkId(), loc3.getChunkId());

        Chunk chunk = manager.getChunk(loc1.getChunkId());
        assertArrayEquals(data1, chunk.read(loc1));
        assertArrayEquals(data2, chunk.read(loc2));
        assertArrayEquals(data3, chunk.read(loc3));
    }

    @Test
    void testGetNonexistentChunk() throws IOException {
        assertNull(manager.getChunk(UUID.randomUUID()));
    }

    @Test
    void testNullParametersThrow() {
        assertThrows(KVStoreRuntimeException.class,
                () -> new ChunkManager((String) null, ownerId, namespaceId));
        assertThrows(KVStoreRuntimeException.class,
                () -> new ChunkManager(tempDir.toFile().getAbsolutePath(), null, namespaceId));
        assertThrows(KVStoreRuntimeException.class,
                () -> new ChunkManager(tempDir.toFile().getAbsolutePath(), ownerId, null));
    }

    @Test
    void testJournalChunksInJournalDir() throws Exception {
        var future = manager.writeJournalAsync("journal data".getBytes());
        manager.flushAsyncWrites();
        SegmentLocation loc = future.get();
        Chunk chunk = manager.getChunk(loc.getChunkId());

        File chunkFile = chunk.getFile();
        assertTrue(chunkFile.getParentFile().getName().equals("journal"));
    }

    @Test
    void testDataChunksInDataDir() throws Exception {
        var f1 = manager.writeLeafPageAsync("leaf".getBytes());
        var f2 = manager.writeIndexPageAsync("index".getBytes());
        manager.flushAsyncWrites();
        
        SegmentLocation leafLoc = f1.get();
        SegmentLocation indexLoc = f2.get();

        Chunk leafChunk = manager.getChunk(leafLoc.getChunkId());
        Chunk indexChunk = manager.getChunk(indexLoc.getChunkId());

        assertEquals("data", leafChunk.getFile().getParentFile().getName());
        assertEquals("data", indexChunk.getFile().getParentFile().getName());
    }

    @Test
    void testMultipleAsyncWrites() throws Exception {
        int numWrites = 10;
        List<java.util.concurrent.CompletableFuture<SegmentLocation>> futures = new java.util.ArrayList<>();
        
        for (int i = 0; i < numWrites; i++) {
            byte[] data = ("async data " + i).getBytes();
            futures.add(manager.writeLeafPageAsync(data));
        }
        
        manager.flushAsyncWrites();
        
        for (int i = 0; i < numWrites; i++) {
            SegmentLocation location = futures.get(i).get();
            assertNotNull(location);
            assertNotNull(location.getChunkId());
        }
    }

    @Test
    void testStopAsyncWriters() throws IOException {
        byte[] data = "test data".getBytes();
        
        manager.writeLeafPageAsync(data);
        manager.writeIndexPageAsync(data);
        
        manager.stopAsyncWriters();
        
        assertDoesNotThrow(() -> manager.stopAsyncWriters());
    }

    @Test
    void testWriteDataBatch() throws IOException {
        List<byte[]> dataList = new java.util.ArrayList<>();
        for (int i = 0; i < 5; i++) {
            dataList.add(("batch data " + i).getBytes());
        }
        
        List<SegmentLocation> locations = manager.writeDataBatch(
            dataList, ChunkType.CHUNK_LEAF, (short) 2);
        
        assertNotNull(locations);
        assertEquals(5, locations.size());
        
        for (int i = 0; i < 5; i++) {
            SegmentLocation loc = locations.get(i);
            assertNotNull(loc);
            assertNotNull(loc.getChunkId());
            assertTrue(loc.getOffset() >= 0);
            assertTrue(loc.getLength() > 0);
            
            Chunk chunk = manager.getChunk(loc.getChunkId());
            byte[] readData = chunk.read(loc);
            assertArrayEquals(dataList.get(i), readData);
        }
    }

    @Test
    void testWriteDataBatchSingleItem() throws IOException {
        List<byte[]> dataList = new java.util.ArrayList<>();
        dataList.add("single item".getBytes());
        
        List<SegmentLocation> locations = manager.writeDataBatch(
            dataList, ChunkType.CHUNK_INDEX, (short) 2);
        
        assertNotNull(locations);
        assertEquals(1, locations.size());
        
        SegmentLocation loc = locations.get(0);
        Chunk chunk = manager.getChunk(loc.getChunkId());
        assertArrayEquals("single item".getBytes(), chunk.read(loc));
    }

    @Test
    void testWriteDataBatchConsistency() throws Exception {
        List<byte[]> batchData = new java.util.ArrayList<>();
        for (int i = 0; i < 3; i++) {
            batchData.add(("batch " + i).getBytes());
        }
        
        List<byte[]> individualData = new java.util.ArrayList<>();
        for (int i = 0; i < 3; i++) {
            individualData.add(("individual " + i).getBytes());
        }
        
        List<SegmentLocation> batchLocations = manager.writeDataBatch(
            batchData, ChunkType.CHUNK_LEAF, (short) 2);
        
        List<java.util.concurrent.CompletableFuture<SegmentLocation>> individualFutures = new java.util.ArrayList<>();
        for (byte[] data : individualData) {
            individualFutures.add(manager.writeLeafPageAsync(data));
        }
        manager.flushAsyncWrites();
        
        List<SegmentLocation> individualLocations = new java.util.ArrayList<>();
        for (var future : individualFutures) {
            individualLocations.add(future.get());
        }
        
        assertEquals(3, batchLocations.size());
        assertEquals(3, individualLocations.size());
        
        for (int i = 0; i < 3; i++) {
            Chunk batchChunk = manager.getChunk(batchLocations.get(i).getChunkId());
            Chunk individualChunk = manager.getChunk(individualLocations.get(i).getChunkId());
            
            byte[] batchRead = batchChunk.read(batchLocations.get(i));
            byte[] individualRead = individualChunk.read(individualLocations.get(i));
            
            assertArrayEquals(batchData.get(i), batchRead);
            assertArrayEquals(individualData.get(i), individualRead);
        }
    }
}
