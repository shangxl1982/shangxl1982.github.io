package org.hyperkv.lsmplus.monitoring;

import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.io.TempDir;

import java.io.File;
import java.nio.file.Files;
import java.nio.file.Path;

import static org.junit.jupiter.api.Assertions.*;

class PerformanceCounterTest {

    @Test
    void testPerformanceCounterBasicOperations() {
        PerformanceCounter counter = new PerformanceCounter("test_counter", "Test counter");
        
        counter.recordSuccess(100);
        counter.recordSuccess(200);
        counter.recordSuccess(300);
        counter.recordError();
        
        CounterSnapshot snapshot = counter.getSnapshot();
        
        assertEquals(3, snapshot.getCount());
        assertEquals(1, snapshot.getErrorCount());
        assertEquals(200.0, snapshot.getMean(), 0.01);
        assertEquals(100, snapshot.getMin());
        assertEquals(300, snapshot.getMax());
    }

    @Test
    void testMetricsRegistryWithLogger(@TempDir Path tempDir) throws Exception {
        File logFile = tempDir.resolve("performance-counter.log").toFile();
        
        MetricsRegistry.shutdown();
        MetricsRegistry.initialize("test");
        MetricsRegistry registry = MetricsRegistry.getInstance();
        
        MetricsLogger logger = new MetricsLogger(logFile.getAbsolutePath());
        
        registry.addListener(logger);
        
        PerformanceCounter putCounter = MetricsRegistry.getCounter("put", "Put operation");
        PerformanceCounter getCounter = MetricsRegistry.getCounter("get", "Get operation");
        
        putCounter.recordSuccess(150);
        putCounter.recordSuccess(250);
        getCounter.recordSuccess(50);
        
        registry.start();
        
        Thread.sleep(11000);
        
        registry.stop();
        logger.close();
        
        String logContent = Files.readString(logFile.toPath());
        assertTrue(logContent.contains("test_put"));
        assertTrue(logContent.contains("test_get"));
        assertTrue(logContent.contains("150"));
        assertTrue(logContent.contains("250"));
        assertTrue(logContent.contains("50"));
        
        MetricsRegistry.shutdown();
    }

    @Test
    void testMetricsSnapshot() {
        MetricsRegistry.shutdown();
        MetricsRegistry.initialize("test");
        MetricsRegistry registry = MetricsRegistry.getInstance();
        
        PerformanceCounter counter = MetricsRegistry.getCounter("operation", "Test operation");
        counter.recordSuccess(100);
        counter.recordSuccess(200);
        
        MetricsSnapshot snapshot = registry.getSnapshot();
        
        assertNotNull(snapshot);
        assertEquals(1, snapshot.getCounters().size());
        assertTrue(snapshot.getCounters().containsKey("operation"));
        
        CounterSnapshot counterSnapshot = snapshot.getCounters().get("operation");
        assertEquals(2, counterSnapshot.getCount());
        assertEquals(150.0, counterSnapshot.getMean(), 0.01);
        
        MetricsRegistry.shutdown();
    }

    @Test
    void testPrometheusFormat() {
        PerformanceCounter counter = new PerformanceCounter("test_op", "Test operation");
        counter.recordSuccess(100);
        counter.recordSuccess(200);
        
        String prometheus = counter.toPrometheusFormat();
        
        assertTrue(prometheus.contains("# HELP test_op Test operation"));
        assertTrue(prometheus.contains("# TYPE test_op summary"));
        assertTrue(prometheus.contains("test_op_count 2"));
        assertTrue(prometheus.contains("test_op_errors 0"));
    }
}
