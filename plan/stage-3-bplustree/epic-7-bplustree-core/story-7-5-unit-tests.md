# Story 7-5: Unit Tests for B+Tree

## Story

As a developer, I want comprehensive unit tests for B+Tree so that all components are verified.

## Acceptance Criteria

- [ ] BPlusTreeTest covers all tree methods
- [ ] PageManagerTest covers all manager methods
- [ ] TreeDumpTest covers all dump methods
- [ ] WriteBufferTest covers all buffer methods
- [ ] Integration test for complete B+Tree operations
- [ ] Test coverage > 90%

## Technical Details

### Test Structure

```
src/test/java/org/hyperkv/lsmplus/core/bplustree/
├── BPlusTreeTest.java
├── PageManagerTest.java
├── TreeDumpTest.java
├── WriteBufferTest.java
└── BPlusTreeIntegrationTest.java
```

### Test Cases

1. **BPlusTreeTest**
   - testInsertAndSearch()
   - testRangeQuery()
   - testMultipleInserts()
   - testDump()
   - testVersionManagement()

2. **PageManagerTest**
   - testLoadAndSavePage()
   - testAllocatePage()
   - testDeletePage()
   - testMultiplePages()
   - testPageRecovery()

3. **TreeDumpTest**
   - testDumpEmptyMemoryTables()
   - testDumpSingleTable()
   - testDumpMultipleTables()
   - testMergeEntries()
   - testTreeBuild()

4. **WriteBufferTest**
   - testAddPage()
   - testFlush()
   - testBatchSizeThreshold()
   - testMultipleBatches()
   - testBatchWrite()

5. **BPlusTreeIntegrationTest**
   - testCompleteBPlusTreeOperations()
   - testMultipleDumps()
   - testTreeRecovery()

## Effort Estimate

2 days
