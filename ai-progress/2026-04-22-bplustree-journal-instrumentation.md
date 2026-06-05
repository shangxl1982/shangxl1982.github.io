# Performance Counter Instrumentation for BPlusTree and Journal

**Date**: 2026-04-22

## Summary

Added performance counters to key operations in BPlusTree and Journal classes to track latency and error rates for critical operations.

## Changes Made

### 1. BPlusTree Instrumentation ([BPlusTree.java](file:///home/wisefox/git/hyperkvstore/lsmplus-kvstore/src/main/java/org/hyperkv/lsmplus/bplustree/BPlusTree.java))

#### Added Counters
- `tree_search` - B+Tree search operation latency
- `tree_range_query` - B+Tree range query operation latency

#### Instrumented Methods
1. **search(IndexKey key)**
   - Tracks latency of tree search operations
   - Records success/error status
   - Measures time from method entry to return

2. **rangeQuery(IndexKey start, IndexKey end)**
   - Tracks latency of range query operations
   - Records success/error status
   - Measures time from method entry to return

#### Implementation Details
```java
public IndexValue search(IndexKey key) {
    // ... validation ...
    
    long startTime = System.nanoTime();
    boolean success = false;
    try {
        IndexValue result = searchInTree(key, readerRoot.get(), height);
        success = true;
        return result;
    } finally {
        long latencyMicros = (System.nanoTime() - startTime) / 1000;
        if (success) {
            searchCounter.recordSuccess(latencyMicros);
        } else {
            searchCounter.recordError();
        }
    }
}
```

### 2. Journal Instrumentation ([Journal.java](file:///home/wisefox/git/hyperkvstore/lsmplus-kvstore/src/main/java/org/hyperkv/lsmplus/journal/Journal.java))

#### Added Counters
- `journal_write` - Journal write operation latency
- `journal_write_batch` - Journal batch write operation latency
- `journal_rotate_chunk` - Journal chunk rotation latency

#### Instrumented Methods
1. **write(OperationType type, IndexKey key, IndexValue value)**
   - Tracks latency of single write operations
   - Records success/error status
   - Measures time including entry creation and writeEntry()

2. **writeBatch(List<JournalEntry.KeyValuePair> operations)**
   - Tracks latency of batch write operations
   - Records success/error status
   - Measures time including batch creation and writeEntry()

3. **rotateChunk()**
   - Tracks latency of chunk rotation operations
   - Records success/error status
   - Measures time including chunk sealing, allocation, and index persistence

#### Implementation Details
```java
public synchronized JournalReplayPoint write(OperationType type, IndexKey key, IndexValue value) throws IOException {
    long startTime = System.nanoTime();
    boolean success = false;
    try {
        JournalEntry entry;
        // ... create entry ...
        JournalReplayPoint result = writeEntry(entry);
        success = true;
        return result;
    } finally {
        long latencyMicros = (System.nanoTime() - startTime) / 1000;
        if (success) {
            writeCounter.recordSuccess(latencyMicros);
        } else {
            writeCounter.recordError();
        }
    }
}
```

## Performance Counter Names

All counters follow a consistent naming pattern:
- **BPlusTree**: `tree_<operation>` (e.g., `tree_search`, `tree_range_query`)
- **Journal**: `journal_<operation>` (e.g., `journal_write`, `journal_write_batch`, `journal_rotate_chunk`)

## Metrics Collected

For each operation, the following metrics are tracked:
- **Count**: Total number of operations
- **Error Count**: Number of failed operations
- **Mean Latency**: Average latency in microseconds
- **Min/Max Latency**: Minimum and maximum latency values
- **Percentiles**: P50, P75, P90, P99 latency values
- **Histogram**: Distribution of latency values

## Usage Example

After starting the KVStore, the performance counters will automatically collect metrics:

```java
// Initialize metrics registry
MetricsRegistry.initialize("hyperkv");

// Use BPlusTree
IndexValue value = tree.search(key);  // Automatically tracked

// Use Journal
journal.write(OperationType.PUT, key, value);  // Automatically tracked

// Metrics are logged to performance-counter.log every 10 seconds
```

## Testing

All existing tests pass successfully:
- `./gradlew clean build -x test` - Build successful
- `./gradlew :lsmplus-kvstore:test` - All tests pass

## Benefits

1. **Visibility**: Track performance of critical operations in real-time
2. **Error Tracking**: Monitor error rates for each operation
3. **Performance Analysis**: Identify slow operations and bottlenecks
4. **Trend Analysis**: Track performance over time
5. **Alerting**: Set up alerts for high latency or error rates

## Integration with KVStore

The instrumentation integrates seamlessly with the existing KVStore:
- Uses the singleton MetricsRegistry for easy access
- No changes required to existing KVStore code
- Counters are automatically initialized when BPlusTree or Journal is created
- Metrics are collected and logged automatically

## Future Enhancements

1. Add counters for PageManager operations (page reads/writes)
2. Add counters for MemoryTable operations
3. Add counters for TreeDumper operations
4. Add counters for GC operations
5. Add histograms with custom buckets for different operation types
6. Add support for distributed tracing

## Related Files

- Implementation: [BPlusTree.java](file:///home/wisefox/git/hyperkvstore/lsmplus-kvstore/src/main/java/org/hyperkv/lsmplus/bplustree/BPlusTree.java)
- Implementation: [Journal.java](file:///home/wisefox/git/hyperkvstore/lsmplus-kvstore/src/main/java/org/hyperkv/lsmplus/journal/Journal.java)
- Metrics Registry: [MetricsRegistry.java](file:///home/wisefox/git/hyperkvstore/lsmplus-monitoring/src/main/java/org/hyperkv/lsmplus/monitoring/MetricsRegistry.java)
- Performance Counter: [PerformanceCounter.java](file:///home/wisefox/git/hyperkvstore/lsmplus-monitoring/src/main/java/org/hyperkv/lsmplus/monitoring/PerformanceCounter.java)
