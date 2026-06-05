package org.hyperkv.lsmplus.api.model;

import com.google.protobuf.ByteString;
import com.google.protobuf.InvalidProtocolBufferException;
import org.hyperkv.lsmplus.proto.Common.KeyType;
import org.hyperkv.lsmplus.proto.Common.PageType;
import org.hyperkv.lsmplus.proto.Common.ValueType;
import org.hyperkv.lsmplus.proto.Keyvalue.KeyValuePairProto;
import org.hyperkv.lsmplus.proto.Keyvalue.KeyProto;
import org.hyperkv.lsmplus.proto.Keyvalue.ValueProto;
import org.hyperkv.lsmplus.proto.Keyvalue.SegmentLocationProto;
import org.hyperkv.lsmplus.proto.Page.PageProto;
import org.junit.jupiter.api.Test;

import java.nio.ByteBuffer;
import java.nio.ByteOrder;

import static org.junit.jupiter.api.Assertions.*;

class PageProtoTest {

    private ByteString encodeEntryOffsets(int[] offsets) {
        ByteBuffer buf = ByteBuffer.allocate(offsets.length * 4).order(ByteOrder.LITTLE_ENDIAN);
        for (int offset : offsets) {
            buf.putInt(offset);
        }
        return ByteString.copyFrom(buf.array());
    }

    private int[] decodeEntryOffsets(ByteString offsetsBytes) {
        int numEntries = offsetsBytes.size() / 4;
        int[] offsets = new int[numEntries];
        ByteBuffer buf = ByteBuffer.wrap(offsetsBytes.toByteArray()).order(ByteOrder.LITTLE_ENDIAN);
        for (int i = 0; i < numEntries; i++) {
            offsets[i] = buf.getInt();
        }
        return offsets;
    }

    private ByteString concatEntries(KeyValuePairProto... entries) {
        ByteBuffer buf = ByteBuffer.allocate(calculateTotalSize(entries));
        for (KeyValuePairProto entry : entries) {
            buf.put(entry.toByteArray());
        }
        return ByteString.copyFrom(buf.array(), 0, buf.position());
    }

    private int calculateTotalSize(KeyValuePairProto[] entries) {
        int size = 0;
        for (KeyValuePairProto entry : entries) {
            size += entry.getSerializedSize();
        }
        return size;
    }

    private KeyValuePairProto parseEntry(byte[] data, int offset, int length) throws InvalidProtocolBufferException {
        byte[] slice = new byte[length];
        System.arraycopy(data, offset, slice, 0, length);
        return KeyValuePairProto.parseFrom(slice);
    }

    @Test
    void testLeafPage() throws InvalidProtocolBufferException {
        IndexKey key1 = IndexKey.orderedBytes("key1".getBytes());
        IndexValue value1 = IndexValue.normal("value1".getBytes());
        IndexKey key2 = IndexKey.orderedBytes("key2".getBytes());
        IndexValue value2 = IndexValue.normal("value2".getBytes());

        KeyValuePairProto entry1 = KeyValuePairProto.newBuilder()
                .setKey(key1.toProto())
                .setValue(value1.toProto())
                .build();

        KeyValuePairProto entry2 = KeyValuePairProto.newBuilder()
                .setKey(key2.toProto())
                .setValue(value2.toProto())
                .build();

        int offset0 = 0;
        int offset1 = entry1.getSerializedSize();
        ByteString entryOffsets = encodeEntryOffsets(new int[]{offset0, offset1});
        ByteString entriesBytes = concatEntries(entry1, entry2);

        PageProto original = PageProto.newBuilder()
                .setPageType(PageType.PAGE_LEAF)
                .setPageId(123L)
                .setUsedSize(1024)
                .setEntryOffsets(entryOffsets)
                .setEntries(entriesBytes)
                .build();

        byte[] serialized = original.toByteArray();
        PageProto restored = PageProto.parseFrom(serialized);

        assertEquals(PageType.PAGE_LEAF, restored.getPageType());
        assertEquals(123L, restored.getPageId());
        assertEquals(1024, restored.getUsedSize());

        int[] offsets = decodeEntryOffsets(restored.getEntryOffsets());
        assertEquals(2, offsets.length);

        byte[] allEntries = restored.getEntries().toByteArray();
        KeyValuePairProto parsedEntry1 = parseEntry(allEntries, offsets[0], offsets[1] - offsets[0]);
        KeyValuePairProto parsedEntry2 = parseEntry(allEntries, offsets[1], allEntries.length - offsets[1]);

        assertEquals("key1", new String(parsedEntry1.getKey().getKeyData().toByteArray()));
        assertEquals("key2", new String(parsedEntry2.getKey().getKeyData().toByteArray()));
    }

