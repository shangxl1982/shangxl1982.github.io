package org.hyperkv.lsmplus.bplustree;

import org.hyperkv.lsmplus.api.model.IndexKey;
import org.hyperkv.lsmplus.api.model.IndexValue;
import org.hyperkv.lsmplus.bplustree.page.IndexPair;
import org.hyperkv.lsmplus.bplustree.page.Page;
import org.hyperkv.lsmplus.core.TreeMetadataManager;
import org.hyperkv.lsmplus.exception.ErrorCode;
import org.hyperkv.lsmplus.exception.Exceptions;
import org.hyperkv.lsmplus.exception.KVStoreException;
import org.hyperkv.lsmplus.exception.KVStoreRuntimeException;
import org.hyperkv.lsmplus.gc.OccupancyManager;
import org.hyperkv.lsmplus.journal.JournalReplayPoint;
import org.hyperkv.lsmplus.monitoring.MetricsRegistry;
import org.hyperkv.lsmplus.monitoring.PerformanceCounter;
import org.hyperkv.lsmplus.storage.SegmentLocation;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

import java.util.*;
import java.util.concurrent.CompletableFuture;

/**
 * Dumps sorted entries into the B+Tree structure.
 *
 * <p>The TreeDumper is responsible for:
 * <ul>
 *   <li>Building a new B+Tree when the tree is empty</li>
 *   <li>Updating an existing B+Tree with new entries</li>
 *   <li>Handling page splits with proper parent propagation</li>
 *   <li>Managing the level-based write buffer for ordered persistence</li>
 * </ul>
 *
 * <h2>Page ID Management</h2>
 * <p>Page IDs are managed by PageIdManager:
 * <ul>
 *   <li>Positive IDs (1, 2, 3, ...): Leaf pages</li>
 *   <li>Negative IDs (Long.MIN_VALUE, Long.MIN_VALUE+1, ...): Index pages</li>
 *   <li>0: Invalid/null page ID</li>
 * </ul>
 *
 * <h2>Virtual Locations for Pending Child References</h2>
 * <p>When a child page splits, the parent uses a virtual location instead of a real one:
 * <ul>
 *   <li>Virtual location: chunkId=0, offset=childPageId, length=0</li>
 *   <li>Stored directly in the parent page</li>
 *   <li>After children are persisted, virtual locations are resolved to real locations</li>
 *   <li>Parent can only be flushed when all virtual locations are resolved</li>
 * </ul>
 */
public class TreeDumper {

    private static final Logger log = LoggerFactory.getLogger(TreeDumper.class);

    private final BPlusTree tree;
    private final PageManager pageManager;
    private final LevelWriteBuffer writeBuffer;
    private final OccupancyManager occupancyManager;
    private final DeltaQueue deltaQueue;
    private final PageLocationMapper pageLocationMapper;
    private DumpMetrics currentDumpMetrics;

    private PerformanceCounter insertEntryCounter;
    private PerformanceCounter flushCounter;

    public TreeDumper(BPlusTree tree, PageManager pageManager, OccupancyManager occupancyManager) {
        if (tree == null || pageManager == null) {
            throw Exceptions.invalidArgument("tree and pageManager must not be null");
        }
        this.tree = tree;
        this.pageManager = pageManager;
        this.pageLocationMapper = new PageLocationMapper();
        this.writeBuffer = new LevelWriteBuffer();
        this.occupancyManager = occupancyManager;
        this.deltaQueue = new DeltaQueue();
        this.insertEntryCounter = MetricsRegistry.getCounter("tree_dump_insert_entry_count", "Number of entries inserted during tree dump");
        this.flushCounter = MetricsRegistry.getCounter("tree_dump_flush_count", "Number of times flush is called during tree dump");
    }

    public TreeDumper(BPlusTree tree, PageManager pageManager) {
        this(tree, pageManager, null);
    }

    private void trackPageWrite(SegmentLocation location) {
        if (occupancyManager == null || location == null) {
            return;
        }
        occupancyManager.recordPageWrite(location.getChunkId(), location.getLength());
    }

    private void trackPageDecommission(SegmentLocation location) {
        if (occupancyManager == null || location == null) {
            return;
        }
        occupancyManager.recordPageDecommission(location.getChunkId(), location.getLength());
        if (currentDumpMetrics != null) {
            currentDumpMetrics.incrementPagesDecommissioned();
        }
    }

    private Page createPage(Page.PageType pageType, long pageId) {
        if (currentDumpMetrics != null) {
            if (pageType == Page.PageType.LEAF) {
                currentDumpMetrics.incrementLeafPagesCreated();
            } else {
                currentDumpMetrics.incrementIndexPagesCreated();
            }
        }
        return Page.createPage(pageType, pageId, tree.getCapacityConfig(), null);
    }

