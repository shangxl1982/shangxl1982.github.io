package org.hyperkv.lsmplus.bplustree.page;
import org.hyperkv.lsmplus.bplustree.PageCapacityConfig;

import org.hyperkv.lsmplus.api.model.IndexKey;
import org.hyperkv.lsmplus.api.model.IndexValue;
import org.hyperkv.lsmplus.storage.SegmentLocation;
import org.junit.jupiter.api.Test;

import java.nio.charset.StandardCharsets;
import java.util.List;
import java.util.UUID;

import static org.junit.jupiter.api.Assertions.*;

class PagesIntegrationTest {

    @Test
    void testCompletePageOperations() {
        Page leafPage = Page.createPage(Page.PageType.LEAF, 1, PageCapacityConfig.DEFAULT, null);

        for (int i = 0; i < 50; i++) {
            IndexKey key = IndexKey.orderedBytes(("key-" + String.format("%02d", i)).getBytes(StandardCharsets.UTF_8));
            IndexValue value = IndexValue.normal(("value-" + i).getBytes(StandardCharsets.UTF_8));
            leafPage.put(key, value);
        }

        assertEquals(50, leafPage.getEntryCount());

        byte[] leafBytes = leafPage.toByteArray();
        Page restoredLeaf = Page.deserialize(leafBytes, PageCapacityConfig.DEFAULT);

        assertEquals(leafPage.getEntryCount(), restoredLeaf.getEntryCount());
        for (int i = 0; i < 50; i++) {
            IndexKey key = IndexKey.orderedBytes(("key-" + String.format("%02d", i)).getBytes(StandardCharsets.UTF_8));
            assertEquals(leafPage.get(key), restoredLeaf.get(key));
        }

        Page indexPage = Page.createPage(Page.PageType.BRANCH, 2, PageCapacityConfig.DEFAULT, null);

        for (int i = 0; i < 10; i++) {
            IndexKey key = IndexKey.orderedBytes(("key-" + String.format("%02d", i)).getBytes(StandardCharsets.UTF_8));
            SegmentLocation location = new SegmentLocation(UUID.randomUUID(), (i + 1) * 1000, 100);
            indexPage.put(key, location);
        }

        byte[] indexBytes = indexPage.toByteArray();
        Page restoredIndex = Page.deserialize(indexBytes, PageCapacityConfig.DEFAULT);

        assertEquals(indexPage.getEntryCount(), restoredIndex.getEntryCount());
    }

    @Test
    void testMultipleSplits() {
        Page page = Page.createPage(Page.PageType.LEAF, 1, PageCapacityConfig.DEFAULT, null);

        for (int i = 0; i < 100; i++) {
            IndexKey key = IndexKey.orderedBytes(("key-" + String.format("%03d", i)).getBytes(StandardCharsets.UTF_8));
            IndexValue value = IndexValue.normal(("value-" + i).getBytes(StandardCharsets.UTF_8));
            page.put(key, value);
        }

        Page right1 = page.split(2);
        Page right2 = right1.split(3);
        Page right3 = right2.split(4);

        assertTrue(page.getEntryCount() > 0);
        assertTrue(right1.getEntryCount() > 0);
        assertTrue(right2.getEntryCount() > 0);
        assertTrue(right3.getEntryCount() > 0);

        int totalEntries = page.getEntryCount() + right1.getEntryCount() +
                          right2.getEntryCount() + right3.getEntryCount();
        assertEquals(100, totalEntries);
    }

    @Test
    void testPageRecovery() {
        Page original = Page.createPage(Page.PageType.LEAF, 1, PageCapacityConfig.DEFAULT, null);

        for (int i = 0; i < 30; i++) {
            IndexKey key = IndexKey.orderedBytes(("key-" + String.format("%02d", i)).getBytes(StandardCharsets.UTF_8));
            IndexValue value = IndexValue.normal(("value-" + i).getBytes(StandardCharsets.UTF_8));
            original.put(key, value);
        }

        byte[] bytes = original.toByteArray();

        Page recovered = Page.deserialize(bytes, PageCapacityConfig.DEFAULT);

        assertEquals(original.getPageId(), recovered.getPageId());
        assertEquals(original.getEntryCount(), recovered.getEntryCount());
        assertEquals(original.getUsedSize(), recovered.getUsedSize());

        for (int i = 0; i < 30; i++) {
            IndexKey key = IndexKey.orderedBytes(("key-" + String.format("%02d", i)).getBytes(StandardCharsets.UTF_8));
            IndexValue originalValue = original.get(key);
            IndexValue recoveredValue = recovered.get(key);
            assertNotNull(recoveredValue);
            assertArrayEquals(originalValue.getValueData(), recoveredValue.getValueData());
        }
    }

