package org.hyperkv.lsmplus.exception;

import java.util.HashMap;
import java.util.Map;

public class KVStoreException extends Exception {
    private final ErrorCode errorCode;
    private final Map<String, Object> context;

    public KVStoreException(ErrorCode errorCode, String message) {
        super(message);
        this.errorCode = errorCode;
        this.context = new HashMap<>();
    }

    public KVStoreException(ErrorCode errorCode, String message, Throwable cause) {
        super(message, cause);
        this.errorCode = errorCode;
        this.context = new HashMap<>();
    }

    public ErrorCode getErrorCode() {
        return errorCode;
    }

    public boolean isRecoverable() {
        return errorCode.isRecoverable();
    }

    public Map<String, Object> getContext() {
        return new HashMap<>(context);
    }

    public KVStoreException addContext(String key, Object value) {
        this.context.put(key, value);
        return this;
    }

    @Override
    public String toString() {
        StringBuilder sb = new StringBuilder();
        sb.append(getClass().getSimpleName())
          .append(": [").append(errorCode.getCode()).append("] ")
          .append(getMessage());
        if (!context.isEmpty()) {
            sb.append(" | Context: ").append(context);
        }
        if (getCause() != null) {
            sb.append(" | Cause: ").append(getCause().getClass().getSimpleName())
              .append(": ").append(getCause().getMessage());
        }
        return sb.toString();
    }
}
