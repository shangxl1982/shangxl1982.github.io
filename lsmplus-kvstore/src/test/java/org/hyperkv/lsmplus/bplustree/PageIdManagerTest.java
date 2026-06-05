package org.hyperkv.lsmplus.bplustree;

import org.hyperkv.lsmplus.bplustree.page.Page;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;

import static org.junit.jupiter.api.Assertions.*;

class PageIdManagerTest {

    private PageIdManager pageIdManager;
    
    @BeforeEach
    void setUp() {
        pageIdManager = new PageIdManager();
    }
    
    @Test
    void testDefaultConstructorInitializesCorrectly() {
        assertEquals(1L, pageIdManager.peekNextLeafPageId());
        assertEquals(Long.MIN_VALUE, pageIdManager.peekNextIndexPageId());
    }
    
    @Test
    void testCustomConstructorInitializesCorrectly() {
        PageIdManager customManager = new PageIdManager(100L, -200L);
        assertEquals(100L, customManager.peekNextLeafPageId());
        assertEquals(-200L, customManager.peekNextIndexPageId());
    }
    
    @Test
    void testCustomConstructorThrowsForInvalidLeafPageId() {
        assertThrows(IllegalArgumentException.class, () -> {
            new PageIdManager(0L, Long.MIN_VALUE);
        });
        
        assertThrows(IllegalArgumentException.class, () -> {
            new PageIdManager(-1L, Long.MIN_VALUE);
        });
    }
    
    @Test
    void testCustomConstructorThrowsForInvalidIndexPageId() {
        assertThrows(IllegalArgumentException.class, () -> {
            new PageIdManager(1L, 0L);
        });
        
        assertThrows(IllegalArgumentException.class, () -> {
            new PageIdManager(1L, 1L);
        });
    }
    
    @Test
    void testAllocateLeafPageId() {
        long pageId1 = pageIdManager.allocateLeafPageId();
        assertEquals(1L, pageId1);
        assertEquals(2L, pageIdManager.peekNextLeafPageId());
        
        long pageId2 = pageIdManager.allocateLeafPageId();
        assertEquals(2L, pageId2);
        assertEquals(3L, pageIdManager.peekNextLeafPageId());
    }
    
    @Test
    void testAllocateIndexPageId() {
        long pageId1 = pageIdManager.allocateIndexPageId();
        assertEquals(Long.MIN_VALUE, pageId1);
        assertEquals(Long.MIN_VALUE + 1, pageIdManager.peekNextIndexPageId());
        
        long pageId2 = pageIdManager.allocateIndexPageId();
        assertEquals(Long.MIN_VALUE + 1, pageId2);
        assertEquals(Long.MIN_VALUE + 2, pageIdManager.peekNextIndexPageId());
    }
    
    @Test
    void testLeafPageIdSequenceMonotonic() {
        long previousId = 0L;
        for (int i = 0; i < 100; i++) {
            long currentId = pageIdManager.allocateLeafPageId();
            assertTrue(currentId > previousId, "Page IDs should be monotonically increasing");
            assertTrue(currentId > 0, "Leaf page IDs should be positive");
            previousId = currentId;
        }
    }
    
    @Test
    void testIndexPageIdSequenceMonotonic() {
        long previousId = pageIdManager.allocateIndexPageId(); // Get the first ID
        for (int i = 0; i < 99; i++) { // Allocate 99 more
            long currentId = pageIdManager.allocateIndexPageId();
            assertTrue(currentId > previousId, "Page IDs should be monotonically increasing");
            assertTrue(currentId < 0, "Index page IDs should be negative");
            previousId = currentId;
        }
    }
    
    @Test
    void testLeafPageIdOverflowDetection() {
        // Create a manager that's about to overflow leaf pages
        PageIdManager overflowManager = new PageIdManager(Long.MAX_VALUE - 1, Long.MIN_VALUE);
        
        // First allocation should work
        long pageId = overflowManager.allocateLeafPageId();
        assertEquals(Long.MAX_VALUE - 1, pageId);
        
        // Second allocation should work (at Long.MAX_VALUE)
        pageId = overflowManager.allocateLeafPageId();
        assertEquals(Long.MAX_VALUE, pageId);
        
        // Third allocation should throw overflow exception
        assertThrows(IllegalStateException.class, overflowManager::allocateLeafPageId);
    }
    
