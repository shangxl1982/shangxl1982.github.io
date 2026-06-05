package org.hyperkv.lsmplus.storage;

import org.junit.jupiter.api.Test;

import java.nio.ByteBuffer;
import java.nio.ByteOrder;

import static org.junit.jupiter.api.Assertions.*;

class WriteItemTest {

    @Test
    void testCreateJournalEntryWriteItem() {
        byte[] body = "journal entry data".getBytes();
        WriteItem item = new WriteItem(WriteItem.TYPE_JOURNAL_ENTRY, body);

        assertEquals(WriteItem.TYPE_JOURNAL_ENTRY, item.getType());
        assertArrayEquals(body, item.getBody());
        assertTrue(item.validate());
    }

    @Test
    void testCreatePageDataWriteItem() {
        byte[] body = new byte[100];
        WriteItem item = new WriteItem(WriteItem.TYPE_PAGE_DATA, body);

        assertEquals(WriteItem.TYPE_PAGE_DATA, item.getType());
        assertTrue(item.validate());
    }

    @Test
    void testNullBodyThrows() {
        assertThrows(IllegalArgumentException.class,
                () -> new WriteItem(WriteItem.TYPE_JOURNAL_ENTRY, null));
    }

    @Test
    void testToByteArray4KAlignment() {
        byte[] body = "small data".getBytes();
        WriteItem item = new WriteItem(WriteItem.TYPE_JOURNAL_ENTRY, body);
        byte[] bytes = item.toByteArray();

        assertEquals(item.getTotalSize(), bytes.length);
        assertEquals(0, bytes.length % WriteItem.ALIGNMENT);
    }

    @Test
    void testToByteArrayExact4K() {
        int bodyLength = 4096 - WriteItem.HEADER_SIZE - WriteItem.CRC32_SIZE;
        byte[] body = new byte[bodyLength];
        WriteItem item = new WriteItem(WriteItem.TYPE_JOURNAL_ENTRY, body);

        assertEquals(4096, item.getTotalSize());
        assertEquals(0, item.getPaddingSize());
    }

    @Test
    void testToByteArraySpansTwo4KPages() {
        int bodyLength = 4096 - WriteItem.HEADER_SIZE - WriteItem.CRC32_SIZE + 1;
        byte[] body = new byte[bodyLength];
        WriteItem item = new WriteItem(WriteItem.TYPE_JOURNAL_ENTRY, body);

        assertEquals(8192, item.getTotalSize());
        assertEquals(4096 - 1, item.getPaddingSize());
    }

    @Test
    void testFromByteArrayRoundTrip() {
        byte[] body = "test round trip data".getBytes();
        WriteItem original = new WriteItem(WriteItem.TYPE_JOURNAL_ENTRY, body);
        byte[] bytes = original.toByteArray();

        WriteItem restored = WriteItem.fromByteArray(bytes, 0);

        assertEquals(original.getType(), restored.getType());
        assertArrayEquals(original.getBody(), restored.getBody());
        assertTrue(restored.validate());
    }

    @Test
    void testFromByteArrayWithOffset() {
        byte[] body = "offset test".getBytes();
        WriteItem original = new WriteItem(WriteItem.TYPE_PAGE_DATA, body);
        byte[] itemBytes = original.toByteArray();

        byte[] wrapped = new byte[100 + itemBytes.length];
        System.arraycopy(itemBytes, 0, wrapped, 100, itemBytes.length);

        WriteItem restored = WriteItem.fromByteArray(wrapped, 100);

        assertEquals(original.getType(), restored.getType());
        assertArrayEquals(original.getBody(), restored.getBody());
    }

    @Test
    void testFromByteArrayInvalidMagic() {
        byte[] data = new byte[4096];
        ByteBuffer bb = ByteBuffer.wrap(data).order(ByteOrder.BIG_ENDIAN);
        bb.putShort((short) 0xFFFF);
        bb.putShort(WriteItem.TYPE_JOURNAL_ENTRY);
        bb.putInt(0);
        bb.putInt(0);

        assertThrows(IllegalArgumentException.class,
                () -> WriteItem.fromByteArray(data, 0));
    }

    @Test
    void testFromByteArrayCRC32Failure() {
        byte[] body = "corrupt me".getBytes();
        WriteItem original = new WriteItem(WriteItem.TYPE_JOURNAL_ENTRY, body);
        byte[] bytes = original.toByteArray();

        int crcOffset = WriteItem.HEADER_SIZE + body.length;
        bytes[crcOffset] ^= 0xFF;

        assertThrows(IllegalArgumentException.class,
                () -> WriteItem.fromByteArray(bytes, 0));
    }

    @Test
    void testIsCompleteWriteItemValid() {
        byte[] body = "complete item".getBytes();
        WriteItem item = new WriteItem(WriteItem.TYPE_JOURNAL_ENTRY, body);
        byte[] bytes = item.toByteArray();

        assertTrue(WriteItem.isCompleteWriteItem(bytes, 0, bytes.length));
    }

    @Test
    void testIsCompleteWriteItemTruncated() {
        byte[] body = "will be truncated".getBytes();
        WriteItem item = new WriteItem(WriteItem.TYPE_JOURNAL_ENTRY, body);
        byte[] bytes = item.toByteArray();

        assertFalse(WriteItem.isCompleteWriteItem(bytes, 0, 100));
    }

    @Test
    void testIsCompleteWriteItemBadMagic() {
        byte[] data = new byte[4096];
        assertFalse(WriteItem.isCompleteWriteItem(data, 0, data.length));
    }

    @Test
    void testBodyIsDefensiveCopy() {
        byte[] body = "original".getBytes();
        WriteItem item = new WriteItem(WriteItem.TYPE_JOURNAL_ENTRY, body);

        body[0] = 'X';
        assertEquals('o', item.getBody()[0]);

        byte[] retrieved = item.getBody();
        retrieved[0] = 'Y';
        assertEquals('o', item.getBody()[0]);
    }

    @Test
    void testMagicConstant() {
        assertEquals((short) 0xABCD, WriteItem.MAGIC);
    }

    @Test
    void testHeaderSize() {
        assertEquals(12, WriteItem.HEADER_SIZE);
    }
}