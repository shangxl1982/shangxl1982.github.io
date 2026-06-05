# Story 10-1: Implement MNS Management

## Story

As a developer, I want to implement MNS (Min Not Sealed) management so that GC can identify reclaimable Chunks based on version history.

## Acceptance Criteria

- [ ] MNS is recorded in tree-metadata.pb during each Tree Dump
- [ ] MNS is retrieved from tree-metadata.pb for specific versions
- [ ] MNS represents the minimum Chunk number that is not SEALED during Dump
- [ ] Chunks with number < MNS can be safely GC'd
- [ ] Unit tests verify all methods

## Technical Details

### MNS Definition

```
MNS = Min Not Sealed number
- Recorded in tree-metadata.pb during Tree Dump
- Represents the minimum Chunk number that is not SEALED
- Chunks with number < MNS can be GC'd (they won't be used by newer versions)
```

### MNS Usage in GC

```java
public class GarbageCollector {
    public void performGC(long targetVersion) {
        // Get MNS for the target version
        long mns = treeMetadataManager.getMNS(targetVersion);
        
        // Find Chunks with number < MNS and status == SEALED
        List<Chunk> candidates = findGCCandidates(mns);
        
        // Classify by occupancy ratio and perform appropriate GC
        classifyAndPerformGC(candidates);
    }
}
```

## Testing

- testMNSRecordedDuringDump()
- testMNSRetrieval()
- testGCCandidateSelection()
- testMNSBasedGC()
- testMultipleVersionsMNS()

## Effort Estimate

1 day
