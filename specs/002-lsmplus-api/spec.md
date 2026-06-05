# Feature Specification: LSM Plus API Models

**Feature Branch**: `002-lsmplus-api`  
**Created**: 2026-04-17  
**Status**: Draft  
**Input**: User description: "API models and protobuf definitions for LSM tree key-value store"

## User Scenarios & Testing *(mandatory)*

### User Story 1 - Key-Value Model Definition (Priority: P1)

As a developer building LSM tree components, I need well-defined key and value models with type safety and serialization support, so that I can reliably store and retrieve data across all system components.

**Why this priority**: Key-value models are the foundation of the entire system. Without them, no other component can function.

**Independent Test**: Can be fully tested by creating IndexKey and IndexValue instances with different types, serializing them to protobuf, deserializing back, and verifying data integrity.

**Acceptance Scenarios**:

1. **Given** a byte array key, **When** I create an IndexKey with ORDERED_BYTES type, **Then** the key preserves the original bytes and supports comparison operations.
2. **Given** a normal value with byte data, **When** I create an IndexValue, **Then** the value correctly identifies as non-tombstone and serializes to protobuf format.
3. **Given** a tombstone marker, **When** I create an IndexValue with tombstone type, **Then** the value correctly identifies as a deletion marker.

---

### User Story 2 - Protobuf Type Definitions (Priority: P1)

As a system architect, I need comprehensive protobuf type definitions for all LSM tree components, so that data can be serialized and deserialized consistently across different modules and programming languages.

**Why this priority**: Protobuf definitions enable cross-language compatibility and efficient serialization, which are critical for distributed systems.

**Independent Test**: Can be tested by generating code from protobuf definitions in multiple languages and verifying that messages serialize/deserialize correctly.

**Acceptance Scenarios**:

1. **Given** common type definitions (KeyType, ValueType, OperationType), **When** I use them in other protobuf messages, **Then** the types are consistent and correctly validated.
2. **Given** a KeyProto message, **When** I serialize and deserialize it, **Then** all fields (keyType, keyData) are preserved without data loss.
3. **Given** a ValueProto message with compression, **When** I serialize it with GZIP compression, **Then** the compressed data is correctly stored and decompressed.

---

### User Story 3 - Journal and Page Message Formats (Priority: P2)

As a storage engineer, I need well-defined protobuf message formats for journal entries and page structures, so that I can implement durable write-ahead logging and B+Tree page storage.

**Why this priority**: Journal and page formats are essential for data durability and efficient storage, but they depend on the basic key-value models.

**Independent Test**: Can be tested by creating JournalEntry and Page messages, serializing them, and verifying that all metadata and data fields are correctly preserved.

**Acceptance Scenarios**:

1. **Given** a journal entry with PUT operation, **When** I serialize it to JournalEntryProto, **Then** the operation type, key, value, and timestamp are correctly encoded.
2. **Given** a page protobuf with entries, **When** I serialize and deserialize it, **Then** all page metadata (pageId, pageType, entries) are preserved.
3. **Given** a batch operation with multiple entries, **When** I create a JournalEntryProto with BATCH type, **Then** all entries are correctly encoded in the repeated field.

---

### User Story 4 - Metadata and Chunk Definitions (Priority: P2)

As a system administrator, I need metadata structures for chunks, segments, and backups, so that I can manage storage lifecycle and perform disaster recovery.

**Why this priority**: Metadata structures enable storage management and backup functionality, but they are secondary to core data operations.

**Independent Test**: Can be tested by creating ChunkMetadata and BackupMetadata messages, verifying that all tracking information is correctly stored.

**Acceptance Scenarios**:

1. **Given** a chunk with metadata, **When** I create ChunkMetadataProto, **Then** chunkId, status, size, and timestamps are correctly encoded.
2. **Given** a backup request, **When** I create BackupMetadataProto, **Then** backup type, timestamp, and chunk references are preserved.
3. **Given** segment location information, **When** I serialize it, **Then** chunkId, offset, and length are correctly encoded for data retrieval.

---

### Edge Cases

- What happens when key data exceeds maximum protobuf message size? (Should validate and reject oversized keys)
- How does the system handle null or empty byte arrays in keys and values? (Should throw validation errors)
- What happens when deserializing corrupted protobuf data? (Should detect and report deserialization errors)
- How does the system handle unknown enum values during deserialization? (Should preserve unknown values for forward compatibility)
- What happens when compression type is specified but data is not compressible? (Should handle gracefully without errors)

## Requirements *(mandatory)*

### Functional Requirements

- **FR-001**: System MUST provide IndexKey model with support for ordered byte comparison and custom key types
- **FR-002**: System MUST provide IndexValue model with support for normal values and tombstone markers
- **FR-003**: System MUST support protobuf serialization for all key-value models with zero data loss
- **FR-004**: System MUST define common enumeration types (KeyType, ValueType, OperationType, PageType, ChunkType, ChunkStatus, BackupType, CompressionType)
- **FR-005**: System MUST provide KeyProto and ValueProto message definitions for key-value serialization
- **FR-006**: System MUST provide JournalEntryProto message definition for write-ahead logging
- **FR-007**: System MUST provide PageProto message definition for B+Tree page storage
- **FR-008**: System MUST provide ChunkMetadataProto and BackupMetadataProto for storage management
- **FR-009**: System MUST support optional compression for value data (NONE, GZIP, LZ4, SNAPPY)
- **FR-010**: System MUST provide SegmentLocationProto for referencing data chunks
- **FR-011**: All protobuf messages MUST be backward compatible to support rolling upgrades
- **FR-012**: System MUST validate key and value data before serialization (no nulls, size limits)

### Key Entities

- **IndexKey**: Represents a sortable key with type information (ORDERED_BYTES or CUSTOM) and byte data
- **IndexValue**: Represents a value with type information (NORMAL or TOMBSTONE) and optional byte data
- **KeyProto**: Protobuf message for key serialization with keyType and keyData fields
- **ValueProto**: Protobuf message for value serialization with valueType, valueData, and optional compression fields
- **JournalEntryProto**: Protobuf message for journal entries with operation type, timestamp, and key-value pairs
- **PageProto**: Protobuf message for B+Tree pages with page metadata and entry data
- **ChunkMetadataProto**: Protobuf message for chunk tracking with chunkId, status, size, and timestamps
- **SegmentLocationProto**: Protobuf message for data location references with chunkId, offset, and length

## Success Criteria *(mandatory)*

### Measurable Outcomes

- **SC-001**: Key-value model serialization completes in under 1 microsecond per operation
- **SC-002**: Protobuf messages are 30-50% smaller than equivalent JSON serialization
- **SC-003**: All enum types support at least 200 future values through reserved field ranges
- **SC-004**: Backward compatibility is maintained across at least 3 major versions
- **SC-005**: Key comparison operations complete in under 100 nanoseconds
- **SC-006**: Zero data loss during serialization/deserialization across 1 million operations
- **SC-007**: Compression reduces value size by at least 50% for compressible data

## Assumptions

- Keys and values are byte arrays; the system does not interpret their semantic meaning
- Protobuf version 3 is used for all message definitions
- Maximum key size is 64KB; maximum value size is 4MB (configurable)
- All timestamps are in milliseconds since Unix epoch
- Compression is optional and applied only to value data, not keys
- The system uses append-only storage; in-place updates are not supported
- Forward compatibility is maintained through reserved field numbers in protobuf definitions
- All modules depend on this API module; breaking changes require coordination across all modules
