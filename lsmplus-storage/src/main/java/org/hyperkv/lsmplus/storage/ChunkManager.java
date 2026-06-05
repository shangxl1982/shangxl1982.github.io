package org.hyperkv.lsmplus.storage;

import org.hyperkv.lsmplus.exception.Exceptions;
import org.hyperkv.lsmplus.proto.Common.ChunkStatus;
import org.hyperkv.lsmplus.proto.Common.ChunkType;
import org.hyperkv.lsmplus.storage.io.FileIOFactory;
import org.hyperkv.lsmplus.storage.io.IOFactory;
import org.hyperkv.lsmplus.storage.io.VirtualDataPath;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

import java.io.File;
import java.io.IOException;
import java.util.ArrayList;
import java.util.Collections;
import java.util.List;
import java.util.Map;
import java.util.UUID;
import java.util.concurrent.CompletableFuture;
import java.util.concurrent.ConcurrentHashMap;
import java.util.concurrent.Executors;
import java.util.concurrent.ScheduledExecutorService;
import java.util.concurrent.TimeUnit;
import java.util.concurrent.atomic.AtomicLong;

public class ChunkManager implements AutoCloseable {

    private static final Logger log = LoggerFactory.getLogger(ChunkManager.class);
    private static final long SEAL_CHECK_INTERVAL_MILLIS = 5 * 60 * 1000L;
    private static final long KEEP_ALIVE_EXTEND_MILLIS = 30 * 60 * 1000L;
    private static final int DEFAULT_CHUNK_CACHE_SIZE = 64;

    public interface JournalChunkAllocationListener {
        void onJournalChunkAllocated(UUID chunkId);
    }

    private final String basePath;
    private final UUID ownerId;
    private final UUID namespaceId;
    private final IOFactory ioFactory;
    private final Map<UUID, ChunkInfo> chunkInfos;
    private final ChunkCache openChunkCache;
    private final AtomicLong chunkNumberCounter;
    private final Object lock = new Object();
    private final ChunkMetadataFile metadataFile;
    private final ScheduledExecutorService sealCheckScheduler;
    private JournalChunkAllocationListener journalChunkListener;

    private Chunk currentLeafChunk;
    private Chunk currentIndexChunk;
    private Chunk currentJournalChunk;
    
    private AsyncBatchWriter leafPageAsyncWriter;
    private AsyncBatchWriter indexPageAsyncWriter;
    private AsyncBatchWriter journalAsyncWriter;

    public ChunkManager(String basePath, UUID ownerId, UUID namespaceId) throws IOException {
        this(basePath, ownerId, namespaceId, DEFAULT_CHUNK_CACHE_SIZE, FileIOFactory.INSTANCE);
    }

    public ChunkManager(String basePath, UUID ownerId, UUID namespaceId, int chunkCacheSize) throws IOException {
        this(basePath, ownerId, namespaceId, chunkCacheSize, FileIOFactory.INSTANCE);
    }

    public ChunkManager(String basePath, UUID ownerId, UUID namespaceId, int chunkCacheSize, IOFactory ioFactory) throws IOException {
        if (basePath == null || basePath.isEmpty()) {
            throw Exceptions.invalidArgument("basePath must not be null or empty");
        }
        if (ownerId == null) {
            throw Exceptions.invalidArgument("ownerId must not be null");
        }
        if (namespaceId == null) {
            throw Exceptions.invalidArgument("namespaceId must not be null");
        }
        if (ioFactory == null) {
            throw Exceptions.invalidArgument("ioFactory must not be null");
        }

        this.basePath = basePath;
        this.ownerId = ownerId;
        this.namespaceId = namespaceId;
        this.ioFactory = ioFactory;
        this.chunkInfos = new ConcurrentHashMap<>();
        this.openChunkCache = new ChunkCache(chunkCacheSize);
        this.chunkNumberCounter = new AtomicLong(0);

        ioFactory.createDirectories(ioFactory.createChunkPath(basePath, "data", "dummy"));
        ioFactory.createDirectories(ioFactory.createChunkPath(basePath, "journal", "dummy"));
        ioFactory.createDirectories(VirtualDataPath.file(basePath + File.separator + "occupancy"));

        VirtualDataPath metadataPath = VirtualDataPath.file(basePath + File.separator + "chunk-metadata.pb");
        this.metadataFile = ChunkMetadataFile.load(metadataPath, ownerId, namespaceId);

        loadFromMetadataFile();
        recoverOpenChunks();
        
        this.sealCheckScheduler = Executors.newSingleThreadScheduledExecutor(r -> {
            Thread t = new Thread(r, "chunk-seal-checker");
            t.setDaemon(true);
            return t;
        });
        startSealCheckTask();
        
        log.info("Initialized ChunkManager: basePath={}, ownerId={}, namespaceId={}, chunks={}, cacheSize={}", 
            basePath, ownerId, namespaceId, chunkInfos.size(), chunkCacheSize);
    }

