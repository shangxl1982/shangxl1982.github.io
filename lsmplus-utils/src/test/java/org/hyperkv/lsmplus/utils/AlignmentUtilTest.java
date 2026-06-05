package org.hyperkv.lsmplus.utils;

import org.junit.jupiter.api.Test;

import static org.junit.jupiter.api.Assertions.*;

class AlignmentUtilTest {

    @Test
    void testAlignTo4KZero() {
        assertEquals(0, AlignmentUtil.alignTo4K(0));
    }

    @Test
    void testAlignTo4KExact() {
        assertEquals(4096, AlignmentUtil.alignTo4K(4096));
        assertEquals(8192, AlignmentUtil.alignTo4K(8192));
    }

    @Test
    void testAlignTo4KOneOver() {
        assertEquals(4096, AlignmentUtil.alignTo4K(1));
        assertEquals(8192, AlignmentUtil.alignTo4K(4097));
    }

    @Test
    void testAlignTo4KOneUnder() {
        assertEquals(4096, AlignmentUtil.alignTo4K(4095));
        assertEquals(8192, AlignmentUtil.alignTo4K(8191));
    }

    @Test
    void testAlignTo4KMidRange() {
        assertEquals(4096, AlignmentUtil.alignTo4K(2048));
        assertEquals(8192, AlignmentUtil.alignTo4K(6000));
    }

    @Test
    void testIs4KAligned() {
        assertTrue(AlignmentUtil.is4KAligned(0));
        assertTrue(AlignmentUtil.is4KAligned(4096));
        assertTrue(AlignmentUtil.is4KAligned(8192));
        assertTrue(AlignmentUtil.is4KAligned(12288));
    }

    @Test
    void testIsNot4KAligned() {
        assertFalse(AlignmentUtil.is4KAligned(1));
        assertFalse(AlignmentUtil.is4KAligned(4095));
        assertFalse(AlignmentUtil.is4KAligned(4097));
        assertFalse(AlignmentUtil.is4KAligned(100));
    }

    @Test
    void testCalculatePaddingZero() {
        assertEquals(0, AlignmentUtil.calculatePadding(0));
        assertEquals(0, AlignmentUtil.calculatePadding(4096));
        assertEquals(0, AlignmentUtil.calculatePadding(8192));
    }

    @Test
    void testCalculatePaddingNonZero() {
        assertEquals(4095, AlignmentUtil.calculatePadding(1));
        assertEquals(1, AlignmentUtil.calculatePadding(4095));
        assertEquals(4095, AlignmentUtil.calculatePadding(4097));
    }

    @Test
    void testNegativeSizeThrows() {
        assertThrows(IllegalArgumentException.class, () -> AlignmentUtil.alignTo4K(-1));
        assertThrows(IllegalArgumentException.class, () -> AlignmentUtil.calculatePadding(-1));
    }

    @Test
    void testNegativeIsNotAligned() {
        assertFalse(AlignmentUtil.is4KAligned(-1));
    }

    @Test
    void testConsistency() {
        for (int size = 0; size <= 16384; size++) {
            int aligned = AlignmentUtil.alignTo4K(size);
            assertTrue(AlignmentUtil.is4KAligned(aligned));
            assertTrue(aligned >= size);
            assertEquals(AlignmentUtil.calculatePadding(size), aligned - size);
        }
    }
}