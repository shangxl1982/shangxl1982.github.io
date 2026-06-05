# Epic 3: Storage Layer

## Overview

Implement the storage layer including Chunk, ChunkManager, and related components. This provides the foundation for persistent storage.

## Goals

1. Implement Chunk with header, data area, and lifecycle management
2. Implement ChunkManager for chunk allocation and management
3. Implement directory structure and file naming conventions
4. Implement chunk lifecycle (OPEN → SEALED → DELETING → DELETED)

## Scope

### In Scope
- Chunk implementation (header, read, write, seal)
- ChunkManager implementation (allocate, get, list, delete)
- Directory structure creation
- Chunk lifecycle management

### Out of Scope
- GC implementation (Stage 5)
- Metadata persistence (Stage 3)

## Technical Design

### Directory Structure

```
data/
├── chunks/
│   ├── index/
│   │   └── chunk-{chunk_number}.dat
│   ├── leaf/
│   │   └── chunk-{chunk_number}.dat
│   └── journal/
│       └── chunk-{chunk_number}.dat
├── metadata/
│   ├── tree-metadata.dat
│   ├── journal-region-index.dat
│   └── chunk-metadata.dat
└── backup/
    └── backup-{timestamp}.dat
```

### Chunk Header Format (4096 bytes)

```
┌─────────────────────────────────────────────────────────────┐
│                    Chunk Header (4096 bytes)                 │
├─────────────────────────────────────────────────────────────┤
│  Offset 0-15:   ChunkID (UUID, 16 bytes)                    │
│  Offset 16-19:  ChunkType (4 bytes)                         │
│  Offset 20-35:  OwnerID (UUID, 16 bytes)                    │
│  Offset 36-51:  NamespaceID (UUID, 16 bytes)                │
│  Offset 52-55:  ValidDataSize (4 bytes)                     │
│  Offset 56-4095: Reserved (4040 bytes, all 0)               │
└─────────────────────────────────────────────────────────────┘
```

### Chunk Lifecycle

```
OPEN → SEALED → DELETING → DELETED
  │        │         │         │
  │        │         │         └── File deleted
  │        │         └── Marked for GC
  │        └── No more writes allowed
  └── Can read and write
```

## Stories

| Story ID | Name | Priority |
|----------|------|----------|
| 3-1 | Implement ChunkHeader | High |
| 3-2 | Implement Chunk Class | High |
| 3-3 | Implement ChunkManager | High |
| 3-4 | Implement Directory Structure | High |
| 3-5 | Implement Chunk Lifecycle | High |
| 3-6 | Unit Tests for Storage Layer | High |

## Dependencies

- Epic 1: Protobuf Serialization
- Epic 2: Data Integrity

## Acceptance Criteria

- [ ] Chunk can be created with correct header
- [ ] Chunk can write and read data
- [ ] Chunk can be sealed (no more writes)
- [ ] ChunkManager can allocate chunks
- [ ] ChunkManager can list and get chunks
- [ ] Directory structure created correctly
- [ ] Chunk lifecycle transitions work

## Risk Mitigation

| Risk | Mitigation |
|------|------------|
| Disk full | Implement disk space check before allocation |
| File corruption | Use CRC32 validation |
| Concurrent access | Use file locks |
