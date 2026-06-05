package org.hyperkv.lsmplus.gc;

import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;

import static org.junit.jupiter.api.Assertions.*;

class OccupancyTrackerTest {

    private OccupancyTracker tracker;

    @BeforeEach
    void setUp() {
        tracker = new OccupancyTracker(0.3, 0.7);
    }

    @Test
    void testRecordOccupancy() {
        tracker.recordOccupancy(1, 1000, 800);

        OccupancyTracker.ChunkOccupancy occupancy = tracker.getOccupancy(1);
        assertNotNull(occupancy);
        assertEquals(1000L, occupancy.getTotalSize());
        assertEquals(800L, occupancy.getValidSize());
        assertEquals(0.8, occupancy.getRatio(), 0.01);
    }

    @Test
    void testGetOccupancyRatio() {
        tracker.recordOccupancy(1, 1000, 500);
        assertEquals(0.5, tracker.getOccupancyRatio(1), 0.01);

        assertEquals(0.0, tracker.getOccupancyRatio(999), 0.01);
    }

    @Test
    void testIsLowOccupancy() {
        tracker.recordOccupancy(1, 1000, 200);
        assertTrue(tracker.isLowOccupancy(1));

        tracker.recordOccupancy(2, 1000, 500);
        assertFalse(tracker.isLowOccupancy(2));
    }

    @Test
    void testIsHighOccupancy() {
        tracker.recordOccupancy(1, 1000, 800);
        assertTrue(tracker.isHighOccupancy(1));

        tracker.recordOccupancy(2, 1000, 500);
        assertFalse(tracker.isHighOccupancy(2));
    }

    @Test
    void testDetermineStrategy() {
        tracker.recordOccupancy(1, 1000, 200);
        assertEquals(GCStrategy.FULL_GC, tracker.determineStrategy(1));

        tracker.recordOccupancy(2, 1000, 500);
        assertEquals(GCStrategy.PARTIAL_GC, tracker.determineStrategy(2));

        tracker.recordOccupancy(3, 1000, 800);
        assertEquals(GCStrategy.HOLE_PUNCHING, tracker.determineStrategy(3));
    }

    @Test
    void testRemoveChunk() {
        tracker.recordOccupancy(1, 1000, 500);
        assertEquals(1, tracker.getChunkCount());

        tracker.removeChunk(1);
        assertEquals(0, tracker.getChunkCount());
        assertNull(tracker.getOccupancy(1));
    }

    @Test
    void testGetChunkCount() {
        assertEquals(0, tracker.getChunkCount());

        tracker.recordOccupancy(1, 1000, 500);
        assertEquals(1, tracker.getChunkCount());

        tracker.recordOccupancy(2, 1000, 500);
        assertEquals(2, tracker.getChunkCount());
    }

    @Test
    void testGetThresholds() {
        assertEquals(0.3, tracker.getLowOccupancyThreshold(), 0.01);
        assertEquals(0.7, tracker.getHighOccupancyThreshold(), 0.01);
    }

    @Test
    void testInvalidThresholds() {
        assertThrows(IllegalArgumentException.class, () -> new OccupancyTracker(-0.1, 0.7));
        assertThrows(IllegalArgumentException.class, () -> new OccupancyTracker(0.3, 1.5));
        assertThrows(IllegalArgumentException.class, () -> new OccupancyTracker(0.7, 0.3));
    }

    @Test
    void testInvalidTotalSize() {
        assertThrows(IllegalArgumentException.class, () -> tracker.recordOccupancy(1, 0, 500));
        assertThrows(IllegalArgumentException.class, () -> tracker.recordOccupancy(1, -1, 500));
    }

    @Test
    void testInvalidValidSize() {
        assertThrows(IllegalArgumentException.class, () -> tracker.recordOccupancy(1, 1000, -1));
    }

    @Test
    void testChunkOccupancyToString() {
        tracker.recordOccupancy(1, 1000, 500);
        OccupancyTracker.ChunkOccupancy occupancy = tracker.getOccupancy(1);
        String str = occupancy.toString();
        assertTrue(str.contains("chunkId=1"));
        assertTrue(str.contains("total=1000"));
        assertTrue(str.contains("valid=500"));
    }
}
