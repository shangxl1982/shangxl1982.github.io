# Story 7-4: Implement WriteBuffer

## Story

As a developer, I want to implement the WriteBuffer class so that writes can be batched efficiently.

## Acceptance Criteria

- [ ] WriteBuffer class created
- [ ] add() method adds entry to buffer
- [ ] flush() method writes buffer to storage
- [ ] Batch size threshold implemented
- [ ] Batch write to Chunk
- [ ] Unit tests verify all methods

## Technical Details

### Class: WriteBuffer

```java
package org.hyperkv.lsmplus.core.bplustree;

public class WriteBuffer {
    private final List<Page> pages;
    private final int batchSize;
    private int currentSize;
    
    public WriteBuffer(int batchSize);
    public void add(Page page);
    public void flush(ChunkManager chunkManager);
    public int getCurrentSize();
}
```

### Batch Flow

```
1. Add page to buffer
2. Check if batch full
3. If full, flush to storage
4. Clear buffer
5. Repeat
```

## Testing

- testAddPage()
- testFlush()
- testBatchSizeThreshold()
- testMultipleBatches()
- testBatchWrite()

## Effort Estimate

1 day
