# Story 3-2: Implement Chunk Class

## Story

As a developer, I want to implement the Chunk class so that data can be written to and read from chunks.

## Acceptance Criteria

- [ ] Chunk class created with status (OPEN, SEALED, DELETING, DELETED)
- [ ] write(byte[]) method appends data and returns SegmentLocation
- [ ] read(SegmentLocation) method reads data at location
- [ ] seal() method transitions to SEALED status
- [ ] getStatus() returns current status
- [ ] getValidDataSize() returns total valid data size
- [ ] File I/O uses RandomAccessFile
- [ ] Unit tests verify all methods

## Technical Details

### Class: Chunk

```java
package org.hyperkv.lsmplus.storage;

public class Chunk {
    private final File file;
    private final ChunkHeader header;
    private ChunkStatus status;
    private RandomAccessFile raf;
    
    public Chunk(File file, ChunkType type, UUID ownerId, UUID namespaceId);
    public SegmentLocation write(byte[] data);
    public byte[] read(SegmentLocation location);
    public void seal();
    public ChunkStatus getStatus();
    public int getValidDataSize();
    public void close();
}
```

### Write Flow

```
1. Check status is OPEN
2. Get current file length (data offset)
3. Create WriteItem from data
4. Write WriteItem to file at offset
5. Update header.validDataSize
6. Return SegmentLocation (chunkId, offset, length)
```

### Read Flow

```
1. Validate location.chunkId matches
2. Read data from file at location.offset
3. Parse WriteItem
4. Validate CRC32
5. Return body data
```

## Testing

- testCreateChunk()
- testWriteAndRead()
- testSeal()
- testWriteAfterSealThrowsException()
- testMultipleWrites()
- testReadAfterSeal()

## Effort Estimate

1.5 days
