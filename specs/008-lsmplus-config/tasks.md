# Tasks: LSM Plus Configuration Management

**Input**: Design documents from `/specs/008-lsmplus-config/`
**Prerequisites**: plan.md, spec.md

**Tests**: Tests included following TDD approach.

## Format: `[ID] [P?] [Story] Description`

## Path Conventions

- Java source: `lsmplus-config/src/main/java/org/hyperkv/lsmplus/config/`
- Tests: `lsmplus-config/src/test/java/org/hyperkv/lsmplus/config/`

---

## Phase 1: Setup

- [x] T001 Create lsmplus-config module directory structure
- [x] T002 Configure build.gradle.kts
- [x] T003 [P] Setup configuration file format

---

## Phase 2: Foundational

**⚠️ CRITICAL**: Must complete before user stories

- [x] T004 Define ConfigValidationException class in lsmplus-config/src/main/java/org/hyperkv/lsmplus/config/ConfigValidationException.java
- [x] T005 [P] Define ConfigChangeListener interface in lsmplus-config/src/main/java/org/hyperkv/lsmplus/config/ConfigChangeListener.java

---

## Phase 3: User Story 1 - Configuration Loading and Validation (Priority: P1) 🎯 MVP

**Goal**: Load configuration from files with validation

**Independent Test**: Create config files, verify loading and validation

### Tests for US1

- [x] T006 [P] [US1] Create ConfigManagerTest.java

### Implementation for US1

- [x] T007 [US1] Implement ConfigManager class in lsmplus-config/src/main/java/org/hyperkv/lsmplus/config/ConfigManager.java
- [x] T008 [US1] Implement ConfigManager.load() method
- [x] T009 [US1] Add validation rules
- [x] T010 [US1] Add default value support

**Checkpoint**: US1 complete - configuration loading operational

---

## Phase 4: User Story 2 - Runtime Configuration Changes (Priority: P1)

**Goal**: Change configuration at runtime without restart

**Independent Test**: Change config values, verify immediate effect

### Implementation for US2

- [x] T011 [US2] Implement ConfigManager.set() method
- [x] T012 [US2] Add runtime validation
- [x] T013 [US2] Add persistence for changes

**Checkpoint**: US2 complete - runtime changes operational

---

## Phase 5: User Story 3 - Configuration Change Notifications (Priority: P2)

**Goal**: Receive notifications when config values change

**Independent Test**: Register listeners, verify notifications received

### Implementation for US3

- [x] T014 [US3] Implement ConfigManager.addListener() method
- [x] T015 [US3] Implement notification delivery
- [x] T016 [US3] Add async notification support

**Checkpoint**: US3 complete - notifications operational

---

## Phase 6: User Story 4 - Configuration Validation Rules (Priority: P2)

**Goal**: Define validation rules for config values

**Independent Test**: Set invalid values, verify validation catches them

### Implementation for US4

- [x] T017 [US4] Implement range validation
- [x] T018 [US4] Implement required field validation
- [x] T019 [US4] Implement dependency validation

**Checkpoint**: US4 complete - validation rules operational

---

## Phase 7: Polish

- [x] T020 [P] Add comprehensive Javadoc
- [x] T021 Run all tests (100% pass target)
- [x] T022 [P] Verify config load <100ms
- [x] T023 Verify runtime change <10ms
- [x] T024 Create quickstart.md

---

## Dependencies

**External**: JUnit 6.0.0, Mockito 5.17.0

## Parallel Execution

- Phase 2: T005 can run in parallel
- Phase 3: T006 can run in parallel
- Phase 7: T020, T022 can run in parallel

## MVP Scope

**MVP**: Phase 1, Phase 2, Phase 3 (US1), Phase 4 (US2)
- Delivers basic configuration management
- Enables runtime configuration

## Task Summary

- **Total Tasks**: 24
- **Setup**: 3 tasks
- **Foundational**: 2 tasks
- **US1 (Loading)**: 5 tasks (MVP)
- **US2 (Runtime Changes)**: 3 tasks (MVP)
- **US3 (Notifications)**: 3 tasks
- **US4 (Validation Rules)**: 3 tasks
- **Polish**: 5 tasks
- **Parallel Opportunities**: 6 tasks
