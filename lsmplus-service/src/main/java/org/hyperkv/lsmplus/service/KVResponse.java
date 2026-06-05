package org.hyperkv.lsmplus.service;

import java.util.Map;
import java.util.Objects;

public class KVResponse {

    private final boolean success;
    private final byte[] value;
    private final Map<String, byte[]> values;
    private final String error;
    private final String errorMessage;

    private KVResponse(boolean success, byte[] value, Map<String, byte[]> values,
                       String error, String errorMessage) {
        this.success = success;
        this.value = value;
        this.values = values;
        this.error = error;
        this.errorMessage = errorMessage;
    }

    public static KVResponse success() {
        return new KVResponse(true, null, null, null, null);
    }

    public static KVResponse success(byte[] value) {
        return new KVResponse(true, value, null, null, null);
    }

    public static KVResponse success(Map<String, byte[]> values) {
        return new KVResponse(true, null, values, null, null);
    }

    public static KVResponse notFound(String key) {
        return new KVResponse(false, null, null, "NOT_FOUND", "Key not found: " + key);
    }

    public static KVResponse error(String error, String message) {
        return new KVResponse(false, null, null, error, message);
    }

    public static KVResponse error(String error, String message, Throwable cause) {
        return new KVResponse(false, null, null, error, message + ": " + cause.getMessage());
    }

    public boolean isSuccess() {
        return success;
    }

    public byte[] getValue() {
        return value;
    }

    public Map<String, byte[]> getValues() {
        return values;
    }

    public String getError() {
        return error;
    }

    public String getErrorMessage() {
        return errorMessage;
    }

    public boolean hasValue() {
        return value != null;
    }

    public boolean hasValues() {
        return values != null && !values.isEmpty();
    }

    @Override
    public String toString() {
        if (success) {
            return "KVResponse{success=true}";
        }
        return "KVResponse{success=false, error='" + error + "', message='" + errorMessage + "'}";
    }
}
