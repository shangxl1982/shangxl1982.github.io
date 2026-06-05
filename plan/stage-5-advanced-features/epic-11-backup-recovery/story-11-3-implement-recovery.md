# Story 11-3: Implement Recovery

## Story

As a developer, I want to implement Recovery so that the system can be restored from backup using two strategies: Tree rollback and full recovery.

## Acceptance Criteria

- [ ] recoverByTreeRollback() method performs fast recovery using Tree rollback
- [ ] recoverFull() method performs complete recovery from backup chain
- [ ] Tree rollback restores to a specific version and replays Journal
- [ ] Full recovery uses batch insert for Tree data and replays Journal
- [ ] Backup chain validation ensures integrity
- [ ] Unit tests verify all methods

## Technical Details

### Recovery Strategy 1: Tree Rollback

```java
public void recoverByTreeRollback(long targetVersion) {
    // 1. Rollback Tree to target version
    treeManager.rollbackToVersion(targetVersion);
    
    // 2. Replay Journal from target version to current
    journalManager.replayFromVersion(targetVersion);
    
    // 3. Verify recovery success
    verifyRecovery();
}
```

### Recovery Strategy 2: Full Recovery

```java
public void recoverFull(String backupChainPath) {
    // 1. Clear KVStore
    kvStore.clear();
    
    // 2. Load backup chain
    List<BackupMetadata> backupChain = loadBackupChain(backupChainPath);
    
    // 3. Restore Tree data from full backup using batch insert
    restoreTreeData(backupChain.get(0));
    
    // 4. Replay Journal data from all backups in chain
    replayJournalChain(backupChain);
    
    // 5. Verify recovery success
    verifyRecovery();
}
```

## Testing

- testRecoverByTreeRollback()
- testRecoverFull()
- testTreeRollbackWithJournalReplay()
- testFullRecoveryWithBackupChain()
- testBackupChainValidation()
- testPointInTimeRecovery()

## Effort Estimate

2 days
