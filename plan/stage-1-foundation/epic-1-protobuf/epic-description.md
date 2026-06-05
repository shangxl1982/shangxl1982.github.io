# Epic 1: Protobuf Serialization

## Overview

Define and implement Protocol Buffers messages for all data types used in the system. This epic establishes the serialization foundation that all other components depend on.

## Goals

1. Create unified Protobuf definitions for Key, Value, and KeyValuePair
2. Define Journal entry message formats
3. Define Page data message formats
4. Define metadata message formats (Tree, Journal Region, Chunk, Occupancy, Backup)
5. Generate Java classes from Protobuf definitions

## Scope

### In Scope
- All Protobuf message definitions in `design-serialization.md`
- Java class generation
- Serialization/deserialization utilities
- Unit tests for all message types

### Out of Scope
- Compression support (future enhancement)
- Encryption support (future enhancement)

## Technical Design

### File Structure
```
src/main/proto/
├── common.proto          # Common types (KeyType, ValueType, etc.)
├── keyvalue.proto        # Key, Value, KeyValuePair
├── journal.proto         # JournalEntry, OperationType
├── page.proto            # Page, PageType
├── metadata.proto        # TreeMetadata, JournalRegion, ChunkMetadata
└── backup.proto          # BackupMetadata
```

### Key Messages

1. **KeyProto**: key_type + key_data
2. **ValueProto**: value_type + value_data
3. **KeyValuePairProto**: key + oneof(value, location)
4. **JournalEntryProto**: operation_type + timestamp + sequence_number + entries
5. **PageProto**: page_type + page_id + max_size + used_size + entries
6. **SegmentLocationProto**: chunk_id + offset + length

## Stories

| Story ID | Name | Priority |
|----------|------|----------|
| 1-1 | Define Common Types | High |
| 1-2 | Define Key/Value Messages | High |
| 1-3 | Define Journal Messages | High |
| 1-4 | Define Page Messages | High |
| 1-5 | Define Metadata Messages | High |
| 1-6 | Implement Serialization Utils | High |
| 1-7 | Unit Tests for All Messages | High |

## Dependencies

- Protocol Buffers compiler (protoc)
- protobuf-java library 3.21.12

## Acceptance Criteria

- [ ] All Protobuf files compile without errors
- [ ] Java classes generated successfully
- [ ] Serialization/deserialization round-trip tests pass
- [ ] All field numbers reserved for future use
- [ ] Documentation comments added to all messages

## Risk Mitigation

| Risk | Mitigation |
|------|------------|
| Breaking changes | Reserve field numbers 1-100 for future use |
| Performance issues | Profile serialization performance early |
| Compatibility issues | Test with different Protobuf versions |
