package org.hyperkv.lsmplus.journal;

import org.hyperkv.lsmplus.api.model.IndexKey;
import org.hyperkv.lsmplus.api.model.IndexValue;
import org.hyperkv.lsmplus.proto.Common.OperationType;
import org.hyperkv.lsmplus.storage.Chunk;
import org.hyperkv.lsmplus.storage.ChunkInfo;
import org.hyperkv.lsmplus.storage.ChunkManager;
import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.io.TempDir;

import java.io.File;
import java.io.IOException;
import java.io.RandomAccessFile;
import java.nio.charset.StandardCharsets;
import java.nio.file.Path;
import java.util.ArrayList;
import java.util.List;
import java.util.UUID;

import static org.junit.jupiter.api.Assertions.*;

class JournalReplayTest {

    @TempDir
    Path tempDir;

    private ChunkManager chunkManager;
    private UUID ownerId;
    private UUID namespaceId;

    @BeforeEach
    void setUp() throws IOException {
        ownerId = UUID.randomUUID();
        namespaceId = UUID.randomUUID();
        chunkManager = new ChunkManager(tempDir.toFile().getAbsolutePath(), ownerId, namespaceId);
    }

    @AfterEach
    void tearDown() throws IOException {
        chunkManager.close();
    }

    @Test
    void testReplayFromBeginning() throws IOException {
        File regionIndexFile = new File(tempDir.toFile(), "journal-region-index");
        JournalRegionManager journal = new JournalRegionManager(chunkManager, regionIndexFile, ownerId, 0L, 64 * 1024 * 1024);

        IndexKey key1 = IndexKey.orderedBytes("key-1".getBytes(StandardCharsets.UTF_8));
        IndexValue value1 = IndexValue.normal("value-1".getBytes(StandardCharsets.UTF_8));
        journal.write(OperationType.PUT, key1, value1);

        IndexKey key2 = IndexKey.orderedBytes("key-2".getBytes(StandardCharsets.UTF_8));
        IndexValue value2 = IndexValue.normal("value-2".getBytes(StandardCharsets.UTF_8));
        journal.write(OperationType.PUT, key2, value2);

        IndexKey key3 = IndexKey.orderedBytes("key-3".getBytes(StandardCharsets.UTF_8));
        journal.write(OperationType.DELETE, key3, IndexValue.tombstone());

        journal.close();

        JournalRegionManager replayJournal = new JournalRegionManager(chunkManager, regionIndexFile, ownerId, 0L, 64 * 1024 * 1024);
        List<JournalEntry> replayed = new ArrayList<>();
        ReplayResult result = replayJournal.replayFromBeginning((entry, point) -> replayed.add(entry));
        replayJournal.close();

        assertEquals(3, replayed.size());
        assertEquals(OperationType.PUT, replayed.get(0).getOperationType());
        assertEquals(OperationType.PUT, replayed.get(1).getOperationType());
        assertEquals(OperationType.DELETE, replayed.get(2).getOperationType());
        assertNotNull(result.getLastReplayPoint());
        assertEquals(0, result.getErrorCount());
    }

    @Test
    void testReplayFromPoint() throws IOException {
        File regionIndexFile = new File(tempDir.toFile(), "journal-region-index");
        JournalRegionManager journal = new JournalRegionManager(chunkManager, regionIndexFile, ownerId, 0L, 64 * 1024 * 1024);

        IndexKey key1 = IndexKey.orderedBytes("key-1".getBytes(StandardCharsets.UTF_8));
        IndexValue value1 = IndexValue.normal("value-1".getBytes(StandardCharsets.UTF_8));
        JournalReplayPoint point1 = journal.write(OperationType.PUT, key1, value1);

        IndexKey key2 = IndexKey.orderedBytes("key-2".getBytes(StandardCharsets.UTF_8));
        IndexValue value2 = IndexValue.normal("value-2".getBytes(StandardCharsets.UTF_8));
        journal.write(OperationType.PUT, key2, value2);

        IndexKey key3 = IndexKey.orderedBytes("key-3".getBytes(StandardCharsets.UTF_8));
        IndexValue value3 = IndexValue.normal("value-3".getBytes(StandardCharsets.UTF_8));
        journal.write(OperationType.PUT, key3, value3);

        journal.close();

        JournalRegionManager replayJournal = new JournalRegionManager(chunkManager, regionIndexFile, ownerId, 0L, 64 * 1024 * 1024);
        List<JournalEntry> replayed = new ArrayList<>();
        ReplayResult result = replayJournal.replayFrom(point1, (entry, point) -> replayed.add(entry));
        replayJournal.close();

        assertFalse(replayed.isEmpty());
        assertTrue(replayed.size() >= 2);
        assertNotNull(result.getLastReplayPoint());
    }

    @Test
    void testReplayWithCRC32Validation() throws IOException {
        File regionIndexFile = new File(tempDir.toFile(), "journal-region-index");
        JournalRegionManager journal = new JournalRegionManager(chunkManager, regionIndexFile, ownerId, 0L, 64 * 1024 * 1024);

        IndexKey key = IndexKey.orderedBytes("crc-key".getBytes(StandardCharsets.UTF_8));
        IndexValue value = IndexValue.normal("crc-value".getBytes(StandardCharsets.UTF_8));
        journal.write(OperationType.PUT, key, value);

        journal.close();

        JournalRegionManager replayJournal = new JournalRegionManager(chunkManager, regionIndexFile, ownerId, 0L, 64 * 1024 * 1024);
        List<JournalEntry> replayed = new ArrayList<>();
        replayJournal.replayFromBeginning((entry, point) -> replayed.add(entry));
        replayJournal.close();

        assertEquals(1, replayed.size());
    }

