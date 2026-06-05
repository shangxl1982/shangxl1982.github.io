# Story 5-4: Implement Tombstone Support

## Story

As a developer, I want to implement tombstone support so that deletes can be tracked in memory.

## Acceptance Criteria

- [ ] Delete operations mark entries as tombstones
- [ ] Get operations return null for tombstones
- [ ] Tombstones are preserved in sealed tables
- [ ] Tombstones are not written to B+Tree
- [ ] Unit tests verify all methods

## Technical Details

### Implementation

```java
public void delete(IndexKey key, JournalReplayPoint replayPoint) {
    IndexValue tombstone = new IndexValue(ValueType.TOMBSTONE, new byte[0]);
    data.put(key, tombstone);
}
```

### Read Flow

```java
public IndexValue get(IndexKey key) {
    IndexValue value = data.get(key);
    
    if (value == null) {
        return null;
    }
    
    if (value.isTombstone()) {
        return null;  // Key was deleted
    }
    
    return value;
}
```

## Testing

- testDeleteCreatesTombstone()
- testGetReturnsNullForTombstone()
- testRangeQueryExcludesTombstones()
- testTombstonePreservedInSeal()
- testMultipleDeletes()

## Effort Estimate

1 day
