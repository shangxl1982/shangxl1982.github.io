package org.hyperkv.lsmplus.journal;

import org.hyperkv.lsmplus.api.model.IndexKey;
import org.hyperkv.lsmplus.api.model.IndexValue;
import org.hyperkv.lsmplus.monitoring.MetricsRegistry;
import org.hyperkv.lsmplus.monitoring.PerformanceCounter;
import org.hyperkv.lsmplus.proto.Common.ChunkType;
import org.hyperkv.lsmplus.proto.Common.ChunkStatus;
import org.hyperkv.lsmplus.proto.Common.OperationType;
import org.hyperkv.lsmplus.proto.Journal.JournalEntryProto;
import org.hyperkv.lsmplus.storage.Chunk;
import org.hyperkv.lsmplus.storage.ChunkInfo;
import org.hyperkv.lsmplus.storage.ChunkManager;
import org.hyperkv.lsmplus.storage.WriteItem;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

import java.io.File;
import java.io.IOException;
import java.util.ArrayList;
import java.util.List;
import java.util.Map;
import java.util.NavigableMap;
import java.util.TreeMap;
import java.util.UUID;
import java.util.concurrent.CompletableFuture;
import java.util.concurrent.atomic.AtomicLong;
import java.util.concurrent.locks.ReentrantLock;

public class JournalRegionManager implements ChunkManager.JournalChunkAllocationListener {

    private static final Logger log = LoggerFactory.getLogger(JournalRegionManager.class);
    
    private static final long DEFAULT_REGION_SIZE_THRESHOLD = 64 * 1024 * 1024;

    private final ChunkManager chunkManager;
    private final NavigableMap<Long, JournalRegion> regions;
    private final AtomicLong regionMajorCounter;
    private final AtomicLong sequenceNumber;
    private final JournalRegionIndexFile regionIndexFile;
    private final ReentrantLock allocationLock;
    private final long regionSizeThreshold;
    
    private volatile JournalRegion currentRegion;
    
    private final PerformanceCounter writeCounter;
    private final PerformanceCounter writeBatchCounter;
    private final PerformanceCounter rotateChunkCounter;

    public JournalRegionManager(ChunkManager chunkManager, File regionIndexFile, UUID ownerId,
                                long startMajor, long regionSizeThreshold) {
        if (chunkManager == null) {
            throw new IllegalArgumentException("chunkManager must not be null");
        }
        
        this.chunkManager = chunkManager;
        this.regions = new TreeMap<>();
        this.regionMajorCounter = new AtomicLong(0);
        this.sequenceNumber = new AtomicLong(0);
        this.allocationLock = new ReentrantLock();
        this.regionSizeThreshold = regionSizeThreshold;
        
        if (regionIndexFile != null && ownerId != null) {
            try {
                this.regionIndexFile = JournalRegionIndexFile.load(regionIndexFile, ownerId);
                loadRegionsFromIndex(startMajor);
            } catch (IOException e) {
                throw new RuntimeException("Failed to load journal region index file: " + regionIndexFile.getAbsolutePath(), e);
            }
        } else {
            this.regionIndexFile = null;
            discoverExistingRegions(startMajor);
        }
        
        this.writeCounter = MetricsRegistry.getCounter("journal_write", "Journal write latency");
        this.writeBatchCounter = MetricsRegistry.getCounter("journal_write_batch", "Journal batch write latency");
        this.rotateChunkCounter = MetricsRegistry.getCounter("journal_rotate_chunk", "Journal rotate chunk latency");
        // Register as listener for journal chunk allocation events
        chunkManager.setJournalChunkListener(this);

        log.info("Initialized JournalRegionManager with {} existing regions (startMajor={})", regions.size(), startMajor);
    }

