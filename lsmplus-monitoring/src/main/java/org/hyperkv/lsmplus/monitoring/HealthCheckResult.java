package org.hyperkv.lsmplus.monitoring;

import java.util.Map;

public class HealthCheckResult {

    private final String name;
    private final HealthStatus status;
    private final String message;
    private final Map<String, Object> details;

    public HealthCheckResult(String name, HealthStatus status, String message, Map<String, Object> details) {
        this.name = name;
        this.status = status;
        this.message = message;
        this.details = details != null ? details : Map.of();
    }

    public String getName() {
        return name;
    }

    public HealthStatus getStatus() {
        return status;
    }

    public String getMessage() {
        return message;
    }

    public Map<String, Object> getDetails() {
        return details;
    }

    public String toJson() {
        StringBuilder sb = new StringBuilder();
        sb.append("{");
        sb.append("\"name\":\"").append(name).append("\",");
        sb.append("\"status\":\"").append(status).append("\",");
        sb.append("\"message\":\"").append(message).append("\",");
        sb.append("\"details\":{");
        boolean first = true;
        for (Map.Entry<String, Object> entry : details.entrySet()) {
            if (!first) sb.append(",");
            sb.append("\"").append(entry.getKey()).append("\":");
            if (entry.getValue() instanceof String) {
                sb.append("\"").append(entry.getValue()).append("\"");
            } else {
                sb.append(entry.getValue());
            }
            first = false;
        }
        sb.append("}");
        sb.append("}");
        return sb.toString();
    }

    @Override
    public String toString() {
        return String.format("HealthCheckResult{name='%s', status=%s, message='%s'}", 
            name, status, message);
    }
}
