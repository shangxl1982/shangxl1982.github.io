# Implementation Plan: LSM Plus Storage Layer

**Branch**: `003-lsmplus-storage` | **Date**: 2026-04-17 | **Spec**: [spec.md](file:///home/wisefox/git/hyperkvstore/specs/003-lsmplus-storage/spec.md)
**Input**: Feature specification from `/specs/003-lsmplus-storage/spec.md`

## Summary

Implement chunk-based append-only storage layer with segment location management for LSM tree persistence. Provides sequential write optimization, crash consistency, and efficient random access through segment locations (chunkId, offset, length). Manages chunk lifecycle (OPEN, SEALED, DELETING, DELETED) with CRC32 checksums and magic numbers for data integrity.

## Technical Context

**Language/Version**: Java 25  
**Primary Dependencies**: lsmplus-api, protobuf-java 3.34.1, JUnit 6.0.0  
**Storage**: File-based chunk storage with append-only writes  
**Testing**: JUnit 5 with Mockito  
**Target Platform**: Linux server (JVM)  
**Project Type**: Library  
**Performance Goals**: >500 MB/s sequential write, <1ms random read, <100μs integrity validation  
**Constraints**: 64MB default chunk size, CRC32 checksums, magic number headers  
**Scale/Scope**: 5 core classes (Chunk, ChunkManager, SegmentLocation, ChunkHeader, WriteItem)  

## Constitution Check

*GATE: Must pass before Phase 0 research. Re-check after Phase 1 design.*

✅ **Library-First**: Standalone storage library with clear purpose
✅ **Test-First**: TDD with unit and integration tests
✅ **Simplicity**: Focused on chunk management, no unnecessary abstractions
✅ **Observability**: Checksums, magic numbers, and status tracking
✅ **Versioning**: Chunk format versioned for compatibility

## Project Structure

### Documentation (this feature)

```text
specs/003-lsmplus-storage/
├── plan.md              # This file
├── research.md          # Phase 0 output
├── data-model.md        # Phase 1 output
├── quickstart.md        # Phase 1 output
├── contracts/           # Phase 1 output
└── tasks.md             # Phase 2 output
```

### Source Code (repository root)

```text
lsmplus-storage/
├── src/
│   ├── main/
│   │   └── java/
│   │       └── org/hyperkv/lsmplus/storage/
│   │           ├── Chunk.java                # Chunk file with header and data
│   │           ├── ChunkHeader.java          # Metadata (chunkId, status, size)
│   │           ├── ChunkManager.java         # Lifecycle management
│   │           ├── SegmentLocation.java      # Data reference (chunkId, offset, length)
│   │           ├── StorageLayout.java        # Directory structure management
│   │           └── WriteItem.java            # Single write operation
│   └── test/
│       └── java/
│           └── org/hyperkv/lsmplus/storage/
│               ├── ChunkTest.java
│               ├── ChunkManagerTest.java
│               ├── SegmentLocationTest.java
│               └── DataIntegrityIntegrationTest.java
└── build.gradle.kts
```

**Structure Decision**: Single library module. Chunk files stored in configurable directory hierarchy. Memory-mapped I/O for read optimization.

## Complexity Tracking

> No constitution violations detected.

## Phase 0: Research & Design Decisions

### Research Tasks

1. **Chunk File Format**
   - Decision: Header (magic, version, metadata) + data region
   - Rationale: Self-describing, supports integrity checks
   - Alternatives: Separate metadata file (consistency issues)

2. **I/O Strategy**
   - Decision: Memory-mapped I/O for reads, buffered writes
   - Rationale: Optimal for sequential writes and random reads
   - Alternatives: Direct I/O (complex), channel I/O (slower)

3. **Chunk Size**
   - Decision: 64MB default, configurable
   - Rationale: Balance between file count and memory usage
   - Alternatives: 128MB (larger files), 32MB (more files)

4. **Concurrent Access**
   - Decision: Multiple readers, single writer per chunk
   - Rationale: Append-only allows concurrent reads
   - Alternatives: Exclusive access (inefficient), lock-free (complex)

5. **Integrity Validation**
   - Decision: CRC32 per write, magic number per chunk
   - Rationale: Fast validation, detect corruption
   - Alternatives: SHA-256 (slower), no validation (unsafe)

### Design Decisions

1. **Append-Only**: All writes append to end of chunk
2. **Sealing**: Chunks become immutable after sealing
3. **Segment Locations**: Use (chunkId, offset, length) for data reference
4. **Chunk IDs**: UUID-based for uniqueness
5. **Status Tracking**: In-memory and persisted in header

## Phase 1: Design & Contracts

### Data Model

See [data-model.md](file:///home/wisefox/git/hyperkvstore/specs/003-lsmplus-storage/data-model.md).

**Core Entities**:
- **Chunk**: Storage unit with header and data region
- **ChunkHeader**: Metadata (chunkId, status, size, timestamps, CRC32)
- **SegmentLocation**: Reference to persisted data
- **ChunkManager**: Coordinates chunk lifecycle
- **WriteItem**: Represents a single write operation

### Contracts

**Public API**:
- `ChunkManager.createChunk()` - Create new open chunk
- `Chunk.append(byte[] data)` - Append data, return SegmentLocation
- `Chunk.seal()` - Make chunk immutable
- `Chunk.read(SegmentLocation)` - Read data at location
- `ChunkManager.deleteChunk(chunkId)` - Delete chunk

**Storage Contract**:
- All writes MUST persist to disk before acknowledgment
- All reads MUST validate CRC32 checksum
- Chunks MUST be sealed before deletion
- Segment locations MUST reference valid data

### Quickstart

See [quickstart.md](file:///home/wisefox/git/hyperkvstore/specs/003-lsmplus-storage/quickstart.md).

## Phase 2: Implementation Tasks

*Tasks will be generated by `/speckit.tasks` command.*

## Dependencies

**Internal Dependencies**:
- lsmplus-api (IndexKey, IndexValue, protobuf messages)

**External Dependencies**:
- protobuf-java 3.34.1
- JUnit 6.0.0
- Mockito 5.11.0

## Risks & Mitigations

| Risk | Impact | Mitigation |
|------|--------|------------|
| Disk I/O bottleneck | High | Use buffered I/O, memory-mapped reads |
| Chunk corruption | High | CRC32 validation, backup chunks |
| Concurrent write conflicts | Medium | Single writer per chunk, queue writes |
| Disk space exhaustion | Medium | Monitor usage, trigger GC early |

## Success Metrics

- ✅ Sequential write throughput >500 MB/s
- ✅ Random read latency <1ms
- ✅ CRC32 validation <100μs
- ✅ Chunk sealing <10ms
- ✅ Zero data loss in 1M operations
- ✅ Storage overhead <5%
