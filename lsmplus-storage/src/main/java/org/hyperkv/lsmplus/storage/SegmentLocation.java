package org.hyperkv.lsmplus.storage;

import org.hyperkv.lsmplus.proto.Keyvalue.SegmentLocationProto;

import java.nio.ByteBuffer;
import java.util.UUID;

/**
 * Represents a location in a segment (chunk).
 * 
 * <p>Used for both real disk locations and virtual locations:
 * <ul>
 *   <li>Real location: chunkId = actual chunk UUID, offset = byte offset, length = data length</li>
 *   <li>Virtual location: chunkId = 0-0-0-0..., offset = pageId (can be negative), length = 0</li>
 * </ul>
 * 
 * <p>Implements Comparable for use in sorted maps and efficient grouping by location.
 * Comparison order: chunkId (most significant) → offset → length (least significant).
 */
public final class SegmentLocation implements Comparable<SegmentLocation> {

    public static final int FIXED_SIZE = 28;

    private final UUID chunkId;
    private final long offset;
    private final int length;

    public SegmentLocation(UUID chunkId, long offset, int length) {
        if (chunkId == null) {
            throw new IllegalArgumentException("chunkId must not be null");
        }
        if (length < 0) {
            throw new IllegalArgumentException("length must be non-negative: " + length);
        }
        this.chunkId = chunkId;
        this.offset = offset;
        this.length = length;
    }

    public UUID getChunkId() {
        return chunkId;
    }

    public long getOffset() {
        return offset;
    }

    public int getLength() {
        return length;
    }

    public SegmentLocationProto toProto() {
        return SegmentLocationProto.newBuilder()
                .setChunkIdMostSig(chunkId.getMostSignificantBits())
                .setChunkIdLeastSig(chunkId.getLeastSignificantBits())
                .setOffset(offset)
                .setLength(length)
                .build();
    }

    public static SegmentLocation fromProto(SegmentLocationProto proto) {
        UUID chunkId = new UUID(
                proto.getChunkIdMostSig(),
                proto.getChunkIdLeastSig()
        );
        return new SegmentLocation(chunkId, proto.getOffset(), proto.getLength());
    }

    public byte[] toBytes() {
        ByteBuffer buffer = ByteBuffer.allocate(FIXED_SIZE);
        buffer.putLong(chunkId.getMostSignificantBits());
        buffer.putLong(chunkId.getLeastSignificantBits());
        buffer.putLong(offset);
        buffer.putInt(length);
        return buffer.array();
    }

    public static SegmentLocation fromBytes(byte[] bytes) {
        if (bytes == null || bytes.length != FIXED_SIZE) {
            throw new IllegalArgumentException(
                    "bytes must be exactly " + FIXED_SIZE + " bytes, got: " +
                    (bytes == null ? "null" : bytes.length));
        }
        ByteBuffer buffer = ByteBuffer.wrap(bytes);
        long mostSig = buffer.getLong();
        long leastSig = buffer.getLong();
        long offset = buffer.getLong();
        int length = buffer.getInt();
        return new SegmentLocation(new UUID(mostSig, leastSig), offset, length);
    }

    @Override
    public boolean equals(Object o) {
        if (this == o) return true;
        if (!(o instanceof SegmentLocation other)) return false;
        return chunkId.equals(other.chunkId) &&
               offset == other.offset &&
               length == other.length;
    }

    @Override
    public int hashCode() {
        int result = chunkId.hashCode();
        result = 31 * result + Long.hashCode(offset);
        result = 31 * result + length;
        return result;
    }

    @Override
    public String toString() {
        return "SegmentLocation{chunkId=" + chunkId +
               ", offset=" + offset + ", length=" + length + '}';
    }
    
    @Override
    public int compareTo(SegmentLocation other) {
        if (other == null) {
            return 1;
        }
        
        int chunkCompare = this.chunkId.compareTo(other.chunkId);
        if (chunkCompare != 0) {
            return chunkCompare;
        }
        
        int offsetCompare = Long.compare(this.offset, other.offset);
        if (offsetCompare != 0) {
            return offsetCompare;
        }
        
        return Integer.compare(this.length, other.length);
    }
}
