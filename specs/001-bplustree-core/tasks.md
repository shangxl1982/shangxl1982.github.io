# Tasks: B+Tree Persistent Storage

**Input**: Design documents from `/specs/001-bplustree-core/`
**Prerequisites**: plan.md, spec.md, research.md, data-model.md

**Organization**: Tasks are grouped by user story to enable independent implementation and testing of each story.

## Phase 1: Setup (Shared Infrastructure)

**Purpose**: Project initialization and protobuf updates

- [x] T001 Update page.proto to use int64 for page_id field
- [x] T002 [P] Update keyvalue.proto to use int64 for SegmentLocation offset field
- [x] T003 Regenerate protobuf Java classes with `./gradlew :lsmplus-api:generateProto`

---

## Phase 2: Foundational (Blocking Prerequisites)

**Purpose**: Core infrastructure that MUST be complete before ANY user story can be implemented

**⚠️ CRITICAL**: No user story work can begin until this phase is complete

- [x] T004 Update SegmentLocation.java to use long for offset field (constructor, getter, serialization)
- [x] T005 [P] Create PageIdManager.java in lsmplus-bplustree/src/main/java/org/hyperkv/lsmplus/bplustree/
- [x] T006 [P] Update Page.java to use long pageId and add createLeaf/createIndex factory methods
- [x] T007 [P] Create VirtualSegmentLocation.java utility class
- [x] T008 Update Page.split() method signature to accept long pageId parameter
- [x] T009 Update all Page-related tests to use long page IDs

**Checkpoint**: Foundation ready - user story implementation can now begin in parallel

---

## Phase 3: User Story 1 - Tree Dump with Level-Based Write Buffer (Priority: P1) 🎯 MVP

**Goal**: Implement level-based write buffer and tree dump operation that persists sealed memory tables to B+Tree with crash-consistent flush ordering

**Independent Test**: Seal a memory table with test data, trigger tree dump, verify all entries are queryable from B+Tree after dump completes

### Tests for User Story 1

- [x] T010 [P] [US1] Create LevelWriteBufferTest.java in lsmplus-bplustree/src/test/java/org/hyperkv/lsmplus/bplustree/
- [x] T011 [P] [US1] Create TreeDumperTest.java in lsmplus-bplustree/src/test/java/org/hyperkv/lsmplus/bplustree/

### Implementation for User Story 1

- [x] T012 [US1] Create LevelWriteBuffer.java in lsmplus-bplustree/src/main/java/org/hyperkv/lsmplus/bplustree/
- [x] T013 [US1] Implement level-based page storage with Map<Integer, LevelBuffer>
- [x] T014 [US1] Implement getFlushablePages() method using maxKey comparison
- [x] T015 [US1] Implement page flush ordering (level 0 → higher levels)
- [x] T016 [US1] Update TreeDumper.java to use LevelWriteBuffer instead of legacy WriteBuffer
- [x] T017 [US1] Implement processEntry() method in TreeDumper for handling NORMAL entries
- [x] T018 [US1] Implement processEntry() method in TreeDumper for handling TOMBSTONE entries
- [x] T019 [US1] Implement findLeafPageLocation() traversal method in TreeDumper
- [x] T020 [US1] Implement tryFlushPages() method in TreeDumper
- [x] T021 [US1] Implement flushAllPages() method for remaining pages after dump
- [x] T022 [US1] Update BPlusTree.dump() method to orchestrate the full tree dump flow
- [x] T023 [US1] Add occupancy delta tracking during page writes and decommissions
- [x] T024 [US1] Implement atomic metadata update after dump completion

**Checkpoint**: At this point, User Story 1 should be fully functional and testable independently

---

## Phase 4: User Story 2 - Page ID Management with Overflow Protection (Priority: P1)

**Goal**: Ensure unique page IDs with separate monotonic sequences for leaf and index pages, persisted in tree metadata for recovery

**Independent Test**: Create millions of pages, verify leaf IDs are positive (starting from 1), index IDs are negative (starting from Long.MIN_VALUE), no duplicates exist, and IDs persist across tree reload

### Tests for User Story 2

- [x] T025 [P] [US2] Create PageIdManagerTest.java in lsmplus-bplustree/src/test/java/org/hyperkv/lsmplus/bplustree/

