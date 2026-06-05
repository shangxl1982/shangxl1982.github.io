package org.hyperkv.lsmplus.storage;

import org.hyperkv.lsmplus.utils.AlignmentUtil;
import org.hyperkv.lsmplus.utils.CRC32Util;
import org.hyperkv.lsmplus.utils.MagicUtil;
import org.junit.jupiter.api.Test;

import java.nio.ByteBuffer;
import java.nio.ByteOrder;

import static org.junit.jupiter.api.Assertions.*;

class DataIntegrityIntegrationTest {

    @Test
    void testCompleteWriteReadCycleJournalEntry() {
        byte[] body = "journal entry payload".getBytes();
        WriteItem item = new WriteItem(WriteItem.TYPE_JOURNAL_ENTRY, body);

        byte[] bytes = item.toByteArray();

        assertTrue(MagicUtil.hasValidMagic(bytes, 0));
        assertTrue(AlignmentUtil.is4KAligned(bytes.length));
        assertTrue(item.validate());

        WriteItem restored = WriteItem.fromByteArray(bytes, 0);
        assertEquals(WriteItem.TYPE_JOURNAL_ENTRY, restored.getType());
        assertArrayEquals(body, restored.getBody());
        assertTrue(restored.validate());
    }

    @Test
    void testCompleteWriteReadCyclePageData() {
        byte[] body = new byte[8000];
        for (int i = 0; i < body.length; i++) {
            body[i] = (byte) (i & 0xFF);
        }
        WriteItem item = new WriteItem(WriteItem.TYPE_PAGE_DATA, body);

        byte[] bytes = item.toByteArray();

        assertTrue(MagicUtil.hasValidMagic(bytes, 0));
        assertTrue(AlignmentUtil.is4KAligned(bytes.length));
        assertEquals(8192, bytes.length);

        WriteItem restored = WriteItem.fromByteArray(bytes, 0);
        assertEquals(WriteItem.TYPE_PAGE_DATA, restored.getType());
        assertArrayEquals(body, restored.getBody());
    }

    @Test
    void testCompleteWriteReadCycleMetadata() {
        byte[] body = "metadata content".getBytes();
        WriteItem item = new WriteItem(WriteItem.TYPE_METADATA, body);

        byte[] bytes = item.toByteArray();

        assertTrue(MagicUtil.hasValidMagic(bytes, 0));
        assertTrue(AlignmentUtil.is4KAligned(bytes.length));

        WriteItem restored = WriteItem.fromByteArray(bytes, 0);
        assertEquals(WriteItem.TYPE_METADATA, restored.getType());
        assertArrayEquals(body, restored.getBody());
    }

    @Test
    void testPartialWriteRecovery() {
        byte[] body1 = "first entry".getBytes();
        byte[] body2 = "second entry".getBytes();
        WriteItem item1 = new WriteItem(WriteItem.TYPE_JOURNAL_ENTRY, body1);
        WriteItem item2 = new WriteItem(WriteItem.TYPE_JOURNAL_ENTRY, body2);

        byte[] chunk = new byte[item1.getTotalSize() + item2.getTotalSize()];
        System.arraycopy(item1.toByteArray(), 0, chunk, 0, item1.getTotalSize());
        System.arraycopy(item2.toByteArray(), 0, chunk, item1.getTotalSize(), item2.getTotalSize());

        assertTrue(WriteItem.isCompleteWriteItem(chunk, 0, chunk.length));
        assertTrue(WriteItem.isCompleteWriteItem(chunk, item1.getTotalSize(), chunk.length));

        int truncatedSize = item1.getTotalSize() + 10;
        assertTrue(WriteItem.isCompleteWriteItem(chunk, 0, truncatedSize));
        assertFalse(WriteItem.isCompleteWriteItem(chunk, item1.getTotalSize(), truncatedSize));
    }

    @Test
    void testCorruptedDataDetectionMagicCorruption() {
        byte[] body = "important data".getBytes();
        WriteItem item = new WriteItem(WriteItem.TYPE_JOURNAL_ENTRY, body);
        byte[] bytes = item.toByteArray();

        bytes[0] = (byte) 0xFF;

        assertFalse(MagicUtil.hasValidMagic(bytes, 0));
        assertThrows(IllegalArgumentException.class,
                () -> WriteItem.fromByteArray(bytes, 0));
    }

    @Test
    void testCorruptedDataDetectionBodyCorruption() {
        byte[] body = "important data".getBytes();
        WriteItem item = new WriteItem(WriteItem.TYPE_JOURNAL_ENTRY, body);
        byte[] bytes = item.toByteArray();

        int bodyOffset = WriteItem.HEADER_SIZE;
        bytes[bodyOffset] ^= 0xFF;

        assertTrue(MagicUtil.hasValidMagic(bytes, 0));
        assertThrows(IllegalArgumentException.class,
                () -> WriteItem.fromByteArray(bytes, 0));
    }

