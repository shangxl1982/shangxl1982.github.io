package org.hyperkv.lsmplus.bplustree;

import org.hyperkv.lsmplus.storage.SegmentLocation;

import java.util.HashMap;
import java.util.Map;
import java.util.Set;

/**
 * Manages bidirectional mappings between page IDs and segment locations.
 * 
 * <p>This class provides a centralized location management system for B+Tree pages:
 * <ul>
 *   <li>oldLocation -> pageId: Find a page by its loaded location (where it was read from)</li>
 *   <li>pageId -> newLocation: Track where a page is written (its new disk location)</li>
 * </ul>
 * 
 * <p>Key design principles:
 * <ul>
 *   <li>Page ID is the primary identifier - all queries should use pageId when possible</li>
 *   <li>Thread-safe: all operations are synchronized</li>
 *   <li>Consistency: ensures bidirectional mappings stay in sync</li>
 *   <li>Validation: prevents invalid states (e.g., duplicate mappings)</li>
 * </ul>
 * 
 * <p>Usage scenarios:
 * <ul>
 *   <li>WriteBuffer: Track pages being modified and their new locations</li>
 *   <li>LevelWriteBuffer: Manage location mappings per tree level</li>
 *   <li>TreeDumper: Track page relocations during flush operations</li>
 *   <li>PageCache: Map loaded locations to page IDs</li>
 * </ul>
 */
public class PageLocationMapper {
    
    private final Map<SegmentLocation, Long> loadedLocationToPageId;
    private final Map<Long, SegmentLocation> pageIdToNewLocation;
    
    public PageLocationMapper() {
        this.loadedLocationToPageId = new HashMap<>();
        this.pageIdToNewLocation = new HashMap<>();
    }
    
    /**
     * Adds a complete mapping for a page.
     * 
     * @param pageId the page ID (must not be 0)
     * @param loadedLocation the location where the page was loaded from (can be null for new pages)
     * @throws IllegalArgumentException if pageId is 0
     */
    public synchronized void addMappingForLoadedLocation(long pageId, SegmentLocation loadedLocation) {
        validatePageId(pageId);
        
        if (loadedLocation != null) {
            Long existingPageId = loadedLocationToPageId.get(loadedLocation);
            if (existingPageId != null && existingPageId != pageId) {
                throw new IllegalArgumentException(
                    "Old location " + loadedLocation + " is already mapped to page " + existingPageId);
            }
            loadedLocationToPageId.put(loadedLocation, pageId);
        }
    }
    
    /**
     * Gets the page ID for a given old location (where the page was loaded from).
     * 
     * @param loadedLocation the old location to look up
     * @return the page ID, or null if not found
     */
    public synchronized Long getPageIdForLoadedLocation(SegmentLocation loadedLocation) {
        if (loadedLocation == null) {
            return null;
        }
        return loadedLocationToPageId.get(loadedLocation);
    }
    
    /**
     * Gets the new location for a given page ID.
     * 
     * @param pageId the page ID
     * @return the new location, or null if not set
     */
    public synchronized SegmentLocation getNewLocation(long pageId) {
        return pageIdToNewLocation.get(pageId);
    }
    
    /**
     * Updates the new location for a page.
     * 
     * @param pageId the page ID
     * @param newLocation the new location
     * @throws IllegalArgumentException if pageId is 0
     */
    public synchronized void addMappingForNewLocation(long pageId, SegmentLocation newLocation) {
        validatePageId(pageId);
        if (newLocation != null) {
            pageIdToNewLocation.put(pageId, newLocation);
        }
    }

    /**
     * Clears all mappings.
     */
    public synchronized void clear() {
        loadedLocationToPageId.clear();
        pageIdToNewLocation.clear();
    }
    


    /**
     * Validates that a page ID is valid (non-zero).
     * 
     * @param pageId the page ID to validate
     * @throws IllegalArgumentException if pageId is 0
     */
    private void validatePageId(long pageId) {
        if (pageId == 0) {
            throw new IllegalArgumentException("Page ID 0 is reserved for invalid/null");
        }
    }
    
    @Override
    public synchronized String toString() {
        return "PageLocationMapper{" +
               "oldLocationMappings=" + loadedLocationToPageId.size() +
               ", newLocationMappings=" + pageIdToNewLocation.size() +
               '}';
    }
}
