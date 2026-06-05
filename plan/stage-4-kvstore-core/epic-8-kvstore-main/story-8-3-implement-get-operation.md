# Story 8-3: Implement Get Operation

## Story

As a developer, I want to implement the get operation so that data can be retrieved.

## Acceptance Criteria

- [ ] get(Key) method retrieves data
- [ ] Query MemoryTable first
- [ ] Query B+Tree if not found
- [ ] Return null if not found
- [ ] Return null for tombstones
- [ ] Unit tests verify all methods

## Technical Details

### Implementation

```java
public IndexValue get(IndexKey key) {
    // 1. Query active MemoryTable
    IndexValue value = memoryTableManager.get(key);
    if (value != null) {
        return value;
    }
    
    // 2. Query B+Tree
    return bPlusTree.search(key);
}
```

### Read Flow

```
1. Query active MemoryTable (with read lock)
2. If not found, query sealed MemoryTables
3. If not found, query B+Tree (snapshot read)
4. Return result
```

## Testing

- testGetExistingEntry()
- testGetNonExistingEntry()
- testGetTombstoneEntry()
- testGetWithMultipleTables()
- testGetAfterDump()

## Effort Estimate

1 day
