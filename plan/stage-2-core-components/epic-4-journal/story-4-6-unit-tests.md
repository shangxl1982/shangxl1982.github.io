# Story 4-6: Unit Tests for Journal

## Story

As a developer, I want comprehensive unit tests for the Journal so that all components are verified.

## Acceptance Criteria

- [ ] JournalTest covers all journal methods
- [ ] JournalWriterTest covers all writer methods
- [ ] ReplayMechanismTest covers all replay methods
- [ ] RegionManagementTest covers all region methods
- [ ] BatchWritingTest covers all batch methods
- [ ] Integration test for complete write/replay cycle
- [ ] Test coverage > 90%

## Technical Details

### Test Structure

```
src/test/java/org/hyperkv/lsmplus/journal/
├── JournalTest.java
├── JournalWriterTest.java
├── ReplayMechanismTest.java
├── RegionManagementTest.java
├── BatchWritingTest.java
└── JournalIntegrationTest.java
```

### Test Cases

1. **JournalTest**
   - testWriteSingleEntry()
   - testWriteMultipleEntries()
   - testReplayFromBeginning()
   - testReplayFromPoint()
   - testSequenceNumbers()
   - testClose()

2. **JournalWriterTest**
   - testWriteSingle()
   - testBatchWrite()
   - testFlush()
   - testBatchBoundary()
   - testMultipleBatches()

3. **ReplayMechanismTest**
   - testReplayFromBeginning()
   - testReplayFromPoint()
   - testReplayWithCRC32Validation()
   - testReplayWithCorruptedEntry()
   - testReplayMultipleRegions()

4. **RegionManagementTest**
   - testCreateRegion()
   - testAddEntry()
   - testPersistAndLoad()
   - testMultipleEntries()
   - testRegionRecovery()

5. **BatchWritingTest**
   - testBatchWrite()
   - testBatchReplay()
   - testBatchAtomicity()
   - testMultipleBatches()
   - testBatchWithMixedOperations()

6. **JournalIntegrationTest**
   - testCompleteWriteReplayCycle()
   - testCrashRecovery()
   - testMultipleRegions()
   - testBatchRecovery()

## Effort Estimate

2 days