    /**
     * Dumps sorted entries into the B+Tree.
     *
     * <p>The entries list must be sorted by key in ascending order.
     * This is typically achieved by merging multiple sealed MemoryTables.
     *
     * @param sortedEntries sorted list of key-value entries to dump
     * @param replayPoint   the journal replay point for this dump (can be null)
     * @return
     */
    public TreeMetadataManager.TreeVersionInfo dump(List<Map.Entry<IndexKey, IndexValue>> sortedEntries, JournalReplayPoint replayPoint) throws KVStoreException {
        if (sortedEntries == null || sortedEntries.isEmpty()) {
            log.debug("Dump skipped: no entries to dump");
            return null;
        }

        deltaQueue.clear();
        writeBuffer.clearAll();
        pageLocationMapper.clear();

        log.debug("Cleared write buffer and delta queue for new dump");

        currentDumpMetrics = new DumpMetrics();
        currentDumpMetrics.start();

        log.info("Starting dump of {} sorted entries, replayPoint={}", sortedEntries.size(), replayPoint);

        if (occupancyManager != null) {
            occupancyManager.startDump(tree.getVersion() + 1);
        }

        log.debug("Processing {} entries", sortedEntries.size());

        buildTree(sortedEntries);

        tree.incrementVersion();

        if (occupancyManager != null) {
            try {
                occupancyManager.finishDump();
            } catch (Exception e) {
                log.error("Failed to finish occupancy tracking", e);
            }
        }

        currentDumpMetrics.finish();
        log.info(currentDumpMetrics.toLogMessage());

        long mns = tree.getMnsTracker() == null ? 0l : tree.getMnsTracker().getCurrentMNS();

        long[] pageIdState = tree.getPageIdManager().getStateForPersistence();
        long nextLeafPageId = pageIdState[0] + 1;
        long nextIndexPageId = pageIdState[1] + 1;

        Page writerRoot = tree.getWriterRoot();
        SegmentLocation rootLocation = (writerRoot == null) ? null : writerRoot.getLocation();

        return new TreeMetadataManager.TreeVersionInfo(
                tree.getVersion(),
                rootLocation,
                replayPoint,
                mns,
                tree.getLeafPageCount(),
                tree.getIndexPageCount(),
                tree.getTotalEntryCount(),
                tree.getHeight(),
                tree.getTotalTreeSize(),
                nextLeafPageId,
                nextIndexPageId
        );
    }

    private void buildTree(List<Map.Entry<IndexKey, IndexValue>> entries) throws KVStoreException {
        if (tree.getWriterRoot() == null) {
            log.debug("Building new tree with {} entries", entries.size());
            buildNewTree(entries);
        } else {
            log.debug("Updating existing tree with {} new entries", entries.size());
            updateExistingTree(entries);
        }
    }

    public void promoteRoot() {
        tree.promoteRoot();
    }

    /**
     * Builds a new B+Tree from scratch.
     */
    private void buildNewTree(List<Map.Entry<IndexKey, IndexValue>> entries) throws KVStoreException {
        PageIdManager pageIdManager = tree.getPageIdManager();

        Page currentLeaf = createPage(Page.PageType.LEAF, pageIdManager.allocateLeafPageId());

        List<Page> leafPages = new ArrayList<>();
        int entryCount = 0;

        for (Map.Entry<IndexKey, IndexValue> entry : entries) {
            IndexKey key = entry.getKey();
            IndexValue value = entry.getValue();

            if (value.isTombstone()) {
                continue;
            }

            if (!currentLeaf.hasSpaceForEntry(IndexPair.of(key, value))) {
                leafPages.add(currentLeaf);

                currentLeaf = createPage(Page.PageType.LEAF, pageIdManager.allocateLeafPageId());
            }

            currentLeaf.put(key, value);
            entryCount++;
            if (currentDumpMetrics != null) {
                currentDumpMetrics.incrementEntriesInserted();
            }
        }

        if (currentLeaf.getEntryCount() > 0) {
            leafPages.add(currentLeaf);
        }

        if (leafPages.isEmpty()) {
            log.debug("No leaf pages created (all entries were tombstones)");
            return;
        }

        tree.setTotalEntryCount(entryCount);
        log.debug("Created {} leaf pages with {} entries", leafPages.size(), entryCount);

        List<SegmentLocation> leafLocations = savePagesWithTracking(leafPages);

        if (leafPages.size() == 1) {
            log.debug("Single leaf page, creating root index page");
            Page rootPage = createPage(Page.PageType.ROOT, pageIdManager.allocateIndexPageId());
            IndexKey minKey = leafPages.get(0).getMinKey();
            rootPage.put(minKey, leafLocations.get(0));
            saveRootPage(rootPage, 2);
        } else {
            log.debug("Multiple leaf pages, building index levels");
            buildIndexLevels(leafPages, leafLocations);
        }
    }

    private void buildIndexLevels(List<Page> leafPages, List<SegmentLocation> leafLocations) throws KVStoreException {
        PageIdManager pageIdManager = tree.getPageIdManager();

        List<SegmentLocation> currentLevelLocations = leafLocations;
        List<Page> currentLevelPages = leafPages;
        int height = 1;

        while (currentLevelLocations.size() > 1) {
            List<Page> nextLevelPages = new ArrayList<>();

            Page currentIndexPage = createPage(Page.PageType.BRANCH, pageIdManager.allocateIndexPageId());

            for (int i = 0; i < currentLevelLocations.size(); i++) {
                SegmentLocation childLocation = currentLevelLocations.get(i);
                IndexKey minKey = currentLevelPages.get(i).getMinKey();

                if (!currentIndexPage.hasSpaceForEntry(IndexPair.of(minKey, childLocation))) {
                    nextLevelPages.add(currentIndexPage);

                    currentIndexPage = createPage(Page.PageType.BRANCH, pageIdManager.allocateIndexPageId());
                }

                currentIndexPage.put(minKey, childLocation);
            }

            if (currentIndexPage.getEntryCount() > 0) {
                nextLevelPages.add(currentIndexPage);
            }

            List<SegmentLocation> nextLevelLocations = savePagesWithTracking(nextLevelPages);

            currentLevelLocations = nextLevelLocations;
            currentLevelPages = nextLevelPages;
            height++;
        }

        if (!currentLevelLocations.isEmpty()) {
            var finalPage = currentLevelPages.get(0);
            Page rootPage;
            if (finalPage.isBranch()) {
                rootPage = Page.createPage(Page.PageType.ROOT, finalPage.getPageId(), tree.getCapacityConfig(), finalPage.getAllEntries());
                rootPage.setLifecycle(Page.PageLifecycle.DIRTY);
            } else {
                rootPage = finalPage;
            }
            saveRootPage(rootPage, height);
            log.debug("Built index level {} with {} pages, final height={}",
                    height, currentLevelLocations.size(), height);
        }
    }

