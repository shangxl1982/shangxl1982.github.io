# Feature Specification: LSM Plus Journal (Write-Ahead Log)

**Feature Branch**: `004-lsmplus-journal`  
**Created**: 2026-04-17  
**Status**: Draft  
**Input**: User description: "Write-ahead logging system for durability and crash recovery in LSM tree"

## User Scenarios & Testing *(mandatory)*

### User Story 1 - Durable Write-Ahead Logging (Priority: P1)

As a database system, I need a write-ahead log (journal) that persists all operations before they are applied to data structures, so that I can guarantee durability and recover from crashes.

**Why this priority**: Write-ahead logging is fundamental to ACID durability guarantees.

**Independent Test**: Can be fully tested by writing operations to the journal, simulating a crash, and verifying that all operations can be recovered.

**Acceptance Scenarios**:

1. **Given** a PUT operation, **When** I write it to the journal, **Then** the operation is persisted to disk before acknowledgment.
2. **Given** a DELETE operation, **When** I write it to the journal, **Then** the tombstone marker is persisted with correct timestamp.
3. **Given** a BATCH operation with multiple entries, **When** I write it to the journal, **Then** all entries are persisted atomically.

---

### User Story 2 - Crash Recovery and Replay (Priority: P1)

As a system administrator, I need to replay journal entries after a crash, so that the system can recover to a consistent state without data loss.

**Why this priority**: Crash recovery is essential for production reliability.

**Independent Test**: Can be tested by writing operations, killing the process mid-write, restarting, and verifying that all committed operations are recovered.

**Acceptance Scenarios**:

1. **Given** a journal with committed operations, **When** I replay from the last checkpoint, **Then** all operations are correctly reconstructed.
2. **Given** a partial write at the end of the journal, **When** I replay, **Then** the incomplete entry is detected and ignored.
3. **Given** multiple journal regions, **When** I replay, **Then** entries are replayed in correct chronological order.

---

### User Story 3 - Journal Region Management (Priority: P2)

As a storage optimizer, I need journal regions that can be rotated and archived, so that disk space is managed efficiently without losing recovery capability.

**Why this priority**: Region management enables efficient storage, but depends on basic journal operations.

**Independent Test**: Can be tested by creating multiple journal regions, rotating them, and verifying that old regions can be archived or deleted.

**Acceptance Scenarios**:

1. **Given** a journal region that reaches size limit, **When** I rotate it, **Then** a new region is created and subsequent writes go to the new region.
2. **Given** an archived journal region, **When** I need to replay from it, **Then** the region can be restored and replayed.
3. **Given** multiple sealed regions, **When** I compact the journal, **Then** old regions are safely deleted after checkpoint.

---

### User Story 4 - Batch Write Optimization (Priority: P2)

As a high-throughput application, I need batch write support in the journal, so that multiple operations can be persisted efficiently in a single I/O operation.

**Why this priority**: Batch optimization improves performance, but depends on basic journal functionality.

**Independent Test**: Can be tested by writing batches of operations and verifying that they are persisted with a single disk sync.

**Acceptance Scenarios**:

1. **Given** a batch of 100 operations, **When** I write them to the journal, **Then** they are persisted with a single fsync for efficiency.
2. **Given** concurrent batch writes, **When** multiple threads write batches, **Then** each batch is persisted atomically without interleaving.
3. **Given** a large batch exceeding buffer size, **When** I write it, **Then** it is split into multiple chunks correctly.

---

### Edge Cases

- What happens when the journal disk is full? (Should fail gracefully and prevent further writes)
- How does the system handle journal corruption? (Should detect corruption and recover from last valid entry)
- What happens during concurrent writes to the same journal region? (Should serialize writes correctly)
- How does the system handle replay when the application state has changed? (Should handle idempotent replay)
- What happens when a journal region file is deleted manually? (Should detect missing region and report error)

## Requirements *(mandatory)*

### Functional Requirements

- **FR-001**: System MUST persist all operations to journal before acknowledging to client
- **FR-002**: System MUST support PUT, DELETE, and BATCH operation types
- **FR-003**: System MUST provide crash recovery through journal replay
- **FR-004**: System MUST detect and handle incomplete writes at the end of journal
- **FR-005**: System MUST support journal region rotation for space management
- **FR-006**: System MUST provide replay points (checkpoints) for efficient recovery
- **FR-007**: System MUST support batch writes with single fsync for efficiency
- **FR-008**: System MUST validate journal entry integrity using checksums
- **FR-009**: System MUST handle concurrent writes with proper serialization
- **FR-010**: System MUST support journal compaction and old region deletion
- **FR-011**: System MUST maintain journal region index for fast replay
- **FR-012**: System MUST provide replay handlers for different operation types

### Key Entities

- **Journal**: Main journal manager handling writes and recovery
- **JournalEntry**: Individual operation entry with type, timestamp, key-value pairs
- **JournalRegion**: A segment of the journal with its own file
- **JournalWriter**: Handles sequential writes to journal regions
- **JournalReplayHandler**: Processes journal entries during recovery
- **JournalReplayPoint**: Checkpoint marking a consistent state for recovery

## Success Criteria *(mandatory)*

### Measurable Outcomes

- **SC-001**: Journal write latency is under 1 millisecond for single operations
- **SC-002**: Batch write throughput exceeds 100,000 operations per second
- **SC-003**: Crash recovery completes in under 5 seconds for 1 million entries
- **SC-004**: Zero data loss for committed operations during crash
- **SC-005**: Journal replay correctly reconstructs 100% of committed operations
- **SC-006**: Journal overhead is less than 10% of operation size
- **SC-007**: Region rotation completes in under 100 milliseconds

## Assumptions

- Journal entries are written sequentially (append-only)
- Each journal entry has a unique monotonically increasing sequence number
- Journal regions have configurable size limits (default 64MB)
- Fsync is called after each write or batch for durability
- Journal files are stored in a dedicated directory
- Replay handlers are provided by the application layer
- Journal compaction is triggered by the garbage collector
- The system uses CRC32 checksums for entry validation
