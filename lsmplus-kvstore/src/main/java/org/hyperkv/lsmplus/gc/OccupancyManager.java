package org.hyperkv.lsmplus.gc;

import org.hyperkv.lsmplus.exception.Exceptions;
import org.hyperkv.lsmplus.storage.ChunkManager;
import org.hyperkv.lsmplus.storage.OccupancyFile;
import org.hyperkv.lsmplus.storage.io.FileIOFactory;
import org.hyperkv.lsmplus.storage.io.IOFactory;
import org.hyperkv.lsmplus.storage.io.VirtualDataPath;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

import java.io.IOException;
import java.util.HashMap;
import java.util.Map;
import java.util.UUID;

public class OccupancyManager {

    private static final Logger log = LoggerFactory.getLogger(OccupancyManager.class);

    private final String basePath;
    private final IOFactory ioFactory;
    private final ChunkManager chunkManager;
    private final MNSTracker mnsTracker;

    private final Map<UUID, Long> currentDeltas;
    private long currentMNS;
    private long currentVersion;

    public OccupancyManager(String basePath, ChunkManager chunkManager, MNSTracker mnsTracker) {
        this(basePath, chunkManager, mnsTracker, FileIOFactory.INSTANCE);
    }

    public OccupancyManager(String basePath, ChunkManager chunkManager, MNSTracker mnsTracker, IOFactory ioFactory) {
        if (basePath == null || basePath.isEmpty()) {
            throw Exceptions.invalidArgument("basePath must not be null or empty");
        }
        if (chunkManager == null) {
            throw Exceptions.invalidArgument("chunkManager must not be null");
        }
        if (mnsTracker == null) {
            throw Exceptions.invalidArgument("mnsTracker must not be null");
        }
        if (ioFactory == null) {
            throw Exceptions.invalidArgument("ioFactory must not be null");
        }

        this.basePath = basePath;
        this.chunkManager = chunkManager;
        this.mnsTracker = mnsTracker;
        this.ioFactory = ioFactory;
        this.currentDeltas = new HashMap<>();
        this.currentMNS = 0;
        this.currentVersion = 0;

        try {
            ioFactory.createDirectories(VirtualDataPath.file(basePath + "/occupancy"));
        } catch (IOException e) {
            throw new RuntimeException("Failed to create occupancy directory", e);
        }
        log.info("Initialized OccupancyManager: basePath={}", basePath);
    }

    public void startDump(long version) {
        if (version < 0) {
            throw Exceptions.invalidArgument("version must be non-negative");
        }
        currentVersion = version;
        currentDeltas.clear();
        currentMNS = chunkManager != null ? chunkManager.getMNS() : 0;
        log.debug("Started dump tracking for version={}, mns={}", version, currentMNS);
    }

    public void recordPageWrite(UUID chunkId, long alignedSize) {
        if (chunkId == null) {
            throw Exceptions.invalidArgument("chunkId must not be null");
        }
        if (alignedSize <= 0) {
            throw Exceptions.invalidArgument("alignedSize must be positive");
        }
        currentDeltas.merge(chunkId, alignedSize, Long::sum);
        log.trace("Recorded page write: chunkId={}, alignedSize={}, totalDelta={}", 
            chunkId, alignedSize, currentDeltas.get(chunkId));
    }

    public void recordPageDecommission(UUID chunkId, long alignedSize) {
        if (chunkId == null) {
            throw Exceptions.invalidArgument("chunkId must not be null");
        }
        if (alignedSize <= 0) {
            throw Exceptions.invalidArgument("alignedSize must be positive");
        }
        currentDeltas.merge(chunkId, -alignedSize, Long::sum);
        log.trace("Recorded page decommission: chunkId={}, alignedSize={}, totalDelta={}", 
            chunkId, alignedSize, currentDeltas.get(chunkId));
    }

    public void finishDump() throws IOException {
        if (currentDeltas.isEmpty()) {
            log.debug("No occupancy changes to record for version={}", currentVersion);
            return;
        }

        VirtualDataPath occupancyPath = VirtualDataPath.file(
            basePath + "/occupancy/" + currentVersion + ".pb");
        
        OccupancyFile occupancyFile = new OccupancyFile(occupancyPath, currentVersion, ioFactory);
        occupancyFile.setMns(currentMNS);
        occupancyFile.setTimestamp(System.currentTimeMillis());

        for (Map.Entry<UUID, Long> entry : currentDeltas.entrySet()) {
            occupancyFile.addDelta(entry.getKey(), entry.getValue());
        }

        occupancyFile.persist();
        
        mnsTracker.recordMNS(currentVersion, currentMNS);

        log.info("Finished dump tracking for version={}, mns={}, deltaCount={}", 
            currentVersion, currentMNS, currentDeltas.size());
        
        currentDeltas.clear();
    }

    public OccupancyFile loadOccupancyRecord(long version) throws IOException {
        VirtualDataPath occupancyPath = VirtualDataPath.file(
            basePath + "/occupancy/" + version + ".pb");
        
        if (!ioFactory.exists(occupancyPath)) {
            log.debug("No occupancy record found for version={}", version);
            return null;
        }

        return OccupancyFile.load(occupancyPath, ioFactory);
    }

    public void applyOccupancyDeltas(OccupancyFile occupancyFile) {
        if (occupancyFile == null) {
            return;
        }

        for (OccupancyFile.OccupancyDeltaEntry delta : occupancyFile.getDeltas()) {
            UUID chunkId = delta.getChunkId();
            long deltaSize = delta.getDeltaSize();
            
            log.debug("Applying occupancy delta: chunkId={}, deltaSize={}", chunkId, deltaSize);
        }
    }

    public Map<UUID, Long> getCurrentDeltas() {
        return Map.copyOf(currentDeltas);
    }

    public long getCurrentMNS() {
        return currentMNS;
    }

    public long getCurrentVersion() {
        return currentVersion;
    }

    public int getDeltaCount() {
        return currentDeltas.size();
    }
}
