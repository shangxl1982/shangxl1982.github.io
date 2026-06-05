package org.hyperkv.lsmplus.journal;

public class ReplayResult {
    private final int errorCount;
    private final JournalReplayPoint lastReplayPoint;

    public ReplayResult(int errorCount, JournalReplayPoint lastReplayPoint) {
        this.errorCount = errorCount;
        this.lastReplayPoint = lastReplayPoint;
    }

    public int getErrorCount() {
        return errorCount;
    }

    public JournalReplayPoint getLastReplayPoint() {
        return lastReplayPoint;
    }

    public static ReplayResult success(JournalReplayPoint lastReplayPoint) {
        return new ReplayResult(0, lastReplayPoint);
    }

    public static ReplayResult withErrors(int errorCount, JournalReplayPoint lastReplayPoint) {
        return new ReplayResult(errorCount, lastReplayPoint);
    }
}
