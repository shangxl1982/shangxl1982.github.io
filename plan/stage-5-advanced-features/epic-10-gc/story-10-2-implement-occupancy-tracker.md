# Story 10-2: Implement Occupancy Tracking

## Story

As a developer, I want to implement Occupancy tracking so that Chunk utilization can be accurately tracked for GC decisions.

## Acceptance Criteria

- [ ] OccupancyDelta records are created during Tree Dump
- [ ] Occupancy is calculated using alignedSize (including padding)
- [ ] DumpOccupancyRecord stores MNS and occupancy deltas per version
- [ ] Occupancy ratio is calculated as occupancySize / totalSize
- [ ] GC decisions use occupancy ratio thresholds (0%, <5%, >=5%)
- [ ] Unit tests verify all methods

## Technical Details

### Occupancy Definition

```
OccupancySize = total valid data size in Chunk (using alignedSize including padding)
OccupancyRatio = occupancySize / totalSize

- New Page write: increase occupancy by alignedSize
- Decommission Page: decrease occupancy by alignedSize
- Same calculation method ensures occupancy can reach exactly 0
```

### OccupancyDelta Structure

```java
public class OccupancyDelta {
    private UUID chunkId;
    private long deltaSize;  // Positive for new pages, negative for decommissioned pages
}

public class DumpOccupancyRecord {
    private long version;
    private long mns;
    private List<OccupancyDelta> deltas;
    private List<DecommissionPage> decommissionPages;  // For hole punching
}
```

## Testing

- testOccupancyDeltaCalculation()
- testDumpOccupancyRecordCreation()
- testOccupancyRatioCalculation()
- testGCThresholdDecisions()
- testMultipleDumpsOccupancy()

## Effort Estimate

1 day
