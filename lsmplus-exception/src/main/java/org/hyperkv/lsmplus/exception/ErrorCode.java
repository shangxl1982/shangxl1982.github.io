package org.hyperkv.lsmplus.exception;

public enum ErrorCode {
    INTERNAL_ERROR(1001, "Internal error", false),
    INVALID_ARGUMENT(1002, "Invalid argument", true),
    SERVICE_STOPPED(1003, "Service stopped", false),
    OPERATION_REJECTED(1004, "Operation rejected", true),

    CHUNK_ALLOCATION_FAILED(2001, "Chunk allocation failed", false),
    CHUNK_WRITE_FAILED(2002, "Chunk write failed", false),
    DISK_FULL(2003, "Disk full", false),
    CHUNK_NOT_FOUND(2004, "Chunk not found", true),

    JOURNAL_WRITE_FAILED(3001, "Journal write failed", false),
    JOURNAL_REPLAY_FAILED(3002, "Journal replay failed", false),
    JOURNAL_TRUNCATE_FAILED(3003, "Journal truncate failed", true),

    DUMP_FAILED(4001, "Dump failed", true),
    TREE_CORRUPT(4002, "Tree corrupt", false),
    PAGE_NOT_FOUND(4003, "Page not found", true),

    METADATA_WRITE_FAILED(5001, "Metadata write failed", false),
    METADATA_CORRUPT(5002, "Metadata corrupt", false),

    CRC32_MISMATCH(6001, "CRC32 mismatch", false),
    INVALID_MAGIC(6002, "Invalid magic", false),
    DATA_CORRUPT(6003, "Data corrupt", false);

    private final int code;
    private final String message;
    private final boolean recoverable;

    ErrorCode(int code, String message, boolean recoverable) {
        this.code = code;
        this.message = message;
        this.recoverable = recoverable;
    }

    public int getCode() {
        return code;
    }

    public String getMessage() {
        return message;
    }

    public boolean isRecoverable() {
        return recoverable;
    }
}
