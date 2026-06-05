package org.hyperkv.lsmplus.gc;

import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;

import static org.junit.jupiter.api.Assertions.*;

class MNSTrackerTest {

    private MNSTracker tracker;

    @BeforeEach
    void setUp() {
        tracker = new MNSTracker();
    }

    @Test
    void testRecordMNS() {
        tracker.recordMNS(1, 100);
        tracker.recordMNS(2, 150);

        assertEquals(100L, tracker.getMNS(1));
        assertEquals(150L, tracker.getMNS(2));
    }

    @Test
    void testGetCurrentMNS() {
        tracker.recordMNS(1, 100);
        assertEquals(100L, tracker.getCurrentMNS());

        tracker.recordMNS(2, 150);
        assertEquals(150L, tracker.getCurrentMNS());
    }

    @Test
    void testGetCurrentVersion() {
        tracker.recordMNS(1, 100);
        assertEquals(1L, tracker.getCurrentVersion());

        tracker.recordMNS(2, 150);
        assertEquals(2L, tracker.getCurrentVersion());
    }

    @Test
    void testCanGC() {
        tracker.recordMNS(1, 100);

        assertTrue(tracker.canGC(50));
        assertTrue(tracker.canGC(99));
        assertFalse(tracker.canGC(100));
        assertFalse(tracker.canGC(150));
    }

    @Test
    void testUpdateMNS() {
        tracker.updateMNS(100);
        assertEquals(100L, tracker.getCurrentMNS());

        tracker.updateMNS(50);
        assertEquals(100L, tracker.getCurrentMNS());

        tracker.updateMNS(200);
        assertEquals(200L, tracker.getCurrentMNS());
    }

    @Test
    void testClearVersionsBefore() {
        tracker.recordMNS(1, 100);
        tracker.recordMNS(2, 150);
        tracker.recordMNS(3, 200);

        tracker.clearVersionsBefore(2);

        assertNull(tracker.getMNS(1));
        assertEquals(150L, tracker.getMNS(2));
        assertEquals(200L, tracker.getMNS(3));
    }

    @Test
    void testGetVersionCount() {
        assertEquals(0, tracker.getVersionCount());

        tracker.recordMNS(1, 100);
        assertEquals(1, tracker.getVersionCount());

        tracker.recordMNS(2, 150);
        assertEquals(2, tracker.getVersionCount());
    }

    @Test
    void testInvalidVersion() {
        assertThrows(IllegalArgumentException.class, () -> tracker.recordMNS(-1, 100));
    }

    @Test
    void testInvalidMNS() {
        assertThrows(IllegalArgumentException.class, () -> tracker.recordMNS(1, -1));
    }
}
