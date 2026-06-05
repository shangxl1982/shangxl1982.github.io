package org.hyperkv.lsmplus.gc;

import java.util.Map;
import java.util.concurrent.ConcurrentHashMap;

public class OccupancyTracker {

    private final Map<Long, ChunkOccupancy> occupancies;
    private final double lowOccupancyThreshold;
    private final double highOccupancyThreshold;

    public OccupancyTracker(double lowOccupancyThreshold, double highOccupancyThreshold) {
        if (lowOccupancyThreshold < 0 || lowOccupancyThreshold > 1) {
            throw new IllegalArgumentException("lowOccupancyThreshold must be between 0 and 1");
        }
        if (highOccupancyThreshold < 0 || highOccupancyThreshold > 1) {
            throw new IllegalArgumentException("highOccupancyThreshold must be between 0 and 1");
        }
        if (lowOccupancyThreshold >= highOccupancyThreshold) {
            throw new IllegalArgumentException("lowOccupancyThreshold must be less than highOccupancyThreshold");
        }
        this.occupancies = new ConcurrentHashMap<>();
        this.lowOccupancyThreshold = lowOccupancyThreshold;
        this.highOccupancyThreshold = highOccupancyThreshold;
    }

    public OccupancyTracker() {
        this(0.3, 0.7);
    }

    public void recordOccupancy(long chunkId, long totalSize, long validSize) {
        if (totalSize <= 0) {
            throw new IllegalArgumentException("totalSize must be positive");
        }
        if (validSize < 0) {
            throw new IllegalArgumentException("validSize must be non-negative");
        }
        double ratio = (double) validSize / totalSize;
        occupancies.put(chunkId, new ChunkOccupancy(chunkId, totalSize, validSize, ratio));
    }

    public ChunkOccupancy getOccupancy(long chunkId) {
        return occupancies.get(chunkId);
    }

    public double getOccupancyRatio(long chunkId) {
        ChunkOccupancy occupancy = occupancies.get(chunkId);
        return occupancy != null ? occupancy.getRatio() : 0.0;
    }

    public boolean isLowOccupancy(long chunkId) {
        double ratio = getOccupancyRatio(chunkId);
        return ratio < lowOccupancyThreshold;
    }

    public boolean isHighOccupancy(long chunkId) {
        double ratio = getOccupancyRatio(chunkId);
        return ratio >= highOccupancyThreshold;
    }

    public GCStrategy determineStrategy(long chunkId) {
        double ratio = getOccupancyRatio(chunkId);
        if (ratio < lowOccupancyThreshold) {
            return GCStrategy.FULL_GC;
        } else if (ratio < highOccupancyThreshold) {
            return GCStrategy.PARTIAL_GC;
        } else {
            return GCStrategy.HOLE_PUNCHING;
        }
    }

    public void removeChunk(long chunkId) {
        occupancies.remove(chunkId);
    }

    public int getChunkCount() {
        return occupancies.size();
    }

    public Map<Long, ChunkOccupancy> getAllOccupancies() {
        return Map.copyOf(occupancies);
    }

    public double getLowOccupancyThreshold() {
        return lowOccupancyThreshold;
    }

    public double getHighOccupancyThreshold() {
        return highOccupancyThreshold;
    }

    public static final class ChunkOccupancy {
        private final long chunkId;
        private final long totalSize;
        private final long validSize;
        private final double ratio;

        public ChunkOccupancy(long chunkId, long totalSize, long validSize, double ratio) {
            this.chunkId = chunkId;
            this.totalSize = totalSize;
            this.validSize = validSize;
            this.ratio = ratio;
        }

        public long getChunkId() {
            return chunkId;
        }

        public long getTotalSize() {
            return totalSize;
        }

        public long getValidSize() {
            return validSize;
        }

        public double getRatio() {
            return ratio;
        }

        @Override
        public String toString() {
            return String.format("ChunkOccupancy{chunkId=%d, total=%d, valid=%d, ratio=%.2f%%}",
                    chunkId, totalSize, validSize, ratio * 100);
        }
    }
}
