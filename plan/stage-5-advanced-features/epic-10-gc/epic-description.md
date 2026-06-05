# Epic 10: Garbage Collection

## Overview

Implement Garbage Collection (GC) to reclaim space from old versions of the B+Tree. This epic builds the GC mechanism with MNS (Min Not Sealed) and Occupancy tracking.

## Goals

1. Implement MNS (Min Not Sealed) number tracking
2. Implement Occupancy tracking per Chunk
3. Implement GC strategies: Full, Partial, Hole Punching
4. Implement Chunk lifecycle management

## Scope

### In Scope
- MNS tracking
- Occupancy tracking
- GC strategies
- Chunk lifecycle (OPEN → SEALED → DELETING → DELETED)

### Out of Scope
- Automatic GC triggering (future enhancement)
- GC policies (future enhancement)

## Technical Design

### GC Architecture

```
┌─────────────────────────────────────────────────────────────┐
│                    GC Architecture                           │
├─────────────────────────────────────────────────────────────┤
│  Components:                                                 │
│  - GarbageCollector                                          │
│  - MNS (Min Not Sealed) Tracker                              │
│  - Occupancy Tracker                                         │
│  - Chunk Lifecycle Manager                                    │
└─────────────────────────────────────────────────────────────┘
```

### MNS (Min Not Sealed)

```
MNS = min(version of all active transactions)
- Any Chunk with version < MNS can be GC'd
```

### Occupancy Tracking

```
Occupancy = total valid data size / Chunk size
- Tracked per Chunk
- Updated during Tree Dump
- Used to decide GC strategy
```

## Stories

| Story ID | Name | Priority |
|----------|------|----------|
| 10-1 | Implement MNS Tracker | High |
| 10-2 | Implement Occupancy Tracker | High |
| 10-3 | Implement Full GC | High |
| 10-4 | Implement Partial GC | High |
| 10-5 | Implement Hole Punching | Medium |
| 10-6 | Unit Tests for GC | High |

## Dependencies

- Stage 4: KVStore Core

## Acceptance Criteria

- [ ] MNS tracking works
- [ ] Occupancy tracking works
- [ ] Full GC reclaims space
- [ ] Partial GC reclaims space
- [ ] Hole Punching reclaims space
- [ ] All unit tests pass

## Risk Mitigation

| Risk | Mitigation |
|------|------------|
| Data loss | Validate MNS and Occupancy |
| Performance impact | Run GC in background |
| Space overhead | Track Occupancy accurately |
