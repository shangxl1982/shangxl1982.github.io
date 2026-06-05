# Stage 4: KVStore Core

## Goal

Integrate all components into the KVStore class. This stage builds the main storage engine with put/get/delete/batch operations and concurrency control.

## Duration

2-3 weeks

## Prerequisites

- Stage 1: Foundation Infrastructure completed
- Stage 2: Core Components completed
- Stage 3: B+Tree Implementation completed
- All components implemented and tested

## Stage Objectives

1. Integrate Journal, MemoryTable, and B+Tree
2. Implement put/get/delete operations
3. Implement batch operations
4. Implement dump mechanism
5. Implement concurrency control

## Key Design Decisions

### KVStore Architecture

```
┌─────────────────────────────────────────────────────────────┐
│                        KVStore                               │
├─────────────────────────────────────────────────────────────┤
│  Components:                                                 │
│  - Journal (WAL)                                             │
│  - MemoryTableManager                                        │
│  - B+Tree                                                    │
│  - WriteRequestQueue                                         │
│  - BatchWriter                                               │
└─────────────────────────────────────────────────────────────┘
```

### Write Path

```
1. Client calls put/get/delete
2. Create WriteRequest
3. Add to WriteRequestQueue
4. Wait for completion
5. BatchWriter processes batch
6. Write to Journal
7. Update MemoryTable
8. Notify client
```

### Read Path

```
1. Client calls get
2. Query MemoryTable (active then sealed)
3. Query B+Tree
4. Return result
```

## Epic List

| Epic | Name | Description |
|------|------|-------------|
| 8 | KVStore Main | Integrate all components into KVStore |
| 9 | Concurrency Control | Implement concurrent read/write |

## Dependencies

- Stage 1: Protobuf, Data Integrity, Storage Layer
- Stage 2: Journal, MemoryTable
- Stage 3: B+Tree

## Acceptance Criteria

- [ ] KVStore can put/get/delete data
- [ ] Batch operations work correctly
- [ ] Dump mechanism works
- [ ] Concurrency control works
- [ ] Crash recovery works
- [ ] All unit tests pass

## Next Stage

Stage 5: Advanced Features (GC, Backup, Monitoring, Config)
