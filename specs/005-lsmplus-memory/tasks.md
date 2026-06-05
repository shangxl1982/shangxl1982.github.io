# Tasks: LSM Plus Memory Table Management

**Input**: Design documents from `/specs/005-lsmplus-memory/`
**Prerequisites**: plan.md, spec.md, lsmplus-api module

**Tests**: Tests included following TDD approach.

## Format: `[ID] [P?] [Story] Description`

## Path Conventions

- Java source: `lsmplus-memory/src/main/java/org/hyperkv/lsmplus/memory/`
- Tests: `lsmplus-memory/src/test/java/org/hyperkv/lsmplus/memory/`

---

## Phase 1: Setup

- [x] T001 Create lsmplus-memory module directory structure
- [x] T002 Configure build.gradle.kts with dependencies on lsmplus-api
- [x] T003 [P] Setup memory configuration

---

## Phase 2: Foundational

**⚠️ CRITICAL**: Must complete before user stories

- [x] T004 Define DumpCallback interface in lsmplus-memory/src/main/java/org/hyperkv/lsmplus/memory/DumpCallback.java
- [x] T005 [P] Define memory table constants (default size limit)

---

## Phase 3: User Story 1 - In-Memory Sorted Table (Priority: P1) 🎯 MVP

**Goal**: In-memory sorted table for high write throughput

**Independent Test**: Write key-value pairs, verify sorted order

### Tests for US1

- [x] T006 [P] [US1] Create MemoryTableTest.java

### Implementation for US1

- [x] T007 [US1] Implement MemoryTable class with TreeMap in lsmplus-memory/src/main/java/org/hyperkv/lsmplus/memory/MemoryTable.java
- [x] T008 [US1] Implement MemoryTable.put() method
- [x] T009 [US1] Implement MemoryTable.get() method
- [x] T010 [US1] Implement MemoryTable.delete() method
- [x] T011 [US1] Implement MemoryTable.iterator() for sorted traversal
- [x] T012 [US1] Add size tracking and capacity management

**Checkpoint**: US1 complete - basic memtable operational

---

## Phase 4: User Story 2 - Tombstone Support (Priority: P1)

**Goal**: Mark keys as deleted using tombstones

**Independent Test**: Write tombstones, verify correct identification during reads

### Tests for US2

- [x] T013 [P] [US2] Create TombstoneSupportTest.java

### Implementation for US2

- [x] T014 [US2] Add tombstone handling to MemoryTable.put()
- [x] T015 [US2] Add tombstone detection to MemoryTable.get()
- [x] T016 [US2] Implement tombstone replacement on update

**Checkpoint**: US2 complete - tombstone support operational

---

## Phase 5: User Story 3 - Sealing Mechanism (Priority: P1)

**Goal**: Seal memtable when capacity reached for flushing

**Independent Test**: Fill memtable, seal, verify immutability

### Tests for US3

- [x] T017 [P] [US3] Create SealMechanismTest.java

### Implementation for US3

- [x] T018 [US3] Implement MemoryTable.seal() method
- [x] T019 [US3] Add immutability enforcement after seal
- [x] T020 [US3] Add seal status tracking
- [x] T021 [US3] Trigger DumpCallback on seal

**Checkpoint**: US3 complete - sealing mechanism operational

---

## Phase 6: User Story 4 - Memory Table Manager (Priority: P2)

**Goal**: Manage multiple memtables (active and sealed)

**Independent Test**: Create multiple memtables, seal some, verify manager tracking

### Tests for US4

- [x] T022 [P] [US4] Create MemoryTableManagerTest.java
- [x] T023 [P] [US4] Create MemoryTableIntegrationTest.java

### Implementation for US4

- [x] T024 [US4] Implement MemoryTableManager class in lsmplus-memory/src/main/java/org/hyperkv/lsmplus/memory/MemoryTableManager.java
- [x] T025 [US4] Implement MemoryTableManager.getActiveTable() method
- [x] T026 [US4] Implement MemoryTableManager.getSealedTables() method
- [x] T027 [US4] Implement automatic memtable creation on seal
- [x] T028 [US4] Add sealed table cleanup after flush

**Checkpoint**: US4 complete - manager coordination operational

---

## Phase 7: Polish

- [x] T029 [P] Add comprehensive Javadoc
- [x] T030 Run all tests (100% pass target)
- [x] T031 [P] Run performance benchmarks (>1M ops/s write target)
- [x] T032 Verify memory overhead <20%
- [x] T033 Create quickstart.md

---

## Dependencies

**Internal**: lsmplus-api
**External**: JUnit 6.0.0, Mockito 5.17.0

## Parallel Execution

- Phase 3: T006 can run in parallel
- Phase 4: T013 can run in parallel
- Phase 5: T017 can run in parallel
- Phase 6: T022-T023 can run in parallel
- Phase 7: T029, T031 can run in parallel

## MVP Scope

**MVP**: Phase 1, Phase 2, Phase 3 (US1), Phase 4 (US2), Phase 5 (US3)
- Delivers core memtable with sealing
- Enables write buffering for entire system

## Task Summary

- **Total Tasks**: 33
- **Setup**: 3 tasks
- **Foundational**: 2 tasks
- **US1 (Sorted Table)**: 7 tasks (MVP)
- **US2 (Tombstone)**: 4 tasks (MVP)
- **US3 (Sealing)**: 5 tasks (MVP)
- **US4 (Manager)**: 7 tasks
- **Polish**: 5 tasks
- **Parallel Opportunities**: 10 tasks
