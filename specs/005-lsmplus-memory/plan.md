# Implementation Plan: LSM Plus Memory Table Management

**Branch**: `005-lsmplus-memory` | **Date**: 2026-04-17 | **Spec**: [spec.md](file:///home/wisefox/git/hyperkvstore/specs/005-lsmplus-memory/spec.md)
**Input**: Feature specification from `/specs/005-lsmplus-memory/spec.md`

## Summary

Implement in-memory sorted table (memtable) management with sealing mechanism for LSM tree writes. Provides high write throughput with sorted output, supports tombstone markers for deletions, manages memtable lifecycle with automatic sealing at capacity, and coordinates active and sealed memtables through a manager.

## Technical Context

**Language/Version**: Java 25  
**Primary Dependencies**: lsmplus-api, JUnit 6.0.0  
**Storage**: In-memory (TreeMap-based)  
**Testing**: JUnit 5 with Mockito  
**Target Platform**: Linux server (JVM)  
**Project Type**: Library  
**Performance Goals**: >1M ops/s write, <100μs read, <1ms seal  
**Constraints**: 64MB default size limit, concurrent reads, serialized writes  
**Scale/Scope**: 3 core classes (MemoryTable, MemoryTableManager, DumpCallback)  

## Constitution Check

✅ **Library-First**: Standalone memory management library
✅ **Test-First**: TDD with unit and integration tests
✅ **Simplicity**: Focused on in-memory sorted storage
✅ **Observability**: Size tracking, entry counting, seal status
✅ **Versioning**: N/A (in-memory only)

## Project Structure

```text
lsmplus-memory/
├── src/
│   ├── main/java/org/hyperkv/lsmplus/memory/
│   │   ├── MemoryTable.java          # Sorted key-value store
│   │   ├── MemoryTableManager.java   # Coordinates active/sealed tables
│   │   └── DumpCallback.java         # Flush notification interface
│   └── test/java/org/hyperkv/lsmplus/memory/
│       ├── MemoryTableTest.java
│       ├── MemoryTableManagerTest.java
│       └── MemoryTableIntegrationTest.java
└── build.gradle.kts
```

## Phase 0: Research & Design Decisions

### Research Tasks

1. **Data Structure**: TreeMap for sorted order, O(log n) operations
2. **Concurrency**: ReadWriteLock for concurrent reads, exclusive writes
3. **Sealing**: Atomic transition to immutable state
4. **Memory Management**: Track size, trigger seal at limit
5. **Tombstone Handling**: Store as special value type

### Design Decisions

1. **Immutable After Seal**: Sealed memtables cannot be modified
2. **Factory Methods**: Create memtables through manager
3. **Callback Pattern**: Notify when memtable needs flush
4. **Size Tracking**: Accurate byte counting for capacity management

## Phase 1: Design & Contracts

**Public API**:
- `MemoryTable.put(IndexKey, IndexValue)` - Store key-value
- `MemoryTable.get(IndexKey)` - Retrieve value
- `MemoryTable.seal()` - Make immutable
- `MemoryTableManager.getActiveTable()` - Get current write target
- `MemoryTableManager.getSealedTables()` - Get tables awaiting flush

## Dependencies

**Internal**: lsmplus-api  
**External**: JUnit 6.0.0, Mockito 5.11.0

## Success Metrics

- ✅ Write throughput >1M ops/s
- ✅ Read latency <100μs
- ✅ Seal operation <1ms
- ✅ Memory overhead <20%
