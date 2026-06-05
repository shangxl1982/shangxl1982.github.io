# Stage 2: Core Components

## Goal

Implement the core components of the storage system: Journal (Write-Ahead Log) and MemoryTable. These components handle the in-memory data structures and log persistence.

## Duration

2-3 weeks

## Prerequisites

- Stage 1: Foundation Infrastructure completed
- Protobuf serialization implemented
- Storage layer (Chunk, ChunkManager) implemented

## Stage Objectives

1. Implement Journal with WAL functionality
2. Implement MemoryTable with active/sealed states
3. Implement MemoryTableManager for table lifecycle management
4. Implement replay mechanism for crash recovery

## Key Design Decisions

### Journal (WAL)
- Append-only log for durability
- Write to Chunk before MemoryTable update
- Replay on startup for recovery
- Region-based organization

### MemoryTable
- Active table for writes
- Sealed tables for reads
- Tombstone support for deletes
- Size-based sealing (default 64MB)

## Epic List

| Epic | Name | Description |
|------|------|-------------|
| 4 | Journal | Implement Write-Ahead Log with replay |
| 5 | MemoryTable | Implement in-memory table with active/sealed states |

## Dependencies

- Stage 1: Protobuf, Data Integrity, Storage Layer

## Acceptance Criteria

- [ ] Journal can write entries and replay them
- [ ] MemoryTable supports put/get/delete operations
- [ ] MemoryTable can be sealed and replaced
- [ ] MemoryTableManager can manage multiple tables
- [ ] Crash recovery works correctly
- [ ] All unit tests pass

## Next Stage

Stage 3: B+Tree Implementation (Page, B+Tree, Dump)
