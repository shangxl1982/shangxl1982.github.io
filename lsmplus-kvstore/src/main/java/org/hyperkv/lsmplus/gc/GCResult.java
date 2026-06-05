package org.hyperkv.lsmplus.gc;

public class GCResult {

    private int fullGCCount;
    private int partialGCCount;
    private int holePunchingCount;
    private long reclaimedSpace;

    public GCResult() {
        this.fullGCCount = 0;
        this.partialGCCount = 0;
        this.holePunchingCount = 0;
        this.reclaimedSpace = 0;
    }

    public void incrementFullGC() {
        fullGCCount++;
    }

    public void incrementPartialGC() {
        partialGCCount++;
    }

    public void incrementHolePunching() {
        holePunchingCount++;
    }

    public void addReclaimedSpace(long space) {
        reclaimedSpace += space;
    }

    public int getFullGCCount() {
        return fullGCCount;
    }

    public int getPartialGCCount() {
        return partialGCCount;
    }

    public int getHolePunchingCount() {
        return holePunchingCount;
    }

    public long getReclaimedSpace() {
        return reclaimedSpace;
    }

    public int getTotalGCCount() {
        return fullGCCount + partialGCCount + holePunchingCount;
    }

    public boolean hasReclaimed() {
        return getTotalGCCount() > 0;
    }

    @Override
    public String toString() {
        return String.format("GCResult{full=%d, partial=%d, holePunch=%d, reclaimed=%d bytes}",
                fullGCCount, partialGCCount, holePunchingCount, reclaimedSpace);
    }
}
