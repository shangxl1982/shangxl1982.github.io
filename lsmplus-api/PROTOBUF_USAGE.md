# Protobuf Type Usage Guide

## Overview

This document provides comprehensive documentation for using protobuf types in the LSM Plus key-value store system.

## Common Types (common.proto)

### KeyType

**Purpose**: Defines the type of key used in the system.

**Values**:
- `ORDERED_BYTES` (0): Keys that support byte-by-byte comparison for sorting
- `CUSTOM` (1): User-defined key formats

**Usage**:
```java
IndexKey orderedKey = IndexKey.orderedBytes("user:12345".getBytes());
IndexKey customKey = IndexKey.custom(customKeyData);
```

**Reserved Fields**: 100-200 for future expansion

---

### ValueType

**Purpose**: Defines the type of value stored in the system.

**Values**:
- `NORMAL` (0): Regular data value
- `TOMBSTONE` (1): Deletion marker

**Usage**:
```java
IndexValue normalValue = IndexValue.normal("data".getBytes());
IndexValue tombstone = IndexValue.tombstone();
```

**Reserved Fields**: 100-200 for future expansion

---

### OperationType

**Purpose**: Defines the type of journal operation.

**Values**:
- `PUT` (0): Insert or update operation (1 entry)
- `DELETE` (1): Delete operation (1 entry with TOMBSTONE value)
- `BATCH` (2): Batch operation (multiple entries)

**Usage**:
```java
JournalEntryProto putEntry = JournalEntryProto.newBuilder()
    .setOperationType(OperationType.PUT)
    .addEntries(keyValuePair)
    .setTimestamp(System.currentTimeMillis())
    .build();
```

**Reserved Fields**: 100-200 for future expansion

---

### PageType

**Purpose**: Defines the type of B+Tree page.

**Values**:
- `PAGE_LEAF` (0): Leaf page containing key-value pairs
- `PAGE_BRANCH` (1): Branch page (intermediate index page) containing key-location pairs
- `PAGE_ROOT` (2): Root page (top-level index page) containing key-location pairs

**Usage**:
```java
PageProto leafPage = PageProto.newBuilder()
    .setPageType(PageType.PAGE_LEAF)
    .setPageId(123)
    .build();

PageProto rootPage = PageProto.newBuilder()
    .setPageType(PageType.PAGE_ROOT)
    .setPageId(-1)
    .build();
```

**Reserved Fields**: 100-200 for future expansion

---

### ChunkType

**Purpose**: Defines the type of storage chunk.

**Values**:
- `CHUNK_INDEX` (0): Chunk containing index page data
- `CHUNK_LEAF` (1): Chunk containing leaf page data
- `CHUNK_JOURNAL` (2): Chunk containing journal data

**Reserved Fields**: 100-200 for future expansion

---

### ChunkStatus

**Purpose**: Defines the lifecycle status of a chunk.

**Values**:
- `OPEN` (0): Chunk is open for writes
- `SEALED` (1): Chunk is sealed and read-only
- `DELETING` (2): Chunk is being deleted
- `DELETED` (3): Chunk has been deleted

**Reserved Fields**: 100-200 for future expansion

---

### BackupType

**Purpose**: Defines the type of backup.

**Values**:
- `FULL` (0): Full backup of all data
- `INCREMENTAL` (1): Incremental backup since last backup

**Reserved Fields**: 100-200 for future expansion

---

### CompressionType

**Purpose**: Defines the compression algorithm for value data.

**Values**:
- `NONE` (0): No compression
- `GZIP` (1): GZIP compression
- `LZ4` (2): LZ4 compression
- `SNAPPY` (3): Snappy compression

**Reserved Fields**: 100-200 for future expansion

---

## Key-Value Messages (keyvalue.proto)

### KeyProto

**Purpose**: Protobuf message for key serialization.

**Fields**:
- `key_type` (1): KeyType enum
- `key_data` (2): Byte array containing key data

**Usage**:
```java
IndexKey key = IndexKey.orderedBytes("my-key".getBytes());
KeyProto proto = key.toProto();
IndexKey restored = IndexKey.fromProto(proto);
```

---

### ValueProto

**Purpose**: Protobuf message for value serialization.

**Fields**:
- `value_type` (1): ValueType enum
- `value_data` (2): Byte array containing value data (empty for TOMBSTONE)

**Usage**:
```java
IndexValue value = IndexValue.normal("my-value".getBytes());
ValueProto proto = value.toProto();
IndexValue restored = IndexValue.fromProto(proto);
```

---

### KeyValuePairProto

**Purpose**: Unified key-value pair message used across Journal, LeafPage, and IndexPage.

**Fields**:
- `key` (1): KeyProto message
- `value` (2): ValueProto message (for leaf pages and journal)
- `location` (3): SegmentLocationProto message (for index pages)

**Usage**:
```java
KeyValuePairProto pair = KeyValuePairProto.newBuilder()
    .setKey(key.toProto())
    .setValue(value.toProto())
    .build();
```

---

### SegmentLocationProto

**Purpose**: References data location in chunks.

**Fields**:
- `chunk_id_most_sig` (1): Chunk UUID high 64 bits
- `chunk_id_least_sig` (2): Chunk UUID low 64 bits
- `offset` (3): Offset within chunk (supports negative for virtual locations)
- `length` (4): Data length

