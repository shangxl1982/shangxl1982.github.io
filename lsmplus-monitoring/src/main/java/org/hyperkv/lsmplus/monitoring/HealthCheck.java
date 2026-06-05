package org.hyperkv.lsmplus.monitoring;

import java.io.File;
import java.lang.management.ManagementFactory;
import java.lang.management.MemoryMXBean;
import java.lang.management.OperatingSystemMXBean;
import java.util.HashMap;
import java.util.Map;

public class HealthCheck {

    private final File dataDir;
    private final long diskSpaceThreshold;
    private final long memoryThreshold;

    public HealthCheck(File dataDir, long diskSpaceThreshold, long memoryThreshold) {
        this.dataDir = dataDir;
        this.diskSpaceThreshold = diskSpaceThreshold;
        this.memoryThreshold = memoryThreshold;
    }

    public HealthCheck(File dataDir) {
        this(dataDir, 1024 * 1024 * 1024, 100 * 1024 * 1024);
    }

    public HealthStatus check() {
        Map<String, Object> details = new HashMap<>();
        boolean healthy = true;

        HealthStatus diskStatus = checkDiskSpace();
        details.put("diskSpace", diskStatus);
        if (!diskStatus.isHealthy()) {
            healthy = false;
        }

        HealthStatus memoryStatus = checkMemory();
        details.put("memory", memoryStatus);
        if (!memoryStatus.isHealthy()) {
            healthy = false;
        }

        return new HealthStatus(healthy ? "HEALTHY" : "UNHEALTHY", details);
    }

    public HealthStatus checkDiskSpace() {
        if (dataDir == null || !dataDir.exists()) {
            return new HealthStatus("UNHEALTHY", Map.of("error", "Data directory not found"));
        }

        long freeSpace = dataDir.getFreeSpace();
        long totalSpace = dataDir.getTotalSpace();
        long usableSpace = dataDir.getUsableSpace();

        boolean healthy = usableSpace >= diskSpaceThreshold;

        Map<String, Object> details = new HashMap<>();
        details.put("freeSpace", freeSpace);
        details.put("totalSpace", totalSpace);
        details.put("usableSpace", usableSpace);
        details.put("threshold", diskSpaceThreshold);

        return new HealthStatus(healthy ? "HEALTHY" : "UNHEALTHY", details);
    }

    public HealthStatus checkMemory() {
        MemoryMXBean memoryBean = ManagementFactory.getMemoryMXBean();
        long usedMemory = memoryBean.getHeapMemoryUsage().getUsed();
        long maxMemory = memoryBean.getHeapMemoryUsage().getMax();
        long freeMemory = maxMemory - usedMemory;

        boolean healthy = freeMemory >= memoryThreshold;

        Map<String, Object> details = new HashMap<>();
        details.put("usedMemory", usedMemory);
        details.put("maxMemory", maxMemory);
        details.put("freeMemory", freeMemory);
        details.put("threshold", memoryThreshold);

        return new HealthStatus(healthy ? "HEALTHY" : "UNHEALTHY", details);
    }

    public HealthStatus checkSystemLoad() {
        OperatingSystemMXBean osBean = ManagementFactory.getOperatingSystemMXBean();
        double systemLoad = osBean.getSystemLoadAverage();
        int processors = osBean.getAvailableProcessors();

        Map<String, Object> details = new HashMap<>();
        details.put("systemLoad", systemLoad);
        details.put("availableProcessors", processors);

        return new HealthStatus("HEALTHY", details);
    }
}
