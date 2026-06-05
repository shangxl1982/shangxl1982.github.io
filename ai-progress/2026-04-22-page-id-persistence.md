# Page ID Persistence in Tree Metadata

**Date**: 2026-04-22

## Summary

Added persistence of next page IDs (for leaf and index pages) in tree metadata to ensure proper recovery and continuation of page ID sequences after restart.

## Background

The B+Tree uses separate page ID sequences for leaf pages (positive IDs starting from 1) and index pages (negative IDs starting from Long.MIN_VALUE). These sequences need to be persisted and restored to prevent:
1. Page ID collisions after recovery
2. Loss of page ID state between restarts
3. Potential corruption from reusing page IDs

## Changes Made

### 1. Proto Structure (metadata.proto)

The proto structure already had the necessary fields defined:

```protobuf
message PageIdTracker {
    int64 next_index_page_id = 1; // 下一个索引页 ID
    int64 next_leaf_page_id = 2;  // 下一个叶页 ID
}

message TreeMetadataEntry {
    int64 version = 1;
    SegmentLocationProto root_location = 2;
    JournalReplayPointProto replay_point = 3;
    PageIdTracker page_id_tracker = 4; // 页面 ID 跟踪器
    int64 mns = 5;
    int64 created_at = 6;
    TreeStats stats = 7;
}
```

### 2. TreeMetadataManager.TreeVersionInfo

