package org.hyperkv.lsmplus.monitoring;

import java.util.Arrays;
import java.util.HashMap;
import java.util.List;
import java.util.Map;
import java.util.concurrent.atomic.AtomicLong;

/**
 * Extended performance counter that supports additional custom statistics.
 * 
 * <p>Fields are declared at construction time and values are recorded positionally.
 * 
 * <p>Example usage:
 * <pre>
 * ExtendedPerformanceCounter putCounter = MetricsRegistry.getExtendedCounter(
 *     "put", "PUT operations", "putSize", "keySize", "valueSize");
 * 
 * putCounter.recordSuccess(latencyMicros, putSize, keySize, valueSize);
 * </pre>
 */
public class ExtendedPerformanceCounter extends PerformanceCounter {

    private final String[] fieldNames;
    private final FieldStatistics[] fieldStatistics;

    public ExtendedPerformanceCounter(String name, String description, String... fieldNames) {
        super(name, description);
        this.fieldNames = fieldNames != null ? fieldNames.clone() : new String[0];
        this.fieldStatistics = new FieldStatistics[this.fieldNames.length];
        for (int i = 0; i < this.fieldNames.length; i++) {
            this.fieldStatistics[i] = new FieldStatistics();
        }
    }

    /**
     * Records a successful operation with latency only (no field values).
     */
    @Override
    public void recordSuccess(long latencyMicros) {
        super.recordSuccess(latencyMicros);
    }

    /**
     * Records a successful operation with latency and additional field values.
     * 
     * <p>Field values must be provided in the same order as field names declared at construction.
     * 
     * @param latencyMicros latency in microseconds
     * @param fieldValues field values in the same order as declared field names
     * @throws IllegalArgumentException if fieldValues length doesn't match declared field names
     */
    public void recordSuccess(long latencyMicros, long... fieldValues) {
        super.recordSuccess(latencyMicros);
        
        if (fieldValues == null || fieldValues.length == 0) {
            return;
        }
        
        if (fieldValues.length != fieldNames.length) {
            throw new IllegalArgumentException(
                "Field values count (" + fieldValues.length + ") doesn't match declared fields count (" 
                + fieldNames.length + ") for counter " + getName());
        }
        
        for (int i = 0; i < fieldValues.length; i++) {
            fieldStatistics[i].record(fieldValues[i]);
        }
    }

    /**
     * Gets the field names declared for this counter.
     * 
     * @return array of field names
     */
    public String[] getFieldNames() {
        return fieldNames.clone();
    }

    /**
     * Gets statistics for a specific field by name.
     * 
     * @param fieldName name of the field
     * @return FieldSnapshot with statistics, or null if field doesn't exist
     */
    public FieldSnapshot getFieldSnapshot(String fieldName) {
        for (int i = 0; i < fieldNames.length; i++) {
            if (fieldNames[i].equals(fieldName)) {
                return fieldStatistics[i].getSnapshot();
            }
        }
        return null;
    }

    /**
     * Gets statistics for a specific field by index.
     * 
     * @param index field index (0-based)
     * @return FieldSnapshot with statistics
     * @throws IndexOutOfBoundsException if index is out of range
     */
    public FieldSnapshot getFieldSnapshot(int index) {
        if (index < 0 || index >= fieldNames.length) {
            throw new IndexOutOfBoundsException("Field index " + index + " out of bounds for " + fieldNames.length + " fields");
        }
        return fieldStatistics[index].getSnapshot();
    }

    /**
     * Gets all field snapshots as a map.
     * 
     * @return map of field name to snapshot
     */
    public Map<String, FieldSnapshot> getAllFieldSnapshots() {
        Map<String, FieldSnapshot> result = new HashMap<>();
        for (int i = 0; i < fieldNames.length; i++) {
            result.put(fieldNames[i], fieldStatistics[i].getSnapshot());
        }
        return result;
    }

    /**
     * Gets all field snapshots as a list, in declaration order.
     * 
     * @return list of field snapshots
     */
    public List<FieldSnapshot> getFieldSnapshots() {
        return Arrays.stream(fieldStatistics)
            .map(FieldStatistics::getSnapshot)
            .toList();
    }

    /**
     * Gets the number of declared fields.
     * 
     * @return number of fields
     */
    public int getFieldCount() {
        return fieldNames.length;
    }

    @Override
    public void reset() {
        super.reset();
        for (FieldStatistics stats : fieldStatistics) {
            stats.reset();
        }
    }

    /**
     * Inner class to track statistics for a single field.
     */
    private static class FieldStatistics {
        private final AtomicLong count = new AtomicLong(0);
        private final AtomicLong sum = new AtomicLong(0);
        private final AtomicLong min = new AtomicLong(Long.MAX_VALUE);
        private final AtomicLong max = new AtomicLong(Long.MIN_VALUE);

        void record(long value) {
            count.incrementAndGet();
            sum.addAndGet(value);
            
            updateMin(value);
            updateMax(value);
        }

        private void updateMin(long value) {
            long current;
            do {
                current = min.get();
                if (value >= current) break;
            } while (!min.compareAndSet(current, value));
        }

        private void updateMax(long value) {
            long current;
            do {
                current = max.get();
                if (value <= current) break;
            } while (!max.compareAndSet(current, value));
        }

        void reset() {
            count.set(0);
            sum.set(0);
            min.set(Long.MAX_VALUE);
            max.set(Long.MIN_VALUE);
        }

        FieldSnapshot getSnapshot() {
            long c = count.get();
            return new FieldSnapshot(
                c,
                c > 0 ? (double) sum.get() / c : 0.0,
                c > 0 ? min.get() : 0,
                c > 0 ? max.get() : 0,
                sum.get()
            );
        }
    }

    /**
     * Snapshot of field statistics.
     */
    public static class FieldSnapshot {
        private final long count;
        private final double average;
        private final long min;
        private final long max;
        private final long total;

        public FieldSnapshot(long count, double average, long min, long max, long total) {
            this.count = count;
            this.average = average;
            this.min = min;
            this.max = max;
            this.total = total;
        }

        public long getCount() { return count; }
        public double getAverage() { return average; }
        public long getMin() { return min; }
        public long getMax() { return max; }
        public long getTotal() { return total; }

        @Override
        public String toString() {
            return String.format("count=%d, avg=%.2f, min=%d, max=%d, total=%d",
                count, average, min, max, total);
        }
    }
}
