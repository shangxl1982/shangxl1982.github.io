package org.hyperkv.lsmplus.exception;

public class DataIntegrityException extends KVStoreRuntimeException {
    public DataIntegrityException(ErrorCode errorCode, String message) {
        super(errorCode, message);
    }

    public DataIntegrityException(ErrorCode errorCode, String message, Throwable cause) {
        super(errorCode, message, cause);
    }

    public static DataIntegrityException crc32Mismatch(String message) {
        return new DataIntegrityException(ErrorCode.CRC32_MISMATCH, message);
    }

    public static DataIntegrityException invalidMagic(String message) {
        return new DataIntegrityException(ErrorCode.INVALID_MAGIC, message);
    }

    public static DataIntegrityException dataCorrupt(String message) {
        return new DataIntegrityException(ErrorCode.DATA_CORRUPT, message);
    }

    public static DataIntegrityException treeCorrupt(String message) {
        return new DataIntegrityException(ErrorCode.TREE_CORRUPT, message);
    }
}
