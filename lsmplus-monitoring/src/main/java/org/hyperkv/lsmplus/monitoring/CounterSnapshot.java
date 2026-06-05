package org.hyperkv.lsmplus.monitoring;

public class CounterSnapshot {

    private final String name;
    private final long timestamp;
    private final long count;
    private final long errorCount;
    private final double mean;
    private final long min;
    private final long max;
    private final long p50;
    private final long p75;
    private final long p90;
    private final long p99;

    public CounterSnapshot(String name, long timestamp, long count, long errorCount,
                          double mean, long min, long max,
                          long p50, long p75, long p90, long p99) {
        this.name = name;
        this.timestamp = timestamp;
        this.count = count;
        this.errorCount = errorCount;
        this.mean = mean;
        this.min = min;
        this.max = max;
        this.p50 = p50;
        this.p75 = p75;
        this.p90 = p90;
        this.p99 = p99;
    }

    public String getName() {
        return name;
    }

    public long getTimestamp() {
        return timestamp;
    }

    public long getCount() {
        return count;
    }

    public long getErrorCount() {
        return errorCount;
    }

    public double getMean() {
        return mean;
    }

    public long getMin() {
        return min;
    }

    public long getMax() {
        return max;
    }

    public long getP50() {
        return p50;
    }

    public long getP75() {
        return p75;
    }

    public long getP90() {
        return p90;
    }

    public long getP99() {
        return p99;
    }

    public String toJson() {
        return String.format(
            "{\"name\":\"%s\",\"timestamp\":%d,\"count\":%d,\"errorCount\":%d," +
            "\"mean\":%.2f,\"min\":%d,\"max\":%d,\"p50\":%d,\"p75\":%d,\"p90\":%d,\"p99\":%d}",
            name, timestamp, count, errorCount, mean, min, max, p50, p75, p90, p99
        );
    }

    @Override
    public String toString() {
        return String.format(
            "CounterSnapshot{name='%s', count=%d, errors=%d, mean=%.2fμs, min=%dμs, max=%dμs, " +
            "p50=%dμs, p75=%dμs, p90=%dμs, p99=%dμs}",
            name, count, errorCount, mean, min, max, p50, p75, p90, p99
        );
    }
}
