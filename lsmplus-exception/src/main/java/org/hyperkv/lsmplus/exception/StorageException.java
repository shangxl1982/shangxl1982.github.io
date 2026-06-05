package org.hyperkv.lsmplus.exception;

public class StorageException extends KVStoreRuntimeException {
    public StorageException(ErrorCode errorCode, String message) {
        super(errorCode, message);
    }

    public StorageException(ErrorCode errorCode, String message, Throwable cause) {
        super(errorCode, message, cause);
    }

    public static StorageException chunkAllocationFailed(String message) {
        return new StorageException(ErrorCode.CHUNK_ALLOCATION_FAILED, message);
    }

    public static StorageException chunkAllocationFailed(String message, Throwable cause) {
        return new StorageException(ErrorCode.CHUNK_ALLOCATION_FAILED, message, cause);
    }

    public static StorageException chunkWriteFailed(String message) {
        return new StorageException(ErrorCode.CHUNK_WRITE_FAILED, message);
    }

    public static StorageException chunkWriteFailed(String message, Throwable cause) {
        return new StorageException(ErrorCode.CHUNK_WRITE_FAILED, message, cause);
    }

    public static StorageException diskFull(String message) {
        return new StorageException(ErrorCode.DISK_FULL, message);
    }

    public static StorageException chunkNotFound(String message) {
        return new StorageException(ErrorCode.CHUNK_NOT_FOUND, message);
    }

    public static StorageException journalWriteFailed(String message) {
        return new StorageException(ErrorCode.JOURNAL_WRITE_FAILED, message);
    }

    public static StorageException journalWriteFailed(String message, Throwable cause) {
        return new StorageException(ErrorCode.JOURNAL_WRITE_FAILED, message, cause);
    }

    public static StorageException journalReplayFailed(String message) {
        return new StorageException(ErrorCode.JOURNAL_REPLAY_FAILED, message);
    }

    public static StorageException journalReplayFailed(String message, Throwable cause) {
        return new StorageException(ErrorCode.JOURNAL_REPLAY_FAILED, message, cause);
    }

    public static StorageException metadataWriteFailed(String message) {
        return new StorageException(ErrorCode.METADATA_WRITE_FAILED, message);
    }

    public static StorageException metadataWriteFailed(String message, Throwable cause) {
        return new StorageException(ErrorCode.METADATA_WRITE_FAILED, message, cause);
    }

    public static StorageException metadataCorrupt(String message) {
        return new StorageException(ErrorCode.METADATA_CORRUPT, message);
    }

    public static StorageException dumpFailed(String message) {
        return new StorageException(ErrorCode.DUMP_FAILED, message);
    }

    public static StorageException dumpFailed(String message, Throwable cause) {
        return new StorageException(ErrorCode.DUMP_FAILED, message, cause);
    }

    public static StorageException pageNotFound(String message) {
        return new StorageException(ErrorCode.PAGE_NOT_FOUND, message);
    }
}
