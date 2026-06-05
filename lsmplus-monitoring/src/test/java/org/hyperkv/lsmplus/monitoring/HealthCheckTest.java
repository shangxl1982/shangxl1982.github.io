package org.hyperkv.lsmplus.monitoring;

import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.io.TempDir;

import java.io.File;

import static org.junit.jupiter.api.Assertions.*;

class HealthCheckTest {

    @TempDir
    File tempDir;

    private HealthCheck healthCheck;

    @BeforeEach
    void setUp() {
        healthCheck = new HealthCheck(tempDir);
    }

    @Test
    void testCheck() {
        HealthStatus status = healthCheck.check();

        assertNotNull(status);
        assertNotNull(status.getStatus());
        assertNotNull(status.getDetails());
    }

    @Test
    void testCheckDiskSpace() {
        HealthStatus status = healthCheck.checkDiskSpace();

        assertNotNull(status);
        assertNotNull(status.getStatus());
        assertTrue(status.getDetails().containsKey("freeSpace"));
        assertTrue(status.getDetails().containsKey("totalSpace"));
        assertTrue(status.getDetails().containsKey("usableSpace"));
    }

    @Test
    void testCheckMemory() {
        HealthStatus status = healthCheck.checkMemory();

        assertNotNull(status);
        assertNotNull(status.getStatus());
        assertTrue(status.getDetails().containsKey("usedMemory"));
        assertTrue(status.getDetails().containsKey("maxMemory"));
        assertTrue(status.getDetails().containsKey("freeMemory"));
    }

    @Test
    void testCheckSystemLoad() {
        HealthStatus status = healthCheck.checkSystemLoad();

        assertNotNull(status);
        assertNotNull(status.getStatus());
        assertTrue(status.getDetails().containsKey("systemLoad"));
        assertTrue(status.getDetails().containsKey("availableProcessors"));
    }

    @Test
    void testHealthStatusIsHealthy() {
        HealthStatus healthy = new HealthStatus("HEALTHY", null);
        assertTrue(healthy.isHealthy());

        HealthStatus unhealthy = new HealthStatus("UNHEALTHY", null);
        assertFalse(unhealthy.isHealthy());
    }

    @Test
    void testHealthStatusToString() {
        HealthStatus status = new HealthStatus("HEALTHY", null);
        String str = status.toString();
        assertTrue(str.contains("HEALTHY"));
    }
}
