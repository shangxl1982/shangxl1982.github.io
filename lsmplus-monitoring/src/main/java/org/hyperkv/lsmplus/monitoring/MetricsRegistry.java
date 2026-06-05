package org.hyperkv.lsmplus.monitoring;

import java.util.*;
import java.util.concurrent.*;
import java.util.function.Supplier;

public class MetricsRegistry {

    private static volatile MetricsRegistry instance;
    private static final Object lock = new Object();

    private final Map<String, PerformanceCounter> counters;
    private final Map<String, Gauge> gauges;
    private final Map<String, Histogram> histograms;
    private final Map<String, HealthCheck> healthChecks;
    private final Map<String, Supplier<Long>> gaugeSuppliers;
    private final List<MetricsListener> listeners;
    private final List<MetricsSnapshot> history;
    private final int maxHistorySize;
    private final long snapshotInterval;
    private final ScheduledExecutorService scheduler;
    private final String namespace;
    private volatile boolean running;

    private MetricsRegistry(String namespace, long snapshotInterval, int maxHistorySize) {
        this.namespace = namespace;
        this.snapshotInterval = snapshotInterval;
        this.maxHistorySize = maxHistorySize;
        this.counters = new ConcurrentHashMap<>();
        this.gauges = new ConcurrentHashMap<>();
        this.histograms = new ConcurrentHashMap<>();
        this.healthChecks = new ConcurrentHashMap<>();
        this.gaugeSuppliers = new ConcurrentHashMap<>();
        this.listeners = new CopyOnWriteArrayList<>();
        this.history = new CopyOnWriteArrayList<>();
        this.scheduler = Executors.newSingleThreadScheduledExecutor(r -> {
            Thread t = new Thread(r, "metrics-registry-scheduler");
            t.setDaemon(true);
            return t;
        });
        this.running = false;
    }

    public static MetricsRegistry getInstance() {
        return instance;
    }

    public static void initialize(String namespace, long snapshotInterval, int maxHistorySize) {
        synchronized (lock) {
            if (instance != null) {
                return;
            }
            instance = new MetricsRegistry(namespace, snapshotInterval, maxHistorySize);
        }
    }

    public static void initialize(String namespace) {
        initialize(namespace, 2000, 360);
    }

    public static void shutdown() {
        synchronized (lock) {
            if (instance != null) {
                instance.stop();
                instance = null;
            }
        }
    }

    public static PerformanceCounter getCounter(String name, String description) {
        MetricsRegistry registry = getInstance();
        if (registry == null) {
            return NoOpPerformanceCounter.getInstance();
        }
        return registry.counter(name, description);
    }

    public static PerformanceCounter getCounter(String name) {
        return getCounter(name, "");
    }

    public PerformanceCounter counter(String name, String description) {
        return counters.computeIfAbsent(name,
            n -> new PerformanceCounter(namespace + "_" + n, description));
    }

    /**
     * Gets or creates an extended performance counter with support for additional fields.
     * 
     * <p>Field names are declared at creation time and values are recorded positionally.
     * 
     * <p>Example:
     * <pre>
     * ExtendedPerformanceCounter putCounter = MetricsRegistry.getExtendedCounter(
     *     "put", "PUT operations", "putSize", "keySize", "valueSize");
     * putCounter.recordSuccess(latencyMicros, putSize, keySize, valueSize);
     * </pre>
     * 
     * @param name counter name
     * @param description counter description
     * @param fieldNames names of additional fields to track
     * @return ExtendedPerformanceCounter instance
     */
    public static ExtendedPerformanceCounter getExtendedCounter(String name, String description, String... fieldNames) {
        MetricsRegistry registry = getInstance();
        if (registry == null) {
            return new NoOpExtendedPerformanceCounter(name, description, fieldNames);
        }
        return registry.extendedCounter(name, description, fieldNames);
    }

    public static ExtendedPerformanceCounter getExtendedCounter(String name) {
        return getExtendedCounter(name, "", new String[0]);
    }

