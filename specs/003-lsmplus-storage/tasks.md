# Tasks: LSM Plus Storage Layer

**Input**: Design documents from `/specs/003-lsmplus-storage/`
**Prerequisites**: plan.md, spec.md, lsmplus-api module

**Tests**: Tests are included following TDD approach.

**Organization**: Tasks grouped by user story for independent implementation.

## Format: `[ID] [P?] [Story] Description`

## Path Conventions

- Java source: `lsmplus-storage/src/main/java/org/hyperkv/lsmplus/storage/`
- Tests: `lsmplus-storage/src/test/java/org/hyperkv/lsmplus/storage/`

---

## Phase 1: Setup

- [x] T001 Create lsmplus-storage module directory structure
- [x] T002 Configure build.gradle.kts with dependencies on lsmplus-api
- [x] T003 [P] Setup storage directory configuration in build.gradle.kts

---

## Phase 2: Foundational (Core Infrastructure)

**⚠️ CRITICAL**: Must complete before user stories

- [x] T004 Define ChunkStatus enum in lsmplus-storage/src/main/java/org/hyperkv/lsmplus/storage/ChunkStatus.java
- [x] T005 [P] Define StorageLayout constants for directory structure
- [x] T006 [P] Implement CRC32 checksum utility methods
- [x] T007 [P] Implement magic number constants for chunk identification

---

## Phase 3: User Story 1 - Chunk-Based Append-Only Storage (Priority: P1) 🎯 MVP

**Goal**: Append-only chunk-based storage for optimal performance and crash consistency

**Independent Test**: Create chunks, append data sequentially, verify all data is persisted correctly

### Tests for US1

- [x] T008 [P] [US1] Create ChunkTest.java in lsmplus-storage/src/test/java/org/hyperkv/lsmplus/storage/
- [x] T009 [P] [US1] Create ChunkHeaderTest.java in lsmplus-storage/src/test/java/org/hyperkv/lsmplus/storage/

### Implementation for US1

- [x] T010 [P] [US1] Implement ChunkHeader class with metadata fields in lsmplus-storage/src/main/java/org/hyperkv/lsmplus/storage/ChunkHeader.java
- [x] T011 [P] [US1] Implement Chunk class with append() method in lsmplus-storage/src/main/java/org/hyperkv/lsmplus/storage/Chunk.java
- [x] T012 [US1] Implement Chunk.seal() method for immutability
- [x] T013 [US1] Implement Chunk.read() method for data retrieval
- [x] T014 [US1] Add CRC32 checksum validation to Chunk operations
- [x] T015 [US1] Add magic number validation to Chunk header

**Checkpoint**: US1 complete - basic chunk storage operational

---

## Phase 4: User Story 2 - Segment Location Management (Priority: P1)

**Goal**: Reference persisted data using segment locations (chunkId, offset, length)

**Independent Test**: Create segment locations, persist data, verify retrieval using location

### Tests for US2

- [x] T016 [P] [US2] Create SegmentLocationTest.java

### Implementation for US2

- [x] T017 [P] [US2] Implement SegmentLocation class with chunkId, offset, length fields in lsmplus-storage/src/main/java/org/hyperkv/lsmplus/storage/SegmentLocation.java
- [x] T018 [US2] Implement SegmentLocation validation methods
- [x] T019 [US2] Add SegmentLocation serialization to protobuf

**Checkpoint**: US2 complete - segment location references ready

---

## Phase 5: User Story 3 - Chunk Lifecycle Management (Priority: P2)

**Goal**: Track chunk status (OPEN, SEALED, DELETING, DELETED) for GC

**Independent Test**: Create chunks, transition through states, verify valid transitions

### Tests for US3

- [x] T020 [P] [US3] Create ChunkManagerTest.java
- [x] T021 [P] [US3] Create ChunkLifecycleTest.java

### Implementation for US3

- [x] T022 [US3] Implement ChunkManager.createChunk() method in lsmplus-storage/src/main/java/org/hyperkv/lsmplus/storage/ChunkManager.java
- [x] T023 [US3] Implement ChunkManager.sealChunk() method
- [x] T024 [US3] Implement ChunkManager.deleteChunk() method
- [x] T025 [US3] Implement ChunkManager.getChunk() method
- [x] T026 [US3] Add chunk status tracking and persistence

**Checkpoint**: US3 complete - chunk lifecycle management ready

---

## Phase 6: User Story 4 - Data Integrity and Recovery (Priority: P2)

**Goal**: CRC32 checksums and magic numbers for data integrity

**Independent Test**: Write data with checksums, corrupt data, verify detection

### Tests for US4

- [x] T027 [P] [US4] Create DataIntegrityIntegrationTest.java

### Implementation for US4

- [x] T028 [US4] Implement checksum validation on Chunk.read()
- [x] T029 [US4] Implement integrity error reporting with details
- [x] T030 [US4] Add recovery mechanisms for corrupted chunks

**Checkpoint**: US4 complete - data integrity validation operational

---

## Phase 7: Polish

- [x] T031 [P] Add comprehensive Javadoc
- [x] T032 Run all tests and verify 100% pass
- [x] T033 [P] Run performance benchmarks (>500 MB/s write target)
- [x] T034 Verify zero data loss in 1M operations
- [x] T035 Create quickstart.md

---

## Dependencies

**Internal**: lsmplus-api (IndexKey, IndexValue, protobuf messages)
**External**: JUnit 6.0.0, Mockito 5.17.0

## Parallel Execution

- Phase 2: T005-T007 can run in parallel
- Phase 3: T008-T009, T010-T011 can run in parallel
- Phase 5: T020-T021 can run in parallel
- Phase 7: T031, T033 can run in parallel

## MVP Scope

**Minimum Viable Product**: Phase 1, Phase 2, Phase 3 (US1), Phase 4 (US2)
- Delivers core chunk storage with segment location management
- Enables other modules to persist and retrieve data

## Task Summary

- **Total Tasks**: 35
- **Phase 1 (Setup)**: 3 tasks
- **Phase 2 (Foundational)**: 4 tasks
- **Phase 3 (US1 - Chunk Storage)**: 8 tasks (MVP)
- **Phase 4 (US2 - Segment Location)**: 4 tasks (MVP)
- **Phase 5 (US3 - Lifecycle)**: 7 tasks
- **Phase 6 (US4 - Integrity)**: 4 tasks
- **Phase 7 (Polish)**: 5 tasks
- **Parallel Opportunities**: 15 tasks