    private void startSealCheckTask() {
        sealCheckScheduler.scheduleAtFixedRate(
            this::checkAndSealExpiredChunks,
            SEAL_CHECK_INTERVAL_MILLIS,
            SEAL_CHECK_INTERVAL_MILLIS,
            TimeUnit.MILLISECONDS
        );
        log.debug("Started seal check task with interval {}ms", SEAL_CHECK_INTERVAL_MILLIS);
    }

    private void checkAndSealExpiredChunks() {
        try {
            extendActiveChunks();
            sealExpiredChunks();
        } catch (Exception e) {
            log.error("Error during seal check", e);
        }
    }

    private void extendActiveChunks() {
        synchronized (lock) {
            if (currentJournalChunk != null && 
                currentJournalChunk.getStatus() == ChunkStatus.OPEN) {
                currentJournalChunk.extend(KEEP_ALIVE_EXTEND_MILLIS);
                UUID chunkId = currentJournalChunk.getChunkId();
                ChunkInfo info = chunkInfos.get(chunkId);
                if (info != null) {
                    info.setKeepAliveTime(System.currentTimeMillis() + KEEP_ALIVE_EXTEND_MILLIS);
                }
                log.trace("Extended active journal chunk: chunkId={}", chunkId);
            }
            if (currentLeafChunk != null && 
                currentLeafChunk.getStatus() == ChunkStatus.OPEN) {
                currentLeafChunk.extend(KEEP_ALIVE_EXTEND_MILLIS);
                UUID chunkId = currentLeafChunk.getChunkId();
                ChunkInfo info = chunkInfos.get(chunkId);
                if (info != null) {
                    info.setKeepAliveTime(System.currentTimeMillis() + KEEP_ALIVE_EXTEND_MILLIS);
                }
                log.trace("Extended active leaf chunk: chunkId={}", chunkId);
            }
            if (currentIndexChunk != null && 
                currentIndexChunk.getStatus() == ChunkStatus.OPEN) {
                currentIndexChunk.extend(KEEP_ALIVE_EXTEND_MILLIS);
                UUID chunkId = currentIndexChunk.getChunkId();
                ChunkInfo info = chunkInfos.get(chunkId);
                if (info != null) {
                    info.setKeepAliveTime(System.currentTimeMillis() + KEEP_ALIVE_EXTEND_MILLIS);
                }
                log.trace("Extended active index chunk: chunkId={}", chunkId);
            }
        }
    }

    private void sealExpiredChunks() {
        List<ChunkInfo> expiredChunks = new ArrayList<>();
        
        for (ChunkInfo info : chunkInfos.values()) {
            if (info.getStatus() == ChunkStatus.OPEN && info.isKeepAliveExpired()) {
                expiredChunks.add(info);
            }
        }
        
        int sealedCount = 0;
        for (ChunkInfo info : expiredChunks) {
            try {
                synchronized (lock) {
                    if (info.getStatus() == ChunkStatus.OPEN) {
                        info.setStatus(ChunkStatus.SEALED);
                        sealedCount++;
                        
                        if (currentLeafChunk != null && currentLeafChunk.getChunkId().equals(info.getChunkId())) {
                            openChunkCache.remove(info.getChunkId());
                            currentLeafChunk = null;
                        } else if (currentIndexChunk != null && currentIndexChunk.getChunkId().equals(info.getChunkId())) {
                            openChunkCache.remove(info.getChunkId());
                            currentIndexChunk = null;
                        } else if (currentJournalChunk != null && currentJournalChunk.getChunkId().equals(info.getChunkId())) {
                            openChunkCache.remove(info.getChunkId());
                            currentJournalChunk = null;
                        }
                        
                        log.info("Auto-sealed expired chunk: chunkId={}", info.getChunkId());
                    }
                }
            } catch (Exception e) {
                log.error("Failed to seal expired chunk: chunkId={}", info.getChunkId(), e);
            }
        }
        
        if (sealedCount > 0) {
            persistChunkMetadata();
            log.info("Auto-sealed {} expired chunks", sealedCount);
        }
    }