    public ExtendedPerformanceCounter extendedCounter(String name, String description, String... fieldNames) {
        PerformanceCounter existing = counters.get(name);
        if (existing instanceof ExtendedPerformanceCounter extCounter) {
            return extCounter;
        }
        return (ExtendedPerformanceCounter) counters.computeIfAbsent(name,
            n -> new ExtendedPerformanceCounter(namespace + "_" + n, description, fieldNames));
    }

    public void gauge(String name, String description, Supplier<Long> supplier) {
        gauges.put(name, new Gauge(namespace + "_" + name, description));
        gaugeSuppliers.put(name, supplier);
    }

    public Gauge gauge(String name, String description) {
        return gauges.computeIfAbsent(name,
            n -> new Gauge(namespace + "_" + n, description));
    }

    public Histogram histogram(String name, String description) {
        return histograms.computeIfAbsent(name,
            n -> new Histogram(namespace + "_" + n, description));
    }

    public Metric getMetric(String name) {
        Metric metric = counters.get(name);
        if (metric != null) return metric;
        
        metric = gauges.get(name);
        if (metric != null) return metric;
        
        metric = histograms.get(name);
        if (metric != null) return metric;
        
        return null;
    }

    public void healthCheck(String name, HealthCheck check) {
        healthChecks.put(name, check);
    }

    public void addListener(MetricsListener listener) {
        listeners.add(listener);
    }

    public void removeListener(MetricsListener listener) {
        listeners.remove(listener);
    }

    public void start() {
        if (running) {
            return;
        }
        running = true;
        scheduler.scheduleAtFixedRate(
            this::collectAndSnapshot,
            snapshotInterval,
            snapshotInterval,
            TimeUnit.MILLISECONDS
        );
    }

    public void stop() {
        if (!running) {
            return;
        }
        running = false;
        scheduler.shutdown();
        try {
            if (!scheduler.awaitTermination(5, TimeUnit.SECONDS)) {
                scheduler.shutdownNow();
            }
        } catch (InterruptedException e) {
            scheduler.shutdownNow();
            Thread.currentThread().interrupt();
        }
    }

    public void collectAndSnapshot() {
        try {
            Map<String, CounterSnapshot> counterSnapshots = new HashMap<>();
            Map<String, ExtendedCounterSnapshot> extendedCounterSnapshots = new HashMap<>();
            
            for (Map.Entry<String, PerformanceCounter> entry : counters.entrySet()) {
                PerformanceCounter counter = entry.getValue();
                CounterSnapshot snapshot = counter.getSnapshot();
                
                // Check if it's an extended counter
                if (counter instanceof ExtendedPerformanceCounter extCounter) {
                    ExtendedCounterSnapshot extSnapshot = new ExtendedCounterSnapshot(
                        snapshot.getName(),
                        snapshot.getTimestamp(),
                        snapshot.getCount(),
                        snapshot.getErrorCount(),
                        snapshot.getMean(),
                        snapshot.getMin(),
                        snapshot.getMax(),
                        snapshot.getP50(),
                        snapshot.getP75(),
                        snapshot.getP90(),
                        snapshot.getP99(),
                        extCounter.getAllFieldSnapshots()
                    );
                    extendedCounterSnapshots.put(entry.getKey(), extSnapshot);
                } else {
                    counterSnapshots.put(entry.getKey(), snapshot);
                }
                
                entry.getValue().reset();
            }

            Map<String, Long> gaugeValues = new HashMap<>();
            for (Map.Entry<String, Supplier<Long>> entry : gaugeSuppliers.entrySet()) {
                try {
                    Long value = entry.getValue().get();
                    if (value != null) {
                        gaugeValues.put(entry.getKey(), value);
                    }
                } catch (Exception e) {
                    gaugeValues.put(entry.getKey(), -1L);
                }
            }

            Map<String, HealthCheckResult> healthResults = new HashMap<>();
            for (Map.Entry<String, HealthCheck> entry : healthChecks.entrySet()) {
                try {
                    HealthStatus status = entry.getValue().check();
                    healthResults.put(entry.getKey(), new HealthCheckResult(
                        entry.getKey(),
                        status.isHealthy() ? HealthStatus.UP : HealthStatus.DOWN,
                        status.isHealthy() ? "Health check passed" : "Health check failed",
                        status.getDetails()
                    ));
                } catch (Exception e) {
                    healthResults.put(entry.getKey(), new HealthCheckResult(
                        entry.getKey(),
                        HealthStatus.DOWN,
                        "Health check error: " + e.getMessage(),
                        Map.of("error", e.getMessage())
                    ));
                }
            }

            MetricsSnapshot snapshot = new MetricsSnapshot(
                System.currentTimeMillis(),
                counterSnapshots,
                extendedCounterSnapshots,
                gaugeValues,
                healthResults
            );

            history.add(snapshot);
            if (history.size() > maxHistorySize) {
                history.remove(0);
            }

            for (MetricsListener listener : listeners) {
                try {
                    listener.onSnapshot(snapshot);
                } catch (Exception e) {
                    System.err.println("Error notifying listener: " + e.getMessage());
                }
            }

        } catch (Exception e) {
            System.err.println("Error collecting metrics snapshot: " + e.getMessage());
            e.printStackTrace();
        }
    }

