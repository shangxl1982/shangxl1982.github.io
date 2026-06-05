package org.hyperkv.lsmplus.monitoring;

import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;

import java.util.Map;

import static org.junit.jupiter.api.Assertions.*;

public class ExtendedPerformanceCounterTest {

    private ExtendedPerformanceCounter counter;

    @BeforeEach
    void setUp() {
        counter = new ExtendedPerformanceCounter("test_counter", "Test counter with fields", 
            "putSize", "keySize", "valueSize");
    }

    @Test
    void testRecordSuccessWithLatencyOnly() {
        counter.recordSuccess(100);
        counter.recordSuccess(200);
        counter.recordSuccess(300);

        CounterSnapshot snapshot = counter.getSnapshot();
        assertEquals(3, snapshot.getCount());
        assertEquals(200.0, snapshot.getMean(), 0.01);
        assertEquals(100, snapshot.getMin());
        assertEquals(300, snapshot.getMax());
    }

    @Test
    void testRecordSuccessWithFieldValues() {
        counter.recordSuccess(100, 1024, 16, 1008);
        counter.recordSuccess(200, 2048, 32, 2016);
        counter.recordSuccess(300, 512, 8, 504);

        // Check latency stats
        CounterSnapshot snapshot = counter.getSnapshot();
        assertEquals(3, snapshot.getCount());
        assertEquals(200.0, snapshot.getMean(), 0.01);

        // Check putSize field stats (index 0)
        ExtendedPerformanceCounter.FieldSnapshot putSizeSnapshot = counter.getFieldSnapshot("putSize");
        assertNotNull(putSizeSnapshot);
        assertEquals(3, putSizeSnapshot.getCount());
        assertEquals(1024 + 2048 + 512, putSizeSnapshot.getTotal());
        assertEquals((1024.0 + 2048.0 + 512.0) / 3, putSizeSnapshot.getAverage(), 0.01);
        assertEquals(512, putSizeSnapshot.getMin());
        assertEquals(2048, putSizeSnapshot.getMax());

        // Check keySize field stats (index 1)
        ExtendedPerformanceCounter.FieldSnapshot keySizeSnapshot = counter.getFieldSnapshot("keySize");
        assertNotNull(keySizeSnapshot);
        assertEquals(3, keySizeSnapshot.getCount());
        assertEquals(16 + 32 + 8, keySizeSnapshot.getTotal());
        assertEquals((16.0 + 32.0 + 8.0) / 3, keySizeSnapshot.getAverage(), 0.01);
        assertEquals(8, keySizeSnapshot.getMin());
        assertEquals(32, keySizeSnapshot.getMax());

        // Check valueSize field stats (index 2)
        ExtendedPerformanceCounter.FieldSnapshot valueSizeSnapshot = counter.getFieldSnapshot("valueSize");
        assertNotNull(valueSizeSnapshot);
        assertEquals(3, valueSizeSnapshot.getCount());
        assertEquals(1008 + 2016 + 504, valueSizeSnapshot.getTotal());
    }

    @Test
    void testGetFieldSnapshotByIndex() {
        counter.recordSuccess(100, 1024, 16, 1008);

        ExtendedPerformanceCounter.FieldSnapshot putSizeSnapshot = counter.getFieldSnapshot(0);
        assertNotNull(putSizeSnapshot);
        assertEquals(1024, putSizeSnapshot.getTotal());

        ExtendedPerformanceCounter.FieldSnapshot keySizeSnapshot = counter.getFieldSnapshot(1);
        assertNotNull(keySizeSnapshot);
        assertEquals(16, keySizeSnapshot.getTotal());

        ExtendedPerformanceCounter.FieldSnapshot valueSizeSnapshot = counter.getFieldSnapshot(2);
        assertNotNull(valueSizeSnapshot);
        assertEquals(1008, valueSizeSnapshot.getTotal());
    }

    @Test
    void testGetFieldNames() {
        String[] fieldNames = counter.getFieldNames();
        assertArrayEquals(new String[]{"putSize", "keySize", "valueSize"}, fieldNames);
    }

    @Test
    void testGetFieldCount() {
        assertEquals(3, counter.getFieldCount());
    }

    @Test
    void testFieldValuesCountMismatch() {
        assertThrows(IllegalArgumentException.class, () -> {
            counter.recordSuccess(100, 1024, 16); // Missing valueSize
        });

        assertThrows(IllegalArgumentException.class, () -> {
            counter.recordSuccess(100, 1024, 16, 1008, 999); // Extra value
        });
    }

    @Test
    void testGetFieldSnapshotInvalidIndex() {
        assertThrows(IndexOutOfBoundsException.class, () -> {
            counter.getFieldSnapshot(-1);
        });

        assertThrows(IndexOutOfBoundsException.class, () -> {
            counter.getFieldSnapshot(3);
        });
    }

    @Test
    void testGetFieldSnapshotInvalidName() {
        ExtendedPerformanceCounter.FieldSnapshot snapshot = counter.getFieldSnapshot("nonExistent");
        assertNull(snapshot);
    }

    @Test
    void testGetAllFieldSnapshots() {
        counter.recordSuccess(100, 1024, 16, 1008);
        counter.recordSuccess(200, 2048, 32, 2016);

        Map<String, ExtendedPerformanceCounter.FieldSnapshot> allFields = counter.getAllFieldSnapshots();
        assertEquals(3, allFields.size());
        assertTrue(allFields.containsKey("putSize"));
        assertTrue(allFields.containsKey("keySize"));
        assertTrue(allFields.containsKey("valueSize"));

        assertEquals(2, allFields.get("putSize").getCount());
        assertEquals(3072, allFields.get("putSize").getTotal());
    }

