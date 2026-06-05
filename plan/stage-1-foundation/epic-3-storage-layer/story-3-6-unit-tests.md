# Story 3-6: Unit Tests for Storage Layer

## Story

As a developer, I want comprehensive unit tests for the storage layer so that all components are verified.

## Acceptance Criteria

- [ ] ChunkHeaderTest covers all header methods
- [ ] ChunkTest covers all chunk methods
- [ ] ChunkManagerTest covers all manager methods
- [ ] DirectoryStructureTest verifies directory creation
- [ ] ChunkLifecycleTest verifies all state transitions
- [ ] Integration test for complete write/read cycle
- [ ] Test coverage > 90%

## Technical Details

### Test Structure

```
src/test/java/org/hyperkv/lsmplus/storage/
├── ChunkHeaderTest.java
├── ChunkTest.java
├── ChunkManagerTest.java
├── DirectoryStructureTest.java
├── ChunkLifecycleTest.java
└── StorageLayerIntegrationTest.java
```

### Test Cases

1. **ChunkHeaderTest**
   - testCreateHeader()
   - testToByteArray()
   - testFromByteArray()
   - testFixedSize4096Bytes()

2. **ChunkTest**
   - testCreateChunk()
   - testWriteAndRead()
   - testSeal()
   - testWriteAfterSealThrowsException()
   - testMultipleWrites()
   - testReadAfterSeal()

3. **ChunkManagerTest**
   - testAllocateLeafChunk()
   - testAllocateIndexChunk()
   - testAllocateJournalChunk()
   - testWriteAndRead()
   - testListChunks()
   - testDeleteChunk()

4. **DirectoryStructureTest**
   - testCreateDirectoryStructure()
   - testAllDirectoriesCreated()
   - testDirectoryPermissions()

5. **ChunkLifecycleTest**
   - testOpenToSealed()
   - testSealedToDeleting()
   - testDeletingToDeleted()
   - testInvalidTransitions()

6. **StorageLayerIntegrationTest**
   - testCompleteWriteReadCycle()
   - testMultipleChunks()
   - testChunkRecovery()

## Effort Estimate

2 days
