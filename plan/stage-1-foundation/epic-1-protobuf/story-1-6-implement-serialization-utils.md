# Story 1-6: Implement Serialization Utils

## Story

As a developer, I want to implement serialization utility classes so that Java objects can be converted to/from Protobuf messages easily.

## Acceptance Criteria

- [ ] IndexKey class implemented with toProto() and fromProto() methods
- [ ] IndexValue class implemented with toProto() and fromProto() methods
- [ ] KeyValuePair class implemented
- [ ] SegmentLocation class implemented (24 bytes)
- [ ] JournalEntry class implemented
- [ ] Page class implemented
- [ ] All utility methods have unit tests
- [ ] Performance benchmark shows acceptable serialization speed

## Technical Details

### Classes to Implement

```java
// org.hyperkv.lsmplus.api.model.IndexKey
public class IndexKey {
    private final KeyType keyType;
    private final byte[] keyData;
    
    public KeyProto toProto();
    public static IndexKey fromProto(KeyProto proto);
    public int compareTo(IndexKey other); // For ORDERED_BYTES
}

// org.hyperkv.lsmplus.api.model.IndexValue
public class IndexValue {
    private final ValueType valueType;
    private final byte[] valueData;
    
    public ValueProto toProto();
    public static IndexValue fromProto(ValueProto proto);
    public boolean isTombstone();
}

// org.hyperkv.lsmplus.storage.SegmentLocation
public class SegmentLocation {
    private final UUID chunkId;
    private final int offset;
    private final int length;
    
    public SegmentLocationProto toProto();
    public static SegmentLocation fromProto(SegmentLocationProto proto);
    public byte[] toBytes(); // 24 bytes
    public static SegmentLocation fromBytes(byte[] bytes);
}
```

## Testing

- Round-trip test for each class: object → proto → object
- Verify SegmentLocation is exactly 24 bytes
- Test ORDERED_BYTES comparison
- Test Tombstone detection

## Effort Estimate

1 day
