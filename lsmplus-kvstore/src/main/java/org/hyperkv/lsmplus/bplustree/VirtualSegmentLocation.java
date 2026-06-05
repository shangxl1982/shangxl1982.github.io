package org.hyperkv.lsmplus.bplustree;

import org.hyperkv.lsmplus.bplustree.page.IndexPair;
import org.hyperkv.lsmplus.bplustree.page.Page;
import org.hyperkv.lsmplus.storage.SegmentLocation;

import java.util.UUID;

/**
 * Utility class for creating and identifying virtual segment locations.
 * 
 * <p>Virtual locations are used to track pending child references in parent pages
 * before the actual disk location is known. They use a special format:
 * <ul>
 *   <li>chunkId = 0-0-0-0... (UUID with all bits set to 0)</li>
 *   <li>offset = pageId MSB 4 bytes (can be positive for leaf pages or negative for index pages)</li>
 *   <li>length = pageId LSB 4 bytes</li>
 * </ul>
 * 
 * <p>Virtual locations are resolved to real disk locations during the flush process
 * when the child page is persisted to disk.
 */
public final class VirtualSegmentLocation {
    
    /**
     * UUID representing a virtual chunk (all bits set to 0).
     */
    public static final UUID VIRTUAL_CHUNK_ID = new UUID(0L, 0L);
    
    /**
     * Creates a virtual segment location for a given page ID.
     * 
     * <p>Encoding scheme:
     * <ul>
     *   <li>offset = pageId (full 64-bit value)</li>
     *   <li>length = 0 (indicates this is a virtual location)</li>
     * </ul>
     * 
     * @param pageId the page ID (positive for leaf, negative for index)
     * @return a SegmentLocation with virtual chunk ID and page ID in offset field
     */
    public static SegmentLocation create(long pageId) {
        // Store the full page ID in the offset field
        // Use length = 0 to indicate this is a virtual location
        return new SegmentLocation(VIRTUAL_CHUNK_ID, pageId, 0);
    }
    
    /**
     * Checks if a segment location is virtual.
     * 
     * @param location the location to check
     * @return true if the location is virtual (chunk ID is all zeros)
     */
    public static boolean isVirtual(SegmentLocation location) {
        return location != null && 
               location.getChunkId().equals(VIRTUAL_CHUNK_ID);
    }
    
    /**
     * Extracts the page ID from a virtual segment location.
     * 
     * @param location the virtual location
     * @return the page ID from the offset field
     * @throws IllegalArgumentException if the location is not virtual
     */
    public static long extractPageId(SegmentLocation location) {
        if (!isVirtual(location)) {
            throw new IllegalArgumentException("Location is not virtual: " + location);
        }
        // The page ID is stored directly in the offset field
        return location.getOffset();
    }
    
    /**
     * Resolves a virtual location to a real disk location.
     * 
     * @param virtualLocation the virtual location to resolve
     * @param realLocation the actual disk location
     * @return a new SegmentLocation with the real chunk ID and offset
     * @throws IllegalArgumentException if the virtualLocation is not virtual
     */
    public static SegmentLocation resolve(SegmentLocation virtualLocation, SegmentLocation realLocation) {
        if (!isVirtual(virtualLocation)) {
            throw new IllegalArgumentException("First location must be virtual: " + virtualLocation);
        }
        if (realLocation == null) {
            throw new IllegalArgumentException("Real location must not be null");
        }
        
        return new SegmentLocation(
            realLocation.getChunkId(),
            realLocation.getOffset(),
            realLocation.getLength()
        );
    }
    
    /**
     * Creates a real segment location from chunk ID and offset.
     * 
     * @param chunkId the actual chunk UUID
     * @param offset the byte offset in the chunk
     * @param length the data length
     * @return a real SegmentLocation
     */
    public static SegmentLocation createReal(UUID chunkId, long offset, int length) {
        return new SegmentLocation(chunkId, offset, length);
    }
    
    /**
     * Validates that a page ID is valid for virtual location creation.
     * 
     * @param pageId the page ID to validate
     * @throws IllegalArgumentException if pageId is 0 (reserved for invalid/null)
     */
    public static void validatePageIdForVirtualLocation(long pageId) {
        if (pageId == 0) {
            throw new IllegalArgumentException("Page ID 0 is reserved for invalid/null and cannot be used for virtual locations");
        }
    }
    
    /**
     * Creates a virtual location for a leaf page.
     * 
     * @param pageId the leaf page ID (must be positive)
     * @return a virtual SegmentLocation
     * @throws IllegalArgumentException if pageId is not positive
     */
    public static SegmentLocation createForLeaf(long pageId) {
        if (pageId <= 0) {
            throw new IllegalArgumentException("Leaf page ID must be positive: " + pageId);
        }
        validatePageIdForVirtualLocation(pageId);
        return create(pageId);
    }
    
    /**
     * Creates a virtual location for an index page.
     * 
     * @param pageId the index page ID (must be negative)
     * @return a virtual SegmentLocation
     * @throws IllegalArgumentException if pageId is not negative
     */
    public static SegmentLocation createForIndex(long pageId) {
        if (pageId >= 0) {
            throw new IllegalArgumentException("Index page ID must be negative: " + pageId);
        }
        validatePageIdForVirtualLocation(pageId);
        return create(pageId);
    }
    
    /**
     * Determines if a virtual location points to a leaf page.
     * 
     * @param location the virtual location
     * @return true if the location points to a leaf page (positive page ID)
     * @throws IllegalArgumentException if the location is not virtual
     */
    public static boolean isLeafVirtualLocation(SegmentLocation location) {
        long pageId = extractPageId(location);
        return pageId > 0;
    }
    
    /**
     * Determines if a virtual location points to an index page.
     * 
     * @param location the virtual location
     * @return true if the location points to an index page (negative page ID)
     * @throws IllegalArgumentException if the location is not virtual
     */
    public static boolean isIndexVirtualLocation(SegmentLocation location) {
        long pageId = extractPageId(location);
        return pageId < 0;
    }
    
    /**
     * Checks if a page has any virtual child references.
     * 
     * @param page the page to check
     * @return true if the page contains any virtual locations
     */
    public static boolean hasVirtualChildReferences(Page page) {
        if (page == null || page.isLeaf()) {
            return false;
        }
        
        for (IndexPair entry : page.getAllEntries()) {
            if (entry instanceof IndexPair.LocationEntry le) {
                SegmentLocation location = le.location();
                if (location != null && isVirtual(location)) {
                    return true;
                }
            }
        }
        
        return false;
    }
    
    private VirtualSegmentLocation() {
        throw new AssertionError("VirtualSegmentLocation is a utility class and cannot be instantiated");
    }
}