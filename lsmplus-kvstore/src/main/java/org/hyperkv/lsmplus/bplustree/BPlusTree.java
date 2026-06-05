package org.hyperkv.lsmplus.bplustree;

import org.hyperkv.lsmplus.api.model.IndexKey;
import org.hyperkv.lsmplus.api.model.IndexValue;
import org.hyperkv.lsmplus.api.model.RangeQueryOptions;
import org.hyperkv.lsmplus.api.model.RangeQueryResult;
import org.hyperkv.lsmplus.bplustree.page.Page;
import org.hyperkv.lsmplus.core.TreeMetadataManager;
import org.hyperkv.lsmplus.gc.MNSTracker;
import org.hyperkv.lsmplus.storage.SegmentLocation;

import java.util.Iterator;
import java.util.List;
import java.util.Map;

public class BPlusTree {

    public static final int DEFAULT_LEAF_PAGE_SIZE = 8 * 1024;
    public static final int DEFAULT_INDEX_PAGE_SIZE = 64 * 1024;

    private final ReaderTree readerTree;
    private final PersistingTree persistingTree;

    public BPlusTree(PageManager pageManager, PageCapacityConfig capacityConfig, MNSTracker mnsTracker) {
        if (pageManager == null) {
            throw new IllegalArgumentException("pageManager must not be null");
        }
        if (capacityConfig == null) {
            throw new IllegalArgumentException("capacityConfig must not be null");
        }
        this.readerTree = new ReaderTree(pageManager);
        this.persistingTree = new PersistingTree(pageManager, capacityConfig, mnsTracker);
    }

    public BPlusTree(PageManager pageManager, int leafPageMaxSize, int indexPageMaxSize) {
        this(pageManager, PageCapacityConfig.sizeBased(leafPageMaxSize, indexPageMaxSize));
    }

    public BPlusTree(PageManager pageManager, PageCapacityConfig capacityConfig) {
        this(pageManager, capacityConfig, null);
    }

    public BPlusTree(PageManager pageManager) {
        this(pageManager, pageManager.getCapacityConfig(), null);
    }

    public IndexValue search(IndexKey key) {
        return readerTree.search(key);
    }

    public List<Map.Entry<IndexKey, IndexValue>> rangeQuery(IndexKey start, IndexKey end) {
        return readerTree.rangeQuery(start, end);
    }

    public RangeQueryResult rangeQuery(RangeQueryOptions options) {
        return readerTree.rangeQuery(options);
    }

    public Iterator<Map.Entry<IndexKey, IndexValue>> rangeIterator(RangeQueryOptions options) {
        return readerTree.rangeIterator(options);
    }

    public long getVersion() {
        return persistingTree.getVersion();
    }

    public SegmentLocation getWriterRootLocation() {
        return persistingTree.getWriterRootLocation();
    }

    public int getHeight() {
        return persistingTree.getHeight();
    }

    public int getLeafPageMaxSize() {
        return persistingTree.getLeafPageMaxSize();
    }

    public int getIndexPageMaxSize() {
        return persistingTree.getIndexPageMaxSize();
    }

    public PageCapacityConfig getCapacityConfig() {
        return persistingTree.getCapacityConfig();
    }

    public PageManager getPageManager() {
        return persistingTree.getPageManager();
    }

    public PageIdManager getPageIdManager() {
        return persistingTree.getPageIdManager();
    }

    public long getMaxLeafPageId() {
        return persistingTree.getMaxLeafPageId();
    }

    public long getMinIndexPageId() {
        return persistingTree.getMinIndexPageId();
    }

    public long getLeafPageCount() {
        return persistingTree.getLeafPageCount();
    }

    public long getIndexPageCount() {
        return persistingTree.getIndexPageCount();
    }

    public long getTotalTreeSize() {
        return persistingTree.getTotalTreeSize();
    }

    public void setRootLocation(SegmentLocation location) {
        persistingTree.setRootLocation(location);
    }

    public void setHeight(int height) {
        persistingTree.setHeight(height);
    }

    public void setVersion(long version) {
        persistingTree.setVersion(version);
    }

    void incrementVersion() {
        persistingTree.incrementVersion();
    }

    void restorePageIds(long maxLeafPageId, long minIndexPageId) {
        persistingTree.restorePageIds(maxLeafPageId, minIndexPageId);
    }

    public boolean isEmpty() {
        return readerTree.isEmpty();
    }

    public int getTotalEntryCount() {
        return persistingTree.getTotalEntryCount();
    }

    void incrementEntryCount(int delta) {
        persistingTree.incrementEntryCount(delta);
    }

    void setTotalEntryCount(int count) {
        persistingTree.setTotalEntryCount(count);
    }

    @Override
    public String toString() {
        return "BPlusTree{" + persistingTree.toString() + "}";
    }

    public void startFrom(TreeMetadataManager.TreeVersionInfo treeInfo) {
        persistingTree.startFrom(treeInfo, readerTree);
    }

    public Page getWriterRoot() {
        return persistingTree.getWriterRoot();
    }

    public void setWriterRoot(Page page) {
        persistingTree.setWriterRoot(page);
    }

    public void promoteRoot() {
        persistingTree.promoteRoot(readerTree);
    }

    public MNSTracker getMnsTracker() {
        return persistingTree.getMnsTracker();
    }

    public ReaderTree getReaderTree() {
        return readerTree;
    }

    public PersistingTree getPersistingTree() {
        return persistingTree;
    }
}
