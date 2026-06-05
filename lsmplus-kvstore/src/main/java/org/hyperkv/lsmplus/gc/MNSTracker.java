package org.hyperkv.lsmplus.gc;

import java.util.Map;
import java.util.concurrent.ConcurrentHashMap;
import java.util.concurrent.atomic.AtomicLong;

public class MNSTracker {

    private final Map<Long, Long> versionToMNS;
    private final AtomicLong currentMNS;
    private final AtomicLong currentVersion;

    public MNSTracker() {
        this.versionToMNS = new ConcurrentHashMap<>();
        this.currentMNS = new AtomicLong(0);
        this.currentVersion = new AtomicLong(0);
    }

    public void recordMNS(long version, long mns) {
        if (version < 0) {
            throw new IllegalArgumentException("version must be non-negative");
        }
        if (mns < 0) {
            throw new IllegalArgumentException("mns must be non-negative");
        }
        versionToMNS.put(version, mns);
        currentVersion.set(version);
        if (mns > currentMNS.get()) {
            currentMNS.set(mns);
        }
    }

    public Long getMNS(long version) {
        return versionToMNS.get(version);
    }

    public long getCurrentMNS() {
        return currentMNS.get();
    }

    public long getCurrentVersion() {
        return currentVersion.get();
    }

    public boolean canGC(long chunkNumber) {
        return chunkNumber < currentMNS.get();
    }

    public void updateMNS(long mns) {
        if (mns < 0) {
            throw new IllegalArgumentException("mns must be non-negative");
        }
        currentMNS.updateAndGet(current -> Math.max(current, mns));
    }

    public void clearVersionsBefore(long version) {
        versionToMNS.entrySet().removeIf(entry -> entry.getKey() < version);
    }

    public int getVersionCount() {
        return versionToMNS.size();
    }

    public Map<Long, Long> getAllMNS() {
        return Map.copyOf(versionToMNS);
    }
}