    /**
     * Updates an existing B+Tree with new entries.
     *
     * <p>Uses PALM-style delta-based propagation:
     * <ol>
     *   <li>Phase 1: Process all entries and create parent change deltas</li>
     *   <li>Phase 2: Process deltas level-by-level (bottom-up)</li>
     *   <li>Phase 3: Flush all pages</li>
     * </ol>
     */
    private void updateExistingTree(List<Map.Entry<IndexKey, IndexValue>> entries) throws KVStoreException {
        int insertedCount = 0;
        int deletedCount = 0;

        locationPairs.clear();

        // Phase 1: Process all entries (creates deltas)
        var iterator = entries.iterator();
        var entry = iterator.next();
        IndexKey key = entry.getKey();
        IndexValue value = entry.getValue();
        List<PathEntry> path = null, lastPath = null;

        while (true) {
            path = findLeafPageWithPath(key);

            var leafPage = path.getLast().page;
            int pageLevel = path.getLast().level;
            var parent = path.get(path.size() - 2);
            int parentLevel = parent.level;
            IndexKey oldMapping = parent.page().getEntryAt(parent.indexNum).key();

            IndexKey rightSiblingKey = getRightSiblingKey(path);

            // queue deltas
            List<ChangeDelta> changeDeltas = new ArrayList<>();
            do {
                if (value.isTombstone()) {
                    changeDeltas.add(ChangeDelta.delete(key));
                } else {
                    changeDeltas.add(ChangeDelta.put(key, IndexPair.of(key, value)));
                }
                if (iterator.hasNext()) {
                    entry = iterator.next();
                    key = entry.getKey();
                    value = entry.getValue();
                } else {
                    break;
                }

            } while (rightSiblingKey == null || key.compareTo(rightSiblingKey) < 0);

            leafPage.applyAll(changeDeltas);

            // postpone split, merge
            if (leafPage.getLifecycle() == Page.PageLifecycle.DIRTY) {
                for (int i = 1; i < path.size(); i++) {
                    recordParentLocation(path.get(i).page, path.get(i - 1).page.getLocation());
                }

                if (leafPage.isEmpty()) {
                    deltaQueue.addDelta(parent.page().getLocation(), ChangeDelta.delete(oldMapping), parentLevel);
                } else {
                    deltaQueue.addDelta(parent.page().getLocation(), ChangeDelta.update(oldMapping, IndexPair.of(leafPage.getMinKey(), leafPage.getLocation())), parentLevel);
                }
                addToWriteBuffer(pageLevel, leafPage);

                checkMergeSplitForLastPath(lastPath);
                lastPath = path;
            }

            flushCompletedPages(lastPath.getLast().page().getMinKey());

            if (!iterator.hasNext()) {
                break;
            }
        }

        checkMergeSplitForLastPath(path);

        flushAllPageInLevel(0);

        // Phase 2: Process all parent change deltas (PALM-style bottom-up propagation)
        processParentDeltas();

        tree.incrementEntryCount(insertedCount - deletedCount);
        log.debug("Updated tree: +{} inserts, -{} deletes, net={}", insertedCount, deletedCount, insertedCount - deletedCount);
    }

    private void checkMergeSplitForLastPath(List<PathEntry> path) {
        if (path == null || path.isEmpty()) {
            return;
        }

        PathEntry leafEntry = path.getLast();
        Page leafPage = leafEntry.page;

        if (path.size() < 2) {
            return;
        }

        PathEntry parentEntry = path.get(path.size() - 2);
        Page parentPage = parentEntry.page;

        IndexKey origMappingKey = parentEntry.page.getEntryAt(parentEntry.indexNum).key();

        try {
            checkMergeSplitForPage(leafPage, origMappingKey, leafEntry.level, parentPage, null);
        } catch (KVStoreException e) {
            log.error("Failed to handle merge/split for leaf page {}", leafPage.getPageId(), e);
            throw new KVStoreRuntimeException(ErrorCode.INTERNAL_ERROR,
                    "Failed to handle merge/split", e);
        }
    }

