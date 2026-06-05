# Feature Specification: B+Tree Persistent Storage

**Feature Branch**: `001-bplustree-core`  
**Created**: 2026-04-16  
**Status**: Draft  
**Input**: User description: "B+Tree persistent storage with level-based write buffer, page ID management, and tree dump for LSM tree"

## User Scenarios & Testing *(mandatory)*

### User Story 1 - Tree Dump with Level-Based Write Buffer (Priority: P1)

As the LSM tree system, I need to persist sealed memory tables into the B+Tree in a crash-consistent manner, so that data is safely stored on disk and can be recovered after system restart.

**Why this priority**: This is the core functionality that enables data persistence. Without it, the system cannot guarantee data durability.

**Independent Test**: Can be fully tested by sealing a memory table, triggering a tree dump, and verifying all entries are queryable from the B+Tree after the dump completes.

**Acceptance Scenarios**:

1. **Given** a sealed memory table with 100 key-value pairs, **When** tree dump is triggered, **Then** all 100 pairs are queryable from the B+Tree and parent pages are written after all child pages are persisted.
2. **Given** multiple sealed memory tables with overlapping keys, **When** tree dump is triggered, **Then** the latest value for each key is persisted and tombstones cause physical deletion.
3. **Given** a page split occurs during dump, **When** the split propagates to parent pages, **Then** parent pages are updated with virtual locations and resolved to real locations during flush.

---

### User Story 2 - Page ID Management with Overflow Protection (Priority: P1)

As the B+Tree system, I need to manage unique page IDs for all pages with separate monotonic sequences for leaf and index pages, so that page ID collisions never occur and integer overflow is prevented.

**Why this priority**: Page ID uniqueness is fundamental to data integrity. Overflow would cause catastrophic data corruption.

**Independent Test**: Can be tested by creating millions of pages and verifying that leaf page IDs are always positive (starting from 1), index page IDs are always negative (starting from Long.MIN_VALUE), and no duplicates exist.

**Acceptance Scenarios**:

1. **Given** a new B+Tree, **When** the first leaf page is created, **Then** its page ID is 1.
2. **Given** a new B+Tree, **When** the first index page is created, **Then** its page ID is Long.MIN_VALUE.
3. **Given** a tree with existing pages, **When** a page split occurs, **Then** the new page receives the next available ID in the appropriate sequence (leaf or index).
4. **Given** the tree is persisted and reloaded, **When** new pages are created, **Then** page IDs continue from the persisted maximum values without duplication.

---

### User Story 3 - Crash-Consistent Page Flush Ordering (Priority: P1)

As the B+Tree system, I need to ensure child pages are always persisted before their parent pages during flush, so that no dangling pointers exist if a crash occurs mid-flush.

**Why this priority**: Incorrect flush ordering would result in data corruption and unrecoverable state after crashes.

**Independent Test**: Can be tested by simulating a crash mid-flush and verifying that the persisted state contains no references to unpersisted pages.

**Acceptance Scenarios**:

1. **Given** pages at multiple tree levels in the write buffer, **When** flush is triggered, **Then** level 0 (leaf) pages are flushed first, followed by level 1, and so on up to the root.
2. **Given** a parent page references a child with a virtual location, **When** the child is flushed, **Then** the parent's virtual location is resolved to the child's actual disk location before the parent is flushed.

---

### User Story 4 - Efficient Page Splitting with Cascading Propagation (Priority: P2)

As the B+Tree system, I need to handle page splits that propagate up the tree when parent pages also run out of space, so that the tree maintains its balanced structure.

**Why this priority**: Cascading splits are essential for maintaining tree balance during bulk inserts, though they occur less frequently than simple splits.

**Independent Test**: Can be tested by inserting enough entries to cause multiple levels of splits and verifying the tree remains balanced with correct parent-child relationships.

**Acceptance Scenarios**:

1. **Given** a leaf page that is full, **When** a new entry is inserted, **Then** the leaf splits and the parent index page is updated with the new child location.
2. **Given** a parent index page is full and a child split requires an update, **Then** the parent splits and the split propagates to its parent, creating a new root if necessary.

---

### User Story 5 - Tombstone Handling During Tree Dump (Priority: P2)

As the LSM tree system, I need to physically delete entries from the B+Tree when tombstones are encountered during dump, so that deleted data is removed from persistent storage.

**Why this priority**: Tombstone handling ensures deleted data is properly removed, though it's secondary to the core insert functionality.

**Independent Test**: Can be tested by inserting entries, marking them as tombstones in a memory table, triggering a dump, and verifying the entries are no longer queryable.