### Implementation for User Story 2

- [x] T026 [US2] Implement allocateLeafPageId() in PageIdManager (starts at 1, increments)
- [x] T027 [US2] Implement allocateIndexPageId() in PageIdManager (starts at Long.MIN_VALUE, increments)
- [x] T028 [US2] Implement overflow detection and error handling in PageIdManager
- [x] T029 [US2] Add getMaxLeafPageId() and getMinIndexPageId() methods for persistence
- [x] T030 [US2] Update BPlusTree to persist max page IDs in metadata during dump
- [x] T031 [US2] Update BPlusTree to restore page ID sequences from metadata on load
- [x] T032 [US2] Update TreeDumper to use PageIdManager for all page ID allocation during splits
- [x] T033 [US2] Add validation in Page constructor to verify page ID matches page type

**Checkpoint**: User Stories 1 AND 2 should both work independently

---

## Phase 5: User Story 3 - Crash-Consistent Page Flush Ordering (Priority: P1)

**Goal**: Ensure child pages are always persisted before parent pages during flush, with virtual location resolution for pending references

**Independent Test**: Simulate crash mid-flush, verify persisted state contains no references to unpersisted pages

### Tests for User Story 3

- [x] T034 [P] [US3] Create VirtualSegmentLocationTest.java in lsmplus-bplustree/src/test/java/org/hyperkv/lsmplus/bplustree/
- [x] T035 [P] [US3] Add crash consistency tests to TreeDumperTest.java

### Implementation for User Story 3

- [x] T036 [US3] Implement VirtualSegmentLocation.isVirtual() method
- [x] T037 [US3] Implement VirtualSegmentLocation.getPendingPageId() method
- [x] T038 [US3] Update LevelWriteBuffer to track virtual locations in parent pages
- [x] T039 [US3] Implement resolveVirtualLocations() method in TreeDumper
- [x] T040 [US3] Update flush ordering to resolve virtual locations before flushing parents
- [x] T041 [US3] Add validation in flushAllPages() to detect unresolved virtual locations
- [x] T042 [US3] Implement crash recovery test that verifies no dangling pointers

**Checkpoint**: All P1 user stories should now be independently functional

---

## Phase 6: User Story 4 - Efficient Page Splitting with Cascading Propagation (Priority: P2)

**Goal**: Handle page splits that propagate up the tree when parent pages also run out of space, maintaining balanced tree structure

**Independent Test**: Insert enough entries to cause multiple levels of splits, verify tree remains balanced with correct parent-child relationships

### Tests for User Story 4

- [x] T043 [P] [US4] Add cascading split tests to PageSplitTest.java

### Implementation for User Story 4

- [x] T044 [US4] Implement handlePageSplit() method in TreeDumper for leaf page splits
- [x] T045 [US4] Implement propagateSplitToParent() method for index page updates
- [x] T046 [US4] Implement recursive split propagation when parent pages are full
- [x] T047 [US4] Implement new root creation when split reaches current root
- [x] T048 [US4] Update parent index pages with separator keys and child locations
- [x] T049 [US4] Add path tracking during tree traversal for split propagation
- [x] T050 [US4] Update LevelWriteBuffer to handle newly created split pages

**Checkpoint**: User Stories 1-4 should all be independently functional

---

## Phase 7: User Story 5 - Tombstone Handling During Tree Dump (Priority: P2)

**Goal**: Physically delete entries from B+Tree when tombstones are encountered during dump, decommission empty leaf pages

**Independent Test**: Insert entries, mark as tombstones in memory table, trigger dump, verify entries are no longer queryable

### Tests for User Story 5

- [x] T051 [P] [US5] Add tombstone handling tests to TreeDumperTest.java

### Implementation for User Story 5

- [x] T052 [US5] Implement deleteEntry() method in TreeDumper for tombstone processing
- [x] T053 [US5] Implement leaf page entry removal with proper reordering
- [x] T054 [US5] Implement empty page detection after tombstone deletions
- [x] T055 [US5] Implement decommission page logic with occupancy delta recording
- [x] T056 [US5] Update parent index pages to remove references to decommissioned pages
- [x] T057 [US5] Add decommissioned page location tracking for hole punching GC