    private void loadFromMetadataFile() {
        long maxNumber = 0;
        for (ChunkMetadataFile.ChunkEntry entry : metadataFile.getEntries()) {
            ChunkInfo info = ChunkInfo.fromEntry(entry, basePath, ioFactory);
            chunkInfos.put(info.getChunkId(), info);
            maxNumber = Math.max(maxNumber, info.getChunkNumber());
        }
        chunkNumberCounter.set(maxNumber);
        log.debug("Loaded {} chunk metadata entries", chunkInfos.size());
    }

    private void recoverOpenChunks() {
        for (ChunkInfo info : chunkInfos.values()) {
            if (info.getStatus() == ChunkStatus.OPEN) {
                try {
                    Chunk chunk = Chunk.openForRecovery(info, ioFactory.createIO(), ioFactory);
                    chunk.recover();
                    chunk.seal();
                    openChunkCache.put(info.getChunkId(), chunk);
                    
                    info.setStatus(ChunkStatus.SEALED);
                    info.setUsedSize(chunk.getValidDataSize());
                    
                    log.info("Recovered and sealed open chunk: chunkId={}, validDataSize={}", 
                        info.getChunkId(), chunk.getValidDataSize());
                } catch (IOException e) {
                    log.error("Failed to recover open chunk: chunkId={}", info.getChunkId(), e);
                }
            }
        }
        persistChunkMetadata();
    }

    public CompletableFuture<SegmentLocation> writeJournalAsync(byte[] data) {
        return writeDataAsync(data, ChunkType.CHUNK_JOURNAL, WriteItem.TYPE_JOURNAL_ENTRY);
    }

    public Chunk getChunk(UUID chunkId) throws IOException {
        return getChunkForRead(chunkId);
    }

    public Chunk getCurrentWriteChunk(ChunkType chunkType) {
        synchronized (lock) {
            return switch (chunkType) {
                case CHUNK_LEAF -> currentLeafChunk;
                case CHUNK_INDEX -> currentIndexChunk;
                case CHUNK_JOURNAL -> currentJournalChunk;
                default -> null;
            };
        }
    }

    public UUID createJournalChunk() throws IOException {
        synchronized (lock) {
            if (currentJournalChunk != null && currentJournalChunk.getStatus() == ChunkStatus.OPEN) {
                return currentJournalChunk.getChunkId();
            }
            currentJournalChunk = allocateChunk(ChunkType.CHUNK_JOURNAL);
            return currentJournalChunk.getChunkId();
        }
    }

    public Chunk getChunkForRead(UUID chunkId) throws IOException {
        Chunk cached = openChunkCache.get(chunkId);
        if (cached != null) {
            return cached;
        }
        
        ChunkInfo info = chunkInfos.get(chunkId);
        if (info == null) {
            return null;
        }
        
        Chunk chunk = Chunk.openForRead(info, ioFactory.createIO());
        openChunkCache.put(chunkId, chunk);
        return chunk;
    }

    public ChunkInfo getChunkInfo(UUID chunkId) {
        return chunkInfos.get(chunkId);
    }

    public List<ChunkInfo> listChunkInfos(ChunkType type) {
        List<ChunkInfo> result = new ArrayList<>();
        for (ChunkInfo info : chunkInfos.values()) {
            if (info.getChunkType() == type) {
                result.add(info);
            }
        }
        return Collections.unmodifiableList(result);
    }

    public void deleteChunk(UUID chunkId) throws IOException {
        synchronized (lock) {
            openChunkCache.remove(chunkId);
            ChunkInfo info = chunkInfos.remove(chunkId);
            
            if (info != null) {
                ioFactory.delete(info.getPath());
                log.debug("Deleted chunk: chunkId={}, path={}", chunkId, info.getPath());
            }
            
            if (currentLeafChunk != null && currentLeafChunk.getChunkId().equals(chunkId)) {
                currentLeafChunk = null;
            } else if (currentIndexChunk != null && currentIndexChunk.getChunkId().equals(chunkId)) {
                currentIndexChunk = null;
            } else if (currentJournalChunk != null && currentJournalChunk.getChunkId().equals(chunkId)) {
                currentJournalChunk = null;
            }
        }
    }