    @Test
    void testCorruptedDataDetectionCRC32Corruption() {
        byte[] body = "important data".getBytes();
        WriteItem item = new WriteItem(WriteItem.TYPE_JOURNAL_ENTRY, body);
        byte[] bytes = item.toByteArray();

        int crcOffset = WriteItem.HEADER_SIZE + body.length;
        bytes[crcOffset] ^= 0xFF;

        assertTrue(MagicUtil.hasValidMagic(bytes, 0));
        assertThrows(IllegalArgumentException.class,
                () -> WriteItem.fromByteArray(bytes, 0));
    }

    @Test
    void testMultipleWriteItemsInChunk() {
        byte[] body1 = "entry one".getBytes();
        byte[] body2 = "entry two".getBytes();
        byte[] body3 = "entry three".getBytes();

        WriteItem item1 = new WriteItem(WriteItem.TYPE_JOURNAL_ENTRY, body1);
        WriteItem item2 = new WriteItem(WriteItem.TYPE_PAGE_DATA, body2);
        WriteItem item3 = new WriteItem(WriteItem.TYPE_JOURNAL_ENTRY, body3);

        int totalChunkSize = item1.getTotalSize() + item2.getTotalSize() + item3.getTotalSize();
        assertTrue(AlignmentUtil.is4KAligned(totalChunkSize));

        byte[] chunk = new byte[totalChunkSize];
        int offset = 0;

        System.arraycopy(item1.toByteArray(), 0, chunk, offset, item1.getTotalSize());
        offset += item1.getTotalSize();

        System.arraycopy(item2.toByteArray(), 0, chunk, offset, item2.getTotalSize());
        offset += item2.getTotalSize();

        System.arraycopy(item3.toByteArray(), 0, chunk, offset, item3.getTotalSize());

        offset = 0;
        WriteItem r1 = WriteItem.fromByteArray(chunk, offset);
        assertEquals(WriteItem.TYPE_JOURNAL_ENTRY, r1.getType());
        assertArrayEquals(body1, r1.getBody());
        offset += r1.getTotalSize();

        WriteItem r2 = WriteItem.fromByteArray(chunk, offset);
        assertEquals(WriteItem.TYPE_PAGE_DATA, r2.getType());
        assertArrayEquals(body2, r2.getBody());
        offset += r2.getTotalSize();

        WriteItem r3 = WriteItem.fromByteArray(chunk, offset);
        assertEquals(WriteItem.TYPE_JOURNAL_ENTRY, r3.getType());
        assertArrayEquals(body3, r3.getBody());
    }

    @Test
    void testAlignmentAndPaddingConsistency() {
        for (int bodyLen : new int[]{0, 1, 100, 4076, 4077, 8000, 12000}) {
            byte[] body = new byte[bodyLen];
            WriteItem item = new WriteItem(WriteItem.TYPE_JOURNAL_ENTRY, body);

            int expectedRaw = WriteItem.HEADER_SIZE + bodyLen + WriteItem.CRC32_SIZE;
            int expectedTotal = AlignmentUtil.alignTo4K(expectedRaw);
            int expectedPadding = AlignmentUtil.calculatePadding(expectedRaw);

            assertEquals(expectedTotal, item.getTotalSize());
            assertEquals(expectedPadding, item.getPaddingSize());
            assertTrue(AlignmentUtil.is4KAligned(item.getTotalSize()));

            byte[] bytes = item.toByteArray();
            assertEquals(expectedTotal, bytes.length);
            assertTrue(MagicUtil.hasValidMagic(bytes, 0));
        }
    }

    @Test
    void testCRC32IndependentlyMatchesWriteItem() {
        byte[] body = "verify crc32 match".getBytes();
        WriteItem item = new WriteItem(WriteItem.TYPE_JOURNAL_ENTRY, body);
        byte[] bytes = item.toByteArray();

        ByteBuffer headerBuf = ByteBuffer.allocate(WriteItem.HEADER_SIZE)
                .order(ByteOrder.BIG_ENDIAN);
        headerBuf.putShort(WriteItem.MAGIC);
        headerBuf.putShort(WriteItem.TYPE_JOURNAL_ENTRY);
        headerBuf.putInt(body.length);
        headerBuf.putInt(0);

        int expectedCrc = CRC32Util.calculate(headerBuf.array());
        byte[] bodyForCrc = new byte[body.length];
        System.arraycopy(bytes, WriteItem.HEADER_SIZE, bodyForCrc, 0, body.length);
        CRC32Util.validate(bodyForCrc, CRC32Util.calculate(bodyForCrc));

        int fullCrc = CRC32Util.calculate(bytes, 0, WriteItem.HEADER_SIZE + body.length);
        assertTrue(item.validate());
    }
}