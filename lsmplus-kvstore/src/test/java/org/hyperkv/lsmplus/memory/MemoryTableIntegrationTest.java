package org.hyperkv.lsmplus.memory;

import org.hyperkv.lsmplus.api.model.IndexKey;
import org.hyperkv.lsmplus.api.model.IndexValue;
import org.hyperkv.lsmplus.journal.JournalReplayPoint;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;

import java.nio.charset.StandardCharsets;
import java.util.List;
import java.util.Map;
import java.util.concurrent.CountDownLatch;
import java.util.concurrent.ExecutorService;
import java.util.concurrent.Executors;
import java.util.concurrent.atomic.AtomicInteger;

import static org.junit.jupiter.api.Assertions.*;

class MemoryTableIntegrationTest {

    private MemoryTableManager manager;
    private JournalReplayPoint replayPoint;

    @BeforeEach
    void setUp() {
        manager = new MemoryTableManager(1024 * 1024);
        replayPoint = new JournalReplayPoint(1, 0, 4096);
    }

    @Test
    void testCompleteWriteReadCycle() {
        for (int i = 0; i < 100; i++) {
            IndexKey key = IndexKey.orderedBytes(("cycle-key-" + i).getBytes(StandardCharsets.UTF_8));
            IndexValue value = IndexValue.normal(("cycle-value-" + i).getBytes(StandardCharsets.UTF_8));
            manager.put(key, value, replayPoint);
        }

        for (int i = 0; i < 100; i++) {
            IndexKey key = IndexKey.orderedBytes(("cycle-key-" + i).getBytes(StandardCharsets.UTF_8));
            IndexValue retrieved = manager.get(key);
            assertNotNull(retrieved);
            assertArrayEquals(("cycle-value-" + i).getBytes(StandardCharsets.UTF_8), retrieved.getValueData());
        }
    }

    @Test
    void testMultipleTables() {
        for (int batch = 0; batch < 3; batch++) {
            for (int i = 0; i < 10; i++) {
                IndexKey key = IndexKey.orderedBytes(
                        ("batch-" + batch + "-key-" + i).getBytes(StandardCharsets.UTF_8));
                IndexValue value = IndexValue.normal(
                        ("batch-" + batch + "-value-" + i).getBytes(StandardCharsets.UTF_8));
                manager.put(key, value, replayPoint);
            }
            manager.sealActiveTable();
        }

        assertEquals(3, manager.getSealedTableCount());

        for (int batch = 0; batch < 3; batch++) {
            for (int i = 0; i < 10; i++) {
                IndexKey key = IndexKey.orderedBytes(
                        ("batch-" + batch + "-key-" + i).getBytes(StandardCharsets.UTF_8));
                IndexValue retrieved = manager.get(key);
                assertNotNull(retrieved);
            }
        }
    }

    @Test
    void testSealAndReplay() {
        for (int i = 0; i < 50; i++) {
            IndexKey key = IndexKey.orderedBytes(("seal-key-" + i).getBytes(StandardCharsets.UTF_8));
            IndexValue value = IndexValue.normal(("seal-value-" + i).getBytes(StandardCharsets.UTF_8));
            manager.put(key, value, replayPoint);
        }

        manager.sealActiveTable();

        for (int i = 50; i < 100; i++) {
            IndexKey key = IndexKey.orderedBytes(("seal-key-" + i).getBytes(StandardCharsets.UTF_8));
            IndexValue value = IndexValue.normal(("seal-value-" + i).getBytes(StandardCharsets.UTF_8));
            manager.put(key, value, replayPoint);
        }

        for (int i = 0; i < 100; i++) {
            IndexKey key = IndexKey.orderedBytes(("seal-key-" + i).getBytes(StandardCharsets.UTF_8));
            assertNotNull(manager.get(key));
        }
    }

    @Test
    void testConcurrentOperations() throws InterruptedException {
        int threadCount = 10;
        int operationsPerThread = 100;
        ExecutorService executor = Executors.newFixedThreadPool(threadCount);
        CountDownLatch latch = new CountDownLatch(threadCount);
        AtomicInteger errors = new AtomicInteger(0);

        for (int t = 0; t < threadCount; t++) {
            final int threadId = t;
            executor.submit(() -> {
                try {
                    for (int i = 0; i < operationsPerThread; i++) {
                        IndexKey key = IndexKey.orderedBytes(
                                ("concurrent-" + threadId + "-" + i).getBytes(StandardCharsets.UTF_8));
                        IndexValue value = IndexValue.normal(
                                ("value-" + threadId + "-" + i).getBytes(StandardCharsets.UTF_8));
                        manager.put(key, value, replayPoint);
                    }
                } catch (Exception e) {
                    errors.incrementAndGet();
                } finally {
                    latch.countDown();
                }
            });
        }

        latch.await();
        executor.shutdown();

        assertEquals(0, errors.get());
        assertEquals(threadCount * operationsPerThread, manager.getTotalEntryCount());
    }

