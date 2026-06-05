# Story 7-3: Implement Tree Dump

## Story

As a developer, I want to implement the Tree Dump mechanism so that MemoryTable data can be persisted to B+Tree.

## Acceptance Criteria

- [ ] dump() method merges sealed MemoryTables
- [ ] Ordered entries created from MemoryTables
- [ ] B+Tree built from ordered entries
- [ ] New Tree version created
- [ ] Old MemoryTables can be deleted
- [ ] Unit tests verify all methods

## Technical Details

### Dump Flow

```
1. Collect all sealed MemoryTables
2. Merge entries in reverse order (newest first)
3. Create ordered entries list
4. Build B+Tree from entries
5. Write to new chunks
6. Create new Tree version
7. Update metadata
```

### Implementation

```java
public void dump() {
    // 1. Collect sealed tables
    List<MemoryTable> tables = memoryTableManager.getSealedTables();
    
    // 2. Merge entries
    List<Map.Entry<IndexKey, IndexValue>> entries = mergeTables(tables);
    
    // 3. Build B+Tree
    BPlusTree newTree = buildTree(entries);
    
    // 4. Write to chunks
    writeTreeToChunks(newTree);
    
    // 5. Create version
    TreeVersion version = new TreeVersion(newTree.getRoot(), replayPoint);
    versions.add(version);
}
```

## Testing

- testDumpEmptyMemoryTables()
- testDumpSingleTable()
- testDumpMultipleTables()
- testMergeEntries()
- testTreeBuild()

## Effort Estimate

2 days
