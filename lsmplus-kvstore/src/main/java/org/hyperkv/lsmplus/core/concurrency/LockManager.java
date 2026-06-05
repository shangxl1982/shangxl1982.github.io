package org.hyperkv.lsmplus.core.concurrency;

import org.hyperkv.lsmplus.api.model.IndexKey;

import java.util.concurrent.ConcurrentHashMap;
import java.util.concurrent.locks.ReentrantReadWriteLock;

public class LockManager {

    private final ConcurrentHashMap<IndexKey, ReentrantReadWriteLock> keyLocks;
    private final ReentrantReadWriteLock globalLock;
    private final int maxLocks;

    public LockManager(int maxLocks) {
        if (maxLocks <= 0) {
            throw new IllegalArgumentException("maxLocks must be positive");
        }
        this.keyLocks = new ConcurrentHashMap<>();
        this.globalLock = new ReentrantReadWriteLock();
        this.maxLocks = maxLocks;
    }

    public LockManager() {
        this(10000);
    }

    private void checkLockLimit() {
        if (keyLocks.size() >= maxLocks) {
            cleanup();
            if (keyLocks.size() >= maxLocks) {
                throw new IllegalStateException("Lock limit exceeded: " + maxLocks);
            }
        }
    }

    public LockHandle acquireReadLock(IndexKey key) {
        ReentrantReadWriteLock lock = getOrCreateLock(key);
        lock.readLock().lock();
        return new LockHandle(lock, true);
    }

    public LockHandle acquireWriteLock(IndexKey key) {
        ReentrantReadWriteLock lock = getOrCreateLock(key);
        lock.writeLock().lock();
        return new LockHandle(lock, false);
    }

    public boolean tryAcquireReadLock(IndexKey key) {
        ReentrantReadWriteLock lock = getOrCreateLock(key);
        return lock.readLock().tryLock();
    }

    public boolean tryAcquireWriteLock(IndexKey key) {
        ReentrantReadWriteLock lock = getOrCreateLock(key);
        return lock.writeLock().tryLock();
    }

    public void acquireGlobalReadLock() {
        globalLock.readLock().lock();
    }

    public void releaseGlobalReadLock() {
        globalLock.readLock().unlock();
    }

    public void acquireGlobalWriteLock() {
        globalLock.writeLock().lock();
    }

    public void releaseGlobalWriteLock() {
        globalLock.writeLock().unlock();
    }

    private ReentrantReadWriteLock getOrCreateLock(IndexKey key) {
        checkLockLimit();
        return keyLocks.computeIfAbsent(key, k -> new ReentrantReadWriteLock());
    }

    public int getLockCount() {
        return keyLocks.size();
    }

    public void cleanup() {
        keyLocks.entrySet().removeIf(entry -> {
            ReentrantReadWriteLock lock = entry.getValue();
            return !lock.isWriteLocked() && lock.getReadLockCount() == 0;
        });
    }

    public static final class LockHandle implements AutoCloseable {
        private final ReentrantReadWriteLock lock;
        private final boolean isReadLock;
        private boolean released;

        private LockHandle(ReentrantReadWriteLock lock, boolean isReadLock) {
            this.lock = lock;
            this.isReadLock = isReadLock;
            this.released = false;
        }

        public void release() {
            if (!released) {
                if (isReadLock) {
                    lock.readLock().unlock();
                } else {
                    lock.writeLock().unlock();
                }
                released = true;
            }
        }

        public ReentrantReadWriteLock getLock() {
            return lock;
        }

        @Override
        public void close() {
            release();
        }
    }
}
