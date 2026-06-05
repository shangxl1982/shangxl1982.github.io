# Tasks: LSM Plus KV Service Layer

**Input**: Design documents from `/specs/010-lsmplus-service/`
**Prerequisites**: plan.md, spec.md, lsmplus-core, lsmplus-api modules

**Tests**: Tests included following TDD approach.

## Format: `[ID] [P?] [Story] Description`

## Path Conventions

- Java source: `lsmplus-service/src/main/java/org/hyperkv/lsmplus/service/`
- Tests: `lsmplus-service/src/test/java/org/hyperkv/lsmplus/service/`

---

## Phase 1: Setup

- [x] T001 Create lsmplus-service module directory structure
- [x] T002 Configure build.gradle.kts with dependencies on lsmplus-core
- [x] T003 [P] Setup service configuration

---

## Phase 2: Foundational

**⚠️ CRITICAL**: Must complete before user stories

- [x] T004 Define KVRequest class in lsmplus-service/src/main/java/org/hyperkv/lsmplus/service/KVRequest.java
- [x] T005 [P] Define KVResponse class in lsmplus-service/src/main/java/org/hyperkv/lsmplus/service/KVResponse.java
- [x] T006 [P] Define BatchOperationItem class in lsmplus-service/src/main/java/org/hyperkv/lsmplus/service/BatchOperationItem.java
- [x] T007 [P] Define RequestContext class in lsmplus-service/src/main/java/org/hyperkv/lsmplus/service/RequestContext.java

---

## Phase 3: User Story 1 - Request/Response API (Priority: P1) 🎯 MVP

**Goal**: Simple request/response API for key-value operations

**Independent Test**: Send requests, verify correct responses returned

### Tests for US1

- [x] T008 [P] [US1] Create KVServiceTest.java

### Implementation for US1

- [x] T009 [US1] Implement KVService class in lsmplus-service/src/main/java/org/hyperkv/lsmplus/service/KVService.java
- [x] T010 [US1] Implement KVService.execute() method
- [x] T011 [US1] Add GET request handling
- [x] T012 [US1] Add PUT request handling
- [x] T013 [US1] Add DELETE request handling

**Checkpoint**: US1 complete - basic API operational

---

## Phase 4: User Story 2 - Batch Operation Support (Priority: P1)

**Goal**: Submit batch operations through service layer

**Independent Test**: Submit batch requests, verify all operations processed correctly

### Implementation for US2

- [x] T014 [US2] Implement KVService.executeBatch() method
- [x] T015 [US2] Add batch atomicity
- [x] T016 [US2] Add batch error handling

**Checkpoint**: US2 complete - batch operations operational

---

## Phase 5: User Story 3 - Error Handling and Responses (Priority: P1)

**Goal**: Clear error responses for failed operations

**Independent Test**: Trigger errors, verify appropriate error responses

### Implementation for US3

- [x] T017 [US3] Implement structured error responses
- [x] T018 [US3] Add error codes and messages
- [x] T019 [US3] Add error context details

**Checkpoint**: US3 complete - error handling operational

---

## Phase 6: User Story 4 - Request Validation (Priority: P2)

**Goal**: Validate requests before processing

**Independent Test**: Send invalid requests, verify rejection with errors

### Implementation for US4

- [x] T020 [US4] Implement request validation
- [x] T021 [US4] Add size limit enforcement
- [x] T022 [US4] Add null field validation

**Checkpoint**: US4 complete - validation operational

---

## Phase 7: User Story 5 - Request Context and Metadata (Priority: P2)

**Goal**: Pass context and metadata with requests

**Independent Test**: Send requests with context, verify proper handling

### Implementation for US5

- [x] T023 [US5] Add tenant ID support
- [x] T024 [US5] Add trace ID propagation
- [x] T025 [US5] Add user metadata support

**Checkpoint**: US5 complete - context support operational

---

## Phase 8: Polish

- [x] T026 [P] Add comprehensive Javadoc
- [x] T027 Run all tests (100% pass target)
- [x] T028 [P] Verify request processing <1ms
- [x] T029 Verify batch throughput >50K ops/s
- [x] T030 Create quickstart.md

---

## Dependencies

**Internal**: lsmplus-core, lsmplus-api
**External**: JUnit 6.0.0, Mockito 5.17.0

## Parallel Execution

- Phase 2: T005-T007 can run in parallel
- Phase 3: T008 can run in parallel
- Phase 8: T026, T028 can run in parallel

## MVP Scope

**MVP**: Phase 1, Phase 2, Phase 3 (US1), Phase 4 (US2), Phase 5 (US3)
- Delivers basic service layer with error handling
- Enables application integration

## Task Summary

- **Total Tasks**: 30
- **Setup**: 3 tasks
- **Foundational**: 4 tasks
- **US1 (Request/Response API)**: 6 tasks (MVP)
- **US2 (Batch Operations)**: 3 tasks (MVP)
- **US3 (Error Handling)**: 3 tasks (MVP)
- **US4 (Validation)**: 3 tasks
- **US5 (Context)**: 3 tasks
- **Polish**: 5 tasks
- **Parallel Opportunities**: 8 tasks