**Acceptance Scenarios**:

1. **Given** a key exists in the B+Tree, **When** a tombstone for that key is encountered during dump, **Then** the key is physically removed from the leaf page.
2. **Given** a leaf page becomes empty after tombstone deletions, **When** the page is processed, **Then** the empty page is decommissioned and removed from the tree structure.

---

### Edge Cases

- What happens when the tree is empty and the first entry is inserted? (Root leaf page creation)
- How does the system handle a page that is exactly at capacity? (Split threshold handling)
- What happens when all entries in a range are tombstoned? (Empty page cleanup)
- How does the system handle very large keys that exceed page capacity? (Entry size validation)
- What happens when page ID sequences approach their limits? (Overflow detection and error handling)

## Requirements *(mandatory)*

### Functional Requirements

- **FR-001**: System MUST persist sealed memory table entries to B+Tree pages via tree dump operation
- **FR-002**: System MUST use level-based write buffer to organize pages by tree level (0 = leaves, higher = index pages)
- **FR-003**: System MUST flush pages from lowest level to highest level to ensure children are persisted before parents
- **FR-004**: System MUST assign unique positive page IDs (1, 2, 3, ...) to leaf pages in monotonic order
- **FR-005**: System MUST assign unique negative page IDs (Long.MIN_VALUE, Long.MIN_VALUE+1, ...) to index pages in monotonic order
- **FR-006**: System MUST use 0 as the invalid/null page ID sentinel value
- **FR-007**: System MUST persist max leaf page ID and min index page ID in tree metadata for recovery
- **FR-008**: System MUST use long (64-bit) integers for page IDs to prevent overflow
- **FR-009**: System MUST use virtual locations (chunkId=0, offset=pageId) to track pending child references in parent pages
- **FR-010**: System MUST resolve virtual locations to actual disk locations before flushing parent pages
- **FR-011**: System MUST handle page splits by creating new pages and updating parent index pages with separator keys
- **FR-012**: System MUST propagate splits recursively when parent pages also run out of space
- **FR-013**: System MUST create a new root page when split propagation reaches the current root
- **FR-014**: System MUST physically remove entries from leaf pages when tombstones are encountered during dump
- **FR-015**: System MUST decommission empty leaf pages after tombstone deletions
- **FR-016**: System MUST track occupancy deltas (new pages and decommissioned pages) for GC purposes
- **FR-017**: System MUST record decommissioned page locations for hole punching GC
- **FR-018**: System MUST update journal replay point after successful dump to enable crash recovery
- **FR-019**: System MUST atomically update tree metadata (version, root location, replay point) after dump
- **FR-020**: System MUST support range queries across leaf pages using sibling pointers

### Key Entities

- **B+Tree**: The persistent storage component with version tracking, root location, and journal replay point
- **Page**: A fixed-capacity storage unit that can be either a leaf page (stores key-value pairs) or an index page (stores key-location pairs)
- **PageIdManager**: Manages monotonic page ID allocation with separate sequences for leaf and index pages
- **LevelWriteBuffer**: Organizes modified pages by tree level to ensure proper flush ordering
- **VirtualSegmentLocation**: Marker location used to track pending child references before actual disk location is known
- **TreeDumper**: Orchestrates the tree dump process including page splits, parent updates, and flush ordering
- **OccupancyDelta**: Records chunk space usage changes (positive for new pages, negative for decommissioned pages)

## Success Criteria *(mandatory)*

### Measurable Outcomes

- **SC-001**: System can dump 1 million key-value pairs from sealed memory tables to B+Tree in under 30 seconds
- **SC-002**: System maintains crash consistency with zero dangling pointers after any mid-flush interruption
- **SC-003**: System supports B+Tree with 1 billion+ pages without page ID overflow
- **SC-004**: Tree dump completes with all entries queryable and no data loss
- **SC-005**: Page splits propagate correctly through all tree levels without data corruption
- **SC-006**: System recovers from crash using persisted metadata and journal replay within 60 seconds

## Assumptions

- The system uses append-only storage where page updates create new pages rather than modifying in-place
- Page capacity is fixed at creation time (leaf pages: 8KB, index pages: 64KB by default)
- Tombstones exist only in memory tables; B+Tree pages store only NORMAL values
- The ChunkManager provides reliable append-only storage with unique chunk IDs
- Journal replay provides idempotent recovery for entries after the last successful dump
- The system runs on a 64-bit JVM where long integers provide sufficient range for page IDs
- Memory tables are sealed before dump and are read-only during the dump process
- The B+Tree is the only persistent index structure; no secondary indexes are considered
