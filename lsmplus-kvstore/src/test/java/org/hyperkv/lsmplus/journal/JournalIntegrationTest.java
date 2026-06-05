package org.hyperkv.lsmplus.journal;

import org.hyperkv.lsmplus.api.model.IndexKey;
import org.hyperkv.lsmplus.api.model.IndexValue;
import org.hyperkv.lsmplus.proto.Common.OperationType;
import org.hyperkv.lsmplus.storage.ChunkManager;
import org.hyperkv.lsmplus.storage.StorageLayout;
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

class JournalIntegrationTest {

    @TempDir
    Path tempDir;

    private StorageLayout layout;
    private ChunkManager chunkManager;
    private UUID ownerId;
    private UUID namespaceId;

    @BeforeEach
    void setUp() throws IOException {
        ownerId = UUID.randomUUID();
        namespaceId = UUID.randomUUID();
        layout = new StorageLayout(tempDir.toFile());
        layout.initialize();
        chunkManager = new ChunkManager(layout.getBaseDir().getAbsolutePath(), ownerId, namespaceId);
    }

    @AfterEach
    void tearDown() throws IOException {
        chunkManager.close();
    }

    @Test
    void testCompleteWriteReplayCycle() throws IOException {
        File regionIndexFile = new File(tempDir.toFile(), "journal-region-index");
        JournalRegionManager journal = new JournalRegionManager(chunkManager, regionIndexFile, ownerId, 0L, 64 * 1024 * 1024);

        for (int i = 0; i < 20; i++) {
            IndexKey key = IndexKey.orderedBytes(("cycle-key-" + i).getBytes(StandardCharsets.UTF_8));
            IndexValue value = IndexValue.normal(("cycle-value-" + i).getBytes(StandardCharsets.UTF_8));
            journal.write(OperationType.PUT, key, value);
        }

        journal.close();

        JournalRegionManager replayJournal = new JournalRegionManager(chunkManager, regionIndexFile, ownerId, 0L, 64 * 1024 * 1024);
        List<JournalEntry> replayed = new ArrayList<>();
        replayJournal.replayFromBeginning((entry, point) -> replayed.add(entry));
        replayJournal.close();

        assertEquals(20, replayed.size());
        for (int i = 0; i < 20; i++) {
            assertEquals(OperationType.PUT, replayed.get(i).getOperationType());
        }
    }

    @Test
    void testCrashRecovery() throws IOException {
        File regionIndexFile = new File(tempDir.toFile(), "journal-region-index");
        JournalRegionManager journal = new JournalRegionManager(chunkManager, regionIndexFile, ownerId, 0L, 64 * 1024 * 1024);

        IndexKey key1 = IndexKey.orderedBytes("crash-key-1".getBytes(StandardCharsets.UTF_8));
        IndexValue value1 = IndexValue.normal("crash-value-1".getBytes(StandardCharsets.UTF_8));
        journal.write(OperationType.PUT, key1, value1);

        IndexKey key2 = IndexKey.orderedBytes("crash-key-2".getBytes(StandardCharsets.UTF_8));
        IndexValue value2 = IndexValue.normal("crash-value-2".getBytes(StandardCharsets.UTF_8));
        journal.write(OperationType.PUT, key2, value2);

        journal.close();

        JournalRegionManager recoveryJournal = new JournalRegionManager(chunkManager, regionIndexFile, ownerId, 0L, 64 * 1024 * 1024);
        List<JournalEntry> recovered = new ArrayList<>();
        recoveryJournal.replayFromBeginning((entry, point) -> recovered.add(entry));
        recoveryJournal.close();

        assertEquals(2, recovered.size());
    }

    @Test
    void testMultipleRegions() throws IOException {
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
        replayJournal.replayFromBeginning((entry, point) -> replayed.add(entry));
        replayJournal.close();

        assertEquals(3, replayed.size());
    }

