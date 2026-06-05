package org.hyperkv.lsmplus.journal;

import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.io.TempDir;

import java.io.File;
import java.io.IOException;
import java.nio.file.Path;
import java.util.UUID;

import static org.junit.jupiter.api.Assertions.*;

class JournalRegionIndexFileTest {

    @TempDir
    Path tempDir;

    @Test
    void testCreateRegionIndex() {
        File file = new File(tempDir.toFile(), "journal-region.pb");
        UUID instanceId = UUID.randomUUID();

        JournalRegionIndexFile index = new JournalRegionIndexFile(file, instanceId);

        assertNotNull(index);
        assertTrue(index.getEntries().isEmpty());
    }

    @Test
    void testAddEntry() {
        File file = new File(tempDir.toFile(), "journal-region.pb");
        UUID instanceId = UUID.randomUUID();

        JournalRegionIndexFile index = new JournalRegionIndexFile(file, instanceId);
        UUID chunkId = UUID.randomUUID();

        JournalRegionIndexFile.RegionEntry entry = new JournalRegionIndexFile.RegionEntry(
                1, 0, chunkId, 0, -1, System.currentTimeMillis());
        index.addEntry(entry);

        assertEquals(1, index.getEntries().size());
        assertEquals(1, index.getEntries().get(0).regionMajor());
        assertEquals(chunkId, index.getEntries().get(0).chunkId());
    }

    @Test
    void testPersistAndLoad() throws IOException {
        File file = new File(tempDir.toFile(), "journal-region.pb");
        UUID instanceId = UUID.randomUUID();

        JournalRegionIndexFile index = new JournalRegionIndexFile(file, instanceId);
        UUID chunkId1 = UUID.randomUUID();
        UUID chunkId2 = UUID.randomUUID();

        index.addEntry(new JournalRegionIndexFile.RegionEntry(1, 0, chunkId1, 0, -1, 1000L));
        index.addEntry(new JournalRegionIndexFile.RegionEntry(2, 0, chunkId2, 0, -1, 2000L));

        index.persist();
        assertTrue(file.exists());

        JournalRegionIndexFile loaded = JournalRegionIndexFile.load(file, instanceId);
        assertEquals(2, loaded.getEntries().size());

        assertEquals(1, loaded.getEntries().get(0).regionMajor());
        assertEquals(chunkId1, loaded.getEntries().get(0).chunkId());
        assertEquals(0, loaded.getEntries().get(0).offset());
        assertEquals(-1, loaded.getEntries().get(0).length());

        assertEquals(2, loaded.getEntries().get(1).regionMajor());
        assertEquals(chunkId2, loaded.getEntries().get(1).chunkId());
    }

    @Test
    void testMultipleEntries() throws IOException {
        File file = new File(tempDir.toFile(), "journal-region.pb");
        UUID instanceId = UUID.randomUUID();

        JournalRegionIndexFile index = new JournalRegionIndexFile(file, instanceId);

        for (int i = 1; i <= 10; i++) {
            index.addEntry(new JournalRegionIndexFile.RegionEntry(
                    i, 0, UUID.randomUUID(), 0, -1, System.currentTimeMillis()));
        }

        index.persist();

        JournalRegionIndexFile loaded = JournalRegionIndexFile.load(file, instanceId);
        assertEquals(10, loaded.getEntries().size());

        for (int i = 0; i < 10; i++) {
            assertEquals(i + 1, loaded.getEntries().get(i).regionMajor());
        }
    }

    @Test
    void testRegionRecovery() throws IOException {
        File file = new File(tempDir.toFile(), "journal-region.pb");
        UUID instanceId = UUID.randomUUID();

        JournalRegionIndexFile index = new JournalRegionIndexFile(file, instanceId);
        UUID chunkId = UUID.randomUUID();
        index.addEntry(new JournalRegionIndexFile.RegionEntry(1, 0, chunkId, 0, -1, 1000L));
        index.persist();

        JournalRegionIndexFile recovered = JournalRegionIndexFile.load(file, instanceId);
        assertEquals(1, recovered.getEntries().size());

        JournalRegionIndexFile.RegionEntry entry = recovered.getEntries().get(0);
        assertEquals(1, entry.regionMajor());
        assertEquals(0, entry.regionMinor());
        assertEquals(chunkId, entry.chunkId());
        assertEquals(0, entry.offset());
        assertEquals(-1, entry.length());
        assertEquals(1000L, entry.createdAt());
    }

    @Test
    void testLoadNonExistentFile() throws IOException {
        File file = new File(tempDir.toFile(), "nonexistent.pb");
        UUID instanceId = UUID.randomUUID();

        JournalRegionIndexFile loaded = JournalRegionIndexFile.load(file, instanceId);
        assertTrue(loaded.getEntries().isEmpty());
    }

    @Test
    void testRegionEntryProtoRoundTrip() {
        UUID chunkId = UUID.randomUUID();
        JournalRegionIndexFile.RegionEntry original = new JournalRegionIndexFile.RegionEntry(
                5, 2, chunkId, 4096, 8192, 1234567890L);

        var proto = original.toProto();
        JournalRegionIndexFile.RegionEntry restored = JournalRegionIndexFile.RegionEntry.fromProto(proto);

        assertEquals(original.regionMajor(), restored.regionMajor());
        assertEquals(original.regionMinor(), restored.regionMinor());
        assertEquals(original.chunkId(), restored.chunkId());
        assertEquals(original.offset(), restored.offset());
        assertEquals(original.length(), restored.length());
        assertEquals(original.createdAt(), restored.createdAt());
    }
}