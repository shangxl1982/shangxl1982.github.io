# Story 4-5: Implement Batch Writing

## Story

As a developer, I want to implement batch writing so that multiple operations can be written efficiently.

## Acceptance Criteria

- [ ] batchWrite() method writes multiple operations
- [ ] Batch operations are atomically replayed
- [ ] Batch entry has BATCH operation type
- [ ] Sub-operations preserved in batch
- [ ] Unit tests verify all methods

## Technical Details

### Batch Entry Format

```protobuf
message JournalEntryProto {
    OperationType operation_type = 1;  // BATCH
    int64 timestamp = 2;
    int64 sequence_number = 3;
    repeated KeyValuePairProto entries = 4;  // Multiple operations
}
```

### Implementation

```java
public JournalReplayPoint batchWrite(List<JournalOperation> operations) {
    JournalEntry entry = new JournalEntry(OperationType.BATCH, operations);
    
    byte[] body = entry.toProto().toByteArray();
    WriteItem writeItem = new WriteItem(body);
    
    SegmentLocation location = chunkManager.writeJournal(writeItem.toByteArray());
    
    currentRegion.addEntry(location);
    
    return new JournalReplayPoint(currentRegion.getMajor(), 
                                  currentRegion.getMinor(), 
                                  location.getOffset());
}
```

## Testing

- testBatchWrite()
- testBatchReplay()
- testBatchAtomicity()
- testMultipleBatches()
- testBatchWithMixedOperations()

## Effort Estimate

1 day
