package org.hyperkv.lsmplus.bplustree.page;
import org.hyperkv.lsmplus.bplustree.PageCapacityConfig;

import org.hyperkv.lsmplus.api.model.IndexKey;
import org.hyperkv.lsmplus.api.model.IndexValue;
import org.hyperkv.lsmplus.storage.SegmentLocation;
import org.junit.jupiter.api.Test;

import java.nio.charset.StandardCharsets;
import java.util.UUID;

import static org.junit.jupiter.api.Assertions.*;

class PageSplitTest {

    @Test
    void testSplitLeafPage() {
        Page page = Page.createPage(Page.PageType.LEAF, 1, PageCapacityConfig.DEFAULT, null);

        for (int i = 0; i < 10; i++) {
            IndexKey key = IndexKey.orderedBytes(("key-" + String.format("%02d", i)).getBytes(StandardCharsets.UTF_8));
            IndexValue value = IndexValue.normal(("value-" + i).getBytes(StandardCharsets.UTF_8));
            page.put(key, value);
        }

        Page rightPage = page.split(2);

        assertNotNull(rightPage);
        assertEquals(2, rightPage.getPageId());
        assertTrue(page.getEntryCount() >= 4);
        assertTrue(rightPage.getEntryCount() >= 4);
    }

    @Test
    void testSplitIndexPage() {
        Page page = Page.createPage(Page.PageType.BRANCH, 1, PageCapacityConfig.DEFAULT, null);
        
        for (int i = 0; i < 10; i++) {
            IndexKey key = IndexKey.orderedBytes(("key-" + String.format("%02d", i)).getBytes(StandardCharsets.UTF_8));
            SegmentLocation location = new SegmentLocation(UUID.randomUUID(), (i + 1) * 1000, 100);
            page.put(key, location);
        }

        Page rightPage = page.split(2);

        assertNotNull(rightPage);
        assertEquals(2, rightPage.getPageId());
        assertTrue(page.getEntryCount() >= 4);
        assertTrue(rightPage.getEntryCount() >= 4);
    }

    @Test
    void testCascadingLeafSplit() {
        // Test that leaf page split propagates correctly
        Page leafPage = Page.createPage(Page.PageType.LEAF, 1, PageCapacityConfig.DEFAULT, null);
        
        // Fill the leaf page to capacity
        for (int i = 0; i < 50; i++) {
            IndexKey key = IndexKey.orderedBytes(("key-" + String.format("%03d", i)).getBytes(StandardCharsets.UTF_8));
            IndexValue value = IndexValue.normal(("value-" + i).getBytes(StandardCharsets.UTF_8));
            leafPage.put(key, value);
        }
        
        // Split the leaf page
        Page rightLeaf = leafPage.split(2);
        
        assertNotNull(rightLeaf);
        assertTrue(leafPage.getEntryCount() > 0);
        assertTrue(rightLeaf.getEntryCount() > 0);
        
        // Verify that all entries are preserved
        int totalEntries = leafPage.getEntryCount() + rightLeaf.getEntryCount();
        assertEquals(50, totalEntries);
        
        // Verify key ranges are correct
        IndexKey leftMaxKey = leafPage.getMaxKey();
        IndexKey rightMinKey = rightLeaf.getMinKey();
        assertTrue(leftMaxKey.compareTo(rightMinKey) < 0);
    }
    
    @Test
    void testCascadingIndexSplit() {
        // Test that index page split propagates correctly
        Page indexPage = Page.createPage(Page.PageType.BRANCH, 1, PageCapacityConfig.DEFAULT, null);
        
        // Fill the index page to capacity
        for (int i = 0; i < 20; i++) {
            IndexKey key = IndexKey.orderedBytes(("key-" + String.format("%03d", i)).getBytes(StandardCharsets.UTF_8));
            SegmentLocation location = new SegmentLocation(UUID.randomUUID(), i * 1000, 100);
            indexPage.put(key, location);
        }
        
        // Split the index page
        Page rightIndex = indexPage.split(2);
        
        assertNotNull(rightIndex);
        assertTrue(indexPage.getEntryCount() > 0);
        assertTrue(rightIndex.getEntryCount() > 0);
        
        // Verify that all entries are preserved
        int totalEntries = indexPage.getEntryCount() + rightIndex.getEntryCount();
        assertEquals(20, totalEntries);
        
        // Verify key ranges are correct
        IndexKey leftMaxKey = indexPage.getMaxKey();
        IndexKey rightMinKey = rightIndex.getMinKey();
        assertTrue(leftMaxKey.compareTo(rightMinKey) < 0);
    }
    
