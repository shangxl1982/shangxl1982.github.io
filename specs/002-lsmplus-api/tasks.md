# Tasks: LSM Plus API Models

**Input**: Design documents from `/specs/002-lsmplus-api/`
**Prerequisites**: plan.md (required), spec.md (required for user stories)

**Tests**: Tests are included following TDD approach as specified in the plan.

**Organization**: Tasks are grouped by user story to enable independent implementation and testing of each story.

## Format: `[ID] [P?] [Story] Description`

- **[P]**: Can run in parallel (different files, no dependencies)
- **[Story]**: Which user story this task belongs to (e.g., US1, US2, US3, US4)
- Include exact file paths in descriptions

## Path Conventions

- **Single project**: `lsmplus-api/src/` at repository root
- Protobuf definitions: `lsmplus-api/src/main/proto/`
- Java models: `lsmplus-api/src/main/java/org/hyperkv/lsmplus/api/model/`
- Tests: `lsmplus-api/src/test/java/org/hyperkv/lsmplus/api/model/`

---

## Phase 1: Setup (Project Infrastructure)

**Purpose**: Initialize project structure and configure protobuf build

- [x] T001 Create lsmplus-api module directory structure per implementation plan
- [x] T002 Configure build.gradle.kts with protobuf plugin and dependencies
- [x] T003 [P] Setup Gradle wrapper and build configuration
- [x] T004 [P] Create .gitignore for generated protobuf files

---

## Phase 2: Foundational (Core Protobuf Definitions)

**Purpose**: Define core protobuf types that all user stories depend on

**⚠️ CRITICAL**: No user story work can begin until this phase is complete

- [x] T005 Define KeyType enum in lsmplus-api/src/main/proto/common.proto
- [x] T006 [P] Define ValueType enum in lsmplus-api/src/main/proto/common.proto
- [x] T007 [P] Define OperationType enum in lsmplus-api/src/main/proto/common.proto
- [x] T008 [P] Define PageType enum in lsmplus-api/src/main/proto/common.proto
- [x] T009 [P] Define ChunkType enum in lsmplus-api/src/main/proto/common.proto
- [x] T010 [P] Define ChunkStatus enum in lsmplus-api/src/main/proto/common.proto
- [x] T011 [P] Define BackupType enum in lsmplus-api/src/main/proto/common.proto
- [x] T012 [P] Define CompressionType enum in lsmplus-api/src/main/proto/common.proto
- [x] T013 Generate Java classes from protobuf definitions using Gradle build

**Checkpoint**: Foundation ready - user story implementation can now begin in parallel

---

## Phase 3: User Story 1 - Key-Value Model Definition (Priority: P1) 🎯 MVP

**Goal**: Provide well-defined key and value models with type safety and serialization support

**Independent Test**: Create IndexKey and IndexValue instances with different types, serialize to protobuf, deserialize back, and verify data integrity

### Tests for User Story 1 (TDD Approach)

> **NOTE: Write these tests FIRST, ensure they FAIL before implementation**

- [x] T014 [P] [US1] Create IndexKeyTest.java in lsmplus-api/src/test/java/org/hyperkv/lsmplus/api/model/
- [x] T015 [P] [US1] Create IndexValueTest.java in lsmplus-api/src/test/java/org/hyperkv/lsmplus/api/model/

### Protobuf Definitions for User Story 1

- [x] T016 [P] [US1] Define KeyProto message in lsmplus-api/src/main/proto/keyvalue.proto
- [x] T017 [P] [US1] Define ValueProto message in lsmplus-api/src/main/proto/keyvalue.proto
- [x] T018 [US1] Regenerate Java classes from updated protobuf definitions

### Implementation for User Story 1

