package org.hyperkv.lsmplus.bplustree;

import org.hyperkv.lsmplus.bplustree.page.Page;

/**
 * Manages monotonic page ID allocation with separate sequences for leaf and index pages.
 * 
 * <p>Page ID allocation strategy:
 * <ul>
 *   <li>Leaf pages: positive IDs starting from 1 (1, 2, 3, ...)</li>
 *   <li>Index pages: negative IDs starting from Long.MIN_VALUE (Long.MIN_VALUE, Long.MIN_VALUE+1, ...)</li>
 *   <li>0 is reserved as the invalid/null page ID sentinel value</li>
 * </ul>
 * 
 * <p>This prevents page ID collisions and integer overflow by using separate sequences
 * and 64-bit integers for page IDs.
 */
public class PageIdManager {
    
    private long nextLeafPageId;
    private long nextIndexPageId;
    
    /**
     * Creates a new PageIdManager starting from initial values.
     * 
     * @param initialLeafPageId the starting leaf page ID (typically 1 for new trees)
     * @param initialIndexPageId the starting index page ID (typically Long.MIN_VALUE for new trees)
     */
    public PageIdManager(long initialLeafPageId, long initialIndexPageId) {
        if (initialLeafPageId <= 0) {
            throw new IllegalArgumentException("Initial leaf page ID must be positive: " + initialLeafPageId);
        }
        if (initialIndexPageId >= 0) {
            throw new IllegalArgumentException("Initial index page ID must be negative: " + initialIndexPageId);
        }
        this.nextLeafPageId = initialLeafPageId;
        this.nextIndexPageId = initialIndexPageId;
    }
    
    /**
     * Creates a new PageIdManager for a new tree with default starting values.
     */
    public PageIdManager() {
        this(1L, Long.MIN_VALUE);
    }
    
    /**
     * Allocates the next available leaf page ID.
     * 
     * @return the next leaf page ID (positive integer)
     * @throws IllegalStateException if leaf page ID sequence would overflow
     */
    public synchronized long allocateLeafPageId() {
        if (nextLeafPageId <= 0) {
            throw new IllegalStateException("Leaf page ID sequence overflow");
        }
        return nextLeafPageId++;
    }

    public synchronized long allocatePageId(Page.PageType pageType) {
        if (pageType == Page.PageType.LEAF) {
            return allocateLeafPageId();
        } else {
            return allocateIndexPageId();
        }
    }

    /**
     * Allocates the next available index page ID.
     * 
     * @return the next index page ID (negative integer)
     * @throws IllegalStateException if index page ID sequence would overflow
     */
    public synchronized long allocateIndexPageId() {
        if (nextIndexPageId >= 0) {
            throw new IllegalStateException("Index page ID sequence overflow");
        }
        return nextIndexPageId++;
    }
    
    /**
     * Gets the next leaf page ID without allocating it.
     * 
     * @return the next available leaf page ID
     */
    public synchronized long peekNextLeafPageId() {
        return nextLeafPageId;
    }
    
    /**
     * Gets the next index page ID without allocating it.
     * 
     * @return the next available index page ID
     */
    public synchronized long peekNextIndexPageId() {
        return nextIndexPageId;
    }
    
    /**
     * Updates the page ID sequences to start from the given values.
     * Used when loading a persisted tree to continue from where it left off.
     * 
     * @param maxLeafPageId the maximum leaf page ID that has been allocated
     * @param minIndexPageId the minimum index page ID that has been allocated
     */
    public synchronized void updateSequences(long maxLeafPageId, long minIndexPageId) {
        if (maxLeafPageId <= 0) {
            throw new IllegalArgumentException("Max leaf page ID must be positive: " + maxLeafPageId);
        }
        if (minIndexPageId >= 0) {
            throw new IllegalArgumentException("Min index page ID must be negative: " + minIndexPageId);
        }
        
        // Set next IDs to continue from the maximum values
        this.nextLeafPageId = maxLeafPageId + 1;
        this.nextIndexPageId = minIndexPageId + 1;
        
        // Validate that we're not going to overflow
        if (nextLeafPageId <= 0) {
            throw new IllegalStateException("Leaf page ID sequence would overflow: " + nextLeafPageId);
        }
        if (nextIndexPageId >= 0) {
            throw new IllegalStateException("Index page ID sequence would overflow: " + nextIndexPageId);
        }
    }
    
    /**
     * Validates that a page ID is valid for its type.
     * 
     * @param pageId the page ID to validate
     * @param isLeaf true if this should be a leaf page ID, false for index page ID
     * @throws IllegalArgumentException if the page ID is invalid for the given type
     */
    public static void validatePageId(long pageId, boolean isLeaf) {
        if (pageId == 0) {
            throw new IllegalArgumentException("Page ID 0 is reserved for invalid/null");
        }
        if (isLeaf && pageId <= 0) {
            throw new IllegalArgumentException("Leaf page ID must be positive: " + pageId);
        }
        if (!isLeaf && pageId >= 0) {
            throw new IllegalArgumentException("Index page ID must be negative: " + pageId);
        }
    }
    
    /**
     * Determines if a page ID is for a leaf page.
     * 
     * @param pageId the page ID to check
     * @return true if this is a leaf page ID (positive), false if index page ID (negative)
     */
    public static boolean isLeafPageId(long pageId) {
        return pageId > 0;
    }
    
    /**
     * Determines if a page ID is for an index page.
     * 
     * @param pageId the page ID to check
     * @return true if this is an index page ID (negative), false if leaf page ID (positive)
     */
    public static boolean isIndexPageId(long pageId) {
        return pageId < 0;
    }
    
    /**
     * Gets the current state for persistence.
     * 
     * @return an array containing [maxLeafPageId, minIndexPageId]
     */
    public synchronized long[] getStateForPersistence() {
        return new long[]{
            nextLeafPageId - 1,  // max allocated leaf page ID
            nextIndexPageId - 1  // min allocated index page ID
        };
    }
    
    @Override
    public String toString() {
        return "PageIdManager{nextLeafPageId=" + nextLeafPageId + 
               ", nextIndexPageId=" + nextIndexPageId + '}';
    }
}