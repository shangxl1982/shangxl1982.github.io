# Story 9-1: Implement WriteRequestQueue

## Story

As a developer, I want to implement the WriteRequestQueue class so that writes can be serialized.

## Acceptance Criteria

- [ ] WriteRequestQueue class created
- [ ] offer() method adds request to queue
- [ ] poll() method retrieves request
- [ ] size() method returns queue size
- [ ] drain() method retrieves all requests
- [ ] Unit tests verify all methods

## Technical Details

### Class: WriteRequestQueue

```java
package org.hyperkv.lsmplus.core.concurrency;

public class WriteRequestQueue {
    private final ConcurrentLinkedQueue<WriteRequest> queue;
    private final int batchSizeThreshold;
    private final long timeWindowThreshold;
    
    public WriteRequestQueue(int batchSizeThreshold, long timeWindowThreshold);
    public boolean offer(WriteRequest request);
    public WriteRequest poll();
    public int size();
    public List<WriteRequest> drain();
}
```

### WriteRequest Class

```java
public class WriteRequest {
    private final OperationType operationType;
    private final IndexKey key;
    private final IndexValue value;
    private final CompletableFuture<Void> promise;
    
    public WriteRequest(OperationType type, IndexKey key, IndexValue value);
    public void complete();
    public void completeExceptionally(Exception e);
    public void await();
}
```

## Testing

- testOfferAndPoll()
- testSize()
- testDrain()
- testMultipleRequests()
- testConcurrentAccess()

## Effort Estimate

1 day
