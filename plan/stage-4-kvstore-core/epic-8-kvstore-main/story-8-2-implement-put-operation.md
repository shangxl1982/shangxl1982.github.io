# Story 8-2: Implement Put Operation

## Story

As a developer, I want to implement the put operation so that data can be inserted.

## Acceptance Criteria

- [ ] put(Key, Value) method inserts data
- [ ] WriteRequest created and added to queue
- [ ] BatchWriter processes request
- [ ] Data written to Journal
- [ ] Data updated in MemoryTable
- [ ] Client notified on completion
- [ ] Unit tests verify all methods

## Technical Details

### Implementation

```java
public void put(IndexKey key, IndexValue value) {
    WriteRequest request = new WriteRequest(OperationType.PUT, key, value);
    requestQueue.offer(request);
    request.await();  // Synchronous wait
}
```

### BatchWriter Processing

```
1. Collect requests (batchSize or timeWindow)
2. For each request:
   a. Create WriteItem
   b. Write to Journal
3. Update MemoryTable
4. Notify clients
```

## Testing

- testPutSingleEntry()
- testPutMultipleEntries()
- testPutWithBatch()
- testPutAfterShutdown()
- testPutWithTombstone()

## Effort Estimate

1 day
