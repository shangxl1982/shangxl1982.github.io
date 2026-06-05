package org.hyperkv.lsmplus.journal;

import org.hyperkv.lsmplus.proto.Metadata.JournalRegionEntry;
import org.hyperkv.lsmplus.proto.Metadata.JournalRegionIndex;

import java.io.File;
import java.io.FileInputStream;
import java.io.FileOutputStream;
import java.io.IOException;
import java.nio.file.Files;
import java.nio.file.StandardCopyOption;
import java.util.ArrayList;
import java.util.Collections;
import java.util.List;
import java.util.UUID;

public class JournalRegionIndexFile {

    private static final int MAGIC = 0x4A524944;
    private static final int FORMAT_VERSION = 1;

    private final File file;
    private final UUID instanceId;
    private final List<RegionEntry> entries;

    public JournalRegionIndexFile(File file, UUID instanceId) {
        if (file == null) {
            throw new IllegalArgumentException("file must not be null");
        }
        if (instanceId == null) {
            throw new IllegalArgumentException("instanceId must not be null");
        }
        this.file = file;
        this.instanceId = instanceId;
        this.entries = new ArrayList<>();
    }

    public void addEntry(RegionEntry entry) {
        if (entry == null) {
            throw new IllegalArgumentException("entry must not be null");
        }
        entries.add(entry);
    }

    public void clearEntries() {
        entries.clear();
    }

    public List<RegionEntry> getEntries() {
        return Collections.unmodifiableList(entries);
    }

    public void persist() throws IOException {
        JournalRegionIndex.Builder builder = JournalRegionIndex.newBuilder()
                .setMagic(MAGIC)
                .setFormatVersion(FORMAT_VERSION)
                .setInstanceIdMostSig(instanceId.getMostSignificantBits())
                .setInstanceIdLeastSig(instanceId.getLeastSignificantBits());

        for (RegionEntry entry : entries) {
            builder.addEntries(entry.toProto());
        }

        JournalRegionIndex index = builder.build();

        File tmpFile = new File(file.getParent(), file.getName() + ".tmp");
        try (FileOutputStream fos = new FileOutputStream(tmpFile)) {
            index.writeTo(fos);
            fos.getFD().sync();
        }

        Files.move(tmpFile.toPath(), file.toPath(), StandardCopyOption.REPLACE_EXISTING);
    }

    public static JournalRegionIndexFile load(File file, UUID instanceId) throws IOException {
        if (!file.exists()) {
            return new JournalRegionIndexFile(file, instanceId);
        }

        JournalRegionIndex proto;
        try (FileInputStream fis = new FileInputStream(file)) {
            proto = JournalRegionIndex.parseFrom(fis);
        }

        if (proto.getMagic() != MAGIC) {
            throw new IOException("Invalid magic in region index file: " + file);
        }
        if (proto.getFormatVersion() != FORMAT_VERSION) {
            throw new IOException("Unsupported format version: " + proto.getFormatVersion());
        }

        UUID persistedInstanceId = new UUID(proto.getInstanceIdMostSig(), proto.getInstanceIdLeastSig());

        JournalRegionIndexFile indexFile = new JournalRegionIndexFile(file, persistedInstanceId);
        for (JournalRegionEntry entryProto : proto.getEntriesList()) {
            indexFile.entries.add(RegionEntry.fromProto(entryProto));
        }

        return indexFile;
    }

    public record RegionEntry(long regionMajor, long regionMinor, UUID chunkId, int offset, int length,
                              long createdAt) {
        public RegionEntry {
            if (chunkId == null) {
                throw new IllegalArgumentException("chunkId must not be null");
            }
        }

        public JournalRegionEntry toProto() {
            return JournalRegionEntry.newBuilder()
                    .setRegionMajor(regionMajor)
                    .setRegionMinor(regionMinor)
                    .setChunkIdMostSig(chunkId.getMostSignificantBits())
                    .setChunkIdLeastSig(chunkId.getLeastSignificantBits())
                    .setOffset(offset)
                    .setLength(length)
                    .setCreatedAt(createdAt)
                    .build();
        }

        public static RegionEntry fromProto(JournalRegionEntry proto) {
            return new RegionEntry(
                    proto.getRegionMajor(),
                    proto.getRegionMinor(),
                    new UUID(proto.getChunkIdMostSig(), proto.getChunkIdLeastSig()),
                    proto.getOffset(),
                    proto.getLength(),
                    proto.getCreatedAt()
            );
        }
    }
}