# Unified Page Class Refactoring

**Date:** 2026-04-15
**Status:** ✅ Complete

## Overview

Unified `LeafPage` and `IndexPage` into a single `Page` class with `IndexPair` sealed interface to represent both key-value and key-address pairs. This simplifies the codebase and reduces code duplication.

## Changes Made

### 1. Simplified IndexPair Sealed Interface
**File:** `lsmplus-bplustree/src/main/java/org/hyperkv/lsmplus/bplustree/page/IndexPair.java`

```java
public sealed interface IndexPair permits IndexPair.ValueEntry, IndexPair.LocationEntry {

    IndexKey key();  // Key is now part of IndexPair

    KeyValuePairProto toProto();

    static IndexPair of(IndexKey key, IndexValue value);
    static IndexPair of(IndexKey key, SegmentLocation location);
    static IndexPair fromProto(KeyValuePairProto proto);

    record ValueEntry(IndexKey key, IndexValue value) implements IndexPair { }
    record LocationEntry(IndexKey key, SegmentLocation location) implements IndexPair { }
}
```

**Key Design:**
- IndexPair now contains both key AND value/location
- Two parameters in constructor: `of(key, value)` or `of(key, location)`
- Direct mapping to KeyValuePairProto for serialization

### 2. Refactored Page Class with ArrayList
**File:** `lsmplus-bplustree/src/main/java/org/hyperkv/lsmplus/bplustree/page/Page.java`

- Changed from `TreeMap<IndexKey, IndexPair>` to `ArrayList<IndexPair>`
- Uses binary search for O(log n) lookups
- Maintains sorted order on insert
- Unified `rangeQuery()` method returns `List<IndexPair>`

**Key Methods:**
- `put(IndexKey, IndexValue)` - for leaf pages
- `put(IndexKey, SegmentLocation)` - for index pages
- `get(IndexKey)` - returns IndexValue for leaf pages
- `getChildLocation(IndexKey)` - returns SegmentLocation for index pages
- `rangeQuery(start, end)` - unified range query returning List<IndexPair>
- `getAllEntries()` - returns all entries as List<IndexPair>

### 3. Updated BPlusTree.java
**File:** `lsmplus-bplustree/src/main/java/org/hyperkv/lsmplus/bplustree/BPlusTree.java`

- Uses `page.rangeQuery()` instead of `page.rangeQueryValues()`
- Uses `page.getAllEntries()` instead of `page.getEntries()`
- Converts IndexPair to Map.Entry for API compatibility

### 4. Updated Test Files
**Files:**
- `LeafPageTest.java` - Updated to use `rangeQuery()`
- `IndexPageTest.java` - Updated to use `rangeQuery()`
- `PagesIntegrationTest.java` - Updated to use `rangeQuery()`
- `PageManagerTest.java` - Uses unified Page class
- `WriteBufferTest.java` - Uses unified Page class

## Automatic Dump Trigger

### 5. Added DumpCallback Interface
**File:** `lsmplus-memory/src/main/java/org/hyperkv/lsmplus/memory/DumpCallback.java`

```java
@FunctionalInterface
public interface DumpCallback {
    void onTableSealed(int sealedTableCount);
}
```

### 6. Updated MemoryTableManager
**File:** `lsmplus-memory/src/main/java/org/hyperkv/lsmplus/memory/MemoryTableManager.java`

- Added `setDumpCallback(DumpCallback)` method
- Calls callback when a table is sealed via `notifyDumpCallback()`
- Callback receives the current sealed table count

### 7. Updated KVStore
**File:** `lsmplus-core/src/main/java/org/hyperkv/lsmplus/core/KVStore.java`

- Registers dump callback during initialization
- Automatically triggers dump when sealed table count >= maxSealedTables
- Added `triggerDump()` method for callback invocation

**Flow:**
1. MemoryTable reaches max size → sealed automatically
2. `checkAndSeal()` calls `notifyDumpCallback()`
3. Callback checks if sealedCount >= maxSealedTables
4. If threshold reached → `triggerDump()` → `treeDumper.dump()`

## Benefits

1. **Simpler IndexPair:** Key is now part of the pair, matching KeyValuePairProto structure
2. **ArrayList Storage:** More memory-efficient for sequential access patterns
3. **Binary Search:** O(log n) lookup performance maintained
4. **Unified API:** Single `rangeQuery()` method for both page types
5. **Direct Serialization:** IndexPair directly maps to/from protobuf
6. **Automatic Dump:** No manual intervention needed for tree dumping

## Design Pattern

The unified Page class uses:
- **Sealed Interface Pattern:** IndexPair with ValueEntry and LocationEntry records
- **Factory Method Pattern:** `createLeaf()` and `createIndex()` static methods
- **Binary Search:** Maintains sorted order with efficient lookups
- **Callback Pattern:** DumpCallback for automatic dump triggering

## WriteBuffer Integration

### 8. Fixed TreeDumper to Use WriteBuffer
**File:** `lsmplus-bplustree/src/main/java/org/hyperkv/lsmplus/bplustree/TreeDumper.java`

