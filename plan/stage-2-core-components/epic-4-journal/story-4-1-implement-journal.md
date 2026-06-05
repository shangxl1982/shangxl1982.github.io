# Story 4-1: Implement Journal Class

## Story

As a developer, I want to implement the Journal class so that operations can be logged and replayed.

## Acceptance Criteria

- [ ] Journal class created
- [ ] write(OperationType, Key, Value) method writes entry
- [ ] replay(ReplayPoint, MemoryTableManager) method replays from point
- [ ] replayFromBeginning(MemoryTableManager) method replays all
- [ ] getReplayPoint() returns current replay point
- [ ] close() method closes journal
- [ ] Unit tests verify all methods

## Technical Details

### Class: Journal

```java
package org.hyperkv.lsmplus.journal;

public class Journal {
    private final File journalDir;
    private final ChunkManager chunkManager;
    private final Map<Integer, JournalRegion> regions;
    private JournalRegion currentRegion;
    private long sequenceNumber;
    
    public Journal(File journalDir, ChunkManager chunkManager);
    public JournalReplayPoint write(OperationType type, IndexKey key, IndexValue value);
    public JournalReplayPoint writeBatch(List<JournalOperation> operations);
    public void replayFrom(JournalReplayPoint point, MemoryTableManager manager);
    public void replayFromBeginning(MemoryTableManager manager);
    public JournalReplayPoint getReplayPoint();
    public void close();
}
```

### Write Implementation

```java
public JournalReplayPoint write(OperationType type, IndexKey key, IndexValue value) {
    JournalEntry entry = new JournalEntry(type, key, value, sequenceNumber++);
    
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

- testWriteSingleEntry()
- testWriteMultipleEntries()
- testReplayFromBeginning()
- testReplayFromPoint()
- testSequenceNumbers()
- testClose()

## Effort Estimate

1.5 days
