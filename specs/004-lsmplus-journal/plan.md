# Implementation Plan: LSM Plus Journal (Write-Ahead Log)

**Branch**: `004-lsmplus-journal` | **Date**: 2026-04-17 | **Spec**: [spec.md](file:///home/wisefox/git/hyperkvstore/specs/004-lsmplus-journal/spec.md)
**Input**: Feature specification from `/specs/004-lsmplus-journal/spec.md`

## Summary

Implement write-ahead logging system for durability and crash recovery in LSM tree. Persists all operations (PUT, DELETE, BATCH) before acknowledgment, supports crash recovery through journal replay, manages journal regions with rotation and archiving, and provides batch write optimization with single fsync for efficiency.

## Technical Context

**Language/Version**: Java 25  
**Primary Dependencies**: lsmplus-api, lsmplus-storage, protobuf-java 3.34.1, JUnit 6.0.0  
**Storage**: File-based journal regions with sequential writes  
**Testing**: JUnit 5 with Mockito  
**Target Platform**: Linux server (JVM)  
**Project Type**: Library  
**Performance Goals**: <1ms single write, >100K ops/s batch, <5s recovery for 1M entries  
**Constraints**: 64MB default region size, CRC32 checksums, monotonic sequence numbers  
**Scale/Scope**: 6 core classes (Journal, JournalEntry, JournalRegion, JournalWriter, JournalReplayHandler, JournalReplayPoint)  

## Constitution Check

*GATE: Must pass before Phase 0 research. Re-check after Phase 1 design.*

✅ **Library-First**: Standalone journal library with clear purpose
✅ **Test-First**: TDD with unit, integration, and crash recovery tests
✅ **Simplicity**: Focused on write-ahead logging, no unnecessary features
✅ **Observability**: Entry checksums, sequence numbers, region tracking
✅ **Versioning**: Journal format versioned for compatibility

## Project Structure

### Documentation (this feature)

```text
specs/004-lsmplus-journal/
├── plan.md              # This file
├── research.md          # Phase 0 output
├── data-model.md        # Phase 1 output
├── quickstart.md        # Phase 1 output
├── contracts/           # Phase 1 output
└── tasks.md             # Phase 2 output
```

### Source Code (repository root)

```text
lsmplus-journal/
├── src/
│   ├── main/
│   │   └── java/
│   │       └── org/hyperkv/lsmplus/journal/
│   │           ├── Journal.java               # Main journal manager
│   │           ├── JournalEntry.java          # Individual operation entry
│   │           ├── JournalRegion.java         # Journal segment file
│   │           ├── JournalRegionIndexFile.java # Region index for fast replay
│   │           ├── JournalWriter.java         # Sequential write handler
│   │           ├── JournalReplayHandler.java  # Recovery processor
│   │           ├── JournalReplayPoint.java    # Checkpoint marker
│   │           └── JournalReplayException.java # Recovery error handling
│   └── test/
│       └── java/
│           └── org/hyperkv/lsmplus/journal/
│               ├── JournalTest.java
│               ├── JournalWriterTest.java
│               ├── JournalReplayTest.java
│               └── JournalIntegrationTest.java
└── build.gradle.kts
```

**Structure Decision**: Single library module. Journal regions stored as separate files with index for fast replay. Sequential writes with periodic fsync.

## Complexity Tracking

> No constitution violations detected.

## Phase 0: Research & Design Decisions

### Research Tasks

1. **Journal Format**
   - Decision: Entry-based with header (type, timestamp, size) + data
   - Rationale: Self-describing, supports partial recovery
   - Alternatives: Block-based (complex), stream-based (no boundaries)

2. **Region Management**
   - Decision: Fixed-size regions (64MB) with rotation
   - Rationale: Balance file size and management overhead
   - Alternatives: Single file (large), variable size (unpredictable)

3. **Recovery Strategy**
   - Decision: Replay from last checkpoint with validation
   - Rationale: Fast recovery, detect corruption
   - Alternatives: Full replay (slow), snapshot-based (complex)

4. **Batch Optimization**
   - Decision: Group entries with single fsync
   - Rationale: Reduce I/O overhead for bulk writes
   - Alternatives: Individual fsync (slow), no fsync (unsafe)

5. **Sequence Numbers**
   - Decision: Monotonically increasing long values
   - Rationale: Total ordering, detect missing entries
   - Alternatives: Timestamps (non-monotonic), UUIDs (no ordering)

### Design Decisions

1. **Append-Only**: All entries append to current region
2. **Region Rotation**: Automatic when region reaches size limit
3. **Checksums**: CRC32 per entry for integrity
4. **Replay Handlers**: Pluggable handlers for different operation types
5. **Checkpoints**: Periodic markers for efficient recovery

## Phase 1: Design & Contracts

### Data Model

See [data-model.md](file:///home/wisefox/git/hyperkvstore/specs/004-lsmplus-journal/data-model.md).

**Core Entities**:
- **Journal**: Main coordinator for writes and recovery
- **JournalEntry**: Individual operation with type, timestamp, key-value
- **JournalRegion**: Segment file with entries
- **JournalWriter**: Handles sequential writes
- **JournalReplayHandler**: Processes entries during recovery
- **JournalReplayPoint**: Checkpoint for recovery start

### Contracts

**Public API**:
- `Journal.write(JournalEntry)` - Persist single entry
- `Journal.writeBatch(List<JournalEntry>)` - Persist batch atomically
- `Journal.replay(JournalReplayPoint)` - Recover from checkpoint
- `Journal.rotate()` - Rotate to new region
- `JournalReplayHandler.handle(JournalEntry)` - Process entry during recovery

**Durability Contract**:
- All entries MUST persist to disk before acknowledgment
- All entries MUST have unique monotonic sequence numbers
- Recovery MUST reconstruct all committed operations
- Incomplete writes MUST be detected and ignored

### Quickstart

See [quickstart.md](file:///home/wisefox/git/hyperkvstore/specs/004-lsmplus-journal/quickstart.md).

## Phase 2: Implementation Tasks

*Tasks will be generated by `/speckit.tasks` command.*

## Dependencies

**Internal Dependencies**:
- lsmplus-api (IndexKey, IndexValue, JournalEntryProto)
- lsmplus-storage (Chunk, SegmentLocation for journal regions)

**External Dependencies**:
- protobuf-java 3.34.1
- JUnit 6.0.0
- Mockito 5.11.0

## Risks & Mitigations

| Risk | Impact | Mitigation |
|------|--------|------------|
| Journal disk full | High | Monitor usage, rotate early, alert |
| Recovery performance | Medium | Use checkpoints, parallel replay |
| Concurrent write conflicts | Medium | Serialize writes, use write queue |
| Corruption mid-write | High | CRC32 validation, ignore incomplete entries |

## Success Metrics

- ✅ Single write latency <1ms
- ✅ Batch throughput >100K ops/s
- ✅ Recovery time <5s for 1M entries
- ✅ Zero data loss for committed operations
- ✅ 100% recovery accuracy
- ✅ Journal overhead <10%
