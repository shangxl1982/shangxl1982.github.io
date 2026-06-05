package org.hyperkv.lsmplus.storage;

import org.hyperkv.lsmplus.proto.Common.ChunkType;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

import java.io.IOException;
import java.util.ArrayList;
import java.util.List;
import java.util.concurrent.ArrayBlockingQueue;
import java.util.concurrent.BlockingQueue;
import java.util.concurrent.CompletableFuture;
import java.util.concurrent.CountDownLatch;
import java.util.concurrent.TimeUnit;
import java.util.concurrent.atomic.AtomicBoolean;

public class AsyncBatchWriter {

    private static final Logger log = LoggerFactory.getLogger(AsyncBatchWriter.class);
    private static final int DEFAULT_BATCH_SIZE = 100;
    private static final int DEFAULT_QUEUE_SIZE = 10000;
    private static final long FLUSH_TIMEOUT_MILLIS = 30000;

    private final ChunkManager chunkManager;
    private final ChunkType chunkType;
    private final short writeItemType;
    private final int batchSize;
    private final BlockingQueue<WriteRequest> writeQueue;
    private final Thread writerThread;
    private final AtomicBoolean running;
    private volatile IOException lastError;

    private static class WriteRequest {
        final byte[] data;
        final CompletableFuture<SegmentLocation> future;

        WriteRequest(byte[] data, CompletableFuture<SegmentLocation> future) {
            this.data = data;
            this.future = future;
        }
    }

    public AsyncBatchWriter(ChunkManager chunkManager, ChunkType chunkType, short writeItemType) {
        this(chunkManager, chunkType, writeItemType, DEFAULT_BATCH_SIZE, DEFAULT_QUEUE_SIZE);
    }

    public AsyncBatchWriter(ChunkManager chunkManager, ChunkType chunkType, short writeItemType, 
                            int batchSize, int queueSize) {
        this.chunkManager = chunkManager;
        this.chunkType = chunkType;
        this.writeItemType = writeItemType;
        this.batchSize = batchSize;
        this.writeQueue = new ArrayBlockingQueue<>(queueSize);
        this.running = new AtomicBoolean(true);
        this.writerThread = createWriterThread();
        this.writerThread.start();
        
        log.info("Started AsyncBatchWriter for chunkType={}, batchSize={}, queueSize={}", 
            chunkType, batchSize, queueSize);
    }

    private Thread createWriterThread() {
        Thread thread = new Thread(this::processWrites, 
            "async-batch-writer-" + chunkType.name().toLowerCase());
        thread.setDaemon(true);
        return thread;
    }

    private void processWrites() {
        List<WriteRequest> batch = new ArrayList<>(batchSize);
        
        while (running.get() || !writeQueue.isEmpty()) {
            try {
                batch.clear();
                
                WriteRequest first = writeQueue.poll(100, TimeUnit.MILLISECONDS);
                if (first == null) {
                    continue;
                }
                
                batch.add(first);
                
                writeQueue.drainTo(batch, batchSize - 1);
                
                processBatch(batch);
                
            } catch (InterruptedException e) {
                Thread.currentThread().interrupt();
                log.debug("AsyncBatchWriter interrupted");
                break;
            } catch (Exception e) {
                log.error("Error processing batch writes", e);
                lastError = new IOException("Batch write failed", e);
                failBatch(batch, e);
            }
        }
        
        log.info("AsyncBatchWriter stopped for chunkType={}", chunkType);
    }

    private void processBatch(List<WriteRequest> batch) throws IOException {
        if (batch.isEmpty()) {
            return;
        }
        
        log.debug("Processing batch of {} writes for chunkType={}", batch.size(), chunkType);
        
        List<byte[]> dataList = new ArrayList<>(batch.size());
        for (WriteRequest request : batch) {
            dataList.add(request.data);
        }
        
        try {
            List<SegmentLocation> locations = chunkManager.writeDataBatch(dataList, chunkType, writeItemType);
            
            if (locations.size() != batch.size()) {
                throw new IOException("Batch write returned wrong number of locations: expected " + 
                    batch.size() + ", got " + locations.size());
            }
            
            for (int i = 0; i < batch.size(); i++) {
                batch.get(i).future.complete(locations.get(i));
            }
            
            log.trace("Completed batch of {} writes", batch.size());
        } catch (IOException e) {
            failBatch(batch, e);
            throw e;
        }
    }

    private void failBatch(List<WriteRequest> batch, Exception error) {
        for (WriteRequest request : batch) {
            if (!request.future.isDone()) {
                request.future.completeExceptionally(error);
            }
        }
    }

    public CompletableFuture<SegmentLocation> submit(byte[] data) {
        if (!running.get()) {
            CompletableFuture<SegmentLocation> future = new CompletableFuture<>();
            future.completeExceptionally(new IllegalStateException("AsyncBatchWriter is stopped"));
            return future;
        }
        
        if (lastError != null) {
            CompletableFuture<SegmentLocation> future = new CompletableFuture<>();
            future.completeExceptionally(lastError);
            return future;
        }
        
        CompletableFuture<SegmentLocation> future = new CompletableFuture<>();
        WriteRequest request = new WriteRequest(data, future);
        
        if (!writeQueue.offer(request)) {
            future.completeExceptionally(new IllegalStateException("Write queue is full"));
            return future;
        }
        
        return future;
    }

    public void flush() throws IOException, InterruptedException {
        log.debug("Flushing AsyncBatchWriter for chunkType={}, queueSize={}", 
            chunkType, writeQueue.size());
        
        long startTime = System.currentTimeMillis();
        while (!writeQueue.isEmpty()) {
            if (System.currentTimeMillis() - startTime > FLUSH_TIMEOUT_MILLIS) {
                throw new IOException("Flush timeout exceeded");
            }
            
            if (lastError != null) {
                throw lastError;
            }
            
            Thread.sleep(10);
        }
        
        if (lastError != null) {
            throw lastError;
        }
        
        log.debug("AsyncBatchWriter flush completed for chunkType={}", chunkType);
    }

    public void stop() {
        log.info("Stopping AsyncBatchWriter for chunkType={}", chunkType);
        running.set(false);
        writerThread.interrupt();
        
        try {
            writerThread.join(5000);
        } catch (InterruptedException e) {
            Thread.currentThread().interrupt();
            log.warn("Interrupted while waiting for AsyncBatchWriter to stop");
        }
        
        failAllPending(new IllegalStateException("AsyncBatchWriter stopped"));
    }

    private void failAllPending(Exception error) {
        List<WriteRequest> pending = new ArrayList<>();
        writeQueue.drainTo(pending);
        failBatch(pending, error);
    }

    public int getQueueSize() {
        return writeQueue.size();
    }

    public boolean isRunning() {
        return running.get();
    }
}
