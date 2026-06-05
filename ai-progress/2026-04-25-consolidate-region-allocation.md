# 2026-04-25-consolidate-region-allocation.md

## Consolidated Journal Region Allocation Logic

### Problem
The `rotateRegion()` method was not being called by any production code, and there was duplication between `allocateNewRegion()` and `rotateRegion()` methods. Both methods needed to seal the current chunk before creating a new region.

### Solution
Consolidated the logic into a single private method `allocateNewRegion(boolean force)` with two public wrappers:

1. **`allocateNewRegion()`** - Automatic allocation when region is full
   - Calls `allocateNewRegion(false)`
   - Returns current region if not full
   - Used internally by `getCurrentRegion()`

2. **`rotateRegion()`** - Manual rotation for administrative purposes
   - Calls `allocateNewRegion(true)` with force flag
   - Always creates a new region regardless of current region status
   - Records performance metrics
   - Provides clear API for manual operations

### Implementation Details

#### Core Logic (private method)
```java
private JournalRegion allocateNewRegion(boolean force) throws IOException {
    allocationLock.lock();
    try {
        // Skip check if force=true (manual rotation)
        if (!force && currentRegion != null && !isRegionFull(currentRegion)) {
            return currentRegion;
        }
        
        // Seal current chunk before creating new one
        if (currentRegion != null) {
            Chunk currentChunk = chunkManager.getCurrentWriteChunk(ChunkType.CHUNK_JOURNAL);
            if (currentChunk != null && currentChunk.getStatus() == ChunkStatus.OPEN) {
                currentChunk.seal();
                log.debug("Sealed current journal chunk before allocating new region: chunkId={}", currentChunk.getChunkId());
            }
        }
        
        // Create new region
        long newMajor = regionMajorCounter.incrementAndGet();
        UUID chunkId = chunkManager.createJournalChunk();
        JournalRegion newRegion = new JournalRegion(newMajor, 0, chunkId);
        regions.put(newMajor, newRegion);
        currentRegion = newRegion;
        
        persistRegionInfo();
        
        log.info("Allocated new journal region: major={}, chunkId={}", newMajor, chunkId);
        return newRegion;
    } finally {
        allocationLock.unlock();
    }
}
```

#### Public API
```java
// Automatic allocation - returns current region if not full
public JournalRegion allocateNewRegion() throws IOException {
    return allocateNewRegion(false);
}

// Manual rotation - forces new region creation
public void rotateRegion() throws IOException {
    log.info("Manually rotating journal region");
    long startTime = System.nanoTime();
    try {
        allocateNewRegion(true);
    } finally {
        long latencyMicros = (System.nanoTime() - startTime) / 1000;
        rotateChunkCounter.recordSuccess(latencyMicros);
    }
}
```

### Benefits
1. **Single source of truth** - All region allocation logic in one place
2. **Consistent behavior** - Both automatic and manual rotation seal chunks properly
3. **Clear API** - `rotateRegion()` makes intent obvious for manual operations
4. **Performance tracking** - Manual rotation is tracked with performance counters
5. **No code duplication** - Eliminates redundant chunk sealing logic

### Testing
- All journal tests pass successfully
- `testRotateChunk()` verifies manual rotation creates new regions
- Tests validate both automatic and forced allocation paths

### Files Modified
- [JournalRegionManager.java](../lsmplus-kvstore/src/main/java/org/hyperkv/lsmplus/journal/JournalRegionManager.java): Consolidated allocation logic

### Related Changes
- Fixed missing chunk sealing in `allocateNewRegion()` (see 2026-04-25-journal-region-manager-fix.md)
- Integrated Journal class into JournalRegionManager (see earlier changes)
