# Story 10-3: Implement Full GC

## Story

As a developer, I want to implement Full GC so that completely empty Chunks (occupancyRatio == 0) can be reclaimed.

## Acceptance Criteria

- [ ] performFullGC() method reclaims Chunks with occupancyRatio == 0
- [ ] MNS is used to identify SEALED Chunks with number < MNS
- [ ] Chunks are transitioned to DELETING then DELETED
- [ ] Chunk files are deleted from disk
- [ ] Occupancy validation ensures Chunk is truly empty
- [ ] Unit tests verify all methods

## Technical Details

### Full GC Algorithm

```java
public void performFullGC(List<Chunk> candidates) {
    for (Chunk chunk : candidates) {
        if (chunk.getOccupancyRatio() == 0.0) {
            // Validate Chunk is truly empty
            if (validateChunkEmpty(chunk)) {
                chunk.transitionToDeleting();
                chunk.deleteFile();
                chunk.transitionToDeleted();
                removeFromMetadata(chunk);
            }
        }
    }
}
```

### Full GC Conditions

```
Full GC applies to Chunks that meet:
1. Chunk number < MNS (won't be used by newer versions)
2. Chunk status == SEALED
3. Occupancy ratio == 0% (completely empty)
```

## Testing

- testPerformFullGC()
- testOccupancyRatioZeroValidation()
- testChunkDeletion()
- testSpaceReclamation()
- testFullGCMultipleRuns()
- testNonEmptyChunkSkipped()

## Effort Estimate

1.5 days