**Checkpoint**: All user stories should now be independently functional

---

## Phase 8: Polish & Cross-Cutting Concerns

**Purpose**: Improvements that affect multiple user stories

- [x] T058 [P] Add comprehensive Javadoc comments to all public classes and methods
- [x] T059 [P] Update existing BPlusTreeTest.java to cover new functionality
- [x] T060 [P] Add integration tests for full dump lifecycle in lsmplus-bplustree/src/test/
- [x] T061 Performance optimization for bulk insert scenarios
- [x] T062 Add logging for tree dump operations and page splits
- [x] T063 Run full test suite: `./gradlew :lsmplus-bplustree:test`
- [x] T064 Verify build passes: `./gradlew :lsmplus-bplustree:build`

---

## Dependencies & Execution Order

### Phase Dependencies

- **Setup (Phase 1)**: No dependencies - can start immediately
- **Foundational (Phase 2)**: Depends on Setup completion - BLOCKS all user stories
- **User Stories (Phase 3+)**: All depend on Foundational phase completion
  - User stories can then proceed in parallel (if staffed)
  - Or sequentially in priority order (P1 → P2)
- **Polish (Phase 8)**: Depends on all desired user stories being complete

### User Story Dependencies

- **User Story 1 (P1)**: Can start after Foundational (Phase 2) - No dependencies on other stories
- **User Story 2 (P1)**: Can start after Foundational (Phase 2) - Integrates with US1 for page ID allocation
- **User Story 3 (P1)**: Can start after Foundational (Phase 2) - Integrates with US1 for flush ordering
- **User Story 4 (P2)**: Can start after Foundational (Phase 2) - Depends on US1 write buffer infrastructure
- **User Story 5 (P2)**: Can start after Foundational (Phase 2) - Depends on US1 dump infrastructure

### Within Each User Story

- Tests MUST be written and FAIL before implementation
- Models before services
- Core implementation before integration
- Story complete before moving to next priority

### Parallel Opportunities

- T001, T002, T003 can run in parallel (Setup phase)
- T004, T005, T006, T007 can run in parallel (Foundational phase)
- T010, T011 can run in parallel (US1 tests)
- T025 can run in parallel with other US2 tasks
- T034, T035 can run in parallel (US3 tests)
- T043 can run in parallel with other US4 tasks
- T051 can run in parallel with other US5 tasks
- T058, T059, T060 can run in parallel (Polish phase)

---

## Parallel Example: User Story 1

```bash
# Launch all tests for User Story 1 together:
Task: "Create LevelWriteBufferTest.java"
Task: "Create TreeDumperTest.java"

# Launch foundational components together:
Task: "Update SegmentLocation.java"
Task: "Create PageIdManager.java"
Task: "Update Page.java"
Task: "Create VirtualSegmentLocation.java"
```

---

## Implementation Strategy

### MVP First (User Story 1 Only)

1. Complete Phase 1: Setup
2. Complete Phase 2: Foundational (CRITICAL - blocks all stories)
3. Complete Phase 3: User Story 1
4. **STOP and VALIDATE**: Test tree dump with sealed memory table
5. Deploy/demo if ready

### Incremental Delivery

1. Complete Setup + Foundational → Foundation ready
2. Add User Story 1 → Test independently → Deploy/Demo (MVP!)
3. Add User Story 2 → Test independently → Deploy/Demo
4. Add User Story 3 → Test independently → Deploy/Demo
5. Add User Story 4 → Test independently → Deploy/Demo
6. Add User Story 5 → Test independently → Deploy/Demo
7. Each story adds value without breaking previous stories

### Parallel Team Strategy

With multiple developers:

1. Team completes Setup + Foundational together
2. Once Foundational is done:
   - Developer A: User Story 1
   - Developer B: User Story 2
   - Developer C: User Story 3
3. Stories complete and integrate independently

---

## Notes

- [P] tasks = different files, no dependencies
- [Story] label maps task to specific user story for traceability
- Each user story should be independently completable and testable
- Verify tests fail before implementing
- Commit after each task or logical group
- Stop at any checkpoint to validate story independently
- Avoid: vague tasks, same file conflicts, cross-story dependencies that break independence
