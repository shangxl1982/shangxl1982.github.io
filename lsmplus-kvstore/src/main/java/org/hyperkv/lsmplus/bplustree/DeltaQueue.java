package org.hyperkv.lsmplus.bplustree;

import org.hyperkv.lsmplus.storage.SegmentLocation;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

import java.util.*;
import java.util.concurrent.ConcurrentHashMap;
import java.util.concurrent.ConcurrentSkipListMap;

/**
 * Manages pending change deltas grouped by page location.
 * 
 * <p>This implements the PALM-style batch processing approach:
 * <ul>
 *   <li>Deltas are grouped by page location</li>
 *   <li>Multiple deltas to the same page are batched together</li>
 *   <li>Processing happens level-by-level (bottom-up)</li>
 * </ul>
 * 
 * <p>Key design principles:
 * <ul>
 *   <li>SegmentLocation is Comparable, enabling efficient grouping in sorted maps</li>
 *   <li>Level tracking enables bottom-up processing order</li>
 *   <li>Batching reduces the number of page modifications</li>
 * </ul>
 */
public final class DeltaQueue {
    
    private final Map<SegmentLocation, List<ChangeDelta>> deltasByLocation;
    private final NavigableMap<Integer, Set<SegmentLocation>> locationsByLevel;

    private static final Logger log = LoggerFactory.getLogger(DeltaQueue.class);

    /**
     * Creates a new DeltaQueue (non-thread-safe by default).
     */
    public DeltaQueue() {
        this(false);
    }
    
    /**
     * Creates a new DeltaQueue with optional thread safety.
     * 
     * @param threadSafe if true, uses concurrent data structures
     */
    public DeltaQueue(boolean threadSafe) {
        if (threadSafe) {
            this.deltasByLocation = new ConcurrentHashMap<>();
            this.locationsByLevel = new ConcurrentSkipListMap<>();
        } else {
            this.deltasByLocation = new TreeMap<>();
            this.locationsByLevel = new TreeMap<>();
        }
    }
    
    /**
     * Adds a delta for a page.
     * 
     * @param location the location of the page
     * @param delta the change delta to apply
     * @param level the tree level of the page
     */
    public void addDelta(SegmentLocation location, ChangeDelta delta, int level) {
        if (location == null) {
            throw new IllegalArgumentException("location must not be null");
        }
        if (delta == null) {
            throw new IllegalArgumentException("delta must not be null");
        }
        
        log.debug("addDelta: {} {} {}", location, delta, level);

        deltasByLocation
            .computeIfAbsent(location, k -> new ArrayList<>())
            .add(delta);
        
        locationsByLevel
            .computeIfAbsent(level, k -> ConcurrentHashMap.newKeySet())
            .add(location);
    }
    
    /**
     * Gets all deltas for a specific page location.
     * 
     * @param location the page location
     * @return list of deltas, or empty list if none
     */
    public List<ChangeDelta> getDeltasForLocation(SegmentLocation location) {
        if (location == null) {
            return Collections.emptyList();
        }
        return deltasByLocation.getOrDefault(location, Collections.emptyList());
    }
    
    /**
     * Gets all page locations at a specific level.
     * 
     * @param level the tree level
     * @return set of page locations at this level, or empty set if none
     */
    public List<SegmentLocation> getSortedLocationsAtLevel(int level) {
        // need to sort locations by one of the change delta keys
        List<SegmentLocation> result = new ArrayList<>(locationsByLevel.get(level));
                result.sort((l1, l2) ->
                        deltasByLocation.get(l1).getFirst().getTargetKey()
                                .compareTo(deltasByLocation.get(l2).getFirst().getTargetKey()));
        return result;
    }
    
    /**
     * Gets all levels that have pending deltas.
     * 
     * @return sorted set of levels (bottom-up order)
     */
    public Set<Integer> getLevels() {
        return locationsByLevel.keySet();
    }
    
    /**
     * Removes all deltas for a specific page.
     * 
     * @param location the page location
     */
    public void removeDeltasForLocation(SegmentLocation location) {
        if (location == null) {
            return;
        }
        deltasByLocation.remove(location);
        
        locationsByLevel.values().forEach(set -> set.remove(location));
    }
    
    /**
     * Clears all deltas for a specific level.
     * 
     * @param level the tree level
     */
    public void clearLevel(int level) {
        Set<SegmentLocation> locations = locationsByLevel.remove(level);
        if (locations != null) {
            locations.forEach(deltasByLocation::remove);
        }
    }
    
    /**
     * Clears all deltas.
     */
    public void clear() {
        deltasByLocation.clear();
        locationsByLevel.clear();
    }
    
    /**
     * Returns the total number of pending deltas.
     * 
     * @return total delta count
     */
    public int getTotalDeltaCount() {
        return deltasByLocation.values()
            .stream()
            .mapToInt(List::size)
            .sum();
    }
    
    /**
     * Returns the number of pages with pending deltas.
     * 
     * @return page count
     */
    public int getPageCount() {
        return deltasByLocation.size();
    }
    
    /**
     * Checks if there are any pending deltas.
     * 
     * @return true if no pending deltas
     */
    public boolean isEmpty() {
        return deltasByLocation.isEmpty();
    }
    
    /**
     * Checks if there are pending deltas at a specific level.
     * 
     * @param level the tree level
     * @return true if there are deltas at this level
     */
    public boolean hasDeltasAtLevel(int level) {
        Set<SegmentLocation> locations = locationsByLevel.get(level);
        return locations != null && !locations.isEmpty();
    }
    
    @Override
    public String toString() {
        return String.format("DeltaQueue{pages=%d, deltas=%d, levels=%s}",
            getPageCount(), getTotalDeltaCount(), getLevels());
    }
}
