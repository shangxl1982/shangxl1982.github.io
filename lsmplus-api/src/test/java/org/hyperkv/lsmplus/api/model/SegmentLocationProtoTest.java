package org.hyperkv.lsmplus.api.model;

import com.google.protobuf.InvalidProtocolBufferException;
import org.hyperkv.lsmplus.proto.Keyvalue.SegmentLocationProto;
import org.junit.jupiter.api.Test;

import static org.junit.jupiter.api.Assertions.*;

class SegmentLocationProtoTest {

    @Test
    void testSegmentLocationSerialization() throws InvalidProtocolBufferException {
        SegmentLocationProto original = SegmentLocationProto.newBuilder()
                .setChunkIdMostSig(123456789L)
                .setChunkIdLeastSig(987654321L)
                .setOffset(1024L)
                .setLength(512)
                .build();

        byte[] serialized = original.toByteArray();
        SegmentLocationProto restored = SegmentLocationProto.parseFrom(serialized);

        assertEquals(123456789L, restored.getChunkIdMostSig());
        assertEquals(987654321L, restored.getChunkIdLeastSig());
        assertEquals(1024L, restored.getOffset());
        assertEquals(512, restored.getLength());
    }

    @Test
    void testNegativeOffsetForVirtualLocations() throws InvalidProtocolBufferException {
        SegmentLocationProto virtual = SegmentLocationProto.newBuilder()
                .setChunkIdMostSig(1L)
                .setChunkIdLeastSig(2L)
                .setOffset(-1L)
                .setLength(0)
                .build();

        byte[] serialized = virtual.toByteArray();
        SegmentLocationProto restored = SegmentLocationProto.parseFrom(serialized);

        assertEquals(-1L, restored.getOffset());
        assertEquals(0, restored.getLength());
    }

    @Test
    void testLargeOffsetAndLength() throws InvalidProtocolBufferException {
        long largeOffset = Long.MAX_VALUE;
        int largeLength = Integer.MAX_VALUE;

        SegmentLocationProto original = SegmentLocationProto.newBuilder()
                .setChunkIdMostSig(1L)
                .setChunkIdLeastSig(2L)
                .setOffset(largeOffset)
                .setLength(largeLength)
                .build();

        byte[] serialized = original.toByteArray();
        SegmentLocationProto restored = SegmentLocationProto.parseFrom(serialized);

        assertEquals(largeOffset, restored.getOffset());
        assertEquals(largeLength, restored.getLength());
    }

    @Test
    void testZeroOffsetAndLength() throws InvalidProtocolBufferException {
        SegmentLocationProto original = SegmentLocationProto.newBuilder()
                .setChunkIdMostSig(1L)
                .setChunkIdLeastSig(2L)
                .setOffset(0L)
                .setLength(0)
                .build();

        byte[] serialized = original.toByteArray();
        SegmentLocationProto restored = SegmentLocationProto.parseFrom(serialized);

        assertEquals(0L, restored.getOffset());
        assertEquals(0, restored.getLength());
    }

    @Test
    void testChunkIdPreservation() throws InvalidProtocolBufferException {
        long mostSig = 1234567890123456789L;
        long leastSig = 987654321098765432L;

        SegmentLocationProto original = SegmentLocationProto.newBuilder()
                .setChunkIdMostSig(mostSig)
                .setChunkIdLeastSig(leastSig)
                .setOffset(100L)
                .setLength(200)
                .build();

        byte[] serialized = original.toByteArray();
        SegmentLocationProto restored = SegmentLocationProto.parseFrom(serialized);

        assertEquals(mostSig, restored.getChunkIdMostSig());
        assertEquals(leastSig, restored.getChunkIdLeastSig());
    }

    @Test
    void testSegmentLocationEquality() throws InvalidProtocolBufferException {
        SegmentLocationProto loc1 = SegmentLocationProto.newBuilder()
                .setChunkIdMostSig(1L)
                .setChunkIdLeastSig(2L)
                .setOffset(100L)
                .setLength(200)
                .build();

        SegmentLocationProto loc2 = SegmentLocationProto.newBuilder()
                .setChunkIdMostSig(1L)
                .setChunkIdLeastSig(2L)
                .setOffset(100L)
                .setLength(200)
                .build();

        assertEquals(loc1, loc2);
        assertEquals(loc1.hashCode(), loc2.hashCode());
    }

    @Test
    void testSegmentLocationInequality() throws InvalidProtocolBufferException {
        SegmentLocationProto loc1 = SegmentLocationProto.newBuilder()
                .setChunkIdMostSig(1L)
                .setChunkIdLeastSig(2L)
                .setOffset(100L)
                .setLength(200)
                .build();

        SegmentLocationProto loc2 = SegmentLocationProto.newBuilder()
                .setChunkIdMostSig(3L)
                .setChunkIdLeastSig(4L)
                .setOffset(100L)
                .setLength(200)
                .build();

        assertNotEquals(loc1, loc2);
    }

    @Test
    void testSerializedSize() throws InvalidProtocolBufferException {
        SegmentLocationProto location = SegmentLocationProto.newBuilder()
                .setChunkIdMostSig(1L)
                .setChunkIdLeastSig(2L)
                .setOffset(100L)
                .setLength(200)
                .build();

        byte[] serialized = location.toByteArray();
        assertTrue(serialized.length > 0);
        assertTrue(serialized.length < 100);
    }
}
