package org.hyperkv.lsmplus.core;

import org.hyperkv.lsmplus.journal.JournalReplayPoint;
import org.hyperkv.lsmplus.proto.Metadata.PageIdTracker;
import org.hyperkv.lsmplus.proto.Metadata.TreeMetadataEntry;
import org.hyperkv.lsmplus.proto.Metadata.TreeMetadataFile;
import org.hyperkv.lsmplus.proto.Metadata.TreeStats;
import org.hyperkv.lsmplus.storage.SegmentLocation;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

import java.io.File;
import java.io.FileOutputStream;
import java.io.IOException;
import java.nio.file.Files;
import java.nio.file.StandardCopyOption;
import java.util.ArrayList;
import java.util.Collections;
import java.util.List;

public class TreeMetadataManager {

    private static final Logger log = LoggerFactory.getLogger(TreeMetadataManager.class);

    private static final int MAGIC = 0x54524545;
    private static final int FORMAT_VERSION = 1;
    private static final int MAX_VERSIONS = 30;

    private final File metadataFile;
    private final int maxVersions;

    public TreeMetadataManager(File metadataFile) {
        this(metadataFile, MAX_VERSIONS);
    }

    public TreeMetadataManager(File metadataFile, int maxVersions) {
        if (metadataFile == null) {
            throw new IllegalArgumentException("metadataFile must not be null");
        }
        this.metadataFile = metadataFile;
        this.maxVersions = maxVersions > 0 ? maxVersions : MAX_VERSIONS;
    }

    public static class TreeVersionInfo {
        private final long version;
        private final SegmentLocation rootLocation;
        private final JournalReplayPoint replayPoint;
        private final long mns;
        private final long createdAt;
        private final long leafPageCount;
        private final long indexPageCount;
        private final long totalEntries;
        private final int height;
        private final long totalSize;
        private final long nextLeafPageId;
        private final long nextIndexPageId;

        public TreeVersionInfo(long version, SegmentLocation rootLocation, 
                               JournalReplayPoint replayPoint, long mns,
                               long leafPageCount, long indexPageCount, 
                               long totalEntries, int height, long totalSize,
                               long nextLeafPageId, long nextIndexPageId) {
            this.version = version;
            this.rootLocation = rootLocation;
            this.replayPoint = replayPoint;
            this.mns = mns;
            this.createdAt = System.currentTimeMillis();
            this.leafPageCount = leafPageCount;
            this.indexPageCount = indexPageCount;
            this.totalEntries = totalEntries;
            this.height = height;
            this.totalSize = totalSize;
            this.nextLeafPageId = nextLeafPageId;
            this.nextIndexPageId = nextIndexPageId;
        }

        public long getVersion() { return version; }
        public SegmentLocation getRootLocation() { return rootLocation; }
        public JournalReplayPoint getReplayPoint() { return replayPoint; }
        public long getMns() { return mns; }
        public long getCreatedAt() { return createdAt; }
        public long getLeafPageCount() { return leafPageCount; }
        public long getIndexPageCount() { return indexPageCount; }
        public long getTotalEntries() { return totalEntries; }
        public int getHeight() { return height; }
        public long getTotalSize() { return totalSize; }
        public long getNextLeafPageId() { return nextLeafPageId; }
        public long getNextIndexPageId() { return nextIndexPageId; }
    }

    public void save(TreeVersionInfo info) throws IOException {
        if (info == null) {
            throw new IllegalArgumentException("info must not be null");
        }

        TreeMetadataFile existing = loadExisting();
        List<TreeMetadataEntry> entries = new ArrayList<>();
        
        TreeMetadataEntry newEntry = toProto(info);
        entries.add(newEntry);
        
        if (existing != null && existing.getEntriesList() != null) {
            for (TreeMetadataEntry oldEntry : existing.getEntriesList()) {
                if (entries.size() >= maxVersions) {
                    break;
                }
                if (oldEntry.getVersion() != info.getVersion()) {
                    entries.add(oldEntry);
                }
            }
        }

        TreeMetadataFile newFile = TreeMetadataFile.newBuilder()
                .setMagic(MAGIC)
                .setFormatVersion(FORMAT_VERSION)
                .addAllEntries(entries)
                .build();

        writeAtomic(newFile);
        log.info("Saved tree metadata: version={}, rootLocation={}, entriesCount={}", 
            info.getVersion(), info.getRootLocation(), entries.size());
    }

    public TreeVersionInfo loadLatest() throws IOException {
        TreeMetadataFile file = loadExisting();
        if (file == null || file.getEntriesList().isEmpty()) {
            log.debug("No tree metadata found");
            return null;
        }

        TreeMetadataEntry latest = file.getEntriesList().get(0);
        TreeVersionInfo info = fromProto(latest);
        log.info("Loaded tree metadata: version={}, rootLocation={}, height={}", 
            info.getVersion(), info.getRootLocation(), info.getHeight());
        return info;
    }

    public List<TreeVersionInfo> loadAll() throws IOException {
        TreeMetadataFile file = loadExisting();
        if (file == null || file.getEntriesList().isEmpty()) {
            return Collections.emptyList();
        }

        List<TreeVersionInfo> result = new ArrayList<>();
        for (TreeMetadataEntry entry : file.getEntriesList()) {
            result.add(fromProto(entry));
        }
        return result;
    }

