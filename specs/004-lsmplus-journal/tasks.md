# Tasks: LSM Plus Journal (Write-Ahead Log)

**Input**: Design documents from `/specs/004-lsmplus-journal/`
**Prerequisites**: plan.md, spec.md, lsmplus-api, lsmplus-storage modules

**Tests**: Tests included following TDD approach.

## Format: `[ID] [P?] [Story] Description`

## Path Conventions

- Java source: `lsmplus-journal/src/main/java/org/hyperkv/lsmplus/journal/`
- Tests: `lsmplus-journal/src/test/java/org/hyperkv/lsmplus/journal/`

---

## Phase 1: Setup

- [x] T001 Create lsmplus-journal module directory structure
- [x] T002 Configure build.gradle.kts with dependencies on lsmplus-api and lsmplus-storage
- [x] T003 [P] Setup journal directory configuration

---

## Phase 2: Foundational

**⚠️ CRITICAL**: Must complete before user stories

- [x] T004 Define JournalEntryType enum
- [x] T005 [P] Implement JournalReplayException class
- [x] T006 [P] Define journal file format constants

---

## Phase 3: User Story 1 - Durable Write-Ahead Logging (Priority: P1) 🎯 MVP

**Goal**: Persist all operations to journal before acknowledgment

**Independent Test**: Write operations to journal, simulate crash, verify recovery

### Tests for US1

- [x] T007 [P] [US1] Create JournalTest.java
- [x] T008 [P] [US1] Create JournalWriterTest.java

### Implementation for US1

- [x] T009 [P] [US1] Implement JournalEntry class in lsmplus-journal/src/main/java/org/hyperkv/lsmplus/journal/JournalEntry.java
- [x] T010 [P] [US1] Implement JournalWriter class in lsmplus-journal/src/main/java/org/hyperkv/lsmplus/journal/JournalWriter.java
- [x] T011 [US1] Implement Journal.write() method in lsmplus-journal/src/main/java/org/hyperkv/lsmplus/journal/Journal.java
- [x] T012 [US1] Add CRC32 checksum to journal entries
- [x] T013 [US1] Implement fsync for durability

**Checkpoint**: US1 complete - basic journal write operational

---

## Phase 4: User Story 2 - Crash Recovery and Replay (Priority: P1)

**Goal**: Replay journal entries after crash to recover consistent state

**Independent Test**: Write operations, kill process, restart, verify all committed operations recovered

### Tests for US2

- [x] T014 [P] [US2] Create JournalReplayTest.java

### Implementation for US2

- [x] T015 [P] [US2] Implement JournalReplayHandler interface in lsmplus-journal/src/main/java/org/hyperkv/lsmplus/journal/JournalReplayHandler.java
- [x] T016 [P] [US2] Implement JournalReplayPoint class in lsmplus-journal/src/main/java/org/hyperkv/lsmplus/journal/JournalReplayPoint.java
- [x] T017 [US2] Implement Journal.replay() method
- [x] T018 [US2] Add incomplete write detection
- [x] T019 [US2] Implement chronological replay ordering

**Checkpoint**: US2 complete - crash recovery operational

---

## Phase 5: User Story 3 - Journal Region Management (Priority: P2)

**Goal**: Rotate and archive journal regions for space management

**Independent Test**: Create multiple regions, rotate, verify old regions can be archived

### Tests for US3

- [x] T020 [P] [US3] Create JournalRegionTest.java
- [x] T021 [P] [US3] Create JournalRegionIndexFileTest.java

### Implementation for US3

- [x] T022 [P] [US3] Implement JournalRegion class in lsmplus-journal/src/main/java/org/hyperkv/lsmplus/journal/JournalRegion.java
- [x] T023 [P] [US3] Implement JournalRegionIndexFile class in lsmplus-journal/src/main/java/org/hyperkv/lsmplus/journal/JournalRegionIndexFile.java
- [x] T024 [US3] Implement Journal.rotate() method
- [x] T025 [US3] Implement region archiving
- [x] T026 [US3] Add region compaction

**Checkpoint**: US3 complete - region management operational

---

## Phase 6: User Story 4 - Batch Write Optimization (Priority: P2)

**Goal**: Batch writes with single fsync for efficiency

**Independent Test**: Write batches, verify single disk sync

### Tests for US4

- [x] T027 [P] [US4] Create JournalBatchWriteTest.java

### Implementation for US4

- [x] T028 [US4] Implement Journal.writeBatch() method
- [x] T029 [US4] Add batch atomicity guarantees
- [x] T030 [US4] Optimize batch serialization

**Checkpoint**: US4 complete - batch optimization operational

---

## Phase 7: Polish

- [x] T031 [P] Add comprehensive Javadoc
- [x] T032 Run all tests (100% pass target)
- [x] T033 [P] Run performance benchmarks (>100K ops/s batch target)
- [x] T034 Verify recovery time <5s for 1M entries
- [x] T035 Create quickstart.md

---

## Dependencies

**Internal**: lsmplus-api, lsmplus-storage
**External**: JUnit 6.0.0, Mockito 5.17.0

## Parallel Execution

- Phase 2: T005-T006 can run in parallel
- Phase 3: T007-T008, T009-T010 can run in parallel
- Phase 4: T015-T016 can run in parallel
- Phase 5: T020-T021, T022-T023 can run in parallel
- Phase 7: T031, T033 can run in parallel

## MVP Scope

**MVP**: Phase 1, Phase 2, Phase 3 (US1), Phase 4 (US2)
- Delivers core journal with crash recovery
- Enables durable writes for entire system

## Task Summary

- **Total Tasks**: 35
- **Setup**: 3 tasks
- **Foundational**: 3 tasks
- **US1 (Durable Logging)**: 7 tasks (MVP)
- **US2 (Crash Recovery)**: 6 tasks (MVP)
- **US3 (Region Management)**: 7 tasks
- **US4 (Batch Optimization)**: 4 tasks
- **Polish**: 5 tasks
- **Parallel Opportunities**: 15 tasks
