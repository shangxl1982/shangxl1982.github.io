# Quickstart: B+Tree Persistent Storage

## Prerequisites

- Java 21+
- Gradle 9.4+
- Linux (64-bit)

## Build

```bash
cd /home/wisefox/git/hyperkvstore
./gradlew :lsmplus-bplustree:build
```

## Run Tests

```bash
# Run all B+Tree tests
./gradlew :lsmplus-bplustree:test

# Run specific test class
./gradlew :lsmplus-bplustree:test --tests "PageTest"
./gradlew :lsmplus-bplustree:test --tests "BPlusTreeTest"
./gradlew :lsmplus-bplustree:test --tests "PageSplitTest"
```

## Feature Validation Checklist

### MVP (User Story 1 - Tree Dump)

1. **Build passes**: `./gradlew :lsmplus-bplustree:build`
2. **LevelWriteBuffer tests pass**: `./gradlew :lsmplus-bplustree:test --tests "LevelWriteBufferTest"`
3. **TreeDumper tests pass**: `./gradlew :lsmplus-bplustree:test --tests "TreeDumperTest"`
4. **Manual validation**:
   - Create a BPlusTree instance
   - Seal a MemoryTable with test entries
   - Call `tree.dump(sealedTables)`
   - Verify entries are queryable via `tree.search(key)`

### Page ID Management (User Story 2)

1. **PageIdManager tests pass**: `./gradlew :lsmplus-bplustree:test --tests "PageIdManagerTest"`
2. **Validation**:
   - Leaf page IDs start at 1 and increment
   - Index page IDs start at Long.MIN_VALUE and increment
   - No collisions between sequences
   - IDs persist and restore correctly

### Crash Consistency (User Story 3)

1. **Virtual location tests pass**: `./gradlew :lsmplus-bplustree:test --tests "VirtualSegmentLocationTest"`
2. **Crash consistency tests pass**: Check TreeDumperTest for crash simulation tests
3. **Validation**:
   - Verify flush order: level 0 → level 1 → ... → root
   - Verify virtual locations resolve before parent flush

### Page Splitting (User Story 4)

1. **Page split tests pass**: `./gradlew :lsmplus-bplustree:test --tests "PageSplitTest"`
2. **Validation**:
   - Insert enough entries to trigger splits
   - Verify tree remains balanced
   - Verify parent pages updated correctly

### Tombstone Handling (User Story 5)

1. **Tombstone tests pass**: Check TreeDumperTest for tombstone tests
2. **Validation**:
   - Insert entries, tombstone them in MemoryTable
   - Trigger dump
   - Verify entries are removed from B+Tree

## Common Issues

### Protobuf not generated
```bash
./gradlew :lsmplus-api:generateProto
```

### Test failures after proto changes
```bash
./gradlew clean :lsmplus-bplustree:test
```

### Long page ID type mismatches
Check that all page ID usages use `long` not `int`:
- Page.pageId
- LevelWriteBuffer page maps
- SegmentLocation.offset

## Architecture Overview

```
MemoryTable (sealed) → TreeDumper → LevelWriteBuffer → PageManager → ChunkManager (disk)
                              ↓
                        PageIdManager (ID allocation)
                              ↓
                    VirtualSegmentLocation (pending refs)
                              ↓
                    BPlusTree metadata (version, root, replay point)
```

## Key Files

| File | Purpose |
|------|---------|
| `BPlusTree.java` | Main tree with version management |
| `TreeDumper.java` | Orchestrates dump, splits, flush |
| `LevelWriteBuffer.java` | Level-based page buffering |
| `PageIdManager.java` | Monotonic page ID allocation |
| `Page.java` | Unified page (leaf/index) |
| `VirtualSegmentLocation.java` | Virtual location utility |
| `PageManager.java` | Page load/save with caching |
