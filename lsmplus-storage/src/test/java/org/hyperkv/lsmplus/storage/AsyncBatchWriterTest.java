package org.hyperkv.lsmplus.storage;

import org.hyperkv.lsmplus.proto.Common.ChunkType;
import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.io.TempDir;

import java.io.IOException;
import java.nio.file.Path;
import java.util.ArrayList;
import java.util.Collections;
import java.util.List;
import java.util.UUID;
import java.util.concurrent.CompletableFuture;
import java.util.concurrent.ExecutionException;
import java.util.concurrent.TimeUnit;
import java.util.concurrent.TimeoutException;

import static org.junit.jupiter.api.Assertions.*;

class AsyncBatchWriterTest {

    @TempDir
    Path tempDir;

    private ChunkManager chunkManager;
    private AsyncBatchWriter asyncWriter;
    private UUID ownerId;
    private UUID namespaceId;

    @BeforeEach
    void setUp() throws IOException {
        ownerId = UUID.randomUUID();
        namespaceId = UUID.randomUUID();
        chunkManager = new ChunkManager(tempDir.toFile().getAbsolutePath(), ownerId, namespaceId);
        asyncWriter = new AsyncBatchWriter(chunkManager, ChunkType.CHUNK_LEAF, (short) 1);
    }

    @AfterEach
    void tearDown() throws IOException {
        if (asyncWriter != null) {
            asyncWriter.stop();
        }
        if (chunkManager != null) {
            chunkManager.close();
        }
    }

    @Test
    void testSubmitSingleWrite() 
            throws IOException, InterruptedException, ExecutionException, TimeoutException {
        byte[] data = "test data".getBytes();
        
        CompletableFuture<SegmentLocation> future = asyncWriter.submit(data);
        
        assertNotNull(future);
        
        asyncWriter.flush();
        
        SegmentLocation location = future.get(5, TimeUnit.SECONDS);
        assertNotNull(location);
        assertNotNull(location.getChunkId());
        assertTrue(location.getOffset() >= 0);
    }

    @Test
    void testSubmitMultipleWrites() 
            throws IOException, InterruptedException, ExecutionException, TimeoutException {
        int numWrites = 10;
        List<CompletableFuture<SegmentLocation>> futures = new ArrayList<>();
        
        for (int i = 0; i < numWrites; i++) {
            byte[] data = ("test data " + i).getBytes();
            futures.add(asyncWriter.submit(data));
        }
        
        asyncWriter.flush();
        
        for (int i = 0; i < numWrites; i++) {
            SegmentLocation location = futures.get(i).get(5, TimeUnit.SECONDS);
            assertNotNull(location);
            assertNotNull(location.getChunkId());
        }
    }

    @Test
    void testBatchProcessing() 
            throws IOException, InterruptedException, ExecutionException, TimeoutException {
        int batchSize = 5;
        AsyncBatchWriter smallBatchWriter = new AsyncBatchWriter(
            chunkManager, ChunkType.CHUNK_INDEX, (short) 1, batchSize, 1000);
        
        try {
            List<CompletableFuture<SegmentLocation>> futures = new ArrayList<>();
            
            for (int i = 0; i < batchSize * 2; i++) {
                byte[] data = ("batch data " + i).getBytes();
                futures.add(smallBatchWriter.submit(data));
            }
            
            smallBatchWriter.flush();
            
            for (CompletableFuture<SegmentLocation> future : futures) {
                SegmentLocation location = future.get(5, TimeUnit.SECONDS);
                assertNotNull(location);
            }
        } finally {
            smallBatchWriter.stop();
        }
    }

    @Test
    void testFlushOperation() 
            throws IOException, InterruptedException, ExecutionException, TimeoutException {
        byte[] data = "flush test".getBytes();
        
        CompletableFuture<SegmentLocation> future = asyncWriter.submit(data);
        
        asyncWriter.flush();
        
        SegmentLocation location = future.get(5, TimeUnit.SECONDS);
        assertNotNull(location);
    }

