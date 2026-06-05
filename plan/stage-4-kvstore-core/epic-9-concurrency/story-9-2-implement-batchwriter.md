# Story 9-2: Implement BatchWriter

## Story

As a developer, I want to implement the BatchWriter class so that writes can be processed in batches.

## Acceptance Criteria

- [ ] BatchWriter class created
- [ ] start() method starts batch processing
- [ ] stop() method stops batch processing
- [ ] processBatch() method processes batch
- [ ] Batch size threshold implemented
- [ ] Time window threshold implemented
- [ ] Unit tests verify all methods

## Technical Details

### Class: BatchWriter

```java
package org.hyperkv.lsmplus.core.concurrency;

public class BatchWriter {
    private final WriteRequestQueue queue;
    private final int batchSize;
    private final long timeWindow;
    private final ScheduledExecutorService executor;
    private volatile boolean running;
    
    public BatchWriter(WriteRequestQueue queue, int batchSize, long timeWindow);
    public void start();
    public void stop();
    private void processBatch();
}
```

### Processing Flow

```
1. Collect requests (batchSize or timeWindow)
2. For each request:
   a. Create WriteItem
   b. Write to Journal
3. Update MemoryTable
4. Complete all promises
```

## Testing

- testStartAndStop()
- testProcessBatchSize()
- testProcessTimeWindow()
- testMultipleBatches()
- testConcurrentProcessing()

## Effort Estimate

1.5 days
