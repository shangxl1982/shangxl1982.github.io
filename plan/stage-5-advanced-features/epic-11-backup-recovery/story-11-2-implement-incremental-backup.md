# Story 11-2: Implement Incremental Backup

## Story

As a developer, I want to implement Incremental Backup so that only Journal data changes since the last backup are backed up.

## Acceptance Criteria

- [ ] incrementalBackup() method creates incremental backup with only Journal data
- [ ] Journal data from last backup's cutoff point to current cutoff point is copied
- [ ] Incremental metadata with parent backup ID is created
- [ ] Backup chain integrity is maintained
- [ ] No Tree data is included in incremental backups
- [ ] Unit tests verify all methods

## Technical Details

### Incremental Backup Structure

```
backup_20240102_120000_inc/
├── backup.metadata.pb          # Incremental backup metadata
└── journal/
    ├── region_4.bin        # Journal Region 4 data
    └── region_5.bin        # Journal Region 5 data
```

### Incremental Backup Algorithm

```java
public BackupResult incrementalBackup(String targetPath, String parentBackupId) {
    // 1. Load parent backup metadata
    BackupMetadata parentMetadata = loadParentMetadata(parentBackupId);
    
    // 2. Create backup directory
    File backupDir = createBackupDir(targetPath);
    
    // 3. Copy Journal data from parent's cutoff to current cutoff
    copyIncrementalJournalData(backupDir, parentMetadata);
    
    // 4. Create incremental backup metadata
    BackupMetadata metadata = createIncrementalMetadata(parentBackupId);
    writeMetadata(backupDir, metadata);
    
    return new BackupResult(metadata);
}
```

### Backup Chain

```
Full Backup 1          Incremental 1        Incremental 2
┌──────────────┐      ┌──────────────┐     ┌──────────────┐
│ Tree Data    │      │ Journal      │     │ Journal      │
│ Journal R1-5 │ ───► │ Journal R6-8 │ ──► │ Journal R9-10│
│ Cutoff: R5   │      │ Cutoff: R8   │     │ Cutoff: R10  │
└──────────────┘      └──────────────┘     └──────────────┘
```

## Testing

- testIncrementalBackup()
- testJournalDataFromCutoff()
- testBackupChainIntegrity()
- testIncrementalMetadataCreation()
- testMultipleIncrementalBackups()
- testBackupChainValidation()

## Effort Estimate

1.5 days
