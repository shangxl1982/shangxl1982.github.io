package org.hyperkv.lsmplus.monitoring;

import java.util.Collections;
import java.util.Map;

/**
 * Snapshot for extended performance counter that includes additional field statistics.
 */
public class ExtendedCounterSnapshot extends CounterSnapshot {

    private final Map<String, ExtendedPerformanceCounter.FieldSnapshot> fieldSnapshots;

    public ExtendedCounterSnapshot(String name, long timestamp, long count, long errorCount,
                                   double mean, long min, long max,
                                   long p50, long p75, long p90, long p99,
                                   Map<String, ExtendedPerformanceCounter.FieldSnapshot> fieldSnapshots) {
        super(name, timestamp, count, errorCount, mean, min, max, p50, p75, p90, p99);
        this.fieldSnapshots = fieldSnapshots != null ? fieldSnapshots : Collections.emptyMap();
    }

    public Map<String, ExtendedPerformanceCounter.FieldSnapshot> getFieldSnapshots() {
        return fieldSnapshots;
    }

    @Override
    public String toJson() {
        StringBuilder sb = new StringBuilder();
        sb.append("{");
        sb.append("\"name\":\"").append(getName()).append("\",");
        sb.append("\"timestamp\":").append(getTimestamp()).append(",");
        sb.append("\"count\":").append(getCount()).append(",");
        sb.append("\"errorCount\":").append(getErrorCount()).append(",");
        sb.append("\"mean\":").append(String.format("%.2f", getMean())).append(",");
        sb.append("\"min\":").append(getMin()).append(",");
        sb.append("\"max\":").append(getMax()).append(",");
        sb.append("\"p50\":").append(getP50()).append(",");
        sb.append("\"p75\":").append(getP75()).append(",");
        sb.append("\"p90\":").append(getP90()).append(",");
        sb.append("\"p99\":").append(getP99()).append(",");
        sb.append("\"fields\":{");
        boolean first = true;
        for (Map.Entry<String, ExtendedPerformanceCounter.FieldSnapshot> entry : fieldSnapshots.entrySet()) {
            if (!first) sb.append(",");
            ExtendedPerformanceCounter.FieldSnapshot field = entry.getValue();
            sb.append("\"").append(entry.getKey()).append("\":{");
            sb.append("\"count\":").append(field.getCount()).append(",");
            sb.append("\"average\":").append(String.format("%.2f", field.getAverage())).append(",");
            sb.append("\"min\":").append(field.getMin()).append(",");
            sb.append("\"max\":").append(field.getMax()).append(",");
            sb.append("\"total\":").append(field.getTotal());
            sb.append("}");
            first = false;
        }
        sb.append("}}");
        return sb.toString();
    }

    @Override
    public String toString() {
        StringBuilder sb = new StringBuilder(super.toString());
        sb.deleteCharAt(sb.length() - 1); // Remove trailing }
        sb.append(", fields={");
        for (Map.Entry<String, ExtendedPerformanceCounter.FieldSnapshot> entry : fieldSnapshots.entrySet()) {
            sb.append(entry.getKey()).append("=").append(entry.getValue()).append(", ");
        }
        sb.append("}}");
        return sb.toString();
    }
}