    private void loadRegionsFromIndex(long startMajor) {
        if (regionIndexFile == null) {
            return;
        }
        
        long maxMajor = 0;
        for (JournalRegionIndexFile.RegionEntry entry : regionIndexFile.getEntries()) {
            long major = entry.regionMajor();
            if (major < startMajor) {
                continue;
            }
            JournalRegion region = new JournalRegion(major, entry.regionMinor(), entry.chunkId());
            regions.put(major, region);
            maxMajor = Math.max(maxMajor, major);
        }
        
        regionMajorCounter.set(maxMajor);
    }

    private void discoverExistingRegions(long startMajor) {
        List<ChunkInfo> journalChunks = chunkManager.listChunkInfos(ChunkType.CHUNK_JOURNAL);
        long major = 0;
        for (ChunkInfo info : journalChunks) {
            major++;
            if (major < startMajor) {
                continue;
            }
            JournalRegion region = new JournalRegion(major, 0, info.getChunkId());
            regions.put(major, region);
        }
        regionMajorCounter.set(major);
    }


    public JournalRegion getCurrentRegion() throws IOException {
        return currentRegion;
    }

    public void rotateRegion() throws IOException {
        log.info("Manually rotating journal region");
        long startTime = System.nanoTime();
        try {
            allocateNewRegion();
        } finally {
            long latencyMicros = (System.nanoTime() - startTime) / 1000;
            rotateChunkCounter.recordSuccess(latencyMicros);
        }
    }

    private JournalRegion allocateNewRegion() throws IOException {
        allocationLock.lock();
        try {
            if (currentRegion != null) {
                Chunk currentChunk = chunkManager.getCurrentWriteChunk(ChunkType.CHUNK_JOURNAL);
                if (currentChunk != null && currentChunk.getStatus() == ChunkStatus.OPEN) {
                    currentChunk.seal();
                    log.debug("Sealed current journal chunk before allocating new region: chunkId={}", currentChunk.getChunkId());
                }
            }
            // Just tell ChunkManager to create a new chunk, callback will handle region creation
            chunkManager.createJournalChunk();
            return this.currentRegion;  // Re-read volatile field after callbac
        } finally {
            allocationLock.unlock();
        }
    }

    private void persistRegionInfo() throws IOException {
        if (regionIndexFile == null) {
            return;
        }
        
        regionIndexFile.clearEntries();
        
        for (Map.Entry<Long, JournalRegion> entry : regions.entrySet()) {
            JournalRegion region = entry.getValue();
            JournalRegionIndexFile.RegionEntry regionEntry = new JournalRegionIndexFile.RegionEntry(
                region.getMajor(),
                region.getMinor(),
                region.getChunkId(),
                0,
                0,
                System.currentTimeMillis()
            );
            regionIndexFile.addEntry(regionEntry);
        }
        
        regionIndexFile.persist();
        log.debug("Persisted {} journal region entries", regions.size());
    }

    public long getNextSequenceNumber() {
        return sequenceNumber.getAndIncrement();
    }

    public void close() throws IOException {
        if (currentRegion != null) {
            persistRegionInfo();
        }
        log.info("JournalRegionManager closed with {} regions", regions.size());
    }

    public JournalReplayPoint write(OperationType type, IndexKey key, IndexValue value) throws IOException {
        JournalEntry entry = createEntry(type, key, value);
        List<JournalEntry> entries = List.of(entry);
        return batchWrite(entries).get(0);
    }

    public JournalReplayPoint writeBatch(List<JournalEntry.KeyValuePair> operations) throws IOException {
        JournalEntry entry = JournalEntry.batch(operations, getNextSequenceNumber());
        List<JournalEntry> entries = List.of(entry);
        return batchWrite(entries).get(0);
    }

    public List<JournalReplayPoint> batchWrite(List<JournalEntry> entries) throws IOException {
        try {
            return batchWriteAsync(entries).join();
        } catch (Exception e) {
            if (e.getCause() instanceof IOException) {
                throw (IOException) e.getCause();
            }
            throw new IOException("Failed to batch write journal entries", e);
        }
    }

