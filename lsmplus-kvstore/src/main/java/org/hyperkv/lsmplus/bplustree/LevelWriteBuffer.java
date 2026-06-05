package org.hyperkv.lsmplus.bplustree;

import org.hyperkv.lsmplus.api.model.IndexKey;
import org.hyperkv.lsmplus.bplustree.page.IndexPair;
import org.hyperkv.lsmplus.bplustree.page.Page;
import org.hyperkv.lsmplus.storage.SegmentLocation;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

import java.util.ArrayList;
import java.util.HashMap;
import java.util.HashSet;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import java.util.Set;

/**
 * A level-based write buffer for B+Tree page modifications.
 * 
 * <p>This buffer organizes pages by their tree level, enabling proper write ordering:
 * <ul>
 *   <li>Level 0: Leaf pages (bottom of tree)</li>
 *   <li>Level 1+: Index pages (higher levels toward root)</li>
 * </ul>
 * 
 * <p>Key design principles:
 * <ul>
 *   <li>Pages are buffered by level to delay persistence</li>
 *   <li>Flushing happens from bottom (level 0) to top (root level)</li>
 *   <li>Parent pages are only written AFTER all their children are persisted</li>
 *   <li>This ensures crash consistency - no dangling pointers in the tree</li>
 * </ul>
 * 
 * <p>Virtual locations for pending child references:
 * <ul>
 *   <li>When a child page splits, parent uses a virtual location (chunkId=0, offset=pageId)</li>
 *   <li>Virtual locations are stored directly in parent pages</li>
 *   <li>When children are persisted, virtual locations are resolved to real locations</li>
 *   <li>A page is flushable only when it has no virtual child references</li>
 * </ul>
 * 
 * <p>The flushable page detection uses the current key being processed.
 * A page is flushable when:
 * <ul>
 *   <li>Its maxKey is less than the current key (no more modifications)</li>
 *   <li>It has no virtual child references (all children persisted)</li>
 * </ul>
 * 
 * <p>Page ID scheme:
 * <ul>
 *   <li>Positive (1, 2, 3, ...): Leaf pages</li>
 *   <li>Negative (Long.MIN_VALUE, Long.MIN_VALUE+1, ...): Index pages</li>
 * </ul>
 */
public class LevelWriteBuffer {
    private static final Logger log = LoggerFactory.getLogger(LevelWriteBuffer.class);

    private final Map<Integer, LevelBuffer> levels;
    private final int maxSizePerLevel;
    private IndexKey currentKey;

    public LevelWriteBuffer(int maxSizePerLevel) {
        if (maxSizePerLevel <= 0) {
            throw new IllegalArgumentException("maxSizePerLevel must be positive");
        }
        this.maxSizePerLevel = maxSizePerLevel;
        this.levels = new LinkedHashMap<>();
    }

    public LevelWriteBuffer() {
        this(1000);
    }

    public synchronized void setCurrentKey(IndexKey key) {
        this.currentKey = key;
    }

    public synchronized IndexKey getCurrentKey() {
        return currentKey;
    }

    /**
     * Adds a page to the buffer at the specified level.
     * 
     * @param level the tree level (0 = leaf, higher = closer to root)
     * @param page the page to buffer
     */
    public synchronized void put(int level, Page page) {
        if (page == null) {
            return;
        }
        levels.computeIfAbsent(level, k -> new LevelBuffer(maxSizePerLevel))
              .put(page.getPageId(), page);
    }

    public synchronized Page get(int level, long pageId) {
        LevelBuffer levelBuffer = levels.get(level);
        if (levelBuffer == null) {
            return null;
        }
        return levelBuffer.get(pageId);
    }

    public synchronized boolean contains(int level, long pageId) {
        LevelBuffer levelBuffer = levels.get(level);
        if (levelBuffer == null) {
            return false;
        }
        return levelBuffer.contains(pageId);
    }

    public synchronized void setPageLocation(int level, long pageId, SegmentLocation location) {
        LevelBuffer levelBuffer = levels.get(level);
        if (levelBuffer != null && location != null) {
            levelBuffer.setPageLocation(pageId, location);
        }
    }

    public synchronized SegmentLocation getPageLocation(int level, long pageId) {
        LevelBuffer levelBuffer = levels.get(level);
        if (levelBuffer == null) {
            return null;
        }
        return levelBuffer.getPageLocation(pageId);
    }

    /**
     * Searches all levels for a page location by page ID.
     */
    public synchronized SegmentLocation findPageLocation(long pageId) {
        for (LevelBuffer levelBuffer : levels.values()) {
            SegmentLocation location = levelBuffer.getPageLocation(pageId);
            if (location != null) {
                return location;
            }
        }
        return null;
    }