    @Test
    void testGetFieldSnapshotsList() {
        counter.recordSuccess(100, 1024, 16, 1008);
        counter.recordSuccess(200, 2048, 32, 2016);

        var snapshots = counter.getFieldSnapshots();
        assertEquals(3, snapshots.size());
        
        // Verify order matches declaration order
        assertEquals(1024 + 2048, snapshots.get(0).getTotal()); // putSize
        assertEquals(16 + 32, snapshots.get(1).getTotal());     // keySize
        assertEquals(1008 + 2016, snapshots.get(2).getTotal()); // valueSize
    }

    @Test
    void testReset() {
        counter.recordSuccess(100, 1024, 16, 1008);

        assertNotNull(counter.getFieldSnapshot("putSize"));
        assertEquals(1, counter.getFieldSnapshot("putSize").getCount());

        counter.reset();

        // After reset, count should be 0 but fields still exist
        ExtendedPerformanceCounter.FieldSnapshot snapshot = counter.getFieldSnapshot("putSize");
        assertNotNull(snapshot);
        assertEquals(0, snapshot.getCount());
        assertEquals(0, snapshot.getMin());
        assertEquals(0, snapshot.getMax());

        CounterSnapshot latencySnapshot = counter.getSnapshot();
        assertEquals(0, latencySnapshot.getCount());
    }

    @Test
    void testEmptyCounter() {
        CounterSnapshot snapshot = counter.getSnapshot();
        assertEquals(0, snapshot.getCount());
        assertEquals(0.0, snapshot.getMean(), 0.01);
        assertEquals(0, snapshot.getMin());
        assertEquals(0, snapshot.getMax());

        // Fields exist but have no data
        assertEquals(3, counter.getFieldCount());
        for (int i = 0; i < 3; i++) {
            ExtendedPerformanceCounter.FieldSnapshot fieldSnapshot = counter.getFieldSnapshot(i);
            assertNotNull(fieldSnapshot);
            assertEquals(0, fieldSnapshot.getCount());
        }
    }

    @Test
    void testRecordError() {
        counter.recordSuccess(100, 1024, 16, 1008);
        counter.recordError();
        counter.recordError();

        CounterSnapshot snapshot = counter.getSnapshot();
        assertEquals(1, snapshot.getCount());
        assertEquals(2, snapshot.getErrorCount());

        // Field stats should still be tracked
        ExtendedPerformanceCounter.FieldSnapshot fieldSnapshot = counter.getFieldSnapshot("putSize");
        assertNotNull(fieldSnapshot);
        assertEquals(1, fieldSnapshot.getCount());
    }

    @Test
    void testFieldSnapshotToString() {
        counter.recordSuccess(100, 1024, 16, 1008);
        counter.recordSuccess(200, 2048, 32, 2016);

        ExtendedPerformanceCounter.FieldSnapshot snapshot = counter.getFieldSnapshot("putSize");
        String str = snapshot.toString();
        assertTrue(str.contains("count=2"));
        assertTrue(str.contains("avg=1536.00"));
        assertTrue(str.contains("min=1024"));
        assertTrue(str.contains("max=2048"));
        assertTrue(str.contains("total=3072"));
    }

    @Test
    void testExtendedCounterSnapshot() {
        counter.recordSuccess(100, 1024, 16, 1008);
        counter.recordSuccess(200, 2048, 32, 2016);

        CounterSnapshot baseSnapshot = counter.getSnapshot();
        Map<String, ExtendedPerformanceCounter.FieldSnapshot> fieldSnapshots = counter.getAllFieldSnapshots();

        ExtendedCounterSnapshot extSnapshot = new ExtendedCounterSnapshot(
            baseSnapshot.getName(),
            baseSnapshot.getTimestamp(),
            baseSnapshot.getCount(),
            baseSnapshot.getErrorCount(),
            baseSnapshot.getMean(),
            baseSnapshot.getMin(),
            baseSnapshot.getMax(),
            baseSnapshot.getP50(),
            baseSnapshot.getP75(),
            baseSnapshot.getP90(),
            baseSnapshot.getP99(),
            fieldSnapshots
        );

        assertEquals(2, extSnapshot.getCount());
        assertEquals(150.0, extSnapshot.getMean(), 0.01);
        assertNotNull(extSnapshot.getFieldSnapshots().get("putSize"));

        // Test JSON output
        String json = extSnapshot.toJson();
        assertTrue(json.contains("\"name\""));
        assertTrue(json.contains("\"count\":2"));
        assertTrue(json.contains("\"fields\""));
        assertTrue(json.contains("\"putSize\""));
    }

    @Test
    void testCounterWithNoFields() {
        ExtendedPerformanceCounter noFieldCounter = new ExtendedPerformanceCounter("no_fields", "Counter without fields");
        
        assertEquals(0, noFieldCounter.getFieldCount());
        
        noFieldCounter.recordSuccess(100);
        noFieldCounter.recordSuccess(200);
        
        CounterSnapshot snapshot = noFieldCounter.getSnapshot();
        assertEquals(2, snapshot.getCount());
        assertEquals(150.0, snapshot.getMean(), 0.01);
        
        assertTrue(noFieldCounter.getAllFieldSnapshots().isEmpty());
    }
}
