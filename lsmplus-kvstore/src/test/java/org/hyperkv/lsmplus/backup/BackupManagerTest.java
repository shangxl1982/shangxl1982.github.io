package org.hyperkv.lsmplus.backup;

import org.hyperkv.lsmplus.storage.ChunkManager;
import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.io.TempDir;

import java.io.File;
import java.io.IOException;
import java.util.List;
import java.util.UUID;

import static org.junit.jupiter.api.Assertions.*;

class BackupManagerTest {

    @TempDir
    File tempDir;

    @TempDir
    File backupDir;

    private ChunkManager chunkManager;
    private BackupManager backupManager;

    @BeforeEach
    void setUp() throws IOException {
        chunkManager = new ChunkManager(tempDir.getAbsolutePath(), UUID.randomUUID(), UUID.randomUUID());
        backupManager = new BackupManager(chunkManager, backupDir);
    }

    @AfterEach
    void tearDown() throws IOException {
        if (chunkManager != null) {
            chunkManager.close();
        }
    }

    @Test
    void testCreateFullBackup() throws IOException {
        BackupMetadata metadata = backupManager.createFullBackup();

        assertNotNull(metadata);
        assertNotNull(metadata.getBackupId());
        assertEquals(BackupType.FULL, metadata.getType());
        assertNotNull(metadata.getTimestamp());
        assertTrue(metadata.getSize() > 0);
        assertTrue(metadata.getLocation().exists());
        assertTrue(metadata.isFull());
        assertFalse(metadata.isIncremental());
    }

    @Test
    void testGetBackup() throws IOException {
        BackupMetadata created = backupManager.createFullBackup();
        BackupMetadata retrieved = backupManager.getBackup(created.getBackupId());

        assertNotNull(retrieved);
        assertEquals(created.getBackupId(), retrieved.getBackupId());
        assertEquals(created.getType(), retrieved.getType());
    }

    @Test
    void testListBackups() throws IOException {
        backupManager.createFullBackup();
        backupManager.createFullBackup();

        List<BackupMetadata> backups = backupManager.listBackups();
        assertEquals(2, backups.size());
    }

    @Test
    void testListFullBackups() throws IOException {
        backupManager.createFullBackup();

        List<BackupMetadata> fullBackups = backupManager.listFullBackups();
        assertEquals(1, fullBackups.size());
        assertTrue(fullBackups.get(0).isFull());
    }

    @Test
    void testDeleteBackup() throws IOException {
        BackupMetadata metadata = backupManager.createFullBackup();
        UUID backupId = metadata.getBackupId();

        assertTrue(metadata.getLocation().exists());
        assertEquals(1, backupManager.getBackupCount());

        backupManager.deleteBackup(backupId);

        assertNull(backupManager.getBackup(backupId));
        assertEquals(0, backupManager.getBackupCount());
    }

    @Test
    void testRestore() throws IOException {
        BackupMetadata metadata = backupManager.createFullBackup();

        File restoreDir = new File(tempDir, "restore");
        backupManager.restore(metadata, restoreDir);

        assertTrue(restoreDir.exists());
    }

    @Test
    void testGetBackupCount() throws IOException {
        assertEquals(0, backupManager.getBackupCount());

        backupManager.createFullBackup();
        assertEquals(1, backupManager.getBackupCount());

        backupManager.createFullBackup();
        assertEquals(2, backupManager.getBackupCount());
    }

    @Test
    void testBackupMetadataToString() throws IOException {
        BackupMetadata metadata = backupManager.createFullBackup();
        String str = metadata.toString();
        assertTrue(str.contains("type=FULL"));
    }
}