    @Test
    void testIndexPage() throws InvalidProtocolBufferException {
        IndexKey key1 = IndexKey.orderedBytes("key1".getBytes());
        IndexKey key2 = IndexKey.orderedBytes("key2".getBytes());

        SegmentLocationProto location1 = SegmentLocationProto.newBuilder()
                .setChunkIdMostSig(1L)
                .setChunkIdLeastSig(2L)
                .setOffset(100L)
                .setLength(200)
                .build();

        SegmentLocationProto location2 = SegmentLocationProto.newBuilder()
                .setChunkIdMostSig(3L)
                .setChunkIdLeastSig(4L)
                .setOffset(300L)
                .setLength(400)
                .build();

        KeyValuePairProto entry1 = KeyValuePairProto.newBuilder()
                .setKey(key1.toProto())
                .setLocation(location1)
                .build();

        KeyValuePairProto entry2 = KeyValuePairProto.newBuilder()
                .setKey(key2.toProto())
                .setLocation(location2)
                .build();

        int offset0 = 0;
        int offset1 = entry1.getSerializedSize();
        ByteString entryOffsets = encodeEntryOffsets(new int[]{offset0, offset1});
        ByteString entriesBytes = concatEntries(entry1, entry2);

        PageProto original = PageProto.newBuilder()
                .setPageType(PageType.PAGE_BRANCH)
                .setPageId(-456L)
                .setUsedSize(2048)
                .setEntryOffsets(entryOffsets)
                .setEntries(entriesBytes)
                .build();

        byte[] serialized = original.toByteArray();
        PageProto restored = PageProto.parseFrom(serialized);

        assertEquals(PageType.PAGE_BRANCH, restored.getPageType());
        assertEquals(-456L, restored.getPageId());

        int[] offsets = decodeEntryOffsets(restored.getEntryOffsets());
        byte[] allEntries = restored.getEntries().toByteArray();
        KeyValuePairProto parsedEntry1 = parseEntry(allEntries, offsets[0], offsets[1] - offsets[0]);
        KeyValuePairProto parsedEntry2 = parseEntry(allEntries, offsets[1], allEntries.length - offsets[1]);

        assertEquals(location1, parsedEntry1.getLocation());
        assertEquals(location2, parsedEntry2.getLocation());
    }

    @Test
    void testEmptyPage() throws InvalidProtocolBufferException {
        PageProto original = PageProto.newBuilder()
                .setPageType(PageType.PAGE_LEAF)
                .setPageId(1L)
                .setUsedSize(0)
                .build();

        byte[] serialized = original.toByteArray();
        PageProto restored = PageProto.parseFrom(serialized);

        assertEquals(PageType.PAGE_LEAF, restored.getPageType());
        assertEquals(1L, restored.getPageId());
        assertEquals(0, restored.getEntryOffsets().size());
        assertEquals(0, restored.getEntries().size());
    }

    @Test
    void testPageMetadataPreservation() throws InvalidProtocolBufferException {
        long pageId = Long.MAX_VALUE;
        int usedSize = 12345;

        PageProto original = PageProto.newBuilder()
                .setPageType(PageType.PAGE_LEAF)
                .setPageId(pageId)
                .setUsedSize(usedSize)
                .build();

        byte[] serialized = original.toByteArray();
        PageProto restored = PageProto.parseFrom(serialized);

        assertEquals(pageId, restored.getPageId());
        assertEquals(usedSize, restored.getUsedSize());
    }

    @Test
    void testNegativePageIdForIndexPages() throws InvalidProtocolBufferException {
        PageProto indexPage = PageProto.newBuilder()
                .setPageType(PageType.PAGE_BRANCH)
                .setPageId(-100L)
                .setUsedSize(1024)
                .build();

        byte[] serialized = indexPage.toByteArray();
        PageProto restored = PageProto.parseFrom(serialized);

        assertEquals(-100L, restored.getPageId());
        assertEquals(PageType.PAGE_BRANCH, restored.getPageType());
    }

