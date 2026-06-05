package org.hyperkv.lsmplus.monitoring;

import java.util.Collections;
import java.util.Map;

public class MetricsSnapshot {

    private final long timestamp;
    private final Map<String, CounterSnapshot> counters;
    private final Map<String, ExtendedCounterSnapshot> extendedCounters;
    private final Map<String, Long> gauges;
    private final Map<String, HealthCheckResult> healthChecks;

    public MetricsSnapshot(long timestamp,
                          Map<String, CounterSnapshot> counters,
                          Map<String, Long> gauges,
                          Map<String, HealthCheckResult> healthChecks) {
        this(timestamp, counters, Collections.emptyMap(), gauges, healthChecks);
    }

    public MetricsSnapshot(long timestamp,
                          Map<String, CounterSnapshot> counters,
                          Map<String, ExtendedCounterSnapshot> extendedCounters,
                          Map<String, Long> gauges,
                          Map<String, HealthCheckResult> healthChecks) {
        this.timestamp = timestamp;
        this.counters = counters;
        this.extendedCounters = extendedCounters;
        this.gauges = gauges;
        this.healthChecks = healthChecks;
    }

    public long getTimestamp() {
        return timestamp;
    }

    public Map<String, CounterSnapshot> getCounters() {
        return counters;
    }

    public Map<String, ExtendedCounterSnapshot> getExtendedCounters() {
        return extendedCounters;
    }

    public Map<String, Long> getGauges() {
        return gauges;
    }

    public Map<String, HealthCheckResult> getHealthChecks() {
        return healthChecks;
    }

    public String toJson() {
        StringBuilder sb = new StringBuilder();
        sb.append("{");
        sb.append("\"timestamp\":").append(timestamp).append(",");

        sb.append("\"counters\":{");
        boolean first = true;
        for (Map.Entry<String, CounterSnapshot> entry : counters.entrySet()) {
            if (!first) sb.append(",");
            sb.append("\"").append(entry.getKey()).append("\":");
            sb.append(entry.getValue().toJson());
            first = false;
        }
        sb.append("},");

        sb.append("\"extendedCounters\":{");
        first = true;
        for (Map.Entry<String, ExtendedCounterSnapshot> entry : extendedCounters.entrySet()) {
            if (!first) sb.append(",");
            sb.append("\"").append(entry.getKey()).append("\":");
            sb.append(entry.getValue().toJson());
            first = false;
        }
        sb.append("},");

        sb.append("\"gauges\":{");
        first = true;
        for (Map.Entry<String, Long> entry : gauges.entrySet()) {
            if (!first) sb.append(",");
            sb.append("\"").append(entry.getKey()).append("\":").append(entry.getValue());
            first = false;
        }
        sb.append("},");

        sb.append("\"healthChecks\":{");
        first = true;
        for (Map.Entry<String, HealthCheckResult> entry : healthChecks.entrySet()) {
            if (!first) sb.append(",");
            sb.append("\"").append(entry.getKey()).append("\":");
            sb.append(entry.getValue().toJson());
            first = false;
        }
        sb.append("}");

        sb.append("}");
        return sb.toString();
    }

    public String toPrometheus() {
        StringBuilder sb = new StringBuilder();

        for (CounterSnapshot counter : counters.values()) {
            sb.append("# HELP ").append(counter.getName()).append(" Performance counter\n");
            sb.append("# TYPE ").append(counter.getName()).append(" summary\n");
            sb.append(counter.getName()).append("_count ").append(counter.getCount()).append("\n");
            sb.append(counter.getName()).append("_sum ").append((long)(counter.getMean() * counter.getCount())).append("\n");
            sb.append(counter.getName()).append("{quantile=\"0.5\"} ").append(counter.getP50()).append("\n");
            sb.append(counter.getName()).append("{quantile=\"0.75\"} ").append(counter.getP75()).append("\n");
            sb.append(counter.getName()).append("{quantile=\"0.9\"} ").append(counter.getP90()).append("\n");
            sb.append(counter.getName()).append("{quantile=\"0.99\"} ").append(counter.getP99()).append("\n");
            sb.append(counter.getName()).append("_errors ").append(counter.getErrorCount()).append("\n");
            sb.append("\n");
        }

        for (ExtendedCounterSnapshot extCounter : extendedCounters.values()) {
            sb.append("# HELP ").append(extCounter.getName()).append(" Extended performance counter\n");
            sb.append("# TYPE ").append(extCounter.getName()).append(" summary\n");
            sb.append(extCounter.getName()).append("_count ").append(extCounter.getCount()).append("\n");
            sb.append(extCounter.getName()).append("_sum ").append((long)(extCounter.getMean() * extCounter.getCount())).append("\n");
            sb.append(extCounter.getName()).append("_errors ").append(extCounter.getErrorCount()).append("\n");
            sb.append("\n");

            // Add field metrics
            for (Map.Entry<String, ExtendedPerformanceCounter.FieldSnapshot> field : extCounter.getFieldSnapshots().entrySet()) {
                String fieldName = extCounter.getName() + "_" + field.getKey();
                ExtendedPerformanceCounter.FieldSnapshot snapshot = field.getValue();
                sb.append("# HELP ").append(fieldName).append(" Field metric\n");
                sb.append("# TYPE ").append(fieldName).append(" summary\n");
                sb.append(fieldName).append("_count ").append(snapshot.getCount()).append("\n");
                sb.append(fieldName).append("_sum ").append(snapshot.getTotal()).append("\n");
                sb.append(fieldName).append("_avg ").append(snapshot.getAverage()).append("\n");
                sb.append(fieldName).append("_min ").append(snapshot.getMin()).append("\n");
                sb.append(fieldName).append("_max ").append(snapshot.getMax()).append("\n");
                sb.append("\n");
            }
        }

        for (Map.Entry<String, Long> entry : gauges.entrySet()) {
            sb.append("# HELP ").append(entry.getKey()).append(" Gauge metric\n");
            sb.append("# TYPE ").append(entry.getKey()).append(" gauge\n");
            sb.append(entry.getKey()).append(" ").append(entry.getValue()).append("\n");
            sb.append("\n");
        }

        return sb.toString();
    }
}