    private void checkMergeSplitForPage(Page page, IndexKey oldMinKey, int level, 
                                         Page parentPage, Set<Long> mergedPages) throws KVStoreException {
        if (page == null || page.isEmpty()) {
            return;
        }

        if (page.isOverFull()) {
            handlePageSplit(page, oldMinKey, level);
        } else if (page.isUnderfull()) {
            Long mergedPageId;
            if (page.isLeaf()) {
                handlePageMerge(page, parentPage, oldMinKey, level + 1);
                mergedPageId = null;
            } else {
                mergedPageId = handleIndexPageMerge(page, oldMinKey, level);
            }
            if (mergedPageId != null && mergedPages != null) {
                mergedPages.add(mergedPageId);
            }
        } else {
            if (!page.isLeaf() && page.getPageId() != tree.getWriterRoot().getPageId()) {
                createParentDeltaForUpdatedPage(page, level, oldMinKey);
            }
        }
    }

    private void addToWriteBuffer(int level, Page page) {
        if (page == null) {
            return;
        }
        if (!VirtualSegmentLocation.isVirtual(page.getLocation())) {
            log.error("can not put unmodified page to write buffer: {}", page.getLocation());
            throw new KVStoreRuntimeException(ErrorCode.INTERNAL_ERROR, "can not put unmodified page to write buffer: " + page.getLocation());
        }
        log.debug("put page {} to write buffer at level {}", page, level);
        writeBuffer.put(level, page);
    }
    private void handlePageSplit(Page pageToSplit, IndexKey origMappingKey, int level) throws KVStoreException {
        var pageIdManager = tree.getPageIdManager();
        int parentLevel = level + 1;

        List<Page> resultPages = splitPageIntoN(pageToSplit, pageIdManager);

        for (Page page : resultPages) {
            addToWriteBuffer(level, page);
        }

        if (pageToSplit.getPageId() == tree.getWriterRoot().getPageId()) {
            for (Page page : resultPages) {
                page.setPageType(Page.PageType.BRANCH);
            }

            long newRootId = tree.getPageIdManager().allocatePageId(Page.PageType.ROOT);
            Page newRoot = createPage(Page.PageType.ROOT, newRootId);
            for (Page page : resultPages) {
                recordParentLocation(page, newRoot.getLocation());
                deltaQueue.addDelta(VirtualSegmentLocation.create(newRootId),
                        ChangeDelta.put(page.getMinKey(),
                                IndexPair.of(page.getMinKey(), page.getLocation())),
                        level + 1);
            }
            tree.setWriterRoot(newRoot);
            tree.setHeight(tree.getHeight() + 1);
            addToWriteBuffer(level + 1, newRoot);

            log.info("Created new root {} due to page split into {} pages",
                    newRoot.getPageId(), resultPages.size());
        } else {
            SegmentLocation parentLocation = getParentLocation(pageToSplit);
            if (parentLocation != null) {
                for (Page page : resultPages) {
                    recordParentLocation(page, parentLocation);
                }
                deltaQueue.addDelta(parentLocation,
                        ChangeDelta.update(origMappingKey,
                                IndexPair.of(resultPages.get(0).getMinKey(), resultPages.get(0).getLocation())),
                        parentLevel);

                for (int i = 1; i < resultPages.size(); i++) {
                    Page page = resultPages.get(i);
                    deltaQueue.addDelta(parentLocation,
                            ChangeDelta.put(page.getMinKey(),
                                    IndexPair.of(page.getMinKey(), page.getLocation())),
                            parentLevel);
                }
            }
        }
    }

    private List<Page> splitPageIntoN(Page page, PageIdManager pageIdManager) {
        List<Page> result = new ArrayList<>();
        Deque<Page> toProcess = new ArrayDeque<>();
        toProcess.add(page);

        while (!toProcess.isEmpty()) {
            Page current = toProcess.poll();
            if (current.isOverFull() && current.getEntryCount() >= 2) {
                Page rightPage = current.split(pageIdManager.allocatePageId(current.getPageType()));
                if (currentDumpMetrics != null) {
                    currentDumpMetrics.incrementPagesSplit();
                }
                toProcess.add(current);
                toProcess.add(rightPage);
            } else {
                result.add(current);
            }
        }

        return result;
    }

    // handle page merge. returns the new right sibling key after merge, or null if no merge happened
    private void handlePageMerge(Page targetPage, Page parentPage, IndexKey origTargetPageMappingKey, int parentLevel) throws KVStoreException {
        handlePageMergeInternal(targetPage, origTargetPageMappingKey, parentLevel - 1, parentPage);
    }

    private Long handleIndexPageMerge(Page parentPage, IndexKey oldMinKey, int level) throws KVStoreException {
        return handlePageMergeInternal(parentPage, oldMinKey, level, null);
    }

