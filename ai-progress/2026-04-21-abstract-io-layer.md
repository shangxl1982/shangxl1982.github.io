# AbstractIO Layer Implementation

**Date**: 2026-04-21

## Problem

The Chunk class was tightly coupled to `RandomAccessFile`, making it difficult to support alternative storage backends (memory, cloud storage, etc.).

## Solution

Created an abstract I/O layer to decouple file operations from Chunk:

1. `VirtualDataPath` - Virtual path abstraction (file://, memory://, s3://)
2. `AbstractIO` - Interface for I/O operations
3. `FileIO` - File system implementation using RandomAccessFile

## New Files

### 1. VirtualDataPath.java

```java
public final class VirtualDataPath {
    private final String scheme;  // file, memory, s3
    private final String path;
    
    public static VirtualDataPath file(String filePath);
    public static VirtualDataPath memory(String path);
    public static VirtualDataPath s3(String bucket, String key);
}
```

### 2. AbstractIO.java

```java
public interface AbstractIO extends AutoCloseable {
    enum OpenMode { READ, WRITE, READ_WRITE }
    
    void open(VirtualDataPath path, OpenMode mode);
    byte[] read(long offset, int length);
    void write(long offset, byte[] data);
    void sync(long offset, long length);
    void sync();
    long length();
    void setLength(long newLength);
    void close();
    boolean isOpen();
    VirtualDataPath getPath();
    OpenMode getMode();
}
```

### 3. FileIO.java

File system implementation using `RandomAccessFile`:
- Supports all AbstractIO operations
- Thread-safe with proper state management
- Auto-creates parent directories for write mode

## Modified Files

### Chunk.java

Changed from `RandomAccessFile raf` to `AbstractIO io`:

```java
// Before
private RandomAccessFile raf;
raf = new RandomAccessFile(file, "rw");
raf.write(data);

// After
private AbstractIO io;
io = new FileIO();
io.open(VirtualDataPath.file(file.getAbsolutePath()), OpenMode.WRITE);
io.write(0, data);
```

All constructors and static factory methods now support dependency injection of `AbstractIO`:

```java
public Chunk(File file, ChunkType type, UUID ownerId, UUID namespaceId, AbstractIO io);
public static Chunk openForWrite(ChunkInfo info, AbstractIO io);
public static Chunk openForRead(ChunkInfo info, AbstractIO io);
public static Chunk openExisting(File file, AbstractIO io);
```

## Architecture

```
┌─────────────────────────────────────────────────────────────┐
│                         Chunk                                │
│  - Uses AbstractIO interface                                 │
│  - No direct file system dependencies                        │
└─────────────────────────────────────────────────────────────┘
                              │
                              ▼
┌─────────────────────────────────────────────────────────────┐
│                       AbstractIO                             │
│  - open(VirtualDataPath, OpenMode)                          │
│  - read(offset, length)                                      │
│  - write(offset, data)                                       │
│  - sync(offset, length)                                      │
│  - close()                                                   │
└─────────────────────────────────────────────────────────────┘
                              │
           ┌──────────────────┼──────────────────┐
           ▼                  ▼                  ▼
    ┌─────────────┐    ┌─────────────┐    ┌─────────────┐
    │   FileIO    │    │  MemoryIO   │    │    S3IO     │
    │ (current)   │    │  (future)   │    │  (future)   │
    └─────────────┘    └─────────────┘    └─────────────┘
```

## Benefits

1. **Extensibility**: Easy to add new storage backends (memory, S3, etc.)
2. **Testability**: Can mock I/O operations for unit testing
3. **Flexibility**: Different chunks can use different storage backends
4. **Clean separation**: Chunk focuses on data structure, AbstractIO handles storage

## Test Results

All tests pass after the refactoring.