    @Override
    public void close() throws IOException {
        log.info("Closing ChunkManager with {} chunk infos, {} open chunks", 
            chunkInfos.size(), openChunkCache.size());
        
        stopAsyncWriters();
        
        sealCheckScheduler.shutdown();
        try {
            if (!sealCheckScheduler.awaitTermination(10, TimeUnit.SECONDS)) {
                sealCheckScheduler.shutdownNow();
            }
        } catch (InterruptedException e) {
            sealCheckScheduler.shutdownNow();
            Thread.currentThread().interrupt();
        }

        // close all open chunks
        int n = 0;
        for (Chunk chunk : openChunkCache.getAll()) {
            if (chunk.getStatus() == ChunkStatus.OPEN) {
                chunk.seal();
                n++;
            }
        }
        log.info("Sealing {} open chunks", n);

        synchronized (lock) {
            persistChunkMetadata();
            openChunkCache.clear();
            chunkInfos.clear();
            currentLeafChunk = null;
            currentIndexChunk = null;
            currentJournalChunk = null;
        }
        log.info("ChunkManager closed");
    }

    List<SegmentLocation> writeDataBatch(List<byte[]> dataList, ChunkType chunkType, short writeItemType) throws IOException {
        synchronized (lock) {
            Chunk chunk = getOrCreateChunk(chunkType);

            // Check if current chunk has enough space BEFORE writing
            int totalRequiredSpace = 0;
            for (byte[] data : dataList) {
                WriteItem item = new WriteItem(writeItemType, data);
                totalRequiredSpace += item.getTotalSize();
            }
            if (chunk.getRemainingSpace() < totalRequiredSpace) {
                sealCurrentChunk(chunkType);
                chunk = getOrCreateChunk(chunkType);
            }

            List<SegmentLocation> locations = chunk.writeBatch(dataList, writeItemType);

            if (chunk.getRemainingSpace() < WriteItem.ALIGNMENT) {
                sealCurrentChunk(chunkType);
            }

            return locations;
        }
    }

    public CompletableFuture<SegmentLocation> writeLeafPageAsync(byte[] data) {
        return writeDataAsync(data, ChunkType.CHUNK_LEAF, WriteItem.TYPE_PAGE_DATA);
    }

    public CompletableFuture<SegmentLocation> writeIndexPageAsync(byte[] data) {
        return writeDataAsync(data, ChunkType.CHUNK_INDEX, WriteItem.TYPE_PAGE_DATA);
    }

    private CompletableFuture<SegmentLocation> writeDataAsync(byte[] data, ChunkType chunkType, short writeItemType) {
        AsyncBatchWriter writer = getOrCreateAsyncWriter(chunkType);
        return writer.submit(data);
    }

    private AsyncBatchWriter getOrCreateAsyncWriter(ChunkType chunkType) {
        return switch (chunkType) {
            case CHUNK_LEAF -> {
                if (leafPageAsyncWriter == null) {
                    leafPageAsyncWriter = new AsyncBatchWriter(this, chunkType, WriteItem.TYPE_PAGE_DATA);
                }
                yield leafPageAsyncWriter;
            }
            case CHUNK_INDEX -> {
                if (indexPageAsyncWriter == null) {
                    indexPageAsyncWriter = new AsyncBatchWriter(this, chunkType, WriteItem.TYPE_PAGE_DATA);
                }
                yield indexPageAsyncWriter;
            }
            case CHUNK_JOURNAL -> {
                if (journalAsyncWriter == null) {
                    journalAsyncWriter = new AsyncBatchWriter(this, chunkType, WriteItem.TYPE_JOURNAL_ENTRY);
                }
                yield journalAsyncWriter;
            }
            default -> throw new IllegalArgumentException("Async writer not supported for chunk type: " + chunkType);
        };
    }

    public void flushAsyncWrites() throws IOException, InterruptedException {
        if (leafPageAsyncWriter != null) {
            leafPageAsyncWriter.flush();
        }
        if (indexPageAsyncWriter != null) {
            indexPageAsyncWriter.flush();
        }
        if (journalAsyncWriter != null) {
            journalAsyncWriter.flush();
        }
    }

    public void stopAsyncWriters() {
        if (leafPageAsyncWriter != null) {
            leafPageAsyncWriter.stop();
        }
        if (indexPageAsyncWriter != null) {
            indexPageAsyncWriter.stop();
        }
        if (journalAsyncWriter != null) {
            journalAsyncWriter.stop();
        }
    }