- [x] T019 [P] [US1] Implement IndexKey.orderedBytes() factory method in lsmplus-api/src/main/java/org/hyperkv/lsmplus/api/model/IndexKey.java
- [x] T020 [P] [US1] Implement IndexKey.custom() factory method in lsmplus-api/src/main/java/org/hyperkv/lsmplus/api/model/IndexKey.java
- [x] T021 [US1] Implement IndexKey.toProto() serialization method in lsmplus-api/src/main/java/org/hyperkv/lsmplus/api/model/IndexKey.java
- [x] T022 [US1] Implement IndexKey.fromProto() deserialization method in lsmplus-api/src/main/java/org/hyperkv/lsmplus/api/model/IndexKey.java
- [x] T023 [US1] Implement IndexKey.compareTo() for key comparison in lsmplus-api/src/main/java/org/hyperkv/lsmplus/api/model/IndexKey.java
- [x] T024 [US1] Add validation logic (null checks, size limits) to IndexKey constructor in lsmplus-api/src/main/java/org/hyperkv/lsmplus/api/model/IndexKey.java
- [x] T025 [P] [US1] Implement IndexValue.normal() factory method in lsmplus-api/src/main/java/org/hyperkv/lsmplus/api/model/IndexValue.java
- [x] T026 [P] [US1] Implement IndexValue.tombstone() factory method in lsmplus-api/src/main/java/org/hyperkv/lsmplus/api/model/IndexValue.java
- [x] T027 [US1] Implement IndexValue.toProto() serialization method in lsmplus-api/src/main/java/org/hyperkv/lsmplus/api/model/IndexValue.java
- [x] T028 [US1] Implement IndexValue.fromProto() deserialization method in lsmplus-api/src/main/java/org/hyperkv/lsmplus/api/model/IndexValue.java
- [x] T029 [US1] Add validation logic (null checks, size limits) to IndexValue constructor in lsmplus-api/src/main/java/org/hyperkv/lsmplus/api/model/IndexValue.java

**Checkpoint**: At this point, User Story 1 should be fully functional and testable independently

---

## Phase 4: User Story 2 - Protobuf Type Definitions (Priority: P1)

**Goal**: Provide comprehensive protobuf type definitions for all LSM tree components

**Independent Test**: Generate code from protobuf definitions in multiple languages and verify that messages serialize/deserialize correctly

### Implementation for User Story 2

- [x] T030 [P] [US2] Verify all common.proto enums compile and generate correctly
- [x] T031 [P] [US2] Add reserved field numbers (100-200) to all enum definitions for future expansion
- [x] T032 [US2] Create comprehensive documentation for protobuf type usage
- [x] T033 [US2] Add backward compatibility tests for protobuf messages

**Checkpoint**: User Story 2 complete - all protobuf types defined and validated

---

## Phase 5: User Story 3 - Journal and Page Message Formats (Priority: P2)

**Goal**: Provide well-defined protobuf message formats for journal entries and page structures

**Independent Test**: Create JournalEntry and Page messages, serialize them, and verify that all metadata and data fields are correctly preserved

### Tests for User Story 3 (TDD Approach)

- [x] T034 [P] [US3] Create JournalEntryProtoTest.java in lsmplus-api/src/test/java/org/hyperkv/lsmplus/api/model/
- [x] T035 [P] [US3] Create PageProtoTest.java in lsmplus-api/src/test/java/org/hyperkv/lsmplus/api/model/

### Protobuf Definitions for User Story 3

- [x] T036 [P] [US3] Define JournalEntryProto message in lsmplus-api/src/main/proto/journal.proto
- [x] T037 [P] [US3] Define PageProto message in lsmplus-api/src/main/proto/page.proto
- [x] T038 [US3] Regenerate Java classes from updated protobuf definitions

### Implementation for User Story 3

- [x] T039 [US3] Add batch operation support to JournalEntryProto definition
- [x] T040 [US3] Add page metadata fields (pageId, pageType, entries) to PageProto
- [x] T041 [US3] Validate journal entry serialization with PUT operation
- [x] T042 [US3] Validate page serialization with entry data

**Checkpoint**: User Story 3 complete - journal and page message formats ready

---

## Phase 6: User Story 4 - Metadata and Chunk Definitions (Priority: P2)

**Goal**: Provide metadata structures for chunks, segments, and backups

**Independent Test**: Create ChunkMetadata and BackupMetadata messages, verify that all tracking information is correctly stored

### Tests for User Story 4 (TDD Approach)

- [x] T043 [P] [US4] Create ChunkMetadataProtoTest.java in lsmplus-api/src/test/java/org/hyperkv/lsmplus/api/model/
- [x] T044 [P] [US4] Create BackupMetadataProtoTest.java in lsmplus-api/src/test/java/org/hyperkv/lsmplus/api/model/
- [x] T045 [P] [US4] Create SegmentLocationProtoTest.java in lsmplus-api/src/test/java/org/hyperkv/lsmplus/api/model/

### Protobuf Definitions for User Story 4

