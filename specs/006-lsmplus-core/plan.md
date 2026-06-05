# Implementation Plan: LSM Plus Core KV Store

**Branch**: `006-lsmplus-core` | **Date**: 2026-04-17 | **Spec**: [spec.md](file:///home/wisefox/git/hyperkvstore/specs/006-lsmplus-core/spec.md)
**Input**: Feature specification from `/specs/006-lsmplus-core/spec.md`

## Summary

Implement core key-value store coordinating all operations with read/write support, snapshots, batch operations, and concurrency control. Integrates memory table, journal, and B+Tree modules, provides state management (INITIALIZING, READY, ERROR, CLOSED), and handles crash recovery through journal replay.

## Technical Context

**Language/Version**: Java 25  
**Primary Dependencies**: lsmplus-api, lsmplus-memory, lsmplus-journal, lsmplus-bplustree, JUnit 6.0.0  
**Storage**: Coordinates memory table, journal, and B+Tree  
**Testing**: JUnit 5 with Mockito  
**Target Platform**: Linux server (JVM)  
**Project Type**: Library  
**Performance Goals**: <1ms single op, >100K ops/s batch, <10μs snapshot  
**Constraints**: Key-level locking, atomic batches, <10s recovery  
**Scale/Scope**: 6 core classes (KVStore, KVStoreState, Snapshot, BatchOperation, LockManager, RecoveryHandler)  

## Constitution Check

✅ **Library-First**: Core KV store library
✅ **Test-First**: TDD with comprehensive integration tests
✅ **Simplicity**: Coordinates existing modules, minimal new logic
✅ **Observability**: State tracking, operation metrics
✅ **Versioning**: State machine versioned

## Project Structure

```text
lsmplus-core/
├── src/
│   ├── main/java/org/hyperkv/lsmplus/core/
│   │   ├── KVStore.java              # Main store coordinator
│   │   ├── KVStoreState.java         # State enum
│   │   ├── BatchOperation.java       # Batch container
│   │   ├── RecoveryHandler.java      # Journal replay handler
│   │   └── concurrency/
│   │       ├── LockManager.java      # Key-level locks
│   │       ├── Snapshot.java         # Point-in-time view
│   │       ├── WriteRequest.java     # Write operation
│   │       ├── WriteRequestQueue.java # Write batching
│   │       └── BatchWriter.java      # Batch executor
│   └── test/java/org/hyperkv/lsmplus/core/
│       ├── KVStoreTest.java
│       ├── concurrency/
│       │   ├── LockManagerTest.java
│       │   ├── SnapshotTest.java
│       │   └── WriteRequestQueueTest.java
│       └── KVStoreIntegrationTest.java
└── build.gradle.kts
```

## Phase 0: Research & Design Decisions

### Research Tasks

1. **Coordination Pattern**: Facade pattern for module coordination
2. **Concurrency**: Key-level read-write locks
3. **Snapshots**: Copy-on-write for consistent views
4. **State Machine**: Atomic state transitions
5. **Recovery**: Replay journal to restore state

### Design Decisions

1. **Facade Pattern**: KVStore coordinates all modules
2. **Key-Level Locking**: Fine-grained concurrency control
3. **Write Queue**: Batch writes for efficiency
4. **State Tracking**: Atomic state transitions with validation
5. **Recovery Integration**: Use journal replay handler

## Phase 1: Design & Contracts

**Public API**:
- `KVStore.get(IndexKey)` - Retrieve value
- `KVStore.put(IndexKey, IndexValue)` - Store key-value
- `KVStore.delete(IndexKey)` - Delete key
- `KVStore.batch(BatchOperation)` - Execute batch atomically
- `KVStore.createSnapshot()` - Create point-in-time view
- `KVStore.getState()` - Get current state

## Dependencies

**Internal**: lsmplus-api, lsmplus-memory, lsmplus-journal, lsmplus-bplustree  
**External**: JUnit 6.0.0, Mockito 5.11.0

## Success Metrics

- ✅ Single operation latency <1ms
- ✅ Batch throughput >100K ops/s
- ✅ Snapshot creation <10μs
- ✅ Recovery time <10s for 1M entries
- ✅ Zero data corruption in concurrent operations
