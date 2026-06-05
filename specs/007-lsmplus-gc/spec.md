# Feature Specification: LSM Plus Garbage Collection

**Feature Branch**: `007-lsmplus-gc`  
**Created**: 2026-04-17  
**Status**: Draft  
**Input**: User description: "Garbage collection system for reclaiming storage space from obsolete data in LSM tree"

## User Scenarios & Testing *(mandatory)*

### User Story 1 - Obsolete Data Detection (Priority: P1)

As a storage optimizer, I need to identify obsolete chunks and pages that are no longer referenced, so that I can reclaim storage space efficiently.

**Why this priority**: Detecting obsolete data is the foundation of garbage collection.

**Independent Test**: Can be fully tested by creating chunks, marking some as obsolete, and verifying that the GC correctly identifies them.

**Acceptance Scenarios**:

1. **Given** chunks with no active references, **When** I run garbage collection, **Then** those chunks are identified as candidates for deletion.
2. **Given** pages referenced by active B+Tree nodes, **When** I run garbage collection, **Then** those pages are not marked as obsolete.
3. **Given** multiple obsolete chunks, **When** I query for GC candidates, **Then** all obsolete chunks are returned with correct metadata.

---

### User Story 2 - Space Reclamation (Priority: P1)

As a system administrator, I need to safely delete obsolete chunks and reclaim disk space, so that storage costs are minimized.

**Why this priority**: Space reclamation is the primary purpose of garbage collection.

**Independent Test**: Can be tested by running garbage collection and verifying that disk space is reclaimed without data loss.

**Acceptance Scenarios**:

1. **Given** obsolete chunks, **When** I run garbage collection, **Then** the chunks are deleted and disk space is reclaimed.
2. **Given** chunks in DELETING state, **When** deletion completes, **Then** chunks transition to DELETED state.
3. **Given** active references to a chunk, **When** I attempt to delete it, **Then** deletion is prevented to avoid data loss.

---

### User Story 3 - Occupancy Tracking (Priority: P2)

As a storage planner, I need to track chunk occupancy rates (percentage of valid data), so that I can make informed decisions about compaction and space reclamation.

**Why this priority**: Occupancy tracking enables intelligent GC decisions but depends on basic GC operations.

**Independent Test**: Can be tested by writing data, deleting some data, and verifying that occupancy rates are correctly calculated.

**Acceptance Scenarios**:

1. **Given** a chunk with mixed valid and obsolete data, **When** I check occupancy, **Then** the percentage of valid data is accurately reported.
2. **Given** multiple chunks with varying occupancy, **When** I query occupancy stats, **Then** all chunks are ranked by occupancy rate.
3. **Given** a chunk with low occupancy, **When** it drops below threshold, **Then** it is marked for compaction.

---

### User Story 4 - Compaction Strategy (Priority: P2)

As a performance optimizer, I need to compact chunks with low occupancy by rewriting valid data, so that storage efficiency is improved.

**Why this priority**: Compaction improves storage efficiency but depends on occupancy tracking.

**Independent Test**: Can be tested by triggering compaction on low-occupancy chunks and verifying that valid data is rewritten efficiently.

**Acceptance Scenarios**:

1. **Given** chunks with occupancy below threshold, **When** I run compaction, **Then** valid data is rewritten to new chunks.
2. **Given** compaction in progress, **When** new writes occur, **Then** they are directed to new chunks without blocking.
3. **Given** compaction completion, **When** old chunks are no longer referenced, **Then** they are marked for deletion.

---

### User Story 5 - MNS (Minimum Not-Sealed) Tracking (Priority: P2)

As a recovery system, I need to track the minimum not-sealed sequence number, so that I can determine which journal entries can be safely deleted.

**Why this priority**: MNS tracking enables journal cleanup but depends on basic GC operations.

**Independent Test**: Can be tested by advancing the MNS and verifying that old journal entries are correctly identified for cleanup.

**Acceptance Scenarios**:

1. **Given** sealed memtables flushed to disk, **When** I update MNS, **Then** the minimum not-sealed sequence advances.
2. **Given** journal entries before MNS, **When** I run GC, **Then** those entries are marked as safe to delete.
3. **Given** active memtables, **When** I check MNS, **Then** their sequence numbers are not included in the minimum.

---

### Edge Cases

- What happens when GC runs while compaction is in progress? (Should wait for compaction to complete)
- How does the system handle GC failures mid-deletion? (Should retry and maintain consistency)
- What happens when occupancy calculation fails? (Should use conservative estimates)
- How does the system handle concurrent GC and read operations? (Should not block reads)
- What happens when MNS cannot advance due to long-running transactions? (Should track and report blocking transactions)

## Requirements *(mandatory)*

### Functional Requirements

- **FR-001**: System MUST detect obsolete chunks and pages with no active references
- **FR-002**: System MUST safely delete obsolete chunks and reclaim disk space
- **FR-003**: System MUST track chunk occupancy rates (valid data percentage)
- **FR-004**: System MUST support compaction of low-occupancy chunks
- **FR-005**: System MUST track Minimum Not-Sealed (MNS) sequence number
- **FR-006**: System MUST prevent deletion of actively referenced data
- **FR-007**: System MUST provide GC configuration (thresholds, schedules)
- **FR-008**: System MUST report GC results (space reclaimed, chunks processed)
- **FR-009**: System MUST handle concurrent GC and normal operations safely
- **FR-010**: System MUST support manual GC trigger for administrative purposes
- **FR-011**: System MUST integrate with chunk manager for lifecycle management
- **FR-012**: System MUST coordinate with B+Tree for reference tracking

### Key Entities

- **GarbageCollector**: Main GC coordinator managing cleanup operations
- **GCStrategy**: Defines GC policy (thresholds, compaction rules)
- **GCResult**: Reports outcome of GC operations (space reclaimed, errors)
- **OccupancyTracker**: Tracks valid data percentage in chunks
- **MNSTracker**: Tracks minimum not-sealed sequence number
- **GCConfig**: Configuration for GC thresholds and schedules

## Success Criteria *(mandatory)*

### Measurable Outcomes

- **SC-001**: GC reclaims at least 90% of obsolete space within 1 minute of becoming obsolete
- **SC-002**: Occupancy calculation completes in under 100 milliseconds per chunk
- **SC-003**: Compaction throughput exceeds 100 MB/s on SSD storage
- **SC-004**: GC operations do not impact read/write latency by more than 5%
- **SC-005**: MNS tracking accuracy is 100% (no false positives/negatives)
- **SC-006**: Zero data loss during GC operations
- **SC-007**: GC completes within configured time window

## Assumptions

- GC runs periodically based on schedule or trigger
- Compaction rewrites valid data to new chunks before deleting old chunks
- Occupancy threshold for compaction is configurable (default 50%)
- MNS advances when memtables are flushed and sealed
- GC coordinates with B+Tree to track active page references
- GC does not run during peak load hours (configurable)
- Deleted chunks are tracked in metadata before physical deletion
