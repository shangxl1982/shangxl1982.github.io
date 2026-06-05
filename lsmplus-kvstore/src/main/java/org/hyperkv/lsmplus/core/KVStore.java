package org.hyperkv.lsmplus.core;

import org.hyperkv.lsmplus.api.model.IndexKey;
import org.hyperkv.lsmplus.api.model.IndexValue;
import org.hyperkv.lsmplus.api.model.RangeQueryOptions;
import org.hyperkv.lsmplus.api.model.RangeQueryResult;
import org.hyperkv.lsmplus.bplustree.BPlusTree;
import org.hyperkv.lsmplus.bplustree.PageCapacityConfig;
import org.hyperkv.lsmplus.bplustree.PageManager;
import org.hyperkv.lsmplus.bplustree.TreeDumper;
import org.hyperkv.lsmplus.core.concurrency.Snapshot;
import org.hyperkv.lsmplus.exception.Exceptions;
import org.hyperkv.lsmplus.exception.StorageException;
import org.hyperkv.lsmplus.journal.JournalEntry;
import org.hyperkv.lsmplus.journal.JournalRegionManager;
import org.hyperkv.lsmplus.journal.JournalReplayPoint;
import org.hyperkv.lsmplus.gc.MNSTracker;
import org.hyperkv.lsmplus.gc.OccupancyManager;
import org.hyperkv.lsmplus.memory.MemoryTable;
import org.hyperkv.lsmplus.memory.MemoryTableManager;
import org.hyperkv.lsmplus.monitoring.MetricsLogger;
import org.hyperkv.lsmplus.monitoring.MetricsRegistry;
import org.hyperkv.lsmplus.monitoring.PerformanceCounter;
import org.hyperkv.lsmplus.storage.ChunkManager;
import org.hyperkv.lsmplus.storage.StorageLayout;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

import java.io.File;
import java.io.IOException;
import java.util.ArrayList;
import java.util.List;
import java.util.Map;
import java.util.UUID;
import java.util.concurrent.atomic.AtomicReference;

public class KVStore {

    private static final Logger log = LoggerFactory.getLogger(KVStore.class);

    private final File dataDir;
    private final UUID ownerId;
    private final UUID namespaceId;
    private final int memoryTableMaxSize;
    private final int maxSealedTables;
    private final PageCapacityConfig pageCapacityConfig;

    private ChunkManager chunkManager;
    private JournalRegionManager journalRegionManager;
    private MemoryTableManager memoryTableManager;
    private BPlusTree bPlusTree;
    private PageManager pageManager;
    private TreeDumper treeDumper;
    private TreeMetadataManager treeMetadataManager;
    private MNSTracker mnsTracker;
    private OccupancyManager occupancyManager;
    private AsyncDumpExecutor asyncDumpExecutor;
    
    private MetricsLogger metricsLogger;
    private PerformanceCounter putCounter;
    private PerformanceCounter getCounter;
    private PerformanceCounter deleteCounter;

    private final AtomicReference<KVStoreState> state;

    public KVStore(File dataDir, UUID ownerId, UUID namespaceId, 
                   int memoryTableMaxSize, int maxSealedTables,
                   PageCapacityConfig pageCapacityConfig) {
        if (dataDir == null) {
            throw Exceptions.invalidArgument("dataDir must not be null");
        }
        this.dataDir = dataDir;
        this.ownerId = ownerId != null ? ownerId : UUID.randomUUID();
        this.namespaceId = namespaceId != null ? namespaceId : UUID.randomUUID();
        this.memoryTableMaxSize = memoryTableMaxSize > 0 ? memoryTableMaxSize : MemoryTable.DEFAULT_MAX_SIZE;
        this.maxSealedTables = maxSealedTables > 0 ? maxSealedTables : 10;
        this.pageCapacityConfig = pageCapacityConfig != null ? pageCapacityConfig : PageCapacityConfig.DEFAULT;
        this.state = new AtomicReference<>(KVStoreState.CREATED);
    }

    public KVStore(File dataDir, UUID ownerId, UUID namespaceId, 
                   int memoryTableMaxSize, int maxSealedTables) {
        this(dataDir, ownerId, namespaceId, memoryTableMaxSize, maxSealedTables, null);
    }

    public KVStore(File dataDir, PageCapacityConfig pageCapacityConfig) {
        this(dataDir, null, null, 0, 0, pageCapacityConfig);
    }

    public KVStore(File dataDir) {
        this(dataDir, null, null, 0, 0, null);
    }

