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

class JournalBatchWriteTest {

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
        try {
            journalRegionManager.close();
        } catch (IllegalStateException ignored) {
        }
        chunkManager.close();
    }

    @Test
    void testBatchWrite() throws IOException {
        List<JournalEntry.KeyValuePair> operations = new ArrayList<>();
        for (int i = 0; i < 10; i++) {
            operations.add(new JournalEntry.KeyValuePair(
                    IndexKey.orderedBytes(("key-" + i).getBytes(StandardCharsets.UTF_8)),
                    IndexValue.normal(("value-" + i).getBytes(StandardCharsets.UTF_8))
            ));
        }

        JournalReplayPoint point = journalRegionManager.writeBatch(operations);

        assertNotNull(point);
        assertTrue(point.getRegionMajor() > 0);
    }

    @Test
    void testBatchReplay() throws IOException {
        List<JournalEntry.KeyValuePair> operations = new ArrayList<>();
        operations.add(new JournalEntry.KeyValuePair(
                IndexKey.orderedBytes("batch-key-1".getBytes(StandardCharsets.UTF_8)),
                IndexValue.normal("batch-value-1".getBytes(StandardCharsets.UTF_8))
        ));
        operations.add(new JournalEntry.KeyValuePair(
                IndexKey.orderedBytes("batch-key-2".getBytes(StandardCharsets.UTF_8)),
                IndexValue.normal("batch-value-2".getBytes(StandardCharsets.UTF_8))
        ));
        operations.add(new JournalEntry.KeyValuePair(
                IndexKey.orderedBytes("batch-key-3".getBytes(StandardCharsets.UTF_8)),
                IndexValue.tombstone()
        ));

        journalRegionManager.writeBatch(operations);
        journalRegionManager.close();

        File regionIndexFile = new File(tempDir.toFile(), "journal-region-index");
        JournalRegionManager replayJournal = new JournalRegionManager(chunkManager, regionIndexFile, ownerId, 0L, 64 * 1024 * 1024);
        List<JournalEntry> replayed = new ArrayList<>();
        replayJournal.replayFromBeginning((entry, point) -> replayed.add(entry));
        replayJournal.close();

        assertEquals(1, replayed.size());
        assertEquals(OperationType.BATCH, replayed.get(0).getOperationType());
        assertEquals(3, replayed.get(0).getEntries().size());
    }

    @Test
    void testBatchAtomicity() throws IOException {
        List<JournalEntry.KeyValuePair> operations = new ArrayList<>();
        for (int i = 0; i < 5; i++) {
            operations.add(new JournalEntry.KeyValuePair(
                    IndexKey.orderedBytes(("atomic-" + i).getBytes(StandardCharsets.UTF_8)),
                    IndexValue.normal(("val-" + i).getBytes(StandardCharsets.UTF_8))
            ));
        }

        journalRegionManager.writeBatch(operations);
        journalRegionManager.close();

        File regionIndexFile = new File(tempDir.toFile(), "journal-region-index");
        JournalRegionManager replayJournal = new JournalRegionManager(chunkManager, regionIndexFile, ownerId, 0L, 64 * 1024 * 1024);
        List<JournalEntry> replayed = new ArrayList<>();
        replayJournal.replayFromBeginning((entry, point) -> replayed.add(entry));
        replayJournal.close();

        assertEquals(1, replayed.size());
        assertEquals(5, replayed.get(0).getEntries().size());
    }

    @Test
    void testMultipleBatches() throws IOException {
        for (int batch = 0; batch < 3; batch++) {
            List<JournalEntry.KeyValuePair> operations = new ArrayList<>();
            for (int i = 0; i < 3; i++) {
                operations.add(new JournalEntry.KeyValuePair(
                        IndexKey.orderedBytes(("batch-" + batch + "-key-" + i).getBytes(StandardCharsets.UTF_8)),
                        IndexValue.normal(("val-" + i).getBytes(StandardCharsets.UTF_8))
                ));
            }
            journalRegionManager.writeBatch(operations);
        }
        journalRegionManager.close();

        File regionIndexFile = new File(tempDir.toFile(), "journal-region-index");
        JournalRegionManager replayJournal = new JournalRegionManager(chunkManager, regionIndexFile, ownerId, 0L, 64 * 1024 * 1024);
        List<JournalEntry> replayed = new ArrayList<>();
        replayJournal.replayFromBeginning((entry, point) -> replayed.add(entry));
        replayJournal.close();

        assertEquals(3, replayed.size());
        for (JournalEntry entry : replayed) {
            assertEquals(OperationType.BATCH, entry.getOperationType());
            assertEquals(3, entry.getEntries().size());
        }
    }

    @Test
    void testBatchWithMixedOperations() throws IOException {
        List<JournalEntry.KeyValuePair> operations = new ArrayList<>();
        
        operations.add(new JournalEntry.KeyValuePair(
                IndexKey.orderedBytes("put-key-1".getBytes(StandardCharsets.UTF_8)),
                IndexValue.normal("put-value-1".getBytes(StandardCharsets.UTF_8))
        ));
        operations.add(new JournalEntry.KeyValuePair(
                IndexKey.orderedBytes("delete-key-1".getBytes(StandardCharsets.UTF_8)),
                IndexValue.tombstone()
        ));
        operations.add(new JournalEntry.KeyValuePair(
                IndexKey.orderedBytes("put-key-2".getBytes(StandardCharsets.UTF_8)),
                IndexValue.normal("put-value-2".getBytes(StandardCharsets.UTF_8))
        ));

        journalRegionManager.writeBatch(operations);
        journalRegionManager.close();

        File regionIndexFile = new File(tempDir.toFile(), "journal-region-index");
        JournalRegionManager replayJournal = new JournalRegionManager(chunkManager, regionIndexFile, ownerId, 0L, 64 * 1024 * 1024);
        List<JournalEntry> replayed = new ArrayList<>();
        replayJournal.replayFromBeginning((entry, point) -> replayed.add(entry));
        replayJournal.close();

        assertEquals(1, replayed.size());
        JournalEntry entry = replayed.get(0);
        assertEquals(OperationType.BATCH, entry.getOperationType());
        assertEquals(3, entry.getEntries().size());

        assertFalse(entry.getEntries().get(0).getValue().isTombstone());
        assertTrue(entry.getEntries().get(1).getValue().isTombstone());
        assertFalse(entry.getEntries().get(2).getValue().isTombstone());
    }

    @Test
    void testBatchWriteReturnsReplayPoint() throws IOException {
        List<JournalEntry.KeyValuePair> operations = new ArrayList<>();
        operations.add(new JournalEntry.KeyValuePair(
                IndexKey.orderedBytes("point-key".getBytes(StandardCharsets.UTF_8)),
                IndexValue.normal("point-value".getBytes(StandardCharsets.UTF_8))
        ));

        JournalReplayPoint point = journalRegionManager.writeBatch(operations);

        assertNotNull(point);
        assertTrue(point.getRegionMajor() > 0);
        assertTrue(point.getOffset() >= 4096);
    }

    @Test
    void testBatchWriteWithEmptyListThrows() {
        List<JournalEntry.KeyValuePair> operations = new ArrayList<>();
        
        assertThrows(IllegalArgumentException.class, () -> journalRegionManager.writeBatch(operations));
    }

    @Test
    void testBatchWritePreservesOrder() throws IOException {
        List<JournalEntry.KeyValuePair> operations = new ArrayList<>();
        for (int i = 0; i < 5; i++) {
            operations.add(new JournalEntry.KeyValuePair(
                    IndexKey.orderedBytes(("order-" + i).getBytes(StandardCharsets.UTF_8)),
                    IndexValue.normal(("val-" + i).getBytes(StandardCharsets.UTF_8))
            ));
        }

        journalRegionManager.writeBatch(operations);
        journalRegionManager.close();

        File regionIndexFile = new File(tempDir.toFile(), "journal-region-index");
        JournalRegionManager replayJournal = new JournalRegionManager(chunkManager, regionIndexFile, ownerId, 0L, 64 * 1024 * 1024);
        List<JournalEntry> replayed = new ArrayList<>();
        replayJournal.replayFromBeginning((entry, point) -> replayed.add(entry));
        replayJournal.close();

        JournalEntry entry = replayed.get(0);
        for (int i = 0; i < 5; i++) {
            byte[] expectedKey = ("order-" + i).getBytes(StandardCharsets.UTF_8);
            assertArrayEquals(expectedKey, entry.getEntries().get(i).getKey().getKeyData());
        }
    }
}