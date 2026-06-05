package org.hyperkv.lsmplus.storage;

import org.hyperkv.lsmplus.proto.Common.ChunkStatus;
import org.hyperkv.lsmplus.proto.Common.ChunkType;
import org.hyperkv.lsmplus.storage.io.AbstractIO;
import org.hyperkv.lsmplus.storage.io.FileIOFactory;
import org.hyperkv.lsmplus.storage.io.IOFactory;
import org.hyperkv.lsmplus.storage.io.VirtualDataPath;

import java.io.IOException;
import java.util.ArrayList;
import java.util.Collections;
import java.util.List;
import java.util.UUID;

public class ChunkMetadataFile {

    private static final int MAGIC = 0x434D5444;
    private static final int FORMAT_VERSION = 1;

    private final VirtualDataPath path;
    private final UUID ownerId;
    private final UUID namespaceId;
    private final IOFactory ioFactory;
    private final List<ChunkEntry> entries;

    public ChunkMetadataFile(VirtualDataPath path, UUID ownerId, UUID namespaceId, IOFactory ioFactory) {
        if (path == null) {
            throw new IllegalArgumentException("path must not be null");
        }
        if (ownerId == null) {
            throw new IllegalArgumentException("ownerId must not be null");
        }
        if (namespaceId == null) {
            throw new IllegalArgumentException("namespaceId must not be null");
        }
        if (ioFactory == null) {
            throw new IllegalArgumentException("ioFactory must not be null");
        }
        this.path = path;
        this.ownerId = ownerId;
        this.namespaceId = namespaceId;
        this.ioFactory = ioFactory;
        this.entries = new ArrayList<>();
    }

    public void addEntry(ChunkEntry entry) {
        if (entry == null) {
            throw new IllegalArgumentException("entry must not be null");
        }
        entries.add(entry);
    }

    public void clearEntries() {
        entries.clear();
    }

    public List<ChunkEntry> getEntries() {
        return Collections.unmodifiableList(entries);
    }

    public void persist() throws IOException {
        org.hyperkv.lsmplus.proto.Metadata.ChunkMetadataFile.Builder builder = 
                org.hyperkv.lsmplus.proto.Metadata.ChunkMetadataFile.newBuilder()
                .setMagic(MAGIC)
                .setFormatVersion(FORMAT_VERSION);

        for (ChunkEntry entry : entries) {
            builder.addChunks(entry.toProto());
        }

        org.hyperkv.lsmplus.proto.Metadata.ChunkMetadataFile proto = builder.build();

        String pathStr = path.getPath();
        VirtualDataPath tmpPath = VirtualDataPath.file(pathStr + ".tmp");
        
        AbstractIO io = ioFactory.createIO();
        io.open(tmpPath, AbstractIO.OpenMode.WRITE);
        io.write(0, proto.toByteArray());
        io.sync();
        io.close();

        ioFactory.delete(path);
        ioFactory.createDirectories(path);
        
        java.io.File tmpFile = new java.io.File(tmpPath.getPath());
        java.io.File targetFile = new java.io.File(pathStr);
        if (!tmpFile.renameTo(targetFile)) {
            throw new IOException("Failed to rename " + tmpPath + " to " + path);
        }
    }

    public static ChunkMetadataFile load(VirtualDataPath path, UUID ownerId, UUID namespaceId) throws IOException {
        return load(path, ownerId, namespaceId, FileIOFactory.INSTANCE);
    }

    public static ChunkMetadataFile load(VirtualDataPath path, UUID ownerId, UUID namespaceId, IOFactory ioFactory) throws IOException {
        ChunkMetadataFile metadataFile = new ChunkMetadataFile(path, ownerId, namespaceId, ioFactory);

        if (!ioFactory.exists(path)) {
            return metadataFile;
        }

        AbstractIO io = ioFactory.createIO();
        io.open(path, AbstractIO.OpenMode.READ);
        byte[] data = io.read(0, (int) io.length());
        io.close();

        org.hyperkv.lsmplus.proto.Metadata.ChunkMetadataFile proto = 
            org.hyperkv.lsmplus.proto.Metadata.ChunkMetadataFile.parseFrom(data);

        if (proto.getMagic() != MAGIC) {
            throw new IOException("Invalid magic in chunk metadata file: " + path);
        }
        if (proto.getFormatVersion() != FORMAT_VERSION) {
            throw new IOException("Unsupported format version: " + proto.getFormatVersion());
        }

        for (org.hyperkv.lsmplus.proto.Metadata.ChunkMetadata chunkProto : proto.getChunksList()) {
            metadataFile.entries.add(ChunkEntry.fromProto(chunkProto));
        }

        return metadataFile;
    }

