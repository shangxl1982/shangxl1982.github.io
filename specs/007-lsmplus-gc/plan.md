# Implementation Plan: LSM Plus Garbage Collection

**Branch**: `007-lsmplus-gc` | **Date**: 2026-04-17 | **Spec**: [spec.md](file:///home/wisefox/git/hyperkvstore/specs/007-lsmplus-gc/spec.md)
**Input**: Feature specification from `/specs/007-lsmplus-gc/spec.md`

## Summary

Implement garbage collection system for reclaiming storage space from obsolete data in LSM tree. Detects obsolete chunks and pages, safely deletes them while maintaining references, tracks chunk occupancy rates for compaction decisions, and manages Minimum Not-Sealed (MNS) sequence numbers for journal cleanup.

## Technical Context

**Language/Version**: Java 25  
**Primary Dependencies**: lsmplus-api, lsmplus-storage, lsmplus-bplustree, JUnit 6.0.0  
**Storage**: Coordinates with storage layer for chunk deletion  
**Testing**: JUnit 5 with Mockito  
**Target Platform**: Linux server (JVM)  
**Project Type**: Library  
**Performance Goals**: >90% obsolete space reclaimed within 1min, >100MB/s compaction  
**Constraints**: No data loss, <5% performance impact, configurable thresholds  
**Scale/Scope**: 6 core classes (GarbageCollector, GCStrategy, GCResult, OccupancyTracker, MNSTracker, GCConfig)  

## Constitution Check

✅ **Library-First**: Standalone GC library
✅ **Test-First**: TDD with unit and integration tests
✅ **Simplicity**: Focused on space reclamation
✅ **Observability**: Occupancy tracking, GC metrics, result reporting
✅ **Versioning**: GC strategy versioned

## Project Structure

```text
lsmplus-gc/
├── src/
│   ├── main/java/org/hyperkv/lsmplus/gc/
│   │   ├── GarbageCollector.java     # Main GC coordinator
│   │   ├── GCStrategy.java           # Policy definition
│   │   ├── GCResult.java             # Outcome reporting
│   │   ├── OccupancyTracker.java     # Valid data percentage
│   │   ├── MNSTracker.java           # Minimum not-sealed tracking
│   │   └── GCConfig.java             # Configuration
│   └── test/java/org/hyperkv/lsmplus/gc/
│       ├── GarbageCollectorTest.java
│       ├── OccupancyTrackerTest.java
│       ├── MNSTrackerTest.java
│       └── GCIntegrationTest.java
└── build.gradle.kts
```

## Phase 0: Research & Design Decisions

### Research Tasks

1. **Obsolescence Detection**: Reference counting or mark-sweep
2. **Compaction Strategy**: Rewrite valid data to new chunks
3. **MNS Tracking**: Track minimum sequence across active memtables
4. **Concurrency**: GC runs in background, coordinate with operations
5. **Scheduling**: Periodic or trigger-based GC runs

### Design Decisions

1. **Reference Tracking**: Track active page references from B+Tree
2. **Occupancy Threshold**: Compact chunks below 50% occupancy
3. **Background Execution**: GC runs in separate thread
4. **Graceful Coordination**: Don't block operations during GC
5. **Result Reporting**: Track space reclaimed, chunks processed

## Phase 1: Design & Contracts

**Public API**:
- `GarbageCollector.run()` - Execute GC cycle
- `GarbageCollector.compact(Chunk)` - Compact low-occupancy chunk
- `OccupancyTracker.calculate(Chunk)` - Get occupancy percentage
- `MNSTracker.update()` - Advance MNS based on sealed memtables

## Dependencies

**Internal**: lsmplus-api, lsmplus-storage, lsmplus-bplustree  
**External**: JUnit 6.0.0, Mockito 5.11.0

## Success Metrics

- ✅ Reclaim >90% obsolete space within 1 minute
- ✅ Occupancy calculation <100ms per chunk
- ✅ Compaction throughput >100MB/s
- ✅ GC impact <5% on operation latency
- ✅ Zero data loss during GC
