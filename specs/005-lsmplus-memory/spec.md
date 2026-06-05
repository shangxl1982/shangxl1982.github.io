# Feature Specification: LSM Plus Memory Table Management

**Feature Branch**: `005-lsmplus-memory`  
**Created**: 2026-04-17  
**Status**: Draft  
**Input**: User description: "In-memory sorted table management with sealing mechanism for LSM tree writes"

## User Scenarios & Testing *(mandatory)*

### User Story 1 - In-Memory Sorted Table (Priority: P1)

As a write-heavy application, I need an in-memory sorted table (memtable) that buffers writes before flushing to disk, so that I can achieve high write throughput with sorted output.

**Why this priority**: Memtable is the primary write path and enables LSM tree performance.

**Independent Test**: Can be fully tested by writing key-value pairs to the memtable and verifying that they are stored in sorted order.

**Acceptance Scenarios**:

1. **Given** an empty memtable, **When** I write multiple key-value pairs, **Then** they are stored in sorted order by key.
2. **Given** a memtable with existing keys, **When** I write to the same key, **Then** the value is updated in-place.
3. **Given** a memtable near capacity, **When** I check available space, **Then** accurate remaining capacity is reported.

---

### User Story 2 - Tombstone Support (Priority: P1)

As a database system, I need to mark keys as deleted using tombstones in the memtable, so that deletions are properly handled during compaction.

**Why this priority**: Tombstone support is essential for correct deletion semantics in LSM trees.

**Independent Test**: Can be tested by writing tombstones to the memtable and verifying that they are correctly identified during reads.

**Acceptance Scenarios**:

1. **Given** a key with a normal value, **When** I write a tombstone for that key, **Then** the key is marked as deleted.
2. **Given** a tombstone in the memtable, **When** I read that key, **Then** a deletion marker is returned.
3. **Given** a tombstone, **When** I write a normal value for the same key, **Then** the tombstone is replaced with the new value.

---

### User Story 3 - Sealing Mechanism (Priority: P1)

As a compaction process, I need to seal a memtable when it reaches capacity, so that it becomes immutable and can be flushed to disk while a new memtable accepts writes.

**Why this priority**: Sealing enables concurrent flushing and writing, which is critical for LSM tree operation.

**Independent Test**: Can be tested by filling a memtable to capacity, sealing it, and verifying that it becomes immutable while a new memtable is created.

**Acceptance Scenarios**:

1. **Given** a memtable at capacity, **When** I seal it, **Then** the memtable becomes immutable and no further writes are accepted.
2. **Given** a sealed memtable, **When** I attempt to write to it, **Then** the write is rejected with an appropriate error.
3. **Given** a sealed memtable, **When** I read from it, **Then** all previously written data is still accessible.

---

### User Story 4 - Memory Table Manager (Priority: P2)

As a system coordinator, I need a manager that handles multiple memtables (active and sealed), so that I can coordinate the transition between memtables during flush operations.

**Why this priority**: The manager enables coordination but depends on basic memtable operations.

**Independent Test**: Can be tested by creating multiple memtables, sealing some, and verifying that the manager correctly tracks active and sealed tables.

**Acceptance Scenarios**:

1. **Given** an active memtable, **When** it is sealed, **Then** the manager creates a new active memtable automatically.
2. **Given** multiple sealed memtables, **When** I query the manager, **Then** all sealed tables are returned in correct order.
3. **Given** sealed memtables that have been flushed, **When** I clear them, **Then** they are removed from the manager's tracking.

---

### Edge Cases

- What happens when the memtable exceeds memory limits? (Should trigger automatic sealing)
- How does the system handle concurrent writes to the same key? (Should use proper synchronization)
- What happens when sealing fails? (Should retry and maintain data integrity)
- How does the system handle reads during sealing? (Should allow concurrent reads)
- What happens when the manager runs out of memory for new memtables? (Should block writes until space is available)

## Requirements *(mandatory)*

### Functional Requirements

- **FR-001**: System MUST maintain key-value pairs in sorted order in memory
- **FR-002**: System MUST support tombstone markers for deletions
- **FR-003**: System MUST provide sealing mechanism to make memtables immutable
- **FR-004**: System MUST track memtable size and enforce capacity limits
- **FR-005**: System MUST support concurrent reads while writes are serialized
- **FR-006**: System MUST provide memory table manager for coordinating multiple tables
- **FR-007**: System MUST automatically create new memtable when current is sealed
- **FR-008**: System MUST track sealed memtables for flushing
- **FR-009**: System MUST support clearing sealed memtables after flush
- **FR-010**: System MUST provide iteration over all entries in sorted order
- **FR-011**: System MUST handle concurrent access with proper synchronization
- **FR-012**: System MUST support dump callbacks for flush notifications

### Key Entities

- **MemoryTable**: In-memory sorted key-value store with sealing support
- **MemoryTableManager**: Coordinates active and sealed memtables
- **DumpCallback**: Interface for receiving flush notifications
- **SealMechanism**: Handles memtable sealing and immutability

## Success Criteria *(mandatory)*

### Measurable Outcomes

- **SC-001**: Write throughput exceeds 1 million operations per second
- **SC-002**: Read latency is under 100 microseconds for in-memory lookups
- **SC-003**: Sealing operation completes in under 1 millisecond
- **SC-004**: Memory overhead is less than 20% beyond stored data size
- **SC-005**: Concurrent read throughput scales linearly with reader threads
- **SC-006**: Zero data loss during sealing operations
- **SC-007**: Sorted iteration completes in O(n) time

## Assumptions

- Memtables use TreeMap or similar sorted data structure
- Default memtable size limit is configurable (e.g., 64MB)
- Sealing is triggered by size limit or explicit request
- Sealed memtables are flushed by a separate process (TreeDumper)
- The system uses copy-on-write or similar technique for concurrent access
- Tombstones are preserved during memtable lifetime
- Memory management is handled by the JVM garbage collector
