package org.hyperkv.lsmplus.journal;

import org.hyperkv.lsmplus.api.model.IndexKey;
import org.hyperkv.lsmplus.api.model.IndexValue;
import org.hyperkv.lsmplus.proto.Common.OperationType;
import org.hyperkv.lsmplus.storage.ChunkManager;
import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.io.TempDir;

import java.io.File;
import java.io.IOException;
import java.nio.charset.StandardCharsets;
import java.nio.file.Path;
import java.util.ArrayList;
import java.util.List;
import java.util.UUID;

import static org.junit.jupiter.api.Assertions.*;

class JournalTest {

    @TempDir
    Path tempDir;

    private ChunkManager chunkManager;
    private JournalRegionManager journalRegionManager;
    private UUID ownerId;
    private UUID namespaceId;

    @BeforeEach
    void setUp() throws IOException {
        ownerId = UUID.randomUUID();
        namespaceId = UUID.randomUUID();
        chunkManager = new ChunkManager(tempDir.toFile().getAbsolutePath(), ownerId, namespaceId);
        File regionIndexFile = new File(tempDir.toFile(), "journal-region-index");
        journalRegionManager = new JournalRegionManager(chunkManager, regionIndexFile, ownerId, 0L, 64 * 1024 * 1024);
    }

    @AfterEach
    void tearDown() throws IOException {
        if (journalRegionManager != null) {
            try {
                journalRegionManager.close();
            } catch (IllegalStateException ignored) {
            }
        }
        if (chunkManager != null) {
            chunkManager.close();
        }
    }

    @Test
    void testWriteSinglePutEntry() throws IOException {
        IndexKey key = IndexKey.orderedBytes("test-key".getBytes(StandardCharsets.UTF_8));
        IndexValue value = IndexValue.normal("test-value".getBytes(StandardCharsets.UTF_8));

        JournalReplayPoint point = journalRegionManager.write(OperationType.PUT, key, value);

        assertNotNull(point);
        assertTrue(point.getRegionMajor() > 0);
    }

    @Test
    void testWriteSingleDeleteEntry() throws IOException {
        IndexKey key = IndexKey.orderedBytes("delete-key".getBytes(StandardCharsets.UTF_8));

        JournalReplayPoint point = journalRegionManager.write(OperationType.DELETE, key, IndexValue.tombstone());

        assertNotNull(point);
        assertTrue(point.getRegionMajor() > 0);
    }

    @Test
    void testWriteMultipleEntries() throws IOException {
        for (int i = 0; i < 10; i++) {
            IndexKey key = IndexKey.orderedBytes(("key-" + i).getBytes(StandardCharsets.UTF_8));
            IndexValue value = IndexValue.normal(("value-" + i).getBytes(StandardCharsets.UTF_8));
            journalRegionManager.write(OperationType.PUT, key, value);
        }

        JournalRegion region = journalRegionManager.getCurrentRegion();
        assertNotNull(region);
    }

    @Test
    void testWriteBatchEntry() throws IOException {
        List<JournalEntry.KeyValuePair> operations = new ArrayList<>();
        for (int i = 0; i < 5; i++) {
            operations.add(new JournalEntry.KeyValuePair(
                    IndexKey.orderedBytes(("batch-key-" + i).getBytes(StandardCharsets.UTF_8)),
                    IndexValue.normal(("batch-value-" + i).getBytes(StandardCharsets.UTF_8))
            ));
        }

        JournalReplayPoint point = journalRegionManager.writeBatch(operations);
        assertNotNull(point);
    }

    @Test
    void testReplayFromBeginning() throws IOException {
        List<JournalEntry> replayed = new ArrayList<>();

        IndexKey key1 = IndexKey.orderedBytes("replay-key-1".getBytes(StandardCharsets.UTF_8));
        IndexValue value1 = IndexValue.normal("replay-value-1".getBytes(StandardCharsets.UTF_8));
        journalRegionManager.write(OperationType.PUT, key1, value1);

        IndexKey key2 = IndexKey.orderedBytes("replay-key-2".getBytes(StandardCharsets.UTF_8));
        IndexValue value2 = IndexValue.normal("replay-value-2".getBytes(StandardCharsets.UTF_8));
        journalRegionManager.write(OperationType.PUT, key2, value2);

        journalRegionManager.close();
        journalRegionManager = null;

        File regionIndexFile = new File(tempDir.toFile(), "journal-region-index");
        JournalRegionManager replayJournalRegionManager = new JournalRegionManager(chunkManager, regionIndexFile, ownerId, 0L, 64 * 1024 * 1024);
        replayJournalRegionManager.replayFromBeginning((entry, point) -> replayed.add(entry));
        replayJournalRegionManager.close();

        assertFalse(replayed.isEmpty());
    }

