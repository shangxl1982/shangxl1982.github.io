# Performance Counter Implementation

**Date**: 2026-04-22

## Summary

Implemented performance counter system per design-monitor.md specification and integrated it into the KVStore critical put/get/delete paths with logging to performance-counter.log.

## Changes Made

### 1. New Monitoring Classes

#### PerformanceCounter ([PerformanceCounter.java](file:///home/wisefox/git/hyperkvstore/lsmplus-monitoring/src/main/java/org/hyperkv/lsmplus/monitoring/PerformanceCounter.java))
- Extends Metric class
- Tracks operation latency distribution using Histogram
- Records success/error counts
- Provides percentile calculations (P50, P75, P90, P99)
- Methods:
  - `recordSuccess(long latencyMicros)` - records successful operation
  - `recordError()` - records failed operation
  - `getSnapshot()` - returns CounterSnapshot with current metrics

#### CounterSnapshot ([CounterSnapshot.java](file:///home/wisefox/git/hyperkvstore/lsmplus-monitoring/src/main/java/org/hyperkv/lsmplus/monitoring/CounterSnapshot.java))
- Immutable snapshot of counter state
- Contains: name, timestamp, count, errorCount, mean, min, max, p50, p75, p90, p99
- Provides JSON serialization

#### MetricsSnapshot ([MetricsSnapshot.java](file:///home/wisefox/git/hyperkvstore/lsmplus-monitoring/src/main/java/org/hyperkv/lsmplus/monitoring/MetricsSnapshot.java))
- Complete snapshot of all metrics
- Contains: timestamp, counters, gauges, healthChecks
- Provides JSON and Prometheus format serialization

#### MetricsLogger ([MetricsLogger.java](file:///home/wisefox/git/hyperkvstore/lsmplus-monitoring/src/main/java/org/hyperkv/lsmplus/monitoring/MetricsLogger.java))
- Implements MetricsListener
- Writes performance metrics to performance-counter.log
- Format: timestamp | counter_name | count | errors | mean(μs) | min(μs) | max(μs) | p50(μs) | p75(μs) | p90(μs) | p99(μs)

#### MetricsListener ([MetricsListener.java](file:///home/wisefox/git/hyperkvstore/lsmplus-monitoring/src/main/java/org/hyperkv/lsmplus/monitoring/MetricsListener.java))
- Interface for receiving metrics updates
- Methods:
  - `onSnapshot(MetricsSnapshot snapshot)`
  - `onHealthCheckChanged(String name, HealthCheckResult result)`

#### HealthCheckResult ([HealthCheckResult.java](file:///home/wisefox/git/hyperkvstore/lsmplus-monitoring/src/main/java/org/hyperkv/lsmplus/monitoring/HealthCheckResult.java))
- Result of health check execution
- Contains: name, status, message, details
- Provides JSON serialization

### 2. Updated Classes

#### MetricsRegistry ([MetricsRegistry.java](file:///home/wisefox/git/hyperkvstore/lsmplus-monitoring/src/main/java/org/hyperkv/lsmplus/monitoring/MetricsRegistry.java))
Major enhancements:
- Added PerformanceCounter support
- Added snapshot collection (10-second intervals)
- Added history management (up to 360 snapshots = 1 hour)
- Added listener support for metrics updates
- Added scheduled metrics collection
- Methods:
  - `counter(String name, String description)` - returns PerformanceCounter
  - `gauge(String name, String description, Supplier<Long> supplier)` - registers gauge with supplier
  - `addListener(MetricsListener listener)` - registers listener
  - `start()` - starts scheduled collection
  - `stop()` - stops collection
  - `getSnapshot()` - returns current snapshot
  - `getHistory(long durationMillis)` - returns historical snapshots

#### Histogram ([Histogram.java](file:///home/wisefox/git/hyperkvstore/lsmplus-monitoring/src/main/java/org/hyperkv/lsmplus/monitoring/Histogram.java))
- Added `getBucketCount(int index)` method for percentile calculation

#### HealthStatus ([HealthStatus.java](file:///home/wisefox/git/hyperkvstore/lsmplus-monitoring/src/main/java/org/hyperkv/lsmplus/monitoring/HealthStatus.java))
- Added static constants UP and DOWN

### 3. KVStore Integration

