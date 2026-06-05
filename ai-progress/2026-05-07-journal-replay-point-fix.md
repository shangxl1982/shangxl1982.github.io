# Journal Replay Point Tracking Fix

## Problem

The `replayChunkFromOffset` method in `JournalRegionManager` did not track the journal replay point during recovery. This caused the following issues:

1. After recovery, the recovered memory table did not have a valid journal replay point
2. When the tree was dumped, the `SealedTablesMerger.findMaxReplayPoint()` would return `null`
3. The tree metadata would be saved with `null` replay point
4. On next recovery, the replay point would reset to 0, causing duplicate replay of journal entries

## Root Cause

The `RecoveryHandler.handle()` method was passing `null` as the `JournalReplayPoint` parameter to `memoryTableManager.put()`. This was because:

1. `JournalReplayHandler.handle()` only accepted `JournalEntry`, not the replay point
2. `replayChunkFromOffset` didn't track or return the last replayed offset
3. `replayFrom` didn't return the final replay point

## Solution

### 1. Created `ReplayResult` class

New class to hold both error count and last replay point:

```java
public class ReplayResult {
    private final int errorCount;
    private final JournalReplayPoint lastReplayPoint;
    // ...
}
```

### 2. Updated `JournalReplayHandler.handle()` signature

Changed from:
```java
void handle(JournalEntry entry);
```

To:
```java
void handle(JournalEntry entry, JournalReplayPoint replayPoint);
```

### 3. Updated `replayChunkFromOffset` method

- Added `ChunkReplayResult` inner class to track both error count and last offset
- Method now tracks the last successfully replayed offset
- Passes the current replay point to the handler for each entry

### 4. Updated `replayFrom` method

- Now returns `ReplayResult` instead of `void`
- Tracks the last replay point across all regions
- Returns the final replay point to the caller

### 5. Updated `RecoveryHandler`

- Now receives the replay point from the handler
- Passes the replay point to `memoryTableManager.put()` and `memoryTableManager.delete()`
- This ensures the memory table tracks the correct replay point range

## Files Changed

1. `lsmplus-kvstore/src/main/java/org/hyperkv/lsmplus/journal/ReplayResult.java` - New file
2. `lsmplus-kvstore/src/main/java/org/hyperkv/lsmplus/journal/JournalReplayHandler.java` - Updated interface
3. `lsmplus-kvstore/src/main/java/org/hyperkv/lsmplus/journal/JournalRegionManager.java` - Updated replay methods
4. `lsmplus-kvstore/src/main/java/org/hyperkv/lsmplus/core/RecoveryHandler.java` - Updated to pass replay point

## Test Updates

Updated the following test files to use the new `JournalReplayHandler` signature:

1. `JournalReplayTest.java`
2. `JournalTest.java`
3. `JournalIntegrationTest.java`
4. `JournalBatchWriteTest.java`

## Verification

All tests pass:
- `./gradlew :lsmplus-kvstore:test --tests "org.hyperkv.lsmplus.journal.*"` - PASSED
- `./gradlew :lsmplus-kvstore:test --tests "org.hyperkv.lsmplus.core.*Test"` - PASSED

---

## Issue 2: Total Entry Count Not Restored on Recovery

### Problem

When opening an existing KVStore, the `totalEntries` stat was not correctly recovered. For example:
- Version 7 had 49938 entries
- After recovery with 62 new entries, Version 8 showed only 63 entries instead of 50000

### Root Cause

The `BPlusTree.startFrom()` method did not restore the `totalEntryCount` from the `TreeVersionInfo`:

```java
public void startFrom(TreeMetadataManager.TreeVersionInfo treeInfo) {
    setRootLocation(treeInfo.getRootLocation());
    setHeight(treeInfo.getHeight());
    setVersion(treeInfo.getVersion());
    // Missing: setTotalEntryCount()
    ...
}
```

### Solution

Added restoration of `totalEntryCount` in `BPlusTree.startFrom()`:

```java
public void startFrom(TreeMetadataManager.TreeVersionInfo treeInfo) {
    setRootLocation(treeInfo.getRootLocation());
    setHeight(treeInfo.getHeight());
    setVersion(treeInfo.getVersion());
    setTotalEntryCount((int) treeInfo.getTotalEntries());  // Added
    ...
}
```

### File Changed

- `lsmplus-kvstore/src/main/java/org/hyperkv/lsmplus/bplustree/BPlusTree.java` - Added `setTotalEntryCount()` call in `startFrom()`

### Verification

All tests pass:
- `./gradlew :lsmplus-kvstore:test --tests "org.hyperkv.lsmplus.bplustree.*"` - PASSED

---

## Issue 3: Add Tree Revert Command to DiagnosticTool

### Feature

Added a new `tree-revert` command to the DiagnosticTool that allows removing the latest tree metadata entry, effectively reverting the tree to a previous version.

### Files Changed

1. `lsmplus-kvstore/src/main/java/org/hyperkv/lsmplus/core/TreeMetadataManager.java`
   - Added `removeLatestEntry()` method
   - Added `getEntryCount()` method

2. `tools/src/main/java/org/hyperkv/lsmplus/tools/DiagnosticTool.java`
   - Added `TreeRevertCommand` class
   - Added `revertTreeMetadata()` method

### Usage

```bash
# Dry run - see what would be removed without making changes
./gradlew :tools:run --args="tree-revert -n /path/to/kvstore"

# Actually revert to previous version
./gradlew :tools:run --args="tree-revert /path/to/kvstore"

# JSON output
./gradlew :tools:run --args="tree-revert -j /path/to/kvstore"
```

### Example Output

```
=== Tree Metadata Revert ===
File: /home/wisefox/vd/demo-kvstore/tree-metadata.pb

Current version to remove: 8
  Total Entries: 63

Will revert to version: 7
  Total Entries: 49938

Remaining entries after revert: 7

Dry run - no changes made
```
