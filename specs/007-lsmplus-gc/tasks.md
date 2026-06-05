# Tasks: LSM Plus Garbage Collection

**Input**: Design documents from `/specs/007-lsmplus-gc/`
**Prerequisites**: plan.md, spec.md, lsmplus-api, lsmplus-storage, lsmplus-bplustree modules

**Tests**: Tests included following TDD approach.

## Format: `[ID] [P?] [Story] Description`

## Path Conventions

- Java source: `lsmplus-gc/src/main/java/org/hyperkv/lsmplus/gc/`
- Tests: `lsmplus-gc/src/test/java/org/hyperkv/lsmplus/gc/`

---

## Phase 1: Setup

- [x] T001 Create lsmplus-gc module directory structure
- [x] T002 Configure build.gradle.kts with dependencies
- [x] T003 [P] Setup GC configuration

---

## Phase 2: Foundational

**⚠️ CRITICAL**: Must complete before user stories

- [x] T004 Define GCConfig class in lsmplus-gc/src/main/java/org/hyperkv/lsmplus/gc/GCConfig.java
- [x] T005 [P] Define GCStrategy interface in lsmplus-gc/src/main/java/org/hyperkv/lsmplus/gc/GCStrategy.java
- [x] T006 [P] Define GCResult class in lsmplus-gc/src/main/java/org/hyperkv/lsmplus/gc/GCResult.java

---

## Phase 3: User Story 1 - Obsolete Data Detection (Priority: P1) 🎯 MVP

**Goal**: Identify obsolete chunks and pages

**Independent Test**: Create chunks, mark obsolete, verify GC identifies them

### Tests for US1

- [x] T007 [P] [US1] Create GarbageCollectorTest.java

### Implementation for US1

- [x] T008 [US1] Implement GarbageCollector class in lsmplus-gc/src/main/java/org/hyperkv/lsmplus/gc/GarbageCollector.java
- [x] T009 [US1] Implement obsolete chunk detection
- [x] T010 [US1] Implement obsolete page detection
- [x] T011 [US1] Add reference tracking integration

**Checkpoint**: US1 complete - obsolete detection operational

---

## Phase 4: User Story 2 - Space Reclamation (Priority: P1)

**Goal**: Safely delete obsolete chunks

**Independent Test**: Run GC, verify disk space reclaimed without data loss

### Tests for US2

- [x] T012 [P] [US2] Create GCResultTest.java

### Implementation for US2

- [x] T013 [US2] Implement GarbageCollector.run() method
- [x] T014 [US2] Implement chunk deletion with safety checks
- [x] T015 [US2] Add deletion result reporting

**Checkpoint**: US2 complete - space reclamation operational

---

## Phase 5: User Story 3 - Occupancy Tracking (Priority: P2)

**Goal**: Track chunk occupancy rates

**Independent Test**: Write/delete data, verify occupancy rates calculated correctly

### Tests for US3

- [x] T016 [P] [US3] Create OccupancyTrackerTest.java

### Implementation for US3

- [x] T017 [P] [US3] Implement OccupancyTracker class in lsmplus-gc/src/main/java/org/hyperkv/lsmplus/gc/OccupancyTracker.java
- [x] T018 [US3] Implement occupancy calculation
- [x] T019 [US3] Add low-occupancy chunk identification

**Checkpoint**: US3 complete - occupancy tracking operational

---

## Phase 6: User Story 4 - Compaction Strategy (Priority: P2)

**Goal**: Compact low-occupancy chunks

**Independent Test**: Trigger compaction, verify valid data rewritten efficiently

### Implementation for US4

- [x] T020 [US4] Implement GarbageCollector.compact() method
- [x] T021 [US4] Add compaction coordination with writes
- [x] T022 [US4] Implement old chunk cleanup after compaction

**Checkpoint**: US4 complete - compaction operational

---

## Phase 7: User Story 5 - MNS Tracking (Priority: P2)

**Goal**: Track minimum not-sealed sequence number

**Independent Test**: Advance MNS, verify old journal entries identified for cleanup

### Tests for US5

- [x] T023 [P] [US5] Create MNSTrackerTest.java

### Implementation for US5

- [x] T024 [P] [US5] Implement MNSTracker class in lsmplus-gc/src/main/java/org/hyperkv/lsmplus/gc/MNSTracker.java
- [x] T025 [US5] Implement MNS update on memtable flush
- [x] T026 [US5] Add journal cleanup integration

**Checkpoint**: US5 complete - MNS tracking operational

---

## Phase 8: Polish

- [x] T027 [P] Add comprehensive Javadoc
- [x] T028 Run all tests (100% pass target)
- [x] T029 [P] Run performance benchmarks (>90% space reclaimed)
- [x] T030 Verify GC impact <5% on operation latency
- [x] T031 Create quickstart.md

---

## Dependencies

**Internal**: lsmplus-api, lsmplus-storage, lsmplus-bplustree
**External**: JUnit 6.0.0, Mockito 5.17.0

## Parallel Execution

- Phase 2: T005-T006 can run in parallel
- Phase 3: T007 can run in parallel
- Phase 4: T012 can run in parallel
- Phase 5: T016-T017 can run in parallel
- Phase 7: T023-T024 can run in parallel
- Phase 8: T027, T029 can run in parallel

## MVP Scope

**MVP**: Phase 1, Phase 2, Phase 3 (US1), Phase 4 (US2)
- Delivers basic GC with space reclamation
- Enables storage management

## Task Summary

- **Total Tasks**: 31
- **Setup**: 3 tasks
- **Foundational**: 3 tasks
- **US1 (Obsolete Detection)**: 5 tasks (MVP)
- **US2 (Space Reclamation)**: 4 tasks (MVP)
- **US3 (Occupancy Tracking)**: 4 tasks
- **US4 (Compaction)**: 3 tasks
- **US5 (MNS Tracking)**: 4 tasks
- **Polish**: 5 tasks
- **Parallel Opportunities**: 12 tasks
