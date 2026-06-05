# Epic 8: KVStore Main

## Overview

Integrate all components into the KVStore class. This epic builds the main storage engine with put/get/delete/batch operations.

## Goals

1. Implement KVStore class
2. Implement put/get/delete operations
3. Implement batch operations
4. Implement dump mechanism
5. Implement crash recovery

## Scope

### In Scope
- KVStore class with all operations
- Put/Get/Delete operations
- Batch operations
- Dump mechanism
- Crash recovery

### Out of Scope
- Advanced features (GC, Backup, Monitoring, Config)
- Service layer (REST/gRPC)

## Technical Design

### KVStore Class

```java
public class KVStore {
    private final File dataDir;
    private final Config config;
    private final ChunkManager chunkManager;
    private final Journal journal;
    private final MemoryTableManager memoryTableManager;
    private final BPlusTree bPlusTree;
    
    public KVStore(File dataDir, Config config);
    public void start();
    public void shutdown();
    public void put(IndexKey key, IndexValue value);
    public IndexValue get(IndexKey key);
    public void delete(IndexKey key);
    public void batch(List<BatchOperation> operations);
    public void dump();
}
```

### Write Path

```
1. Client calls put(key, value)
2. Create WriteRequest
3. Add to WriteRequestQueue
4. Wait for completion
5. BatchWriter processes
6. Write to Journal
7. Update MemoryTable
8. Notify client
```

### Read Path

```
1. Client calls get(key)
2. Query MemoryTable
3. Query B+Tree
4. Return result
```

## Stories

| Story ID | Name | Priority |
|----------|------|----------|
| 8-1 | Implement KVStore Class | High |
| 8-2 | Implement Put Operation | High |
| 8-3 | Implement Get Operation | High |
| 8-4 | Implement Delete Operation | High |
| 8-5 | Implement Batch Operation | High |
| 8-6 | Implement Dump Mechanism | High |
| 8-7 | Implement Crash Recovery | High |
| 8-8 | Unit Tests for KVStore | High |

## Dependencies

- Stage 1: Protobuf, Data Integrity, Storage Layer
- Stage 2: Journal, MemoryTable
- Stage 3: B+Tree

## Acceptance Criteria

- [ ] KVStore can put/get/delete data
- [ ] Batch operations work correctly
- [ ] Dump mechanism works
- [ ] Crash recovery works
- [ ] All unit tests pass

## Risk Mitigation

| Risk | Mitigation |
|------|------------|
| Data loss | WAL ensures durability |
| Concurrent access | Request queue serializes writes |
| Memory overflow | Size-based dump |
