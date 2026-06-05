package org.hyperkv.lsmplus.storage;

import java.nio.ByteBuffer;
import java.nio.ByteOrder;
import java.util.zip.CRC32;

public final class WriteItem {

    public static final short MAGIC = (short) 0xABCD;
    public static final short TYPE_JOURNAL_ENTRY = 0x0001;
    public static final short TYPE_PAGE_DATA = 0x0002;
    public static final short TYPE_METADATA = 0x0003;
    public static final short TYPE_INDEX_DATA = 0x0004;
    public static final int ALIGNMENT = 4096;
    public static final int HEADER_SIZE = 12;
    public static final int CRC32_SIZE = 4;

    private final short type;
    private final byte[] body;
    private final int crc32;
    private final int paddingSize;
    private final int totalSize;

    public WriteItem(short type, byte[] body) {
        if (body == null) {
            throw new IllegalArgumentException("body must not be null");
        }
        this.type = type;
        this.body = body.clone();
        this.crc32 = computeCRC32(type, this.body);
        int rawSize = HEADER_SIZE + this.body.length + CRC32_SIZE;
        this.totalSize = alignUp(rawSize, ALIGNMENT);
        this.paddingSize = this.totalSize - rawSize;
    }

    private WriteItem(short type, byte[] body, int crc32, int totalSize) {
        this.type = type;
        this.body = body;
        this.crc32 = crc32;
        this.totalSize = totalSize;
        this.paddingSize = totalSize - HEADER_SIZE - body.length - CRC32_SIZE;
    }

    public short getType() {
        return type;
    }

    public byte[] getBody() {
        return body.clone();
    }

    public int getTotalSize() {
        return totalSize;
    }

    public int getPaddingSize() {
        return paddingSize;
    }

    public boolean validate() {
        int expected = computeCRC32(type, body);
        return crc32 == expected;
    }

    public byte[] toByteArray() {
        ByteBuffer buffer = ByteBuffer.allocate(totalSize)
                .order(ByteOrder.BIG_ENDIAN);
        buffer.putShort(MAGIC);
        buffer.putShort(type);
        buffer.putInt(body.length);
        buffer.putInt(0);
        buffer.put(body);
        buffer.putInt(crc32);
        buffer.put(new byte[paddingSize]);
        return buffer.array();
    }

    public static WriteItem fromByteArray(byte[] data, int offset) {
        ByteBuffer buffer = ByteBuffer.wrap(data, offset, HEADER_SIZE)
                .order(ByteOrder.BIG_ENDIAN);

        short magic = buffer.getShort();
        if (magic != MAGIC) {
            throw new IllegalArgumentException(
                    "Invalid magic: 0x" + Integer.toHexString(magic & 0xFFFF) +
                    ", expected: 0x" + Integer.toHexString(MAGIC & 0xFFFF));
        }

        short type = buffer.getShort();
        int length = buffer.getInt();
        int reserved = buffer.getInt();

        if (length < 0) {
            throw new IllegalArgumentException("Negative body length: " + length);
        }

        int rawSize = HEADER_SIZE + length + CRC32_SIZE;
        int totalSize = alignUp(rawSize, ALIGNMENT);

        if (offset + totalSize > data.length) {
            throw new IllegalArgumentException(
                    "Insufficient data: need " + totalSize + " bytes at offset " + offset +
                    ", available " + (data.length - offset));
        }

        byte[] body = new byte[length];
        System.arraycopy(data, offset + HEADER_SIZE, body, 0, length);

        ByteBuffer tailBuffer = ByteBuffer.wrap(data, offset + HEADER_SIZE + length, CRC32_SIZE)
                .order(ByteOrder.BIG_ENDIAN);
        int storedCrc32 = tailBuffer.getInt();

        WriteItem item = new WriteItem(type, body, storedCrc32, totalSize);
        if (!item.validate()) {
            throw new IllegalArgumentException("CRC32 validation failed");
        }

        return item;
    }

    public static boolean isCompleteWriteItem(byte[] data, int offset, int chunkSize) {
        if (offset + HEADER_SIZE > chunkSize) {
            return false;
        }

        ByteBuffer headerBuf = ByteBuffer.wrap(data, offset, HEADER_SIZE)
                .order(ByteOrder.BIG_ENDIAN);
        short magic = headerBuf.getShort();
        if (magic != MAGIC) {
            return false;
        }

        short type = headerBuf.getShort();
        int length = headerBuf.getInt();

        int rawSize = HEADER_SIZE + length + CRC32_SIZE;
        int totalSize = alignUp(rawSize, ALIGNMENT);

        if (offset + totalSize > chunkSize) {
            return false;
        }

        if (offset + totalSize <= data.length) {
            return validateCRC32(data, offset, length);
        }

        return true;
    }

    private static boolean validateCRC32(byte[] data, int offset, int bodyLength) {
        ByteBuffer headerBuf = ByteBuffer.wrap(data, offset, HEADER_SIZE)
                .order(ByteOrder.BIG_ENDIAN);
        short type = headerBuf.getShort(2);

        CRC32 crc = new CRC32();
        ByteBuffer headerForCrc = ByteBuffer.allocate(HEADER_SIZE)
                .order(ByteOrder.BIG_ENDIAN);
        headerForCrc.putShort(MAGIC);
        headerForCrc.putShort(type);
        headerForCrc.putInt(bodyLength);
        headerForCrc.putInt(0);
        crc.update(headerForCrc.array());

        crc.update(data, offset + HEADER_SIZE, bodyLength);

        int expected = (int) crc.getValue();

        ByteBuffer tailBuf = ByteBuffer.wrap(data, offset + HEADER_SIZE + bodyLength, CRC32_SIZE)
                .order(ByteOrder.BIG_ENDIAN);
        int stored = tailBuf.getInt();

        return expected == stored;
    }

    private static int computeCRC32(short type, byte[] body) {
        CRC32 crc = new CRC32();
        ByteBuffer headerBuf = ByteBuffer.allocate(HEADER_SIZE)
                .order(ByteOrder.BIG_ENDIAN);
        headerBuf.putShort(MAGIC);
        headerBuf.putShort(type);
        headerBuf.putInt(body.length);
        headerBuf.putInt(0);
        crc.update(headerBuf.array());
        crc.update(body);
        return (int) crc.getValue();
    }

    public static int alignUp(int value, int alignment) {
        return (value + alignment - 1) & ~(alignment - 1);
    }

    @Override
    public String toString() {
        return "WriteItem{type=0x" + Integer.toHexString(type & 0xFFFF) +
               ", bodyLength=" + body.length +
               ", totalSize=" + totalSize +
               ", paddingSize=" + paddingSize + '}';
    }
}