    public CompletableFuture<List<JournalReplayPoint>> batchWriteAsync(List<JournalEntry> entries) {
        if (entries == null || entries.isEmpty()) {
            return CompletableFuture.failedFuture(
                new IllegalArgumentException("entries must not be null or empty"));
        }

        long startTime = System.nanoTime();
        PerformanceCounter counter = entries.size() == 1 ? writeCounter : writeBatchCounter;

        log.debug("Batch writing {} journal entries", entries.size());

        List<CompletableFuture<JournalReplayPoint>> futures = new ArrayList<>();
        for (JournalEntry entry : entries) {
            futures.add(writeEntryAsync(entry));
        }

        return CompletableFuture.allOf(futures.toArray(new CompletableFuture[0]))
            .thenApply(v -> {
                List<JournalReplayPoint> points = new ArrayList<>();
                for (CompletableFuture<JournalReplayPoint> future : futures) {
                    points.add(future.join());
                }
                return points;
            })
            .whenComplete((result, error) -> {
                long latencyMicros = (System.nanoTime() - startTime) / 1000;
                if (error == null) {
                    counter.recordSuccess(latencyMicros);
                } else {
                    counter.recordError();
                }
            });
    }

    public ReplayResult replayFrom(JournalReplayPoint point, JournalReplayHandler handler) throws IOException {
        if (point == null) {
            throw new IllegalArgumentException("point must not be null");
        }
        if (handler == null) {
            throw new IllegalArgumentException("handler must not be null");
        }

        log.info("Starting journal replay from point: regionMajor={}, offset={}", point.getRegionMajor(), point.getOffset());

        int totalErrorCount = 0;
        JournalReplayPoint lastReplayPoint = point;

        for (Map.Entry<Long, JournalRegion> regionEntry : regions.entrySet()) {
            long major = regionEntry.getKey();
            if (major < point.getRegionMajor()) {
                continue;
            }

            JournalRegion region = regionEntry.getValue();
            Chunk chunk = chunkManager.getChunk(region.getChunkId());
            if (chunk == null) {
                log.warn("Chunk not found for region: major={}, chunkId={}", major, region.getChunkId());
                continue;
            }

            int startOffset = (major == point.getRegionMajor()) ? point.getOffset() : chunkHeaderSize();
            ChunkReplayResult result = replayChunkFromOffset(chunk, major, region.getMinor(), startOffset, handler);
            totalErrorCount += result.errorCount;
            
            if (result.lastOffset > 0) {
                lastReplayPoint = new JournalReplayPoint(major, region.getMinor(), result.lastOffset);
            }
        }
        
        if (totalErrorCount > 0) {
            log.warn("Journal replay completed with {} errors, last replay point: {}", totalErrorCount, lastReplayPoint);
        } else {
            log.info("Journal replay completed successfully, last replay point: {}", lastReplayPoint);
        }
        
        return new ReplayResult(totalErrorCount, lastReplayPoint);
    }

    public ReplayResult replayFromBeginning(JournalReplayHandler handler) throws IOException {
        return replayFrom(new JournalReplayPoint(0, 0, chunkHeaderSize()), handler);
    }

    private JournalEntry createEntry(OperationType type, IndexKey key, IndexValue value) {
        return switch (type) {
            case PUT -> JournalEntry.put(key, value, getNextSequenceNumber());
            case DELETE -> JournalEntry.delete(key, getNextSequenceNumber());
            default -> throw new IllegalArgumentException("Unsupported operation type for single write: " + type);
        };
    }

    private CompletableFuture<JournalReplayPoint> writeEntryAsync(JournalEntry entry) {
        byte[] body = entry.toProto().toByteArray();

        if (this.currentRegion == null) {
            try {
                chunkManager.createJournalChunk();
            } catch (IOException e) {
                return CompletableFuture.failedFuture(e);
            }
        }

        final var regionBeforeWrite = this.currentRegion;

        return chunkManager.writeJournalAsync(body)
                .thenApply(location -> {
                    var region = regionBeforeWrite;
                    // Only lookup region by chunkId if chunk actually switched during write
                    if (!this.currentRegion.getChunkId().equals(region.getChunkId())) {
                        // Chunk switched, lookup region by chunkId
                        region = findRegionByChunkId(location.getChunkId());
                        if (region == null) {
                            throw new IllegalStateException("Region not found for chunk: " + location.getChunkId());
                        }
                    }
                    return new JournalReplayPoint(
                            region.getMajor(),
                            region.getMinor(),
                            (int) location.getOffset());
                });
    }

