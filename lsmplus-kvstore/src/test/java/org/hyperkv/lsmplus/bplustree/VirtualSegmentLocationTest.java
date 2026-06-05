package org.hyperkv.lsmplus.bplustree;

import org.hyperkv.lsmplus.api.model.IndexKey;
import org.hyperkv.lsmplus.bplustree.page.Page;
import org.hyperkv.lsmplus.storage.SegmentLocation;
import org.junit.jupiter.api.Test;

import java.util.UUID;

import static org.junit.jupiter.api.Assertions.*;

class VirtualSegmentLocationTest {

    @Test
    void testCreateVirtualLocationForLeafPage() {
        long leafPageId = 12345L;
        SegmentLocation location = VirtualSegmentLocation.create(leafPageId);
        
        assertNotNull(location);
        assertEquals(VirtualSegmentLocation.VIRTUAL_CHUNK_ID, location.getChunkId());
        assertTrue(VirtualSegmentLocation.isVirtual(location));
        
        // Verify the page ID is stored in the offset field
        assertEquals(leafPageId, location.getOffset());
        assertEquals(0, location.getLength()); // Length is 0 for virtual locations
    }
    
    @Test
    void testCreateVirtualLocationForIndexPage() {
        long indexPageId = -98765L;
        SegmentLocation location = VirtualSegmentLocation.create(indexPageId);
        
        assertNotNull(location);
        assertEquals(VirtualSegmentLocation.VIRTUAL_CHUNK_ID, location.getChunkId());
        assertTrue(VirtualSegmentLocation.isVirtual(location));
        
        // Verify the page ID is stored in the offset field
        assertEquals(indexPageId, location.getOffset());
        assertEquals(0, location.getLength()); // Length is 0 for virtual locations
    }
    
    @Test
    void testExtractPageIdFromVirtualLocation() {
        // Test with positive page ID (leaf)
        long leafPageId = 123456789L;
        SegmentLocation location = VirtualSegmentLocation.create(leafPageId);
        long extractedId = VirtualSegmentLocation.extractPageId(location);
        
        assertEquals(leafPageId, extractedId);
        
        // Test with negative page ID (index)
        long indexPageId = -987654321L;
        location = VirtualSegmentLocation.create(indexPageId);
        extractedId = VirtualSegmentLocation.extractPageId(location);
        
        assertEquals(indexPageId, extractedId);
    }
    
    @Test
    void testExtractPageIdThrowsForNonVirtualLocation() {
        SegmentLocation realLocation = new SegmentLocation(UUID.randomUUID(), 100, 200);
        
        assertThrows(IllegalArgumentException.class, () -> {
            VirtualSegmentLocation.extractPageId(realLocation);
        });
    }
    
    @Test
    void testIsVirtualLocation() {
        // Virtual location
        SegmentLocation virtualLocation = VirtualSegmentLocation.create(12345L);
        assertTrue(VirtualSegmentLocation.isVirtual(virtualLocation));
        
        // Real location
        SegmentLocation realLocation = new SegmentLocation(UUID.randomUUID(), 100, 200);
        assertFalse(VirtualSegmentLocation.isVirtual(realLocation));
        
        // Null location
        assertFalse(VirtualSegmentLocation.isVirtual(null));
    }
    
    @Test
    void testHasVirtualChildReferences() {
        // Create a page with virtual child references
        long pageId = 1L;
        Page page = Page.createPage(Page.PageType.BRANCH, pageId, PageCapacityConfig.DEFAULT, null);
        
        // Add virtual entries
        long childPageId1 = 2L;
        SegmentLocation virtualLocation1 = VirtualSegmentLocation.create(childPageId1);
        page.put(org.hyperkv.lsmplus.api.model.IndexKey.orderedBytes("key1".getBytes()), virtualLocation1);
        
        // Add virtual entry
        long childPageId2 = 3L;
        SegmentLocation virtualLocation2 = VirtualSegmentLocation.create(childPageId2);
        page.put(org.hyperkv.lsmplus.api.model.IndexKey.orderedBytes("key2".getBytes()), virtualLocation2);
        
        assertTrue(VirtualSegmentLocation.hasVirtualChildReferences(page));
    }
    
