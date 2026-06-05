# Module Organization

## Module Structure

Based on design-package-structure.md, the project is organized into the following modules:

### 1. api Module
**Package:** `org.hyperkv.lsmplus.api`
**Status:** ✅ Implemented

| Class | Description |
|-------|-------------|
| IndexKey | Key wrapper with proto conversion |
| IndexValue | Value wrapper with tombstone support |

### 2. storage Module
**Package:** `org.hyperkv.lsmplus.storage`
**Status:** ✅ Implemented

| Class | Description |
|-------|-------------|
| Chunk | Storage chunk with lifecycle management |
| ChunkHeader | 4096B header with UUID, ChunkType |
| ChunkManager | Chunk allocation and management |
| SegmentLocation | Physical location (chunkId, offset, length) |
| StorageLayout | Directory structure management |
| WriteItem | Data integrity wrapper (CRC32, 4K align) |

### 3. journal Module
**Package:** `org.hyperkv.lsmplus.journal`
**Status:** ✅ Implemented

| Class | Description |
|-------|-------------|
| Journal | Core journal (write, replay, rotate) |
| JournalEntry | Entry with operation type, key, value |
| JournalRegion | Region with major, minor, chunkId |
| JournalRegionIndexFile | Persistent region metadata |
| JournalReplayPoint | Replay position (regionMajor, offset) |
| JournalReplayHandler | Callback interface for replay |
| JournalWriter | Batching wrapper |
| JournalReplayException | Replay error handling |

### 4. memory Module
**Package:** `org.hyperkv.lsmplus.memory`
**Status:** ✅ Implemented

| Class | Description |
|-------|-------------|
| MemoryTable | In-memory sorted key-value store |
| MemoryTableManager | Active/sealed table management |

### 5. bplustree Module
**Package:** `org.hyperkv.lsmplus.bplustree`
**Status:** 🔄 In Progress

| Class | Description | Status |
|-------|-------------|--------|
| Page | Base class for pages | ✅ |
| LeafPage | Leaf node with key-value pairs | ✅ |
| IndexPage | Index node with child locations | ✅ |
| BPlusTree | B+Tree main class | 🔄 |
| PageManager | Page load/save via ChunkManager | 📋 |
| PageCache | LRU cache for pages | 📋 |
| WriteBuffer | Write cache for dump | 📋 |

### 6. utils Module
**Package:** `org.hyperkv.lsmplus.utils`
**Status:** ✅ Implemented

| Class | Description |
|-------|-------------|
| CRC32Util | CRC32 calculation and validation |
| AlignmentUtil | 4K alignment utilities |
| MagicUtil | Magic number validation |

### 7. exception Module
**Package:** `org.hyperkv.lsmplus.exception`
**Status:** 📋 Pending

### 8. config Module
**Package:** `org.hyperkv.lsmplus.config`
**Status:** 📋 Pending

### 9. monitoring Module
**Package:** `org.hyperkv.lsmplus.monitoring`
**Status:** 📋 Pending

### 10. backup Module
**Package:** `org.hyperkv.lsmplus.backup`
**Status:** 📋 Pending

### 11. service Module
**Package:** `org.hyperkv.lsmplus.service`
**Status:** 📋 Pending

## Module Dependencies

```
service → core → memory/journal → storage → utils
           ↓
        config/monitoring/backup

All modules → api/utils/exception
```

## Current Implementation Priority

1. **bplustree** - Complete B+Tree core (PageManager, WriteBuffer, Tree Dump)
2. **core** - KVStore main implementation
3. **config** - Configuration management
4. **monitoring** - Metrics and health checks
5. **backup** - Backup and recovery
6. **service** - REST/gRPC services
