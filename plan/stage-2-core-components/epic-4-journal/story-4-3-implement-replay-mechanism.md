# Story 4-3: Implement Replay Mechanism

## Story

As a developer, I want to implement the replay mechanism so that operations can be recovered after a crash.

## Acceptance Criteria

- [ ] replayFrom() method replays from specific point
- [ ] replayFromBeginning() method replays all operations
- [ ] Replay operations are applied to MemoryTableManager
- [ ] Replay point is updated after each replay
- [ ] CRC32 validation during replay
- [ ] Unit tests verify all methods

## Technical Details

### Replay Flow

```
1. Load Region Index
2. For each region:
   a. Load Chunk
   b. Read WriteItems from recorded locations
   c. For each WriteItem:
      i. Validate CRC32
      ii. Parse JournalEntry
      iii. Apply to MemoryTableManager
   d. Update replay point
3. Return final replay point
```

### Implementation

```java
public void replayFrom(JournalReplayPoint point, MemoryTableManager manager) {
    JournalRegion region = regions.get(point.getRegionMajor());
    
    if (region == null) {
        return;
    }
    
    for (SegmentLocation location : region.getEntries()) {
        if (location.getOffset() < point.getOffset()) {
            continue;
        }
        
        byte[] data = chunkManager.read(location);
        WriteItem writeItem = WriteItem.fromByteArray(data);
        
        if (!writeItem.validate()) {
            throw new JournalReplayException("CRC32 mismatch");
        }
        
        JournalEntry entry = JournalEntry.fromProto(writeItem.getBody());
        applyEntry(entry, manager);
    }
}
```

## Testing

- testReplayFromBeginning()
- testReplayFromPoint()
- testReplayWithCRC32Validation()
- testReplayWithCorruptedEntry()
- testReplayMultipleRegions()

## Effort Estimate

1.5 days
