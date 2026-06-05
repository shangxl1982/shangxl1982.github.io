# Story 4-2: Implement JournalWriter

## Story

As a developer, I want to implement the JournalWriter class so that write operations can be batched efficiently.

## Acceptance Criteria

- [ ] JournalWriter class created
- [ ] write() method writes single entry
- [ ] batchWrite() method writes multiple entries
- [ ] writeBatch() method writes batch operation
- [ ] Flush mechanism implemented
- [ ] Unit tests verify all methods

## Technical Details

### Class: JournalWriter

```java
package org.hyperkv.lsmplus.journal;

public class JournalWriter {
    private final Journal journal;
    private final List<JournalOperation> pendingOperations;
    private final int batchSize;
    
    public JournalWriter(Journal journal, int batchSize);
    public JournalReplayPoint write(OperationType type, IndexKey key, IndexValue value);
    public JournalReplayPoint batchWrite(List<JournalOperation> operations);
    public void flush();
}
```

### Batch Write Implementation

```java
public JournalReplayPoint batchWrite(List<JournalOperation> operations) {
    JournalEntry entry = new JournalEntry(OperationType.BATCH, operations);
    
    byte[] body = entry.toProto().toByteArray();
    WriteItem writeItem = new WriteItem(body);
    
    SegmentLocation location = journal.getChunkManager()
        .writeJournal(writeItem.toByteArray());
    
    return new JournalReplayPoint(location);
}
```

## Testing

- testWriteSingle()
- testBatchWrite()
- testFlush()
- testBatchBoundary()
- testMultipleBatches()

## Effort Estimate

1 day
