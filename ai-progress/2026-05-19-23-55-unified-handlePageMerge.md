# Unified handlePageMerge Methods

## Problem
The TreeDumper had two separate methods for handling page merges:
1. `handlePageMerge(Page targetPage, Page parentPage, IndexKey origTargetPageMappingKey, int parentLevel)` - for leaf pages
2. `handleIndexPageMerge(Page parentPage, IndexKey oldMinKey, int level)` - for index pages

These methods had nearly identical logic but different signatures and slight differences in behavior.

## Key Differences Between Original Methods

### handlePageMerge (leaf pages)
- Takes parent page as parameter
- Returns `void`
- Does NOT track page decommission
- Level calculation: `targetLevel = parentLevel - 1`

### handleIndexPageMerge (index pages)
- Loads grandparent from `getParentLocation`
- Returns `Long` (merged page ID for tracking)
- DOES track page decommission
- Level calculation: uses `level` directly

## Solution
Created a unified `handlePageMergeInternal` method that handles both leaf and index page merges.

## Implementation Details

### New Unified Method
```java
private Long handlePageMergeInternal(Page targetPage, IndexKey oldMinKey, int targetLevel, 
                                      Page parentPage) throws KVStoreException
```

**Parameters:**
- `targetPage` - The page to merge (can be leaf or index)
- `oldMinKey` - The old minimum key of the page
- `targetLevel` - The tree level of the target page
- `parentPage` - The parent page (provided for leaf pages, null for index pages)

**Return Value:**
- Returns the merged page ID (for index page tracking), or null

**Logic:**
1. **Root check**: If target is root, just add to write buffer and return null
2. **Parent determination**:
   - If `parentPage` is provided (leaf case), use it directly
   - If `parentPage` is null (index case), load parent from `getParentLocation`
3. **Find right sibling**: Get the next entry in parent
4. **Merge operation**: Merge target with right sibling
5. **Handle overflow**: If merged page is overfull, split it back
6. **Update parent**: Create deltas to update parent mappings
7. **Decommission tracking**: Only track decommission for index pages (when `parentPage == null`)

### Wrapper Methods

Both original methods now delegate to the unified method:

```java
private void handlePageMerge(Page targetPage, Page parentPage, 
                              IndexKey origTargetPageMappingKey, int parentLevel) {
    handlePageMergeInternal(targetPage, origTargetPageMappingKey, parentLevel - 1, parentPage);
}

private Long handleIndexPageMerge(Page parentPage, IndexKey oldMinKey, int level) {
    return handlePageMergeInternal(parentPage, oldMinKey, level, null);
}
```

## Key Design Decisions

1. **Unified level handling**: The unified method takes `targetLevel` directly, simplifying level calculations
2. **Optional parent parameter**: `parentPage` can be null, indicating index page case
3. **Conditional decommission tracking**: Only track decommission when `parentPage == null` (index pages)
4. **Consistent error handling**: All error cases now add the page to write buffer before returning null

## Benefits

1. **Code reuse**: Eliminates ~100 lines of duplicate logic
2. **Maintainability**: Single place to update merge logic
3. **Consistency**: Ensures both leaf and index pages are handled consistently
4. **Better error handling**: Unified method handles all error cases consistently

## Testing

✅ All TreeDumper tests pass  
✅ 406 out of 407 tests pass (1 pre-existing failure unrelated to this change)

## Files Changed

- `lsmplus-kvstore/src/main/java/org/hyperkv/lsmplus/bplustree/TreeDumper.java`
  - Added: `handlePageMergeInternal` (new unified method)
  - Modified: `handlePageMerge` (now delegates to unified method)
  - Modified: `handleIndexPageMerge` (now delegates to unified method)
  - Removed: Old duplicate implementation of `handleIndexPageMerge`

## Code Reduction

- **Before**: ~160 lines (80 + 80 for two separate methods)
- **After**: ~90 lines (unified method + two simple wrappers)
- **Reduction**: ~70 lines of code eliminated
