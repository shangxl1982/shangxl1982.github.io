# Story 2-1: Implement WriteItem Class

## Story

As a developer, I want to implement the WriteItem class so that data can be written to disk with integrity protection according to the design specifications.

## Acceptance Criteria

- [ ] WriteItem class created with Header (12 bytes), Body, CRC32, Padding structure
- [ ] Header contains Magic (0xABCD), Type (JOURNAL_ENTRY/PAGE_DATA), Body Length, Reserved
- [ ] Body contains serialized Protobuf data
- [ ] CRC32 calculated over Header + Body (excluding Tailer and Padding)
- [ ] Padding aligns total size to 4K boundary
- [ ] toByteArray() method produces correct format
- [ ] fromByteArray() method parses correctly with CRC32 validation
- [ ] getTotalSize() returns 4K-aligned size
- [ ] Partial write detection implemented (Magic mismatch, Length overflow, CRC32 failure)

## Technical Details

### Class: WriteItem

```java
package org.hyperkv.lsmplus.storage;

public class WriteItem {
    public static final short MAGIC = (short) 0xABCD;
    public static final short TYPE_JOURNAL_ENTRY = 0x0001;
    public static final short TYPE_PAGE_DATA = 0x0002;
    public static final short TYPE_METADATA = 0x0003;
    public static final short TYPE_INDEX_DATA = 0x0004;
    public static final int ALIGNMENT = 4096;
    public static final int HEADER_SIZE = 12; // Magic(2) + Type(2) + Length(4) + Reserved(4)
    public static final int CRC32_SIZE = 4;
    
    private final short type;
    private final byte[] body;
    private final int crc32;
    private final int paddingSize;
    
    public WriteItem(short type, byte[] body);
    public byte[] toByteArray();
    public static WriteItem fromByteArray(byte[] data, int offset);
    public int getTotalSize();
    public byte[] getBody();
    public short getType();
    public boolean validate();
    public static boolean isCompleteWriteItem(byte[] data, int offset, int chunkSize);
}
```

### Write Item Structure

```
┌─────────────────────────────────────────────────────┐
│                    Write Item                        │
├────────────┬────────────┬────────────┬─────────────┤
│   Magic    │   Type     │   Length   │  Reserved   │
│  2 bytes   │  2 bytes   │  4 bytes   │  4 bytes    │
│  (0xABCD)  │            │            │             │
├────────────┴────────────┴────────────┴─────────────┤
│            Body (Variable)                         │
├────────────────────────────────────────────────────┤
│            CRC32 (4 bytes)                         │
├────────────────────────────────────────────────────┤
│            Padding (4K 对齐)                       │
└────────────────────────────────────────────────────┘

Total Size = align(HEADER_SIZE + bodyLength + CRC32_SIZE, ALIGNMENT)
Padding = Total Size - HEADER_SIZE - bodyLength - CRC32_SIZE
```

### Partial Write Detection

```java
public static boolean isCompleteWriteItem(byte[] data, int offset, int chunkSize) {
    // 1. Check if we have enough data for header
    if (offset + HEADER_SIZE > chunkSize) {
        return false; // Not enough data for header
    }
    
    // 2. Check Magic
    short magic = readMagic(data, offset);
    if (magic != MAGIC) {
        return false; // Magic mismatch - partial write or corruption
    }
    
    // 3. Check Length doesn't exceed chunk boundary
    int length = readLength(data, offset);
    if (offset + HEADER_SIZE + length + CRC32_SIZE > chunkSize) {
        return false; // Body would exceed chunk boundary
    }
    
    // 4. Check CRC32 (if we have the full data)
    if (offset + getTotalSize(length) <= chunkSize) {
        return validateCRC32(data, offset, length);
    }
    
    return true; // Complete Write Item
}
```

## Testing

- testCreateWriteItem()
- testToByteArray()
- testFromByteArray()
- test4KAlignment()
- testValidateCorrectCRC32()
- testValidateIncorrectCRC32()

## Effort Estimate

1 day