    public MetricsSnapshot getSnapshot() {
        if (history.isEmpty()) {
            collectAndSnapshot();
        }
        return history.isEmpty() ? null : history.get(history.size() - 1);
    }

    public List<MetricsSnapshot> getHistory(long durationMillis) {
        long cutoff = System.currentTimeMillis() - durationMillis;
        List<MetricsSnapshot> result = new ArrayList<>();
        for (MetricsSnapshot snapshot : history) {
            if (snapshot.getTimestamp() >= cutoff) {
                result.add(snapshot);
            }
        }
        return result;
    }

    public CounterSnapshot getCounterSnapshot(String name) {
        PerformanceCounter counter = counters.get(name);
        return counter != null ? counter.getSnapshot() : null;
    }

    public Map<String, Long> getGauges() {
        Map<String, Long> result = new HashMap<>();
        for (Map.Entry<String, Supplier<Long>> entry : gaugeSuppliers.entrySet()) {
            try {
                Long value = entry.getValue().get();
                if (value != null) {
                    result.put(entry.getKey(), value);
                }
            } catch (Exception e) {
                result.put(entry.getKey(), -1L);
            }
        }
        return result;
    }

    public Map<String, HealthCheckResult> checkHealth() {
        Map<String, HealthCheckResult> results = new HashMap<>();
        for (Map.Entry<String, HealthCheck> entry : healthChecks.entrySet()) {
            try {
                HealthStatus status = entry.getValue().check();
                results.put(entry.getKey(), new HealthCheckResult(
                    entry.getKey(),
                    status.isHealthy() ? HealthStatus.UP : HealthStatus.DOWN,
                    status.isHealthy() ? "Health check passed" : "Health check failed",
                    status.getDetails()
                ));
            } catch (Exception e) {
                results.put(entry.getKey(), new HealthCheckResult(
                    entry.getKey(),
                    HealthStatus.DOWN,
                    "Health check error: " + e.getMessage(),
                    Map.of("error", e.getMessage())
                ));
            }
        }
        return results;
    }

    public HealthCheckResult checkHealth(String name) {
        HealthCheck check = healthChecks.get(name);
        if (check == null) {
            return new HealthCheckResult(
                name,
                HealthStatus.DOWN,
                "Health check not found",
                Map.of()
            );
        }
        try {
            HealthStatus status = check.check();
            return new HealthCheckResult(
                name,
                status.isHealthy() ? HealthStatus.UP : HealthStatus.DOWN,
                status.isHealthy() ? "Health check passed" : "Health check failed",
                status.getDetails()
            );
        } catch (Exception e) {
            return new HealthCheckResult(
                name,
                HealthStatus.DOWN,
                "Health check error: " + e.getMessage(),
                Map.of("error", e.getMessage())
            );
        }
    }

    public void reset() {
        for (PerformanceCounter counter : counters.values()) {
            counter.reset();
        }
        for (Gauge gauge : gauges.values()) {
            gauge.reset();
        }
        for (Histogram histogram : histograms.values()) {
            histogram.reset();
        }
        history.clear();
    }

    public String exportPrometheus() {
        MetricsSnapshot snapshot = getSnapshot();
        return snapshot != null ? snapshot.toPrometheus() : "";
    }
}