    public synchronized void start() throws IOException {
        if (!state.compareAndSet(KVStoreState.CREATED, KVStoreState.INITIALIZING)) {
            throw Exceptions.invalidState("KVStore can only be started from CREATED state, current: " + state.get());
        }

        log.info("Starting KVStore: dataDir={}, ownerId={}, namespaceId={}", dataDir, ownerId, namespaceId);

        try {
            if (!dataDir.exists()) {
                dataDir.mkdirs();
            }

            MetricsRegistry.initialize("kvstore");
            MetricsRegistry registry = MetricsRegistry.getInstance();
            
            metricsLogger = new MetricsLogger(
                new File(dataDir, "performance-counter.log").getAbsolutePath()
            );
            registry.addListener(metricsLogger);
            
            putCounter = MetricsRegistry.getCounter("put", "Put operation latency");
            getCounter = MetricsRegistry.getCounter("get", "Get operation latency");
            deleteCounter = MetricsRegistry.getCounter("delete", "Delete operation latency");

            chunkManager = new ChunkManager(dataDir.getAbsolutePath(), ownerId, namespaceId);

            StorageLayout storageLayout = new StorageLayout(dataDir);
            
            treeMetadataManager = new TreeMetadataManager(storageLayout.getTreeMetadataFile());
            
            TreeMetadataManager.TreeVersionInfo treeInfo = treeMetadataManager.loadLatest();
            long startMajor = (treeInfo != null && treeInfo.getReplayPoint() != null) 
                ? treeInfo.getReplayPoint().getRegionMajor() 
                : 0L;
            
            journalRegionManager = new JournalRegionManager(
                chunkManager, 
                storageLayout.getJournalRegionIndexFile(), 
                ownerId,
                startMajor,
                64 * 1024 * 1024
            );

            memoryTableManager = new MemoryTableManager(memoryTableMaxSize);

            pageManager = new PageManager(chunkManager, pageCapacityConfig);

            mnsTracker = new MNSTracker();

            occupancyManager = new OccupancyManager(
                dataDir.getAbsolutePath(), 
                chunkManager, 
                mnsTracker
            );

            bPlusTree = new BPlusTree(pageManager, pageCapacityConfig, mnsTracker);

            treeDumper = new TreeDumper(bPlusTree, pageManager, occupancyManager);

            asyncDumpExecutor = new AsyncDumpExecutor(treeDumper, memoryTableManager, treeMetadataManager);

            memoryTableManager.setDumpCallback(sealedCount -> {
                if (sealedCount >= maxSealedTables) {
                    triggerAsyncDump();
                }
            });

            registry.start();
            log.info("Performance monitoring started, logging to performance-counter.log");

            recover(treeInfo);

            state.set(KVStoreState.RUNNING);
            log.info("KVStore started successfully");
        } catch (Exception e) {
            state.set(KVStoreState.STOPPED);
            log.error("Failed to start KVStore", e);
            cleanupOnInitFailure();
            throw e;
        }
    }

    private void cleanupOnInitFailure() {
        log.info("Cleaning up resources after initialization failure");
        try {
            if (asyncDumpExecutor != null) {
                asyncDumpExecutor.shutdown();
            }
        } catch (Exception ex) {
            log.warn("Error shutting down asyncDumpExecutor during cleanup", ex);
        }
        try {
            if (metricsLogger != null) {
                metricsLogger.close();
            }
        } catch (Exception ex) {
            log.warn("Error closing metricsLogger during cleanup", ex);
        }
        try {
            MetricsRegistry.shutdown();
        } catch (Exception ex) {
            log.warn("Error shutting down MetricsRegistry during cleanup", ex);
        }
        try {
            if (journalRegionManager != null) {
                journalRegionManager.close();
            }
        } catch (Exception ex) {
            log.warn("Error closing journalRegionManager during cleanup", ex);
        }
        try {
            if (chunkManager != null) {
                chunkManager.close();
            }
        } catch (Exception ex) {
            log.warn("Error closing chunkManager during cleanup", ex);
        }
    }

    public synchronized void shutdown() throws IOException {
        if (!state.compareAndSet(KVStoreState.RUNNING, KVStoreState.STOPPING)) {
            throw Exceptions.invalidState("KVStore can only be shutdown from RUNNING state, current: " + state.get());
        }

        log.info("Shutting down KVStore");

        MetricsRegistry.getInstance().collectAndSnapshot();
        try {
            if (asyncDumpExecutor != null) {
                asyncDumpExecutor.shutdownAndWait();
            }
            if (metricsLogger != null) {
                metricsLogger.close();
            }
            MetricsRegistry.shutdown();
            if (journalRegionManager != null) {
                journalRegionManager.close();
            }
            if (chunkManager != null) {
                chunkManager.close();
            }
        } finally {
            state.set(KVStoreState.STOPPED);
            log.info("KVStore shutdown completed");
        }
    }

