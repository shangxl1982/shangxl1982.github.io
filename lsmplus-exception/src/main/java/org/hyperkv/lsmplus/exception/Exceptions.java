package org.hyperkv.lsmplus.exception;

public final class Exceptions {
    private Exceptions() {
    }

    public static KVStoreRuntimeException invalidArgument(String message) {
        return new KVStoreRuntimeException(ErrorCode.INVALID_ARGUMENT, message);
    }

    public static KVStoreRuntimeException invalidArgument(String format, Object... args) {
        return new KVStoreRuntimeException(ErrorCode.INVALID_ARGUMENT, String.format(format, args));
    }

    public static KVStoreRuntimeException invalidState(String message) {
        return new KVStoreRuntimeException(ErrorCode.INTERNAL_ERROR, message);
    }

    public static KVStoreRuntimeException invalidState(String format, Object... args) {
        return new KVStoreRuntimeException(ErrorCode.INTERNAL_ERROR, String.format(format, args));
    }

    public static KVStoreRuntimeException operationRejected(String message) {
        return new KVStoreRuntimeException(ErrorCode.OPERATION_REJECTED, message);
    }

    public static KVStoreStoppedException kvStoreStopped(String reason) {
        return new KVStoreStoppedException(reason);
    }
}
