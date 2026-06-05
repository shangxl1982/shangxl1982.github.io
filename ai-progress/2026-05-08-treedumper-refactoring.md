# TreeDumper Code Refactoring - 2026-05-08

## Summary

Successfully refactored TreeDumper.java to eliminate duplicate code patterns by extracting them into reusable helper methods. This improves code maintainability, readability, and reduces the risk of bugs from inconsistent implementations.

## Duplicate Code Patterns Identified

### 1. Saving Pages with Occupancy Tracking
**Pattern:** Setting page lifecycle to FLUSHABLE, saving asynchronously, and tracking occupancy
**Locations:** 
- buildNewTree() - saving leaf pages
- buildIndexLevels() - saving index pages

**Extracted Method:** `savePagesWithTracking(List<Page> pages)`

**Before:**
```java
List<CompletableFuture<SegmentLocation>> leafFutures = new ArrayList<>();
for (Page leaf : leafPages) {
    leaf.setLifecycle(Page.PageLifecycle.FLUSHABLE);
    leafFutures.add(pageManager.savePageAsync(leaf));
}

List<SegmentLocation> leafLocations = new ArrayList<>();
for (int i = 0; i < leafFutures.size(); i++) {
    try {
        SegmentLocation location = leafFutures.get(i).get();
        leafLocations.add(location);
        trackPageWrite(location);
    } catch (Exception e) {
        throw new KVStoreException(ErrorCode.INTERNAL_ERROR, "Failed to save leaf page", e);
    }
}
```

**After:**
```java
List<SegmentLocation> leafLocations = savePagesWithTracking(leafPages);
```

### 2. Saving Root Page
**Pattern:** Saving root page, setting location, tracking occupancy, and updating tree metadata
**Locations:**
- buildNewTree() - single leaf root
- buildIndexLevels() - index root

**Extracted Method:** `saveRootPage(Page rootPage, int height)`

**Before:**
```java
rootPage.setLifecycle(Page.PageLifecycle.FLUSHABLE);
try {
    SegmentLocation rootLocation = pageManager.savePageAsync(rootPage).get();
    rootPage.setLocation(rootLocation);
    trackPageWrite(rootLocation);
    tree.setWriterRoot(rootPage);
    tree.setHeight(2);
} catch (Exception e) {
    throw new KVStoreException(ErrorCode.INTERNAL_ERROR, "Failed to save root page", e);
}
```

**After:**
```java
saveRootPage(rootPage, 2);
```

### 3. Flushing Pages with Occupancy Tracking
**Pattern:** Collecting pages to flush, tracking old locations for decommission, saving with occupancy tracking
**Locations:**
- flushLevelPages() - flushing specific pages
- flushAllLevels() - flushing all pages at a level

**Extracted Method:** `flushPagesWithOccupancyTracking(List<Page> pages, List<Long> pageIds, int level)`

**Before:**
```java
List<CompletableFuture<SegmentLocation>> futures = new ArrayList<>();
List<SegmentLocation> oldLocations = new ArrayList<>();

for (Long pageId : pageIds) {
    Page page = writeBuffer.get(level, pageId);
    if (page != null && writeBuffer.getPageLocation(level, pageId) == null) {
        SegmentLocation oldLocation = page.getLocation();
        if (oldLocation != null && !VirtualSegmentLocation.isVirtual(oldLocation)) {
            oldLocations.add(oldLocation);
        } else {
            oldLocations.add(null);
        }
        page.setLifecycle(Page.PageLifecycle.FLUSHABLE);
        futures.add(pageManager.savePageAsync(page));
        pageIdsToFlush.add(pageId);
    }
}

for (int i = 0; i < futures.size(); i++) {
    try {
        SegmentLocation location = futures.get(i).get();
        writeBuffer.setPageLocation(level, pageIdsToFlush.get(i), location);
        trackPageWrite(location);
        if (oldLocations.get(i) != null) {
            trackPageDecommission(oldLocations.get(i));
        }
    } catch (Exception e) {
        throw new KVStoreException(ErrorCode.INTERNAL_ERROR, "Failed to flush page", e);
    }
}
```

**After:**
```java
List<Page> pagesToFlush = new ArrayList<>();
List<Long> pageIdsToFlush = new ArrayList<>();

for (Long pageId : pageIds) {
    Page page = writeBuffer.get(level, pageId);
    if (page != null && writeBuffer.getPageLocation(level, pageId) == null) {
        pagesToFlush.add(page);
        pageIdsToFlush.add(pageId);
    }
}

flushPagesWithOccupancyTracking(pagesToFlush, pageIdsToFlush, level);
```

## New Helper Methods

### 1. `savePagesWithTracking(List<Page> pages)`
**Purpose:** Saves a list of pages asynchronously and tracks their occupancy
**Responsibilities:**
- Sets page lifecycle to FLUSHABLE
- Saves pages asynchronously
- Tracks page writes for occupancy
- Returns list of segment locations

### 2. `saveRootPage(Page rootPage, int height)`
**Purpose:** Saves a root page and updates tree metadata
**Responsibilities:**
- Sets page lifecycle to FLUSHABLE
- Saves root page
- Sets root page location
- Tracks page write for occupancy
- Updates tree writer root and height

### 3. `flushPagesWithOccupancyTracking(List<Page> pages, List<Long> pageIds, int level)`
**Purpose:** Flushes pages with occupancy tracking including decommission tracking
**Responsibilities:**
- Collects old locations for decommission tracking
- Sets page lifecycle to FLUSHABLE
- Saves pages asynchronously
- Updates write buffer with new locations
- Tracks page writes and decommissions for occupancy
- Handles virtual location filtering

## Benefits

### 1. **Reduced Code Duplication**
- Eliminated ~60 lines of duplicate code
- Single source of truth for common operations

### 2. **Improved Maintainability**
- Changes to page saving logic only need to be made in one place
- Easier to understand and modify

### 3. **Better Consistency**
- All page saves follow the same pattern
- Reduces risk of bugs from inconsistent implementations

### 4. **Enhanced Readability**
- Method names clearly express intent
- Main methods are shorter and more focused

### 5. **Easier Testing**
- Helper methods can be tested independently
- Simpler to mock and verify behavior

## Code Metrics

**Before Refactoring:**
- Total lines: 675
- Duplicate code blocks: 3 major patterns
- Average method length: Higher

**After Refactoring:**
- Total lines: ~660 (reduced by ~15 lines)
- Duplicate code blocks: 0
- Average method length: Lower
- New helper methods: 3

## Testing

All tests pass successfully:
- TreeDumperTest: ✓ PASSED
- BPlusTreeTest: ✓ PASSED
- BPlusTreeFullIntegrationTest: ✓ PASSED
- OccupancyIntegrationTest: ✓ PASSED
- All lsmplus-kvstore tests: ✓ PASSED

## Future Improvements

1. **Further Extraction:** Consider extracting page creation logic into helper methods
2. **Error Handling:** Could standardize error messages across helper methods
3. **Metrics:** Add metrics to track helper method performance
4. **Documentation:** Add JavaDoc comments to new helper methods

## Conclusion

This refactoring significantly improves code quality by eliminating duplicate patterns and creating reusable, well-named helper methods. The code is now more maintainable, readable, and less prone to bugs from inconsistent implementations.
