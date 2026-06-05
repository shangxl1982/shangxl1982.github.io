# Story 11-1: Implement Full Backup

## Story

As a developer, I want to implement Full Backup so that a complete snapshot of Tree data and Journal data can be created.

## Acceptance Criteria

- [ ] fullBackup() method creates complete backup with Tree and Journal data
- [ ] Tree leaf KV data is written in sorted order to tree/data.bin
- [ ] Journal data from replay point to cutoff point is copied
- [ ] Backup metadata with Tree version, MNS, and Journal points is created
- [ ] Tree version is locked during backup to prevent GC
- [ ] Unit tests verify all methods

## Technical Details

### Full Backup Structure

```
backup_20240101_120000/
├── backup.metadata.pb          # Backup metadata
├── tree/
│   └── data.bin            # Tree Leaf KV data (sorted)
└── journal/
    ├── region_1.bin        # Journal Region 1 data
    ├── region_2.bin        # Journal Region 2 data
    └── region_3.bin        # Journal Region 3 data
```

### Backup Algorithm

```java
public BackupResult fullBackup(String targetPath) {
    // 1. Lock latest Tree version to prevent GC
    long targetVersion = lockLatestVersion();
    
    // 2. Create backup directory
    File backupDir = createBackupDir(targetPath);
    
    // 3. Write Tree leaf KV data in sorted order
    writeTreeData(backupDir, targetVersion);
    
    // 4. Copy Journal data from replay to cutoff point
    copyJournalData(backupDir);
    
    // 5. Create backup metadata
    BackupMetadata metadata = createBackupMetadata(targetVersion);
    writeMetadata(backupDir, metadata);
    
    // 6. Unlock version
    unlockVersion(targetVersion);
    
    return new BackupResult(metadata);
}
```

## Testing

- testFullBackup()
- testTreeDataWrittenSorted()
- testJournalDataCopied()
- testBackupMetadataCreation()
- testVersionLocking()
- testMultipleFullBackups()

## Effort Estimate

1.5 days
