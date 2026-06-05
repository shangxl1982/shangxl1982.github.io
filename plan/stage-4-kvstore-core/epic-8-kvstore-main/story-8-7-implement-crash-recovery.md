# Story 8-7: Implement Crash Recovery

## Story

As a developer, I want to implement crash recovery so that data can be restored after a crash.

## Acceptance Criteria

- [ ] Recovery from Journal on startup
- [ ] Replay all operations since last checkpoint
- [ ] MemoryTable restored
- [ ] B+Tree restored from metadata
- [ ] Unit tests verify all methods

## Technical Details

### Recovery Flow

```
1. Load TreeMetadata
2. Load B+Tree from metadata
3. Load Journal RegionIndex
4. Replay from last checkpoint
5. Restore MemoryTable
```

### Implementation

```java
private void recover() {
    // 1. Load metadata
    TreeMetadata metadata = metadataManager.load();
    
    // 2. Load B+Tree
    bPlusTree = loadBPlusTree(metadata.getRootLocation());
    
    // 3. Replay Journal
    JournalReplayPoint point = metadata.getReplayPoint();
    journal.replayFrom(point, memoryTableManager);
}
```

## Testing

- testRecoveryFromEmptyState()
- testRecoveryWithJournal()
- testRecoveryWithBPlusTree()
- testRecoveryWithBoth()
- testRecoveryAfterCrash()

## Effort Estimate

1.5 days
