# Epic 7: B+Tree Core

## Overview

Implement the B+Tree data structure with insert/search operations and dump mechanism. This epic builds the persistent storage layer for the KVStore.

## Goals

1. Implement B+Tree with insert/search operations
2. Implement page management
3. Implement tree dump mechanism
4. Implement WriteBuffer for batch writes

## Scope

### In Scope
- B+Tree class with insert/search
- Page management (load, save, cache)
- Tree dump with MemoryTable merge
- WriteBuffer for batch writes
- Version management

### Out of Scope
- Page caching (future enhancement)
- Compaction (future enhancement)

## Technical Design

### B+Tree Structure

```
B+Tree
├── Root Page (Page ID: 0)
├── Height (number of levels)
├── MaxLeafPageSize (e.g., 64KB)
├── MaxIndexPageSize (e.g., 64KB)
└── Versions (list of tree versions)
```

### Insert Flow

```
1. Start from root
2. Traverse to leaf page
3. Insert key-value pair
4. If leaf full, split and propagate
5. Update root if needed
```

### Search Flow

```
1. Start from root
2. Traverse index pages using key
3. Find leaf page
4. Search leaf page for key
5. Return value or null
```

### Tree Dump Flow

```
1. Merge all sealed MemoryTables
2. Create ordered entries
3. Build B+Tree from entries
4. Write to new chunks
5. Create new Tree version
6. Update metadata
```

## Stories

| Story ID | Name | Priority |
|----------|------|----------|
| 7-1 | Implement B+Tree Class | High |
| 7-2 | Implement PageManager | High |
| 7-3 | Implement Tree Dump | High |
| 7-4 | Implement WriteBuffer | High |
| 7-5 | Unit Tests for B+Tree | High |

## Dependencies

- Epic 1: Protobuf Serialization
- Epic 2: Data Integrity
- Epic 3: Storage Layer
- Epic 6: Page

## Acceptance Criteria

- [ ] B+Tree supports insert/search
- [ ] Page management works correctly
- [ ] Tree dump creates new version
- [ ] WriteBuffer batches writes efficiently
- [ ] All unit tests pass

## Risk Mitigation

| Risk | Mitigation |
|------|------------|
| Tree corruption | CRC32 validation |
| Memory overflow | Size-based dump |
| Concurrent access | Read/write locks |
