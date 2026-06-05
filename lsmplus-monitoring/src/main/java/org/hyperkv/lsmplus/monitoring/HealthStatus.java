package org.hyperkv.lsmplus.monitoring;

import java.util.Map;

public class HealthStatus {

    public static final HealthStatus UP = new HealthStatus("UP", Map.of());
    public static final HealthStatus DOWN = new HealthStatus("DOWN", Map.of());

    private final String status;
    private final Map<String, Object> details;

    public HealthStatus(String status, Map<String, Object> details) {
        this.status = status;
        this.details = details != null ? details : Map.of();
    }

    public String getStatus() {
        return status;
    }

    public Map<String, Object> getDetails() {
        return details;
    }

    public boolean isHealthy() {
        return "HEALTHY".equals(status);
    }

    @Override
    public String toString() {
        return "HealthStatus{status='" + status + "', details=" + details + "}";
    }
}
