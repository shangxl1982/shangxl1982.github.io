# Unified checkMergeSplit Methods

## Problem
The TreeDumper had two separate methods for checking merge/split operations:
1. `checkMergeSplitForLastPath(List<PathEntry> path)` - for leaf pages
2. `checkMergeSplitForParentPage(Page parentPage, IndexKey oldMinKey, int level, Set<Long> mergedPages)` - for index pages

These methods had similar logic but different signatures and slight differences in behavior.

## Solution
Created a unified `checkMergeSplitForPage` method that handles both leaf and index pages.

## Implementation Details

### New Unified Method
```java
private void checkMergeSplitForPage(Page page, IndexKey oldMinKey, int level, 
                                     Page parentPage, Set<Long> mergedPages) throws KVStoreException
```

**Parameters:**
- `page` - The page to check (can be leaf or index)
- `oldMinKey` - The old minimum key of the page
- `level` - The tree level of the page
- `parentPage` - The parent page (required for leaf pages, null for index pages)
- `mergedPages` - Set to track merged page IDs (optional)

**Logic:**
1. Check if page is null or empty - return early
2. Check if page is overFull - call `handlePageSplit`
3. Check if page is underfull:
   - For leaf pages: call `handlePageMerge` with parentPage
   - For index pages: call `handleIndexPageMerge` (loads grandparent internally)
4. Normal case (not overFull, not underfull):
   - For index pages only: call `createParentDeltaForUpdatedPage`
   - For leaf pages: do nothing (parent delta is created elsewhere in the flow)

### Key Design Decision
The critical difference between leaf and index page handling:
- **Leaf pages**: Parent delta is created in `updateExistingTree` main flow, so we don't create it here
- **Index pages**: Parent delta must be created in the normal case to propagate changes upward

This is why the unified method checks `!page.isLeaf()` before calling `createParentDeltaForUpdatedPage`.

### Updated Callers

#### checkMergeSplitForLastPath
Now simply calls the unified method:
```java
checkMergeSplitForPage(leafPage, origMappingKey, leafEntry.level, parentPage, null);
```

#### checkMergeSplitForParentPage
Now simply delegates to the unified method:
```java
checkMergeSplitForPage(parentPage, oldMinKey, level, null, mergedPages);
```

## Benefits
1. **Code reuse**: Eliminates duplicate logic
2. **Maintainability**: Single place to update merge/split logic
3. **Consistency**: Ensures both leaf and index pages are handled consistently
4. **Clarity**: Makes the differences between leaf and index page handling explicit

## Testing
✅ All TreeDumper tests pass  
✅ EntryCountBasedPageIntegrationTest passes  
✅ 406 out of 407 tests pass (1 pre-existing failure unrelated to this change)

## Files Changed
- `lsmplus-kvstore/src/main/java/org/hyperkv/lsmplus/bplustree/TreeDumper.java`
  - Added: `checkMergeSplitForPage` (new unified method)
  - Modified: `checkMergeSplitForLastPath` (now calls unified method)
  - Modified: `checkMergeSplitForParentPage` (now delegates to unified method)