    @Test
    void testSequenceNumbers() throws IOException {
        IndexKey key = IndexKey.orderedBytes("seq-key".getBytes(StandardCharsets.UTF_8));
        IndexValue value = IndexValue.normal("seq-value".getBytes(StandardCharsets.UTF_8));

        List<JournalEntry> entries = new ArrayList<>();

        journalRegionManager.write(OperationType.PUT, key, value);
        journalRegionManager.write(OperationType.PUT, key, value);
        journalRegionManager.write(OperationType.PUT, key, value);

        journalRegionManager.close();
        journalRegionManager = null;

        File regionIndexFile = new File(tempDir.toFile(), "journal-region-index");
        JournalRegionManager replayJournalRegionManager = new JournalRegionManager(chunkManager, regionIndexFile, ownerId, 0L, 64 * 1024 * 1024);
        replayJournalRegionManager.replayFromBeginning((entry, point) -> entries.add(entry));
        replayJournalRegionManager.close();

        for (int i = 1; i < entries.size(); i++) {
            assertTrue(entries.get(i).getSequenceNumber() > entries.get(i - 1).getSequenceNumber(),
                    "Sequence numbers should be monotonically increasing");
        }
    }

    @Test
    void testClose() throws IOException {
        IndexKey key = IndexKey.orderedBytes("close-key".getBytes(StandardCharsets.UTF_8));
        IndexValue value = IndexValue.normal("close-value".getBytes(StandardCharsets.UTF_8));
        journalRegionManager.write(OperationType.PUT, key, value);

        journalRegionManager.close();
        journalRegionManager = null;
    }

    @Test
    void testRotateChunk() throws IOException {
        IndexKey key = IndexKey.orderedBytes("rotate-key".getBytes(StandardCharsets.UTF_8));
        IndexValue value = IndexValue.normal("rotate-value".getBytes(StandardCharsets.UTF_8));
        journalRegionManager.write(OperationType.PUT, key, value);

        JournalRegion region1 = journalRegionManager.getCurrentRegion();
        assertNotNull(region1);

        journalRegionManager.rotateRegion();

        journalRegionManager.write(OperationType.PUT, key, value);
        JournalRegion region2 = journalRegionManager.getCurrentRegion();
        assertNotNull(region2);
        assertNotEquals(region1.getChunkId(), region2.getChunkId());
        assertTrue(region2.getMajor() > region1.getMajor());
    }

    @Test
    void testJournalRegion() {
        UUID chunkId = UUID.randomUUID();
        JournalRegion region = new JournalRegion(1, 0, chunkId);

        assertEquals(1, region.getMajor());
        assertEquals(0, region.getMinor());
        assertEquals(chunkId, region.getChunkId());
    }

    @Test
    void testJournalReplayPoint() {
        JournalReplayPoint point = new JournalReplayPoint(5, 2, 4096);

        assertEquals(5, point.getRegionMajor());
        assertEquals(2, point.getRegionMinor());
        assertEquals(4096, point.getOffset());

        var proto = point.toProto();
        JournalReplayPoint restored = JournalReplayPoint.fromProto(proto);
        assertEquals(point, restored);
    }

    @Test
    void testJournalEntryPut() {
        IndexKey key = IndexKey.orderedBytes("entry-key".getBytes(StandardCharsets.UTF_8));
        IndexValue value = IndexValue.normal("entry-value".getBytes(StandardCharsets.UTF_8));

        JournalEntry entry = JournalEntry.put(key, value, 42L);

        assertEquals(OperationType.PUT, entry.getOperationType());
        assertEquals(42L, entry.getSequenceNumber());
        assertEquals(1, entry.getEntries().size());
        assertEquals(key, entry.getEntries().get(0).getKey());
    }

    @Test
    void testJournalEntryDelete() {
        IndexKey key = IndexKey.orderedBytes("del-key".getBytes(StandardCharsets.UTF_8));

        JournalEntry entry = JournalEntry.delete(key, 99L);

        assertEquals(OperationType.DELETE, entry.getOperationType());
        assertEquals(99L, entry.getSequenceNumber());
        assertTrue(entry.getEntries().get(0).getValue().isTombstone());
    }

    @Test
    void testJournalEntryProtoRoundTrip() {
        IndexKey key = IndexKey.orderedBytes("proto-key".getBytes(StandardCharsets.UTF_8));
        IndexValue value = IndexValue.normal("proto-value".getBytes(StandardCharsets.UTF_8));
        JournalEntry original = JournalEntry.put(key, value, 100L);

        var proto = original.toProto();
        JournalEntry restored = JournalEntry.fromProto(proto);

        assertEquals(original.getOperationType(), restored.getOperationType());
        assertEquals(original.getSequenceNumber(), restored.getSequenceNumber());
        assertEquals(original.getEntries().size(), restored.getEntries().size());
    }
}