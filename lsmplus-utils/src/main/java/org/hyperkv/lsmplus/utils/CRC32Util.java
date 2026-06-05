package org.hyperkv.lsmplus.utils;

import java.util.zip.CRC32;

public final class CRC32Util {

    private CRC32Util() {
    }

    public static int calculate(byte[] data) {
        if (data == null) {
            throw new IllegalArgumentException("data must not be null");
        }
        return calculate(data, 0, data.length);
    }

    public static int calculate(byte[] data, int offset, int length) {
        if (data == null) {
            throw new IllegalArgumentException("data must not be null");
        }
        if (offset < 0 || length < 0 || offset + length > data.length) {
            throw new IllegalArgumentException(
                    "Invalid offset/length: offset=" + offset +
                    ", length=" + length + ", data.length=" + data.length);
        }
        CRC32 crc = new CRC32();
        crc.update(data, offset, length);
        return (int) crc.getValue();
    }

    public static boolean validate(byte[] data, int expectedCrc32) {
        if (data == null) {
            throw new IllegalArgumentException("data must not be null");
        }
        return validate(data, 0, data.length, expectedCrc32);
    }

    public static boolean validate(byte[] data, int offset, int length, int expectedCrc32) {
        int actual = calculate(data, offset, length);
        return actual == expectedCrc32;
    }
}