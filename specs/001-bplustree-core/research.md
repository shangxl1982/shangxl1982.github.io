# Research: B+Tree Persistent Storage

## Decision: Page ID Management Strategy

**Rationale**: Using separate monotonic sequences for leaf (positive) and index (negative) pages with long integers prevents overflow and makes page type detection trivial via sign check.

**Alternatives considered**:
- Single sequence with type prefix: More complex, requires bit manipulation
- UUID-based page IDs: Too large, impacts storage efficiency
- 32-bit integers: Would overflow at ~2 billion pages

## Decision: Level-Based Write Buffer

**Rationale**: Organizing pages by tree level ensures children are always flushed before parents, maintaining crash consistency without complex dependency tracking.

**Alternatives considered**:
- Single buffer with dependency graph: Complex to maintain, error-prone
- Immediate parent updates: Requires multiple disk writes per split
- Separate pending update list: Redundant data structure, harder to validate

## Decision: Virtual Locations for Pending References

**Rationale**: Using chunkId=0 with offset=pageId as virtual markers allows parent pages to directly reference unpersisted children. Resolution happens naturally during flush when child's real location is known.

**Alternatives considered**:
- Separate pending update map: Requires synchronization between two data structures
- Placeholder locations with special offsets: Similar approach but less explicit
- Deferred parent updates: Loses in-memory parent-child relationship tracking

## Decision: Append-Only Page Updates

**Rationale**: Every page modification creates a new page at a new location. This simplifies crash recovery and enables version history without complex in-place update logic.

**Alternatives considered**:
- In-place updates: Requires complex locking, harder recovery
- Copy-on-write with shared segments: More complex, marginal space savings

## Decision: Tree Dump as Batch Operation

**Rationale**: Processing all sealed memory tables in a single dump operation ensures version consistency and enables efficient bulk page management.

**Alternatives considered**:
- Per-entry updates: Would cause excessive page splits, poor performance
- Streaming updates: Harder to maintain version consistency