    @Test
    void testMixedEntriesInPage() throws InvalidProtocolBufferException {
        IndexKey key1 = IndexKey.orderedBytes("key1".getBytes());
        IndexValue normalValue = IndexValue.normal("value".getBytes());
        IndexKey key2 = IndexKey.orderedBytes("key2".getBytes());
        IndexValue tombstone = IndexValue.tombstone();

        KeyValuePairProto entry1 = KeyValuePairProto.newBuilder()
                .setKey(key1.toProto())
                .setValue(normalValue.toProto())
                .build();

        KeyValuePairProto entry2 = KeyValuePairProto.newBuilder()
                .setKey(key2.toProto())
                .setValue(tombstone.toProto())
                .build();

        int offset0 = 0;
        int offset1 = entry1.getSerializedSize();
        ByteString entryOffsets = encodeEntryOffsets(new int[]{offset0, offset1});
        ByteString entriesBytes = concatEntries(entry1, entry2);

        PageProto original = PageProto.newBuilder()
                .setPageType(PageType.PAGE_LEAF)
                .setPageId(1L)
                .setUsedSize(512)
                .setEntryOffsets(entryOffsets)
                .setEntries(entriesBytes)
                .build();

        byte[] serialized = original.toByteArray();
        PageProto restored = PageProto.parseFrom(serialized);

        int[] offsets = decodeEntryOffsets(restored.getEntryOffsets());
        byte[] allEntries = restored.getEntries().toByteArray();
        KeyValuePairProto parsedEntry1 = parseEntry(allEntries, offsets[0], offsets[1] - offsets[0]);
        KeyValuePairProto parsedEntry2 = parseEntry(allEntries, offsets[1], allEntries.length - offsets[1]);

        assertFalse(IndexValue.fromProto(parsedEntry1.getValue()).isTombstone());
        assertTrue(IndexValue.fromProto(parsedEntry2.getValue()).isTombstone());
    }

    @Test
    void testSegmentLocationInIndexPage() throws InvalidProtocolBufferException {
        IndexKey key = IndexKey.orderedBytes("index-key".getBytes());
        
        SegmentLocationProto location = SegmentLocationProto.newBuilder()
                .setChunkIdMostSig(123456789L)
                .setChunkIdLeastSig(987654321L)
                .setOffset(-1L)
                .setLength(0)
                .build();

        KeyValuePairProto entry = KeyValuePairProto.newBuilder()
                .setKey(key.toProto())
                .setLocation(location)
                .build();

        ByteString entryOffsets = encodeEntryOffsets(new int[]{0});
        ByteString entriesBytes = ByteString.copyFrom(entry.toByteArray());

        PageProto original = PageProto.newBuilder()
                .setPageType(PageType.PAGE_BRANCH)
                .setPageId(-1L)
                .setUsedSize(100)
                .setEntryOffsets(entryOffsets)
                .setEntries(entriesBytes)
                .build();

        byte[] serialized = original.toByteArray();
        PageProto restored = PageProto.parseFrom(serialized);

        int[] offsets = decodeEntryOffsets(restored.getEntryOffsets());
        byte[] allEntries = restored.getEntries().toByteArray();
        int entryLength = (offsets.length > 1 ? offsets[1] : allEntries.length) - offsets[0];
        byte[] entryBytes = new byte[entryLength];
        System.arraycopy(allEntries, offsets[0], entryBytes, 0, entryLength);
        KeyValuePairProto parsedEntry = KeyValuePairProto.parseFrom(entryBytes);

        SegmentLocationProto restoredLocation = parsedEntry.getLocation();
        assertEquals(123456789L, restoredLocation.getChunkIdMostSig());
        assertEquals(987654321L, restoredLocation.getChunkIdLeastSig());
        assertEquals(-1L, restoredLocation.getOffset());
        assertEquals(0, restoredLocation.getLength());
    }

    @Test
    void testEntryOffsetsLittleEndian() throws InvalidProtocolBufferException {
        int[] testOffsets = {0, 42, 256, 65536, Integer.MAX_VALUE};
        ByteString encoded = encodeEntryOffsets(testOffsets);

        PageProto page = PageProto.newBuilder()
                .setPageType(PageType.PAGE_LEAF)
                .setPageId(1L)
                .setUsedSize(0)
                .setEntryOffsets(encoded)
                .build();

        byte[] serialized = page.toByteArray();
        PageProto restored = PageProto.parseFrom(serialized);

        int[] decoded = decodeEntryOffsets(restored.getEntryOffsets());
        assertArrayEquals(testOffsets, decoded);
    }
}
