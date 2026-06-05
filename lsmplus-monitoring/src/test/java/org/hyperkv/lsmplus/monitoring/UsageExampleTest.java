package org.hyperkv.lsmplus.monitoring;

import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;

import static org.junit.jupiter.api.Assertions.*;

class UsageExampleTest {

    @BeforeEach
    void setUp() {
        MetricsRegistry.shutdown();
    }

    @AfterEach
    void tearDown() {
        MetricsRegistry.shutdown();
    }

    @Test
    void demonstrateNullSafeCounterUsage() {
        PerformanceCounter counter = MetricsRegistry.getCounter("my_operation", "My custom operation");
        
        assertNotNull(counter);
        assertTrue(counter instanceof NoOpPerformanceCounter);
        
        counter.recordSuccess(100);
        counter.recordSuccess(200);
        counter.recordError();
        
        CounterSnapshot snapshot = counter.getSnapshot();
        assertEquals(0, snapshot.getCount());
        assertEquals(0, snapshot.getErrorCount());
    }

    @Test
    void demonstrateSingletonUsage() {
        MetricsRegistry.initialize("myapp");
        
        PerformanceCounter counter1 = MetricsRegistry.getCounter("operation1", "First operation");
        PerformanceCounter counter2 = MetricsRegistry.getCounter("operation2", "Second operation");
        
        counter1.recordSuccess(100);
        counter1.recordSuccess(150);
        counter2.recordSuccess(50);
        counter2.recordError();
        
        CounterSnapshot snapshot1 = counter1.getSnapshot();
        assertEquals(2, snapshot1.getCount());
        assertEquals(125.0, snapshot1.getMean(), 0.01);
        
        CounterSnapshot snapshot2 = counter2.getSnapshot();
        assertEquals(1, snapshot2.getCount());
        assertEquals(1, snapshot2.getErrorCount());
    }

    @Test
    void demonstrateEasyUsageAnywhere() {
        MetricsRegistry.initialize("myapp");
        
        simulateBusinessLogic();
        
        MetricsRegistry registry = MetricsRegistry.getInstance();
        assertNotNull(registry);
    }
    
    private void simulateBusinessLogic() {
        PerformanceCounter counter = MetricsRegistry.getCounter("business_logic", "Business logic execution");
        
        long startTime = System.nanoTime();
        try {
            Thread.sleep(10);
            counter.recordSuccess((System.nanoTime() - startTime) / 1000);
        } catch (InterruptedException e) {
            counter.recordError();
        }
    }
}