    @Test
    void testHasVirtualChildReferencesWithRealLocations() {
        // Create a page with real child references
        long pageId = 1L;
        Page page = Page.createPage(Page.PageType.BRANCH, pageId, PageCapacityConfig.DEFAULT, null);
        
        // Add real entries
        SegmentLocation realLocation1 = new SegmentLocation(UUID.randomUUID(), 100, 200);
        page.put(org.hyperkv.lsmplus.api.model.IndexKey.orderedBytes("key1".getBytes()), realLocation1);
        
        SegmentLocation realLocation2 = new SegmentLocation(UUID.randomUUID(), 300, 400);
        page.put(org.hyperkv.lsmplus.api.model.IndexKey.orderedBytes("key2".getBytes()), realLocation2);
        
        assertFalse(VirtualSegmentLocation.hasVirtualChildReferences(page));
    }
    
    @Test
    void testHasVirtualChildReferencesWithMixedLocations() {
        // Create a page with mixed virtual and real child references
        long pageId = 1L;
        Page page = Page.createPage(Page.PageType.BRANCH, pageId, PageCapacityConfig.DEFAULT, null);
        
        // Add virtual entry
        long childPageId = 2L;
        SegmentLocation virtualLocation = VirtualSegmentLocation.create(childPageId);
        page.put(org.hyperkv.lsmplus.api.model.IndexKey.orderedBytes("key1".getBytes()), virtualLocation);
        
        // Add real entry
        SegmentLocation realLocation = new SegmentLocation(UUID.randomUUID(), 100, 200);
        page.put(org.hyperkv.lsmplus.api.model.IndexKey.orderedBytes("key2".getBytes()), realLocation);
        
        assertTrue(VirtualSegmentLocation.hasVirtualChildReferences(page));
    }
    
    @Test
    void testHasVirtualChildReferencesWithLeafPage() {
        // Leaf pages should not have child references
        long pageId = 1L;
        Page page = Page.createPage(Page.PageType.LEAF, pageId, PageCapacityConfig.DEFAULT, null);
        
        // Add some entries (leaf pages don't have child references)
        page.put(org.hyperkv.lsmplus.api.model.IndexKey.orderedBytes("key1".getBytes()), 
                 org.hyperkv.lsmplus.api.model.IndexValue.normal("value1".getBytes()));
        
        assertFalse(VirtualSegmentLocation.hasVirtualChildReferences(page));
    }
    
    @Test
    void testLargePageIdHandling() {
        // Test with very large page IDs
        long largePageId = Long.MAX_VALUE;
        SegmentLocation location = VirtualSegmentLocation.create(largePageId);
        long extractedId = VirtualSegmentLocation.extractPageId(location);
        
        assertEquals(largePageId, extractedId);
        
        // Test with very negative page IDs
        long negativePageId = Long.MIN_VALUE;
        location = VirtualSegmentLocation.create(negativePageId);
        extractedId = VirtualSegmentLocation.extractPageId(location);
        
        assertEquals(negativePageId, extractedId);
    }
    
    @Test
    void testPageIdRoundTrip() {
        // Test that we can go from page ID → virtual location → page ID
        long[] testPageIds = {
            1L, 100L, 1000L, 1000000L, Long.MAX_VALUE,
            -1L, -100L, -1000L, -1000000L, Long.MIN_VALUE
        };
        
        for (long originalPageId : testPageIds) {
            SegmentLocation location = VirtualSegmentLocation.create(originalPageId);
            long roundTripPageId = VirtualSegmentLocation.extractPageId(location);
            
            assertEquals(originalPageId, roundTripPageId, 
                        "Round-trip failed for page ID: " + originalPageId);
        }
    }
    
