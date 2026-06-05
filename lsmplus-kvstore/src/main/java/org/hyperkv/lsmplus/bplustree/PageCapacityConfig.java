package org.hyperkv.lsmplus.bplustree;

import org.hyperkv.lsmplus.bplustree.page.Page;

public final class PageCapacityConfig {

    public static final PageCapacityConfig DEFAULT = new PageCapacityConfig(
        BPlusTree.DEFAULT_LEAF_PAGE_SIZE,
        BPlusTree.DEFAULT_INDEX_PAGE_SIZE,
        Integer.MAX_VALUE,
        Integer.MAX_VALUE
    );

    private final int leafMaxSize;
    private final int indexMaxSize;
    private final int leafMaxEntries;
    private final int indexMaxEntries;

    public PageCapacityConfig(int leafMaxSize, int indexMaxSize,
                              int leafMaxEntries, int indexMaxEntries) {
        this.leafMaxSize = leafMaxSize;
        this.indexMaxSize = indexMaxSize;
        this.leafMaxEntries = leafMaxEntries;
        this.indexMaxEntries = indexMaxEntries;
    }

    public static PageCapacityConfig sizeBased(int leafMaxSize, int indexMaxSize) {
        return new PageCapacityConfig(leafMaxSize, indexMaxSize,
            Integer.MAX_VALUE, Integer.MAX_VALUE);
    }

    public static PageCapacityConfig entryCountBased(int leafMaxEntries, int indexMaxEntries) {
        return new PageCapacityConfig(Integer.MAX_VALUE, Integer.MAX_VALUE,
            leafMaxEntries, indexMaxEntries);
    }

    public int getMaxSize(Page.PageType pageType) {
        return pageType == Page.PageType.LEAF ? leafMaxSize : indexMaxSize;
    }

    public int getMaxEntries(Page.PageType pageType) {
        return pageType == Page.PageType.LEAF ? leafMaxEntries : indexMaxEntries;
    }

    @Override
    public String toString() {
        return "PageCapacityConfig{" +
            ", leafMaxSize=" + leafMaxSize +
            ", indexMaxSize=" + indexMaxSize +
            ", leafMaxEntries=" + leafMaxEntries +
            ", indexMaxEntries=" + indexMaxEntries +
            '}';
    }
}