    @Test
    void testBatchRecovery() throws IOException {
        File regionIndexFile = new File(tempDir.toFile(), "journal-region-index");
        JournalRegionManager journal = new JournalRegionManager(chunkManager, regionIndexFile, ownerId, 0L, 64 * 1024 * 1024);

        List<JournalEntry.KeyValuePair> batch1 = new ArrayList<>();
        for (int i = 0; i < 5; i++) {
            batch1.add(new JournalEntry.KeyValuePair(
                    IndexKey.orderedBytes(("batch1-key-" + i).getBytes(StandardCharsets.UTF_8)),
                    IndexValue.normal(("batch1-val-" + i).getBytes(StandardCharsets.UTF_8))
            ));
        }
        journal.writeBatch(batch1);

        List<JournalEntry.KeyValuePair> batch2 = new ArrayList<>();
        for (int i = 0; i < 3; i++) {
            batch2.add(new JournalEntry.KeyValuePair(
                    IndexKey.orderedBytes(("batch2-key-" + i).getBytes(StandardCharsets.UTF_8)),
                    IndexValue.normal(("batch2-val-" + i).getBytes(StandardCharsets.UTF_8))
            ));
        }
        journal.writeBatch(batch2);

        journal.close();

        JournalRegionManager replayJournal = new JournalRegionManager(chunkManager, regionIndexFile, ownerId, 0L, 64 * 1024 * 1024);
        List<JournalEntry> replayed = new ArrayList<>();
        replayJournal.replayFromBeginning((entry, point) -> replayed.add(entry));
        replayJournal.close();

        assertEquals(2, replayed.size());
        assertEquals(5, replayed.get(0).getEntries().size());
        assertEquals(3, replayed.get(1).getEntries().size());
    }

    @Test
    void testMixedOperationsRecovery() throws IOException {
        File regionIndexFile = new File(tempDir.toFile(), "journal-region-index");
        JournalRegionManager journal = new JournalRegionManager(chunkManager, regionIndexFile, ownerId, 0L, 64 * 1024 * 1024);

        IndexKey putKey = IndexKey.orderedBytes("put-key".getBytes(StandardCharsets.UTF_8));
        IndexValue putValue = IndexValue.normal("put-value".getBytes(StandardCharsets.UTF_8));
        journal.write(OperationType.PUT, putKey, putValue);

        IndexKey deleteKey = IndexKey.orderedBytes("delete-key".getBytes(StandardCharsets.UTF_8));
        journal.write(OperationType.DELETE, deleteKey, IndexValue.tombstone());

        List<JournalEntry.KeyValuePair> batch = new ArrayList<>();
        batch.add(new JournalEntry.KeyValuePair(
                IndexKey.orderedBytes("batch-put".getBytes(StandardCharsets.UTF_8)),
                IndexValue.normal("batch-value".getBytes(StandardCharsets.UTF_8))
        ));
        batch.add(new JournalEntry.KeyValuePair(
                IndexKey.orderedBytes("batch-delete".getBytes(StandardCharsets.UTF_8)),
                IndexValue.tombstone()
        ));
        journal.writeBatch(batch);

        journal.close();

        JournalRegionManager replayJournal = new JournalRegionManager(chunkManager, regionIndexFile, ownerId, 0L, 64 * 1024 * 1024);
        List<JournalEntry> replayed = new ArrayList<>();
        replayJournal.replayFromBeginning((entry, point) -> replayed.add(entry));
        replayJournal.close();

        assertEquals(3, replayed.size());
        assertEquals(OperationType.PUT, replayed.get(0).getOperationType());
        assertEquals(OperationType.DELETE, replayed.get(1).getOperationType());
        assertEquals(OperationType.BATCH, replayed.get(2).getOperationType());
    }

    @Test
    void testStorageLayoutIntegration() throws IOException {
        assertTrue(layout.getJournalDir().exists());

        File regionIndexFile = new File(tempDir.toFile(), "journal-region-index");
        JournalRegionManager journal = new JournalRegionManager(chunkManager, regionIndexFile, ownerId, 0L, 64 * 1024 * 1024);

        IndexKey key = IndexKey.orderedBytes("layout-key".getBytes(StandardCharsets.UTF_8));
        IndexValue value = IndexValue.normal("layout-value".getBytes(StandardCharsets.UTF_8));
        journal.write(OperationType.PUT, key, value);

        journal.close();

        File[] journalFiles = layout.getJournalDir().listFiles();
        assertNotNull(journalFiles);
        assertTrue(journalFiles.length > 0);
    }
}