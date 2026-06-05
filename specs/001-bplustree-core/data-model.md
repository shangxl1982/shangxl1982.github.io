# Data Model: B+Tree Persistent Storage

## Entities

### BPlusTree

**Description**: The main B+Tree structure with version tracking and metadata.

**Fields**:
- `currentVersion` (long): Current tree version number, incremented on each dump
- `rootLocation` (SegmentLocation): Physical location of root page (always an index page)
- `journalReplayPoint` (JournalReplayPoint): Point in journal for crash recovery
- `pageIdManager` (PageIdManager): Manages page ID allocation

**Relationships**:
- Has one root page (IndexPage)
- Manages multiple Page instances through PageManager
- Tracks occupancy deltas for GC

### Page

**Description**: Self-contained storage unit that can be either leaf or index type.

**Fields**:
- `pageType` (PageType enum): LEAF or INDEX
- `pageId` (long): Unique page ID (positive for leaf, negative for index, 0 for invalid)
- `maxSize` (int): Maximum capacity in bytes
- `usedSize` (int): Current used bytes
- `entries` (List<IndexPair>): Key-value or key-location pairs
- `maxKey` (IndexKey): Maximum key in page (for flush ordering)

**Validation Rules**:
- Leaf pages cannot store tombstone values
- Index pages cannot store normal values
- Page must not exceed maxSize
- Entries must be in sorted order

**State Transitions**:
- Created → Modified (put/delete) → Split (if full) → Flushed (persisted) → Decommissioned (if replaced)

### PageIdManager

**Description**: Manages monotonic page ID allocation with separate sequences.

**Fields**:
- `nextLeafPageId` (long): Next available leaf page ID (starts at 1)
- `nextIndexPageId` (long): Next available index page ID (starts at Long.MIN_VALUE)

**Invariants**:
- Leaf page IDs are always positive: 1, 2, 3, ...
- Index page IDs are always negative: Long.MIN_VALUE, Long.MIN_VALUE+1, ...
- 0 is reserved for invalid/null page ID
- No ID collisions possible between leaf and index sequences

### LevelWriteBuffer

**Description**: Organizes modified pages by tree level for proper flush ordering.

**Fields**:
- `levels` (Map<Integer, LevelBuffer>): Pages grouped by level (0 = leaves)
- Each LevelBuffer contains:
  - `pages` (Map<Long, Page>): Page ID → Page mapping
  - `pageMaxKeys` (Map<Long, IndexKey>): Page ID → max key mapping
  - `pageLocations` (Map<Long, SegmentLocation>): Page ID → disk location mapping

**Invariants**:
- Level 0 always contains leaf pages
- Higher levels contain index pages
- Pages at level N reference pages at level N-1 as children
- Flush order: level 0 → level 1 → ... → root level

### VirtualSegmentLocation

**Description**: Marker location for pending child references.

**Fields**:
- `chunkId` (UUID): Always UUID(0, 0) for virtual locations
- `offset` (long): Contains the pageId of the pending child
- `length` (int): Always 0 for virtual locations

**Validation**:
- `isVirtual()` returns true if chunkId is all zeros
- `getPendingPageId()` returns the offset as the page ID

### IndexPair

**Description**: Key-value or key-location pair stored in pages.

**Fields**:
- `key` (IndexKey): The entry key
- `value` (IndexValue): For leaf pages - the value
- `location` (SegmentLocation): For index pages - child page location

**Validation**:
- Leaf pages: key + value (location must be null)
- Index pages: key + location (value must be null)

### OccupancyDelta

**Description**: Records chunk space usage changes for GC.

**Fields**:
- `chunkId` (UUID): Affected chunk
- `deltaSize` (long): Size change (positive for new, negative for decommissioned)

### TreeDumpResult

**Description**: Result of a tree dump operation.

**Fields**:
- `newVersion` (long): New tree version number
- `occupancyDeltas` (List<OccupancyDelta>): Space usage changes
- `decommissionedPages` (List<SegmentLocation>): Pages marked for GC
- `mns` (long): Minimum not-sealed chunk number