- [x] T046 [P] [US4] Define ChunkMetadataProto message in lsmplus-api/src/main/proto/metadata.proto
- [x] T047 [P] [US4] Define BackupMetadataProto message in lsmplus-api/src/main/proto/metadata.proto
- [x] T048 [P] [US4] Define SegmentLocationProto message in lsmplus-api/src/main/proto/metadata.proto
- [x] T049 [US4] Regenerate Java classes from updated protobuf definitions

### Implementation for User Story 4

- [x] T050 [US4] Add chunk lifecycle fields (chunkId, status, size, timestamps) to ChunkMetadataProto
- [x] T051 [US4] Add backup tracking fields (backup type, timestamp, chunk references) to BackupMetadataProto
- [x] T052 [US4] Add segment location fields (chunkId, offset, length) to SegmentLocationProto
- [x] T053 [US4] Validate chunk metadata serialization
- [x] T054 [US4] Validate backup metadata serialization
- [x] T055 [US4] Validate segment location serialization

**Checkpoint**: User Story 4 complete - metadata structures ready for storage management

---

## Phase 7: Polish & Cross-Cutting Concerns

**Purpose**: Finalize implementation and ensure quality

- [x] T056 [P] Add comprehensive Javadoc documentation to all public methods
- [x] T057 [P] Add package-info.java with package documentation
- [x] T058 Run all tests and verify 100% pass rate
- [x] T059 [P] Run performance benchmarks for serialization (<1μs target)
- [x] T060 [P] Run performance benchmarks for key comparison (<100ns target)
- [x] T061 Verify protobuf messages are 30-50% smaller than JSON equivalent
- [x] T062 Test serialization round-trip for 1M operations with zero data loss
- [x] T063 [P] Add edge case tests (null handling, oversized keys, corrupted data)
- [x] T064 Create quickstart.md with usage examples
- [x] T065 Final code review and cleanup

---

## Dependencies & Parallel Execution

### User Story Dependencies

```text
US1 (Key-Value Models) ──┐
                          ├──> US3 (Journal/Page Formats)
US2 (Protobuf Types) ────┘
                          
US1 + US2 ────────────────> US4 (Metadata/Chunk Definitions)
```

**Independent Stories**: US1 and US2 can be developed in parallel
**Dependent Stories**: US3 and US4 depend on US1 and US2 completion

### Parallel Execution Opportunities

**Phase 2 (Foundational)**: T005-T012 can run in parallel (different enum definitions)
**Phase 3 (US1 Tests)**: T014-T015 can run in parallel
**Phase 3 (US1 Protobuf)**: T016-T017 can run in parallel
**Phase 3 (US1 Implementation)**: T019-T020, T025-T026 can run in parallel
**Phase 5 (US3 Tests)**: T034-T035 can run in parallel
**Phase 5 (US3 Protobuf)**: T036-T037 can run in parallel
**Phase 6 (US4 Tests)**: T043-T045 can run in parallel
**Phase 6 (US4 Protobuf)**: T046-T048 can run in parallel
**Phase 7 (Polish)**: T056-T057, T059-T060, T063 can run in parallel

### Suggested MVP Scope

**Minimum Viable Product**: Complete Phase 1, Phase 2, and Phase 3 (User Story 1)
- This delivers core key-value models with serialization
- Enables other modules to start development
- Provides foundation for all subsequent user stories

---

## Success Criteria

- ✅ All protobuf messages compile and generate Java classes
- ✅ IndexKey and IndexValue pass all unit tests
- ✅ Serialization round-trip succeeds for 1M operations
- ✅ Key comparison completes in <100ns
- ✅ Protobuf messages 30-50% smaller than JSON equivalent
- ✅ Zero data loss in serialization/deserialization
- ✅ All edge cases handled gracefully

## Task Summary

- **Total Tasks**: 65
- **Phase 1 (Setup)**: 4 tasks
- **Phase 2 (Foundational)**: 9 tasks
- **Phase 3 (US1 - Key-Value Models)**: 16 tasks (MVP)
- **Phase 4 (US2 - Protobuf Types)**: 4 tasks
- **Phase 5 (US3 - Journal/Page)**: 9 tasks
- **Phase 6 (US4 - Metadata/Chunk)**: 13 tasks
- **Phase 7 (Polish)**: 10 tasks
- **Parallel Opportunities**: 28 tasks can run in parallel