**Size**: Fixed 28 bytes when serialized

---

## Journal Messages (journal.proto)

### JournalEntryProto

**Purpose**: Represents a single journal entry.

**Fields**:
- `operation_type` (1): OperationType enum
- `timestamp` (2): Operation timestamp (milliseconds since epoch)
- `sequence_number` (3): Sequence number for ordering
- `entries` (4): Repeated KeyValuePairProto (1 for PUT/DELETE, N for BATCH)

**Usage**:
```java
JournalEntryProto entry = JournalEntryProto.newBuilder()
    .setOperationType(OperationType.PUT)
    .setTimestamp(System.currentTimeMillis())
    .setSequenceNumber(12345L)
    .addEntries(keyValuePair)
    .build();
```

---

### JournalReplayPointProto

**Purpose**: Marks the starting point for journal replay.

**Fields**:
- `region_major` (1): Region major version
- `region_minor` (2): Region minor version
- `offset` (3): Offset within region

---

## Page Messages (page.proto)

### PageProto

**Purpose**: Represents a complete B+Tree page.

**Fields**:
- `page_type` (1): PageType enum
- `page_id` (2): Page ID (positive for leaf, negative for index)
- `max_size` (3): Maximum page capacity in bytes
- `used_size` (4): Current used size in bytes
- `entries` (5): Repeated KeyValuePairProto

**Usage**:
```java
PageProto page = PageProto.newBuilder()
    .setPageType(PageType.PAGE_LEAF)
    .setPageId(123L)
    .setMaxSize(4096)
    .setUsedSize(1024)
    .addEntries(entry1)
    .addEntries(entry2)
    .build();
```

---

## Metadata Messages (metadata.proto)

### TreeMetadataFile

**Purpose**: Root message for tree metadata file.

**Fields**:
- `magic` (1): Magic number for format validation
- `format_version` (2): File format version
- `leaf_page_max_size` (3): Maximum leaf page size
- `index_page_max_size` (4): Maximum index page size
- `entries` (5): Repeated TreeMetadataEntry
- `max_versions` (6): Maximum number of versions to retain

---

### ChunkMetadata

**Purpose**: Metadata for a single chunk.

**Fields**:
- `chunk_id_most_sig` (1): Chunk UUID high 64 bits
- `chunk_id_least_sig` (2): Chunk UUID low 64 bits
- `chunk_number` (3): Chunk sequence number
- `chunk_type` (4): ChunkType enum
- `owner_id_most_sig` (5): Owner UUID high 64 bits
- `owner_id_least_sig` (6): Owner UUID low 64 bits
- `namespace_id_most_sig` (7): Namespace UUID high 64 bits
- `namespace_id_least_sig` (8): Namespace UUID low 64 bits
- `status` (9): ChunkStatus enum
- `created_at` (10): Creation timestamp
- `keep_alive_time` (11): Keep-alive timestamp
- `total_size` (12): Total chunk size
- `used_size` (13): Used size
- `occupancy_size` (14): Valid data size
- `pending_gc` (15): Partial GC pending flag

---

### BackupMetadata

**Purpose**: Metadata for backup management.

**Fields**:
- `backup_id` (1): Unique backup identifier
- `backup_type` (2): BackupType enum
- `created_at` (3): Creation timestamp
- `tree_version` (4): Associated tree version
- `tree_mns` (5): Associated MNS
- `replay_point` (6): Journal replay starting point
- `cutoff_point` (7): Journal cutoff point
- `parent_backup_id` (8): Parent backup ID (for incremental)
- `tree_stats` (9): Tree statistics
- `checksum` (10): Backup checksum

---

## Best Practices

### 1. Backward Compatibility

- **Never change field numbers**: Field numbers must remain stable across versions
- **Use reserved fields**: Reserve field numbers 100-200 for future expansion
- **Add new fields only**: Never remove or rename existing fields
- **Use optional fields**: New fields should be optional with sensible defaults

### 2. Performance

- **Reuse builders**: Protobuf builders can be reused for better performance
- **Batch operations**: Use BATCH operation type for multiple operations
- **Compression**: Use compression for large value data

### 3. Validation

- **Null checks**: Always validate null inputs before creating protobuf messages
- **Size limits**: Enforce maximum key (64KB) and value (4MB) size limits
- **Type validation**: Ensure correct enum values are used

### 4. Error Handling

- **Deserialization errors**: Catch and handle InvalidProtocolBufferException
- **Unknown fields**: Protobuf preserves unknown fields for forward compatibility
- **Default values**: Be aware of protobuf default values for missing fields

---

## Migration Guide

### Adding New Enum Values

1. Add new value to the enum definition
2. Update reserved field range if needed
3. Regenerate Java classes
4. Update application code to handle new value
5. Deploy in rolling update fashion

### Adding New Message Fields

1. Add new field with next available field number
2. Make field optional with default value
3. Update application code to handle new field
4. Test backward compatibility with old data
5. Deploy in rolling update fashion

---

## Testing

All protobuf types should be tested for:
- Serialization/deserialization round-trip
- Backward compatibility across versions
- Performance benchmarks
- Edge cases (null, empty, oversized data)

See test files in `src/test/java/org/hyperkv/lsmplus/api/model/` for examples.