**Problem:** TreeDumper had a WriteBuffer instance but wasn't using it properly:
- `insertIntoTree()` modified pages directly without adding to write buffer
- `deleteFromTree()` modified pages directly without adding to write buffer
- `handlePageSplit()` saved pages directly to storage instead of buffering

**Solution:**
```java
private void insertIntoTree(IndexKey key, IndexValue value) {
    Page leafPage = findLeafPage(key);
    if (leafPage != null) {
        leafPage.put(key, value);
        addToWriteBuffer(leafPage);  // Now adds to buffer
    }
}

private void deleteFromTree(IndexKey key) {
    Page leafPage = findLeafPage(key);
    if (leafPage != null) {
        leafPage.delete(key);
        addToWriteBuffer(leafPage);  // Now adds to buffer
    }
}

private void addToWriteBuffer(Page page) {
    writeBuffer.put(page.getPageId(), page, page.getMaxKey());
}
```

**Added Methods:**
- `addToWriteBuffer(Page)` - Adds modified page to buffer with max key
- `getPageFromBufferOrStorage(SegmentLocation)` - Checks buffer first, then storage

**Flow:**
1. Modify page (insert/delete)
2. Add to WriteBuffer with max key
3. `flushCompletedPages()` flushes pages where maxKey < currentKey
4. `flushAllPages()` flushes remaining pages at end

## Parent Page Update on Split

### 9. Fixed Page Split to Update Parent Index
**File:** `lsmplus-bplustree/src/main/java/org/hyperkv/lsmplus/bplustree/TreeDumper.java`

**Problem:** When a leaf page splits, the parent index page was never updated with the new child location.

**Solution:** Track the path from root to leaf during traversal, then propagate splits up the tree.

**New Methods:**
```java
// Track path during tree traversal
private LinkedList<Page> findLeafPageWithPath(IndexKey key) {
    LinkedList<Page> path = new LinkedList<>();
    // ... traverse from root to leaf, adding each page to path
    return path;
}

// Handle split with parent update
private void handlePageSplit(LinkedList<Page> path, IndexKey key, IndexValue value) {
    Page leafPage = path.removeLast();
    Page rightPage = leafPage.split(newPageId);
    
    // Save both pages and get locations
    SegmentLocation leftLocation = savePageAndGetLocation(leafPage);
    SegmentLocation rightLocation = savePageAndGetLocation(rightPage);
    
    // Propagate to parent
    propagateSplitToParent(path, separatorKey, leftLocation, rightLocation);
}

// Propagate split up the tree (handles cascading splits)
private void propagateSplitToParent(LinkedList<Page> path, IndexKey separatorKey,
                                    SegmentLocation leftLocation, SegmentLocation rightLocation) {
    if (path.isEmpty()) {
        createNewRoot(separatorKey, leftLocation, rightLocation);
        return;
    }
    
    Page parentPage = path.removeLast();
    if (parentPage.hasSpaceForEntry(separatorKey, rightLocation)) {
        parentPage.put(separatorKey, rightLocation);
    } else {
        // Parent also needs to split - recursive
        Page rightParent = parentPage.split(newPageId);
        parentPage.put(separatorKey, rightLocation);
        propagateSplitToParent(path, parentSeparatorKey, ...);
    }
}

// Create new root when split reaches the top
private void createNewRoot(IndexKey separatorKey, SegmentLocation leftLocation, 
                           SegmentLocation rightLocation) {
    Page newRoot = Page.createIndex(newRootId, tree.getIndexPageMaxSize());
    newRoot.setLeftmostChild(leftLocation);
    newRoot.put(separatorKey, rightLocation);
    tree.setRootLocation(rootLocation);
    tree.setHeight(tree.getHeight() + 1);
}
```

**Flow:**
```
insertIntoTree(key, value)
        ↓
findLeafPageWithPath(key) → returns [root, ..., parent, leaf]
        ↓
leafPage needs split?
        ↓ Yes
handlePageSplit(path, key, value)
        ↓
split leaf → left + right pages
        ↓
propagateSplitToParent(path, separatorKey, leftLoc, rightLoc)
        ↓
parent has space? → update parent
        ↓ No
parent also splits → recursive propagation
        ↓
path empty? → createNewRoot()
```

## Level-Based Write Buffer

### 10. Implemented LevelWriteBuffer
**File:** `lsmplus-bplustree/src/main/java/org/hyperkv/lsmplus/bplustree/LevelWriteBuffer.java`

**Problem:** Pages were persisted immediately during splits, before children were fully written. This could leave dangling pointers if a crash occurred mid-operation.

**Solution:** Organize the write buffer by tree level to ensure proper write ordering:
- Level 0: Leaf pages (bottom of tree)
- Level 1+: Index pages (higher levels toward root)

**Key Design Principles:**
1. Pages are buffered by level to delay persistence
2. Flushing happens from bottom (level 0) to top (root level)
3. Parent pages are only written AFTER all their children are persisted
4. This ensures crash consistency - no dangling pointers in the tree

**Flushable Page Detection:**
A page is "flushable" when its maxKey is less than the current key being processed, meaning no more modifications will be made to that page.

