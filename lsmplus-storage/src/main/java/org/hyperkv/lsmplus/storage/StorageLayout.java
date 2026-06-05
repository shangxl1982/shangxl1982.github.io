package org.hyperkv.lsmplus.storage;

import java.io.File;

public final class StorageLayout {

    private final File baseDir;
    private final File dataDir;
    private final File journalDir;
    private final File occupancyDir;
    private final File metadataDir;
    private final File backupDir;

    public StorageLayout(File baseDir) {
        if (baseDir == null) {
            throw new IllegalArgumentException("baseDir must not be null");
        }
        this.baseDir = baseDir;
        this.dataDir = new File(baseDir, "data");
        this.journalDir = new File(baseDir, "journal");
        this.occupancyDir = new File(baseDir, "occupancy");
        this.metadataDir = new File(baseDir, "metadata");
        this.backupDir = new File(baseDir, "backup");
    }

    public void initialize() {
        baseDir.mkdirs();
        dataDir.mkdirs();
        journalDir.mkdirs();
        occupancyDir.mkdirs();
        metadataDir.mkdirs();
        backupDir.mkdirs();
    }

    public boolean exists() {
        return baseDir.exists() && dataDir.exists() && journalDir.exists();
    }

    public File getBaseDir() {
        return baseDir;
    }

    public File getDataDir() {
        return dataDir;
    }

    public File getJournalDir() {
        return journalDir;
    }

    public File getOccupancyDir() {
        return occupancyDir;
    }

    public File getMetadataDir() {
        return metadataDir;
    }

    public File getBackupDir() {
        return backupDir;
    }

    public File getTreeMetadataFile() {
        return new File(baseDir, "tree-metadata.pb");
    }

    public File getJournalRegionIndexFile() {
        return new File(baseDir, "journal-region.pb");
    }

    public File getChunkMetadataFile() {
        return new File(baseDir, "chunk-metadata.pb");
    }

    public File getOccupancyFile(long version) {
        return new File(occupancyDir, version + ".pb");
    }

    public File getChunkFile(java.util.UUID chunkId, org.hyperkv.lsmplus.proto.Common.ChunkType chunkType) {
        File dir = (chunkType == org.hyperkv.lsmplus.proto.Common.ChunkType.CHUNK_JOURNAL) ? journalDir : dataDir;
        return new File(dir, "chunk_" + chunkId + ".dat");
    }
}