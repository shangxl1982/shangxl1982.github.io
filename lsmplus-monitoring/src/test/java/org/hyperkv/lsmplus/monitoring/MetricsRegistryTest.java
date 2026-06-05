package org.hyperkv.lsmplus.monitoring;

import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.io.TempDir;

import java.io.File;

import static org.junit.jupiter.api.Assertions.*;

class MetricsRegistryTest {

    private MetricsRegistry registry;

    @BeforeEach
    void setUp() {
        MetricsRegistry.shutdown();
        MetricsRegistry.initialize("test");
        registry = MetricsRegistry.getInstance();
    }

    @AfterEach
    void tearDown() {
        MetricsRegistry.shutdown();
    }

    @Test
    void testCounter() {
        PerformanceCounter counter = registry.counter("requests", "Total requests");

        counter.recordSuccess(100);
        assertEquals(1, counter.getSnapshot().getCount());

        counter.recordSuccess(200);
        assertEquals(2, counter.getSnapshot().getCount());
    }

    @Test
    void testGauge() {
        Gauge gauge = registry.gauge("active_connections", "Active connections");

        gauge.set(10);
        assertEquals(10, gauge.getValue());

        gauge.increment();
        assertEquals(11, gauge.getValue());

        gauge.decrement();
        assertEquals(10, gauge.getValue());
    }

    @Test
    void testHistogram() {
        Histogram histogram = registry.histogram("latency", "Request latency in ms");

        histogram.observe(10);
        histogram.observe(20);
        histogram.observe(30);

        assertEquals(3, histogram.getCount());
        assertEquals(60, histogram.getSum());
        assertEquals(20.0, histogram.getAverage(), 0.01);
    }

    @Test
    void testGetMetric() {
        PerformanceCounter counter = registry.counter("test_counter", "Test counter");
        Metric retrieved = registry.getMetric("test_counter");

        assertNotNull(retrieved);
        assertEquals(counter.getName(), retrieved.getName());
    }

    @Test
    void testReset() {
        PerformanceCounter counter = registry.counter("test_counter", "Test counter");
        counter.recordSuccess(100);
        counter.recordSuccess(200);

        assertEquals(2, counter.getSnapshot().getCount());

        registry.reset();
        assertEquals(0, counter.getSnapshot().getCount());
    }

    @Test
    void testExportPrometheus() {
        PerformanceCounter counter = registry.counter("requests", "Total requests");
        counter.recordSuccess(100);
        counter.recordSuccess(200);

        String exported = registry.exportPrometheus();

        assertTrue(exported.contains("test_requests"));
        assertTrue(exported.contains("# TYPE"));
        assertTrue(exported.contains("summary"));
    }
}