```java
public class LevelWriteBuffer {
    private final Map<Integer, LevelBuffer> levels;  // level -> pages
    
    void put(int level, Page page);              // Add page at specific level
    List<Integer> getLevelsInFlushOrder();       // Returns levels 0, 1, 2, ...
    List<Integer> getFlushablePageIds(int level); // Pages with maxKey < currentKey
}
```

**Write Ordering Flow:**
```
insertIntoTree(key, value)
        ↓
findLeafPageWithPath(key) → [root@level=N, ..., leaf@level=0]
        ↓
Modify leaf → addToWriteBuffer(level=0, leaf)
        ↓
flushCompletedPages() → flushes level 0 first, then level 1, etc.
        ↓
flushAllLevels() → at end, flush remaining pages bottom-to-top
```

**Benefits:**
1. **Crash Consistency:** Children are always persisted before parents
2. **Better Batching:** Multiple pages can be flushed together
3. **No Dangling Pointers:** Parent never points to unpersisted child
4. **Efficient Memory Use:** Pages stay in buffer until ready to flush

### 11. Delayed Parent Updates for Better Performance
**Problem:** Previously, child pages were persisted immediately during split propagation, then parent was updated. This caused many small writes.

**Solution:** Delay all parent updates until flush time:
1. During split propagation, only add pages to buffer (no persistence)
2. Record pending parent updates: (parentPageId, separatorKey, childPageId)
3. During flush, persist children first, then resolve parent updates using child locations

```java
// PendingParentUpdate tracks what needs to be applied during flush
public static class PendingParentUpdate {
    final int parentLevel;
    final int parentPageId;
    final IndexKey separatorKey;
    final int childPageId;    // Location resolved during flush
    final int childLevel;
}
```

**Flush Process:**
```
for each level (bottom to top):
    1. Persist all pages at this level → get SegmentLocations
    2. Apply pending parent updates:
       - Get child location from persisted pages
       - Update parent: parentPage.put(separatorKey, childLocation)
    3. Parents will be persisted when their level is flushed
```

**Performance Improvement:**
- Batches multiple writes together
- Reduces I/O operations significantly
- Still maintains crash consistency (children before parents)

### 12. Virtual Locations for Pending Child References
**Problem:** The pending parent update tracking required a separate data structure and complex management.

**Solution:** Use virtual locations stored directly in parent pages:
- Virtual location format: `chunkId=0 (UUID 0-0-0-0...), offset=childPageId, length=0`
- Stored directly in parent page entries
- Easy to check if parent has pending children: `VirtualSegmentLocation.hasVirtualChildReferences(page)`
- When children are persisted, resolve virtual locations to real locations

**New File:** `VirtualSegmentLocation.java`
```java
public final class VirtualSegmentLocation {
    public static final UUID VIRTUAL_CHUNK_ID = new UUID(0L, 0L);
    
    public static SegmentLocation create(int pageId);
    public static boolean isVirtual(SegmentLocation location);
    public static int getPageId(SegmentLocation location);
    public static boolean hasVirtualChildReferences(Page page);
}
```

**Simplified Flow:**
```
propagateSplitToParent():
    virtualLocation = VirtualSegmentLocation.create(rightPageId)
    parentPage.put(separatorKey, virtualLocation)  // Direct update!

flushLevelPages(level):
    1. Persist all pages at this level
    2. writeBuffer.resolveVirtualLocations(parentLevel, childLevel)
    3. Parents now have real locations, can be flushed
```

**Benefits:**
1. **Simpler Code:** No separate tracking structure needed
2. **Direct Modification:** Parent pages are modified directly
3. **Easy Detection:** Simple check for virtual locations
4. **Better Performance:** Less overhead, cleaner implementation

### 13. Page ID Management with Long IDs
**Problem:** Using `int` for page IDs could overflow. Also need unique IDs for both leaf and index pages.

**Solution:** Use `long` for page IDs with sign-based differentiation:
- **Positive IDs (1, 2, 3, ...):** Leaf pages
- **Negative IDs (Long.MIN_VALUE, Long.MIN_VALUE+1, ...):** Index pages
- **0:** Invalid/null page ID

**New File:** `PageIdManager.java`
```java
public class PageIdManager {
    public static final long INVALID_PAGE_ID = 0L;
    
    private long nextLeafPageId = 1L;
    private long nextIndexPageId = Long.MIN_VALUE;
    
    public synchronized long allocateLeafPageId();
    public synchronized long allocateIndexPageId();
    public static boolean isLeafPageId(long pageId);
    public static boolean isIndexPageId(long pageId);
}
```

**Changes Required:**
1. Updated `Page.pageId` from `int` to `long`
2. Updated `SegmentLocation.offset` from `int` to `long` (supports negative virtual locations)
3. Updated protobuf definitions to use `int64` for page IDs and offsets
4. Updated `LevelWriteBuffer` to use `long` for page ID keys

**Benefits:**
1. **No Overflow:** 64-bit IDs provide virtually unlimited page IDs
2. **Easy Type Detection:** Check sign to determine page type
3. **Monotonic Allocation:** Separate sequences for leaf and index pages
4. **Persistence Ready:** Max page IDs stored in tree metadata for recovery