    private void recover(TreeMetadataManager.TreeVersionInfo treeInfo) throws IOException {
        log.info("Starting recovery");
        
        JournalReplayPoint replayPoint = null;
        
        if (treeInfo != null && treeInfo.getRootLocation() != null) {
            bPlusTree.startFrom(treeInfo);
            replayPoint = treeInfo.getReplayPoint();
            log.info("Loaded tree from metadata: version={}, height={}, rootLocation={}, replayPoint={}", 
                treeInfo.getVersion(), treeInfo.getHeight(), treeInfo.getRootLocation(), replayPoint);
        }
        
        RecoveryHandler handler = new RecoveryHandler(memoryTableManager);
        if (replayPoint != null) {
            log.info("Replaying journal from saved replay point: {}", replayPoint);
            journalRegionManager.replayFrom(replayPoint, handler);
        } else {
            log.info("Replaying journal from beginning");
            journalRegionManager.replayFromBeginning(handler);
        }
        log.info("Recovery completed, recovered {} entries from journal", handler.getRecoveredEntries());
    }

    public void put(IndexKey key, IndexValue value) {
        ensureRunning();

        long startTime = System.nanoTime();
        boolean success = false;
        
        try {
            log.debug("PUT request: key={}", key);

            List<JournalEntry.KeyValuePair> pairs = List.of(
                    new JournalEntry.KeyValuePair(key, value));
            JournalReplayPoint replayPoint = journalRegionManager.writeBatch(pairs);
            memoryTableManager.put(key, value, replayPoint);
            
            log.debug("PUT completed: key={}", key);
            success = true;
        } catch (IOException e) {
            log.error("Failed to write PUT to journal: key={}", key, e);
            throw StorageException.journalWriteFailed("Failed to write to journal", e);
        } finally {
            if (putCounter != null) {
                long latencyMicros = (System.nanoTime() - startTime) / 1000;
                if (success) {
                    putCounter.recordSuccess(latencyMicros);
                } else {
                    putCounter.recordError();
                }
            }
        }
    }

    public IndexValue get(IndexKey key) {
        ensureRunning();

        long startTime = System.nanoTime();
        boolean success = false;
        
        try {
            log.debug("GET request: key={}", key);

            Snapshot snapshot = createSnapshot();
            IndexValue value = snapshot.get(key);

            if (value != null) {
                log.debug("GET result: key={}, found={}", key, !value.isTombstone());
            } else {
                log.debug("GET result: key={}, not found", key);
            }
            
            success = true;
            return value;
        } finally {
            if (getCounter != null) {
                long latencyMicros = (System.nanoTime() - startTime) / 1000;
                if (success) {
                    getCounter.recordSuccess(latencyMicros);
                } else {
                    getCounter.recordError();
                }
            }
        }
    }

    public void delete(IndexKey key) {
        ensureRunning();

        long startTime = System.nanoTime();
        boolean success = false;
        
        try {
            log.debug("DELETE request: key={}", key);
            List<JournalEntry.KeyValuePair> pairs = List.of(
                    new JournalEntry.KeyValuePair(key, IndexValue.tombstone()));
            JournalReplayPoint replayPoint = journalRegionManager.writeBatch(pairs);

            memoryTableManager.delete(key, replayPoint);
            
            log.debug("DELETE completed: key={}", key);
            success = true;
        } catch (IOException e) {
            log.error("Failed to write DELETE to journal: key={}", key, e);
            throw StorageException.journalWriteFailed("Failed to write to journal", e);
        } finally {
            if (deleteCounter != null) {
                long latencyMicros = (System.nanoTime() - startTime) / 1000;
                if (success) {
                    deleteCounter.recordSuccess(latencyMicros);
                } else {
                    deleteCounter.recordError();
                }
            }
        }
    }

    public void batch(List<BatchOperation> operations) {
        ensureRunning();

        if (operations == null || operations.isEmpty()) {
            return;
        }

        log.debug("BATCH request: operationCount={}", operations.size());

        // Validate all operations first
        for (int i = 0; i < operations.size(); i++) {
            BatchOperation op = operations.get(i);
            if (op == null) {
                throw Exceptions.invalidArgument("Batch operation at index " + i + " is null");
            }
            if (op.getKey() == null) {
                throw Exceptions.invalidArgument("Batch operation at index " + i + " has null key");
            }
            if (op.isPut() && op.getValue() == null) {
                throw Exceptions.invalidArgument("Batch PUT operation at index " + i + " has null value");
            }
        }

        try {
            List<JournalEntry.KeyValuePair> pairs = new ArrayList<>();
            for (BatchOperation op : operations) {
                if (op.isPut()) {
                    pairs.add(new JournalEntry.KeyValuePair(op.getKey(), op.getValue()));
                } else {
                    pairs.add(new JournalEntry.KeyValuePair(op.getKey(), IndexValue.tombstone()));
                }
            }

            JournalReplayPoint replayPoint = journalRegionManager.writeBatch(pairs);

            for (BatchOperation op : operations) {
                if (op.isPut()) {
                    memoryTableManager.put(op.getKey(), op.getValue(), replayPoint);
                } else {
                    memoryTableManager.delete(op.getKey(), replayPoint);
                }
            }

            log.debug("BATCH completed: operationCount={}", operations.size());
        } catch (IOException e) {
            log.error("Failed to write BATCH to journal: operationCount={}", operations.size(), e);
            throw StorageException.journalWriteFailed("Failed to write batch to journal", e);
        }
    }

