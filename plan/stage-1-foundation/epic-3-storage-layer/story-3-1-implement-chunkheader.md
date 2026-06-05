# Story 3-1: Implement ChunkHeader

## Story

As a developer, I want to implement the ChunkHeader class so that chunk metadata can be stored and retrieved.

## Acceptance Criteria

- [ ] ChunkHeader class created
- [ ] Header size is exactly 4096 bytes
- [ ] Fields: ChunkID, ChunkType, OwnerID, NamespaceID, ValidDataSize, Reserved
- [ ] toByteArray() returns 4096 bytes
- [ ] fromByteArray() parses header correctly
- [ ] All fields are properly aligned
- [ ] Unit tests verify header format

## Technical Details

### Class: ChunkHeader

```java
package org.hyperkv.lsmplus.storage;

public class ChunkHeader {
    public static final int HEADER_SIZE = 4096;
    public static final int CHUNK_ID_SIZE = 16;
    public static final int CHUNK_TYPE_SIZE = 4;
    public static final int OWNER_ID_SIZE = 16;
    public static final int NAMESPACE_ID_SIZE = 16;
    public static final int VALID_DATA_SIZE_SIZE = 4;
    public static final int RESERVED_SIZE = 4040;
    
    private final UUID chunkId;
    private final ChunkType chunkType;
    private final UUID ownerId;
    private final UUID namespaceId;
    private int validDataSize;
    private final byte[] reserved;
    
    public ChunkHeader(UUID chunkId, ChunkType chunkType, UUID ownerId, UUID namespaceId);
    
    public byte[] toByteArray();
    public static ChunkHeader fromByteArray(byte[] data);
    public UUID getChunkId();
    public ChunkType getChunkType();
    public UUID getOwnerId();
    public UUID getNamespaceId();
    public int getValidDataSize();
    public void setValidDataSize(int size);
}
```

### Header Layout

```
Offset 0-15:    chunkId (UUID, 16 bytes)
Offset 16-19:   chunkType (4 bytes)
Offset 20-35:   ownerId (UUID, 16 bytes)
Offset 36-51:   namespaceId (UUID, 16 bytes)
Offset 52-55:   validDataSize (4 bytes)
Offset 56-4095: reserved (4040 bytes, all 0)
```

## Testing

- testCreateHeader()
- testToByteArray()
- testFromByteArray()
- testFixedSize4096Bytes()
- testFieldAlignment()

## Effort Estimate

1 day
