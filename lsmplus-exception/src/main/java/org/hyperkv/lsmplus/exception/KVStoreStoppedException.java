package org.hyperkv.lsmplus.exception;

public class KVStoreStoppedException extends KVStoreRuntimeException {
    private final String stopReason;

    public KVStoreStoppedException(String stopReason) {
        super(ErrorCode.SERVICE_STOPPED, "KVStore is stopped: " + stopReason);
        this.stopReason = stopReason;
    }

    public String getStopReason() {
        return stopReason;
    }
}
