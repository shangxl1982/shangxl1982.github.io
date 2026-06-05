package org.hyperkv.lsmplus.journal;

import org.hyperkv.lsmplus.api.model.IndexKey;
import org.hyperkv.lsmplus.api.model.IndexValue;
import org.hyperkv.lsmplus.proto.Common.OperationType;
import org.hyperkv.lsmplus.proto.Journal.JournalEntryProto;
import org.hyperkv.lsmplus.proto.Keyvalue.KeyValuePairProto;

import java.util.ArrayList;
import java.util.List;

public final class JournalEntry {

    private final OperationType operationType;
    private final long timestamp;
    private final long sequenceNumber;
    private final List<KeyValuePair> entries;

    public JournalEntry(OperationType operationType, long timestamp, long sequenceNumber, List<KeyValuePair> entries) {
        if (operationType == null) {
            throw new IllegalArgumentException("operationType must not be null");
        }
        if (entries == null || entries.isEmpty()) {
            throw new IllegalArgumentException("entries must not be null or empty");
        }
        this.operationType = operationType;
        this.timestamp = timestamp;
        this.sequenceNumber = sequenceNumber;
        this.entries = List.copyOf(entries);
    }

    public static JournalEntry put(IndexKey key, IndexValue value, long sequenceNumber) {
        List<KeyValuePair> entries = List.of(new KeyValuePair(key, value));
        return new JournalEntry(OperationType.PUT, System.currentTimeMillis(), sequenceNumber, entries);
    }

    public static JournalEntry delete(IndexKey key, long sequenceNumber) {
        List<KeyValuePair> entries = List.of(new KeyValuePair(key, IndexValue.tombstone()));
        return new JournalEntry(OperationType.DELETE, System.currentTimeMillis(), sequenceNumber, entries);
    }

    public static JournalEntry batch(List<KeyValuePair> operations, long sequenceNumber) {
        return new JournalEntry(OperationType.BATCH, System.currentTimeMillis(), sequenceNumber, operations);
    }

    public OperationType getOperationType() {
        return operationType;
    }

    public long getTimestamp() {
        return timestamp;
    }

    public long getSequenceNumber() {
        return sequenceNumber;
    }

    public List<KeyValuePair> getEntries() {
        return entries;
    }

    public JournalEntryProto toProto() {
        JournalEntryProto.Builder builder = JournalEntryProto.newBuilder()
                .setOperationType(operationType)
                .setTimestamp(timestamp)
                .setSequenceNumber(sequenceNumber);

        for (KeyValuePair kvp : entries) {
            builder.addEntries(kvp.toProto());
        }

        return builder.build();
    }

    public static JournalEntry fromProto(JournalEntryProto proto) {
        List<KeyValuePair> entries = new ArrayList<>();
        for (KeyValuePairProto kvpProto : proto.getEntriesList()) {
            entries.add(KeyValuePair.fromProto(kvpProto));
        }
        return new JournalEntry(
                proto.getOperationType(),
                proto.getTimestamp(),
                proto.getSequenceNumber(),
                entries
        );
    }

    public static final class KeyValuePair {
        private final IndexKey key;
        private final IndexValue value;

        public KeyValuePair(IndexKey key, IndexValue value) {
            if (key == null) throw new IllegalArgumentException("key must not be null");
            if (value == null) throw new IllegalArgumentException("value must not be null");
            this.key = key;
            this.value = value;
        }

        public IndexKey getKey() { return key; }
        public IndexValue getValue() { return value; }

        public KeyValuePairProto toProto() {
            return KeyValuePairProto.newBuilder()
                    .setKey(key.toProto())
                    .setValue(value.toProto())
                    .build();
        }

        public static KeyValuePair fromProto(KeyValuePairProto proto) {
            return new KeyValuePair(
                    IndexKey.fromProto(proto.getKey()),
                    IndexValue.fromProto(proto.getValue())
            );
        }
    }

    @Override
    public String toString() {
        return "JournalEntry{type=" + operationType + ", seq=" + sequenceNumber +
               ", entries=" + entries.size() + '}';
    }
}