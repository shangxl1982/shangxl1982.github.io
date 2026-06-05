# Epic 5: MemoryTable

## Overview

Implement the MemoryTable component for in-memory data storage. MemoryTable supports active/sealed states, tombstones for deletes, and efficient read/write operations.

## Goals

1. Implement MemoryTable with active/sealed states
2. Implement MemoryTableManager for table lifecycle management
3. Implement seal mechanism based on size threshold
4. Implement read operations with tombstone support

## Scope

### In Scope
- MemoryTable with put/get/delete operations
- Active/Sealed state management
- Size-based sealing
- Tombstone support
- MemoryTableManager for multiple tables

### Out of Scope
- Compaction (future enhancement)
- Compression (future enhancement)

## Technical Design

### MemoryTable Structure

```
MemoryTable
├── Active Table (writable)
│   ├── Red-Black Tree or Skip List
│   └── Current Size Tracker
└── Sealed Tables (read-only)
    └── List of sealed tables
```

### Write Flow

```
1. Check if table is sealed
2. Update in-memory structure
3. Update size tracker
4. Check if shouldSeal()
5. If yes, seal and create new active table
```

### Read Flow

```
1. Query active table
2. If not found, query sealed tables in reverse order
3. If not found, return null
4. If tombstone, return null
```

## Stories

| Story ID | Name | Priority |
|----------|------|----------|
| 5-1 | Implement MemoryTable Class | High |
| 5-2 | Implement MemoryTableManager | High |
| 5-3 | Implement Seal Mechanism | High |
| 5-4 | Implement Tombstone Support | High |
| 5-5 | Unit Tests for MemoryTable | High |

## Dependencies

- Epic 1: Protobuf Serialization

## Acceptance Criteria

- [ ] MemoryTable supports put/get/delete
- [ ] MemoryTable can be sealed
- [ ] MemoryTableManager can manage multiple tables
- [ ] Tombstones work correctly
- [ ] Read operations return correct values
- [ ] All unit tests pass

## Risk Mitigation

| Risk | Mitigation |
|------|------------|
| Memory overflow | Size-based sealing, configurable thresholds |
| Concurrent access | Read/write locks |
| Tombstone accumulation | Future compaction |