#### KVStore ([KVStore.java](file:///home/wisefox/git/hyperkvstore/lsmplus-kvstore/src/main/java/org/hyperkv/lsmplus/core/KVStore.java))
Added performance monitoring:
- Fields:
  - `metricsRegistry` - MetricsRegistry instance
  - `metricsLogger` - MetricsLogger for performance-counter.log
  - `putCounter` - PerformanceCounter for put operations
  - `getCounter` - PerformanceCounter for get operations
  - `deleteCounter` - PerformanceCounter for delete operations

- Initialization in `start()`:
  - Creates MetricsRegistry with namespace "kvstore"
  - Creates MetricsLogger pointing to performance-counter.log
  - Registers counters for put/get/delete operations
  - Starts metrics collection

- Instrumentation in critical paths:
  - `put()` - records latency and success/error status
  - `get()` - records latency and success/error status
  - `delete()` - records latency and success/error status

- Cleanup in `shutdown()`:
  - Stops MetricsRegistry
  - Closes MetricsLogger

#### build.gradle.kts ([build.gradle.kts](file:///home/wisefox/git/hyperkvstore/lsmplus-kvstore/build.gradle.kts))
- Added dependency on lsmplus-monitoring module

### 4. Tests

#### PerformanceCounterTest ([PerformanceCounterTest.java](file:///home/wisefox/git/hyperkvstore/lsmplus-monitoring/src/test/java/org/hyperkv/lsmplus/monitoring/PerformanceCounterTest.java))
- Tests basic operations
- Tests MetricsRegistry with logger
- Tests snapshot creation
- Tests Prometheus format output

#### KVStorePerformanceCounterTest ([KVStorePerformanceCounterTest.java](file:///home/wisefox/git/hyperkvstore/lsmplus-kvstore/src/test/java/org/hyperkv/lsmplus/core/KVStorePerformanceCounterTest.java))
- Tests performance counters are recorded
- Tests log file creation

#### MetricsRegistryTest ([MetricsRegistryTest.java](file:///home/wisefox/git/hyperkvstore/lsmplus-monitoring/src/test/java/org/hyperkv/lsmplus/monitoring/MetricsRegistryTest.java))
- Updated to work with PerformanceCounter instead of Counter

## Implementation Details

### Performance Counter Design

1. **Latency Tracking**: Uses Histogram with predefined bucket boundaries (1μs to 5s)
2. **Percentile Calculation**: Calculates P50, P75, P90, P99 from histogram buckets
3. **Error Tracking**: Separate counter for errors
4. **Time Window**: 10-second collection window with automatic reset

### Metrics Collection Flow

```
KVStore Operation
    ↓
PerformanceCounter.recordSuccess/Error
    ↓
MetricsRegistry scheduled collection (every 10s)
    ↓
CounterSnapshot creation
    ↓
MetricsSnapshot creation
    ↓
MetricsLogger.onSnapshot
    ↓
performance-counter.log
```

### Log Format

```
timestamp | counter_name | count | errors | mean(μs) | min(μs) | max(μs) | p50(μs) | p75(μs) | p90(μs) | p99(μs)
```

Example:
```
2026-04-22 10:30:15.123 | kvstore_put                   |       10 |       0 |    125.50 |     100 |     200 |     120 |     150 |     180 |     200
```

## Benefits

1. **Performance Visibility**: Real-time tracking of operation latencies
2. **Percentile Metrics**: Understanding of latency distribution (P50-P99)
3. **Error Tracking**: Separate tracking of successful vs failed operations
4. **Historical Data**: 1 hour of historical metrics for analysis
5. **Multiple Formats**: JSON and Prometheus output formats
6. **Minimal Overhead**: Lock-free atomic operations for counters

## Future Enhancements

1. Add more counters for other critical operations (journal.write, tree.search, etc.)
2. Add gauge metrics (memory usage, disk usage, etc.)
3. Add health checks integration
4. Add REST API endpoint for metrics exposure
5. Add support for custom metrics collection intervals
6. Add metrics aggregation across multiple KVStore instances

## Testing

All tests pass:
- `./gradlew :lsmplus-monitoring:test` - All monitoring tests pass
- `./gradlew :lsmplus-kvstore:test --tests KVStorePerformanceCounterTest` - Integration tests pass
- `./gradlew build -x test` - Build successful

## Related Files

- Design: [design-monitoring.md](file:///home/wisefox/git/hyperkvstore/design/design-monitoring.md)
- Implementation: [lsmplus-monitoring/](file:///home/wisefox/git/hyperkvstore/lsmplus-monitoring/)
- Integration: [KVStore.java](file:///home/wisefox/git/hyperkvstore/lsmplus-kvstore/src/main/java/org/hyperkv/lsmplus/core/KVStore.java)
