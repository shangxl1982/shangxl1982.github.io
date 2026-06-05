# B+Tree Page Merge Logic Implementation - 2026-05-08

## Summary

Successfully implemented page merge logic in TreeDumper to maintain good space utilization when pages become underfull after deletions. The merge logic consolidates underfull pages with their right siblings and re-splits them if necessary to maintain balanced page sizes.

## Problem Statement

When keys are deleted from a B+Tree, pages can become underfull (less than 1/3 of configured size). This leads to:
- Poor space utilization
- Increased tree height
- Degraded query performance
- Wasted disk space

## Solution

Implemented automatic page merging when pages become underfull after deletions:

1. **Detect Underfull Pages**: After deletion, check if page size < 1/3 of maxSize
2. **Find Right Sibling**: Locate the right sibling page from parent index
3. **Merge Pages**: Combine the underfull page with its right sibling
4. **Re-split if Necessary**: If merged page > maxSize, split into two balanced pages
5. **Track Occupancy**: Decommission old page locations for GC

## Implementation Details

### 1. Added Page Merge Methods

**File:** `lsmplus-kvstore/src/main/java/org/hyperkv/lsmplus/bplustree/page/Page.java`

```java
public synchronized void merge(Page rightPage) {
    if (rightPage == null) {
        throw new IllegalArgumentException("rightPage must not be null");
    }
    if (pageType != rightPage.pageType) {
        throw new IllegalArgumentException("Cannot merge pages of different types");
    }
    
    entries.addAll(rightPage.getAllEntries());
    maxKey = rightPage.maxKey;
    usedSize = calculateUsedSize();
    setLifecycle(PageLifecycle.DIRTY);
}

public boolean isUnderfull(int threshold) {
    return usedSize < threshold;
}

public synchronized IndexPair getEntryAt(int index) {
    if (index < 0 || index >= entries.size()) {
        return null;
    }
    return entries.get(index);
}
```

### 2. Implemented Merge Handler in TreeDumper

**File:** `lsmplus-kvstore/src/main/java/org/hyperkv/lsmplus/bplustree/TreeDumper.java`

#### Delete Logic Update
```java
} else {
    int mergeThreshold = leafPage.getMaxSize() / 3;
    SegmentLocation leafLocation = leafPage.getLocation();
    if (leafPage.isUnderfull(mergeThreshold) && 
        path.size() > 1 && 
        leafLocation != null && 
        !VirtualSegmentLocation.isVirtual(leafLocation)) {
        handlePageMerge(path, leafLevel);
    } else {
        // Normal update path
    }
}
```

#### Merge Handler
```java
private void handlePageMerge(LinkedList<PathEntry> path, int leafLevel) throws KVStoreException {
    // 1. Find right sibling from parent
    IndexKey maxKey = leafPage.getMaxKey();
    int rightSiblingIndex = parentPage.getEntryIndex(maxKey) + 1;
    
    // 2. Skip if no right sibling or it has virtual location
    if (rightSiblingIndex >= parentPage.getEntryCount()) {
        return;
    }
    
    // 3. Load right sibling
    Page rightSibling = getPageFromBufferOrStorage(rightSiblingLocation);
    
    // 4. Merge pages
    leafPage.merge(rightSibling);
    
    // 5. Handle merge result
    if (leafPage.getUsedSize() > leafPage.getMaxSize()) {
        // Re-split into two balanced pages
        Page rightPage = leafPage.split(newPageId);
        addToWriteBuffer(leafLevel, leafPage);
        addToWriteBuffer(leafLevel, rightPage);
        // Update parent with both pages
    } else {
        // Single merged page
        addToWriteBuffer(leafLevel, leafPage);
        // Track decommissions
        trackPageDecommission(leftOldLocation);
        trackPageDecommission(rightOldLocation);
        // Update parent to remove right sibling
    }
}
```

## Key Design Decisions

### 1. **Merge Threshold: 1/3 of Max Size**
- **Why**: Balances merge frequency vs. merge benefit
- **Too low**: Pages stay underfull longer
- **Too high**: Excessive merging overhead

### 2. **Only Merge Persisted Pages**
- **Check**: `!VirtualSegmentLocation.isVirtual(leafLocation)`
- **Why**: Pages in write buffer haven't been persisted yet
- **Benefit**: Avoids merge complexity with uncommitted pages

### 3. **Only Merge with Right Sibling**
- **Why**: Simpler than bidirectional merge
- **Trade-off**: May miss some merge opportunities
- **Future**: Could extend to left sibling

### 4. **Re-split if Merged Page Too Large**
- **Condition**: `leafPage.getUsedSize() > leafPage.getMaxSize()`
- **Why**: Maintains balanced page sizes
- **Benefit**: Better space utilization

### 5. **Occupancy Tracking**
- **Decommission both old locations** when merging
- **Track new writes** for re-split pages
- **Ensures accurate GC decisions**

## Merge Scenarios

### Scenario 1: Simple Merge
```
Before: [Page A: 10%] [Page B: 15%]
After:  [Page A+B: 25%]

Actions:
- Merge A and B
- Decommission old A and B locations
- Update parent to remove B
```

### Scenario 2: Merge + Re-split
```
Before: [Page A: 30%] [Page B: 40%]
Merge:  [Page A+B: 70%] (> maxSize)
After:  [Page A': 35%] [Page B': 35%]

Actions:
- Merge A and B
- Split into two balanced pages
- Update parent with both new pages
- Decommission old A and B locations
```

### Scenario 3: No Merge (Virtual Location)
```
Before: [Page A: 10%] (virtual location)

Actions:
- Skip merge (page not yet persisted)
- Continue normal update path
```

## Benefits

### 1. **Improved Space Utilization**
- Underfull pages are consolidated
- Better disk space usage
- Reduced tree height

### 2. **Better Performance**
- Fewer pages to traverse
- Better cache utilization
- Reduced I/O operations

### 3. **Automatic Maintenance**
- No manual intervention needed
- Happens during normal operations
- Transparent to users

### 4. **Accurate Occupancy Tracking**
- Decommissions tracked for GC
- Correct space accounting
- Efficient garbage collection

## Testing

All tests pass successfully:
- TreeDumperTest: ✓ PASSED
- BPlusTreeTest: ✓ PASSED
- BPlusTreeFullIntegrationTest: ✓ PASSED
- OccupancyIntegrationTest: ✓ PASSED
- All lsmplus-kvstore tests: ✓ PASSED

## Future Enhancements

1. **Bidirectional Merge**: Consider merging with left sibling when right sibling doesn't exist
2. **Adaptive Threshold**: Adjust merge threshold based on tree characteristics
3. **Merge Statistics**: Track merge operations for monitoring
4. **Index Page Merging**: Extend merge logic to index pages (currently only leaf pages)
5. **Merge During Updates**: Consider merging during update operations, not just deletions

## Limitations

1. **Only Leaf Pages**: Currently only merges leaf pages, not index pages
2. **Right Sibling Only**: Doesn't consider left sibling for merging
3. **Persisted Pages Only**: Cannot merge pages still in write buffer
4. **Single Threshold**: Uses fixed 1/3 threshold, not adaptive

## Performance Impact

**Positive:**
- Reduced tree height
- Better space utilization
- Fewer pages to traverse

**Negative:**
- Additional CPU during deletions
- Potential for cascading merges
- Increased write buffer usage

**Overall**: Net positive for most workloads, especially delete-heavy ones.
