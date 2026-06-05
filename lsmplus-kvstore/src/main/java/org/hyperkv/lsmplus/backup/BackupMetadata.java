package org.hyperkv.lsmplus.backup;

import java.io.File;
import java.time.Instant;
import java.util.UUID;

public class BackupMetadata {

    private final UUID backupId;
    private final BackupType type;
    private final Instant timestamp;
    private final long size;
    private final File location;
    private final UUID baseBackupId;
    private final String checksum;

    public BackupMetadata(UUID backupId, BackupType type, Instant timestamp,
                          long size, File location, UUID baseBackupId, String checksum) {
        this.backupId = backupId;
        this.type = type;
        this.timestamp = timestamp;
        this.size = size;
        this.location = location;
        this.baseBackupId = baseBackupId;
        this.checksum = checksum;
    }

    public static BackupMetadata fullBackup(UUID backupId, Instant timestamp,
                                            long size, File location, String checksum) {
        return new BackupMetadata(backupId, BackupType.FULL, timestamp, size, location, null, checksum);
    }

    public static BackupMetadata incrementalBackup(UUID backupId, Instant timestamp,
                                                   long size, File location, UUID baseBackupId, String checksum) {
        return new BackupMetadata(backupId, BackupType.INCREMENTAL, timestamp, size, location, baseBackupId, checksum);
    }

    public UUID getBackupId() {
        return backupId;
    }

    public BackupType getType() {
        return type;
    }

    public Instant getTimestamp() {
        return timestamp;
    }

    public long getSize() {
        return size;
    }

    public File getLocation() {
        return location;
    }

    public UUID getBaseBackupId() {
        return baseBackupId;
    }

    public String getChecksum() {
        return checksum;
    }

    public boolean isFull() {
        return type == BackupType.FULL;
    }

    public boolean isIncremental() {
        return type == BackupType.INCREMENTAL;
    }

    @Override
    public String toString() {
        return String.format("BackupMetadata{id=%s, type=%s, timestamp=%s, size=%d}",
                backupId, type, timestamp, size);
    }
}
