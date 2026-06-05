package org.hyperkv.lsmplus.storage;

import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

import java.io.IOException;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import java.util.UUID;
import java.util.concurrent.locks.ReentrantLock;

public class ChunkCache {

    private static final Logger log = LoggerFactory.getLogger(ChunkCache.class);

    private final int maxSize;
    private final Map<UUID, Chunk> cache;
    private final ReentrantLock lock = new ReentrantLock();

    public ChunkCache(int maxSize) {
        if (maxSize <= 0) {
            throw new IllegalArgumentException("maxSize must be positive");
        }
        this.maxSize = maxSize;
        this.cache = new LinkedHashMap<>(16, 0.75f, true) {
            @Override
            protected boolean removeEldestEntry(Map.Entry<UUID, Chunk> eldest) {
                if (size() > ChunkCache.this.maxSize) {
                    try {
                        eldest.getValue().close();
                        log.debug("Evicted chunk from cache: chunkId={}", eldest.getKey());
                    } catch (IOException e) {
                        log.warn("Failed to close evicted chunk: chunkId={}", eldest.getKey(), e);
                    }
                    return true;
                }
                return false;
            }
        };
    }

    public Chunk get(UUID chunkId) {
        lock.lock();
        try {
            return cache.get(chunkId);
        } finally {
            lock.unlock();
        }
    }

    public void put(UUID chunkId, Chunk chunk) {
        lock.lock();
        try {
            Chunk existing = cache.get(chunkId);
            if (existing != null && existing != chunk) {
                try {
                    existing.close();
                } catch (IOException e) {
                    log.warn("Failed to close existing chunk: chunkId={}", chunkId, e);
                }
            }
            cache.put(chunkId, chunk);
        } finally {
            lock.unlock();
        }
    }

    public Chunk remove(UUID chunkId) {
        lock.lock();
        try {
            Chunk chunk = cache.remove(chunkId);
            if (chunk != null) {
                try {
                    chunk.close();
                } catch (IOException e) {
                    log.warn("Failed to close removed chunk: chunkId={}", chunkId, e);
                }
            }
            return chunk;
        } finally {
            lock.unlock();
        }
    }

    public boolean contains(UUID chunkId) {
        lock.lock();
        try {
            return cache.containsKey(chunkId);
        } finally {
            lock.unlock();
        }
    }

    public void clear() {
        lock.lock();
        try {
            for (Chunk chunk : cache.values()) {
                try {
                    chunk.close();
                } catch (IOException e) {
                    log.warn("Failed to close chunk during clear", e);
                }
            }
            cache.clear();
        } finally {
            lock.unlock();
        }
    }

    public int size() {
        lock.lock();
        try {
            return cache.size();
        } finally {
            lock.unlock();
        }
    }

    public int getMaxSize() {
        return maxSize;
    }

    public List<Chunk> getAll() {
        lock.lock();
        try {
            return cache.values().stream().toList();
        } finally {
            lock.unlock();
        }
    }
}
