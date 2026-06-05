# Tasks: LSM Plus Monitoring and Metrics

**Input**: Design documents from `/specs/009-lsmplus-monitoring/`
**Prerequisites**: plan.md, spec.md

**Tests**: Tests included following TDD approach.

## Format: `[ID] [P?] [Story] Description`

## Path Conventions

- Java source: `lsmplus-monitoring/src/main/java/org/hyperkv/lsmplus/monitoring/`
- Tests: `lsmplus-monitoring/src/test/java/org/hyperkv/lsmplus/monitoring/`

---

## Phase 1: Setup

- [x] T001 Create lsmplus-monitoring module directory structure
- [x] T002 Configure build.gradle.kts
- [x] T003 [P] Setup metrics collection configuration

---

## Phase 2: Foundational

**⚠️ CRITICAL**: Must complete before user stories

- [x] T004 Define Metric interface in lsmplus-monitoring/src/main/java/org/hyperkv/lsmplus/monitoring/Metric.java
- [x] T005 [P] Define HealthStatus enum in lsmplus-monitoring/src/main/java/org/hyperkv/lsmplus/monitoring/HealthStatus.java

---

## Phase 3: User Story 1 - Performance Metrics Collection (Priority: P1) 🎯 MVP

**Goal**: Collect performance metrics (latency, throughput, error rates)

**Independent Test**: Perform operations, verify metrics collected correctly

### Tests for US1

- [x] T006 [P] [US1] Create MetricsRegistryTest.java

### Implementation for US1

- [x] T007 [P] [US1] Implement Counter class in lsmplus-monitoring/src/main/java/org/hyperkv/lsmplus/monitoring/Counter.java
- [x] T008 [P] [US1] Implement Gauge class in lsmplus-monitoring/src/main/java/org/hyperkv/lsmplus/monitoring/Gauge.java
- [x] T009 [US1] Implement MetricsRegistry class in lsmplus-monitoring/src/main/java/org/hyperkv/lsmplus/monitoring/MetricsRegistry.java
- [x] T010 [US1] Add latency tracking
- [x] T011 [US1] Add throughput tracking
- [x] T012 [US1] Add error rate tracking

**Checkpoint**: US1 complete - performance metrics operational

---

## Phase 4: User Story 2 - Resource Usage Tracking (Priority: P1)

**Goal**: Track resource usage (memory, disk, CPU)

**Independent Test**: Run workloads, verify resource usage tracked accurately

### Implementation for US2

- [x] T013 [US2] Add memory usage metrics
- [x] T014 [US2] Add disk usage metrics
- [x] T015 [US2] Add CPU usage metrics

**Checkpoint**: US2 complete - resource tracking operational

---

## Phase 5: User Story 3 - Health Checks (Priority: P1)

**Goal**: Provide health check endpoints

**Independent Test**: Call health check, verify correct status returned

### Tests for US3

- [x] T016 [P] [US3] Create HealthCheckTest.java

### Implementation for US3

- [x] T017 [P] [US3] Implement HealthCheck interface in lsmplus-monitoring/src/main/java/org/hyperkv/lsmplus/monitoring/HealthCheck.java
- [x] T018 [US3] Implement health check aggregation
- [x] T019 [US3] Add health status reporting

**Checkpoint**: US3 complete - health checks operational

---

## Phase 6: User Story 4 - Histogram and Distribution Metrics (Priority: P2)

**Goal**: Histogram metrics for latency distributions

**Independent Test**: Record values, verify histogram distributions calculated correctly

### Implementation for US4

- [x] T020 [P] [US4] Implement Histogram class in lsmplus-monitoring/src/main/java/org/hyperkv/lsmplus/monitoring/Histogram.java
- [x] T021 [US4] Add percentile calculation (p50, p95, p99)
- [x] T022 [US4] Add bucket-based distribution

**Checkpoint**: US4 complete - histograms operational

---

## Phase 7: User Story 5 - Metrics Registry and Export (Priority: P2)

**Goal**: Central registry with export capabilities

**Independent Test**: Register metrics, verify export in standard formats

### Implementation for US5

- [x] T023 [US5] Implement Prometheus export format
- [x] T024 [US5] Implement JSON export format
- [x] T025 [US5] Add metric labels/tags support

**Checkpoint**: US5 complete - export operational

---

## Phase 8: Polish

- [x] T026 [P] Add comprehensive Javadoc
- [x] T027 Run all tests (100% pass target)
- [x] T028 [P] Verify metrics overhead <1%
- [x] T029 Verify export <100ms for 10K metrics
- [x] T030 Create quickstart.md

---

## Dependencies

**External**: JUnit 6.0.0, Mockito 5.17.0

## Parallel Execution

- Phase 2: T005 can run in parallel
- Phase 3: T006-T008 can run in parallel
- Phase 5: T016-T017 can run in parallel
- Phase 6: T020 can run in parallel
- Phase 8: T026, T028 can run in parallel

## MVP Scope

**MVP**: Phase 1, Phase 2, Phase 3 (US1), Phase 4 (US2), Phase 5 (US3)
- Delivers basic monitoring with health checks
- Enables system observability

## Task Summary

- **Total Tasks**: 30
- **Setup**: 3 tasks
- **Foundational**: 2 tasks
- **US1 (Performance Metrics)**: 7 tasks (MVP)
- **US2 (Resource Tracking)**: 3 tasks (MVP)
- **US3 (Health Checks)**: 4 tasks (MVP)
- **US4 (Histograms)**: 3 tasks
- **US5 (Export)**: 3 tasks
- **Polish**: 5 tasks
- **Parallel Opportunities**: 10 tasks