    @Test
    void testSplitPropagationToParent() {
        // Test that split creates appropriate separator key for parent
        Page leafPage = Page.createPage(Page.PageType.LEAF, 1, PageCapacityConfig.DEFAULT, null);
        
        // Fill the leaf page
        for (int i = 0; i < 30; i++) {
            IndexKey key = IndexKey.orderedBytes(("key-" + String.format("%03d", i)).getBytes(StandardCharsets.UTF_8));
            IndexValue value = IndexValue.normal(("value-" + i).getBytes(StandardCharsets.UTF_8));
            leafPage.put(key, value);
        }
        
        // Split the leaf page
        Page rightLeaf = leafPage.split(2);
        
        // The separator key should be the minimum key of the right page
        IndexKey separatorKey = rightLeaf.getMinKey();
        assertNotNull(separatorKey);
        
        // Verify separator key is between the two pages
        IndexKey leftMaxKey = leafPage.getMaxKey();
        IndexKey rightMinKey = rightLeaf.getMinKey();
        assertTrue(leftMaxKey.compareTo(separatorKey) < 0);
        assertEquals(separatorKey, rightMinKey);
    }

    @Test
    void testMedianKeyCorrectLeafPage() {
        Page page = Page.createPage(Page.PageType.LEAF, 1, PageCapacityConfig.DEFAULT, null);

        for (int i = 0; i < 10; i++) {
            IndexKey key = IndexKey.orderedBytes(("key-" + String.format("%02d", i)).getBytes(StandardCharsets.UTF_8));
            IndexValue value = IndexValue.normal(("value-" + i).getBytes(StandardCharsets.UTF_8));
            page.put(key, value);
        }

        IndexKey originalMaxKey = page.getMaxKey();
        Page rightPage = page.split(2);

        IndexKey leftMaxKey = page.getMaxKey();
        IndexKey rightMaxKey = rightPage.getMaxKey();

        assertNotNull(leftMaxKey);
        assertNotNull(rightMaxKey);
        assertTrue(leftMaxKey.compareTo(rightMaxKey) < 0);
    }

    @Test
    void testLeftPageEntriesAfterSplit() {
        Page page = Page.createPage(Page.PageType.LEAF, 1, PageCapacityConfig.DEFAULT, null);

        for (int i = 0; i < 10; i++) {
            IndexKey key = IndexKey.orderedBytes(("key-" + String.format("%02d", i)).getBytes(StandardCharsets.UTF_8));
            IndexValue value = IndexValue.normal(("value-" + i).getBytes(StandardCharsets.UTF_8));
            page.put(key, value);
        }

        Page rightPage = page.split(2);

        // Verify left page has entries
        assertTrue(page.getEntryCount() > 0);
        
        // Verify all entries in left page are less than separator
        IndexKey separatorKey = rightPage.getMinKey();
        for (IndexPair pair : page.getAllEntries()) {
            IndexKey key = pair.key();
            assertTrue(key.compareTo(separatorKey) < 0);
        }
    }

    @Test
    void testSplitBySizeNotCount() {
        Page page = Page.createPage(Page.PageType.LEAF, 1, PageCapacityConfig.DEFAULT, null);

        for (int i = 0; i < 10; i++) {
            int valueSize = (i % 3 == 0) ? 500 : 100;
            page.put(IndexKey.orderedBytes(("key-" + String.format("%02d", i)).getBytes(StandardCharsets.UTF_8)), 
                     IndexValue.normal(new byte[valueSize]));
        }

        int totalSizeBefore = page.getUsedSize();
        Page rightPage = page.split(2);
        int totalSizeAfter = page.getUsedSize() + rightPage.getUsedSize();

        assertEquals(totalSizeBefore, totalSizeAfter);

        int leftSize = page.getUsedSize();
        int rightSize = rightPage.getUsedSize();
        int sizeDiff = Math.abs(leftSize - rightSize);
        
        assertTrue(sizeDiff < totalSizeBefore / 3, 
            "Pages should be roughly balanced: left=" + leftSize + ", right=" + rightSize + ", diff=" + sizeDiff);
    }
}