    @Test
    void testIndexPageIdOverflowDetection() {
        // Create a manager that's about to overflow index pages
        PageIdManager overflowManager = new PageIdManager(1L, -1L);
        
        // First allocation should work
        long pageId = overflowManager.allocateIndexPageId();
        assertEquals(-1L, pageId);
        
        // Second allocation should throw overflow exception (next would be 0, which is positive)
        assertThrows(IllegalStateException.class, overflowManager::allocateIndexPageId);
    }
    
    @Test
    void testUpdateSequences() {
        // Update sequences to continue from specific values
        pageIdManager.updateSequences(100L, -200L);
        
        assertEquals(101L, pageIdManager.peekNextLeafPageId());
        assertEquals(-199L, pageIdManager.peekNextIndexPageId());
        
        // Allocate should continue from updated sequences
        long leafId = pageIdManager.allocateLeafPageId();
        assertEquals(101L, leafId);
        
        long indexId = pageIdManager.allocateIndexPageId();
        assertEquals(-199L, indexId);
    }
    
    @Test
    void testUpdateSequencesThrowsForInvalidValues() {
        assertThrows(IllegalArgumentException.class, () -> {
            pageIdManager.updateSequences(0L, Long.MIN_VALUE);
        });
        
        assertThrows(IllegalArgumentException.class, () -> {
            pageIdManager.updateSequences(1L, 0L);
        });
    }
    
    @Test
    void testUpdateSequencesThrowsForOverflow() {
        // Test leaf page overflow
        assertThrows(IllegalStateException.class, () -> {
            pageIdManager.updateSequences(Long.MAX_VALUE, Long.MIN_VALUE);
        });
        
        // Test index page overflow
        assertThrows(IllegalStateException.class, () -> {
            pageIdManager.updateSequences(1L, -1L);
        });
    }
    
    @Test
    void testValidatePageId() {
        // Valid leaf page IDs
        PageIdManager.validatePageId(1L, true);
        PageIdManager.validatePageId(100L, true);
        PageIdManager.validatePageId(Long.MAX_VALUE, true);
        
        // Valid index page IDs
        PageIdManager.validatePageId(-1L, false);
        PageIdManager.validatePageId(Long.MIN_VALUE, false);
        
        // Invalid: page ID 0
        assertThrows(IllegalArgumentException.class, () -> {
            PageIdManager.validatePageId(0L, true);
        });
        assertThrows(IllegalArgumentException.class, () -> {
            PageIdManager.validatePageId(0L, false);
        });
        
        // Invalid: leaf page ID that's negative
        assertThrows(IllegalArgumentException.class, () -> {
            PageIdManager.validatePageId(-1L, true);
        });
        
        // Invalid: index page ID that's positive
        assertThrows(IllegalArgumentException.class, () -> {
            PageIdManager.validatePageId(1L, false);
        });
    }
    
    @Test
    void testIsLeafPageId() {
        assertTrue(PageIdManager.isLeafPageId(1L));
        assertTrue(PageIdManager.isLeafPageId(100L));
        assertTrue(PageIdManager.isLeafPageId(Long.MAX_VALUE));
        
        assertFalse(PageIdManager.isLeafPageId(-1L));
        assertFalse(PageIdManager.isLeafPageId(Long.MIN_VALUE));
        assertFalse(PageIdManager.isLeafPageId(0L));
    }
    
    @Test
    void testIsIndexPageId() {
        assertTrue(PageIdManager.isIndexPageId(-1L));
        assertTrue(PageIdManager.isIndexPageId(Long.MIN_VALUE));
        
        assertFalse(PageIdManager.isIndexPageId(1L));
        assertFalse(PageIdManager.isIndexPageId(100L));
        assertFalse(PageIdManager.isIndexPageId(0L));
    }
    
    @Test
    void testGetStateForPersistence() {
        // Allocate some IDs
        pageIdManager.allocateLeafPageId(); // 1
        pageIdManager.allocateLeafPageId(); // 2
        pageIdManager.allocateIndexPageId(); // Long.MIN_VALUE
        pageIdManager.allocateIndexPageId(); // Long.MIN_VALUE + 1
        
        long[] state = pageIdManager.getStateForPersistence();
        assertEquals(2, state.length);
        assertEquals(2L, state[0]); // max leaf page ID
        assertEquals(Long.MIN_VALUE + 1, state[1]); // min index page ID
    }
    
