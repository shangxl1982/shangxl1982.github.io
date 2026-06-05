package org.hyperkv.lsmplus.monitoring;

import java.util.concurrent.atomic.AtomicLong;

public class Gauge extends Metric {

    private final AtomicLong value;

    protected Gauge(String name, String description) {
        super(name, description);
        this.value = new AtomicLong(0);
    }

    public void set(long value) {
        this.value.set(value);
    }

    public void increment() {
        value.incrementAndGet();
    }

    public void decrement() {
        value.decrementAndGet();
    }

    public long getValue() {
        return value.get();
    }

    @Override
    public void reset() {
        value.set(0);
    }

    @Override
    public String toPrometheusFormat() {
        StringBuilder sb = new StringBuilder();
        sb.append("# HELP ").append(name).append(" ").append(description).append("\n");
        sb.append("# TYPE ").append(name).append(" gauge\n");
        sb.append(name).append(" ").append(value.get()).append("\n");
        return sb.toString();
    }
}
