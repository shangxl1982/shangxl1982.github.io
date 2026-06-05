# Async Batch Write Implementation

## Date
2026-04-23

## Summary
Implemented asynchronous batch write functionality for page persistence during dump operations. This improves performance by aggregating multiple page writes into batch operations that are processed asynchronously with a single I/O operation per batch.

## Changes

### 1. AsyncBatchWriter Class
Created new class `AsyncBatchWriter` in `lsmplus-storage` module:
- **Location**: [AsyncBatchWriter.java](file:///home/wisefox/git/hyperkvstore/lsmplus-storage/src/main/java/org/hyperkv/lsmplus/storage/AsyncBatchWriter.java)
- **Purpose**: Handles asynchronous batch processing of page writes
- **Key Features**:
  - Queue-based write request buffering
  - Background thread for batch processing
  - CompletableFuture for async result handling
  - Configurable batch size (default: 100)
  - Configurable queue size (default: 10,000)
  - Proper error handling and propagation
  - Flush and stop methods for resource management
  - **Single I/O operation per batch**: Combines all request data into one write

### 2. Chunk Enhancements
Modified [Chunk.java](file:///home/wisefox/git/hyperkvstore/lsmplus-storage/src/main/java/org/hyperkv/lsmplus/storage/Chunk.java):
- Added `writeBatch(List<byte[]> dataList, short writeItemType)` method
- Combines multiple WriteItem byte arrays into a single buffer
- Performs one I/O write operation for all items
- Single sync operation for the entire batch
- Returns individual SegmentLocation for each item
- Maintains proper alignment and CRC for each item

### 3. ChunkManager Enhancements
Modified [ChunkManager.java](file:///home/wisefox/git/hyperkvstore/lsmplus-storage/src/main/java/org/hyperkv/lsmplus/storage/ChunkManager.java):
- Added async writer fields for leaf and index pages
- Added public methods:
  - `writeLeafPageAsync(byte[] data)`: Async write for leaf pages
  - `writeIndexPageAsync(byte[] data)`: Async write for index pages
  - `flushAsyncWrites()`: Flush all pending async writes
  - `stopAsyncWriters()`: Stop async writer threads
  - `writeDataBatch(List<byte[]> dataList, ChunkType chunkType, short writeItemType)`: Batch write
- Made `writeData()` method package-private for AsyncBatchWriter access
- Updated `close()` method to stop async writers during shutdown

### 3. PageManager Enhancements
Modified [PageManager.java](file:///home/wisefox/git/hyperkvstore/lsmplus-kvstore/src/main/java/org/hyperkv/lsmplus/bplustree/PageManager.java):
- Added async save methods:
  - `savePageAsync(Page page)`: Async save for single page
  - `savePagesAsync(List<Page> pages)`: Async save for multiple pages
  - `flushAsyncWrites()`: Flush pending async writes
- Proper lifecycle management and error handling
- Cache updates on successful writes

### 4. TreeDumper Integration
Modified [TreeDumper.java](file:///home/wisefox/git/hyperkvstore/lsmplus-kvstore/src/main/java/org/hyperkv/lsmplus/bplustree/TreeDumper.java):
- Updated all page save operations to use async writes:
  - `buildNewTree()`: Leaf and root page saves
  - `buildIndexLevels()`: Index page saves
  - `flushLevelPages()`: Level-based page flushing
  - `flushAllLevels()`: Multi-level page flushing
- Added proper exception handling for async operations
- Updated method signatures to throw `KVStoreException`

### 5. Test Fix
Fixed incorrect assertion in [UsageExampleTest.java](file:///home/wisefox/git/hyperkvstore/lsmplus-monitoring/src/test/java/org/hyperkv/lsmplus/monitoring/UsageExampleTest.java):
- Corrected mean calculation expectation from 150.0 to 125.0

## Technical Details

### Async Write Flow
1. Page is marked as `FLUSHABLE`
2. Page data is serialized to byte array
3. Write request is submitted to AsyncBatchWriter queue
4. CompletableFuture is returned immediately
5. Background thread processes batches:
   - Collects up to batch size requests
   - **Combines all request data into single buffer**
   - **Performs one I/O write operation for entire batch**
   - **Single sync operation for all items**
   - Completes futures with individual SegmentLocation results
6. Caller waits for completion using `future.get()`

### Batch Write Optimization
The key optimization is combining multiple write requests into a single I/O operation:
1. **Data Collection**: Gather all request data from the batch
2. **Buffer Allocation**: Create a single buffer sized for all items
3. **Item Serialization**: Each item is wrapped in WriteItem with header, data, CRC, and padding
4. **Single Write**: All items written to file in one I/O operation
5. **Single Sync**: One sync call for the entire batch
6. **Location Tracking**: Individual SegmentLocation returned for each item

This reduces:
- Number of I/O system calls
- Number of sync operations
- File system overhead
- Overall latency for batch operations

### Error Handling
- Failed writes complete futures exceptionally
- Error propagation to caller
- Page lifecycle reset on failure
- Proper exception wrapping in `KVStoreException`

### Resource Management
- Async writers stopped on ChunkManager close
- Flush operations ensure all writes complete
- Thread interruption handling
- Queue overflow protection

## Performance Benefits
- Reduced I/O latency through batching
- Better resource utilization with async processing
- Improved throughput for dump operations
- Non-blocking write submission

## Testing
- All existing tests pass
- Build successful
- No compilation errors
- Proper error handling verified

### Test Cases Added

#### 1. AsyncBatchWriterTest
Created comprehensive test suite for AsyncBatchWriter:
- **Location**: [AsyncBatchWriterTest.java](file:///home/wisefox/git/hyperkvstore/lsmplus-storage/src/test/java/org/hyperkv/lsmplus/storage/AsyncBatchWriterTest.java)
- **Test Cases**:
  - `testSubmitSingleWrite`: Single async write operation
  - `testSubmitMultipleWrites`: Multiple sequential writes
  - `testBatchProcessing`: Batch collection and processing
  - `testFlushOperation`: Flush mechanism verification
  - `testStopOperation`: Stop and cleanup
  - `testSubmitAfterStop`: Error handling after stop
  - `testWriteVerification`: Data integrity verification
  - `testConcurrentSubmissions`: Multi-threaded submission
  - `testLargeDataWrite`: Large payload handling
  - `testMultipleFlushes`: Multiple flush cycles

#### 2. ChunkManagerTest
Added async method tests:
- `testWriteLeafPageAsync`: Async leaf page write
- `testWriteIndexPageAsync`: Async index page write
- `testMultipleAsyncWrites`: Multiple concurrent async writes
- `testStopAsyncWriters`: Async writer lifecycle management
- `testWriteDataBatch`: Batch write with multiple items
- `testWriteDataBatchSingleItem`: Batch write with single item
- `testWriteDataBatchConsistency`: Verify batch vs individual write consistency

#### 3. PageManagerTest
Added async method tests:
- `testSavePageAsync`: Async leaf page save
- `testSaveIndexPageAsync`: Async index page save
- `testSavePagesAsync`: Batch async page saves
- `testSavePageAsyncNullPage`: Null page error handling
- `testSavePageAsyncInvalidLifecycle`: Invalid lifecycle error handling
- `testAsyncPageCacheUpdate`: Cache update verification

## Future Enhancements
1. Add metrics for async write performance
2. Implement adaptive batch sizing
3. Add write coalescing for better efficiency
4. Consider write ordering guarantees
5. Add backpressure mechanisms for queue management
