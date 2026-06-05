package org.hyperkv.lsmplus.monitoring;

import java.util.concurrent.atomic.AtomicLong;

public class Histogram extends Metric {

    private final AtomicLong count;
    private final AtomicLong sum;
    private final long[] buckets;
    private final AtomicLong[] bucketCounts;

    protected Histogram(String name, String description) {
        super(name, description);
        this.count = new AtomicLong(0);
        this.sum = new AtomicLong(0);
        this.buckets = new long[]{1, 5, 10, 25, 50, 100, 250, 500, 1000, 2500, 5000, 10000};
        this.bucketCounts = new AtomicLong[buckets.length];
        for (int i = 0; i < bucketCounts.length; i++) {
            bucketCounts[i] = new AtomicLong(0);
        }
    }

    public void observe(long value) {
        count.incrementAndGet();
        sum.addAndGet(value);

        for (int i = 0; i < buckets.length; i++) {
            if (value <= buckets[i]) {
                bucketCounts[i].incrementAndGet();
            }
        }
    }

    public long getCount() {
        return count.get();
    }

    public long getSum() {
        return sum.get();
    }

    public double getAverage() {
        long c = count.get();
        return c > 0 ? (double) sum.get() / c : 0.0;
    }

    public long getBucketCount(int index) {
        if (index < 0 || index >= bucketCounts.length) {
            return 0;
        }
        return bucketCounts[index].get();
    }

    @Override
    public void reset() {
        count.set(0);
        sum.set(0);
        for (AtomicLong bucketCount : bucketCounts) {
            bucketCount.set(0);
        }
    }

    @Override
    public String toPrometheusFormat() {
        StringBuilder sb = new StringBuilder();
        sb.append("# HELP ").append(name).append(" ").append(description).append("\n");
        sb.append("# TYPE ").append(name).append(" histogram\n");

        for (int i = 0; i < buckets.length; i++) {
            sb.append(name).append("_bucket{le=\"").append(buckets[i]).append("\"} ")
              .append(bucketCounts[i].get()).append("\n");
        }
        sb.append(name).append("_bucket{le=\"+Inf\"} ").append(count.get()).append("\n");
        sb.append(name).append("_sum ").append(sum.get()).append("\n");
        sb.append(name).append("_count ").append(count.get()).append("\n");

        return sb.toString();
    }
}
