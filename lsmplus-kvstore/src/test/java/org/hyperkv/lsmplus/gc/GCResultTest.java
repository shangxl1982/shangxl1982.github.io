package org.hyperkv.lsmplus.gc;

import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;

import static org.junit.jupiter.api.Assertions.*;

class GCResultTest {

    private GCResult result;

    @BeforeEach
    void setUp() {
        result = new GCResult();
    }

    @Test
    void testInitialState() {
        assertEquals(0, result.getFullGCCount());
        assertEquals(0, result.getPartialGCCount());
        assertEquals(0, result.getHolePunchingCount());
        assertEquals(0, result.getTotalGCCount());
        assertEquals(0L, result.getReclaimedSpace());
        assertFalse(result.hasReclaimed());
    }

    @Test
    void testIncrementFullGC() {
        result.incrementFullGC();
        assertEquals(1, result.getFullGCCount());
        assertEquals(1, result.getTotalGCCount());
        assertTrue(result.hasReclaimed());
    }

    @Test
    void testIncrementPartialGC() {
        result.incrementPartialGC();
        assertEquals(1, result.getPartialGCCount());
        assertEquals(1, result.getTotalGCCount());
        assertTrue(result.hasReclaimed());
    }

    @Test
    void testIncrementHolePunching() {
        result.incrementHolePunching();
        assertEquals(1, result.getHolePunchingCount());
        assertEquals(1, result.getTotalGCCount());
        assertTrue(result.hasReclaimed());
    }

    @Test
    void testAddReclaimedSpace() {
        result.addReclaimedSpace(1000);
        assertEquals(1000L, result.getReclaimedSpace());

        result.addReclaimedSpace(500);
        assertEquals(1500L, result.getReclaimedSpace());
    }

    @Test
    void testMultipleOperations() {
        result.incrementFullGC();
        result.incrementFullGC();
        result.incrementPartialGC();
        result.incrementHolePunching();
        result.addReclaimedSpace(2000);

        assertEquals(2, result.getFullGCCount());
        assertEquals(1, result.getPartialGCCount());
        assertEquals(1, result.getHolePunchingCount());
        assertEquals(4, result.getTotalGCCount());
        assertEquals(2000L, result.getReclaimedSpace());
    }

    @Test
    void testToString() {
        result.incrementFullGC();
        result.addReclaimedSpace(1000);
        String str = result.toString();
        assertTrue(str.contains("full=1"));
        assertTrue(str.contains("reclaimed=1000"));
    }
}
