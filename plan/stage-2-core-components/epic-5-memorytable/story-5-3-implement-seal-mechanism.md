# Story 5-3: Implement Seal Mechanism

## Story

As a developer, I want to implement the seal mechanism so that memory tables can be transitioned from active to sealed.

## Acceptance Criteria

- [ ] shouldSeal() method checks size threshold
- [ ] seal() method transitions active to sealed
- [ ] New active table created after seal
- [ ] Sealed tables are read-only
- [ ] Unit tests verify all methods

## Technical Details

### Implementation

```java
public void sealActiveTable() {
    if (activeTable.isSealed()) {
        return;
    }
    
    activeTable.seal();
    sealedTables.add(activeTable);
    activeTable = new MemoryTable(tableMaxSize);
}
```

### Trigger Conditions

1. Size threshold reached (default 64MB)
2. Manual seal request
3. System shutdown

## Testing

- testShouldSealSizeThreshold()
- testSealActiveTable()
- testNewActiveTableCreated()
- testSealedTableReadOnly()
- testMultipleSeals()

## Effort Estimate

1 day
