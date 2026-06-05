package org.hyperkv.lsmplus.monitoring;

public class NoOpPerformanceCounter extends PerformanceCounter {

    private static final NoOpPerformanceCounter INSTANCE = new NoOpPerformanceCounter();

    private NoOpPerformanceCounter() {
        super("noop", "No-op counter");
    }

    public static NoOpPerformanceCounter getInstance() {
        return INSTANCE;
    }

    @Override
    public void recordSuccess(long latencyMicros) {
    }

    @Override
    public void recordError() {
    }

    @Override
    public CounterSnapshot getSnapshot() {
        return new CounterSnapshot("noop", System.currentTimeMillis(), 0, 0, 0.0, 0, 0, 0, 0, 0, 0);
    }

    @Override
    public void reset() {
    }

    @Override
    public String toPrometheusFormat() {
        return "";
    }
}
