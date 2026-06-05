# Story 10-4: Implement Partial GC

## Story

As a developer, I want to implement Partial GC so that Chunks with very low occupancy (occupancyRatio < 5%) can be reclaimed by migrating valid data.

## Acceptance Criteria

- [ ] performPartialGC() method reclaims Chunks with 0% < occupancyRatio < 5%
- [ ] Valid data is migrated to new Chunks before deletion
- [ ] MNS is used to identify SEALED Chunks with number < MNS
- [ ] Chunks are deleted after successful data migration
- [ ] Batch processing of multiple Chunks for efficiency
- [ ] Unit tests verify all methods

## Technical Details

### Partial GC Algorithm

```java
public void performPartialGC(List<Chunk> candidates) {
    List<Chunk> partialGCCandidates = candidates.stream()
        .filter(chunk -> chunk.getOccupancyRatio() > 0 && chunk.getOccupancyRatio() < 0.05)
        .collect(Collectors.toList());
    
    if (!partialGCCandidates.isEmpty()) {
        migrateValidData(partialGCCandidates);
        deleteChunks(partialGCCandidates);
    }
}
```

### Partial GC Conditions

```
Partial GC applies to Chunks that meet:
1. Chunk number < MNS (won't be used by newer versions)
2. Chunk status == SEALED
3. 0% < occupancy ratio < 5% (very low occupancy)
```

## Testing

- testPerformPartialGC()
- testOccupancyThresholdValidation()
- testDataMigration()
- testBatchProcessing()
- testPartialGCMultipleRuns()
- testHighOccupancyChunkSkipped()

## Effort Estimate

1.5 days