    /**
     * Searches all levels for a page by page ID.
     */
    public synchronized Page findPage(long pageId) {
        for (LevelBuffer levelBuffer : levels.values()) {
            Page page = levelBuffer.get(pageId);
            if (page != null) {
                return page;
            }
        }
        return null;
    }

    /**
     * Gets page IDs that can be flushed at the specified level.
     * A page is flushable when:
     * 1. Its maxKey is less than the current key (no more modifications)
     * 2. It has no virtual child references (all children persisted)
     */
    public synchronized List<Long> getFlushablePageIds(int level) {
        LevelBuffer levelBuffer = levels.get(level);
        if (levelBuffer == null || currentKey == null) {
            return new ArrayList<>();
        }
        return levelBuffer.getFlushablePageIds(currentKey);
    }

    // provide current page counts in level x
    public synchronized int getPageCount(int level) {
        LevelBuffer levelBuffer = levels.get(level);
        return levelBuffer == null ? 0 : levelBuffer.size();
    }

    public synchronized Map<Long, Page> getPages(int level) {
        LevelBuffer levelBuffer = levels.get(level);
        if (levelBuffer == null) {
            return new HashMap<>();
        }
        return levelBuffer.getPages();
    }

    public synchronized void remove(int level, long pageId) {
        LevelBuffer levelBuffer = levels.get(level);
        if (levelBuffer != null) {
            levelBuffer.remove(pageId);
        }
    }

    public synchronized void clearLevel(int level) {
        LevelBuffer levelBuffer = levels.get(level);
        if (levelBuffer != null) {
            levelBuffer.clear();
        }
    }

    public synchronized void clearAll() {
        for (LevelBuffer levelBuffer : levels.values()) {
            levelBuffer.clear();
            levelBuffer.clearAllLocations();
        }
        currentKey = null;
    }

    public synchronized int getLevelCount() {
        return levels.size();
    }

    public synchronized int getTotalPageCount() {
        int count = 0;
        for (LevelBuffer levelBuffer : levels.values()) {
            count += levelBuffer.size();
        }
        return count;
    }

    public synchronized boolean isLevelEmpty(int level) {
        LevelBuffer levelBuffer = levels.get(level);
        return levelBuffer == null || levelBuffer.isEmpty();
    }

    public synchronized int getMaxLevel() {
        if (levels.isEmpty()) {
            return -1;
        }
        return levels.keySet().stream().max(Integer::compare).orElse(-1);
    }

    /**
     * Returns levels in flush order: lowest level first (leaves), then upward to root.
     * This ensures children are persisted before parents.
     */
    public synchronized List<Integer> getLevelsInFlushOrder() {
        List<Integer> sortedLevels = new ArrayList<>(levels.keySet());
        sortedLevels.sort(Integer::compare);
        return sortedLevels;
    }

    /**
     * Resolves virtual locations in parent pages after children are persisted.
     * 
     * <p>For each parent page at parentLevel, finds virtual locations pointing to
     * children at childLevel and replaces them with the real persisted locations.
     * 
     * @param parentLevel the level of parent pages to update
     * @param childLevel the level of child pages that were just persisted
     */
    public synchronized void resolveVirtualLocations(int parentLevel, int childLevel) {
        LevelBuffer parentBuffer = levels.get(parentLevel);
        LevelBuffer childBuffer = levels.get(childLevel);
        
        if (parentBuffer == null || childBuffer == null) {
            log.debug("Cannot resolve virtual locations: parentBuffer={}, childBuffer={}", parentBuffer, childBuffer);
            return;
        }
        
        Map<Long, Page> parents = parentBuffer.getPages();
        log.info("Resolving virtual locations for {} parent pages at level {} with {} child pages at level {}", 
            parents.size(), parentLevel, childBuffer.size(), childLevel);
        
        for (Page parent : parents.values()) {
            if (parent.isLeaf()) {
                continue;
            }
            
            // Check all entries for virtual locations
            for (IndexPair entry : parent.getAllEntries()) {
                if (entry instanceof IndexPair.LocationEntry le) {
                    SegmentLocation loc = le.location();
                    if (VirtualSegmentLocation.isVirtual(loc)) {
                        long childPageId = VirtualSegmentLocation.extractPageId(loc);
                        SegmentLocation realLocation = childBuffer.getPageLocation(childPageId);
                        if (realLocation != null) {
                            log.info("Resolving virtual location for child page {} to real location {}", childPageId, realLocation);
                            parent.put(le.key(), realLocation);
                        } else {
                            log.warn("Could not find real location for child page {} in child buffer", childPageId);
                        }
                    }
                }
            }
        }
    }

