package org.hyperkv.lsmplus.bplustree;

import org.hyperkv.lsmplus.bplustree.page.Page;
import org.hyperkv.lsmplus.core.TreeMetadataManager;
import org.hyperkv.lsmplus.gc.MNSTracker;
import org.hyperkv.lsmplus.storage.SegmentLocation;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

public class PersistingTree {

    private static final Logger log = LoggerFactory.getLogger(PersistingTree.class);

    private final PageManager pageManager;
    private final PageIdManager pageIdManager;
    private final PageCapacityConfig capacityConfig;
    private final MNSTracker mnsTracker;

    private volatile long currentVersion;
    private volatile Page writerRoot;
    private volatile SegmentLocation rootLocation;
    private volatile int height;
    private volatile int totalEntryCount;

    public PersistingTree(PageManager pageManager, PageCapacityConfig capacityConfig, MNSTracker mnsTracker) {
        if (pageManager == null) {
            throw new IllegalArgumentException("pageManager must not be null");
        }
        if (capacityConfig == null) {
            throw new IllegalArgumentException("capacityConfig must not be null");
        }
        this.pageManager = pageManager;
        this.capacityConfig = capacityConfig;
        this.pageIdManager = new PageIdManager();
        this.mnsTracker = mnsTracker;
        this.currentVersion = 0;
        this.writerRoot = null;
        this.height = 0;
    }

    public Page getWriterRoot() {
        return writerRoot;
    }

    public void setWriterRoot(Page page) {
        writerRoot = page;
    }

    public SegmentLocation getWriterRootLocation() {
        if (writerRoot == null) {
            return null;
        }
        return writerRoot.getLocation();
    }

    public void setRootLocation(SegmentLocation location) {
        this.rootLocation = location;
        if (writerRoot != null) {
            writerRoot.setLocation(location);
        }
    }

    public SegmentLocation getRootLocation() {
        return rootLocation;
    }

    public void setHeight(int height) {
        this.height = height;
    }

    public int getHeight() {
        return height;
    }

    public void setVersion(long version) {
        this.currentVersion = version;
    }

    public long getVersion() {
        return currentVersion;
    }

    void incrementVersion() {
        this.currentVersion++;
    }

    void restorePageIds(long maxLeafPageId, long minIndexPageId) {
        pageIdManager.updateSequences(maxLeafPageId, minIndexPageId);
    }

    public void incrementEntryCount(int delta) {
        this.totalEntryCount += delta;
    }

    public void setTotalEntryCount(int count) {
        this.totalEntryCount = count;
    }

    public int getTotalEntryCount() {
        return totalEntryCount;
    }

    public void startFrom(TreeMetadataManager.TreeVersionInfo treeInfo, ReaderTree readerTree) {
        setRootLocation(treeInfo.getRootLocation());
        setHeight(treeInfo.getHeight());
        setVersion(treeInfo.getVersion());
        setTotalEntryCount((int) treeInfo.getTotalEntries());
        
        Page rootPage = pageManager.getPage(treeInfo.getRootLocation(), false);
        readerTree.setReaderRoot(rootPage);
        writerRoot = new Page(rootPage);
        
        pageIdManager.updateSequences(treeInfo.getNextLeafPageId() - 1, treeInfo.getNextIndexPageId() - 1);
        log.info("Restored tree from metadata: version={}, height={}, totalEntries={}, nextLeafPageId={}, nextIndexPageId={}", 
            treeInfo.getVersion(), treeInfo.getHeight(), treeInfo.getTotalEntries(),
            treeInfo.getNextLeafPageId(), treeInfo.getNextIndexPageId());
    }

    public void promoteRoot(ReaderTree readerTree) {
        readerTree.setReaderRoot(writerRoot == null ? null : new Page(writerRoot));
    }

    public PageManager getPageManager() {
        return pageManager;
    }

    public PageIdManager getPageIdManager() {
        return pageIdManager;
    }

    public PageCapacityConfig getCapacityConfig() {
        return capacityConfig;
    }

    public int getLeafPageMaxSize() {
        return capacityConfig.getMaxSize(Page.PageType.LEAF);
    }

    public int getIndexPageMaxSize() {
        return capacityConfig.getMaxSize(Page.PageType.ROOT);
    }

    public long getMaxLeafPageId() {
        long[] state = pageIdManager.getStateForPersistence();
        return state[0];
    }

    public long getMinIndexPageId() {
        long[] state = pageIdManager.getStateForPersistence();
        return state[1];
    }

    public long getLeafPageCount() {
        return getMaxLeafPageId();
    }

    public long getIndexPageCount() {
        long minIndexPageId = getMinIndexPageId();
        if (minIndexPageId >= 0) {
            return 0;
        }
        return Long.MIN_VALUE - minIndexPageId + 1;
    }

    public long getTotalTreeSize() {
        if (rootLocation == null) {
            return 0;
        }
        return rootLocation.getLength();
    }

    public MNSTracker getMnsTracker() {
        return mnsTracker;
    }

    @Override
    public String toString() {
        return "PersistingTree{version=" + currentVersion +
                ", height=" + height +
                ", maxLeafPageId=" + getMaxLeafPageId() +
                ", minIndexPageId=" + getMinIndexPageId() +
                ", rootLocation=" + rootLocation + "}";
    }
}
