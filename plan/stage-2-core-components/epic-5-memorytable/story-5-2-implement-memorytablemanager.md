# Story 5-2: Implement MemoryTableManager

## Story

As a developer, I want to implement the MemoryTableManager class so that multiple memory tables can be managed.

## Acceptance Criteria

- [ ] MemoryTableManager class created
- [ ] put(Key, Value) method writes to active table
- [ ] get(Key) method reads from all tables
- [ ] delete(Key) method deletes from active table
- [ ] sealActiveTable() method seals current table
- [ ] getAllTables() method returns all tables
- [ ] Unit tests verify all methods

## Technical Details

### Class: MemoryTableManager

```java
package org.hyperkv.lsmplus.memory;

public class MemoryTableManager {
    private final int tableMaxSize;
    private final List<MemoryTable> sealedTables;
    private MemoryTable activeTable;
    
    public MemoryTableManager(int tableMaxSize);
    public void put(IndexKey key, IndexValue value);
    public IndexValue get(IndexKey key);
    public void delete(IndexKey key);
    public void sealActiveTable();
    public List<MemoryTable> getAllTables();
    public int getActiveTableSize();
}
```

### Write Flow

```
1. Write to active table
2. Check if shouldSeal()
3. If yes, seal active table and create new one
4. Return
```

### Read Flow

```
1. Query active table
2. If not found, query sealed tables in reverse order
3. Return result
```

## Testing

- testPutAndGet()
- testDelete()
- testSealActiveTable()
- testMultipleTables()
- testRangeQuery()

## Effort Estimate

1.5 days
