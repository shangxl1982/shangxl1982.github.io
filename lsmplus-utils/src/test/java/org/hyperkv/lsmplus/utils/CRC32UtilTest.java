package org.hyperkv.lsmplus.utils;

import org.junit.jupiter.api.Test;

import static org.junit.jupiter.api.Assertions.*;

class CRC32UtilTest {

    @Test
    void testCalculateSimpleData() {
        byte[] data = "hello world".getBytes();
        int crc = CRC32Util.calculate(data);
        assertNotEquals(0, crc);
    }

    @Test
    void testCalculateEmptyData() {
        byte[] data = new byte[0];
        int crc = CRC32Util.calculate(data);
        assertEquals(0, crc);
    }

    @Test
    void testCalculatePartialArray() {
        byte[] data = "hello world".getBytes();
        int fullCrc = CRC32Util.calculate(data);
        int partialCrc = CRC32Util.calculate(data, 0, data.length);
        assertEquals(fullCrc, partialCrc);

        int helloCrc = CRC32Util.calculate(data, 0, 5);
        int worldCrc = CRC32Util.calculate(data, 6, 5);
        assertNotEquals(helloCrc, worldCrc);
    }

    @Test
    void testValidateCorrectCRC32() {
        byte[] data = "test data".getBytes();
        int crc = CRC32Util.calculate(data);
        assertTrue(CRC32Util.validate(data, crc));
    }

    @Test
    void testValidateIncorrectCRC32() {
        byte[] data = "test data".getBytes();
        assertFalse(CRC32Util.validate(data, 0xDEADBEEF));
    }

    @Test
    void testKnownValues() {
        byte[] data = "123456789".getBytes();
        int crc = CRC32Util.calculate(data);
        assertEquals(0xCBF43926, crc);
    }

    @Test
    void testValidatePartialArray() {
        byte[] data = "prefix_hello_suffix".getBytes();
        int offset = 7;
        int length = 5;
        int crc = CRC32Util.calculate(data, offset, length);
        assertTrue(CRC32Util.validate(data, offset, length, crc));
    }

    @Test
    void testNullDataThrows() {
        assertThrows(IllegalArgumentException.class, () -> CRC32Util.calculate(null));
    }

    @Test
    void testInvalidOffsetLengthThrows() {
        byte[] data = "test".getBytes();
        assertThrows(IllegalArgumentException.class, () -> CRC32Util.calculate(data, -1, 2));
        assertThrows(IllegalArgumentException.class, () -> CRC32Util.calculate(data, 0, -1));
        assertThrows(IllegalArgumentException.class, () -> CRC32Util.calculate(data, 0, 100));
    }

    @Test
    void testDeterministic() {
        byte[] data = "deterministic".getBytes();
        int crc1 = CRC32Util.calculate(data);
        int crc2 = CRC32Util.calculate(data);
        assertEquals(crc1, crc2);
    }
}