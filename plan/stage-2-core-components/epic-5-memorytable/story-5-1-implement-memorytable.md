# Story 5-1: Implement MemoryTable Class

## Story

As a developer, I want to implement the MemoryTable class so that data can be stored in memory.

## Acceptance Criteria

- [ ] MemoryTable class created
- [ ] put(Key, Value, ReplayPoint) method inserts data
- [ ] get(Key) method retrieves data
- [ ] delete(Key, ReplayPoint) method marks as deleted
- [ ] rangeQuery(Key, Key) method returns range
- [ ] shouldSeal() method checks size threshold
- [ ] seal() method transitions to sealed state
- [ ] Unit tests verify all methods

## Technical Details

### Class: MemoryTable

```java
package org.hyperkv.lsmplus.memory;

public class MemoryTable {
    private final int maxSize;
    private int currentSize;
    private final ConcurrentSkipListMap<IndexKey, IndexValue> data;
    private volatile boolean sealed;
    
    public MemoryTable(int maxSize);
    public void put(IndexKey key, IndexValue value, JournalReplayPoint replayPoint);
    public IndexValue get(IndexKey key);
    public void delete(IndexKey key, JournalReplayPoint replayPoint);
    public List<Map.Entry<IndexKey, IndexValue>> rangeQuery(IndexKey start, IndexKey end);
    public boolean shouldSeal();
    public void seal();
    public boolean isSealed();
    public int getCurrentSize();
}
```

### Implementation Notes

- Use ConcurrentSkipListMap for thread-safe operations
- Track current size in bytes
- Support tombstones for deletes
- Size threshold default 64MB

## Testing

- testPutAndGet()
- testDelete()
- testRangeQuery()
- testSeal()
- testShouldSeal()
- testTombstone()

## Effort Estimate

2 days
