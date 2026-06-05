package org.hyperkv.lsmplus.api.model;

import com.google.protobuf.InvalidProtocolBufferException;
import org.hyperkv.lsmplus.proto.Common.ChunkStatus;
import org.hyperkv.lsmplus.proto.Common.ChunkType;
import org.hyperkv.lsmplus.proto.Metadata.ChunkMetadata;
import org.junit.jupiter.api.Test;

import static org.junit.jupiter.api.Assertions.*;

class ChunkMetadataProtoTest {

    @Test
    void testChunkMetadataSerialization() throws InvalidProtocolBufferException {
        ChunkMetadata original = ChunkMetadata.newBuilder()
                .setChunkIdMostSig(123456789L)
                .setChunkIdLeastSig(987654321L)
                .setChunkNumber(1L)
                .setChunkType(ChunkType.CHUNK_LEAF)
                .setOwnerIdMostSig(111L)
                .setOwnerIdLeastSig(222L)
                .setNamespaceIdMostSig(333L)
                .setNamespaceIdLeastSig(444L)
                .setStatus(ChunkStatus.OPEN)
                .setCreatedAt(System.currentTimeMillis())
                .setKeepAliveTime(System.currentTimeMillis())
                .setTotalSize(1024 * 1024)
                .setUsedSize(512 * 1024)
                .setOccupancySize(256 * 1024)
                .setPendingGc(0)
                .build();

        byte[] serialized = original.toByteArray();
        ChunkMetadata restored = ChunkMetadata.parseFrom(serialized);

        assertEquals(123456789L, restored.getChunkIdMostSig());
        assertEquals(987654321L, restored.getChunkIdLeastSig());
        assertEquals(1L, restored.getChunkNumber());
        assertEquals(ChunkType.CHUNK_LEAF, restored.getChunkType());
        assertEquals(ChunkStatus.OPEN, restored.getStatus());
        assertEquals(1024 * 1024, restored.getTotalSize());
        assertEquals(512 * 1024, restored.getUsedSize());
        assertEquals(256 * 1024, restored.getOccupancySize());
    }

    @Test
    void testChunkStatusTransitions() throws InvalidProtocolBufferException {
        ChunkMetadata open = ChunkMetadata.newBuilder()
                .setChunkIdMostSig(1L)
                .setChunkIdLeastSig(2L)
                .setStatus(ChunkStatus.OPEN)
                .build();

        byte[] openBytes = open.toByteArray();
        ChunkMetadata restoredOpen = ChunkMetadata.parseFrom(openBytes);
        assertEquals(ChunkStatus.OPEN, restoredOpen.getStatus());

        ChunkMetadata sealed = ChunkMetadata.newBuilder()
                .setChunkIdMostSig(1L)
                .setChunkIdLeastSig(2L)
                .setStatus(ChunkStatus.SEALED)
                .build();

        byte[] sealedBytes = sealed.toByteArray();
        ChunkMetadata restoredSealed = ChunkMetadata.parseFrom(sealedBytes);
        assertEquals(ChunkStatus.SEALED, restoredSealed.getStatus());
    }

    @Test
    void testChunkTypes() throws InvalidProtocolBufferException {
        for (ChunkType type : ChunkType.values()) {
            if (type == ChunkType.UNRECOGNIZED) {
                continue;
            }

            ChunkMetadata chunk = ChunkMetadata.newBuilder()
                    .setChunkIdMostSig(1L)
                    .setChunkIdLeastSig(2L)
                    .setChunkType(type)
                    .build();

            byte[] serialized = chunk.toByteArray();
            ChunkMetadata restored = ChunkMetadata.parseFrom(serialized);
            assertEquals(type, restored.getChunkType());
        }
    }

    @Test
    void testChunkSizeMetrics() throws InvalidProtocolBufferException {
        long totalSize = 64 * 1024 * 1024;
        long usedSize = 48 * 1024 * 1024;
        long occupancySize = 32 * 1024 * 1024;

        ChunkMetadata original = ChunkMetadata.newBuilder()
                .setChunkIdMostSig(1L)
                .setChunkIdLeastSig(2L)
                .setTotalSize(totalSize)
                .setUsedSize(usedSize)
                .setOccupancySize(occupancySize)
                .build();

        byte[] serialized = original.toByteArray();
        ChunkMetadata restored = ChunkMetadata.parseFrom(serialized);

        assertEquals(totalSize, restored.getTotalSize());
        assertEquals(usedSize, restored.getUsedSize());
        assertEquals(occupancySize, restored.getOccupancySize());
    }

    @Test
    void testTimestampPreservation() throws InvalidProtocolBufferException {
        long createdAt = System.currentTimeMillis() - 3600000;
        long keepAliveTime = System.currentTimeMillis();

        ChunkMetadata original = ChunkMetadata.newBuilder()
                .setChunkIdMostSig(1L)
                .setChunkIdLeastSig(2L)
                .setCreatedAt(createdAt)
                .setKeepAliveTime(keepAliveTime)
                .build();

        byte[] serialized = original.toByteArray();
        ChunkMetadata restored = ChunkMetadata.parseFrom(serialized);

        assertEquals(createdAt, restored.getCreatedAt());
        assertEquals(keepAliveTime, restored.getKeepAliveTime());
    }

    @Test
    void testPendingGcFlag() throws InvalidProtocolBufferException {
        ChunkMetadata withGc = ChunkMetadata.newBuilder()
                .setChunkIdMostSig(1L)
                .setChunkIdLeastSig(2L)
                .setPendingGc(1)
                .build();

        byte[] serialized = withGc.toByteArray();
        ChunkMetadata restored = ChunkMetadata.parseFrom(serialized);
        assertEquals(1, restored.getPendingGc());

        ChunkMetadata withoutGc = ChunkMetadata.newBuilder()
                .setChunkIdMostSig(1L)
                .setChunkIdLeastSig(2L)
                .setPendingGc(0)
                .build();

        serialized = withoutGc.toByteArray();
        restored = ChunkMetadata.parseFrom(serialized);
        assertEquals(0, restored.getPendingGc());
    }

    @Test
    void testOwnerAndNamespaceIds() throws InvalidProtocolBufferException {
        ChunkMetadata original = ChunkMetadata.newBuilder()
                .setChunkIdMostSig(1L)
                .setChunkIdLeastSig(2L)
                .setOwnerIdMostSig(111111L)
                .setOwnerIdLeastSig(222222L)
                .setNamespaceIdMostSig(333333L)
                .setNamespaceIdLeastSig(444444L)
                .build();

        byte[] serialized = original.toByteArray();
        ChunkMetadata restored = ChunkMetadata.parseFrom(serialized);

        assertEquals(111111L, restored.getOwnerIdMostSig());
        assertEquals(222222L, restored.getOwnerIdLeastSig());
        assertEquals(333333L, restored.getNamespaceIdMostSig());
        assertEquals(444444L, restored.getNamespaceIdLeastSig());
    }
}
