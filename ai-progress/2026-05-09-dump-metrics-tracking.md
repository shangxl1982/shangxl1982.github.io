# Dump Metrics Tracking Implementation - 2026-05-09

## Summary

Successfully implemented comprehensive metrics tracking for the TreeDumper operations. The metrics provide detailed insights into each dump operation's performance and resource utilization, logged at the end of every dump.

## Problem Statement

The TreeDumper lacked visibility into its operations. Without metrics, it was difficult to:
- Understand dump performance characteristics
- Identify optimization opportunities
- Monitor resource utilization
- Debug performance issues
- Track page operations (splits, merges, decommissions)

## Solution

Implemented a `DumpMetrics` inner class that tracks comprehensive statistics for each dump operation, with automatic logging at the end of every dump.

## Implementation Details

### 1. DumpMetrics Class

**File:** `lsmplus-kvstore/src/main/java/org/hyperkv/lsmplus/bplustree/TreeDumper.java`

```java
private static class DumpMetrics {
    private long startTime;
    private long endTime;
    private int leafPagesCreated;
    private int indexPagesCreated;
    private int pagesSplit;
    private int pagesMerged;
    private int pagesSaved;
    private int pagesDecommissioned;
    private int entriesInserted;
    private int entriesDeleted;
    private int pagesFlushed;
    private int rootPageSaved;

    public void start() {
        this.startTime = System.currentTimeMillis();
        // Reset all counters
    }

    public void finish() {
        this.endTime = System.currentTimeMillis();
    }

    public String toLogMessage() {
        return String.format(
            "Dump completed in %d ms: leafPagesCreated=%d, indexPagesCreated=%d, " +
            "pagesSplit=%d, pagesMerged=%d, pagesSaved=%d, pagesDecommissioned=%d, " +
            "entriesInserted=%d, entriesDeleted=%d, pagesFlushed=%d, rootPageSaved=%d",
            getDurationMs(), leafPagesCreated, indexPagesCreated,
            pagesSplit, pagesMerged, pagesSaved, pagesDecommissioned,
            entriesInserted, entriesDeleted, pagesFlushed, rootPageSaved
        );
    }
}
```

### 2. Metrics Integration Points

#### Page Creation
```java
private Page createLeafPage(long pageId) {
    if (currentDumpMetrics != null) {
        currentDumpMetrics.incrementLeafPagesCreated();
    }
    // ... create page
}

private Page createIndexPage(long pageId) {
    if (currentDumpMetrics != null) {
        currentDumpMetrics.incrementIndexPagesCreated();
    }
    // ... create page
}
```

#### Page Operations
```java
// Page splits
private void handlePageSplit(...) {
    Page rightPage = targetPage.split(newPageId);
    if (currentDumpMetrics != null) {
        currentDumpMetrics.incrementPagesSplit();
    }
    // ... handle split
}

// Page merges
private void handlePageMerge(...) {
    leafPage.merge(rightSibling);
    if (currentDumpMetrics != null) {
        currentDumpMetrics.incrementPagesMerged();
    }
    // ... handle merge
}
```

#### Page Persistence
```java
private List<SegmentLocation> savePagesWithTracking(List<Page> pages) {
    // ... save pages
    if (currentDumpMetrics != null) {
        currentDumpMetrics.incrementPagesSaved(pages.size());
    }
    return locations;
}

private void saveRootPage(Page rootPage, int height) {
    // ... save root
    if (currentDumpMetrics != null) {
        currentDumpMetrics.incrementRootPageSaved();
    }
}

private void flushPagesWithOccupancyTracking(...) {
    // ... flush pages
    if (currentDumpMetrics != null) {
        currentDumpMetrics.incrementPagesFlushed(pages.size());
    }
}
```

#### Page Decommission
```java
private void trackPageDecommission(SegmentLocation location) {
    occupancyManager.recordPageDecommission(location.getChunkId(), location.getLength());
    if (currentDumpMetrics != null) {
        currentDumpMetrics.incrementPagesDecommissioned();
    }
}
```

#### Entry Operations
```java
// Insertions
private void insertIntoTree(...) {
    leafPage.put(key, value);
    if (currentDumpMetrics != null) {
        currentDumpMetrics.incrementEntriesInserted();
    }
    // ... propagate updates
}

// Deletions
private boolean deleteFromTree(...) {
    var removed = leafPage.delete(key);
    if (removed != null) {
        if (currentDumpMetrics != null) {
            currentDumpMetrics.incrementEntriesDeleted();
        }
        // ... handle deletion
    }
}

// Build new tree
private void buildNewTree(...) {
    currentLeaf.put(key, value);
    if (currentDumpMetrics != null) {
        currentDumpMetrics.incrementEntriesInserted();
    }
}
```

### 3. Dump Method Integration

```java
public TreeMetadataManager.TreeVersionInfo dump(...) {
    currentDumpMetrics = new DumpMetrics();
    currentDumpMetrics.start();

    // ... perform dump operations

    currentDumpMetrics.finish();
    log.info(currentDumpMetrics.toLogMessage());

    return versionInfo;
}
```

## Metrics Tracked

