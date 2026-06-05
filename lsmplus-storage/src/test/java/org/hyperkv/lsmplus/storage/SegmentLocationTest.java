package org.hyperkv.lsmplus.storage;

import org.hyperkv.lsmplus.proto.Keyvalue.SegmentLocationProto;
import org.junit.jupiter.api.Test;

import java.util.UUID;

import static org.junit.jupiter.api.Assertions.*;

class SegmentLocationTest {

    @Test
    void testCreation() {
        UUID chunkId = UUID.randomUUID();
        SegmentLocation loc = new SegmentLocation(chunkId, 4096, 1024);

        assertEquals(chunkId, loc.getChunkId());
        assertEquals(4096, loc.getOffset());
        assertEquals(1024, loc.getLength());
    }

    @Test
    void testNullChunkIdThrows() {
        assertThrows(IllegalArgumentException.class,
                () -> new SegmentLocation(null, 0, 100));
    }

    @Test
    void testNegativeOffsetAllowedForVirtualLocations() {
        SegmentLocation virtualLoc = new SegmentLocation(UUID.randomUUID(), -1, 0);
        assertEquals(-1, virtualLoc.getOffset());
        assertEquals(0, virtualLoc.getLength());
    }

    @Test
    void testNegativeLengthThrows() {
        assertThrows(IllegalArgumentException.class,
                () -> new SegmentLocation(UUID.randomUUID(), 0, -1));
    }

    @Test
    void testToFromProto() {
        UUID chunkId = UUID.randomUUID();
        SegmentLocation original = new SegmentLocation(chunkId, 8192, 2048);

        SegmentLocationProto proto = original.toProto();
        SegmentLocation restored = SegmentLocation.fromProto(proto);

        assertEquals(original, restored);
        assertEquals(chunkId, restored.getChunkId());
        assertEquals(8192, restored.getOffset());
        assertEquals(2048, restored.getLength());
    }

    @Test
    void testToFromBytes() {
        UUID chunkId = UUID.randomUUID();
        SegmentLocation original = new SegmentLocation(chunkId, 4096, 512);

        byte[] bytes = original.toBytes();
        SegmentLocation restored = SegmentLocation.fromBytes(bytes);

        assertEquals(original, restored);
        assertEquals(chunkId, restored.getChunkId());
        assertEquals(4096, restored.getOffset());
        assertEquals(512, restored.getLength());
    }

    @Test
    void testFixedSize28Bytes() {
        SegmentLocation loc = new SegmentLocation(UUID.randomUUID(), 0, 0);
        byte[] bytes = loc.toBytes();

        assertEquals(SegmentLocation.FIXED_SIZE, bytes.length);
        assertEquals(28, bytes.length);
    }

    @Test
    void testFromBytesWrongSizeThrows() {
        assertThrows(IllegalArgumentException.class,
                () -> SegmentLocation.fromBytes(new byte[23]));
        assertThrows(IllegalArgumentException.class,
                () -> SegmentLocation.fromBytes(new byte[25]));
        assertThrows(IllegalArgumentException.class,
                () -> SegmentLocation.fromBytes(null));
    }

    @Test
    void testEqualsAndHashCode() {
        UUID chunkId = UUID.randomUUID();
        SegmentLocation loc1 = new SegmentLocation(chunkId, 100, 200);
        SegmentLocation loc2 = new SegmentLocation(chunkId, 100, 200);
        SegmentLocation loc3 = new SegmentLocation(chunkId, 100, 999);

        assertEquals(loc1, loc2);
        assertEquals(loc1.hashCode(), loc2.hashCode());
        assertNotEquals(loc1, loc3);
    }
    
    @Test
    void testCompareToEqual() {
        UUID chunkId = UUID.randomUUID();
        SegmentLocation loc1 = new SegmentLocation(chunkId, 100, 200);
        SegmentLocation loc2 = new SegmentLocation(chunkId, 100, 200);
        
        assertEquals(0, loc1.compareTo(loc2));
        assertEquals(0, loc2.compareTo(loc1));
    }
    
    @Test
    void testCompareToDifferentChunkId() {
        UUID chunkId1 = UUID.randomUUID();
        UUID chunkId2 = UUID.randomUUID();
        SegmentLocation loc1 = new SegmentLocation(chunkId1, 100, 200);
        SegmentLocation loc2 = new SegmentLocation(chunkId2, 100, 200);
        
        assertNotEquals(0, loc1.compareTo(loc2));
    }
    
    @Test
    void testCompareToDifferentOffset() {
        UUID chunkId = UUID.randomUUID();
        SegmentLocation loc1 = new SegmentLocation(chunkId, 100, 200);
        SegmentLocation loc2 = new SegmentLocation(chunkId, 200, 200);
        
        assertTrue(loc1.compareTo(loc2) < 0);
        assertTrue(loc2.compareTo(loc1) > 0);
    }
    
    @Test
    void testCompareToDifferentLength() {
        UUID chunkId = UUID.randomUUID();
        SegmentLocation loc1 = new SegmentLocation(chunkId, 100, 200);
        SegmentLocation loc2 = new SegmentLocation(chunkId, 100, 300);
        
        assertTrue(loc1.compareTo(loc2) < 0);
        assertTrue(loc2.compareTo(loc1) > 0);
    }
    
    @Test
    void testCompareToNull() {
        UUID chunkId = UUID.randomUUID();
        SegmentLocation loc = new SegmentLocation(chunkId, 100, 200);
        
        assertTrue(loc.compareTo(null) > 0);
    }
    
    @Test
    void testCompareToOrdering() {
        UUID chunkId1 = UUID.fromString("00000000-0000-0000-0000-000000000001");
        UUID chunkId2 = UUID.fromString("00000000-0000-0000-0000-000000000002");
        
        SegmentLocation loc1 = new SegmentLocation(chunkId1, 100, 200);
        SegmentLocation loc2 = new SegmentLocation(chunkId1, 200, 200);
        SegmentLocation loc3 = new SegmentLocation(chunkId1, 200, 300);
        SegmentLocation loc4 = new SegmentLocation(chunkId2, 100, 200);
        
        assertTrue(loc1.compareTo(loc2) < 0);
        assertTrue(loc2.compareTo(loc3) < 0);
        assertTrue(loc3.compareTo(loc4) < 0);
    }
    
    @Test
    void testComparableInSortedSet() {
        UUID chunkId = UUID.randomUUID();
        SegmentLocation loc1 = new SegmentLocation(chunkId, 300, 200);
        SegmentLocation loc2 = new SegmentLocation(chunkId, 100, 200);
        SegmentLocation loc3 = new SegmentLocation(chunkId, 200, 200);
        
        java.util.TreeSet<SegmentLocation> set = new java.util.TreeSet<>();
        set.add(loc1);
        set.add(loc2);
        set.add(loc3);
        
        SegmentLocation[] sorted = set.toArray(new SegmentLocation[0]);
        assertEquals(loc2, sorted[0]);
        assertEquals(loc3, sorted[1]);
        assertEquals(loc1, sorted[2]);
    }
}
