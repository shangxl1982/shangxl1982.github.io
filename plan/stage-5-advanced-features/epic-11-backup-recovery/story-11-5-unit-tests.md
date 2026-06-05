# Story 11-5: Unit Tests for Backup

## Story

As a developer, I want comprehensive unit tests for Backup & Recovery so that all backup mechanisms are verified.

## Acceptance Criteria

- [ ] FullBackupTest covers all full backup methods
- [ ] IncrementalBackupTest covers all incremental methods
- [ ] RecoveryTest covers all recovery methods
- [ ] BackupMetadataTest covers all metadata methods
- [ ] Integration test for complete backup/recovery cycle
- [ ] Test coverage > 90%

## Technical Details

### Test Structure

```
src/test/java/org/hyperkv/lsmplus/backup/
├── FullBackupTest.java
├── IncrementalBackupTest.java
├── RecoveryTest.java
├── BackupMetadataTest.java
└── BackupIntegrationTest.java
```

### Test Cases

1. **FullBackupTest**
   - testFullBackup()
   - testBackupAllChunks()
   - testBackupMetadata()
   - testBackupMetadataCreation()
   - testMultipleFullBackups()

2. **IncrementalBackupTest**
   - testIncrementalBackup()
   - testChangedChunksIdentification()
   - testCopyOnlyChangedChunks()
   - testIncrementalMetadataCreation()
   - testMultipleIncrementalBackups()

3. **RecoveryTest**
   - testRecoverFromFullBackup()
   - testRecoverFromIncrementalBackup()
   - testPointInTimeRecovery()
   - testDataRestoration()
   - testMultipleRecoveries()

4. **BackupMetadataTest**
   - testCreateBackupMetadata()
   - testPersistAndLoad()
   - testFullBackupMetadata()
   - testIncrementalBackupMetadata()
   - testMultipleBackups()

5. **BackupIntegrationTest**
   - testCompleteBackupRecoveryCycle()
   - testFullThenIncrementalThenRecovery()
   - testBackupRecoveryUnderLoad()

## Effort Estimate

2 days