    private Long handlePageMergeInternal(Page targetPage, IndexKey oldMinKey, int targetLevel, 
                                          Page parentPage) throws KVStoreException {
        if (targetPage.getPageId() == tree.getWriterRoot().getPageId()) {
            addToWriteBuffer(targetLevel, targetPage);
            return null;
        }

        SegmentLocation parentLocation;
        Page parent;
        int parentLevel = targetLevel + 1;
        
        if (parentPage != null) {
            parent = parentPage;
            parentLocation = parent.getLocation();
        } else {
            parentLocation = getParentLocation(targetPage);
            if (parentLocation == null) {
                log.warn("Could not find parent for page {}", targetPage.getPageId());
                addToWriteBuffer(targetLevel, targetPage);
                return null;
            }
            
            parent = getPageFromBufferOrStorage(parentLocation);
            if (parent == null) {
                log.error("Failed to load parent page at location: {}", parentLocation);
                addToWriteBuffer(targetLevel, targetPage);
                return null;
            }
        }

        int targetPageIndex = parent.getEntryIndex(oldMinKey);
        if (targetPageIndex < 0) {
            deltaQueue.addDelta(parentLocation, 
                    ChangeDelta.update(oldMinKey, IndexPair.of(targetPage.getMinKey(), targetPage.getLocation())), 
                    parentLevel);
            addToWriteBuffer(targetLevel, targetPage);
            return null;
        }

        var rightSiblingIndex = targetPageIndex + 1;

        if (rightSiblingIndex >= parent.getEntryCount()) {
            deltaQueue.addDelta(parentLocation,
                    ChangeDelta.update(oldMinKey,
                            IndexPair.of(targetPage.getMinKey(), targetPage.getLocation())),
                    parentLevel);
            addToWriteBuffer(targetLevel, targetPage);
            return null;
        }

        IndexPair rightSiblingEntry = parent.getEntryAt(rightSiblingIndex);
        if (!(rightSiblingEntry instanceof IndexPair.LocationEntry)) {
            log.error("Right sibling is not a location entry: {}", rightSiblingEntry);
            addToWriteBuffer(targetLevel, targetPage);
            return null;
        }

        SegmentLocation rightSiblingLocation = ((IndexPair.LocationEntry) rightSiblingEntry).location();
        Page rightSibling = getPageFromBufferOrStorage(rightSiblingLocation);

        if (rightSibling == null) {
            log.error("Failed to load right sibling page at location: {}", rightSiblingLocation);
            addToWriteBuffer(targetLevel, targetPage);
            return null;
        }
        
        if (rightSibling.isEmpty()) {
            log.info("Right sibling is empty, no need to merge: {}", rightSiblingLocation);
            addToWriteBuffer(targetLevel, targetPage);
            return null;
        }

        var rightMinKey = rightSibling.getMinKey();
        var rightPageId = rightSibling.getPageId();
        log.info("Merging right sibling page {} with target page {}", rightSiblingLocation, targetPage.getLocation());

        targetPage.merge(rightSibling);

        if (currentDumpMetrics != null) {
            currentDumpMetrics.incrementPagesMerged();
        }

        if (targetPage.isOverFull()) {
            targetPage.splitTo(rightSibling);
            log.info("Target page is over full, splitting into left = {}, right = {}", targetPage.getPageId(), rightSibling.getPageId());

            addToWriteBuffer(targetLevel, targetPage);
            addToWriteBuffer(targetLevel, rightSibling);
            
            deltaQueue.addDelta(parentLocation,
                    ChangeDelta.update(oldMinKey,
                            IndexPair.of(targetPage.getMinKey(), targetPage.getLocation())),
                    parentLevel);
            deltaQueue.addDelta(parentLocation,
                    ChangeDelta.update(rightMinKey,
                            IndexPair.of(rightSibling.getMinKey(), rightSibling.getLocation())),
                    parentLevel);
            return null;
        } else {
            addToWriteBuffer(targetLevel, targetPage);

            deltaQueue.addDelta(parentLocation,
                    ChangeDelta.update(oldMinKey,
                            IndexPair.of(targetPage.getMinKey(), targetPage.getLocation())),
                    parentLevel);
            deltaQueue.addDelta(parentLocation,
                    ChangeDelta.delete(rightMinKey),
                    parentLevel);

            if (parentPage == null) {
                trackPageDecommission(rightSibling.getOldLocation());
            }
            return rightPageId;
        }
    }

    private List<PathEntry> findLeafPageWithPath(IndexKey key) {
        LinkedList<PathEntry> path = new LinkedList<>();
        if (tree.getWriterRoot() == null) {
            return path;
        }

        int currentLevel = tree.getHeight() - 1;
        Page currentPage = tree.getWriterRoot();

        while (currentPage != null) {
            int entryIndex = Page.adjustIndexToChild(currentPage.getEntryIndex(key));
            path.add(new PathEntry(currentPage, currentLevel, entryIndex));
            if (currentPage.isLeaf()) {
                break;
            }
            var currentLocation = ((IndexPair.LocationEntry) currentPage.getEntryAt(entryIndex)).location();
            if (currentLocation == null) {
                return new LinkedList<>();
            }
            currentPage = getPageFromBufferOrStorage(currentLocation);
            currentLevel--;

            if (currentPage == null) {
                log.warn("Failed to load page at location: {} for key: {}", currentLocation, key);
                return new LinkedList<>();
            }
        }

        return path;
    }

    private IndexKey getRightSiblingKey(List<PathEntry> path) {
        IndexKey rightSiblingKey = null;
        for (var p: path.subList(0, path.size() - 1).reversed()) {
            int indexNum = p.indexNum;
            if (indexNum < p.page.getEntryCount() - 1) {
                rightSiblingKey = p.page.getEntryAt(indexNum + 1).key();
                break;
            }
        }
        return rightSiblingKey;
    }

    private Page getPageFromBufferOrStorage(SegmentLocation location) {
        if (VirtualSegmentLocation.isVirtual(location)) {
            long pageId = VirtualSegmentLocation.extractPageId(location);
            return writeBuffer.findPage(pageId);
        } else {
            Long pageId = pageLocationMapper.getPageIdForLoadedLocation(location);
            if (pageId != null) {
                var page = writeBuffer.findPage(pageId);
                if (page != null) {
                    return page;
                }
            }
        }
        var page = pageManager.getPage(location, false);
        pageLocationMapper.addMappingForLoadedLocation(page.getPageId(), location);
        return page;
    }

