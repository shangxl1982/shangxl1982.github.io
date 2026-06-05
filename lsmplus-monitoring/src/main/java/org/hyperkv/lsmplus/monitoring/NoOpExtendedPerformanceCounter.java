package org.hyperkv.lsmplus.monitoring;

import java.util.Collections;
import java.util.List;
import java.util.Map;

/**
 * No-op implementation of ExtendedPerformanceCounter used when MetricsRegistry is not initialized.
 */
public class NoOpExtendedPerformanceCounter extends ExtendedPerformanceCounter {

    public NoOpExtendedPerformanceCounter(String name, String description, String... fieldNames) {
        super(name, description, fieldNames);
    }

    @Override
    public void recordSuccess(long latencyMicros) {
        // No-op
    }

    @Override
    public void recordSuccess(long latencyMicros, long... fieldValues) {
        // No-op
    }

    @Override
    public FieldSnapshot getFieldSnapshot(String fieldName) {
        return null;
    }

    @Override
    public FieldSnapshot getFieldSnapshot(int index) {
        return null;
    }

    @Override
    public Map<String, FieldSnapshot> getAllFieldSnapshots() {
        return Collections.emptyMap();
    }

    @Override
    public List<FieldSnapshot> getFieldSnapshots() {
        return Collections.emptyList();
    }

    @Override
    public void recordError() {
        // No-op
    }

    @Override
    public void reset() {
        // No-op
    }
}
