package org.hyperkv.lsmplus.storage;

import org.hyperkv.lsmplus.proto.Common.ChunkStatus;
import org.hyperkv.lsmplus.proto.Common.ChunkType;
import org.hyperkv.lsmplus.storage.io.IOFactory;
import org.hyperkv.lsmplus.storage.io.VirtualDataPath;

import java.util.UUID;

public final class ChunkInfo {

    private final UUID chunkId;
    private final long chunkNumber;
    private final ChunkType chunkType;
    private final UUID ownerId;
    private final UUID namespaceId;
    private volatile ChunkStatus status;
    private volatile long createdAt;
    private volatile long keepAliveTime;
    private volatile long totalSize;
    private volatile long usedSize;
    private volatile long occupancySize;
    private final VirtualDataPath path;

    public ChunkInfo(UUID chunkId, long chunkNumber, ChunkType chunkType,
                     UUID ownerId, UUID namespaceId, ChunkStatus status,
                     long createdAt, long keepAliveTime, long totalSize,
                     long usedSize, long occupancySize, VirtualDataPath path) {
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
        this.path = path;
    }

    public static ChunkInfo fromEntry(ChunkMetadataFile.ChunkEntry entry, String basePath, IOFactory ioFactory) {
        String chunkTypeStr = entry.getChunkType() == ChunkType.CHUNK_JOURNAL ? "journal" : "data";
        VirtualDataPath path = ioFactory.createChunkPath(basePath, chunkTypeStr, entry.getChunkId().toString());
        return new ChunkInfo(
            entry.getChunkId(),
            entry.getChunkNumber(),
            entry.getChunkType(),
            entry.getOwnerId(),
            entry.getNamespaceId(),
            entry.getStatus(),
            entry.getCreatedAt(),
            entry.getKeepAliveTime(),
            entry.getTotalSize(),
            entry.getUsedSize(),
            entry.getOccupancySize(),
            path
        );
    }

    public ChunkMetadataFile.ChunkEntry toEntry() {
        return new ChunkMetadataFile.ChunkEntry(
            chunkId, chunkNumber, chunkType, ownerId, namespaceId,
            status, createdAt, keepAliveTime, totalSize, usedSize, occupancySize
        );
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
    public VirtualDataPath getPath() { return path; }

    public void setStatus(ChunkStatus status) { this.status = status; }
    public void setCreatedAt(long createdAt) { this.createdAt = createdAt; }
    public void setKeepAliveTime(long keepAliveTime) { this.keepAliveTime = keepAliveTime; }
    public void setTotalSize(long totalSize) { this.totalSize = totalSize; }
    public void setUsedSize(long usedSize) { this.usedSize = usedSize; }
    public void setOccupancySize(long occupancySize) { this.occupancySize = occupancySize; }

    public boolean isKeepAliveExpired() {
        return System.currentTimeMillis() > keepAliveTime;
    }

    public double getOccupancyRatio() {
        if (totalSize <= 0) return 0.0;
        return (double) occupancySize / totalSize;
    }

    @Override
    public String toString() {
        return "ChunkInfo{chunkId=" + chunkId +
               ", type=" + chunkType +
               ", status=" + status +
               ", number=" + chunkNumber +
               ", usedSize=" + usedSize + '}';
    }
}
