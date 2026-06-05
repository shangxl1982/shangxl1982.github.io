# Implementation Plan: LSM Plus Backup Management

**Branch**: `011-lsmplus-backup` | **Date**: 2026-04-17 | **Spec**: [spec.md](file:///home/wisefox/git/hyperkvstore/specs/011-lsmplus-backup/spec.md)
**Input**: Feature specification from `/specs/011-lsmplus-backup/spec.md`

## Summary

Implement backup and restore system for LSM tree data with full and incremental backup support. Creates full backups of all data (chunks, journal, metadata), supports incremental backups capturing only changes, provides restoration from backup chains, manages automated backup scheduling, and enforces retention policies for cleanup.

## Technical Context

**Language/Version**: Java 25  
**Primary Dependencies**: lsmplus-api, lsmplus-storage, lsmplus-journal, JUnit 6.0.0  
**Storage**: File-based backup storage separate from primary data  
**Testing**: JUnit 5 with Mockito  
**Target Platform**: Linux server (JVM)  
**Project Type**: Library  
**Performance Goals**: <1hr full backup for 100GB, <10min incremental for 1GB, <2hr restore for 100GB  
**Constraints**: Point-in-time consistency, integrity validation, retention policies  
**Scale/Scope**: 5 core classes (BackupManager, BackupMetadata, BackupType, BackupSchedule, BackupRetention)  

## Constitution Check

✅ **Library-First**: Standalone backup library
✅ **Test-First**: TDD with unit and integration tests
✅ **Simplicity**: Focused on backup and restore
✅ **Observability**: Backup progress, integrity checks, retention tracking
✅ **Versioning**: Backup format versioned

## Project Structure

```text
lsmplus-backup/
├── src/
│   ├── main/java/org/hyperkv/lsmplus/backup/
│   │   ├── BackupManager.java         # Main backup coordinator
│   │   ├── BackupMetadata.java        # Backup information
│   │   ├── BackupType.java            # FULL/INCREMENTAL enum
│   │   ├── BackupSchedule.java        # Automated scheduling
│   │   └── BackupRetention.java       # Retention policy
│   └── test/java/org/hyperkv/lsmplus/backup/
│       └── BackupManagerTest.java
└── build.gradle.kts
```

## Phase 0: Research & Design Decisions

### Research Tasks

1. **Backup Consistency**: Snapshot or quiesce writes during backup
2. **Incremental Strategy**: Track changes via timestamps or sequence numbers
3. **Backup Storage**: Separate directory or remote storage
4. **Restoration Process**: Full restore then apply incrementals
5. **Retention Policy**: Time-based or count-based retention

### Design Decisions

1. **Snapshot-Based**: Create consistent snapshot during backup
2. **Timestamp-Based Incremental**: Track changes since last backup
3. **Local Directory**: Backup to configurable directory
4. **Chain Restoration**: Apply full backup then incrementals in order
5. **Time-Based Retention**: Keep backups for N days

## Phase 1: Design & Contracts

**Public API**:
- `BackupManager.createFullBackup()` - Create full backup
- `BackupManager.createIncrementalBackup()` - Create incremental backup
- `BackupManager.restore(BackupMetadata)` - Restore from backup
- `BackupManager.verify(BackupMetadata)` - Verify backup integrity
- `BackupManager.cleanup()` - Apply retention policy

## Dependencies

**Internal**: lsmplus-api, lsmplus-storage, lsmplus-journal  
**External**: JUnit 6.0.0, Mockito 5.11.0

## Success Metrics

- ✅ Full backup <1hr for 100GB
- ✅ Incremental backup <10min for 1GB
- ✅ Restoration <2hr for 100GB
- ✅ 100% integrity validation accuracy
- ✅ Zero data loss during backup/restore