    private Chunk getOrCreateChunk(ChunkType chunkType) throws IOException {
        return switch (chunkType) {
            case CHUNK_LEAF -> {
                if (currentLeafChunk == null || currentLeafChunk.getStatus() != ChunkStatus.OPEN) {
                    currentLeafChunk = allocateChunk(chunkType);
                }
                yield currentLeafChunk;
            }
            case CHUNK_INDEX -> {
                if (currentIndexChunk == null || currentIndexChunk.getStatus() != ChunkStatus.OPEN) {
                    currentIndexChunk = allocateChunk(chunkType);
                }
                yield currentIndexChunk;
            }
            case CHUNK_JOURNAL -> {
                if (currentJournalChunk == null || currentJournalChunk.getStatus() != ChunkStatus.OPEN) {
                    currentJournalChunk = allocateChunk(chunkType);
                }
                yield currentJournalChunk;
            }
            default -> throw Exceptions.invalidArgument("Unknown chunk type: " + chunkType);
        };
    }

    private Chunk allocateChunk(ChunkType chunkType) throws IOException {
        UUID chunkId = UUID.randomUUID();
        String chunkTypeStr = (chunkType == ChunkType.CHUNK_JOURNAL) ? "journal" : "data";
        VirtualDataPath path = ioFactory.createChunkPath(basePath, chunkTypeStr, chunkId.toString());

        ioFactory.createDirectories(path);
        Chunk chunk = new Chunk(path, chunkType, ownerId, namespaceId, ioFactory.createIO());
        
        long chunkNumber = chunkNumberCounter.incrementAndGet();
        long now = System.currentTimeMillis();
        ChunkInfo info = new ChunkInfo(
            chunkId, chunkNumber, chunkType, ownerId, namespaceId,
            ChunkStatus.OPEN, now, now + Chunk.DEFAULT_KEEP_ALIVE_MILLIS,
            0, 0, 0, path
        );
        
        chunkInfos.put(chunkId, info);
        openChunkCache.put(chunkId, chunk);

        log.debug("Allocated new chunk: chunkId={}, type={}, path={}", chunkId, chunkType, path);

        // Notify listener if this is a journal chunk
        if (chunkType == ChunkType.CHUNK_JOURNAL && journalChunkListener != null) {
            journalChunkListener.onJournalChunkAllocated(chunkId);
        }

        return chunk;
    }

    private void sealCurrentChunk(ChunkType chunkType) throws IOException {
        synchronized (lock) {
            switch (chunkType) {
                case CHUNK_LEAF -> {
                    if (currentLeafChunk != null) {
                        currentLeafChunk.seal();
                        UUID chunkId = currentLeafChunk.getChunkId();
                        ChunkInfo info = chunkInfos.get(chunkId);
                        if (info != null) {
                            info.setStatus(ChunkStatus.SEALED);
                        }
                        openChunkCache.remove(chunkId);
                        log.debug("Sealed current leaf chunk: chunkId={}", chunkId);
                        currentLeafChunk = null;
                    }
                }
                case CHUNK_INDEX -> {
                    if (currentIndexChunk != null) {
                        currentIndexChunk.seal();
                        UUID chunkId = currentIndexChunk.getChunkId();
                        ChunkInfo info = chunkInfos.get(chunkId);
                        if (info != null) {
                            info.setStatus(ChunkStatus.SEALED);
                        }
                        openChunkCache.remove(chunkId);
                        log.debug("Sealed current index chunk: chunkId={}", chunkId);
                        currentIndexChunk = null;
                    }
                }
                case CHUNK_JOURNAL -> {
                    if (currentJournalChunk != null) {
                        currentJournalChunk.seal();
                        UUID chunkId = currentJournalChunk.getChunkId();
                        ChunkInfo info = chunkInfos.get(chunkId);
                        if (info != null) {
                            info.setStatus(ChunkStatus.SEALED);
                        }
                        openChunkCache.remove(chunkId);
                        log.debug("Sealed current journal chunk: chunkId={}", chunkId);
                        currentJournalChunk = null;
                    }
                }
                default -> {}
            }
            persistChunkMetadata();
        }
    }

    private void persistChunkMetadata() {
        try {
            metadataFile.clearEntries();
            for (ChunkInfo info : chunkInfos.values()) {
                metadataFile.addEntry(info.toEntry());
            }
            metadataFile.persist();
            log.debug("Persisted chunk metadata with {} entries", chunkInfos.size());
        } catch (IOException e) {
            log.error("Failed to persist chunk metadata", e);
        }
    }

    public String getBasePath() {
        return basePath;
    }

