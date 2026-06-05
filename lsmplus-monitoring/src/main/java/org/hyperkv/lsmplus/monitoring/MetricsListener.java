package org.hyperkv.lsmplus.monitoring;

public interface MetricsListener {
    
    void onSnapshot(MetricsSnapshot snapshot);
    
    void onHealthCheckChanged(String name, HealthCheckResult result);
}
