# Story 6-2: Implement LeafPage

## Story

As a developer, I want to implement the LeafPage class so that data can be stored in B+Tree leaf nodes.

## Acceptance Criteria

- [ ] LeafPage class created
- [ ] Stores key-value pairs with values
- [ ] Supports put/get/delete operations
- [ ] Range query support
- [ ] Serialization/deserialization
- [ ] Unit tests verify all methods

## Technical Details

### Class: LeafPage

```java
package org.hyperkv.lsmplus.core.bplustree.page;

public class LeafPage extends Page {
    private final Map<IndexKey, IndexValue> data;
    
    public LeafPage(int pageId, int maxSize);
    public void put(IndexKey key, IndexValue value);
    public IndexValue get(IndexKey key);
    public void delete(IndexKey key);
    public List<Map.Entry<IndexKey, IndexValue>> rangeQuery(IndexKey start, IndexKey end);
    public byte[] toByteArray();
    public static LeafPage fromByteArray(byte[] data);
}
```

### Implementation Notes

- Use TreeMap for ordered storage
- Track used size in bytes
- Support tombstones for deletes

## Testing

- testPutAndGet()
- testDelete()
- testRangeQuery()
- testMultipleEntries()
- testSerialization()

## Effort Estimate

1.5 days
