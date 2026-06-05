package org.hyperkv.lsmplus.bplustree.page;

import org.hyperkv.lsmplus.api.model.IndexKey;
import org.hyperkv.lsmplus.api.model.IndexValue;
import org.hyperkv.lsmplus.bplustree.BPlusTree;
import org.hyperkv.lsmplus.bplustree.PageCapacityConfig;
import org.junit.jupiter.api.Test;

import java.util.ArrayList;
import java.util.List;

import static org.junit.jupiter.api.Assertions.*;

class PageSplitMergeStrategyTest {

    @Test
    void testSplitByEntryCount() {
        PageCapacityConfig config = PageCapacityConfig.entryCountBased(10, 10);
        Page page = new Page(Page.PageType.LEAF, 1, config, null);
        
        for (int i = 0; i < 10; i++) {
            page.put(IndexKey.orderedBytes(("key" + i).getBytes()), 
                    IndexValue.normal(("value" + i).getBytes()));
        }
        
        assertEquals(10, page.getEntryCount());
        assertEquals(10, page.getMaxEntries());
        
        Page rightPage = page.split(2);
        
        assertEquals(5, page.getEntryCount());
        assertEquals(5, rightPage.getEntryCount());
        
        System.out.println("✓ Split by entry count works correctly");
        System.out.println("  Left page entries: " + page.getEntryCount());
        System.out.println("  Right page entries: " + rightPage.getEntryCount());
    }

    @Test
    void testSplitBySize() {
        PageCapacityConfig config = PageCapacityConfig.sizeBased(4096, 4096);
        Page page = new Page(Page.PageType.LEAF, 1, config, null);
        
        for (int i = 0; i < 100; i++) {
            page.put(IndexKey.orderedBytes(("key" + String.format("%03d", i)).getBytes()), 
                    IndexValue.normal(("value" + i).getBytes()));
        }
        
        assertTrue(page.getUsedSize() > 0);
        assertEquals(Integer.MAX_VALUE, page.getMaxEntries());
        
        int originalSize = page.getUsedSize();
        Page rightPage = page.split(2);
        
        assertTrue(page.getUsedSize() < originalSize);
        assertTrue(rightPage.getUsedSize() < originalSize);
        assertTrue(Math.abs(page.getUsedSize() - rightPage.getUsedSize()) < originalSize / 4);
        
        System.out.println("✓ Split by size works correctly");
        System.out.println("  Left page size: " + page.getUsedSize() + " bytes");
        System.out.println("  Right page size: " + rightPage.getUsedSize() + " bytes");
        System.out.println("  Size difference: " + Math.abs(page.getUsedSize() - rightPage.getUsedSize()) + " bytes");
    }

    @Test
    void testIsUnderfullByEntryCount() {
        PageCapacityConfig config = PageCapacityConfig.entryCountBased(100, 100);
        Page page = new Page(Page.PageType.LEAF, 1, config, null);
        
        for (int i = 0; i < 20; i++) {
            page.put(IndexKey.orderedBytes(("key" + i).getBytes()), 
                    IndexValue.normal(("value" + i).getBytes()));
        }
        
        assertTrue(page.isUnderfull());
        
        System.out.println("✓ isUnderfull by entry count works correctly");
        System.out.println("  Entry count: " + page.getEntryCount());
        System.out.println("  Is underfull: " + page.isUnderfull());
    }

    @Test
    void testIsUnderfullBySize() {
        PageCapacityConfig config = PageCapacityConfig.sizeBased(4096, 4096);
        Page page = new Page(Page.PageType.LEAF, 1, config, null);
        
        for (int i = 0; i < 10; i++) {
            page.put(IndexKey.orderedBytes(("key" + i).getBytes()), 
                    IndexValue.normal(("value" + i).getBytes()));
        }
        
        assertTrue(page.isUnderfull());
        
        System.out.println("✓ isUnderfull by size works correctly");
        System.out.println("  Used size: " + page.getUsedSize() + " bytes");
        System.out.println("  Is underfull: " + page.isUnderfull());
    }

    @Test
    void testMergeByEntryCount() {
        PageCapacityConfig config = PageCapacityConfig.entryCountBased(100, 100);
        Page leftPage = new Page(Page.PageType.LEAF, 1, config, null);
        Page rightPage = new Page(Page.PageType.LEAF, 2, config, null);
        
        for (int i = 0; i < 30; i++) {
            leftPage.put(IndexKey.orderedBytes(("key" + i).getBytes()), 
                        IndexValue.normal(("value" + i).getBytes()));
        }
        
        for (int i = 30; i < 50; i++) {
            rightPage.put(IndexKey.orderedBytes(("key" + i).getBytes()), 
                         IndexValue.normal(("value" + i).getBytes()));
        }
        
        assertEquals(30, leftPage.getEntryCount());
        assertEquals(20, rightPage.getEntryCount());
        
        leftPage.merge(rightPage);
        
        assertEquals(50, leftPage.getEntryCount());
        
        System.out.println("✓ Merge by entry count works correctly");
        System.out.println("  Merged entry count: " + leftPage.getEntryCount());
    }
}