    /**
     * Processes all pending parent change deltas level by level (PALM-style).
     *
     * <p>Always processes the lowest level with pending deltas first.
     * Since split/merge operations only add deltas to higher levels,
     * this naturally forms a single bottom-up pass.
     */
    private void processParentDeltas() throws KVStoreException {
        while (!deltaQueue.isEmpty()) {
            int level = deltaQueue.getLevels().iterator().next();
            processDeltasAtLevel(level);
            flushAllPageInLevel(level);
        }
    }

    /**
     * Processes all deltas at a specific tree level.
     *
     * <p>Uses two-phase processing:
     * <ol>
     *   <li>Phase 1: Apply all deltas to all parents</li>
     *   <li>Phase 2: Check merge/split for all parents in sorted order</li>
     * </ol>
     */
    private void processDeltasAtLevel(int level) throws KVStoreException {
        var targetPageLocations = deltaQueue.getSortedLocationsAtLevel(level);

        log.info("Processing deltas at level {}. Number of target pages: {}", level, targetPageLocations.size());

        Set<Long> mergedPages = new HashSet<>();
        Page lastProcessedPage = null;
        IndexKey lastOldMinKey = null;

        for (SegmentLocation parentLocation : targetPageLocations) {
            List<ChangeDelta> deltas = deltaQueue.getDeltasForLocation(parentLocation);

            log.info("Processing {} deltas for parent at location {}", deltas.size(), parentLocation);

            Page parentPage = getPageFromBufferOrStorage(parentLocation);
            if (parentPage == null) {
                log.error("Failed to load parent page at location: {}", parentLocation);
                continue;
            }

            if (mergedPages.contains(parentPage.getPageId())) {
                log.debug("Skipping page {} as it was merged as right sibling", parentPage.getPageId());
                continue;
            }

            log.info("Loaded parent page {} with {} entries", parentPage, parentPage.getEntryCount());

            IndexKey oldMinKey = parentPage.getMinKey();
            applyDeltasToPage(parentPage, deltas, level);

            if (parentPage.isEmpty()) {
                if (parentPage.getPageId() != tree.getWriterRoot().getPageId()) {
                    SegmentLocation grandparentLocation = getParentLocation(parentPage);
                    if (grandparentLocation != null) {
                        deltaQueue.addDelta(grandparentLocation,
                                ChangeDelta.delete(oldMinKey), level + 1);
                    }
                } else {
                    tree.setWriterRoot(null);
                }
                continue;
            }

            addToWriteBuffer(level, parentPage);

            if (lastProcessedPage != null) {
                checkMergeSplitForParentPage(lastProcessedPage, lastOldMinKey, level, mergedPages);
                
                if (lastProcessedPage.getMaxKey() != null) {
                    writeBuffer.setCurrentKey(lastProcessedPage.getMaxKey());
                    flushCompletedPages(parentPage.getMinKey());
                }
            }

            lastProcessedPage = parentPage;
            lastOldMinKey = oldMinKey;
        }

        deltaQueue.clearLevel(level);

        if (lastProcessedPage != null) {
            checkMergeSplitForParentPage(lastProcessedPage, lastOldMinKey, level, mergedPages);
        }
    }

    /**
     * Checks and handles merge/split for a parent page after all deltas at the level are applied.
     */
    private void checkMergeSplitForParentPage(Page parentPage, IndexKey oldMinKey, int level, Set<Long> mergedPages) throws KVStoreException {
        checkMergeSplitForPage(parentPage, oldMinKey, level, null, mergedPages);
    }


    /**
     * Applies a list of deltas to a page.
     */
    private void applyDeltasToPage(Page page, List<ChangeDelta> deltas, int pageLevel) {
        log.info("Applying {} deltas to page {}", deltas.size(), page.getPageId());
        for (ChangeDelta delta : deltas) {
            log.debug("Applying delta: operation={}, targetKey={}, newIndexPair={}",
                    delta.getOperation(), delta.getTargetKey(), delta.getNewIndexPair());
            switch (delta.getOperation()) {
                case PUT -> {
                    IndexPair p = delta.getNewIndexPair();
                    if (p instanceof IndexPair.LocationEntry) {
                        p = IndexPair.of(p.key(), writeBuffer.resolveVirtualLocation(((IndexPair.LocationEntry) p).location(), pageLevel - 1 ));
                    }
                    page.put(p);
                }
                case UPDATE -> {
                    IndexPair p = delta.getNewIndexPair();
                    page.delete(delta.getTargetKey());
                    if (p instanceof IndexPair.LocationEntry) {
                        p = IndexPair.of(p.key(), writeBuffer.resolveVirtualLocation(((IndexPair.LocationEntry) p).location(), pageLevel - 1 ));
                    }
                    page.put(p);
                }
                case DELETE -> page.delete(delta.getTargetKey());
                default -> throw new IllegalArgumentException("Unknown operation: " + delta.getOperation());
            }
        }
        log.info("After applying deltas, page {} has {} entries", page.getPageId(), page.getEntryCount());
    }

