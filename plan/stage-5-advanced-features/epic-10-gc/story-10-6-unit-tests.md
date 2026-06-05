# Story 10-6: Unit Tests for GC

## Story

As a developer, I want comprehensive unit tests for GC so that all GC mechanisms are verified.

## Acceptance Criteria

- [ ] MNSTrackerTest covers all tracker methods
- [ ] OccupancyTrackerTest covers all tracker methods
- [ ] FullGCTest covers all full GC methods
- [ ] PartialGCTest covers all partial GC methods
- [ ] HolePunchingTest covers all hole punching methods
- [ ] Integration test for complete GC operations
- [ ] Test coverage > 90%

## Technical Details

### Test Structure

```
src/test/java/org/hyperkv/lsmplus/storage/gc/
├── MNSTrackerTest.java
├── OccupancyTrackerTest.java
├── FullGCTest.java
├── PartialGCTest.java
├── HolePunchingTest.java
└── GCIntegrationTest.java
```

### Test Cases

1. **MNSTrackerTest**
   - testTrackVersion()
   - testGetMNS()
   - testReleaseVersion()
   - testMultipleVersions()
   - testMNSChanges()

2. **OccupancyTrackerTest**
   - testTrackDelta()
   - testGetOccupancy()
   - testPersistAndLoad()
   - testMultipleChunks()
   - testOccupancyChanges()

3. **FullGCTest**
   - testPerformFullGC()
   - testMNSFiltering()
   - testChunkDeletion()
   - testSpaceReclamation()
   - testFullGCMultipleRuns()

4. **PartialGCTest**
   - testPerformPartialGC()
   - testOccupancyThreshold()
   - testMNSFiltering()
   - testChunkDeletion()
   - testPartialGCMultipleRuns()

5. **HolePunchingTest**
   - testPerformHolePunching()
   - testDecommissionedPages()
   - testSpaceReclamation()
   - testOccupancyUpdate()
   - testHolePunchingMultipleRuns()

6. **GCIntegrationTest**
   - testCompleteGCOperations()
   - testMixedGCStrategies()
   - testSpaceReclamationUnderLoad()

## Effort Estimate

2 days
