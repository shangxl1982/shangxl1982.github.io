# Story 8-6: Implement Dump Mechanism

## Story

As a developer, I want to implement the dump mechanism so that MemoryTable data can be persisted to B+Tree.

## Acceptance Criteria

- [ ] dump() method triggers tree dump
- [ ] Sealed MemoryTables merged
- [ ] Ordered entries created
- [ ] B+Tree built from entries
- [ ] New Tree version created
- [ ] Unit tests verify all methods

## Technical Details

### Implementation

```java
public void dump() {
    // 1. Merge sealed MemoryTables
    List<Map.Entry<IndexKey, IndexValue>> entries = 
        memoryTableManager.mergeSealedTables();
    
    // 2. Build B+Tree
    BPlusTree newTree = new BPlusTree();
    newTree.dump(entries);
    
    // 3. Update B+Tree reference
    bPlusTree = newTree;
    
    // 4. Update metadata
    metadataManager.saveTreeMetadata(newTree);
}
```

## Testing

- testDumpEmptyMemoryTables()
- testDumpSingleTable()
- testDumpMultipleTables()
- testDumpAfterMultiplePuts()
- testDumpAndRecovery()

## Effort Estimate

1.5 days