    public List<Map.Entry<IndexKey, IndexValue>> rangeQuery(IndexKey start, IndexKey end) {
        ensureRunning();

        log.debug("RANGE_QUERY request: start={}, end={}", start, end);

        Snapshot snapshot = createSnapshot();
        List<Map.Entry<IndexKey, IndexValue>> results = snapshot.rangeQuery(start, end);

        log.debug("RANGE_QUERY result: count={}", results.size());

        return results;
    }

    public RangeQueryResult rangeQuery(RangeQueryOptions options) {
        ensureRunning();

        if (options == null) {
            options = RangeQueryOptions.DEFAULT;
        }

        log.debug("RANGE_QUERY request: start={}, end={}, limit={}, prefix={}", 
            options.getStart(), options.getEnd(), options.getLimit(), options.getPrefix());

        Snapshot snapshot = createSnapshot();
        RangeQueryResult result = snapshot.rangeQuery(options);

        log.debug("RANGE_QUERY result: count={}, hasMore={}", result.getCount(), result.hasMore());

        return result;
    }

    public Snapshot createSnapshot() {
        ensureRunning();
        return Snapshot.create(memoryTableManager, bPlusTree);
    }

    public IndexValue snapshotGet(Snapshot snapshot, IndexKey key) {
        ensureRunning();
        return snapshot.get(key);
    }

    public void dump() {
        ensureRunning();

        if (memoryTableManager.getSealedTableCount() == 0) {
            log.debug("Dump skipped: no sealed tables");
            return;
        }

        log.info("Starting synchronous dump: sealedTableCount={}", memoryTableManager.getSealedTableCount());

        triggerAsyncDump();
        try {
            boolean completed = asyncDumpExecutor.awaitDumpCompletion(60000);
            if (!completed) {
                log.warn("Dump did not complete within timeout");
            }
        } catch (InterruptedException e) {
            log.error("Interrupted while waiting for async dump completion", e);
            Thread.currentThread().interrupt();
        }

        log.info("Synchronous dump completed");
    }

    public void triggerAsyncDump() {
        if (state.get() != KVStoreState.RUNNING) {
            log.warn("Async dump ignored: store not running, state={}", state.get());
            return;
        }

        if (asyncDumpExecutor == null) {
            log.warn("AsyncDumpExecutor not initialized, falling back to sync dump");
            return;
        }

        if (isDumpInProgress()) {
            log.warn("Async dump request ignored: dump is in progress");
            return;
        }

        log.info("Submitting async dump request: sealedTableCount={}", memoryTableManager.getSealedTableCount());
        asyncDumpExecutor.submitDump();
    }

    public boolean isDumpInProgress() {
        return asyncDumpExecutor != null && asyncDumpExecutor.isDumpInProgress();
    }

    public void sealActiveTable() {
        ensureRunning();
        memoryTableManager.sealActiveTable();
    }

    public int getSealedTableCount() {
        return memoryTableManager.getSealedTableCount();
    }

    public boolean shouldDump() {
        return memoryTableManager.getSealedTableCount() >= maxSealedTables;
    }

    private void ensureRunning() {
        if (state.get() != KVStoreState.RUNNING) {
            throw Exceptions.invalidState("KVStore is not running, current state: " + state.get());
        }
    }

    public KVStoreState getState() {
        return state.get();
    }

    public File getDataDir() {
        return dataDir;
    }

    public UUID getOwnerId() {
        return ownerId;
    }

    public UUID getNamespaceId() {
        return namespaceId;
    }

    public int getMemoryTableMaxSize() {
        return memoryTableMaxSize;
    }

    public int getMaxSealedTables() {
        return maxSealedTables;
    }

    public BPlusTree getBPlusTree() {
        return bPlusTree;
    }

    public MemoryTableManager getMemoryTableManager() {
        return memoryTableManager;
    }

    public JournalRegionManager getJournalRegionManager() {
        return journalRegionManager;
    }

    public ChunkManager getChunkManager() {
        return chunkManager;
    }

    public AsyncDumpExecutor getAsyncDumpExecutor() {
        return asyncDumpExecutor;
    }
}
