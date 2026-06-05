# Story 3-3: Implement ChunkManager

## Story

As a developer, I want to implement the ChunkManager class so that chunks can be allocated and managed.

## Acceptance Criteria

- [ ] ChunkManager class created
- [ ] allocateChunk(ChunkType) method creates new chunk
- [ ] getChunk(UUID) method retrieves chunk by ID
- [ ] listChunks(ChunkType) method lists all chunks of type
- [ ] deleteChunk(UUID) method deletes chunk
- [ ] Directory structure created on initialization
- [ ] Chunk metadata cached in memory
- [ ] Unit tests verify all methods

## Technical Details

### Class: ChunkManager

```java
package org.hyperkv.lsmplus.storage;

public class ChunkManager {
    private final File baseDir;
    private final Map<UUID, Chunk> chunkCache;
    private final Map<UUID, ChunkType> chunkTypeMap;
    private final Object lock = new Object();
    
    public ChunkManager(File baseDir);
    public SegmentLocation writeLeafPage(byte[] data);
    public SegmentLocation writeIndexPage(byte[] data);
    public SegmentLocation writeJournal(byte[] data);
    public Chunk getChunk(UUID chunkId);
    public List<Chunk> listChunks(ChunkType type);
    public void deleteChunk(UUID chunkId);
    public void close();
}
```

### Write Methods

```java
public SegmentLocation writeLeafPage(byte[] data) {
    Chunk chunk = getOrCreateChunk(ChunkType.LEAF);
    return chunk.write(data);
}

public SegmentLocation writeIndexPage(byte[] data) {
    Chunk chunk = getOrCreateChunk(ChunkType.INDEX);
    return chunk.write(data);
}

public SegmentLocation writeJournal(byte[] data) {
    Chunk chunk = getOrCreateChunk(ChunkType.JOURNAL);
    return chunk.write(data);
}
```

## Testing

- testAllocateLeafChunk()
- testAllocateIndexChunk()
- testAllocateJournalChunk()
- testWriteAndRead()
- testListChunks()
- testDeleteChunk()
- testDirectoryStructure()

## Effort Estimate

1.5 days
