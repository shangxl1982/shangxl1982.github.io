# Occupancy Tracking Integration - 2026-05-08

## Summary

Successfully integrated the occupancy tracking mechanism into the tree dump function as specified in design/design-gc.md. The system now tracks chunk occupancy changes during tree dumps and persists them for GC decision-making.

## Changes Made

### 1. Created OccupancyManager Class
**File:** `lsmplus-kvstore/src/main/java/org/hyperkv/lsmplus/gc/OccupancyManager.java`

**Purpose:** Coordinates occupancy tracking during tree dumps

**Key Features:**
- Tracks page writes and decommissions per dump
- Calculates aligned sizes using WriteItem.ALIGNMENT (4096 bytes)
- Persists occupancy records to `{basePath}/occupancy/{version}.pb`
- Integrates with MNSTracker for MNS tracking
- Thread-safe delta accumulation

**Key Methods:**
- `startDump(version)`: Initialize tracking for a new dump
- `recordPageWrite(chunkId, alignedSize)`: Track new page writes
- `recordPageDecommission(chunkId, alignedSize)`: Track page replacements
- `finishDump()`: Persist occupancy record and update MNS
- `loadOccupancyRecord(version)`: Load historical occupancy data

### 2. Updated TreeDumper Class
**File:** `lsmplus-kvstore/src/main/java/org/hyperkv/lsmplus/bplustree/TreeDumper.java`

**Changes:**
- Added `OccupancyManager` field and constructor parameter
- Added `trackPageWrite()` helper method to calculate aligned sizes
- Added `trackPageDecommission()` helper method
- Updated `dump()` method to call `startDump()` and `finishDump()`
- Updated all page save locations to track occupancy:
  - `buildNewTree()`: Track leaf and index page writes
  - `buildIndexLevels()`: Track index page writes
  - `flushLevelPages()`: Track flushed pages
  - `flushAllLevels()`: Track all flushed pages

**Occupancy Tracking Points:**
1. New tree builds (buildNewTree): Track leaf and index page writes
2. Index level construction (buildIndexLevels): Track index page writes
3. Page flushes during updates (flushLevelPages, flushAllLevels): Track both writes and decommissions
4. **Decommission tracking**: When pages with existing real locations are flushed to new locations, the old locations are tracked as decommissioned
5. **Delete path tracking**: When pages become empty after deletion (both leaf and index pages), their locations are tracked as decommissioned

### 3. Updated KVStore Class
**File:** `lsmplus-kvstore/src/main/java/org/hyperkv/lsmplus/core/KVStore.java`

**Changes:**
- Added `OccupancyManager` field
- Created `OccupancyManager` instance during initialization
- Passed `OccupancyManager` to `TreeDumper` constructor

### 4. Created Integration Tests
**File:** `lsmplus-kvstore/src/test/java/org/hyperkv/lsmplus/gc/OccupancyIntegrationTest.java`

**Test Coverage:**
- `testOccupancyTrackingDuringDump()`: Verifies single dump tracking
- `testMultipleDumpsWithOccupancyTracking()`: Verifies multiple consecutive dumps
- `testOccupancyManagerWithoutOccupancyManager()`: Verifies backward compatibility

## How It Works

### Occupancy Tracking Flow

```
Tree Dump Starts
    ↓
OccupancyManager.startDump(version)
    ↓
Build/Update Tree
    ↓
For each page saved:
    - Calculate aligned size (header + data + CRC32 + padding)
    - Record page write: occupancyManager.recordPageWrite(chunkId, alignedSize)
    ↓
For each page replaced (has old real location):
    - Record decommission: occupancyManager.recordPageDecommission(chunkId, alignedSize)
    ↓
For each page replaced:
    - Record decommission: occupancyManager.recordPageDecommission(chunkId, alignedSize)
    ↓
OccupancyManager.finishDump()
    - Persist occupancy record to {basePath}/occupancy/{version}.pb
    - Update MNS in MNSTracker
```

### Aligned Size Calculation

The `SegmentLocation.length` already contains the aligned size from `WriteItem.toByteArray()`, which includes:
- Header (12 bytes)
- Page data
- CRC32 (4 bytes)
- Padding (to align to 4096 bytes)

So we can directly use `location.getLength()` without re-serializing the page:

```java
// Optimized: Use location length directly (already aligned)
occupancyManager.recordPageWrite(location.getChunkId(), location.getLength());
```

**Note:** The Chunk.write() method returns:
```java
return new SegmentLocation(chunkId, offset, itemBytes.length);
```
where `itemBytes` is the complete WriteItem including padding.

### Occupancy Record Format

Each dump creates a protobuf file containing:
- Tree version
- MNS (Min Not Sealed number)
- Timestamp
- List of occupancy deltas (chunkId, deltaSize)

## Benefits

1. **Accurate GC Decision Making**: GC can now determine which chunks have low occupancy and should be cleaned up
2. **Version-Based Tracking**: Each tree version has its own occupancy record
3. **MNS Integration**: Proper tracking of which chunks are safe for GC
4. **Aligned Size Tracking**: Accurate space accounting using Write Item alignment
5. **Backward Compatible**: Works without occupancy tracking if OccupancyManager is null

## Testing

All tests pass successfully:
- TreeDumperTest: ✓ PASSED
- BPlusTreeTest: ✓ PASSED
- BPlusTreeFullIntegrationTest: ✓ PASSED
- OccupancyIntegrationTest: ✓ PASSED
- All lsmplus-kvstore tests: ✓ PASSED

## Future Work

1. **Chunk Occupancy Aggregation**: Implement aggregation logic to calculate total chunk occupancy across versions
2. **GC Integration**: Connect occupancy data to GC decision-making logic
3. **Metrics**: Add metrics for occupancy tracking performance
4. **Delete Path Testing**: Add specific tests for occupancy tracking during delete operations

## Design Compliance

This implementation follows the design specified in design/design-gc.md:
- ✓ Occupancy tracking per dump
- ✓ MNS recording per version
- ✓ Aligned size calculation
- ✓ Occupancy file persistence
- ✓ Integration with existing infrastructure