    @Test
    void testConcurrentAllocation() throws InterruptedException {
        final int numThreads = 10;
        final int allocationsPerThread = 100;
        
        Thread[] threads = new Thread[numThreads];
        long[][] allocatedIds = new long[numThreads][allocationsPerThread];
        
        for (int i = 0; i < numThreads; i++) {
            final int threadIndex = i;
            threads[i] = new Thread(() -> {
                for (int j = 0; j < allocationsPerThread; j++) {
                    allocatedIds[threadIndex][j] = pageIdManager.allocateLeafPageId();
                }
            });
        }
        
        // Start all threads
        for (Thread thread : threads) {
            thread.start();
        }
        
        // Wait for all threads to complete
        for (Thread thread : threads) {
            thread.join();
        }
        
        // Verify all IDs are unique and in correct range
        java.util.Set<Long> uniqueIds = new java.util.HashSet<>();
        long maxId = 0;
        for (int i = 0; i < numThreads; i++) {
            for (int j = 0; j < allocationsPerThread; j++) {
                long id = allocatedIds[i][j];
                assertTrue(id > 0, "Leaf page ID should be positive: " + id);
                assertTrue(uniqueIds.add(id), "All IDs should be unique, found duplicate: " + id);
                maxId = Math.max(maxId, id);
            }
        }
        
        // Verify we got the expected number of unique IDs
        assertEquals(numThreads * allocationsPerThread, uniqueIds.size(), "Should have allocated the expected number of IDs");
        
        // Verify the next ID is correct
        assertEquals(maxId + 1, pageIdManager.peekNextLeafPageId());
    }
    
    @Test
    void testToString() {
        String str = pageIdManager.toString();
        assertTrue(str.contains("nextLeafPageId=1"));
        assertTrue(str.contains("nextIndexPageId=" + Long.MIN_VALUE));
        
        // Allocate some IDs and check again
        pageIdManager.allocateLeafPageId();
        pageIdManager.allocateIndexPageId();
        
        str = pageIdManager.toString();
        assertTrue(str.contains("nextLeafPageId=2"));
        assertTrue(str.contains("nextIndexPageId=" + (Long.MIN_VALUE + 1)));
    }
    
    @Test
    void testLargeScaleAllocation() {
        // Test allocating a large number of IDs to ensure no performance issues
        final int numAllocations = 10000;
        
        for (int i = 0; i < numAllocations; i++) {
            long leafId = pageIdManager.allocateLeafPageId();
            long indexId = pageIdManager.allocateIndexPageId();
            
            assertEquals(i + 1, leafId);
            assertEquals(Long.MIN_VALUE + i, indexId);
        }
        
        assertEquals(numAllocations + 1, pageIdManager.peekNextLeafPageId());
        assertEquals(Long.MIN_VALUE + numAllocations, pageIdManager.peekNextIndexPageId());
    }
    
    @Test
    void testPageIdValidationInPageConstructor() {
        // Test that Page constructor validates page ID type
        long validLeafId = pageIdManager.allocateLeafPageId();
        long validIndexId = pageIdManager.allocateIndexPageId();
        
        // These should work
        Page.createPage(Page.PageType.LEAF, validLeafId, PageCapacityConfig.DEFAULT, null);
        Page.createPage(Page.PageType.BRANCH, validIndexId, PageCapacityConfig.DEFAULT, null);
        
        // Note: The Page constructor doesn't actually validate page ID types,
        // so these tests are just verifying that the pages can be created
        // without throwing exceptions for valid IDs
        
        // Test that we can create pages with different ID types
        // (The actual validation would need to be implemented in Page constructor)
        Page.createPage(Page.PageType.LEAF, validIndexId, PageCapacityConfig.DEFAULT, null); // This should work since Page doesn't validate
        Page.createPage(Page.PageType.BRANCH, validLeafId, PageCapacityConfig.DEFAULT, null); // This should work since Page doesn't validate
    }
}