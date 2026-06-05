# Story 3-4: Implement Directory Structure

## Story

As a developer, I want to implement directory structure creation so that chunks and metadata are organized properly.

## Acceptance Criteria

- [ ] Base directory created on initialization
- [ ] chunks/ subdirectory created
- [ ] chunks/index/, chunks/leaf/, chunks/journal/ subdirectories created
- [ ] metadata/ subdirectory created
- [ ] backup/ subdirectory created
- [ ] All directories are created with correct permissions
- [ ] Unit tests verify directory structure

## Technical Details

### Directory Structure

```
data/
├── chunks/
│   ├── index/
│   │   └── chunk-{chunk_number}.dat
│   ├── leaf/
│   │   └── chunk-{chunk_number}.dat
│   └── journal/
│       └── chunk-{chunk_number}.dat
├── metadata/
│   ├── tree-metadata.dat
│   ├── journal-region-index.dat
│   └── chunk-metadata.dat
└── backup/
    └── backup-{timestamp}.dat
```

### Implementation

```java
private void createDirectoryStructure() {
    chunksDir = new File(baseDir, "chunks");
    indexDir = new File(chunksDir, "index");
    leafDir = new File(chunksDir, "leaf");
    journalDir = new File(chunksDir, "journal");
    metadataDir = new File(baseDir, "metadata");
    backupDir = new File(baseDir, "backup");
    
    // Create all directories
    baseDir.mkdirs();
    indexDir.mkdirs();
    leafDir.mkdirs();
    journalDir.mkdirs();
    metadataDir.mkdirs();
    backupDir.mkdirs();
}
```

## Testing

- testCreateDirectoryStructure()
- testAllDirectoriesCreated()
- testDirectoryPermissions()
- testDirectoryStructureExists()

## Effort Estimate

0.5 day