    public void extendChunk(UUID chunkId, long durationMillis) {
        if (chunkId == null) {
            throw Exceptions.invalidArgument("chunkId must not be null");
        }
        if (durationMillis <= 0) {
            throw Exceptions.invalidArgument("durationMillis must be positive: " + durationMillis);
        }

        ChunkInfo info = chunkInfos.get(chunkId);
        if (info == null) {
            throw Exceptions.invalidArgument("Chunk not found: " + chunkId);
        }

        info.setKeepAliveTime(System.currentTimeMillis() + durationMillis);
        
        Chunk chunk = openChunkCache.get(chunkId);
        if (chunk != null) {
            chunk.extend(durationMillis);
        }
        
        log.debug("Extended chunk keep-alive: chunkId={}, duration={}ms", chunkId, durationMillis);
    }

    public long getMNS() {
        long minNotSealed = Long.MAX_VALUE;
        for (ChunkInfo info : chunkInfos.values()) {
            if (info.getStatus() == ChunkStatus.OPEN) {
                minNotSealed = Math.min(minNotSealed, info.getChunkNumber());
            }
        }
        return minNotSealed == Long.MAX_VALUE ? 0 : minNotSealed;
    }

    public List<ChunkInfo> getSealedChunkInfos() {
        List<ChunkInfo> result = new ArrayList<>();
        for (ChunkInfo info : chunkInfos.values()) {
            if (info.getStatus() == ChunkStatus.SEALED) {
                result.add(info);
            }
        }
        return Collections.unmodifiableList(result);
    }

    public List<ChunkInfo> getOpenChunkInfos() {
        List<ChunkInfo> result = new ArrayList<>();
        for (ChunkInfo info : chunkInfos.values()) {
            if (info.getStatus() == ChunkStatus.OPEN) {
                result.add(info);
            }
        }
        return Collections.unmodifiableList(result);
    }

    public Long getChunkNumber(UUID chunkId) {
        ChunkInfo info = chunkInfos.get(chunkId);
        return info != null ? info.getChunkNumber() : null;
    }

    public IOFactory getIOFactory() {
        return ioFactory;
    }

    public void setJournalChunkListener(JournalChunkAllocationListener listener) {
        this.journalChunkListener = listener;
    }

    public OccupancyFile createOccupancyFile(long version) {
        VirtualDataPath path = VirtualDataPath.file(basePath + File.separator + "occupancy" + File.separator + version + ".pb");
        return new OccupancyFile(path, version, ioFactory);
    }

    public OccupancyFile loadOccupancyFile(long version) throws IOException {
        VirtualDataPath path = VirtualDataPath.file(basePath + File.separator + "occupancy" + File.separator + version + ".pb");
        return OccupancyFile.load(path, ioFactory);
    }

    public List<Long> listOccupancyVersions() {
        List<Long> versions = new ArrayList<>();
        
        VirtualDataPath occupancyDirPath = VirtualDataPath.file(basePath + File.separator + "occupancy");
        File occupancyDir = new File(occupancyDirPath.getPath());
        
        if (!occupancyDir.exists() || !occupancyDir.isDirectory()) {
            return versions;
        }

        File[] files = occupancyDir.listFiles((d, name) -> name.endsWith(".pb"));
        if (files == null) {
            return versions;
        }

        for (File file : files) {
            String name = file.getName();
            if (name.endsWith(".pb")) {
                try {
                    long version = Long.parseLong(name.substring(0, name.length() - 3));
                    versions.add(version);
                } catch (NumberFormatException ignored) {
                }
            }
        }

        Collections.sort(versions);
        return versions;
    }

    public void deleteOccupancyFilesBefore(long version) throws IOException {
        VirtualDataPath occupancyDirPath = VirtualDataPath.file(basePath + File.separator + "occupancy");
        File occupancyDir = new File(occupancyDirPath.getPath());
        
        if (!occupancyDir.exists() || !occupancyDir.isDirectory()) {
            return;
        }

        File[] files = occupancyDir.listFiles((d, name) -> name.endsWith(".pb"));
        if (files == null) {
            return;
        }

        for (File file : files) {
            String name = file.getName();
            if (name.endsWith(".pb")) {
                try {
                    long fileVersion = Long.parseLong(name.substring(0, name.length() - 3));
                    if (fileVersion < version) {
                        VirtualDataPath path = VirtualDataPath.file(file.getAbsolutePath());
                        ioFactory.delete(path);
                        log.debug("Deleted occupancy file: version={}", fileVersion);
                    }
                } catch (NumberFormatException ignored) {
                }
            }
        }
    }
}
