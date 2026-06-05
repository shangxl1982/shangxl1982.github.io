# Tree Metadata Persistence Implementation

Date: 2026-04-18

## Summary

Implemented tree metadata persistence to enable B+Tree loading from disk during KVStore startup.

## Problem

The tree metadata was being persisted to `tree-metadata.pb` but was never loaded during KVStore initialization. This meant:
- Tree version always started at 0 after restart
- Root location was null after restart
- Tree height was 0 after restart
- All data had to be recovered from journal replay only

## Solution

### 1. Created TreeMetadataManager Class

**File**: `lsmplus-core/src/main/java/org/hyperkv/lsmplus/core/TreeMetadataManager.java`

A new class to handle reading/writing tree metadata to `tree-metadata.pb`:
- `save(TreeVersionInfo)` - Saves tree metadata after dump
- `loadLatest()` - Loads the latest tree version info
- `loadAll()` - Loads all tree versions
- Atomic file writes using temp file + move pattern
- Magic number validation (0x54524545)
- Max 30 versions retained

### 2. Modified KVStore to Save Tree Metadata

**File**: `lsmplus-core/src/main/java/org/hyperkv/lsmplus/core/KVStore.java`

- Added `TreeMetadataManager` field and initialization
- Added `saveTreeMetadata()` method to save tree state after dump
- Modified `dump()` and `triggerDump()` to call `saveTreeMetadata()`

### 3. Modified KVStore to Load Tree Metadata on Startup

**File**: `lsmplus-core/src/main/java/org/hyperkv/lsmplus/core/KVStore.java`

- Modified `recover()` to load tree metadata before journal replay
- Sets `rootLocation`, `height`, and `version` from persisted metadata

### 4. Added Public Setters to BPlusTree

**File**: `lsmplus-bplustree/src/main/java/org/hyperkv/lsmplus/bplustree/BPlusTree.java`

- Made `setRootLocation()` public
- Made `setHeight()` public  
- Added `setVersion()` public method

## Files Changed

1. `lsmplus-core/src/main/java/org/hyperkv/lsmplus/core/TreeMetadataManager.java` - NEW
2. `lsmplus-core/src/main/java/org/hyperkv/lsmplus/core/KVStore.java` - MODIFIED
3. `lsmplus-bplustree/src/main/java/org/hyperkv/lsmplus/bplustree/BPlusTree.java` - MODIFIED
4. `lsmplus-bplustree/src/main/java/org/hyperkv/lsmplus/bplustree/TreeDumper.java` - MODIFIED
5. `lsmplus-bplustree/src/test/java/org/hyperkv/lsmplus/bplustree/BPlusTreeTest.java` - MODIFIED

## Test Results

- All 172 tests pass
- `KVServiceFullIntegrationTest` passes
- Tree version is correctly restored after restart (version >= 1)
- Tree height and root location are restored from metadata

## Additional Fixes

### 1. TreeDumper Merge with Existing Tree

**File**: `lsmplus-bplustree/src/main/java/org/hyperkv/lsmplus/bplustree/TreeDumper.java`

Fixed `buildTree()` to merge new entries with existing tree entries:
- If tree has existing data, scan all entries from existing tree
- Merge with new entries (new entries take precedence)
- Build new tree from merged result

### 2. BPlusTree.scanAll() Method

**File**: `lsmplus-bplustree/src/main/java/org/hyperkv/lsmplus/bplustree/BPlusTree.java`

Added `scanAll()` method to retrieve all entries from the tree for merging.

### 3. Test Method Rename

**File**: `lsmplus-bplustree/src/test/java/org/hyperkv/lsmplus/bplustree/BPlusTreeTest.java`

Renamed `testSetCurrentVersion()` to `testSetVersion()` to match the renamed method.

### 4. Journal Replay Point Optimization

**File**: `lsmplus-core/src/main/java/org/hyperkv/lsmplus/core/KVStore.java`

Modified `recover()` to use the journal replay point from tree metadata:
- If tree metadata has a replay point, replay journal from that point
- If no replay point, replay from beginning
- Avoids reprocessing journal entries already included in dumped tree

## Data Flow

```
Dump:
1. Memory table sealed
2. TreeDumper.dumpFromTreeMap() builds tree
3. saveTreeMetadata() saves:
   - version
   - rootLocation
   - height
   - replayPoint
   - stats

Recovery:
1. TreeMetadataManager.loadLatest() reads tree-metadata.pb
2. BPlusTree setters restore:
   - rootLocation
   - height
   - version
3. Journal replay applies any new entries
```
