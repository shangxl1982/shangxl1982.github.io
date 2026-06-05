# Tasks: LSM Plus Utility Classes

**Input**: Design documents from `/specs/012-lsmplus-utils/`
**Prerequisites**: plan.md, spec.md

**Tests**: Tests included following TDD approach.

## Format: `[ID] [P?] [Story] Description`

## Path Conventions

- Java source: `lsmplus-utils/src/main/java/org/hyperkv/lsmplus/utils/`
- Tests: `lsmplus-utils/src/test/java/org/hyperkv/lsmplus/utils/`

---

## Phase 1: Setup

- [x] T001 Create lsmplus-utils module directory structure
- [x] T002 Configure build.gradle.kts
- [x] T003 [P] Setup utility configuration

---

## Phase 2: Foundational

**⚠️ CRITICAL**: Must complete before user stories

- [x] T004 Define LSMException base class in lsmplus-utils/src/main/java/org/hyperkv/lsmplus/utils/LSMException.java

---

## Phase 3: User Story 1 - Byte Array Utilities (Priority: P1) 🎯 MVP

**Goal**: Utility functions for byte array operations

**Independent Test**: Perform byte array operations, verify correctness

### Tests for US1

- [x] T005 [P] [US1] Create ByteArrayUtilsTest.java

### Implementation for US1

- [x] T006 [P] [US1] Implement ByteArrayUtils class in lsmplus-utils/src/main/java/org/hyperkv/lsmplus/utils/ByteArrayUtils.java
- [x] T007 [US1] Implement ByteArrayUtils.compare() method
- [x] T008 [US1] Implement ByteArrayUtils.concat() method
- [x] T009 [US1] Implement ByteArrayUtils.toHex() method
- [x] T010 [US1] Implement ByteArrayUtils.fromHex() method

**Checkpoint**: US1 complete - byte array utilities operational

---

## Phase 4: User Story 2 - Checksum Utilities (Priority: P1)

**Goal**: Checksum utilities (CRC32, MD5, SHA-256)

**Independent Test**: Compute checksums, verify corruption detection

### Tests for US2

- [x] T011 [P] [US2] Create ChecksumUtilsTest.java

### Implementation for US2

- [x] T012 [P] [US2] Implement ChecksumUtils class in lsmplus-utils/src/main/java/org/hyperkv/lsmplus/utils/ChecksumUtils.java
- [x] T013 [US2] Implement ChecksumUtils.crc32() method
- [x] T014 [US2] Implement ChecksumUtils.md5() method
- [x] T015 [US2] Implement ChecksumUtils.sha256() method

**Checkpoint**: US2 complete - checksum utilities operational

---

## Phase 5: User Story 3 - Exception Handling Utilities (Priority: P2)

**Goal**: Custom exception classes and error handling

**Independent Test**: Throw exceptions, verify proper formatting

### Implementation for US3

- [x] T016 [US3] Add error codes to LSMException
- [x] T017 [US3] Add context information to exceptions
- [x] T018 [US3] Add exception wrapping utilities

**Checkpoint**: US3 complete - exception utilities operational

---

## Phase 6: User Story 4 - Thread and Concurrency Utilities (Priority: P2)

**Goal**: Thread utilities (thread pools, locks, synchronization)

**Independent Test**: Use thread utilities in concurrent scenarios, verify correct behavior

### Tests for US4

- [x] T019 [P] [US4] Create ThreadUtilsTest.java

### Implementation for US4

- [x] T020 [P] [US4] Implement ThreadUtils class in lsmplus-utils/src/main/java/org/hyperkv/lsmplus/utils/ThreadUtils.java
- [x] T021 [US4] Implement thread pool utilities
- [x] T022 [US4] Implement lock utilities

**Checkpoint**: US4 complete - thread utilities operational

---

## Phase 7: User Story 5 - I/O Utilities (Priority: P2)

**Goal**: I/O utilities for file operations

**Independent Test**: Perform file operations, verify correct behavior

### Tests for US5

- [x] T023 [P] [US5] Create IOUtilsTest.java

### Implementation for US5

- [x] T024 [P] [US5] Implement IOUtils class in lsmplus-utils/src/main/java/org/hyperkv/lsmplus/utils/IOUtils.java
- [x] T025 [US5] Implement IOUtils.readFile() method
- [x] T026 [US5] Implement IOUtils.writeFile() method
- [x] T027 [US5] Add streaming support for large files

**Checkpoint**: US5 complete - I/O utilities operational

---

## Phase 8: User Story 6 - Time and Date Utilities (Priority: P3)

**Goal**: Time utilities for timestamp handling

**Independent Test**: Generate, format, parse timestamps, verify correctness

### Implementation for US6

- [x] T028 [P] [US6] Implement TimeUtils class in lsmplus-utils/src/main/java/org/hyperkv/lsmplus/utils/TimeUtils.java
- [x] T029 [US6] Implement TimeUtils.currentTimestamp() method
- [x] T030 [US6] Implement timestamp formatting
- [x] T031 [US6] Implement timestamp parsing

**Checkpoint**: US6 complete - time utilities operational

---

## Phase 9: Polish

- [x] T032 [P] Add comprehensive Javadoc
- [x] T033 Run all tests (100% pass target)
- [x] T034 [P] Verify byte comparison <100ns/KB
- [x] T035 Verify CRC32 <1ms/MB
- [x] T036 Create quickstart.md

---

## Dependencies

**External**: JUnit 6.0.0, Mockito 5.17.0

## Parallel Execution

- Phase 3: T005-T006 can run in parallel
- Phase 4: T011-T012 can run in parallel
- Phase 6: T019-T020 can run in parallel
- Phase 7: T023-T024 can run in parallel
- Phase 8: T028 can run in parallel
- Phase 9: T032, T034 can run in parallel

## MVP Scope

**MVP**: Phase 1, Phase 2, Phase 3 (US1), Phase 4 (US2)
- Delivers core utility functions
- Enables other modules to use common utilities

## Task Summary

- **Total Tasks**: 36
- **Setup**: 3 tasks
- **Foundational**: 1 task
- **US1 (Byte Array)**: 6 tasks (MVP)
- **US2 (Checksum)**: 5 tasks (MVP)
- **US3 (Exception)**: 3 tasks
- **US4 (Thread)**: 4 tasks
- **US5 (I/O)**: 5 tasks
- **US6 (Time)**: 4 tasks
- **Polish**: 5 tasks
- **Parallel Opportunities**: 12 tasks
