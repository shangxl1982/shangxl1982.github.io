# Performance Counter Fix

## Date
2026-04-23

## Issue
During testing, only the `kvstore.put` counter was appearing in the performance-counter.log file. Other counters (tree_search, tree_range_query, journal_write, journal_write_batch, journal_rotate_chunk) were not being logged.

## Root Cause
The MetricsRegistry was being initialized AFTER Journal and BPlusTree objects were created. This caused these components to receive NoOpPerformanceCounter instances instead of real PerformanceCounter instances.

### Code Flow Before Fix
1. `chunkManager = new ChunkManager(...)` - Created
2. `journal = new Journal(...)` - Created, calls `MetricsRegistry.getCounter()` → Returns NoOpPerformanceCounter (registry is null)
3. `bPlusTree = new BPlusTree(...)` - Created, calls `MetricsRegistry.getCounter()` → Returns NoOpPerformanceCounter (registry is null)
4. `MetricsRegistry.initialize("kvstore")` - Registry initialized
5. `putCounter = MetricsRegistry.getCounter("put")` - Returns real PerformanceCounter
6. `registry.start()` - Starts collecting metrics

## Fix
Moved MetricsRegistry initialization to occur BEFORE creating Journal and BPlusTree objects.

### Code Flow After Fix
1. `MetricsRegistry.initialize("kvstore")` - Registry initialized
2. `metricsLogger = new MetricsLogger(...)` - Logger created and added as listener
3. `putCounter = MetricsRegistry.getCounter("put")` - Returns real PerformanceCounter
4. `chunkManager = new ChunkManager(...)` - Created
5. `journal = new Journal(...)` - Created, calls `MetricsRegistry.getCounter()` → Returns real PerformanceCounter
6. `bPlusTree = new BPlusTree(...)` - Created, calls `MetricsRegistry.getCounter()` → Returns real PerformanceCounter
7. `registry.start()` - Starts collecting metrics

## Changes

### KVStore.java
Modified [KVStore.java](file:///home/wisefox/git/hyperkvstore/lsmplus-kvstore/src/main/java/org/hyperkv/lsmplus/core/KVStore.java):
- Moved MetricsRegistry initialization from line 156 to line 125 (before creating Journal and BPlusTree)
- Removed duplicate initialization code
- Ensured all components get real PerformanceCounter instances

## Impact
Now all performance counters are properly registered and logged:
- `kvstore_put` - Put operation latency
- `kvstore_get` - Get operation latency
- `kvstore_delete` - Delete operation latency
- `kvstore_tree_search` - B+Tree search latency
- `kvstore_tree_range_query` - B+Tree range query latency
- `kvstore_journal_write` - Journal write latency
- `kvstore_journal_write_batch` - Journal batch write latency
- `kvstore_journal_rotate_chunk` - Journal rotate chunk latency

## Testing
- All existing tests pass
- Build successful
- Counters now properly registered and logged to performance-counter.log
