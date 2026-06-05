package org.hyperkv.lsmplus.bplustree;

import org.hyperkv.lsmplus.api.model.IndexKey;
import org.hyperkv.lsmplus.bplustree.page.Page;
import org.hyperkv.lsmplus.storage.SegmentLocation;

import java.util.ArrayList;
import java.util.HashMap;
import java.util.List;
import java.util.Map;

public class WriteBuffer {

    public static final int DEFAULT_MAX_SIZE = 1000;

    private final Map<Integer, Page> pages;
    private final Map<Integer, IndexKey> pageMaxKeys;
    private final Map<Integer, SegmentLocation> pageLocations;
    private final int maxSize;
    private IndexKey currentKey;

    public WriteBuffer(int maxSize) {
        if (maxSize <= 0) {
            throw new IllegalArgumentException("maxSize must be positive");
        }
        this.maxSize = maxSize;
        this.pages = new HashMap<>();
        this.pageMaxKeys = new HashMap<>();
        this.pageLocations = new HashMap<>();
        this.currentKey = null;
    }

    public WriteBuffer() {
        this(DEFAULT_MAX_SIZE);
    }

    public synchronized void put(int pageId, Page page) {
        if (pageId < 0 || page == null) {
            return;
        }
        pages.put(pageId, page);
    }

    public synchronized void put(int pageId, Page page, IndexKey maxKey) {
        if (pageId < 0 || page == null) {
            return;
        }
        pages.put(pageId, page);
        if (maxKey != null) {
            pageMaxKeys.put(pageId, maxKey);
        }
    }

    public synchronized Page get(int pageId) {
        return pages.get(pageId);
    }

    public synchronized boolean contains(int pageId) {
        return pages.containsKey(pageId);
    }

    public synchronized void remove(int pageId) {
        pages.remove(pageId);
        pageMaxKeys.remove(pageId);
        pageLocations.remove(pageId);
    }

    public synchronized void setCurrentKey(IndexKey key) {
        this.currentKey = key;
    }

    public synchronized IndexKey getCurrentKey() {
        return currentKey;
    }

    public synchronized List<Integer> getFlushablePageIds() {
        List<Integer> flushable = new ArrayList<>();
        if (currentKey == null) {
            return flushable;
        }

        for (Map.Entry<Integer, IndexKey> entry : pageMaxKeys.entrySet()) {
            IndexKey maxKey = entry.getValue();
            if (maxKey != null && maxKey.compareTo(currentKey) < 0) {
                flushable.add(entry.getKey());
            }
        }
        return flushable;
    }

    public synchronized List<Page> getFlushablePages() {
        List<Page> flushable = new ArrayList<>();
        for (Integer pageId : getFlushablePageIds()) {
            Page page = pages.get(pageId);
            if (page != null) {
                flushable.add(page);
            }
        }
        return flushable;
    }

    public synchronized void setPageLocation(int pageId, SegmentLocation location) {
        if (location != null) {
            pageLocations.put(pageId, location);
        }
    }

    public synchronized SegmentLocation getPageLocation(int pageId) {
        return pageLocations.get(pageId);
    }

    public synchronized void clear() {
        pages.clear();
        pageMaxKeys.clear();
        pageLocations.clear();
        currentKey = null;
    }

    public synchronized boolean isFull() {
        return pages.size() >= maxSize;
    }

    public synchronized int size() {
        return pages.size();
    }

    public synchronized boolean isEmpty() {
        return pages.isEmpty();
    }

    public int getMaxSize() {
        return maxSize;
    }

    public synchronized IndexKey getPageMaxKey(int pageId) {
        return pageMaxKeys.get(pageId);
    }

    public synchronized void updatePageMaxKey(int pageId, IndexKey maxKey) {
        if (maxKey != null) {
            pageMaxKeys.put(pageId, maxKey);
        }
    }

    public synchronized Map<Integer, Page> getPages() {
        return new HashMap<>(pages);
    }

    public synchronized Map<Integer, SegmentLocation> getPageLocations() {
        return new HashMap<>(pageLocations);
    }
}
