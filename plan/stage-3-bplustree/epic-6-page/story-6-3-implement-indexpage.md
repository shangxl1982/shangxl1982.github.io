# Story 6-3: Implement IndexPage

## Story

As a developer, I want to implement the IndexPage class so that navigation can be stored in B+Tree index nodes.

## Acceptance Criteria

- [ ] IndexPage class created
- [ ] Stores key-location pairs with SegmentLocation
- [ ] Supports get child location by key
- [ ] Supports range query for child locations
- [ ] Serialization/deserialization
- [ ] Unit tests verify all methods

## Technical Details

### Class: IndexPage

```java
package org.hyperkv.lsmplus.core.bplustree.page;

public class IndexPage extends Page {
    private final Map<IndexKey, SegmentLocation> children;
    
    public IndexPage(int pageId, int maxSize);
    public void put(IndexKey key, SegmentLocation location);
    public SegmentLocation getChildLocation(IndexKey key);
    public List<Map.Entry<IndexKey, SegmentLocation>> rangeQuery(IndexKey start, IndexKey end);
    public byte[] toByteArray();
    public static IndexPage fromByteArray(byte[] data);
}
```

### Implementation Notes

- Use TreeMap for ordered storage
- SegmentLocation points to child page
- First key is always null (leftmost child)

## Testing

- testPutAndGetChildLocation()
- testRangeQuery()
- testMultipleEntries()
- testSerialization()
- testLeftmostChild()

## Effort Estimate

1.5 days