    @Test
    void testVirtualLocationFormatConsistency() {
        // Verify that the new encoding scheme works correctly
        long pageId = 0x123456789ABCDEF0L; // A large page ID with distinct bytes
        SegmentLocation location = VirtualSegmentLocation.create(pageId);
        
        // Verify the page ID is stored directly in the offset field
        assertEquals(pageId, location.getOffset());
        assertEquals(0, location.getLength()); // Length is 0 for virtual locations
        
        // Verify round-trip
        long extractedId = VirtualSegmentLocation.extractPageId(location);
        assertEquals(pageId, extractedId);
    }
    
    @Test
    void testCrashRecoveryScenario() {
        // Simulate a crash recovery scenario where we have virtual locations
        // that need to be resolved to real locations
        
        // Create a parent page with virtual child references
        long parentPageId = 1L;
        Page parentPage = Page.createPage(Page.PageType.BRANCH, parentPageId, PageCapacityConfig.DEFAULT, null);
        
        long childPageId1 = 2L;
        long childPageId2 = 3L;
        
        // Add virtual references
        IndexKey key1 = org.hyperkv.lsmplus.api.model.IndexKey.orderedBytes("key1".getBytes());
        IndexKey key2 = org.hyperkv.lsmplus.api.model.IndexKey.orderedBytes("key2".getBytes());
        
        parentPage.put(key1, VirtualSegmentLocation.create(childPageId1));
        parentPage.put(key2, VirtualSegmentLocation.create(childPageId2));
        
        // Simulate crash recovery: child pages are now persisted
        SegmentLocation realLocation1 = new SegmentLocation(UUID.randomUUID(), 100, 200);
        SegmentLocation realLocation2 = new SegmentLocation(UUID.randomUUID(), 300, 400);
        
        // During recovery, we should be able to resolve virtual to real locations
        assertTrue(VirtualSegmentLocation.hasVirtualChildReferences(parentPage));
        
        // After resolution, there should be no virtual references
        // (This would be done by the LevelWriteBuffer.resolveVirtualLocations method)
        parentPage.put(key1, realLocation1);
        parentPage.put(key2, realLocation2);
        
        assertFalse(VirtualSegmentLocation.hasVirtualChildReferences(parentPage));
    }
    
    @Test
    void testEdgeCasePageIds() {
        // Test edge case page IDs
        long[] edgeCases = {
            0L, // Should be invalid, but test behavior
            -0L, // Same as 0
            0xFFFFFFFFL, // Maximum 32-bit unsigned
            0x100000000L, // First value requiring 33 bits
            -0xFFFFFFFFL, // Negative of max 32-bit unsigned
            -0x100000000L // Negative of first 33-bit value
        };
        
        for (long pageId : edgeCases) {
            if (pageId == 0L) {
                // Page ID 0 is reserved, so creating a virtual location should work
                // but it might have special meaning
                SegmentLocation location = VirtualSegmentLocation.create(pageId);
                assertNotNull(location);
                assertTrue(VirtualSegmentLocation.isVirtual(location));
                
                long extracted = VirtualSegmentLocation.extractPageId(location);
                assertEquals(pageId, extracted);
            } else {
                // Normal test
                SegmentLocation location = VirtualSegmentLocation.create(pageId);
                long extracted = VirtualSegmentLocation.extractPageId(location);
                assertEquals(pageId, extracted);
            }
        }
    }
    
    @Test
    void testVirtualLocationEquality() {
        // Two virtual locations with the same page ID should be equal
        long pageId = 12345L;
        SegmentLocation location1 = VirtualSegmentLocation.create(pageId);
        SegmentLocation location2 = VirtualSegmentLocation.create(pageId);
        
        assertEquals(location1, location2);
        assertEquals(location1.hashCode(), location2.hashCode());
        
        // Different page IDs should create different locations
        SegmentLocation location3 = VirtualSegmentLocation.create(67890L);
        assertNotEquals(location1, location3);
    }
}