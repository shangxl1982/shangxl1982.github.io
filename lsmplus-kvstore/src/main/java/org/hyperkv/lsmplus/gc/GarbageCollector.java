package org.hyperkv.lsmplus.gc;

import org.hyperkv.lsmplus.proto.Common.ChunkStatus;
import org.hyperkv.lsmplus.proto.Common.ChunkType;
import org.hyperkv.lsmplus.storage.ChunkInfo;
import org.hyperkv.lsmplus.storage.ChunkManager;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

import java.io.IOException;
import java.util.ArrayList;
import java.util.List;
import java.util.UUID;

public class GarbageCollector {

    private static final Logger log = LoggerFactory.getLogger(GarbageCollector.class);

    private final ChunkManager chunkManager;
    private final MNSTracker mnsTracker;
    private final OccupancyTracker occupancyTracker;
    private final GCConfig config;

    public GarbageCollector(ChunkManager chunkManager, MNSTracker mnsTracker,
                           OccupancyTracker occupancyTracker, GCConfig config) {
        if (chunkManager == null) {
            throw new IllegalArgumentException("chunkManager must not be null");
        }
        if (mnsTracker == null) {
            throw new IllegalArgumentException("mnsTracker must not be null");
        }
        if (occupancyTracker == null) {
            throw new IllegalArgumentException("occupancyTracker must not be null");
        }
        this.chunkManager = chunkManager;
        this.mnsTracker = mnsTracker;
        this.occupancyTracker = occupancyTracker;
        this.config = config != null ? config : new GCConfig();
    }

    public GarbageCollector(ChunkManager chunkManager) {
        this(chunkManager, new MNSTracker(), new OccupancyTracker(), new GCConfig());
    }

    public GCResult performGC() throws IOException {
        long currentMNS = chunkManager.getMNS();
        mnsTracker.updateMNS(currentMNS);
        return performGC(currentMNS);
    }

    public GCResult performGC(long mns) throws IOException {
        log.info("Starting GC with MNS={}", mns);
        List<ChunkInfo> candidates = findGCCandidates(mns);
        log.debug("Found {} GC candidates", candidates.size());
        return performGCOnCandidates(candidates);
    }

    private List<ChunkInfo> findGCCandidates(long mns) {
        List<ChunkInfo> candidates = new ArrayList<>();
        List<ChunkInfo> allChunks = chunkManager.listChunkInfos(ChunkType.CHUNK_INDEX);

        for (ChunkInfo info : allChunks) {
            if (info.getStatus() == ChunkStatus.SEALED) {
                candidates.add(info);
            }
        }

        return candidates;
    }

    private GCResult performGCOnCandidates(List<ChunkInfo> candidates) throws IOException {
        GCResult result = new GCResult();

        for (ChunkInfo info : candidates) {
            UUID chunkId = info.getChunkId();
            GCStrategy strategy = occupancyTracker.determineStrategy(chunkId.hashCode());

            log.debug("Processing chunk {} with strategy {}", chunkId, strategy);

            switch (strategy) {
                case FULL_GC -> performFullGC(info, result);
                case PARTIAL_GC -> performPartialGC(info, result);
                case HOLE_PUNCHING -> performHolePunching(info, result);
            }
        }

        log.info("GC completed: fullGC={}, partialGC={}, holePunching={}, reclaimedBytes={}",
            result.getFullGCCount(), result.getPartialGCCount(), 
            result.getHolePunchingCount(), result.getReclaimedSpace());

        return result;
    }

    private void performFullGC(ChunkInfo info, GCResult result) throws IOException {
        long fileSize = info.getTotalSize();
        chunkManager.deleteChunk(info.getChunkId());

        UUID chunkId = info.getChunkId();
        occupancyTracker.removeChunk(chunkId.hashCode());

        result.incrementFullGC();
        result.addReclaimedSpace(fileSize);
    }

    private void performPartialGC(ChunkInfo info, GCResult result) {
        result.incrementPartialGC();
    }

    private void performHolePunching(ChunkInfo info, GCResult result) {
        result.incrementHolePunching();
    }

    public MNSTracker getMnsTracker() {
        return mnsTracker;
    }

    public OccupancyTracker getOccupancyTracker() {
        return occupancyTracker;
    }

    public GCConfig getConfig() {
        return config;
    }
}
