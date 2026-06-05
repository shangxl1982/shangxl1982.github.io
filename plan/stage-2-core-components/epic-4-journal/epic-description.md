# Epic 4: Journal

## Overview

Implement the Journal component for Write-Ahead Logging. The Journal ensures durability by writing operations to disk before updating in-memory structures.

## Goals

1. Implement Journal with write and replay functionality
2. Implement Write Item creation and batch writing
3. Implement region management
4. Implement crash recovery via replay

## Scope

### In Scope
- Journal write operations
- Replay mechanism
- Region management
- Batch writing support
- Crash recovery

### Out of Scope
- Compression (future enhancement)
- Encryption (future enhancement)

## Technical Design

### Journal Structure

```
Journal
├── Region 0
│   ├── Chunk 0 (JOURNAL type)
│   └── Region Index
├── Region 1
│   ├── Chunk 1 (JOURNAL type)
│   └── Region Index
└── ...
```

### Write Flow

```
1. Create WriteItem from operation
2. Write WriteItem to current Chunk
3. Update Region Index
4. Return ReplayPoint
```

### Replay Flow

```
1. Load Region Index
2. For each region:
   a. Load Chunk
   b. Read WriteItems
   c. Apply to MemoryTableManager
```

## Stories

| Story ID | Name | Priority |
|----------|------|----------|
| 4-1 | Implement Journal Class | High |
| 4-2 | Implement JournalWriter | High |
| 4-3 | Implement Replay Mechanism | High |
| 4-4 | Implement Region Management | High |
| 4-5 | Implement Batch Writing | High |
| 4-6 | Unit Tests for Journal | High |

## Dependencies

- Epic 1: Protobuf Serialization
- Epic 2: Data Integrity
- Epic 3: Storage Layer

## Acceptance Criteria

- [ ] Journal can write entries
- [ ] Journal can replay entries
- [ ] Region management works correctly
- [ ] Crash recovery works
- [ ] Batch writing works
- [ ] All unit tests pass

## Risk Mitigation

| Risk | Mitigation |
|------|------------|
| Replay failure | Validate CRC32, handle corrupted entries |
| Disk full | Check space before write |
| Concurrent access | Use file locks |
