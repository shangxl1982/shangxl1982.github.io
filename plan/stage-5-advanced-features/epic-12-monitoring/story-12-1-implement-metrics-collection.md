# Story 12-1: Implement Metrics Collection

## Story

As a developer, I want to implement Metrics Collection so that system performance can be tracked with detailed latency distributions and throughput metrics.

## Acceptance Criteria

- [ ] MetricsRegistry class created with counter, gauge, and health check management
- [ ] PerformanceCounter class implemented with percentiles (P50, P75, P90, P99)
- [ ] All KVStore operations tracked (put, get, delete, batch)
- [ ] System metrics tracked (memory, disk, Chunk, MemoryTable, Tree, Cache)
- [ ] Historical data logging to JSON Lines format implemented
- [ ] Unit tests verify all methods

## Technical Details

### MetricsRegistry Class

```java
public class MetricsRegistry {
    private final Map<String, PerformanceCounter> counters;
    private final Map<String, Gauge> gauges;
    private final Map<String, HealthCheck> healthChecks;
    private final List<MetricsListener> listeners;
    private final long snapshotInterval;
    private final List<MetricsSnapshot> history;
    
    public PerformanceCounter counter(String name);
    public void gauge(String name, Supplier<Long> supplier);
    public void healthCheck(String name, HealthCheck check);
    public void addListener(MetricsListener listener);
    public MetricsSnapshot getSnapshot();
    public void start();
    public void stop();
}
```

### PerformanceCounter Metrics

```
kvstore.put: Put operation latency and throughput
kvstore.get: Get operation latency and throughput  
kvstore.delete: Delete operation latency and throughput
kvstore.batch: Batch operation latency and throughput
journal.write: Journal write latency
memorytable.seal: MemoryTable seal latency
tree.dump: Tree dump latency
gc.full: Full GC latency
gc.partial: Partial GC latency
```

### Gauge Metrics

```
memory.used: Used memory
memory.free: Free memory
memory.max: Max memory
disk.used: Disk used space
disk.free: Disk free space
chunk.total: Total Chunks
chunk.open: Open Chunks
chunk.sealed: Sealed Chunks
memorytable.active.size: Active MemoryTable size
memorytable.sealed.count: Sealed MemoryTable count
tree.version: Current Tree version
cache.hit.rate: Cache hit rate
```

## Testing

- testMetricsRegistryCreation()
- testPerformanceCounterRecording()
- testGaugeRegistration()
- testHealthCheckExecution()
- testMetricsSnapshotGeneration()
- testHistoricalDataLogging()

## Effort Estimate

2 days
