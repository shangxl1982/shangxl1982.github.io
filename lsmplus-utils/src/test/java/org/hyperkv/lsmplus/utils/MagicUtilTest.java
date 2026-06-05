package org.hyperkv.lsmplus.utils;

import org.junit.jupiter.api.Test;

import java.nio.ByteBuffer;
import java.nio.ByteOrder;

import static org.junit.jupiter.api.Assertions.*;

class MagicUtilTest {

    @Test
    void testWriteMagic() {
        byte[] data = new byte[16];
        MagicUtil.writeMagic(data, 0);
        assertEquals((short) 0xABCD, ByteBuffer.wrap(data, 0, 2)
                .order(ByteOrder.BIG_ENDIAN).getShort());
    }

    @Test
    void testWriteMagicWithOffset() {
        byte[] data = new byte[16];
        MagicUtil.writeMagic(data, 8);
        assertEquals((short) 0xABCD, ByteBuffer.wrap(data, 8, 2)
                .order(ByteOrder.BIG_ENDIAN).getShort());
    }

    @Test
    void testReadMagic() {
        byte[] data = new byte[16];
        ByteBuffer.wrap(data, 0, 2).order(ByteOrder.BIG_ENDIAN).putShort((short) 0xABCD);
        assertEquals((short) 0xABCD, MagicUtil.readMagic(data, 0));
    }

    @Test
    void testReadMagicWithOffset() {
        byte[] data = new byte[16];
        ByteBuffer.wrap(data, 4, 2).order(ByteOrder.BIG_ENDIAN).putShort((short) 0xABCD);
        assertEquals((short) 0xABCD, MagicUtil.readMagic(data, 4));
    }

    @Test
    void testValidateCorrectMagic() {
        byte[] data = new byte[16];
        MagicUtil.writeMagic(data, 0);
        assertTrue(MagicUtil.validateMagic(data));
        assertTrue(MagicUtil.hasValidMagic(data));
        assertTrue(MagicUtil.hasValidMagic(data, 0));
    }

    @Test
    void testValidateIncorrectMagic() {
        byte[] data = new byte[16];
        assertFalse(MagicUtil.hasValidMagic(data));
        assertFalse(MagicUtil.validateMagic(data));
    }

    @Test
    void testHasValidMagicNullData() {
        assertFalse(MagicUtil.hasValidMagic(null));
        assertFalse(MagicUtil.hasValidMagic(null, 0));
    }

    @Test
    void testHasValidMagicInsufficientData() {
        byte[] data = new byte[1];
        assertFalse(MagicUtil.hasValidMagic(data));
        assertFalse(MagicUtil.hasValidMagic(data, 0));
    }

    @Test
    void testHasValidMagicInvalidOffset() {
        byte[] data = new byte[16];
        assertFalse(MagicUtil.hasValidMagic(data, -1));
        assertFalse(MagicUtil.hasValidMagic(data, 15));
    }

    @Test
    void testWriteMagicNullThrows() {
        assertThrows(IllegalArgumentException.class, () -> MagicUtil.writeMagic(null, 0));
    }

    @Test
    void testWriteMagicInvalidOffsetThrows() {
        byte[] data = new byte[16];
        assertThrows(IllegalArgumentException.class, () -> MagicUtil.writeMagic(data, -1));
        assertThrows(IllegalArgumentException.class, () -> MagicUtil.writeMagic(data, 15));
    }

    @Test
    void testReadMagicNullThrows() {
        assertThrows(IllegalArgumentException.class, () -> MagicUtil.readMagic(null, 0));
    }

    @Test
    void testReadMagicInvalidOffsetThrows() {
        byte[] data = new byte[16];
        assertThrows(IllegalArgumentException.class, () -> MagicUtil.readMagic(data, -1));
        assertThrows(IllegalArgumentException.class, () -> MagicUtil.readMagic(data, 15));
    }

    @Test
    void testMagicConstant() {
        assertEquals((short) 0xABCD, MagicUtil.WRITE_ITEM_MAGIC);
    }
}