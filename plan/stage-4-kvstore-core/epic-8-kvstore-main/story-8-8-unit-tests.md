# Story 8-8: Unit Tests for KVStore

## Story

As a developer, I want comprehensive unit tests for KVStore so that all components are verified.

## Acceptance Criteria

- [ ] KVStoreTest covers all KVStore methods
- [ ] PutOperationTest covers all put methods
- [ ] GetOperationTest covers all get methods
- [ ] DeleteOperationTest covers all delete methods
- [ ] BatchOperationTest covers all batch methods
- [ ] DumpMechanismTest covers all dump methods
- [ ] CrashRecoveryTest covers all recovery methods
- [ ] Integration test for complete KVStore operations
- [ ] Test coverage > 90%

## Technical Details

### Test Structure

```
src/test/java/org/hyperkv/lsmplus/core/
├── KVStoreTest.java
├── PutOperationTest.java
├── GetOperationTest.java
├── DeleteOperationTest.java
├── BatchOperationTest.java
├── DumpMechanismTest.java
├── CrashRecoveryTest.java
└── KVStoreIntegrationTest.java
```

### Test Cases

1. **KVStoreTest**
   - testCreateKVStore()
   - testStart()
   - testShutdown()
   - testPutGetDelete()
   - testBatch()
   - testDump()

2. **PutOperationTest**
   - testPutSingleEntry()
   - testPutMultipleEntries()
   - testPutWithBatch()
   - testPutAfterShutdown()
   - testPutWithTombstone()

3. **GetOperationTest**
   - testGetExistingEntry()
   - testGetNonExistingEntry()
   - testGetTombstoneEntry()
   - testGetWithMultipleTables()
   - testGetAfterDump()

4. **DeleteOperationTest**
   - testDeleteExistingEntry()
   - testDeleteNonExistingEntry()
   - testDeleteWithTombstone()
   - testDeleteAfterGet()
   - testDeleteMultipleTimes()

5. **BatchOperationTest**
   - testBatchPutOperations()
   - testBatchDeleteOperations()
   - testBatchMixedOperations()
   - testBatchAtomicity()
   - testBatchWithTombstones()

6. **DumpMechanismTest**
   - testDumpEmptyMemoryTables()
   - testDumpSingleTable()
   - testDumpMultipleTables()
   - testDumpAfterMultiplePuts()
   - testDumpAndRecovery()

7. **CrashRecoveryTest**
   - testRecoveryFromEmptyState()
   - testRecoveryWithJournal()
   - testRecoveryWithBPlusTree()
   - testRecoveryWithBoth()
   - testRecoveryAfterCrash()

8. **KVStoreIntegrationTest**
   - testCompleteKVStoreOperations()
   - testMultipleDumps()
   - testConcurrentOperations()
   - testCrashRecovery()

## Effort Estimate

3 days
