package org.hyperkv.lsmplus.bplustree;

import org.hyperkv.lsmplus.bplustree.page.Page;

import java.util.LinkedHashMap;
import java.util.Map;

public class PageCache {

    public static final int DEFAULT_MAX_SIZE = 10000;

    private final int maxSize;
    private final Map<String, Page> cache;

    public PageCache(int maxSize) {
        if (maxSize <= 0) {
            throw new IllegalArgumentException("maxSize must be positive");
        }
        this.maxSize = maxSize;
        this.cache = new LinkedHashMap<String, Page>(16, 0.75f, true) {
            private static final long serialVersionUID = 1L;

            @Override
            protected boolean removeEldestEntry(Map.Entry<String, Page> eldest) {
                return size() > PageCache.this.maxSize;
            }
        };
    }

    public PageCache() {
        this(DEFAULT_MAX_SIZE);
    }

    public synchronized Page get(String key) {
        return cache.get(key);
    }

    public synchronized void put(String key, Page page) {
        if (key == null || page == null) {
            return;
        }
        cache.put(key, page);
    }

    public synchronized void remove(String key) {
        cache.remove(key);
    }

    public synchronized void clear() {
        cache.clear();
    }

    public synchronized int size() {
        return cache.size();
    }

    public synchronized boolean contains(String key) {
        return cache.containsKey(key);
    }

    public int getMaxSize() {
        return maxSize;
    }
}
