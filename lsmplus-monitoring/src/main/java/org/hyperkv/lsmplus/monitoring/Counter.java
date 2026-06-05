package org.hyperkv.lsmplus.monitoring;

import java.util.concurrent.atomic.AtomicLong;

public class Counter extends Metric {

    private final AtomicLong value;

    protected Counter(String name, String description) {
        super(name, description);
        this.value = new AtomicLong(0);
    }

    public void increment() {
        value.incrementAndGet();
    }

    public void increment(long delta) {
        value.addAndGet(delta);
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
        sb.append("# TYPE ").append(name).append(" counter\n");
        sb.append(name).append(" ").append(value.get()).append("\n");
        return sb.toString();
    }
}
