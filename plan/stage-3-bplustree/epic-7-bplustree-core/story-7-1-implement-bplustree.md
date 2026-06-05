# Story 7-1: Implement B+Tree Class

## Story

As a developer, I want to implement the B+Tree class so that data can be stored in a B+Tree structure.

## Acceptance Criteria

- [ ] B+Tree class created
- [ ] insert(Key, Value) method inserts data
- [ ] search(Key) method retrieves data
- [ ] rangeQuery(Key, Key) method returns range
- [ ] dump() method creates new version
- [ ] getVersion() method returns current version
- [ ] Unit tests verify all methods

## Technical Details

### Class: BPlusTree

```java
package org.hyperkv.lsmplus.core.bplustree;

public class BPlusTree {
    private final int maxLeafPageSize;
    private final int maxIndexPageSize;
    private Page root;
    private int height;
    private final List<TreeVersion> versions;
    private int currentVersion;
    
    public BPlusTree(int maxLeafPageSize, int maxIndexPageSize);
    public void insert(IndexKey key, IndexValue value);
    public IndexValue search(IndexKey key);
    public List<Map.Entry<IndexKey, IndexValue>> rangeQuery(IndexKey start, IndexKey end);
    public void dump(List<Map.Entry<IndexKey, IndexValue>> entries);
    public int getVersion();
    public Page getRoot();
}
```

### Implementation Notes

- Use PageManager for page operations
- Track tree height
- Support multiple versions

## Testing

- testInsertAndSearch()
- testRangeQuery()
- testMultipleInserts()
- testDump()
- testVersionManagement()

## Effort Estimate

2 days
