# Stage 1: Foundation Infrastructure

## Goal

Build the foundation layer for the storage system, including serialization protocol, data integrity protection, and storage layer components. This stage establishes the core infrastructure that all other components depend on.

## Duration

3-4 weeks

## Prerequisites

- Java 25 development environment
- Protocol Buffers compiler
- Maven build system

## Stage Objectives

1. Implement Protobuf-based serialization for all data types
2. Build data integrity protection (Write Item, CRC32, 4K alignment)
3. Create storage layer (Chunk, ChunkManager)

## Key Design Decisions

### Protobuf Serialization
- Global unified Key/Value definitions for all modules
- Forward and backward compatibility support
- Optimized for performance

### Data Integrity
- Write Item format: Header + Body + CRC32 + Padding (4K aligned)
- Magic number: 0xABCD for quick identification
- Partial write detection

### Storage Layer
- Chunk size: 64MB maximum
- Three types: INDEX, LEAF, JOURNAL
- Chunk lifecycle: OPEN → SEALED → DELETING → DELETED

## Epic List

| Epic | Name | Description |
|------|------|-------------|
| 1 | Protobuf Serialization | Define and implement Protobuf messages for all data types |
| 2 | Data Integrity | Implement Write Item format, CRC32, 4K alignment |
| 3 | Storage Layer | Build Chunk and ChunkManager for persistent storage |

## Dependencies

This stage has no external dependencies and must be completed first.

## Acceptance Criteria

- [ ] All Protobuf messages defined and compiled
- [ ] Write Item format implemented and tested
- [ ] CRC32 validation working
- [ ] Chunk allocation and read/write working
- [ ] Chunk lifecycle management functional
- [ ] Storage directory structure created correctly

## Next Stage

Stage 2: Core Components (Journal, MemoryTable)