    /**
     * Creates a parent delta for an updated page.
     *
     * <p>When a page at level N is updated, we need to propagate the change to its parent at level N+1.
     */
    private void createParentDeltaForUpdatedPage(Page page, int level, IndexKey oldMinKey) throws KVStoreException {
        log.info("createParentDeltaForUpdatedPage: page={}, level={}, oldMinKey={}, currentMinKey={}, treeHeight={}",
                page.getPageId(), level, oldMinKey, page.getMinKey(), tree.getHeight());

        if (level + 1 > tree.getHeight() - 1) {
            log.debug("Page {} is at level {}, no grandparent propagation needed", page.getPageId(), level);
            return;
        }

        SegmentLocation parentLocation = getParentLocation(page);
        if (parentLocation == null) {
            log.warn("Could not find parent for page {} at level {}", page.getPageId(), level);
            return;
        }

        if (!oldMinKey.equals(page.getMinKey())) {
            ChangeDelta deleteDelta = ChangeDelta.delete(oldMinKey);
            deltaQueue.addDelta(parentLocation, deleteDelta, level + 1);

            log.info("Created DELETE delta for old min key {} at level {}", oldMinKey, level + 1);
        }

        ChangeDelta putDelta = ChangeDelta.put(
                page.getMinKey(),
                IndexPair.of(page.getMinKey(), VirtualSegmentLocation.create(page.getPageId()))
        );
        deltaQueue.addDelta(parentLocation, putDelta, level + 1);

        log.info("Created PUT delta for page {} at level {} to parent at level {} (min key: {})",
                page.getPageId(), level, level + 1, page.getMinKey());
    }

    private void flushCompletedPages(IndexKey currentKey) throws KVStoreException {
        writeBuffer.setCurrentKey(currentKey);

        for (int level : writeBuffer.getLevelsInFlushOrder()) {
            int pageCount = writeBuffer.getPageCount(level);
            if (pageCount < 20) {
                continue;
            }
            List<Long> flushableIds = writeBuffer.getFlushablePageIds(level);
            if (!flushableIds.isEmpty()) {
                flushLevelPages(flushableIds, level);
            }
        }
    }

    private void flushLevelPages(List<Long> pageIds, int level) throws KVStoreException {
        List<Page> pagesToFlush = new ArrayList<>();
        List<Long> pageIdsToFlush = new ArrayList<>();

        for (Long pageId : pageIds) {
            Page page = writeBuffer.get(level, pageId);
            if (page != null && writeBuffer.getPageLocation(level, pageId) == null) {
                pagesToFlush.add(page);
                pageIdsToFlush.add(pageId);
            }
        }

        flushPagesWithOccupancyTracking(pagesToFlush, pageIdsToFlush, level);
        log.info("Flushed {} pages at level {}, id = {}", pagesToFlush.size(), level, pageIdsToFlush);
        for (Long pageId : pageIds) {
            writeBuffer.remove(level, pageId);
        }
    }

    private void flushAllPageInLevel(int level) throws KVStoreException {
            var pagesToFlush = writeBuffer.getPages(level);

            log.info("Level {} has {} pages in write buffer", level, pagesToFlush.size());

            List<Page> pages = new ArrayList<>();
            List<Long> pageIdsToFlush = new ArrayList<>();

            for (var p : pagesToFlush.entrySet()) {
                if ( writeBuffer.getPageLocation(level, p.getKey()) == null) {
                    pages.add(p.getValue());
                    pageIdsToFlush.add(p.getKey());
                    log.debug("Will flush page {} at level {}", p.getKey(), level);
                } else {
                    log.error("Page {} at level {} already has a location", p.getKey(), level);
                    throw new KVStoreException(ErrorCode.INTERNAL_ERROR, "Page already has a location");
                }
            }

            log.info("Flushing {} pages at level {}", pages.size(), level);

            flushPagesWithOccupancyTracking(pages, pageIdsToFlush, level);

        if (!pages.isEmpty() && level == tree.getHeight() - 1 && pages.getFirst().getPageType() == Page.PageType.ROOT) {
            tree.setWriterRoot(pages.getFirst());
            if (currentDumpMetrics != null) {
                currentDumpMetrics.incrementRootPageSaved();
            }
        }

        writeBuffer.clearLevel(level);
    }

    private List<SegmentLocation> savePagesWithTracking(List<Page> pages) throws KVStoreException {
        List<CompletableFuture<SegmentLocation>> futures = new ArrayList<>();
        for (Page page : pages) {
            page.setLifecycle(Page.PageLifecycle.FLUSHABLE);
            futures.add(pageManager.savePageAsync(page));
        }

        List<SegmentLocation> locations = new ArrayList<>();
        for (int i = 0; i < futures.size(); i++) {
            try {
                SegmentLocation location = futures.get(i).get();
                locations.add(location);
                trackPageWrite(location);
            } catch (Exception e) {
                throw new KVStoreException(ErrorCode.INTERNAL_ERROR, "Failed to save page", e);
            }
        }
        if (currentDumpMetrics != null) {
            currentDumpMetrics.incrementPagesSaved(pages.size());
        }
        return locations;
    }