    /**
     * Updates the buffer to handle newly created split pages.
     * This method ensures that both the original page and the new split page
     * are properly tracked in the buffer with their correct maximum keys.
     * 
     * @param level the tree level where the split occurred
     * @param originalPageId the ID of the original page that was split
     * @param originalPage the updated original page (after split)
     * @param splitPageId the ID of the newly created split page
     * @param splitPage the new split page
     */
    public synchronized void handleSplitPages(int level, long originalPageId, Page originalPage, 
                                             long splitPageId, Page splitPage) {
        LevelBuffer buffer = levels.get(level);
        if (buffer == null) {
            return;
        }
        
        // Update the original page in the buffer
        buffer.put(originalPageId, originalPage);
        
        // Add the new split page to the buffer
        buffer.put(splitPageId, splitPage);
        
        // If the original page had a location set, the split page should inherit it
        SegmentLocation originalLocation = buffer.getPageLocation(originalPageId);
        if (originalLocation != null) {
            // Both pages will need to be persisted, so clear the location
            // to ensure they get new locations during flush
            buffer.setPageLocation(originalPageId, null);
            buffer.setPageLocation(splitPageId, null);
        }
    }
    
    /**
     * Checks if a page exists in the buffer at any level.
     * 
     * @param pageId the page ID to check
     * @return true if the page exists in the buffer
     */
    public synchronized boolean containsPage(long pageId) {
        for (LevelBuffer buffer : levels.values()) {
            if (buffer.contains(pageId)) {
                return true;
            }
        }
        return false;
    }
    
    /**
     * Gets all page IDs across all levels.
     * 
     * @return set of all page IDs in the buffer
     */
    public synchronized Set<Long> getAllPageIds() {
        Set<Long> allPageIds = new HashSet<>();
        for (LevelBuffer buffer : levels.values()) {
            allPageIds.addAll(buffer.getPageIds());
        }
        return allPageIds;
    }

    public SegmentLocation resolveVirtualLocation(SegmentLocation location, int level) {
        if (VirtualSegmentLocation.isVirtual(location)) {
            var realLocation = getPageLocation(level, VirtualSegmentLocation.extractPageId(location));
            if (realLocation == null || VirtualSegmentLocation.isVirtual(realLocation)) {
                throw new IllegalArgumentException("Unable to resolve virtual location" + location);
            }
            return realLocation;
        }
        return location;
    }

    /**
     * Internal buffer for a single tree level.
     */
    private static class LevelBuffer {
        private final Map<Long, Page> pages;
        private final Map<Long, IndexKey> pageMaxKeys;
        private final Map<Long, SegmentLocation> pageLocations;
        private final int maxSize;

        LevelBuffer(int maxSize) {
            this.maxSize = maxSize;
            this.pages = new LinkedHashMap<>();
            this.pageMaxKeys = new HashMap<>();
            this.pageLocations = new HashMap<>();
        }

        void put(long pageId, Page page) {
            var v = pages.putIfAbsent(pageId, page);
            if (v != null) {
                if (v != page) {
                    throw new IllegalArgumentException("Page ID " + pageId + " already exists");
                }
                return;
            }
            var maxKey = page.getMaxKey();
            if (maxKey != null) {
                pageMaxKeys.put(pageId, maxKey);
            }
        }

        Page get(long pageId) {
            return pages.get(pageId);
        }

        boolean contains(long pageId) {
            return pages.containsKey(pageId);
        }

        void setPageLocation(long pageId, SegmentLocation location) {
            pageLocations.put(pageId, location);
        }

        SegmentLocation getPageLocation(long pageId) {
            return pageLocations.get(pageId);
        }

        Set<Long> getPageIds() {
            return pages.keySet();
        }

        List<Long> getFlushablePageIds(IndexKey currentKey) {
            List<Long> flushable = new ArrayList<>();
            for (Map.Entry<Long, IndexKey> entry : pageMaxKeys.entrySet()) {
                IndexKey maxKey = entry.getValue();
                long pageId = entry.getKey();
                Page page = pages.get(pageId);
                
                if (maxKey != null && maxKey.compareTo(currentKey) < 0) {
                    if (!VirtualSegmentLocation.hasVirtualChildReferences(page)) {
                        flushable.add(pageId);
                    }
                }
            }
            return flushable;
        }

        Map<Long, Page> getPages() {
            return new HashMap<>(pages);
        }

        void remove(long pageId) {
            pages.remove(pageId);
            pageMaxKeys.remove(pageId);
        }

        void clear() {
            pages.clear();
            pageMaxKeys.clear();
        }

        int size() {
            return pages.size();
        }

        boolean isEmpty() {
            return pages.isEmpty();
        }

        public void clearAllLocations() {
            pageLocations.clear();
        }
    }
}
