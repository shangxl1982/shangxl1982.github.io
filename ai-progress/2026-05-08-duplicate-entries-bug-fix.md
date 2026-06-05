# Critical Bug Fix: Duplicate Entries in Page Split - 2026-05-08

## Summary
Fixed a critical bug in `handlePageSplit` that caused duplicate entries in index pages when pages split.

## Bug Description

### The Problem
When a page splits, the parent page ends up with **duplicate entries** because the old mapping is not deleted before adding new mappings.

### Example of the Bug

**Before Split:**
```
Parent Index Page:
  [key5 → targetPage]

targetPage (Leaf):
  [key5, key7, key9]
```

**After Split (Buggy Code):**
```
Parent Index Page:
  [key5 → targetPage]  ← OLD MAPPING (NOT DELETED!)
  [key5 → targetPage]  ← NEW LEFT MAPPING
  [key7 → rightPage]   ← NEW RIGHT MAPPING
  
Result: 3 entries instead of 2!
```

**Expected Result:**
```
Parent Index Page:
  [key5 → targetPage]  ← LEFT MAPPING (updated)
  [key7 → rightPage]   ← RIGHT MAPPING (new)
  
Result: 2 entries (correct)
```

## Root Cause

**Location:** TreeDumper.java:472-476 (before fix)

```java
// BUGGY CODE - Missing DELETE operation!
var leftMapping = IndexPair.of(targetPage.getMinKey(), ...);
var rightMapping = IndexPair.of(rightPage.getMinKey(), ...);

ArrayList<Pair<Common.OperationType, IndexPair>> updatesToParent = new ArrayList<>();
updatesToParent.add(Pair.of(Common.OperationType.PUT, leftMapping));
updatesToParent.add(Pair.of(Common.OperationType.PUT, rightMapping));
```

**Issue:** Only PUT operations are added, no DELETE operation for the old mapping!

## The Fix

**Location:** TreeDumper.java:447-489 (after fix)

```java
// Save the old min key before split
IndexKey oldMinKey = targetPage.getMinKey();

// ... perform split ...

ArrayList<Pair<Common.OperationType, IndexPair>> updatesToParent = new ArrayList<>();

// Delete the old mapping from parent (if it exists and changed)
if (oldMinKey != null && !oldMinKey.equals(targetPage.getMinKey())) {
    updatesToParent.add(Pair.of(Common.OperationType.DELETE, 
        IndexPair.of(oldMinKey, VirtualSegmentLocation.create(targetPage.getPageId()))));
}

// Add new mappings for left and right pages
updatesToParent.add(Pair.of(Common.OperationType.PUT, leftMapping));
updatesToParent.add(Pair.of(Common.OperationType.PUT, rightMapping));
```

## Why This Matters

### Impact of the Bug
1. **Duplicate Entries:** Parent pages have multiple references to the same child
2. **Tree Corruption:** Incorrect routing during searches
3. **Memory Waste:** Extra entries consume space
4. **Performance Degradation:** Searches may check duplicate paths
5. **Data Inconsistency:** Could return wrong results

### How It Manifested
- Found during DemoDataGenerator testing
- Duplicate entries in index pages
- Old page references not cleaned up
- Tree structure became inconsistent

## Comparison with Similar Code

**Correct Pattern (from insertIntoTree):**
```java
var leafMapping = leafPage.getMinKey();
leafPage.put(key, value);
// ...
if (leafMapping != null && !leafMapping.equals(leafPage.getMinKey())){
    updatesToParent.add(Pair.of(Common.OperationType.DELETE, ...));  // ← DELETE old!
}
updatesToParent.add(Pair.of(Common.OperationType.PUT, ...));  // ← PUT new!
```

**Now Fixed in handlePageSplit:**
```java
IndexKey oldMinKey = targetPage.getMinKey();
// ... split ...
if (oldMinKey != null && !oldMinKey.equals(targetPage.getMinKey())){
    updatesToParent.add(Pair.of(Common.OperationType.DELETE, ...));  // ← DELETE old!
}
updatesToParent.add(Pair.of(Common.OperationType.PUT, leftMapping));   // ← PUT new!
updatesToParent.add(Pair.of(Common.OperationType.PUT, rightMapping));  // ← PUT new!
```

## Test Results

All 24 tests in TreeDumperTest pass, including:
- ✅ testMultiplePageSplitsWithEdgeInserts
- ✅ testPageSplitWithMinKeyChange
- ✅ testMixedPutAndDeleteOperations
- ✅ All edge case tests

## Files Modified
- `lsmplus-kvstore/src/main/java/org/hyperkv/lsmplus/bplustree/TreeDumper.java`
  - Added old min key tracking before split
  - Added DELETE operation for old mapping
  - Proper cleanup of parent references

## Key Takeaways

1. **Always clean up old references** when updating parent pointers
2. **Follow the DELETE-then-PUT pattern** for min key changes
3. **Test with real data generators** to catch bugs unit tests might miss
4. **Consistency is key** - apply the same pattern across similar operations

## Prevention

To prevent similar bugs in the future:
1. Always check if old references need to be deleted
2. Use consistent patterns for parent updates
3. Add validation to detect duplicate entries
4. Test with larger datasets and edge cases
