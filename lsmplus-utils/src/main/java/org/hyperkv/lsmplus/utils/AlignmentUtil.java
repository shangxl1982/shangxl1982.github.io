package org.hyperkv.lsmplus.utils;

public final class AlignmentUtil {

    public static final int ALIGNMENT = 4096;

    private AlignmentUtil() {
    }

    public static int alignTo4K(int size) {
        if (size < 0) {
            throw new IllegalArgumentException("size must be non-negative: " + size);
        }
        return (size + ALIGNMENT - 1) & ~(ALIGNMENT - 1);
    }

    public static boolean is4KAligned(int size) {
        if (size < 0) {
            return false;
        }
        return (size & (ALIGNMENT - 1)) == 0;
    }

    public static int calculatePadding(int size) {
        if (size < 0) {
            throw new IllegalArgumentException("size must be non-negative: " + size);
        }
        return alignTo4K(size) - size;
    }
}