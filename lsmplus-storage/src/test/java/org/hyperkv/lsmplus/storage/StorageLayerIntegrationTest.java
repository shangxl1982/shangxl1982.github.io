package org.hyperkv.lsmplus.storage;

import org.hyperkv.lsmplus.proto.Common.ChunkType;
import org.hyperkv.lsmplus.storage.io.VirtualDataPath;
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

class StorageLayerIntegrationTest {

    @TempDir
    Path tempDir;

    private StorageLayout layout;
    private ChunkManager manager;
    private UUID ownerId;
    private UUID namespaceId;

    @BeforeEach
    void setUp() throws IOException {
        ownerId = UUID.randomUUID();
        namespaceId = UUID.randomUUID();
        layout = new StorageLayout(tempDir.toFile());
        layout.initialize();
        manager = new ChunkManager(layout.getBaseDir().getAbsolutePath(), ownerId, namespaceId);
    }

    @AfterEach
    void tearDown() throws IOException {
        if (manager != null) {
            manager.close();
        }
    }

    @Test
    void testCompleteWriteReadCycle() throws Exception {
        byte[] leafData = "leaf page content".getBytes();
        byte[] indexData = "index page content".getBytes();
        byte[] journalData = "journal entry content".getBytes();

        var f1 = manager.writeLeafPageAsync(leafData);
        var f2 = manager.writeIndexPageAsync(indexData);
        var f3 = manager.writeJournalAsync(journalData);
        manager.flushAsyncWrites();
        
        SegmentLocation leafLoc = f1.get();
        SegmentLocation indexLoc = f2.get();
        SegmentLocation journalLoc = f3.get();

        Chunk leafChunk = manager.getChunk(leafLoc.getChunkId());
        Chunk indexChunk = manager.getChunk(indexLoc.getChunkId());
        Chunk journalChunk = manager.getChunk(journalLoc.getChunkId());

        assertArrayEquals(leafData, leafChunk.read(leafLoc));
        assertArrayEquals(indexData, indexChunk.read(indexLoc));
        assertArrayEquals(journalData, journalChunk.read(journalLoc));
    }

    @Test
    void testMultipleChunks() throws Exception {
        byte[] data1 = "data for chunk 1".getBytes();
        byte[] data2 = "data for chunk 2".getBytes();

        var f1 = manager.writeLeafPageAsync(data1);
        var f2 = manager.writeLeafPageAsync(data2);
        manager.flushAsyncWrites();
        
        SegmentLocation loc1 = f1.get();
        SegmentLocation loc2 = f2.get();

        assertEquals(loc1.getChunkId(), loc2.getChunkId());

        List<ChunkInfo> leafChunks = manager.listChunkInfos(ChunkType.CHUNK_LEAF);
        assertEquals(1, leafChunks.size());
    }

    @Test
    void testChunkRecovery() throws Exception {
        byte[] data = "persistent data".getBytes();
        var future = manager.writeLeafPageAsync(data);
        manager.flushAsyncWrites();
        SegmentLocation loc = future.get();
        File chunkFile = manager.getChunk(loc.getChunkId()).getFile();
        manager.close();

        Chunk recovered = Chunk.openExisting(VirtualDataPath.file(chunkFile.getAbsolutePath()));
        byte[] readData = recovered.read(loc);
        assertArrayEquals(data, readData);
        recovered.close();
    }

    @Test
    void testDirectoryStructureIntegration() {
        assertTrue(layout.getDataDir().exists());
        assertTrue(layout.getJournalDir().exists());
        assertTrue(layout.getOccupancyDir().exists());
    }

    @Test
    void testWriteItemIntegrityInChunk() throws Exception {
        byte[] data = new byte[100];
        for (int i = 0; i < data.length; i++) {
            data[i] = (byte) (i & 0xFF);
        }

        var future = manager.writeIndexPageAsync(data);
        manager.flushAsyncWrites();
        SegmentLocation loc = future.get();
        Chunk chunk = manager.getChunk(loc.getChunkId());

        byte[] readData = chunk.read(loc);
        assertArrayEquals(data, readData);
    }

    @Test
    void testChunkHeaderMatchesChunkManager() throws Exception {
        var future = manager.writeLeafPageAsync("test".getBytes());
        manager.flushAsyncWrites();
        SegmentLocation loc = future.get();
        Chunk chunk = manager.getChunk(loc.getChunkId());

        assertEquals(ChunkType.CHUNK_LEAF, chunk.getChunkType());
        assertEquals(ownerId, chunk.getHeader().getOwnerId());
        assertEquals(namespaceId, chunk.getHeader().getNamespaceId());
    }

    @Test
    void testMixedChunkTypes() throws Exception {
        for (int i = 0; i < 5; i++) {
            manager.writeLeafPageAsync(("leaf-" + i).getBytes());
            manager.writeIndexPageAsync(("index-" + i).getBytes());
            manager.writeJournalAsync(("journal-" + i).getBytes());
        }
        manager.flushAsyncWrites();

        List<ChunkInfo> leafChunks = manager.listChunkInfos(ChunkType.CHUNK_LEAF);
        List<ChunkInfo> indexChunks = manager.listChunkInfos(ChunkType.CHUNK_INDEX);
        List<ChunkInfo> journalChunks = manager.listChunkInfos(ChunkType.CHUNK_JOURNAL);

        assertTrue(leafChunks.size() >= 1);
        assertTrue(indexChunks.size() >= 1);
        assertTrue(journalChunks.size() >= 1);
    }

    @Test
    void testStorageLayoutFilePaths() throws IOException {
        assertNotNull(layout.getTreeMetadataFile());
        assertNotNull(layout.getJournalRegionIndexFile());
        assertNotNull(layout.getChunkMetadataFile());

        assertTrue(layout.getTreeMetadataFile().getName().equals("tree-metadata.pb"));
        assertTrue(layout.getJournalRegionIndexFile().getName().equals("journal-region.pb"));
        assertTrue(layout.getChunkMetadataFile().getName().equals("chunk-metadata.pb"));
    }
}