    @Test
    void testIndexPageRecovery() {
        Page original = Page.createPage(Page.PageType.BRANCH, 1, PageCapacityConfig.DEFAULT, null);

        for (int i = 0; i < 20; i++) {
            IndexKey key = IndexKey.orderedBytes(("key-" + String.format("%02d", i)).getBytes(StandardCharsets.UTF_8));
            SegmentLocation location = new SegmentLocation(UUID.randomUUID(), (i + 1) * 1000, 100);
            original.put(key, location);
        }

        byte[] bytes = original.toByteArray();

        Page recovered = Page.deserialize(bytes, PageCapacityConfig.DEFAULT);

        assertEquals(original.getPageId(), recovered.getPageId());
        assertEquals(original.getEntryCount(), recovered.getEntryCount());

        for (int i = 0; i < 20; i++) {
            IndexKey key = IndexKey.orderedBytes(("key-" + String.format("%02d", i)).getBytes(StandardCharsets.UTF_8));
            SegmentLocation originalLocation = original.getChildLocation(key);
            SegmentLocation recoveredLocation = recovered.getChildLocation(key);
            assertEquals(originalLocation, recoveredLocation);
        }
    }

    @Test
    void testRangeQueryAfterSplit() {
        Page page = Page.createPage(Page.PageType.LEAF, 1, PageCapacityConfig.DEFAULT, null);

        for (int i = 0; i < 20; i++) {
            IndexKey key = IndexKey.orderedBytes(("key-" + String.format("%02d", i)).getBytes(StandardCharsets.UTF_8));
            IndexValue value = IndexValue.normal(("value-" + i).getBytes(StandardCharsets.UTF_8));
            page.put(key, value);
        }

        Page rightPage = page.split(2);

        IndexKey start = IndexKey.orderedBytes("key-05".getBytes(StandardCharsets.UTF_8));
        IndexKey end = IndexKey.orderedBytes("key-15".getBytes(StandardCharsets.UTF_8));

        List<IndexPair> leftResults = page.rangeQuery(start, end);
        List<IndexPair> rightResults = rightPage.rangeQuery(start, end);

        assertTrue(leftResults.size() > 0 || rightResults.size() > 0);
    }

    @Test
    void testIndexPageNavigation() {
        Page indexPage = Page.createPage(Page.PageType.BRANCH, 1, PageCapacityConfig.DEFAULT, null);

        SegmentLocation loc0 = new SegmentLocation(UUID.randomUUID(), 0, 100);
        SegmentLocation loc1 = new SegmentLocation(UUID.randomUUID(), 1000, 100);
        SegmentLocation loc2 = new SegmentLocation(UUID.randomUUID(), 2000, 100);
        SegmentLocation loc3 = new SegmentLocation(UUID.randomUUID(), 3000, 100);

        IndexKey key0 = IndexKey.orderedBytes("key-00".getBytes(StandardCharsets.UTF_8));
        IndexKey key1 = IndexKey.orderedBytes("key-10".getBytes(StandardCharsets.UTF_8));
        IndexKey key2 = IndexKey.orderedBytes("key-20".getBytes(StandardCharsets.UTF_8));
        IndexKey key3 = IndexKey.orderedBytes("key-30".getBytes(StandardCharsets.UTF_8));

        indexPage.put(key0, loc0);
        indexPage.put(key1, loc1);
        indexPage.put(key2, loc2);
        indexPage.put(key3, loc3);

        assertEquals(loc0, indexPage.getChildLocation(IndexKey.orderedBytes("key-05".getBytes(StandardCharsets.UTF_8))));
        assertEquals(loc1, indexPage.getChildLocation(IndexKey.orderedBytes("key-15".getBytes(StandardCharsets.UTF_8))));
        assertEquals(loc2, indexPage.getChildLocation(IndexKey.orderedBytes("key-25".getBytes(StandardCharsets.UTF_8))));
        assertEquals(loc3, indexPage.getChildLocation(IndexKey.orderedBytes("key-35".getBytes(StandardCharsets.UTF_8))));
    }

    @Test
    void testPageTypeValidation() {
        Page leafPage = Page.createPage(Page.PageType.LEAF, 1, PageCapacityConfig.DEFAULT, null);
        Page indexPage = Page.createPage(Page.PageType.BRANCH, 2, PageCapacityConfig.DEFAULT, null);

        IndexKey key = IndexKey.orderedBytes("key".getBytes(StandardCharsets.UTF_8));
        leafPage.put(key, IndexValue.normal("value".getBytes(StandardCharsets.UTF_8)));

        SegmentLocation location = new SegmentLocation(UUID.randomUUID(), 1000, 100);
        indexPage.put(key, location);

        byte[] leafBytes = leafPage.toByteArray();
        byte[] indexBytes = indexPage.toByteArray();

        Page restoredLeaf = Page.deserialize(leafBytes, PageCapacityConfig.DEFAULT);
        Page restoredIndex = Page.deserialize(indexBytes, PageCapacityConfig.DEFAULT);

        assertTrue(restoredLeaf.isLeaf());
        assertTrue(restoredIndex.isIndex());
    }
}