**File**: [TreeMetadataManager.java](file:///home/wisefox/git/hyperkvstore/lsmplus-kvstore/src/main/java/org/hyperkv/lsmplus/core/TreeMetadataManager.java)

Added fields to store next page IDs:
```java
public static class TreeVersionInfo {
    private final long nextLeafPageId;
    private final long nextIndexPageId;
    
    public TreeVersionInfo(long version, SegmentLocation rootLocation, 
                           JournalReplayPoint replayPoint, long mns,
                           long leafPageCount, long indexPageCount, 
                           long totalEntries, int height, long totalSize,
                           long nextLeafPageId, long nextIndexPageId) {
        // ... constructor implementation
    }
    
    public long getNextLeafPageId() { return nextLeafPageId; }
    public long getNextIndexPageId() { return nextIndexPageId; }
}
```

### 3. Serialization (toProto)

**File**: [TreeMetadataManager.java](file:///home/wisefox/git/hyperkvstore/lsmplus-kvstore/src/main/java/org/hyperkv/lsmplus/core/TreeMetadataManager.java)

Added PageIdTracker serialization:
```java
private TreeMetadataEntry toProto(TreeVersionInfo info) {
    TreeMetadataEntry.Builder builder = TreeMetadataEntry.newBuilder()
            .setVersion(info.getVersion())
            .setMns(info.getMns())
            .setCreatedAt(info.getCreatedAt());
    
    // ... other fields ...
    
    PageIdTracker pageIdTracker = PageIdTracker.newBuilder()
            .setNextIndexPageId(info.getNextIndexPageId())
            .setNextLeafPageId(info.getNextLeafPageId())
            .build();
    builder.setPageIdTracker(pageIdTracker);
    
    return builder.build();
}
```

### 4. Deserialization (fromProto)

**File**: [TreeMetadataManager.java](file:///home/wisefox/git/hyperkvstore/lsmplus-kvstore/src/main/java/org/hyperkv/lsmplus/core/TreeMetadataManager.java)

Added PageIdTracker deserialization with default values:
```java
private TreeVersionInfo fromProto(TreeMetadataEntry entry) {
    // ... other fields ...
    
    long nextLeafPageId = 1L;
    long nextIndexPageId = Long.MIN_VALUE;
    if (entry.hasPageIdTracker()) {
        PageIdTracker tracker = entry.getPageIdTracker();
        nextLeafPageId = tracker.getNextLeafPageId();
        nextIndexPageId = tracker.getNextIndexPageId();
    }
    
    return new TreeVersionInfo(
            entry.getVersion(),
            rootLocation,
            replayPoint,
            entry.getMns(),
            stats.getLeafPageCount(),
            stats.getIndexPageCount(),
            stats.getTotalEntries(),
            stats.getHeight(),
            stats.getTotalSize(),
            nextLeafPageId,
            nextIndexPageId
    );
}
```

### 5. Dump Persistence

**File**: [TreeDumper.java](file:///home/wisefox/git/hyperkvstore/lsmplus-kvstore/src/main/java/org/hyperkv/lsmplus/bplustree/TreeDumper.java)

Added page ID retrieval during dump:
```java
public TreeMetadataManager.TreeVersionInfo dump(List<Map.Entry<IndexKey, IndexValue>> sortedEntries, 
                                                  JournalReplayPoint replayPoint) throws KVStoreException {
    // ... dump logic ...
    
    long[] pageIdState = tree.getPageIdManager().getStateForPersistence();
    long nextLeafPageId = pageIdState[0] + 1;  // Convert max to next
    long nextIndexPageId = pageIdState[1] + 1; // Convert min to next
    
    return new TreeMetadataManager.TreeVersionInfo(
            tree.getVersion(),
            tree.getWriterRoot().getLocation(),
            replayPoint,
            mns,
            tree.getLeafPageCount(),
            tree.getIndexPageCount(),
            tree.getTotalEntryCount(),
            tree.getHeight(),
            tree.getTotalTreeSize(),
            nextLeafPageId,
            nextIndexPageId
    );
}
```

### 6. Recovery

**File**: [BPlusTree.java](file:///home/wisefox/git/hyperkvstore/lsmplus-kvstore/src/main/java/org/hyperkv/lsmplus/bplustree/BPlusTree.java)

Added page ID restoration during recovery:
```java
public void startFrom(TreeMetadataManager.TreeVersionInfo treeInfo) {
    setRootLocation(treeInfo.getRootLocation());
    setHeight(treeInfo.getHeight());
    setVersion(treeInfo.getVersion());
    readerRoot.set(pageManager.getPage(treeInfo.getRootLocation()));
    writerRoot = new Page(readerRoot.get());
    
    // Restore page ID sequences
    pageIdManager.updateSequences(
        treeInfo.getNextLeafPageId() - 1,   // Convert next to max
        treeInfo.getNextIndexPageId() - 1   // Convert next to min
    );
    log.info("Restored page ID sequences: nextLeafPageId={}, nextIndexPageId={}", 
        treeInfo.getNextLeafPageId(), treeInfo.getNextIndexPageId());
}
```

## Page ID State Management

### PageIdManager Methods

**File**: [PageIdManager.java](file:///home/wisefox/git/hyperkvstore/lsmplus-kvstore/src/main/java/org/hyperkv/lsmplus/bplustree/PageIdManager.java)

Key methods used:
- `getStateForPersistence()`: Returns `[maxLeafPageId, minIndexPageId]`
- `updateSequences(maxLeafPageId, minIndexPageId)`: Restores sequences from persisted values

### Conversion Logic

**During Dump (Persistence)**:
- `maxLeafPageId` → `nextLeafPageId = maxLeafPageId + 1`
- `minIndexPageId` → `nextIndexPageId = minIndexPageId + 1`

**During Recovery (Restoration)**:
- `nextLeafPageId` → `maxLeafPageId = nextLeafPageId - 1`
- `nextIndexPageId` → `minIndexPageId = nextIndexPageId - 1`

## Backward Compatibility

The implementation maintains backward compatibility:
1. If `PageIdTracker` is not present in metadata (old format), default values are used:
   - `nextLeafPageId = 1` (start of leaf page sequence)
   - `nextIndexPageId = Long.MIN_VALUE` (start of index page sequence)
2. This allows recovery from old metadata files without errors
3. New dumps will always include the `PageIdTracker`

## Testing

All tests pass successfully:
- Build: `./gradlew clean build -x test` ✓
- Tests: `./gradlew :lsmplus-kvstore:test` ✓

## Benefits

1. **Data Integrity**: Prevents page ID collisions after recovery
2. **State Preservation**: Maintains page ID sequences across restarts
3. **Corruption Prevention**: Avoids reusing page IDs that might still be referenced
4. **Backward Compatibility**: Works with old metadata format
5. **Clear Logging**: Provides visibility into page ID restoration

## Example Flow

### Dump (Save)
```
1. TreeDumper.dump() completes tree build
2. Get current page ID state: [maxLeaf=100, minIndex=-50]
3. Convert to next IDs: [nextLeaf=101, nextIndex=-49]
4. Create TreeVersionInfo with next IDs
5. TreeMetadataManager.save() persists to metadata.pb
```

### Recovery (Restore)
```
1. KVStore.recover() loads metadata
2. TreeMetadataManager.loadLatest() returns TreeVersionInfo
3. BPlusTree.startFrom() restores tree state
4. PageIdManager.updateSequences() restores IDs: [maxLeaf=100, minIndex=-50]
5. Next allocations continue from: [nextLeaf=101, nextIndex=-49]
```

## Related Files

- Proto: [metadata.proto](file:///home/wisefox/git/hyperkvstore/lsmplus-api/src/main/proto/metadata.proto)
- Manager: [TreeMetadataManager.java](file:///home/wisefox/git/hyperkvstore/lsmplus-kvstore/src/main/java/org/hyperkv/lsmplus/core/TreeMetadataManager.java)
- Dumper: [TreeDumper.java](file:///home/wisefox/git/hyperkvstore/lsmplus-kvstore/src/main/java/org/hyperkv/lsmplus/bplustree/TreeDumper.java)
- BPlusTree: [BPlusTree.java](file:///home/wisefox/git/hyperkvstore/lsmplus-kvstore/src/main/java/org/hyperkv/lsmplus/bplustree/BPlusTree.java)
- PageIdManager: [PageIdManager.java](file:///home/wisefox/git/hyperkvstore/lsmplus-kvstore/src/main/java/org/hyperkv/lsmplus/bplustree/PageIdManager.java)
