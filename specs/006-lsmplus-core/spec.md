# Feature Specification: LSM Plus Core KV Store

**Feature Branch**: `006-lsmplus-core`  
**Created**: 2026-04-17  
**Status**: Draft  
**Input**: User description: "Core key-value store with read/write operations, snapshots, and batch operations"

## User Scenarios & Testing *(mandatory)*

### User Story 1 - Basic Key-Value Operations (Priority: P1)

As an application developer, I need to perform basic GET, PUT, and DELETE operations on the key-value store, so that I can store and retrieve data reliably.

**Why this priority**: Basic operations are the core functionality of any key-value store.

**Independent Test**: Can be fully tested by performing PUT, GET, and DELETE operations and verifying correct behavior.

**Acceptance Scenarios**:

1. **Given** an empty store, **When** I PUT a key-value pair, **Then** the pair is stored and can be retrieved with GET.
2. **Given** a store with existing key, **When** I GET that key, **Then** the correct value is returned.
3. **Given** a store with existing key, **When** I DELETE that key, **Then** the key is removed and subsequent GET returns not found.

---

### User Story 2 - Snapshot Reads (Priority: P1)

As a reporting application, I need to read from a consistent snapshot of the data, so that I see a point-in-time consistent view even while writes are happening.

**Why this priority**: Snapshots enable consistent reads without blocking writes, which is critical for analytics and backups.

**Independent Test**: Can be tested by creating a snapshot, performing writes, and verifying that reads from the snapshot don't see the new writes.

**Acceptance Scenarios**:

1. **Given** an active store, **When** I create a snapshot, **Then** I can read from that snapshot even while new writes occur.
2. **Given** a snapshot, **When** I read from it, **Then** I see data as it existed at snapshot creation time.
3. **Given** multiple snapshots, **When** I read from different snapshots, **Then** each snapshot shows its own consistent view.

---

### User Story 3 - Batch Operations (Priority: P2)

As a bulk data loader, I need to perform multiple operations in a single batch, so that I can achieve high throughput for bulk inserts and updates.

**Why this priority**: Batch operations improve efficiency but depend on basic operations.

**Independent Test**: Can be tested by submitting batches of operations and verifying that all operations are applied atomically.

**Acceptance Scenarios**:

1. **Given** a batch of 1000 PUT operations, **When** I submit the batch, **Then** all operations are applied atomically.
2. **Given** a batch with mixed PUT and DELETE operations, **When** I submit the batch, **Then** all operations are applied in order.
3. **Given** a batch that fails partway, **When** I submit it, **Then** no operations are applied (atomic rollback).

---

### User Story 4 - Concurrency Control (Priority: P2)

As a multi-threaded application, I need proper concurrency control with locking, so that concurrent operations don't corrupt data or cause race conditions.

**Why this priority**: Concurrency control is essential for multi-threaded applications but depends on basic operations.

**Independent Test**: Can be tested by performing concurrent operations from multiple threads and verifying data consistency.

**Acceptance Scenarios**:

1. **Given** concurrent writes to different keys, **When** multiple threads write simultaneously, **Then** all writes succeed without corruption.
2. **Given** concurrent writes to the same key, **When** multiple threads write simultaneously, **Then** writes are serialized correctly.
3. **Given** concurrent reads and writes, **When** operations happen simultaneously, **Then** reads see consistent data.

---

### User Story 5 - Recovery and State Management (Priority: P2)

As a production system, I need to recover state after restart and track the store's operational state, so that I can ensure data durability and monitor system health.

**Why this priority**: Recovery is critical for production but depends on journal and storage modules.

**Independent Test**: Can be tested by writing data, restarting the store, and verifying that all data is recovered.

**Acceptance Scenarios**:

1. **Given** a store with data, **When** I restart the store, **Then** all previously written data is recovered.
2. **Given** a store in error state, **When** I check the state, **Then** appropriate error information is available.
3. **Given** a store during recovery, **When** recovery completes, **Then** the store transitions to ready state.

---

### Edge Cases

- What happens when the store runs out of memory? (Should trigger memtable flush and handle gracefully)
- How does the system handle very large values? (Should support streaming or chunking)
- What happens when a snapshot is held for too long? (Should track and limit snapshot lifetime)
- How does the system handle concurrent batch operations? (Should serialize batches correctly)
- What happens when recovery fails? (Should report error and prevent store from becoming ready)

## Requirements *(mandatory)*

### Functional Requirements

- **FR-001**: System MUST support GET, PUT, and DELETE operations on key-value pairs
- **FR-002**: System MUST provide snapshot reads for consistent point-in-time queries
- **FR-003**: System MUST support batch operations with atomic semantics
- **FR-004**: System MUST provide concurrency control with proper locking
- **FR-005**: System MUST recover state from journal after restart
- **FR-006**: System MUST track operational state (INITIALIZING, READY, ERROR, CLOSED)
- **FR-007**: System MUST coordinate memtable writes and flushes
- **FR-008**: System MUST integrate with journal for durability
- **FR-009**: System MUST integrate with B+Tree for persistent storage
- **FR-010**: System MUST handle concurrent access with proper synchronization
- **FR-011**: System MUST provide write request queue for batching
- **FR-012**: System MUST support graceful shutdown with pending operation completion

### Key Entities

- **KVStore**: Main key-value store interface coordinating all operations
- **KVStoreState**: Enum tracking store state (INITIALIZING, READY, ERROR, CLOSED)
- **Snapshot**: Point-in-time consistent view of the data
- **BatchOperation**: Container for multiple operations in a single batch
- **LockManager**: Manages read/write locks for concurrency control
- **RecoveryHandler**: Handles state recovery from journal after restart
- **WriteRequestQueue**: Queues write operations for batching

## Success Criteria *(mandatory)*

### Measurable Outcomes

- **SC-001**: Single operation latency is under 1 millisecond for in-memory data
- **SC-002**: Batch operation throughput exceeds 100,000 operations per second
- **SC-003**: Snapshot creation completes in under 10 microseconds
- **SC-004**: Recovery completes in under 10 seconds for 1 million entries
- **SC-005**: Concurrent read throughput scales linearly with reader threads
- **SC-006**: Zero data corruption during concurrent operations
- **SC-007**: Graceful shutdown completes within 30 seconds with pending operations

## Assumptions

- The store coordinates between memory table, journal, and B+Tree modules
- Snapshots use copy-on-write or similar technique for efficiency
- Batch operations are written to journal atomically
- Lock granularity is at the key level for better concurrency
- Recovery replays journal entries to restore state
- The store uses a write request queue for batching
- State transitions are atomic and thread-safe
