# Page Type Refactoring: INDEX to ROOT/BRANCH/LEAF

## Date
2026-05-11

## Summary
Refactored page types from the binary INDEX/LEAF classification to a more semantic ROOT/BRANCH/LEAF classification to better represent the role of each page in the B+ tree structure.

## Changes

### 1. Protobuf Definition (common.proto)
- **Before**: `PAGE_LEAF` (0), `PAGE_INDEX` (1)
- **After**: `PAGE_LEAF` (0), `PAGE_BRANCH` (1), `PAGE_ROOT` (2)

### 2. Java Enum (Page.java)
- **Before**: `LEAF`, `INDEX`
- **After**: `LEAF`, `BRANCH`, `ROOT`

### 3. Factory Methods (Page.java)
- **Removed**: `createIndex()`
- **Added**: `createRoot()`, `createBranch()`
- **Kept**: `createLeaf()`

### 4. Helper Methods (Page.java)
- **Added**: `isRoot()`, `isBranch()`
- **Updated**: `isIndex()` now returns true for both ROOT and BRANCH pages

### 5. TreeDumper.java Updates
- Created separate `createRootPage()` and `createBranchPage()` methods
- Updated page creation logic:
  - Root pages are created when building the top-level index
  - Branch pages are created for intermediate index levels
  - When a branch page becomes the final root, it's converted to ROOT type

### 6. Test Files
- Updated all test files to use `createBranch()` instead of `createIndex()`
- Added tests for `createRoot()` and `createBranch()` methods
- Updated PageType enum tests to verify all three types

### 7. Documentation
- Updated [design/design-page.md](../design/design-page.md) to reflect the three page types
- Updated [lsmplus-api/PROTOBUF_USAGE.md](../lsmplus-api/PROTOBUF_USAGE.md) with new PageType values

## Design Rationale

### Why Three Types?
The previous INDEX/LEAF classification didn't distinguish between the root page and intermediate index pages. The new ROOT/BRANCH/LEAF classification provides:

1. **Semantic Clarity**: Each type clearly indicates the page's role in the tree
2. **Better Diagnostics**: Easier to identify root pages in debugging and monitoring
3. **Future Enhancements**: Enables potential optimizations specific to root pages

### Tree Structure Guarantee
- The B+ tree always has at least one root page and one leaf page, or is empty
- No single-leaf-page trees without a root
- This ensures consistent tree structure and simplifies logic

### Page Type Determination
- **ROOT**: Created when building the top-level index page
- **BRANCH**: Created for intermediate index levels
- **LEAF**: Created for data storage

When a branch page becomes the final single page at the top level, it's converted to ROOT type before being saved as the root.

## Testing
All existing tests pass with the new classification:
- Unit tests for Page creation and serialization
- Integration tests for B+ tree operations
- Tree dump and update tests

## Backward Compatibility
This is a breaking change for the protobuf format. Any existing serialized pages with `PAGE_INDEX` type will need to be migrated or will be unrecognized.

## Files Modified
- `lsmplus-api/src/main/proto/common.proto`
- `lsmplus-kvstore/src/main/java/org/hyperkv/lsmplus/bplustree/page/Page.java`
- `lsmplus-kvstore/src/main/java/org/hyperkv/lsmplus/bplustree/TreeDumper.java`
- `lsmplus-kvstore/src/test/java/org/hyperkv/lsmplus/bplustree/page/PageTest.java`
- All other test files using `createIndex()`
- `design/design-page.md`
- `lsmplus-api/PROTOBUF_USAGE.md`
