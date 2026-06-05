# Story 1-7: Unit Tests for All Messages

## Story

As a developer, I want comprehensive unit tests for all Protobuf messages so that serialization correctness is verified.

## Acceptance Criteria

- [ ] Unit tests for all enum types
- [ ] Unit tests for KeyProto round-trip
- [ ] Unit tests for ValueProto round-trip (including TOMBSTONE)
- [ ] Unit tests for KeyValuePairProto (both value and location variants)
- [ ] Unit tests for SegmentLocationProto (24 bytes verification)
- [ ] Unit tests for JournalEntryProto (PUT, DELETE, BATCH)
- [ ] Unit tests for PageProto (Leaf and Index)
- [ ] Unit tests for all metadata messages
- [ ] Test coverage > 90%

## Technical Details

### Test Structure

```
src/test/java/org/hyperkv/lsmplus/
├── proto/
│   ├── CommonProtoTest.java
│   ├── KeyValueProtoTest.java
│   ├── JournalProtoTest.java
│   ├── PageProtoTest.java
│   └── MetadataProtoTest.java
└── model/
    ├── IndexKeyTest.java
    ├── IndexValueTest.java
    ├── SegmentLocationTest.java
    └── JournalEntryTest.java
```

### Test Cases

1. **IndexKeyTest**
   - testOrderedBytesKey()
   - testCustomKey()
   - testComparison()
   - testRoundTrip()

2. **IndexValueTest**
   - testNormalValue()
   - testTombstoneValue()
   - testRoundTrip()

3. **SegmentLocationTest**
   - testToFromProto()
   - testToFromBytes()
   - testFixedSize24Bytes()

4. **JournalEntryTest**
   - testPutEntry()
   - testDeleteEntry()
   - testBatchEntry()

## Effort Estimate

1 day
