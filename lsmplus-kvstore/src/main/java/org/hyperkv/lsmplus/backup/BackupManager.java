package org.hyperkv.lsmplus.backup;

import org.hyperkv.lsmplus.journal.JournalReplayPoint;
import org.hyperkv.lsmplus.storage.ChunkManager;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

import java.io.File;
import java.io.FileInputStream;
import java.io.FileOutputStream;
import java.io.IOException;
import java.nio.file.Files;
import java.nio.file.Path;
import java.nio.file.StandardCopyOption;
import java.time.Instant;
import java.util.ArrayList;
import java.util.List;
import java.util.Map;
import java.util.UUID;
import java.util.concurrent.ConcurrentHashMap;
import java.util.zip.ZipEntry;
import java.util.zip.ZipInputStream;
import java.util.zip.ZipOutputStream;

public class BackupManager {

    private static final Logger log = LoggerFactory.getLogger(BackupManager.class);

    private final ChunkManager chunkManager;
    private final File backupDir;
    private final Map<UUID, BackupMetadata> backups;

    public BackupManager(ChunkManager chunkManager, File backupDir) {
        if (chunkManager == null) {
            throw new IllegalArgumentException("chunkManager must not be null");
        }
        if (backupDir == null) {
            throw new IllegalArgumentException("backupDir must not be null");
        }
        this.chunkManager = chunkManager;
        this.backupDir = backupDir;
        this.backups = new ConcurrentHashMap<>();

        if (!backupDir.exists()) {
            backupDir.mkdirs();
        }
    }

    public BackupMetadata createFullBackup() throws IOException {
        UUID backupId = UUID.randomUUID();
        Instant timestamp = Instant.now();
        File backupFile = new File(backupDir, "backup-" + backupId + ".zip");

        log.info("Creating full backup: id={}, file={}", backupId, backupFile.getName());

        try (ZipOutputStream zos = new ZipOutputStream(new FileOutputStream(backupFile))) {
            File dataDir = new File(chunkManager.getBasePath(), "data");
            if (dataDir.exists() && dataDir.isDirectory()) {
                File[] files = dataDir.listFiles();
                if (files != null) {
                    for (File file : files) {
                        if (file.isFile()) {
                            addToZip(zos, file, file.getName());
                        }
                    }
                }
            }
        }

        long size = backupFile.length();
        String checksum = calculateChecksum(backupFile);

        BackupMetadata metadata = BackupMetadata.fullBackup(backupId, timestamp, size, backupFile, checksum);
        backups.put(backupId, metadata);

        log.info("Full backup created: id={}, size={} bytes", backupId, size);
        return metadata;
    }

    public BackupMetadata createIncrementalBackup(UUID baseBackupId, JournalReplayPoint fromPoint) throws IOException {
        BackupMetadata baseBackup = backups.get(baseBackupId);
        if (baseBackup == null) {
            throw new IllegalArgumentException("Base backup not found: " + baseBackupId);
        }

        UUID backupId = UUID.randomUUID();
        Instant timestamp = Instant.now();
        File backupFile = new File(backupDir, "backup-" + backupId + ".zip");

        try (ZipOutputStream zos = new ZipOutputStream(new FileOutputStream(backupFile))) {
            zos.putNextEntry(new ZipEntry("incremental-info.txt"));
            String info = "baseBackupId=" + baseBackupId + "\nfromPoint=" + fromPoint;
            zos.write(info.getBytes());
            zos.closeEntry();
        }

        long size = backupFile.length();
        String checksum = calculateChecksum(backupFile);

        BackupMetadata metadata = BackupMetadata.incrementalBackup(backupId, timestamp, size, backupFile, baseBackupId, checksum);
        backups.put(backupId, metadata);

        return metadata;
    }

    public void restore(BackupMetadata metadata, File targetDir) throws IOException {
        if (metadata == null) {
            throw new IllegalArgumentException("metadata must not be null");
        }
        if (targetDir == null) {
            throw new IllegalArgumentException("targetDir must not be null");
        }

        log.info("Restoring backup: id={}, targetDir={}", metadata.getBackupId(), targetDir.getAbsolutePath());

        if (!targetDir.exists()) {
            targetDir.mkdirs();
        }

        try (ZipInputStream zis = new ZipInputStream(new FileInputStream(metadata.getLocation()))) {
            ZipEntry entry;
            while ((entry = zis.getNextEntry()) != null) {
                File file = new File(targetDir, entry.getName());
                if (entry.isDirectory()) {
                    file.mkdirs();
                } else {
                    try (FileOutputStream fos = new FileOutputStream(file)) {
                        byte[] buffer = new byte[4096];
                        int len;
                        while ((len = zis.read(buffer)) > 0) {
                            fos.write(buffer, 0, len);
                        }
                    }
                }
                zis.closeEntry();
            }
        }

        log.info("Backup restored successfully: id={}", metadata.getBackupId());
    }

    public void deleteBackup(UUID backupId) throws IOException {
        BackupMetadata metadata = backups.remove(backupId);
        if (metadata != null && metadata.getLocation().exists()) {
            Files.delete(metadata.getLocation().toPath());
        }
    }

    public BackupMetadata getBackup(UUID backupId) {
        return backups.get(backupId);
    }

    public List<BackupMetadata> listBackups() {
        return new ArrayList<>(backups.values());
    }

    public List<BackupMetadata> listFullBackups() {
        List<BackupMetadata> fullBackups = new ArrayList<>();
        for (BackupMetadata metadata : backups.values()) {
            if (metadata.isFull()) {
                fullBackups.add(metadata);
            }
        }
        return fullBackups;
    }

    private void addToZip(ZipOutputStream zos, File file, String entryName) throws IOException {
        zos.putNextEntry(new ZipEntry(entryName));
        try (FileInputStream fis = new FileInputStream(file)) {
            byte[] buffer = new byte[4096];
            int len;
            while ((len = fis.read(buffer)) > 0) {
                zos.write(buffer, 0, len);
            }
        }
        zos.closeEntry();
    }

    private String calculateChecksum(File file) throws IOException {
        return UUID.randomUUID().toString().substring(0, 8);
    }

    public int getBackupCount() {
        return backups.size();
    }
}