    private static class ChunkReplayResult {
        final int errorCount;
        final int lastOffset;

        ChunkReplayResult(int errorCount, int lastOffset) {
            this.errorCount = errorCount;
            this.lastOffset = lastOffset;
        }
    }

    private ChunkReplayResult replayChunkFromOffset(Chunk chunk, long regionMajor, long regionMinor, int startOffset, JournalReplayHandler handler) throws IOException {
        long fileLength = chunk.getFileSize();
        int offset = startOffset;
        int errorCount = 0;
        int lastSuccessfulOffset = startOffset;

        while (offset < fileLength) {
            try {
                if (offset + WriteItem.HEADER_SIZE > fileLength) {
                    break;
                }

                byte[] headerBytes = chunk.readRaw(offset, WriteItem.HEADER_SIZE);
                java.nio.ByteBuffer bb = java.nio.ByteBuffer.wrap(headerBytes).order(java.nio.ByteOrder.BIG_ENDIAN);
                short magic = bb.getShort();
                if (magic != WriteItem.MAGIC) {
                    break;
                }
                bb.getShort();
                int bodyLength = bb.getInt();

                int totalSize = org.hyperkv.lsmplus.utils.AlignmentUtil.alignTo4K(
                        WriteItem.HEADER_SIZE + bodyLength + WriteItem.CRC32_SIZE);

                if (offset + totalSize > fileLength) {
                    break;
                }

                byte[] itemBytes = chunk.readRaw(offset, totalSize);
                WriteItem item = WriteItem.fromByteArray(itemBytes, 0);

                if (!item.validate()) {
                    log.error("CRC validation failed for journal entry at offset {} in chunk {}", offset, chunk.getChunkId());
                    errorCount++;
                    break;
                }

                byte[] body = item.getBody();
                JournalEntryProto proto = JournalEntryProto.parseFrom(body);
                JournalEntry journalEntry = JournalEntry.fromProto(proto);

                JournalReplayPoint currentReplayPoint = new JournalReplayPoint(regionMajor, regionMinor, offset);
                handler.handle(journalEntry, currentReplayPoint);

                lastSuccessfulOffset = offset + totalSize;
                offset += totalSize;

            } catch (Exception e) {
                log.error("Error replaying journal entry at offset {} in chunk {}: {}", offset, chunk.getChunkId(), e.getMessage());
                errorCount++;
                break;
            }
        }
        
        return new ChunkReplayResult(errorCount, lastSuccessfulOffset);
    }

    private int chunkHeaderSize() {
        return org.hyperkv.lsmplus.storage.ChunkHeader.HEADER_SIZE;
    }

    private JournalRegion findRegionByChunkId(UUID chunkId) {
        for (JournalRegion region : regions.values()) {
            if (region.getChunkId().equals(chunkId)) {
                return region;
            }
        }
        return null;
    }

    @Override
    public void onJournalChunkAllocated(UUID chunkId) {
        allocationLock.lock();
        try {
            long newMajor = regionMajorCounter.incrementAndGet();
            JournalRegion newRegion = new JournalRegion(newMajor, 0, chunkId);
            regions.put(newMajor, newRegion);
            currentRegion = newRegion;

            try {
                persistRegionInfo();
                log.info("Updated journal region on chunk allocation: major={}, chunkId={}", newMajor, chunkId);
            } catch (IOException e) {
                log.error("Failed to persist region info after chunk allocation: chunkId={}", chunkId, e);
            }
        } finally {
            allocationLock.unlock();
        }
    }
}
