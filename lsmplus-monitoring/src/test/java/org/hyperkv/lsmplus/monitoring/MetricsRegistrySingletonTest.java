package org.hyperkv.lsmplus.monitoring;

import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;

import static org.junit.jupiter.api.Assertions.*;

class MetricsRegistrySingletonTest {

    @BeforeEach
    void setUp() {
        MetricsRegistry.shutdown();
    }

    @AfterEach
    void tearDown() {
        MetricsRegistry.shutdown();
    }

    @Test
    void testGetInstanceReturnsNullBeforeInitialization() {
        assertNull(MetricsRegistry.getInstance());
    }

    @Test
    void testGetCounterReturnsNoOpBeforeInitialization() {
        PerformanceCounter counter = MetricsRegistry.getCounter("test", "Test counter");
        
        assertNotNull(counter);
        assertTrue(counter instanceof NoOpPerformanceCounter);
        
        counter.recordSuccess(100);
        counter.recordError();
        
        CounterSnapshot snapshot = counter.getSnapshot();
        assertEquals(0, snapshot.getCount());
        assertEquals(0, snapshot.getErrorCount());
    }

    @Test
    void testInitializeCreatesSingleton() {
        MetricsRegistry.initialize("test");
        
        assertNotNull(MetricsRegistry.getInstance());
    }

    @Test
    void testInitializeIsIdempotent() {
        MetricsRegistry.initialize("test");
        MetricsRegistry.initialize("test2");
        
        assertNotNull(MetricsRegistry.getInstance());
        MetricsRegistry registry = MetricsRegistry.getInstance();
        assertNotNull(registry);
    }

    @Test
    void testGetCounterReturnsRealCounterAfterInitialization() {
        MetricsRegistry.initialize("test");
        
        PerformanceCounter counter = MetricsRegistry.getCounter("operation", "Test operation");
        
        assertNotNull(counter);
        assertFalse(counter instanceof NoOpPerformanceCounter);
        
        counter.recordSuccess(100);
        counter.recordSuccess(200);
        
        CounterSnapshot snapshot = counter.getSnapshot();
        assertEquals(2, snapshot.getCount());
        assertEquals(150.0, snapshot.getMean(), 0.01);
    }

    @Test
    void testShutdownClearsInstance() {
        MetricsRegistry.initialize("test");
        assertNotNull(MetricsRegistry.getInstance());
        
        MetricsRegistry.shutdown();
        
        assertNull(MetricsRegistry.getInstance());
    }

    @Test
    void testGetCounterWithSimpleName() {
        MetricsRegistry.initialize("test");
        
        PerformanceCounter counter = MetricsRegistry.getCounter("simple");
        
        assertNotNull(counter);
        assertFalse(counter instanceof NoOpPerformanceCounter);
    }

    @Test
    void testMultipleGetCounterCallsReturnSameCounter() {
        MetricsRegistry.initialize("test");
        
        PerformanceCounter counter1 = MetricsRegistry.getCounter("operation", "Test operation");
        PerformanceCounter counter2 = MetricsRegistry.getCounter("operation", "Test operation");
        
        assertSame(counter1, counter2);
    }

    @Test
    void testNoOpCounterDoesNotThrow() {
        PerformanceCounter counter = NoOpPerformanceCounter.getInstance();
        
        assertDoesNotThrow(() -> {
            counter.recordSuccess(100);
            counter.recordError();
            counter.reset();
            counter.getSnapshot();
            counter.toPrometheusFormat();
        });
    }
}
