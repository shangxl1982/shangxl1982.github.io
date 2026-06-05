# PageLocationMapper Usage Guide

## Overview

`PageLocationMapper` is a centralized location management system for B+Tree pages. It provides bidirectional mappings between page IDs and segment locations, solving the confusion of having multiple parameters and scattered location tracking across different components.

## Key Features

- **Bidirectional Mappings**:
  - `oldLocation → pageId`: Find a page by where it was loaded from
  - `pageId → newLocation`: Track where a page will be written

- **Thread-Safe**: All operations are synchronized for concurrent access

- **Validation**: Prevents invalid states (e.g., duplicate mappings, invalid page IDs)

- **Single Source of Truth**: Centralizes all location mappings in one place

## Basic Usage

### Creating a Mapper

```java
PageLocationMapper mapper = new PageLocationMapper();
```

### Adding Mappings

```java
// Complete mapping: page loaded from oldLocation, will be written to newLocation
mapper.addMapping(pageId, oldLocation, newLocation);

// Only old location (page was loaded but not yet written)
mapper.addMapping(pageId, oldLocation, null);

// Only new location (new page, not loaded from disk)
mapper.addMapping(pageId, null, newLocation);
```

### Querying Mappings

```java
// Get page ID from old location
Long pageId = mapper.getPageId(oldLocation);

// Get new location from page ID
SegmentLocation newLocation = mapper.getNewLocation(pageId);

// Check if mappings exist
boolean hasNew = mapper.hasNewLocation(pageId);
boolean hasOld = mapper.hasOldLocation(oldLocation);
```

### Updating Mappings

```java
// Update the new location for a page
mapper.updateNewLocation(pageId, newLocation);
```

### Removing Mappings

```java
// Remove all mappings for a page
mapper.removeMapping(pageId);

// Remove only the old location mapping
mapper.removeOldLocation(oldLocation);

// Clear all mappings
mapper.clear();
```

## Integration Points

### 1. LevelWriteBuffer Integration

**Current Implementation** ([LevelWriteBuffer.java:464-465](file:///home/wisefox/git/hyperkvstore/lsmplus-kvstore/src/main/java/org/hyperkv/lsmplus/bplustree/LevelWriteBuffer.java#L464-L465)):
```java
private final Map<Long, SegmentLocation> pageLocations;
private final Map<SegmentLocation, Long> loadedLocations;
```

**Recommended Refactor**:
```java
private final PageLocationMapper locationMapper;

// Replace:
SegmentLocation loc = pageLocations.get(pageId);
// With:
SegmentLocation loc = locationMapper.getNewLocation(pageId);

// Replace:
Long pageId = loadedLocations.get(location);
// With:
Long pageId = locationMapper.getPageId(location);
```

### 2. WriteBuffer Integration

**Current Implementation** ([WriteBuffer.java:18](file:///home/wisefox/git/hyperkvstore/lsmplus-kvstore/src/main/java/org/hyperkv/lsmplus/bplustree/WriteBuffer.java#L18)):
```java
private final Map<Integer, SegmentLocation> pageLocations;
```

**Recommended Refactor**:
```java
private final PageLocationMapper locationMapper;

// Replace:
pageLocations.put(pageId, location);
// With:
locationMapper.updateNewLocation(pageId, location);
```

### 3. TreeDumper Integration

**Current Usage** ([TreeDumper.java:1055](file:///home/wisefox/git/hyperkvstore/lsmplus-kvstore/src/main/java/org/hyperkv/lsmplus/bplustree/TreeDumper.java#L1055)):
```java
writeBuffer.setPageLocation(level, pageIds.get(i), location);
```

**Recommended Refactor**:
```java
// In LevelWriteBuffer, delegate to PageLocationMapper
public void setPageLocation(int level, long pageId, SegmentLocation location) {
    locationMapper.updateNewLocation(pageId, location);
}
```

### 4. PageCache Integration

**Potential Usage**:
```java
public class PageCache {
    private final PageLocationMapper locationMapper;
    
    public Page get(SegmentLocation location) {
        Long pageId = locationMapper.getPageId(location);
        if (pageId != null) {
            return getPageById(pageId);
        }
        // Load from disk...
    }
}
```

## Migration Strategy

### Phase 1: Add PageLocationMapper to Components

1. Add `PageLocationMapper` as a field in `LevelWriteBuffer`, `WriteBuffer`, etc.
2. Keep existing maps for backward compatibility
3. Update new code to use `PageLocationMapper`

### Phase 2: Refactor Existing Code

1. Replace direct map access with `PageLocationMapper` methods
2. Update all tests to verify behavior
3. Remove deprecated map fields

### Phase 3: Complete Migration

1. Remove all redundant location tracking maps
2. Ensure all components use `PageLocationMapper` consistently
3. Update documentation

## Benefits

1. **Clarity**: Single parameter (pageId) instead of multiple location parameters
2. **Consistency**: All components use the same location tracking mechanism
3. **Maintainability**: Easier to debug and test location-related issues
4. **Extensibility**: Easy to add new features (validation, statistics, persistence)

## API Reference

### Core Methods

| Method | Description |
|--------|-------------|
| `addMapping(pageId, oldLocation, newLocation)` | Add complete mapping |
| `getPageId(oldLocation)` | Get page ID from old location |
| `getNewLocation(pageId)` | Get new location from page ID |
| `updateNewLocation(pageId, newLocation)` | Update new location |
| `removeMapping(pageId)` | Remove all mappings for a page |
| `clear()` | Clear all mappings |

### Query Methods

| Method | Description |
|--------|-------------|
| `hasNewLocation(pageId)` | Check if page has new location |
| `hasOldLocation(location)` | Check if location is mapped |
| `isEmpty()` | Check if mapper is empty |
| `getNewLocationCount()` | Count of new location mappings |
| `getOldLocationCount()` | Count of old location mappings |

### Bulk Operations

| Method | Description |
|--------|-------------|
| `getPageIdsWithNewLocations()` | Get all page IDs with new locations |
| `getAllOldLocations()` | Get all old locations |
| `getAllNewLocations()` | Get all new location mappings |
| `getAllOldLocationMappings()` | Get all old location mappings |

## Testing

Comprehensive tests are available in [PageLocationMapperTest.java](file:///home/wisefox/git/hyperkvstore/lsmplus-kvstore/src/test/java/org/hyperkv/lsmplus/bplustree/PageLocationMapperTest.java).

Run tests with:
```bash
./gradlew :lsmplus-kvstore:test --tests PageLocationMapperTest
```

## Thread Safety

All public methods are synchronized, making `PageLocationMapper` safe for concurrent access from multiple threads. The concurrent access test verifies this behavior under load.

## Performance Considerations

- Uses `HashMap` for O(1) average case lookups
- Synchronized methods may cause contention under high concurrency
- Consider using `ConcurrentHashMap` if performance becomes an issue
- Snapshot methods (e.g., `getAllNewLocations()`) create copies to avoid concurrent modification

## Future Enhancements

Potential improvements:
- Add validation for location consistency
- Support for persistence/recovery
- Statistics and monitoring
- Event listeners for mapping changes
- Batch operations for better performance