### Performance Metrics
- **Duration**: Total time for dump operation (milliseconds)

### Page Creation Metrics
- **leafPagesCreated**: Number of leaf pages created during dump
- **indexPagesCreated**: Number of index pages created during dump

### Page Operation Metrics
- **pagesSplit**: Number of page splits performed
- **pagesMerged**: Number of page merges performed
- **pagesSaved**: Number of pages saved to storage
- **pagesFlushed**: Number of pages flushed with occupancy tracking
- **rootPageSaved**: Number of root page saves (0 or 1)

### Resource Management Metrics
- **pagesDecommissioned**: Number of pages decommissioned (for GC)

### Data Operation Metrics
- **entriesInserted**: Number of entries inserted
- **entriesDeleted**: Number of entries deleted

## Example Log Output

```
2026-05-09 17:39:43.872 [Test worker] INFO  TreeDumper - Starting dump of 1000 sorted entries, replayPoint=null
2026-05-09 17:39:43.935 [Test worker] INFO  TreeDumper - Dump completed in 63 ms: leafPagesCreated=15, indexPagesCreated=2, pagesSplit=14, pagesMerged=0, pagesSaved=17, pagesDecommissioned=0, entriesInserted=1000, entriesDeleted=0, pagesFlushed=0, rootPageSaved=1
```

## Benefits

### 1. **Operational Visibility**
- Clear understanding of dump performance
- Resource utilization tracking
- Operation breakdown analysis

### 2. **Performance Monitoring**
- Identify slow dumps
- Track page operation patterns
- Monitor merge/split frequency

### 3. **Debugging Support**
- Understand dump behavior
- Identify performance bottlenecks
- Track resource leaks

### 4. **Capacity Planning**
- Understand page creation patterns
- Track storage growth
- Monitor GC effectiveness

## Use Cases

### 1. **Performance Analysis**
```
Dump completed in 150 ms: leafPagesCreated=50, pagesSplit=49, entriesInserted=5000
→ High split count indicates need for larger page size
```

### 2. **Resource Monitoring**
```
Dump completed in 80 ms: pagesDecommissioned=30, pagesSaved=35
→ Good space reclamation, 30 old pages replaced by 35 new ones
```

### 3. **Merge Analysis**
```
Dump completed in 120 ms: pagesMerged=10, entriesDeleted=500
→ Deletions triggered merges, maintaining space efficiency
```

### 4. **Index Overhead Tracking**
```
Dump completed in 90 ms: leafPagesCreated=20, indexPagesCreated=3
→ Low index overhead (3 index pages for 20 leaf pages)
```

## Testing

Created comprehensive test suite in `DumpMetricsTest.java`:
- ✓ testMetricsTrackingDuringDump: Basic metrics tracking
- ✓ testMetricsWithInsertsAndDeletes: Insert/delete operations
- ✓ testMetricsWithPageSplits: Page split tracking

All tests pass successfully:
- ✓ DumpMetricsTest (3 tests)
- ✓ All lsmplus-kvstore tests (378 tests)

## Future Enhancements

1. **Metrics Aggregation**: Aggregate metrics across multiple dumps
2. **Percentile Tracking**: Track P50, P95, P99 latencies
3. **Metrics Export**: Export to monitoring systems (Prometheus, etc.)
4. **Alerting**: Alert on abnormal metrics patterns
5. **Historical Analysis**: Store and analyze metrics history
6. **Detailed Breakdown**: Track time spent in different phases
7. **Memory Metrics**: Track memory usage during dumps
8. **I/O Metrics**: Track bytes read/written

## Design Decisions

### 1. **Per-Dump Metrics**
- **Why**: Each dump is independent
- **Benefit**: Clear per-operation statistics
- **Alternative**: Cumulative metrics (rejected - harder to interpret)

### 2. **Null-Safe Tracking**
- **Why**: Metrics may not be initialized in all contexts
- **Implementation**: Check `currentDumpMetrics != null` before tracking
- **Benefit**: Robust to different usage patterns

### 3. **Simple Counters**
- **Why**: Easy to understand and aggregate
- **Alternative**: Complex metrics with histograms (rejected - overkill for now)
- **Benefit**: Simple and effective

### 4. **Automatic Logging**
- **Why**: No manual intervention needed
- **Implementation**: Log at end of dump method
- **Benefit**: Consistent logging across all dumps

## Performance Impact

**Minimal Overhead:**
- Simple integer increments
- No synchronization needed (single-threaded dumps)
- Negligible CPU impact
- No memory allocation during tracking

**Benefits Outweigh Costs:**
- Valuable operational insights
- Debugging support
- Performance optimization opportunities

## Integration Points

The metrics tracking integrates seamlessly with:
- **Occupancy Tracking**: Decommission tracking for GC
- **Page Management**: Creation, split, merge operations
- **Tree Operations**: Insert, delete, update operations
- **Storage Layer**: Save, flush operations

## Conclusion

The metrics tracking implementation provides comprehensive visibility into TreeDumper operations with minimal overhead. The logged metrics enable performance monitoring, debugging, and capacity planning, making the system more observable and maintainable.
