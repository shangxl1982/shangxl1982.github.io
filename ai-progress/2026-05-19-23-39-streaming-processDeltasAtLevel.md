# Streaming Approach for processDeltasAtLevel

## Problem
The `processDeltasAtLevel` method in TreeDumper.java was holding all parent pages in memory during delta processing. This approach doesn't scale well when there are many parent pages at a given level.

## Original Implementation
The original implementation had two phases:
1. **Phase 1**: Load all parent pages into memory (`processedPages` TreeMap)
2. **Phase 2**: Sort and process merge/split operations

This approach required holding all parent pages in memory simultaneously, which could cause memory issues with large trees.

## New Implementation
The new implementation uses a streaming approach similar to how `updateExistingTree` handles leaf pages:

1. **Process pages in sorted order**: Parent pages are processed one by one in sorted order (by minKey)
2. **Track only the working set**: Instead of storing all pages, only track:
   - The last processed page (for merge/split checking)
   - Pages that were merged as right siblings (to skip them)
3. **Incremental flushing**: Call `flushCompletedPages` periodically to flush pages that won't be modified anymore
4. **Leverage existing infrastructure**: Use the `LevelWriteBuffer` to track pages and determine flushability

## Key Changes

### Removed Data Structures
- `Map<Long, Page> processedPages` - No longer needed
- `Map<Long, IndexKey> oldMinKeys` - No longer needed
- Second loop for processing sorted pages - Eliminated

### New Approach
- Process pages in a single pass
- Track only `lastProcessedPage` and `lastOldMinKey`
- Check merge/split for the previous page before moving to the next
- Flush completed pages incrementally based on key ranges

### Memory Efficiency
- Pages are flushed as soon as they're complete (maxKey < current processing key)
- Only the working set is kept in memory
- Similar to how leaf pages are handled in `updateExistingTree`

## Benefits
1. **Reduced memory usage**: Only keeps the working set in memory, not all parent pages
2. **Better scalability**: Can handle trees with many parent pages at each level
3. **Consistent approach**: Uses the same streaming pattern as leaf page processing
4. **Maintains correctness**: Still properly handles merge/split operations

## Testing
- All existing TreeDumper tests pass
- The implementation maintains the same behavior as before
- One pre-existing test failure (`OccupancyDecommissionBugFixTest`) is unrelated to this change

## Code Location
File: `lsmplus-kvstore/src/main/java/org/hyperkv/lsmplus/bplustree/TreeDumper.java`
Method: `processDeltasAtLevel(int level)`
