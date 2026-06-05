package org.hyperkv.lsmplus.bplustree.page;

import org.hyperkv.lsmplus.api.model.IndexKey;
import org.hyperkv.lsmplus.api.model.IndexValue;
import org.hyperkv.lsmplus.proto.Keyvalue;
import org.hyperkv.lsmplus.storage.SegmentLocation;

public sealed interface IndexPair permits IndexPair.ValueEntry, IndexPair.LocationEntry {

    IndexKey key();

    Keyvalue.KeyValuePairProto toProto();

    static IndexPair of(IndexKey key, IndexValue value) {
        return new ValueEntry(key, value);
    }

    static IndexPair of(IndexKey key, SegmentLocation location) {
        return new LocationEntry(key, location);
    }

    static IndexPair fromProto(Keyvalue.KeyValuePairProto proto) {
        IndexKey key = Page.fromKeyProto(proto.getKey());
        return switch (proto.getEntryValueCase()) {
            case VALUE -> new ValueEntry(key, Page.fromValueProto(proto.getValue()));
            case LOCATION -> new LocationEntry(key, Page.fromSegmentLocationProto(proto.getLocation()));
            default -> throw new IllegalArgumentException("Unknown entry value case: " + proto.getEntryValueCase());
        };
    }

    record ValueEntry(IndexKey key, IndexValue value) implements IndexPair {
        @Override
        public Keyvalue.KeyValuePairProto toProto() {
            return Keyvalue.KeyValuePairProto.newBuilder()
                    .setKey(Page.toKeyProto(key))
                    .setValue(Page.toValueProto(value))
                    .build();
        }
    }

    record LocationEntry(IndexKey key, SegmentLocation location) implements IndexPair {
        @Override
        public Keyvalue.KeyValuePairProto toProto() {
            return Keyvalue.KeyValuePairProto.newBuilder()
                    .setKey(Page.toKeyProto(key))
                    .setLocation(Page.toSegmentLocationProto(location))
                    .build();
        }
    }

    static IndexPair copy(IndexPair other) {
        return other instanceof IndexPair.ValueEntry ?
                new ValueEntry(other.key(), ((ValueEntry) other).value()) :
                new LocationEntry(other.key(), ((LocationEntry) other).location());
    }
}