    private void saveRootPage(Page rootPage, int height) throws KVStoreException {
        rootPage.setLifecycle(Page.PageLifecycle.FLUSHABLE);
        try {
            SegmentLocation rootLocation = pageManager.savePageAsync(rootPage).get();
            rootPage.setLocation(rootLocation);
            trackPageWrite(rootLocation);
            tree.setWriterRoot(rootPage);
            tree.setHeight(height);
            if (currentDumpMetrics != null) {
                currentDumpMetrics.incrementRootPageSaved();
            }
        } catch (Exception e) {
            throw new KVStoreException(ErrorCode.INTERNAL_ERROR, "Failed to save root page", e);
        }
    }

    private void flushPagesWithOccupancyTracking(List<Page> pages, List<Long> pageIds, int level) throws KVStoreException {
        List<CompletableFuture<SegmentLocation>> futures = new ArrayList<>();
        List<SegmentLocation> oldLocations = new ArrayList<>();

        for (int i = 0; i < pages.size(); i++) {
            Page page = pages.get(i);
            SegmentLocation oldLocation = page.getOldLocation();
            if (oldLocation != null && !VirtualSegmentLocation.isVirtual(oldLocation)) {
                oldLocations.add(oldLocation);
            } else {
                oldLocations.add(null);
            }
            page.setLifecycle(Page.PageLifecycle.FLUSHABLE);
            futures.add(pageManager.savePageAsync(page));
        }

        for (int i = 0; i < futures.size(); i++) {
            try {
                SegmentLocation location = futures.get(i).get();
                writeBuffer.setPageLocation(level, pageIds.get(i), location);
                trackPageWrite(location);
                if (oldLocations.get(i) != null) {
                    trackPageDecommission(oldLocations.get(i));
                }
            } catch (Exception e) {
                throw new KVStoreException(ErrorCode.INTERNAL_ERROR, "Failed to flush page", e);
            }
        }
        if (currentDumpMetrics != null) {
            currentDumpMetrics.incrementPagesFlushed(pages.size());
        }
    }

    private record PathEntry(Page page, int level, int indexNum) {
    }

    // child page ID -> parent location
    private final Map<Long, SegmentLocation> locationPairs = new HashMap<>();

    private void recordParentLocation(Page childPage, SegmentLocation parentLocation) {
        if (childPage != null && parentLocation != null) {
            locationPairs.put(childPage.getPageId(), parentLocation);
        }
    }

    private SegmentLocation getParentLocation(Page page) {
        return locationPairs.get(page.getPageId());
    }

    private static class DumpMetrics {
        private long startTime;
        private long endTime;
        private int leafPagesCreated;
        private int indexPagesCreated;
        private int pagesSplit;
        private int pagesMerged;
        private int pagesSaved;
        private int pagesDecommissioned;
        private int entriesInserted;
        private int entriesDeleted;
        private int pagesFlushed;
        private int rootPageSaved;

        public void start() {
            this.startTime = System.currentTimeMillis();
            this.leafPagesCreated = 0;
            this.indexPagesCreated = 0;
            this.pagesSplit = 0;
            this.pagesMerged = 0;
            this.pagesSaved = 0;
            this.pagesDecommissioned = 0;
            this.entriesInserted = 0;
            this.entriesDeleted = 0;
            this.pagesFlushed = 0;
            this.rootPageSaved = 0;
        }

        public void finish() {
            this.endTime = System.currentTimeMillis();
        }

        public long getDurationMs() {
            return endTime - startTime;
        }

        public void incrementLeafPagesCreated() {
            leafPagesCreated++;
        }

        public void incrementIndexPagesCreated() {
            indexPagesCreated++;
        }

        public void incrementPagesSplit() {
            pagesSplit++;
        }

        public void incrementPagesMerged() {
            pagesMerged++;
        }

        public void incrementPagesSaved(int count) {
            pagesSaved += count;
        }

        public void incrementPagesDecommissioned() {
            pagesDecommissioned++;
        }

        public void incrementEntriesInserted() {
            entriesInserted++;
        }

        public void incrementEntriesDeleted() {
            entriesDeleted++;
        }

        public void incrementPagesFlushed(int count) {
            pagesFlushed += count;
        }

        public void incrementRootPageSaved() {
            rootPageSaved++;
        }

        @Override
        public String toString() {
            return String.format(
                    "DumpMetrics{duration=%dms, leafPagesCreated=%d, indexPagesCreated=%d, " +
                            "pagesSplit=%d, pagesMerged=%d, pagesSaved=%d, pagesDecommissioned=%d, " +
                            "entriesInserted=%d, entriesDeleted=%d, pagesFlushed=%d, rootPageSaved=%d}",
                    getDurationMs(), leafPagesCreated, indexPagesCreated,
                    pagesSplit, pagesMerged, pagesSaved, pagesDecommissioned,
                    entriesInserted, entriesDeleted, pagesFlushed, rootPageSaved
            );
        }

        public String toLogMessage() {
            return String.format(
                    "Dump completed in %d ms: leafPagesCreated=%d, indexPagesCreated=%d, " +
                            "pagesSplit=%d, pagesMerged=%d, pagesSaved=%d, pagesDecommissioned=%d, " +
                            "entriesInserted=%d, entriesDeleted=%d, pagesFlushed=%d, rootPageSaved=%d",
                    getDurationMs(), leafPagesCreated, indexPagesCreated,
                    pagesSplit, pagesMerged, pagesSaved, pagesDecommissioned,
                    entriesInserted, entriesDeleted, pagesFlushed, rootPageSaved
            );
        }
    }
}
