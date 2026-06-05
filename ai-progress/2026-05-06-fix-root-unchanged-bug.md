# 2026-05-06 Fix Root Unchanged and Split Bugs in TreeDumper

## Problem 1: Root Unchanged After Dump

When running `DemoDataGenerator`, the root location remained unchanged for multiple tree versions after dump:

```
tree version=5, root=offset=2359296
tree version=6, root=offset=2359296  # Same as version 5
tree version=7, root=offset=2359296  # Same as version 5
tree version=8, root=offset=2359296  # Same as version 5
tree version=9, root=offset=2359296  # Same as version 5
```

## Problem 2: Split Leaves Duplicate Entries

After a page split, both the original page and the new page contained the same entries:

```
Page ID 1031 (new page): entries [0-2] = key-1-0142751e..., key-1-0144a5fd..., key-1-014625a5...
Page ID 6 (original page): entries [4-6] = same keys!
```

This happened because the parent's entry for the original page was not updated to point to the new (virtual) location, so reads went to the old location and got the old version of the page.

## Root Causes

### Cause 1: Missing Ancestor Propagation

The `updateParentWithVirtualLocation` method only updated the **immediate parent** of a modified page. In an append-only B+Tree, when a child page changes location, ALL ancestors must be updated to point to the new child location, cascading up to the root.

The design document explicitly states: "级联更新直到根页" (cascade update until root page).

### Cause 2: Missing Left Page Update in Split

The `propagateSplitToParent` method only added a new entry for the **right** page after a split, but never updated the parent's existing entry for the **left** page (the original page before split). This caused reads to go to the old location of the left page.

## Fixes

### Fix 1: Propagate Updates to All Ancestors

Modified `updateParentWithVirtualLocation` to iterate through all ancestors from the immediate parent up to the root:

```java
Page currentPage = childPage;
int currentLevel = childLevel;

for (int i = path.size() - 2; i >= 0; i--) {
    PathEntry parentEntry = path.get(i);
    Page parentPage = parentEntry.page;
    // ... update each ancestor
    currentPage = parentPage;
    currentLevel = parentLevel;
}
```

### Fix 2: Update Parent's Entry for Left Page After Split

Modified `propagateSplitToParent` to find and update the parent's existing entry for the left page:

```java
IndexKey leftMinKey = leftPage.getMinKey();
int leftChildIndex = parentPage.getEntryIndex(leftMinKey);
if (leftChildIndex < 0) {
    leftChildIndex = -leftChildIndex - 2;
    if (leftChildIndex < 0) {
        leftChildIndex = 0;
    }
}

SegmentLocation leftVirtualLocation = VirtualSegmentLocation.create(leftPage.getPageId());
parentPage.updateIndexPair(leftChildIndex, IndexPair.of(leftMinKey, leftVirtualLocation));
```

### Fix 3: Handle Pages with Real Locations

Fixed `addToWriteBuffer` to properly handle pages loaded from storage that have real locations:

```java
if (page.getLocation() == null || !VirtualSegmentLocation.isVirtual(page.getLocation())) {
    page.setLocation(VirtualSegmentLocation.create(page.getPageId()));
}
```

## Files Changed

- `lsmplus-kvstore/src/main/java/org/hyperkv/lsmplus/bplustree/TreeDumper.java`

## Tests

All existing tests pass after the fix.
