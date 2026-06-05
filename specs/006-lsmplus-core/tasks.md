# Tasks: LSM Plus Core KV Store

**Input**: Design documents from `/specs/006-lsmplus-core/`
**Prerequisites**: plan.md, spec.md, lsmplus-api, lsmplus-memory, lsmplus-journal, lsmplus-bplustree modules

**Tests**: Tests included following TDD approach.

## Format: `[ID] [P?] [Story] Description`

## Path Conventions

- Java source: `lsmplus-core/src/main/java/org/hyperkv/lsmplus/core/`
- Tests: `lsmplus-core/src/test/java/org/hyperkv/lsmplus/core/`

---

## Phase 1: Setup

- [x] T001 Create lsmplus-core module directory structure
- [x] T002 Configure build.gradle.kts with dependencies on all core modules
- [x] T003 [P] Setup core configuration

---

## Phase 2: Foundational

**⚠️ CRITICAL**: Must complete before user stories

- [x] T004 Define KVStoreState enum in lsmplus-core/src/main/java/org/hyperkv/lsmplus/core/KVStoreState.java
- [x] T005 [P] Implement LockManager class in lsmplus-core/src/main/java/org/hyperkv/lsmplus/core/concurrency/LockManager.java
- [x] T006 [P] Implement WriteRequest class in lsmplus-core/src/main/java/org/hyperkv/lsmplus/core/concurrency/WriteRequest.java
- [x] T007 [P] Implement WriteRequestQueue class in lsmplus-core/src/main/java/org/hyperkv/lsmplus/core/concurrency/WriteRequestQueue.java

---

## Phase 3: User Story 1 - Basic Key-Value Operations (Priority: P1) 🎯 MVP

**Goal**: Perform basic GET, PUT, DELETE operations

**Independent Test**: Perform operations and verify correct behavior

### Tests for US1

- [x] T008 [P] [US1] Create KVStoreTest.java

### Implementation for US1

- [x] T009 [US1] Implement KVStore class in lsmplus-core/src/main/java/org/hyperkv/lsmplus/core/KVStore.java
- [x] T010 [US1] Implement KVStore.get() method
- [x] T011 [US1] Implement KVStore.put() method
- [x] T012 [US1] Implement KVStore.delete() method
- [x] T013 [US1] Integrate with MemoryTableManager
- [x] T014 [US1] Integrate with Journal for durability

**Checkpoint**: US1 complete - basic operations operational

---

## Phase 4: User Story 2 - Snapshot Reads (Priority: P1)

**Goal**: Read from consistent point-in-time snapshot

**Independent Test**: Create snapshot, perform writes, verify snapshot doesn't see new writes

### Tests for US2

- [x] T015 [P] [US2] Create SnapshotTest.java in lsmplus-core/src/test/java/org/hyperkv/lsmplus/core/concurrency/

### Implementation for US2

- [x] T016 [P] [US2] Implement Snapshot class in lsmplus-core/src/main/java/org/hyperkv/lsmplus/core/concurrency/Snapshot.java
- [x] T017 [US2] Implement KVStore.createSnapshot() method
- [x] T018 [US2] Add snapshot isolation for reads

**Checkpoint**: US2 complete - snapshot reads operational

---

## Phase 5: User Story 3 - Batch Operations (Priority: P2)

**Goal**: Perform multiple operations in single batch

**Independent Test**: Submit batches, verify atomic application

### Tests for US3

- [x] T019 [P] [US3] Create BatchOperationTest.java

### Implementation for US3

- [x] T020 [P] [US3] Implement BatchOperation class in lsmplus-core/src/main/java/org/hyperkv/lsmplus/core/BatchOperation.java
- [x] T021 [P] [US3] Implement BatchWriter class in lsmplus-core/src/main/java/org/hyperkv/lsmplus/core/concurrency/BatchWriter.java
- [x] T022 [US3] Implement KVStore.batch() method
- [x] T023 [US3] Add atomic batch execution

**Checkpoint**: US3 complete - batch operations operational

---

## Phase 6: User Story 4 - Concurrency Control (Priority: P2)

**Goal**: Proper concurrency control with locking

**Independent Test**: Perform concurrent operations, verify data consistency

### Tests for US4

- [x] T024 [P] [US4] Create LockManagerTest.java
- [x] T025 [P] [US4] Create WriteRequestQueueTest.java

### Implementation for US4

- [x] T026 [US4] Implement key-level locking in LockManager
- [x] T027 [US4] Add read-write lock support
- [x] T028 [US4] Integrate locking with KVStore operations

**Checkpoint**: US4 complete - concurrency control operational

---

## Phase 7: User Story 5 - Recovery and State Management (Priority: P2)

**Goal**: Recover state after restart, track operational state

**Independent Test**: Write data, restart, verify all data recovered

### Tests for US5

- [x] T029 [P] [US5] Create RecoveryHandlerTest.java

### Implementation for US5

- [x] T030 [P] [US5] Implement RecoveryHandler class in lsmplus-core/src/main/java/org/hyperkv/lsmplus/core/RecoveryHandler.java
- [x] T031 [US5] Implement KVStore recovery from journal
- [x] T032 [US5] Add state tracking (INITIALIZING, READY, ERROR, CLOSED)
- [x] T033 [US5] Implement graceful shutdown

**Checkpoint**: US5 complete - recovery and state management operational

---

## Phase 8: Polish

- [x] T034 [P] Add comprehensive Javadoc
- [x] T035 Run all tests (100% pass target)
- [x] T036 [P] Run performance benchmarks (>100K ops/s batch target)
- [x] T037 Verify recovery time <10s for 1M entries
- [x] T038 Create quickstart.md

---

## Dependencies

**Internal**: lsmplus-api, lsmplus-memory, lsmplus-journal, lsmplus-bplustree
**External**: JUnit 6.0.0, Mockito 5.17.0

## Parallel Execution

- Phase 2: T005-T007 can run in parallel
- Phase 3: T008 can run in parallel
- Phase 4: T015-T016 can run in parallel
- Phase 5: T019-T021 can run in parallel
- Phase 6: T024-T025 can run in parallel
- Phase 7: T029-T030 can run in parallel
- Phase 8: T034, T036 can run in parallel

## MVP Scope

**MVP**: Phase 1, Phase 2, Phase 3 (US1), Phase 4 (US2)
- Delivers core KV operations with snapshots
- Enables basic application functionality

## Task Summary

- **Total Tasks**: 38
- **Setup**: 3 tasks
- **Foundational**: 4 tasks
- **US1 (Basic Operations)**: 7 tasks (MVP)
- **US2 (Snapshots)**: 4 tasks (MVP)
- **US3 (Batch Operations)**: 5 tasks
- **US4 (Concurrency)**: 5 tasks
- **US5 (Recovery)**: 5 tasks
- **Polish**: 5 tasks
- **Parallel Opportunities**: 17 tasks
