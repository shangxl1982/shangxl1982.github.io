package org.hyperkv.lsmplus.monitoring;

import java.util.concurrent.atomic.AtomicLong;
import java.util.Arrays;

public class PerformanceCounter extends Metric {

    private final Histogram histogram;
    private final AtomicLong errorCount;
    private final AtomicLong min;
    private final AtomicLong max;
    private volatile long lastUpdateTime;

    private static final long[] BUCKET_BOUNDS = {
        1, 5, 10, 25, 50, 100, 250, 500, 1000, 2500, 5000, 10000,
        25000, 50000, 100000, 250000, 500000, 1000000, 5000000
    };

    protected PerformanceCounter(String name, String description) {
        super(name, description);
        this.histogram = new Histogram(name, description);
        this.errorCount = new AtomicLong(0);
        this.min = new AtomicLong(Long.MAX_VALUE);
        this.max = new AtomicLong(Long.MIN_VALUE);
        this.lastUpdateTime = System.currentTimeMillis();
    }

    /**
     * Returns the current time in nanoseconds for latency calculation.
     * Use this at the start of an operation.
     */
    public static long startTime() {
        return System.nanoTime();
    }

    /**
     * Calculates latency in microseconds from the given start time.
     * @param startTime the start time returned by startTime()
     * @return latency in microseconds
     */
    public static long calcLatency(long startTime) {
        return (System.nanoTime() - startTime) / 1000;
    }

    public void recordSuccess(long latencyMicros) {
        histogram.observe(latencyMicros);
        updateMinMax(latencyMicros);
        lastUpdateTime = System.currentTimeMillis();
    }

    public void recordError() {
        errorCount.incrementAndGet();
        lastUpdateTime = System.currentTimeMillis();
    }

    private void updateMinMax(long value) {
        long currentMin;
        do {
            currentMin = min.get();
            if (value >= currentMin) {
                break;
            }
        } while (!min.compareAndSet(currentMin, value));

        long currentMax;
        do {
            currentMax = max.get();
            if (value <= currentMax) {
                break;
            }
        } while (!max.compareAndSet(currentMax, value));
    }

    public CounterSnapshot getSnapshot() {
        long count = histogram.getCount();
        long sum = histogram.getSum();
        double mean = count > 0 ? (double) sum / count : 0.0;

        return new CounterSnapshot(
            name,
            System.currentTimeMillis(),
            count,
            errorCount.get(),
            mean,
            min.get() == Long.MAX_VALUE ? 0 : min.get(),
            max.get() == Long.MIN_VALUE ? 0 : max.get(),
            calculatePercentile(50),
            calculatePercentile(75),
            calculatePercentile(90),
            calculatePercentile(99)
        );
    }

    private long calculatePercentile(double percentile) {
        long count = histogram.getCount();
        if (count == 0) {
            return 0;
        }

        long targetCount = (long) Math.ceil(count * percentile / 100.0);
        long cumulative = 0;

        for (int i = 0; i < BUCKET_BOUNDS.length; i++) {
            long bucketCount = histogram.getBucketCount(i);
            cumulative += bucketCount;
            if (cumulative >= targetCount) {
                return BUCKET_BOUNDS[i];
            }
        }

        return BUCKET_BOUNDS[BUCKET_BOUNDS.length - 1];
    }

    @Override
    public void reset() {
        histogram.reset();
        errorCount.set(0);
        min.set(Long.MAX_VALUE);
        max.set(Long.MIN_VALUE);
    }

    @Override
    public String toPrometheusFormat() {
        CounterSnapshot snapshot = getSnapshot();
        StringBuilder sb = new StringBuilder();
        
        sb.append("# HELP ").append(name).append(" ").append(description).append("\n");
        sb.append("# TYPE ").append(name).append(" summary\n");
        
        sb.append(name).append("_count ").append(snapshot.getCount()).append("\n");
        sb.append(name).append("_sum ").append((long)(snapshot.getMean() * snapshot.getCount())).append("\n");
        sb.append(name).append("{quantile=\"0.5\"} ").append(snapshot.getP50()).append("\n");
        sb.append(name).append("{quantile=\"0.75\"} ").append(snapshot.getP75()).append("\n");
        sb.append(name).append("{quantile=\"0.9\"} ").append(snapshot.getP90()).append("\n");
        sb.append(name).append("{quantile=\"0.99\"} ").append(snapshot.getP99()).append("\n");
        
        sb.append(name).append("_errors ").append(snapshot.getErrorCount()).append("\n");
        
        return sb.toString();
    }
}
