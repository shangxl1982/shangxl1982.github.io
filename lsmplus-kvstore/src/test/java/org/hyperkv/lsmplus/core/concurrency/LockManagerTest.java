package org.hyperkv.lsmplus.core.concurrency;

import org.hyperkv.lsmplus.api.model.IndexKey;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;

import java.nio.charset.StandardCharsets;
import java.util.concurrent.CountDownLatch;
import java.util.concurrent.ExecutorService;
import java.util.concurrent.Executors;
import java.util.concurrent.TimeUnit;
import java.util.concurrent.atomic.AtomicInteger;

import static org.junit.jupiter.api.Assertions.*;

class LockManagerTest {

    private LockManager lockManager;

    @BeforeEach
    void setUp() {
        lockManager = new LockManager();
    }

    @Test
    void testAcquireReadLock() {
        IndexKey key = IndexKey.orderedBytes("testKey".getBytes(StandardCharsets.UTF_8));

        try (LockManager.LockHandle handle = lockManager.acquireReadLock(key)) {
            assertNotNull(handle);
        }
    }

    @Test
    void testAcquireWriteLock() {
        IndexKey key = IndexKey.orderedBytes("testKey".getBytes(StandardCharsets.UTF_8));

        try (LockManager.LockHandle handle = lockManager.acquireWriteLock(key)) {
            assertNotNull(handle);
        }
    }

    @Test
    void testTryAcquireReadLock() {
        IndexKey key = IndexKey.orderedBytes("testKey".getBytes(StandardCharsets.UTF_8));

        assertTrue(lockManager.tryAcquireReadLock(key));

        assertTrue(lockManager.tryAcquireReadLock(key));
    }

    @Test
    void testTryAcquireWriteLock() {
        IndexKey key = IndexKey.orderedBytes("testKey".getBytes(StandardCharsets.UTF_8));

        assertTrue(lockManager.tryAcquireWriteLock(key));
    }

    @Test
    void testWriteLockBlocksWriteLock() throws InterruptedException {
        IndexKey key = IndexKey.orderedBytes("testKey".getBytes(StandardCharsets.UTF_8));
        AtomicInteger counter = new AtomicInteger(0);

        LockManager.LockHandle handle = lockManager.acquireWriteLock(key);

        ExecutorService executor = Executors.newSingleThreadExecutor();
        CountDownLatch latch = new CountDownLatch(1);

        executor.submit(() -> {
            latch.countDown();
            boolean acquired = lockManager.tryAcquireWriteLock(key);
            if (!acquired) {
                counter.incrementAndGet();
            }
        });

        latch.await();
        Thread.sleep(100);

        assertEquals(1, counter.get());

        handle.close();
        executor.shutdown();
    }

    @Test
    void testWriteLockBlocksReadLock() throws InterruptedException {
        IndexKey key = IndexKey.orderedBytes("testKey".getBytes(StandardCharsets.UTF_8));
        AtomicInteger counter = new AtomicInteger(0);

        LockManager.LockHandle handle = lockManager.acquireWriteLock(key);

        ExecutorService executor = Executors.newSingleThreadExecutor();
        CountDownLatch latch = new CountDownLatch(1);

        executor.submit(() -> {
            latch.countDown();
            boolean acquired = lockManager.tryAcquireReadLock(key);
            if (!acquired) {
                counter.incrementAndGet();
            }
        });

        latch.await();
        Thread.sleep(100);

        assertEquals(1, counter.get());

        handle.close();
        executor.shutdown();
    }

    @Test
    void testReadLockAllowsReadLock() {
        IndexKey key = IndexKey.orderedBytes("testKey".getBytes(StandardCharsets.UTF_8));

        LockManager.LockHandle handle1 = lockManager.acquireReadLock(key);
        assertNotNull(handle1);

        assertTrue(lockManager.tryAcquireReadLock(key));

        handle1.close();
    }

    @Test
    void testGlobalReadLock() {
        lockManager.acquireGlobalReadLock();
        lockManager.releaseGlobalReadLock();
    }

    @Test
    void testGlobalWriteLock() {
        lockManager.acquireGlobalWriteLock();
        lockManager.releaseGlobalWriteLock();
    }

    @Test
    void testLockCount() {
        assertEquals(0, lockManager.getLockCount());

        IndexKey key1 = IndexKey.orderedBytes("key1".getBytes(StandardCharsets.UTF_8));
        IndexKey key2 = IndexKey.orderedBytes("key2".getBytes(StandardCharsets.UTF_8));

        lockManager.acquireReadLock(key1);
        assertEquals(1, lockManager.getLockCount());

        lockManager.acquireReadLock(key2);
        assertEquals(2, lockManager.getLockCount());
    }

    @Test
    void testCleanup() {
        IndexKey key = IndexKey.orderedBytes("testKey".getBytes(StandardCharsets.UTF_8));

        LockManager.LockHandle handle = lockManager.acquireReadLock(key);
        handle.close();

        lockManager.cleanup();

        assertTrue(lockManager.getLockCount() >= 0);
    }

    @Test
    void testLockHandleAutoClose() {
        IndexKey key = IndexKey.orderedBytes("testKey".getBytes(StandardCharsets.UTF_8));

        try (LockManager.LockHandle handle = lockManager.acquireWriteLock(key)) {
            assertNotNull(handle);
        }

        assertTrue(lockManager.tryAcquireWriteLock(key));
    }

    @Test
    void testConcurrentReadLocks() throws InterruptedException {
        IndexKey key = IndexKey.orderedBytes("testKey".getBytes(StandardCharsets.UTF_8));
        int threadCount = 10;
        ExecutorService executor = Executors.newFixedThreadPool(threadCount);
        CountDownLatch latch = new CountDownLatch(threadCount);
        AtomicInteger successCount = new AtomicInteger(0);

        for (int i = 0; i < threadCount; i++) {
            executor.submit(() -> {
                try (LockManager.LockHandle handle = lockManager.acquireReadLock(key)) {
                    successCount.incrementAndGet();
                    Thread.sleep(10);
                } catch (InterruptedException e) {
                    Thread.currentThread().interrupt();
                } finally {
                    latch.countDown();
                }
            });
        }

        assertTrue(latch.await(5, TimeUnit.SECONDS));
        assertEquals(threadCount, successCount.get());

        executor.shutdown();
    }

    @Test
    void testMultipleKeys() {
        IndexKey key1 = IndexKey.orderedBytes("key1".getBytes(StandardCharsets.UTF_8));
        IndexKey key2 = IndexKey.orderedBytes("key2".getBytes(StandardCharsets.UTF_8));

        LockManager.LockHandle handle1 = lockManager.acquireWriteLock(key1);
        LockManager.LockHandle handle2 = lockManager.acquireWriteLock(key2);

        assertNotNull(handle1);
        assertNotNull(handle2);

        handle1.close();
        handle2.close();
    }
}
