package org.hyperkv.lsmplus.api.model;

import com.google.protobuf.InvalidProtocolBufferException;
import org.hyperkv.lsmplus.proto.Common.OperationType;
import org.hyperkv.lsmplus.proto.Common.ValueType;
import org.hyperkv.lsmplus.proto.Journal.JournalEntryProto;
import org.hyperkv.lsmplus.proto.Journal.JournalReplayPointProto;
import org.hyperkv.lsmplus.proto.Keyvalue.KeyValuePairProto;
import org.hyperkv.lsmplus.proto.Keyvalue.KeyProto;
import org.hyperkv.lsmplus.proto.Keyvalue.ValueProto;
import org.junit.jupiter.api.Test;

import static org.junit.jupiter.api.Assertions.*;

class JournalEntryProtoTest {

    @Test
    void testPutOperation() throws InvalidProtocolBufferException {
        IndexKey key = IndexKey.orderedBytes("user:12345".getBytes());
        IndexValue value = IndexValue.normal("data".getBytes());

        KeyValuePairProto entry = KeyValuePairProto.newBuilder()
                .setKey(key.toProto())
                .setValue(value.toProto())
                .build();

        JournalEntryProto original = JournalEntryProto.newBuilder()
                .setOperationType(OperationType.PUT)
                .setTimestamp(System.currentTimeMillis())
                .setSequenceNumber(12345L)
                .addEntries(entry)
                .build();

        byte[] serialized = original.toByteArray();
        JournalEntryProto restored = JournalEntryProto.parseFrom(serialized);

        assertEquals(OperationType.PUT, restored.getOperationType());
        assertEquals(12345L, restored.getSequenceNumber());
        assertEquals(1, restored.getEntriesCount());
        assertEquals(key, IndexKey.fromProto(restored.getEntries(0).getKey()));
        assertEquals(value, IndexValue.fromProto(restored.getEntries(0).getValue()));
    }

    @Test
    void testDeleteOperation() throws InvalidProtocolBufferException {
        IndexKey key = IndexKey.orderedBytes("user:12345".getBytes());
        IndexValue tombstone = IndexValue.tombstone();

        KeyValuePairProto entry = KeyValuePairProto.newBuilder()
                .setKey(key.toProto())
                .setValue(tombstone.toProto())
                .build();

        JournalEntryProto original = JournalEntryProto.newBuilder()
                .setOperationType(OperationType.DELETE)
                .setTimestamp(System.currentTimeMillis())
                .setSequenceNumber(12346L)
                .addEntries(entry)
                .build();

        byte[] serialized = original.toByteArray();
        JournalEntryProto restored = JournalEntryProto.parseFrom(serialized);

        assertEquals(OperationType.DELETE, restored.getOperationType());
        assertEquals(1, restored.getEntriesCount());
        assertTrue(IndexValue.fromProto(restored.getEntries(0).getValue()).isTombstone());
    }

    @Test
    void testBatchOperation() throws InvalidProtocolBufferException {
        IndexKey key1 = IndexKey.orderedBytes("key1".getBytes());
        IndexValue value1 = IndexValue.normal("value1".getBytes());
        IndexKey key2 = IndexKey.orderedBytes("key2".getBytes());
        IndexValue value2 = IndexValue.tombstone();

        KeyValuePairProto entry1 = KeyValuePairProto.newBuilder()
                .setKey(key1.toProto())
                .setValue(value1.toProto())
                .build();

        KeyValuePairProto entry2 = KeyValuePairProto.newBuilder()
                .setKey(key2.toProto())
                .setValue(value2.toProto())
                .build();

        JournalEntryProto original = JournalEntryProto.newBuilder()
                .setOperationType(OperationType.BATCH)
                .setTimestamp(System.currentTimeMillis())
                .setSequenceNumber(12347L)
                .addEntries(entry1)
                .addEntries(entry2)
                .build();

        byte[] serialized = original.toByteArray();
        JournalEntryProto restored = JournalEntryProto.parseFrom(serialized);

        assertEquals(OperationType.BATCH, restored.getOperationType());
        assertEquals(2, restored.getEntriesCount());
        assertEquals(key1, IndexKey.fromProto(restored.getEntries(0).getKey()));
        assertEquals(key2, IndexKey.fromProto(restored.getEntries(1).getKey()));
    }

    @Test
    void testJournalReplayPoint() throws InvalidProtocolBufferException {
        JournalReplayPointProto original = JournalReplayPointProto.newBuilder()
                .setRegionMajor(1L)
                .setRegionMinor(2L)
                .setOffset(1024)
                .build();

        byte[] serialized = original.toByteArray();
        JournalReplayPointProto restored = JournalReplayPointProto.parseFrom(serialized);

        assertEquals(1L, restored.getRegionMajor());
        assertEquals(2L, restored.getRegionMinor());
        assertEquals(1024, restored.getOffset());
    }

    @Test
    void testTimestampPreservation() throws InvalidProtocolBufferException {
        long timestamp = System.currentTimeMillis();
        
        JournalEntryProto original = JournalEntryProto.newBuilder()
                .setOperationType(OperationType.PUT)
                .setTimestamp(timestamp)
                .setSequenceNumber(1L)
                .build();

        byte[] serialized = original.toByteArray();
        JournalEntryProto restored = JournalEntryProto.parseFrom(serialized);

        assertEquals(timestamp, restored.getTimestamp());
    }

    @Test
    void testSequenceNumberPreservation() throws InvalidProtocolBufferException {
        long sequenceNumber = Long.MAX_VALUE;
        
        JournalEntryProto original = JournalEntryProto.newBuilder()
                .setOperationType(OperationType.PUT)
                .setTimestamp(System.currentTimeMillis())
                .setSequenceNumber(sequenceNumber)
                .build();

        byte[] serialized = original.toByteArray();
        JournalEntryProto restored = JournalEntryProto.parseFrom(serialized);

        assertEquals(sequenceNumber, restored.getSequenceNumber());
    }
}
