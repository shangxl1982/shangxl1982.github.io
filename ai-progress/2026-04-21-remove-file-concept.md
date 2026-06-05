# 2026-04-21 Remove File Concept from ChunkManager and Chunk

## Summary
Removed the `File` concept from `ChunkManager.java` and `Chunk.java`, making them only aware of `AbstractIO` and using `FileIO` as the default implementation. This completes the abstraction of I/O operations from the core storage classes.

## Changes

### ChunkManager.java
- Changed constructor parameter from `File dataDir` to `String basePath`
- Added `IOFactory` parameter for creating I/O instances
- Updated `getBasePath()` to return `String` instead of `File`
- Removed `getDataDir()` method (replaced with `getBasePath()`)
- All file operations now use `IOFactory` methods

### Chunk.java
- Added `IOFactory` field for file operations
- Added new constructor with `IOFactory` parameter
- Updated static factory methods (`openForWrite`, `openForRead`, `openExisting`) to accept `IOFactory`
- Updated `cleanup()` method to use `ioFactory.delete()` for file deletion
- Added `getFile()` convenience method for backward compatibility with tests

### ChunkMetadataFile.java
- Updated to use `VirtualDataPath` and `AbstractIO` for all file operations
- Added `IOFactory` parameter for creating I/O instances
- `persist()` method now uses `IOFactory` for file operations

### OccupancyFile.java
- Updated to use `VirtualDataPath` and `AbstractIO` for all file operations
- Added `IOFactory` parameter for creating I/O instances
- `persist()` and `load()` methods now use `IOFactory` for file operations

### KVStore.java
- Updated to pass `dataDir.getAbsolutePath()` to `ChunkManager` constructor

### BackupManager.java
- Updated to use `chunkManager.getBasePath()` instead of `getDataDir()`

### Test Files Updated
- `ChunkManagerTest.java` - Updated to use `getAbsolutePath()` and `VirtualDataPath`
- `ChunkTest.java` - Updated to use `VirtualDataPath` instead of `File`
- `ChunkLifecycleTest.java` - Updated to use `VirtualDataPath`
- `StorageLayerIntegrationTest.java` - Updated to use `VirtualDataPath`
- `JournalTest.java` - Updated to use `getAbsolutePath()`
- `JournalReplayTest.java` - Updated to use `getAbsolutePath()`
- `JournalWriterTest.java` - Updated to use `getAbsolutePath()`
- `JournalBatchWriteTest.java` - Updated to use `getAbsolutePath()`
- `JournalIntegrationTest.java` - Updated to use `getAbsolutePath()`
- `PageManagerTest.java` - Updated to use `getAbsolutePath()`
- `BPlusTreeTest.java` - Updated to use `getAbsolutePath()`
- `BPlusTreeFullIntegrationTest.java` - Updated to use `getAbsolutePath()`
- `EntryCountBasedPageIntegrationTest.java` - Updated to use `getAbsolutePath()`
- `TreeDumperTest.java` - Updated to use `getAbsolutePath()`
- `BackupManagerTest.java` - Updated to use `getAbsolutePath()`

## Benefits
1. **Storage Backend Abstraction**: Core storage classes are now independent of file system operations
2. **Testability**: Easier to mock I/O operations for unit testing
3. **Flexibility**: Can implement different storage backends (e.g., memory, cloud storage) by implementing `AbstractIO` and `IOFactory`
4. **Cleaner Architecture**: Separation of concerns between business logic and I/O operations

## All Tests Pass
All 104 tests pass after the refactoring.
