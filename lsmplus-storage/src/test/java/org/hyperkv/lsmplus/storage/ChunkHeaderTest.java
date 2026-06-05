package org.hyperkv.lsmplus.storage;

import org.hyperkv.lsmplus.proto.Common.ChunkType;
import org.junit.jupiter.api.Test;

import java.util.UUID;

import static org.junit.jupiter.api.Assertions.*;

class ChunkHeaderTest {

    @Test
    void testCreateHeader() {
        UUID chunkId = UUID.randomUUID();
        UUID ownerId = UUID.randomUUID();
        UUID namespaceId = UUID.randomUUID();

        ChunkHeader header = new ChunkHeader(chunkId, ChunkType.CHUNK_INDEX, ownerId, namespaceId);

        assertEquals(chunkId, header.getChunkId());
        assertEquals(ChunkType.CHUNK_INDEX, header.getChunkType());
        assertEquals(ownerId, header.getOwnerId());
        assertEquals(namespaceId, header.getNamespaceId());
        assertEquals(0, header.getValidDataSize());
    }

    @Test
    void testSetValidDataSize() {
        ChunkHeader header = new ChunkHeader(
                UUID.randomUUID(), ChunkType.CHUNK_LEAF,
                UUID.randomUUID(), UUID.randomUUID());

        header.setValidDataSize(8192);
        assertEquals(8192, header.getValidDataSize());
    }

    @Test
    void testSetNegativeValidDataSizeThrows() {
        ChunkHeader header = new ChunkHeader(
                UUID.randomUUID(), ChunkType.CHUNK_LEAF,
                UUID.randomUUID(), UUID.randomUUID());

        assertThrows(IllegalArgumentException.class, () -> header.setValidDataSize(-1));
    }

    @Test
    void testNullParametersThrow() {
        UUID id = UUID.randomUUID();
        assertThrows(IllegalArgumentException.class,
                () -> new ChunkHeader(null, ChunkType.CHUNK_INDEX, id, id));
        assertThrows(IllegalArgumentException.class,
                () -> new ChunkHeader(id, null, id, id));
        assertThrows(IllegalArgumentException.class,
                () -> new ChunkHeader(id, ChunkType.CHUNK_INDEX, null, id));
        assertThrows(IllegalArgumentException.class,
                () -> new ChunkHeader(id, ChunkType.CHUNK_INDEX, id, null));
    }

    @Test
    void testToByteArrayFixedSize4096() {
        ChunkHeader header = new ChunkHeader(
                UUID.randomUUID(), ChunkType.CHUNK_JOURNAL,
                UUID.randomUUID(), UUID.randomUUID());

        byte[] bytes = header.toByteArray();
        assertEquals(4096, bytes.length);
    }

    @Test
    void testFromByteArrayRoundTrip() {
        UUID chunkId = UUID.randomUUID();
        UUID ownerId = UUID.randomUUID();
        UUID namespaceId = UUID.randomUUID();

        ChunkHeader original = new ChunkHeader(chunkId, ChunkType.CHUNK_INDEX, ownerId, namespaceId);
        original.setValidDataSize(12345);

        byte[] bytes = original.toByteArray();
        ChunkHeader restored = ChunkHeader.fromByteArray(bytes);

        assertEquals(original, restored);
        assertEquals(chunkId, restored.getChunkId());
        assertEquals(ChunkType.CHUNK_INDEX, restored.getChunkType());
        assertEquals(ownerId, restored.getOwnerId());
        assertEquals(namespaceId, restored.getNamespaceId());
        assertEquals(12345, restored.getValidDataSize());
    }

    @Test
    void testRoundTripAllChunkTypes() {
        ChunkType[] types = {ChunkType.CHUNK_INDEX, ChunkType.CHUNK_LEAF, ChunkType.CHUNK_JOURNAL};
        for (ChunkType type : types) {
            ChunkHeader original = new ChunkHeader(
                    UUID.randomUUID(), type,
                    UUID.randomUUID(), UUID.randomUUID());
            original.setValidDataSize(4096);

            byte[] bytes = original.toByteArray();
            ChunkHeader restored = ChunkHeader.fromByteArray(bytes);

            assertEquals(type, restored.getChunkType());
            assertEquals(original, restored);
        }
    }

    @Test
    void testFromByteArrayInsufficientDataThrows() {
        assertThrows(IllegalArgumentException.class,
                () -> ChunkHeader.fromByteArray(new byte[100]));
        assertThrows(IllegalArgumentException.class,
                () -> ChunkHeader.fromByteArray(null));
    }

    @Test
    void testFromByteArrayUnknownChunkTypeThrows() {
        UUID id = UUID.randomUUID();
        ChunkHeader header = new ChunkHeader(id, ChunkType.CHUNK_INDEX, id, id);
        byte[] bytes = header.toByteArray();

        java.nio.ByteBuffer.wrap(bytes, 16, 4).putInt(999);

        assertThrows(IllegalArgumentException.class,
                () -> ChunkHeader.fromByteArray(bytes));
    }

    @Test
    void testFieldAlignment() {
        assertEquals(0, ChunkHeader.CHUNK_ID_OFFSET);
        assertEquals(16, ChunkHeader.CHUNK_TYPE_OFFSET);
        assertEquals(20, ChunkHeader.OWNER_ID_OFFSET);
        assertEquals(36, ChunkHeader.NAMESPACE_ID_OFFSET);
        assertEquals(52, ChunkHeader.VALID_DATA_SIZE_OFFSET);
        assertEquals(56, ChunkHeader.RESERVED_OFFSET);
    }

    @Test
    void testReservedAreaIsZero() {
        ChunkHeader header = new ChunkHeader(
                UUID.randomUUID(), ChunkType.CHUNK_LEAF,
                UUID.randomUUID(), UUID.randomUUID());
        byte[] bytes = header.toByteArray();

        for (int i = ChunkHeader.RESERVED_OFFSET; i < ChunkHeader.HEADER_SIZE; i++) {
            assertEquals(0, bytes[i], "Reserved byte at offset " + i + " should be 0");
        }
    }

    @Test
    void testEqualsAndHashCode() {
        UUID chunkId = UUID.randomUUID();
        UUID ownerId = UUID.randomUUID();
        UUID nsId = UUID.randomUUID();

        ChunkHeader h1 = new ChunkHeader(chunkId, ChunkType.CHUNK_INDEX, ownerId, nsId);
        ChunkHeader h2 = new ChunkHeader(chunkId, ChunkType.CHUNK_INDEX, ownerId, nsId);

        assertEquals(h1, h2);
        assertEquals(h1.hashCode(), h2.hashCode());

        h2.setValidDataSize(100);
        assertNotEquals(h1, h2);
    }
}