    @Test
    void testRangeQueryAcrossTables() {
        for (int batch = 0; batch < 3; batch++) {
            for (int i = 0; i < 10; i++) {
                IndexKey key = IndexKey.orderedBytes(
                        ("range-key-" + String.format("%02d", batch * 10 + i)).getBytes(StandardCharsets.UTF_8));
                IndexValue value = IndexValue.normal(("value-" + i).getBytes(StandardCharsets.UTF_8));
                manager.put(key, value, replayPoint);
            }
            manager.sealActiveTable();
        }

        IndexKey start = IndexKey.orderedBytes("range-key-10".getBytes(StandardCharsets.UTF_8));
        IndexKey end = IndexKey.orderedBytes("range-key-20".getBytes(StandardCharsets.UTF_8));

        List<Map.Entry<IndexKey, IndexValue>> results = manager.rangeQuery(start, end);

        assertTrue(results.size() >= 10);
    }

    @Test
    void testMixedOperations() {
        IndexKey key1 = IndexKey.orderedBytes("mixed-put".getBytes(StandardCharsets.UTF_8));
        IndexKey key2 = IndexKey.orderedBytes("mixed-delete".getBytes(StandardCharsets.UTF_8));
        IndexKey key3 = IndexKey.orderedBytes("mixed-update".getBytes(StandardCharsets.UTF_8));

        manager.put(key1, IndexValue.normal("value1".getBytes(StandardCharsets.UTF_8)), replayPoint);
        manager.put(key2, IndexValue.normal("value2".getBytes(StandardCharsets.UTF_8)), replayPoint);
        manager.put(key3, IndexValue.normal("value3".getBytes(StandardCharsets.UTF_8)), replayPoint);

        manager.delete(key2, replayPoint);

        manager.put(key3, IndexValue.normal("updated-value3".getBytes(StandardCharsets.UTF_8)), replayPoint);

        assertFalse(manager.get(key1).isTombstone());
        assertTrue(manager.get(key2).isTombstone());
        assertArrayEquals("updated-value3".getBytes(StandardCharsets.UTF_8),
                manager.get(key3).getValueData());
    }

    @Test
    void testAutoSealUnderLoad() {
        MemoryTableManager smallManager = new MemoryTableManager(500);

        int sealedCount = 0;
        for (int i = 0; i < 100; i++) {
            IndexKey key = IndexKey.orderedBytes(("auto-key-" + i).getBytes(StandardCharsets.UTF_8));
            IndexValue value = IndexValue.normal(new byte[100]);
            smallManager.put(key, value, replayPoint);

            if (smallManager.getSealedTableCount() > sealedCount) {
                sealedCount = smallManager.getSealedTableCount();
            }
        }

        assertTrue(sealedCount > 0);
    }

    @Test
    void testReplayPointTracking() {
        JournalReplayPoint point1 = new JournalReplayPoint(1, 0, 4096);
        JournalReplayPoint point2 = new JournalReplayPoint(1, 0, 8192);
        JournalReplayPoint point3 = new JournalReplayPoint(1, 0, 12288);

        IndexKey key1 = IndexKey.orderedBytes("replay-1".getBytes(StandardCharsets.UTF_8));
        IndexKey key2 = IndexKey.orderedBytes("replay-2".getBytes(StandardCharsets.UTF_8));
        IndexKey key3 = IndexKey.orderedBytes("replay-3".getBytes(StandardCharsets.UTF_8));

        manager.put(key1, IndexValue.normal("v1".getBytes(StandardCharsets.UTF_8)), point1);
        manager.put(key2, IndexValue.normal("v2".getBytes(StandardCharsets.UTF_8)), point2);
        manager.put(key3, IndexValue.normal("v3".getBytes(StandardCharsets.UTF_8)), point3);

        MemoryTable active = manager.getActiveTable();
        assertEquals(point1, active.getFirstReplayPoint());
        assertEquals(point3, active.getLastReplayPoint());
    }
}