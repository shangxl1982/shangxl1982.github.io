# Tasks: LSM Plus Backup Management

**Input**: Design documents from `/specs/011-lsmplus-backup/`
**Prerequisites**: plan.md, spec.md, lsmplus-api, lsmplus-storage, lsmplus-journal modules

**Tests**: Tests included following TDD approach.

## Format: `[ID] [P?] [Story] Description`

## Path Conventions

- Java source: `lsmplus-backup/src/main/java/org/hyperkv/lsmplus/backup/`
- Tests: `lsmplus-backup/src/test/java/org/hyperkv/lsmplus/backup/`

---

## Phase 1: Setup

- [x] T001 Create lsmplus-backup module directory structure
- [x] T002 Configure build.gradle.kts with dependencies
- [x] T003 [P] Setup backup directory configuration

---

## Phase 2: Foundational

**⚠️ CRITICAL**: Must complete before user stories

- [x] T004 Define BackupType enum in lsmplus-backup/src/main/java/org/hyperkv/lsmplus/backup/BackupType.java
- [x] T005 [P] Define BackupMetadata class in lsmplus-backup/src/main/java/org/hyperkv/lsmplus/backup/BackupMetadata.java
- [x] T006 [P] Define BackupSchedule class in lsmplus-backup/src/main/java/org/hyperkv/lsmplus/backup/BackupSchedule.java
- [x] T007 [P] Define BackupRetention class in lsmplus-backup/src/main/java/org/hyperkv/lsmplus/backup/BackupRetention.java

---

## Phase 3: User Story 1 - Full Backup Creation (Priority: P1) 🎯 MVP

**Goal**: Create full backups of all data

**Independent Test**: Create full backup, verify all data captured

### Tests for US1

- [x] T008 [P] [US1] Create BackupManagerTest.java

### Implementation for US1

- [x] T009 [US1] Implement BackupManager class in lsmplus-backup/src/main/java/org/hyperkv/lsmplus/backup/BackupManager.java
- [x] T010 [US1] Implement BackupManager.createFullBackup() method
- [x] T011 [US1] Add chunk backup
- [x] T012 [US1] Add journal backup
- [x] T013 [US1] Add metadata backup
- [x] T014 [US1] Add backup integrity validation

**Checkpoint**: US1 complete - full backup operational

---

## Phase 4: User Story 2 - Incremental Backup Creation (Priority: P2)

**Goal**: Create incremental backups capturing only changes

**Independent Test**: Create full backup, make changes, create incremental, verify only changes captured

### Implementation for US2

- [x] T015 [US2] Implement BackupManager.createIncrementalBackup() method
- [x] T016 [US2] Add change tracking since last backup
- [x] T017 [US2] Add incremental backup chaining

**Checkpoint**: US2 complete - incremental backup operational

---

## Phase 5: User Story 3 - Backup Restoration (Priority: P1)

**Goal**: Restore data from backups

**Independent Test**: Create backup, delete data, restore, verify all data recovered

### Implementation for US3

- [x] T018 [US3] Implement BackupManager.restore() method
- [x] T019 [US3] Add full backup restoration
- [x] T020 [US3] Add incremental backup chain restoration
- [x] T021 [US3] Add restoration integrity validation

**Checkpoint**: US3 complete - restoration operational

---

## Phase 6: User Story 4 - Backup Scheduling and Automation (Priority: P2)

**Goal**: Automated backup scheduling

**Independent Test**: Configure schedule, verify backups created automatically

### Implementation for US4

- [x] T022 [US4] Implement scheduled backup execution
- [x] T023 [US4] Add backup retry on failure
- [x] T024 [US4] Add backup alerting

**Checkpoint**: US4 complete - scheduling operational

---

## Phase 7: User Story 5 - Backup Retention and Cleanup (Priority: P2)

**Goal**: Configure backup retention policies

**Independent Test**: Create multiple backups, set retention, verify old backups cleaned up

### Implementation for US5

- [x] T025 [US5] Implement BackupManager.cleanup() method
- [x] T026 [US5] Add time-based retention
- [x] T027 [US5] Add orphaned backup cleanup

**Checkpoint**: US5 complete - retention operational

---

## Phase 8: Polish

- [x] T028 [P] Add comprehensive Javadoc
- [x] T029 Run all tests (100% pass target)
- [x] T030 [P] Verify full backup <1hr for 100GB
- [x] T031 Verify incremental backup <10min for 1GB
- [x] T032 Create quickstart.md

---

## Dependencies

**Internal**: lsmplus-api, lsmplus-storage, lsmplus-journal
**External**: JUnit 6.0.0, Mockito 5.17.0

## Parallel Execution

- Phase 2: T005-T007 can run in parallel
- Phase 3: T008 can run in parallel
- Phase 8: T028, T030 can run in parallel

## MVP Scope

**MVP**: Phase 1, Phase 2, Phase 3 (US1), Phase 5 (US3)
- Delivers basic backup and restore
- Enables disaster recovery

## Task Summary

- **Total Tasks**: 32
- **Setup**: 3 tasks
- **Foundational**: 4 tasks
- **US1 (Full Backup)**: 7 tasks (MVP)
- **US2 (Incremental Backup)**: 3 tasks
- **US3 (Restoration)**: 4 tasks (MVP)
- **US4 (Scheduling)**: 3 tasks
- **US5 (Retention)**: 3 tasks
- **Polish**: 5 tasks
- **Parallel Opportunities**: 8 tasks
