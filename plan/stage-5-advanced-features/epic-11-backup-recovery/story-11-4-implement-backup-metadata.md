# Story 11-4: Implement Backup Metadata

## Story

As a developer, I want to implement Backup Metadata so that backups can be managed and recovered from.

## Acceptance Criteria

- [ ] BackupMetadata class created
- [ ] Backup type tracked (FULL/INCREMENTAL)
- [ ] Timestamp recorded
- [ ] Chunk list stored
- [ ] persist() method persists metadata
- [ ] load() method loads metadata
- [ ] Unit tests verify all methods

## Technical Details

### Class: BackupMetadata

```java
public class BackupMetadata {
    private final BackupType backupType;
    private final long timestamp;
    private final UUID backupId;
    private final List<UUID> chunkIds;
    private final String baseBackupId;
    
    public void persist(File metadataFile);
    public static BackupMetadata load(File metadataFile);
}
```

## Testing

- testCreateBackupMetadata()
- testPersistAndLoad()
- testFullBackupMetadata()
- testIncrementalBackupMetadata()
- testMultipleBackups()

## Effort Estimate

1 day
