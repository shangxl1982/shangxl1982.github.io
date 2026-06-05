# Lazy Chunk Loading Implementation

**Date**: 2026-04-20

## Problem

The original implementation opened all chunk files at startup, keeping file descriptors open for all chunks. With a large number of chunks, this could exhaust file descriptors.

## Solution

Implemented lazy loading for chunks:

1. Load chunk metadata from `chunk-metadata.pb` at startup (no file opening)
2. Keep `ChunkInfo` objects in memory (lightweight, no file handles)
3. Open `Chunk` objects lazily when data access is needed
4. Use LRU cache for open chunks with configurable size

## Changes

### New Files

1. **[ChunkInfo.java](file:///home/wisefox/git/hyperkvstore/lsmplus-storage/src/main/java/org/hyperkv/lsmplus/storage/ChunkInfo.java)**
   - Lightweight class holding chunk metadata without file handle
   - Contains: chunkId, chunkNumber, chunkType, status, createdAt, keepAliveTime, sizes
   - Can be created from `ChunkMetadataFile.ChunkEntry`
   - Provides `isKeepAliveExpired()` method for status checks

2. **[ChunkCache.java](file:///home/wisefox/git/hyperkvstore/lsmplus-storage/src/main/java/org/hyperkv/lsmplus/storage/ChunkCache.java)**
   - LRU cache for open `Chunk` objects
   - Configurable maximum size (default: 64)
   - Automatically evicts and closes least-recently-used chunks
   - Thread-safe with lock-based synchronization

### Modified Files

1. **[ChunkManager.java](file:///home/wisefox/git/hyperkvstore/lsmplus-storage/src/main/java/org/hyperkv/lsmplus/storage/ChunkManager.java)**
   - Replaced `Map<UUID, Chunk> chunkCache` with `Map<UUID, ChunkInfo> chunkInfos`
   - Added `ChunkCache openChunkCache` for LRU caching of open chunks
   - Added constructor with configurable cache size
   - `loadFromMetadataFile()` now creates `ChunkInfo` objects instead of opening chunks
   - Removed `discoverExistingChunks()` - no longer needed
   - `getChunk(UUID)` now lazily opens chunks for read
   - `getChunkForRead(UUID)` - opens chunk for read-only access
   - `getCurrentWriteChunk(ChunkType)` - returns current write chunk (for sealing)
   - `listChunkInfos(ChunkType)` - returns lightweight metadata
   - `getOpenChunkInfos()` / `getSealedChunkInfos()` - status-based queries
   - Updated `sealExpiredChunks()` to work with `ChunkInfo`

2. **[Chunk.java](file:///home/wisefox/git/hyperkvstore/lsmplus-storage/src/main/java/org/hyperkv/lsmplus/storage/Chunk.java)**
   - Added `openForWrite(ChunkInfo)` - opens existing chunk for write access
   - Added `openForRead(ChunkInfo)` - opens existing chunk for read-only access

3. **[GarbageCollector.java](file:///home/wisefox/git/hyperkvstore/lsmplus-kvstore/src/main/java/org/hyperkv/lsmplus/gc/GarbageCollector.java)**
   - Updated to use `ChunkInfo` instead of `Chunk`
   - Uses `listChunkInfos()` for finding GC candidates

4. **[Journal.java](file:///home/wisefox/git/hyperkvstore/lsmplus-kvstore/src/main/java/org/hyperkv/lsmplus/journal/Journal.java)**
   - Updated `discoverExistingRegions()` to use `listChunkInfos()`
   - Updated `rotateChunk()`, `close()`, `ensureCurrentRegion()` to use `getCurrentWriteChunk()`
   - Added status check before sealing to avoid double-seal error

## Architecture

```
┌─────────────────────────────────────────────────────────────────┐
│                        ChunkManager                              │
├─────────────────────────────────────────────────────────────────┤
│  chunkInfos: Map<UUID, ChunkInfo>  (always in memory)           │
│  ┌─────────────────────────────────────────────────────────┐    │
│  │ ChunkInfo { chunkId, status, type, sizes, keepAlive }   │    │
│  │ - No file handle                                         │    │
│  │ - Lightweight, can hold thousands                        │    │
│  └─────────────────────────────────────────────────────────┘    │
│                                                                  │
│  openChunkCache: ChunkCache (LRU, default 64 entries)           │
│  ┌─────────────────────────────────────────────────────────┐    │
│  │ Chunk { file, raf, header, status }                     │    │
│  │ - Has open file handle                                   │    │
│  │ - Opened lazily on demand                                │    │
│  │ - Evicted when cache is full                            │    │
│  └─────────────────────────────────────────────────────────┘    │
│                                                                  │
│  currentLeafChunk, currentIndexChunk, currentJournalChunk       │
│  - Always open for write while KVStore is running               │
│  - Extended by periodic task                                    │
└─────────────────────────────────────────────────────────────────┘
```

## Benefits

1. **Reduced file descriptors**: Only open chunks are kept in memory (default 64 + 3 current)
2. **Faster startup**: No need to open all chunk files
3. **Scalability**: Can handle millions of chunks without exhausting resources
4. **Configurable cache**: Cache size can be tuned based on available resources

## Test Results

All tests pass after the refactoring.