    @Test
    void testStopOperation() 
            throws InterruptedException {
        asyncWriter.stop();
        
        CompletableFuture<SegmentLocation> future = asyncWriter.submit("data".getBytes());
        
        assertNotNull(future);
        
        assertThrows(ExecutionException.class, () -> future.get(5, TimeUnit.SECONDS));
    }

    @Test
    void testSubmitAfterStop() {
        asyncWriter.stop();
        
        byte[] data = "after stop".getBytes();
        CompletableFuture<SegmentLocation> future = asyncWriter.submit(data);
        
        assertNotNull(future);
        assertThrows(ExecutionException.class, () -> {
            try {
                future.get(5, TimeUnit.SECONDS);
            } catch (InterruptedException e) {
                Thread.currentThread().interrupt();
                throw new RuntimeException(e);
            }
        });
    }

    @Test
    void testWriteVerification() 
            throws IOException, InterruptedException, ExecutionException, TimeoutException {
        byte[] data = "verification data".getBytes();
        
        CompletableFuture<SegmentLocation> future = asyncWriter.submit(data);
        
        asyncWriter.flush();
        
        SegmentLocation location = future.get(5, TimeUnit.SECONDS);
        assertNotNull(location);
        
        Chunk chunk = chunkManager.getChunk(location.getChunkId());
        assertNotNull(chunk);
        
        byte[] readData = chunk.read(location);
        assertArrayEquals(data, readData);
    }

    @Test
    void testConcurrentSubmissions() 
            throws IOException, InterruptedException, ExecutionException, TimeoutException {
        int numThreads = 5;
        int writesPerThread = 10;
        List<CompletableFuture<SegmentLocation>> allFutures = Collections.synchronizedList(new ArrayList<>());
        
        List<Thread> threads = new ArrayList<>();
        for (int t = 0; t < numThreads; t++) {
            final int threadId = t;
            Thread thread = new Thread(() -> {
                for (int i = 0; i < writesPerThread; i++) {
                    byte[] data = ("concurrent-" + threadId + "-" + i).getBytes();
                    allFutures.add(asyncWriter.submit(data));
                }
            });
            threads.add(thread);
            thread.start();
        }
        
        for (Thread thread : threads) {
            thread.join();
        }
        
        asyncWriter.flush();
        
        assertEquals(numThreads * writesPerThread, allFutures.size());
        
        for (CompletableFuture<SegmentLocation> future : allFutures) {
            SegmentLocation location = future.get(10, TimeUnit.SECONDS);
            assertNotNull(location);
        }
    }

    @Test
    void testLargeDataWrite() 
            throws IOException, InterruptedException, ExecutionException, TimeoutException {
        byte[] largeData = new byte[1024 * 100];
        for (int i = 0; i < largeData.length; i++) {
            largeData[i] = (byte) (i % 256);
        }
        
        CompletableFuture<SegmentLocation> future = asyncWriter.submit(largeData);
        
        asyncWriter.flush();
        
        SegmentLocation location = future.get(5, TimeUnit.SECONDS);
        assertNotNull(location);
        
        Chunk chunk = chunkManager.getChunk(location.getChunkId());
        byte[] readData = chunk.read(location);
        assertArrayEquals(largeData, readData);
    }

    @Test
    void testMultipleFlushes() 
            throws IOException, InterruptedException, ExecutionException, TimeoutException {
        for (int flush = 0; flush < 3; flush++) {
            List<CompletableFuture<SegmentLocation>> futures = new ArrayList<>();
            
            for (int i = 0; i < 5; i++) {
                byte[] data = ("flush-" + flush + "-data-" + i).getBytes();
                futures.add(asyncWriter.submit(data));
            }
            
            asyncWriter.flush();
            
            for (CompletableFuture<SegmentLocation> future : futures) {
                SegmentLocation location = future.get(5, TimeUnit.SECONDS);
                assertNotNull(location);
            }
        }
    }
}
