package org.hyperkv.lsmplus.storage;

import org.hyperkv.lsmplus.proto.Common.ChunkType;

import java.nio.ByteBuffer;
import java.nio.ByteOrder;
import java.util.UUID;

public final class ChunkHeader {

    public static final int HEADER_SIZE = 4096;
    public static final int CHUNK_ID_OFFSET = 0;
    public static final int CHUNK_TYPE_OFFSET = 16;
    public static final int OWNER_ID_OFFSET = 20;
    public static final int NAMESPACE_ID_OFFSET = 36;
    public static final int VALID_DATA_SIZE_OFFSET = 52;
    public static final int RESERVED_OFFSET = 56;

    private final UUID chunkId;
    private final ChunkType chunkType;
    private final UUID ownerId;
    private final UUID namespaceId;
    private int validDataSize;

    public ChunkHeader(UUID chunkId, ChunkType chunkType, UUID ownerId, UUID namespaceId) {
        if (chunkId == null) {
            throw new IllegalArgumentException("chunkId must not be null");
        }
        if (chunkType == null) {
            throw new IllegalArgumentException("chunkType must not be null");
        }
        if (ownerId == null) {
            throw new IllegalArgumentException("ownerId must not be null");
        }
        if (namespaceId == null) {
            throw new IllegalArgumentException("namespaceId must not be null");
        }
        this.chunkId = chunkId;
        this.chunkType = chunkType;
        this.ownerId = ownerId;
        this.namespaceId = namespaceId;
        this.validDataSize = 0;
    }

    private ChunkHeader(UUID chunkId, ChunkType chunkType, UUID ownerId,
                        UUID namespaceId, int validDataSize) {
        this.chunkId = chunkId;
        this.chunkType = chunkType;
        this.ownerId = ownerId;
        this.namespaceId = namespaceId;
        this.validDataSize = validDataSize;
    }

    public UUID getChunkId() {
        return chunkId;
    }

    public ChunkType getChunkType() {
        return chunkType;
    }

    public UUID getOwnerId() {
        return ownerId;
    }

    public UUID getNamespaceId() {
        return namespaceId;
    }

    public int getValidDataSize() {
        return validDataSize;
    }

    public void setValidDataSize(int size) {
        if (size < 0) {
            throw new IllegalArgumentException("validDataSize must be non-negative: " + size);
        }
        this.validDataSize = size;
    }

    public byte[] toByteArray() {
        ByteBuffer buffer = ByteBuffer.allocate(HEADER_SIZE)
                .order(ByteOrder.BIG_ENDIAN);

        buffer.putLong(chunkId.getMostSignificantBits());
        buffer.putLong(chunkId.getLeastSignificantBits());

        buffer.putInt(chunkType.getNumber());

        buffer.putLong(ownerId.getMostSignificantBits());
        buffer.putLong(ownerId.getLeastSignificantBits());

        buffer.putLong(namespaceId.getMostSignificantBits());
        buffer.putLong(namespaceId.getLeastSignificantBits());

        buffer.putInt(validDataSize);

        buffer.put(new byte[HEADER_SIZE - RESERVED_OFFSET]);

        return buffer.array();
    }

    public static ChunkHeader fromByteArray(byte[] data) {
        if (data == null || data.length < HEADER_SIZE) {
            throw new IllegalArgumentException(
                    "data must be at least " + HEADER_SIZE + " bytes, got: " +
                    (data == null ? "null" : data.length));
        }

        ByteBuffer buffer = ByteBuffer.wrap(data, 0, HEADER_SIZE)
                .order(ByteOrder.BIG_ENDIAN);

        long chunkIdMost = buffer.getLong();
        long chunkIdLeast = buffer.getLong();
        UUID chunkId = new UUID(chunkIdMost, chunkIdLeast);

        int chunkTypeNum = buffer.getInt();
        ChunkType chunkType = ChunkType.forNumber(chunkTypeNum);
        if (chunkType == null) {
            throw new IllegalArgumentException("Unknown chunk type: " + chunkTypeNum);
        }

        long ownerIdMost = buffer.getLong();
        long ownerIdLeast = buffer.getLong();
        UUID ownerId = new UUID(ownerIdMost, ownerIdLeast);

        long nsIdMost = buffer.getLong();
        long nsIdLeast = buffer.getLong();
        UUID namespaceId = new UUID(nsIdMost, nsIdLeast);

        int validDataSize = buffer.getInt();

        return new ChunkHeader(chunkId, chunkType, ownerId, namespaceId, validDataSize);
    }

    @Override
    public boolean equals(Object o) {
        if (this == o) return true;
        if (!(o instanceof ChunkHeader other)) return false;
        return chunkId.equals(other.chunkId) &&
               chunkType == other.chunkType &&
               ownerId.equals(other.ownerId) &&
               namespaceId.equals(other.namespaceId) &&
               validDataSize == other.validDataSize;
    }

    @Override
    public int hashCode() {
        int result = chunkId.hashCode();
        result = 31 * result + chunkType.hashCode();
        result = 31 * result + ownerId.hashCode();
        result = 31 * result + namespaceId.hashCode();
        result = 31 * result + validDataSize;
        return result;
    }

    @Override
    public String toString() {
        return "ChunkHeader{chunkId=" + chunkId +
               ", chunkType=" + chunkType +
               ", ownerId=" + ownerId +
               ", namespaceId=" + namespaceId +
               ", validDataSize=" + validDataSize + '}';
    }
}