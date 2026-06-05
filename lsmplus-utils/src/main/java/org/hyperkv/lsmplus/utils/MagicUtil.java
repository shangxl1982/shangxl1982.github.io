package org.hyperkv.lsmplus.utils;

import java.nio.ByteBuffer;
import java.nio.ByteOrder;

public final class MagicUtil {

    public static final short WRITE_ITEM_MAGIC = (short) 0xABCD;

    private MagicUtil() {
    }

    public static boolean hasValidMagic(byte[] data) {
        return hasValidMagic(data, 0);
    }

    public static boolean hasValidMagic(byte[] data, int offset) {
        if (data == null || offset < 0 || offset + 2 > data.length) {
            return false;
        }
        short magic = ByteBuffer.wrap(data, offset, 2)
                .order(ByteOrder.BIG_ENDIAN)
                .getShort();
        return magic == WRITE_ITEM_MAGIC;
    }

    public static void writeMagic(byte[] data, int offset) {
        if (data == null) {
            throw new IllegalArgumentException("data must not be null");
        }
        if (offset < 0 || offset + 2 > data.length) {
            throw new IllegalArgumentException(
                    "Invalid offset: " + offset + ", data.length=" + data.length);
        }
        ByteBuffer.wrap(data, offset, 2)
                .order(ByteOrder.BIG_ENDIAN)
                .putShort(WRITE_ITEM_MAGIC);
    }

    public static short readMagic(byte[] data, int offset) {
        if (data == null) {
            throw new IllegalArgumentException("data must not be null");
        }
        if (offset < 0 || offset + 2 > data.length) {
            throw new IllegalArgumentException(
                    "Invalid offset: " + offset + ", data.length=" + data.length);
        }
        return ByteBuffer.wrap(data, offset, 2)
                .order(ByteOrder.BIG_ENDIAN)
                .getShort();
    }

    public static boolean validateMagic(byte[] header) {
        return hasValidMagic(header, 0);
    }
}