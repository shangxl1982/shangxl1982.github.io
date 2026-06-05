# 2026-04-25-journal-region-manager-fix.md

## Issue Found in JournalRegionManager.allocateNewRegion()

### Problem Description
The `allocateNewRegion()` method in [JournalRegionManager.java](../lsmplus-kvstore/src/main/java/org/hyperkv/lsmplus/journal/JournalRegionManager.java) had a logic bug where it did not seal the current chunk before allocating a new region.

### Root Cause
When `allocateNewRegion()` was called because the current region was full (via `getCurrentRegion()`), it would:
1. Create a new chunk via `chunkManager.createJournalChunk()`
2. Create a new JournalRegion
3. Update the currentRegion reference

However, it **did not seal the old chunk** before creating the new one. This was inconsistent with the `rotateRegion()` method which explicitly seals the current chunk.

### Impact
1. **Resource Leak**: Old chunks could remain in OPEN status
2. **Data Integrity**: Unsealed chunks may not be properly recovered after a crash
3. **Inconsistency**: Manual rotation seals chunks, but automatic allocation didn't

### Fix Applied
Added chunk sealing logic before creating a new region:

```java
if (currentRegion != null) {
    Chunk currentChunk = chunkManager.getCurrentWriteChunk(ChunkType.CHUNK_JOURNAL);
    if (currentChunk != null && currentChunk.getStatus() == ChunkStatus.OPEN) {
        currentChunk.seal();
        log.debug("Sealed current journal chunk before allocating new region: chunkId={}", currentChunk.getChunkId());
    }
}
```

### Testing
- All journal-related tests pass successfully
- No regression in existing functionality
- Chunk sealing now happens consistently in both automatic allocation and manual rotation

### Files Modified
- [JournalRegionManager.java](../lsmplus-kvstore/src/main/java/org/hyperkv/lsmplus/journal/JournalRegionManager.java): Added chunk sealing logic in `allocateNewRegion()` method

### Related Code
- `rotateRegion()` method already had this logic
- `getCurrentRegion()` calls `allocateNewRegion()` when region is full
- `isRegionFull()` checks if chunk size exceeds threshold
