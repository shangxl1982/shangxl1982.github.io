package org.hyperkv.lsmplus.gc;

public class GCConfig {

    private final double lowOccupancyThreshold;
    private final double highOccupancyThreshold;
    private final long minChunkAge;
    private final int maxConcurrentGC;

    public GCConfig(double lowOccupancyThreshold, double highOccupancyThreshold,
                    long minChunkAge, int maxConcurrentGC) {
        this.lowOccupancyThreshold = lowOccupancyThreshold;
        this.highOccupancyThreshold = highOccupancyThreshold;
        this.minChunkAge = minChunkAge;
        this.maxConcurrentGC = maxConcurrentGC;
    }

    public GCConfig() {
        this(0.3, 0.7, 60000, 3);
    }

    public double getLowOccupancyThreshold() {
        return lowOccupancyThreshold;
    }

    public double getHighOccupancyThreshold() {
        return highOccupancyThreshold;
    }

    public long getMinChunkAge() {
        return minChunkAge;
    }

    public int getMaxConcurrentGC() {
        return maxConcurrentGC;
    }
}