    public static final class ChunkEntry {
        private final UUID chunkId;
        private final long chunkNumber;
        private final ChunkType chunkType;
        private final UUID ownerId;
        private final UUID namespaceId;
        private final ChunkStatus status;
        private final long createdAt;
        private final long keepAliveTime;
        private final long totalSize;
        private final long usedSize;
        private final long occupancySize;

        public ChunkEntry(UUID chunkId, long chunkNumber, ChunkType chunkType,
                          UUID ownerId, UUID namespaceId, ChunkStatus status,
                          long createdAt, long keepAliveTime, long totalSize,
                          long usedSize, long occupancySize) {
            this.chunkId = chunkId;
            this.chunkNumber = chunkNumber;
            this.chunkType = chunkType;
            this.ownerId = ownerId;
            this.namespaceId = namespaceId;
            this.status = status;
            this.createdAt = createdAt;
            this.keepAliveTime = keepAliveTime;
            this.totalSize = totalSize;
            this.usedSize = usedSize;
            this.occupancySize = occupancySize;
        }

        public UUID getChunkId() { return chunkId; }
        public long getChunkNumber() { return chunkNumber; }
        public ChunkType getChunkType() { return chunkType; }
        public UUID getOwnerId() { return ownerId; }
        public UUID getNamespaceId() { return namespaceId; }
        public ChunkStatus getStatus() { return status; }
        public long getCreatedAt() { return createdAt; }
        public long getKeepAliveTime() { return keepAliveTime; }
        public long getTotalSize() { return totalSize; }
        public long getUsedSize() { return usedSize; }
        public long getOccupancySize() { return occupancySize; }

        public org.hyperkv.lsmplus.proto.Metadata.ChunkMetadata toProto() {
            return org.hyperkv.lsmplus.proto.Metadata.ChunkMetadata.newBuilder()
                    .setChunkIdMostSig(chunkId.getMostSignificantBits())
                    .setChunkIdLeastSig(chunkId.getLeastSignificantBits())
                    .setChunkNumber(chunkNumber)
                    .setChunkType(chunkType)
                    .setOwnerIdMostSig(ownerId.getMostSignificantBits())
                    .setOwnerIdLeastSig(ownerId.getLeastSignificantBits())
                    .setNamespaceIdMostSig(namespaceId.getMostSignificantBits())
                    .setNamespaceIdLeastSig(namespaceId.getLeastSignificantBits())
                    .setStatus(status)
                    .setCreatedAt(createdAt)
                    .setKeepAliveTime(keepAliveTime)
                    .setTotalSize(totalSize)
                    .setUsedSize(usedSize)
                    .setOccupancySize(occupancySize)
                    .build();
        }

        public static ChunkEntry fromProto(org.hyperkv.lsmplus.proto.Metadata.ChunkMetadata proto) {
            return new ChunkEntry(
                    new UUID(proto.getChunkIdMostSig(), proto.getChunkIdLeastSig()),
                    proto.getChunkNumber(),
                    proto.getChunkType(),
                    new UUID(proto.getOwnerIdMostSig(), proto.getOwnerIdLeastSig()),
                    new UUID(proto.getNamespaceIdMostSig(), proto.getNamespaceIdLeastSig()),
                    proto.getStatus(),
                    proto.getCreatedAt(),
                    proto.getKeepAliveTime(),
                    proto.getTotalSize(),
                    proto.getUsedSize(),
                    proto.getOccupancySize()
            );
        }
    }
}