    public boolean exists() {
        return metadataFile.exists() && metadataFile.length() > 0;
    }

    public void delete() throws IOException {
        if (metadataFile.exists()) {
            Files.delete(metadataFile.toPath());
            log.debug("Deleted tree metadata file");
        }
    }

    public boolean removeLatestEntry() throws IOException {
        TreeMetadataFile existing = loadExisting();
        if (existing == null || existing.getEntriesList().isEmpty()) {
            log.warn("No tree metadata entries to remove");
            return false;
        }

        if (existing.getEntriesCount() <= 1) {
            log.warn("Only one entry exists, cannot remove");
            return false;
        }

        TreeMetadataEntry removed = existing.getEntriesList().get(0);
        List<TreeMetadataEntry> remainingEntries = new ArrayList<>(
            existing.getEntriesList().subList(1, existing.getEntriesCount()));

        TreeMetadataFile newFile = TreeMetadataFile.newBuilder()
                .setMagic(MAGIC)
                .setFormatVersion(FORMAT_VERSION)
                .addAllEntries(remainingEntries)
                .build();

        writeAtomic(newFile);
        log.info("Removed tree metadata entry: version={}, remaining entries={}", 
            removed.getVersion(), remainingEntries.size());
        return true;
    }

    public int getEntryCount() throws IOException {
        TreeMetadataFile file = loadExisting();
        return file == null ? 0 : file.getEntriesCount();
    }

    private TreeMetadataFile loadExisting() throws IOException {
        if (!metadataFile.exists() || metadataFile.length() == 0) {
            return null;
        }

        try {
            byte[] data = Files.readAllBytes(metadataFile.toPath());
            TreeMetadataFile file = TreeMetadataFile.parseFrom(data);
            
            if (file.getMagic() != MAGIC) {
                throw new IOException("Invalid tree metadata magic: expected " + MAGIC + 
                    ", got " + file.getMagic());
            }
            
            return file;
        } catch (Exception e) {
            log.error("Failed to parse tree metadata file: {}", e.getMessage());
            throw new IOException("Corrupted tree metadata file: " + metadataFile, e);
        }
    }

    private void writeAtomic(TreeMetadataFile file) throws IOException {
        File parentDir = metadataFile.getParentFile();
        if (parentDir != null && !parentDir.exists()) {
            parentDir.mkdirs();
        }
        
        File tempFile = new File(metadataFile.getParentFile(), metadataFile.getName() + ".tmp");
        
        try (FileOutputStream fos = new FileOutputStream(tempFile)) {
            file.writeTo(fos);
            fos.getFD().sync();
        }
        
        Files.move(tempFile.toPath(), metadataFile.toPath(), StandardCopyOption.REPLACE_EXISTING);
    }

    private TreeMetadataEntry toProto(TreeVersionInfo info) {
        TreeMetadataEntry.Builder builder = TreeMetadataEntry.newBuilder()
                .setVersion(info.getVersion())
                .setMns(info.getMns())
                .setCreatedAt(info.getCreatedAt());

        if (info.getRootLocation() != null) {
            builder.setRootLocation(info.getRootLocation().toProto());
        }

        if (info.getReplayPoint() != null) {
            builder.setReplayPoint(info.getReplayPoint().toProto());
        }

        TreeStats stats = TreeStats.newBuilder()
                .setLeafPageCount(info.getLeafPageCount())
                .setIndexPageCount(info.getIndexPageCount())
                .setTotalEntries(info.getTotalEntries())
                .setHeight(info.getHeight())
                .setTotalSize(info.getTotalSize())
                .build();
        builder.setStats(stats);

        PageIdTracker pageIdTracker = PageIdTracker.newBuilder()
                .setNextIndexPageId(info.getNextIndexPageId())
                .setNextLeafPageId(info.getNextLeafPageId())
                .build();
        builder.setPageIdTracker(pageIdTracker);

        return builder.build();
    }

    private TreeVersionInfo fromProto(TreeMetadataEntry entry) {
        SegmentLocation rootLocation = null;
        if (entry.hasRootLocation()) {
            rootLocation = SegmentLocation.fromProto(entry.getRootLocation());
        }

        JournalReplayPoint replayPoint = null;
        if (entry.hasReplayPoint()) {
            replayPoint = JournalReplayPoint.fromProto(entry.getReplayPoint());
        }

        TreeStats stats = entry.getStats();
        
        long nextLeafPageId = 1L;
        long nextIndexPageId = Long.MIN_VALUE;
        if (entry.hasPageIdTracker()) {
            PageIdTracker tracker = entry.getPageIdTracker();
            nextLeafPageId = tracker.getNextLeafPageId();
            nextIndexPageId = tracker.getNextIndexPageId();
        }
        
        return new TreeVersionInfo(
                entry.getVersion(),
                rootLocation,
                replayPoint,
                entry.getMns(),
                stats.getLeafPageCount(),
                stats.getIndexPageCount(),
                stats.getTotalEntries(),
                stats.getHeight(),
                stats.getTotalSize(),
                nextLeafPageId,
                nextIndexPageId
        );
    }
}
