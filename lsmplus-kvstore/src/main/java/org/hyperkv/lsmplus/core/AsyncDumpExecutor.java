package org.hyperkv.lsmplus.core;

import org.hyperkv.lsmplus.bplustree.TreeDumper;
import org.hyperkv.lsmplus.memory.MemoryTable;
import org.hyperkv.lsmplus.memory.MemoryTableManager;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

import java.util.List;
import java.util.concurrent.*;
import java.util.concurrent.atomic.AtomicLong;

public class AsyncDumpExecutor {

    private static final Logger log = LoggerFactory.getLogger(AsyncDumpExecutor.class);

    private final TreeDumper treeDumper;
    private final MemoryTableManager memoryTableManager;
    private final ExecutorService executor;
    private final AtomicLong lastDumpTime;
    private final TreeMetadataManager treeMetadataManager;

    private volatile CompletableFuture<Void> dumpFuture;

    public AsyncDumpExecutor(TreeDumper treeDumper, MemoryTableManager memoryTableManager, TreeMetadataManager treeMetadataManager) {
        this.treeDumper = treeDumper;
        this.treeMetadataManager = treeMetadataManager;
        this.memoryTableManager = memoryTableManager;
        this.executor = Executors.newSingleThreadExecutor(r -> {
            Thread t = new Thread(r, "async-dump-executor");
            t.setDaemon(true);
            return t;
        });
        this.lastDumpTime = new AtomicLong(0);
        log.info("AsyncDumpExecutor initialized");
    }

    public CompletableFuture<Void> submitDump() {
        if (executor.isShutdown()) {
            log.warn("AsyncDumpExecutor is shut down, dump request rejected");
            return CompletableFuture.failedFuture(new RejectedExecutionException("Executor is shut down"));
        }

        CompletableFuture<Void> current = dumpFuture;
        if (current != null && !current.isDone()) {
            log.debug("Dump already in progress, returning existing future");
            return current;
        }

        log.debug("Submitting dump task to executor");
        CompletableFuture<Void> newFuture = new CompletableFuture<>();
        dumpFuture = newFuture;
        
        executor.submit(() -> {
            try {
                executeDump();
                newFuture.complete(null);
            } catch (Exception e) {
                log.error("Async dump failed", e);
                newFuture.completeExceptionally(e);
            }
        });
        
        log.debug("Dump task submitted successfully");
        return newFuture;
    }

    private void executeDump() {
        List<MemoryTable> tables = null;
        try {
            int sealedCount = memoryTableManager.getSealedTableCount();
            if (sealedCount == 0) {
                log.debug("No sealed tables to dump");
                return;
            }

            log.info("Starting async dump of {} sealed tables", sealedCount);
            long startTime = System.currentTimeMillis();
            tables = memoryTableManager.getSealedTables();
            tables.forEach(MemoryTable::setForDump);
            SealedTablesMerger.MergeResult mergeResult =
                    SealedTablesMerger.merge(tables);
            if (!mergeResult.isEmpty()) {
                TreeMetadataManager.TreeVersionInfo info =
                        treeDumper.dump(mergeResult.getEntries(), mergeResult.getReplayPoint());
                treeMetadataManager.save(info);
            }
            treeDumper.promoteRoot();
            tables.forEach(MemoryTable::setForClear);
            memoryTableManager.clearSealedTables();

            long duration = System.currentTimeMillis() - startTime;
            lastDumpTime.set(System.currentTimeMillis());
            log.info("Async dump completed in {} ms", duration);

        } catch (Exception e) {
            log.error("Async dump failed, performing rollback", e);
            rollbackDump(tables);
            if (e instanceof RuntimeException) {
                throw (RuntimeException) e;
            }
            throw new RuntimeException(e);
        }
    }

    private void rollbackDump(List<MemoryTable> tables) {
        if (tables == null) {
            return;
        }
        log.info("Rolling back dump for {} tables", tables.size());
        for (MemoryTable table : tables) {
            try {
                table.resetFromDumping();
            } catch (Exception ex) {
                log.warn("Failed to rollback table state", ex);
            }
        }
    }

    public boolean isDumpInProgress() {
        CompletableFuture<Void> current = dumpFuture;
        return current != null && !current.isDone();
    }

    public long getLastDumpTime() {
        return lastDumpTime.get();
    }

    public void shutdown() {
        log.info("Shutting down AsyncDumpExecutor");

        executor.shutdown();
        try {
            if (!executor.awaitTermination(30, TimeUnit.SECONDS)) {
                log.warn("AsyncDumpExecutor did not terminate gracefully, forcing shutdown");
                executor.shutdownNow();

                if (!executor.awaitTermination(10, TimeUnit.SECONDS)) {
                    log.error("AsyncDumpExecutor did not terminate");
                }
            }
        } catch (InterruptedException e) {
            log.warn("Interrupted while waiting for AsyncDumpExecutor to terminate");
            executor.shutdownNow();
            Thread.currentThread().interrupt();
        }

        log.info("AsyncDumpExecutor shutdown completed");
    }

    public void shutdownAndWait() {
        shutdown();

        CompletableFuture<Void> current = dumpFuture;
        if (current != null && !current.isDone()) {
            try {
                current.get(30, TimeUnit.SECONDS);
            } catch (TimeoutException e) {
                log.warn("Dump did not complete during shutdown");
            } catch (InterruptedException e) {
                Thread.currentThread().interrupt();
            } catch (ExecutionException e) {
                log.warn("Dump failed during shutdown: {}", e.getCause().getMessage());
            }
        }
    }

    public boolean awaitDumpCompletion(long timeoutMillis) throws InterruptedException {
        CompletableFuture<Void> current = dumpFuture;
        if (current == null || current.isDone()) {
            return true;
        }
        try {
            current.get(timeoutMillis, TimeUnit.MILLISECONDS);
            return true;
        } catch (ExecutionException e) {
            log.error("Dump execution failed", e.getCause());
            return false;
        } catch (TimeoutException e) {
            log.warn("Dump did not complete within {} ms", timeoutMillis);
            return false;
        }
    }
}
