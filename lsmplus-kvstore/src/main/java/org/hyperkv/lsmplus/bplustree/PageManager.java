package org.hyperkv.lsmplus.bplustree;

import org.hyperkv.lsmplus.bplustree.page.Page;
import org.hyperkv.lsmplus.exception.Exceptions;
import org.hyperkv.lsmplus.exception.StorageException;
import org.hyperkv.lsmplus.storage.Chunk;
import org.hyperkv.lsmplus.storage.ChunkManager;
import org.hyperkv.lsmplus.storage.SegmentLocation;

import java.io.IOException;
import java.util.ArrayList;
import java.util.List;
import java.util.concurrent.CompletableFuture;

public class PageManager {

    private final ChunkManager chunkManager;
    private final PageCache readCache;
    private final PageCapacityConfig config;

    public PageManager(ChunkManager chunkManager, PageCapacityConfig config, int maxCacheSize) {
        if (chunkManager == null) {
            throw Exceptions.invalidArgument("chunkManager must not be null");
        }
        if (config == null) {
            throw Exceptions.invalidArgument("config must not be null");
        }
        this.chunkManager = chunkManager;
        this.config = config;
        this.readCache = new PageCache(maxCacheSize);
    }

    public PageManager(ChunkManager chunkManager, PageCapacityConfig config) {
        this(chunkManager, config, PageCache.DEFAULT_MAX_SIZE);
    }

    public Page getPage(SegmentLocation location) {
        return getPage(location, true);
    }

    public Page getPage(SegmentLocation location, boolean cacheData) {
        if (location == null) {
            return null;
        }

        String cacheKey = toCacheKey(location);
        Page cachedPage = readCache.get(cacheKey);
        if (cachedPage != null) {
            return cacheData ? cachedPage : new Page(cachedPage);
        }

        try {
            Chunk chunk = chunkManager.getChunk(location.getChunkId());
            if (chunk == null) {
                return null;
            }

            byte[] data = chunk.read(location);
            if (data == null || data.length == 0) {
                return null;
            }

            Page page = Page.deserialize(data, config);
            page.setLocation(location);
            if(cacheData) {
                readCache.put(cacheKey, page);
            }
            return page;
        } catch (IOException e) {
            throw StorageException.pageNotFound("Failed to read page at " + location).addContext("location", location);
        }
    }

    public CompletableFuture<SegmentLocation> savePageAsync(Page page) {
        if (page == null) {
            return CompletableFuture.failedFuture(
                new IllegalArgumentException("page must not be null"));
        }

        if (page.getLifecycle() != Page.PageLifecycle.FLUSHABLE) {
            return CompletableFuture.failedFuture(
                new IllegalArgumentException("page must be flushable"));
        }

        page.setLifecycle(Page.PageLifecycle.WRITING);

        byte[] data = page.toByteArray();

        CompletableFuture<SegmentLocation> future;
        if (page.isLeaf()) {
            future = chunkManager.writeLeafPageAsync(data);
        } else {
            future = chunkManager.writeIndexPageAsync(data);
        }

        return future.thenApply(location -> {
            String cacheKey = toCacheKey(location);
            readCache.put(cacheKey, page);
            page.setLifecycle(Page.PageLifecycle.CLEAN);
            page.setLocation(location);
            return location;
        }).exceptionally(throwable -> {
            page.setLifecycle(Page.PageLifecycle.FLUSHABLE);
            throw new RuntimeException("Failed to save page", throwable);
        });
    }

    public List<CompletableFuture<SegmentLocation>> savePagesAsync(List<Page> pages) {
        List<CompletableFuture<SegmentLocation>> futures = new ArrayList<>();
        for (Page page : pages) {
            futures.add(savePageAsync(page));
        }
        return futures;
    }

    public void flushAsyncWrites() throws IOException, InterruptedException {
        chunkManager.flushAsyncWrites();
    }

    public void invalidate(SegmentLocation location) {
        if (location != null) {
            readCache.remove(toCacheKey(location));
        }
    }

    public void clearCache() {
        readCache.clear();
    }

    public int getCacheSize() {
        return readCache.size();
    }

    private String toCacheKey(SegmentLocation location) {
        return location.getChunkId() + ":" + location.getOffset() + ":" + location.getLength();
    }

    public ChunkManager getChunkManager() {
        return chunkManager;
    }

    public PageCapacityConfig getCapacityConfig() {
        return config;
    }
}
