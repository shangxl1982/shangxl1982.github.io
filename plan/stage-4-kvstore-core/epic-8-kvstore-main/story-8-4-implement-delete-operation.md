# Story 8-4: Implement Delete Operation

## Story

As a developer, I want to implement the delete operation so that data can be removed.

## Acceptance Criteria

- [ ] delete(Key) method removes data
- [ ] Tombstone created in MemoryTable
- [ ] Tombstone written to Journal
- [ ] Tombstone not written to B+Tree
- [ ] Unit tests verify all methods

## Technical Details

### Implementation

```java
public void delete(IndexKey key) {
    WriteRequest request = new WriteRequest(OperationType.DELETE, key, null);
    requestQueue.offer(request);
    request.await();
}
```

### Delete Flow

```
1. Create tombstone
2. Write to Journal
3. Update MemoryTable with tombstone
4. Notify client
```

## Testing

- testDeleteExistingEntry()
- testDeleteNonExistingEntry()
- testDeleteWithTombstone()
- testDeleteAfterGet()
- testDeleteMultipleTimes()

## Effort Estimate

1 day