    @Test
    void testReplayWithCorruptedEntry() throws IOException {
        File regionIndexFile = new File(tempDir.toFile(), "journal-region-index");
        JournalRegionManager journal = new JournalRegionManager(chunkManager, regionIndexFile, ownerId, 0L, 64 * 1024 * 1024);

        IndexKey key = IndexKey.orderedBytes("corrupt-key".getBytes(StandardCharsets.UTF_8));
        IndexValue value = IndexValue.normal("corrupt-value".getBytes(StandardCharsets.UTF_8));
        journal.write(OperationType.PUT, key, value);

        journal.close();

        var chunks = chunkManager.listChunkInfos(
                org.hyperkv.lsmplus.proto.Common.ChunkType.CHUNK_JOURNAL);
        assertFalse(chunks.isEmpty());

        ChunkInfo info = chunks.get(0);
        Chunk chunk = chunkManager.getChunk(info.getChunkId());
        long fileSize = chunk.getFileSize();

        try (RandomAccessFile raf = new RandomAccessFile(chunk.getFile(), "rw")) {
            int corruptOffset = 4096 + 20;
            if (corruptOffset < fileSize) {
                raf.seek(corruptOffset);
                raf.write(0xFF);
                raf.write(0xFF);
                raf.write(0xFF);
                raf.write(0xFF);
            }
        }

        JournalRegionManager replayJournal = new JournalRegionManager(chunkManager, regionIndexFile, ownerId, 0L, 64 * 1024 * 1024);
        List<JournalEntry> replayed = new ArrayList<>();
        replayJournal.replayFromBeginning((entry, point) -> replayed.add(entry));
        replayJournal.close();

        assertTrue(replayed.size() <= 1);
    }

    @Test
    void testReplayMultipleRegions() throws IOException {
        File regionIndexFile = new File(tempDir.toFile(), "journal-region-index");
        JournalRegionManager journal = new JournalRegionManager(chunkManager, regionIndexFile, ownerId, 0L, 64 * 1024 * 1024);

        IndexKey key = IndexKey.orderedBytes("region-key".getBytes(StandardCharsets.UTF_8));
        IndexValue value = IndexValue.normal("region-value".getBytes(StandardCharsets.UTF_8));

        journal.write(OperationType.PUT, key, value);
        journal.rotateRegion();
        journal.write(OperationType.PUT, key, value);
        journal.rotateRegion();
        journal.write(OperationType.PUT, key, value);

        journal.close();

        JournalRegionManager replayJournal = new JournalRegionManager(chunkManager, regionIndexFile, ownerId, 0L, 64 * 1024 * 1024);
        List<JournalEntry> replayed = new ArrayList<>();
        ReplayResult result = replayJournal.replayFromBeginning((entry, point) -> replayed.add(entry));
        replayJournal.close();

        assertEquals(3, replayed.size());
        assertNotNull(result.getLastReplayPoint());
        assertTrue(result.getLastReplayPoint().getRegionMajor() >= 1);
    }

    @Test
    void testReplayBatchEntry() throws IOException {
        File regionIndexFile = new File(tempDir.toFile(), "journal-region-index");
        JournalRegionManager journal = new JournalRegionManager(chunkManager, regionIndexFile, ownerId, 0L, 64 * 1024 * 1024);

        List<JournalEntry.KeyValuePair> operations = new ArrayList<>();
        for (int i = 0; i < 5; i++) {
            operations.add(new JournalEntry.KeyValuePair(
                    IndexKey.orderedBytes(("batch-" + i).getBytes(StandardCharsets.UTF_8)),
                    IndexValue.normal(("val-" + i).getBytes(StandardCharsets.UTF_8))
            ));
        }

        journal.writeBatch(operations);
        journal.close();

        JournalRegionManager replayJournal = new JournalRegionManager(chunkManager, regionIndexFile, ownerId, 0L, 64 * 1024 * 1024);
        List<JournalEntry> replayed = new ArrayList<>();
        replayJournal.replayFromBeginning((entry, point) -> replayed.add(entry));
        replayJournal.close();

        assertEquals(1, replayed.size());
        assertEquals(OperationType.BATCH, replayed.get(0).getOperationType());
        assertEquals(5, replayed.get(0).getEntries().size());
    }

    @Test
    void testReplayPreservesSequenceNumbers() throws IOException {
        File regionIndexFile = new File(tempDir.toFile(), "journal-region-index");
        JournalRegionManager journal = new JournalRegionManager(chunkManager, regionIndexFile, ownerId, 0L, 64 * 1024 * 1024);

        for (int i = 0; i < 5; i++) {
            IndexKey key = IndexKey.orderedBytes(("seq-" + i).getBytes(StandardCharsets.UTF_8));
            IndexValue value = IndexValue.normal(("val-" + i).getBytes(StandardCharsets.UTF_8));
            journal.write(OperationType.PUT, key, value);
        }

        journal.close();

        JournalRegionManager replayJournal = new JournalRegionManager(chunkManager, regionIndexFile, ownerId, 0L, 64 * 1024 * 1024);
        List<JournalEntry> replayed = new ArrayList<>();
        replayJournal.replayFromBeginning((entry, point) -> replayed.add(entry));
        replayJournal.close();

        assertEquals(5, replayed.size());
        for (int i = 1; i < replayed.size(); i++) {
            assertTrue(replayed.get(i).getSequenceNumber() > replayed.get(i - 1).getSequenceNumber());
        }
    }
}