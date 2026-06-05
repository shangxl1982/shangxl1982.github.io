# 2026-04-19 AsyncDumpExecutor Merge Sort Optimization

## Changes

Modified `AsyncDumpExecutor.java` to improve merge performance when merging sealed MemoryTables.

### Before
- `MergeResult.entries` was a `TreeMap<IndexKey, IndexValue>`
- Merge was done by iterating through each MemoryTable and putting entries into the TreeMap
- Time complexity: O(n log n) where n is total entries

### After
- `MergeResult.entries` is now a `List<Map.Entry<IndexKey, IndexValue>>`
- Implemented k-way merge sort using a min-heap (PriorityQueue)
- Time complexity: O(n log k) where n is total entries and k is number of tables

### Key Implementation Details

1. **MergeResult class**: Changed `entries` field from `TreeMap` to `List<Map.Entry<IndexKey, IndexValue>>`

2. **mergeSortSealedTables method**: New method implementing k-way merge sort:
   - Uses `PriorityQueue` (min-heap) to efficiently merge k sorted lists
   - Each MemoryTable already has sorted data (ConcurrentSkipListMap)
   - Handles duplicate keys by keeping the latest value (from later tables)

3. **IteratorEntry class**: Helper class for the merge algorithm:
   - Wraps an iterator over a MemoryTable's entries
   - Tracks current key and value
   - Used in the min-heap for comparison

4. **findMaxReplayPoint method**: Extracted to a separate method for cleaner code

5. **Updated call**: Changed from `dumpFromTreeMap` to `dump` method in TreeDumper

## Performance Improvement

- Old approach: O(n log n) - each entry inserted into TreeMap requires O(log n) comparison
- New approach: O(n log k) - each entry comparison is O(log k) where k is the number of tables
- For large datasets with few tables, this provides significant performance improvement
