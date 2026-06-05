package org.hyperkv.lsmplus.bplustree.page;

import org.hyperkv.lsmplus.api.model.IndexKey;
import org.hyperkv.lsmplus.api.model.IndexValue;
import org.hyperkv.lsmplus.bplustree.ChangeDelta;
import org.hyperkv.lsmplus.bplustree.PageCapacityConfig;
import org.hyperkv.lsmplus.bplustree.Pair;
import org.hyperkv.lsmplus.bplustree.VirtualSegmentLocation;
import org.hyperkv.lsmplus.exception.ErrorCode;
import org.hyperkv.lsmplus.exception.KVStoreRuntimeException;
import org.hyperkv.lsmplus.proto.Common;
import org.hyperkv.lsmplus.proto.Keyvalue;
import org.hyperkv.lsmplus.storage.SegmentLocation;

import java.nio.ByteBuffer;
import java.nio.ByteOrder;
import java.util.ArrayList;
import java.util.Collections;
import java.util.Comparator;
import java.util.List;
import java.util.UUID;
import java.util.stream.Collectors;

/**
 * Represents a page in the B+Tree.
 * 
 * <p>Page ID scheme:
 * <ul>
 *   <li>Positive (1, 2, 3, ...): Leaf pages</li>
 *   <li>Negative (Long.MIN_VALUE, Long.MIN_VALUE+1, ...): Index pages</li>
 *   <li>0: Invalid/null page ID</li>
 * </ul>
 * 
 * <p>Index page structure:
 * <ul>
 *   <li>All child references are stored as key-location pairs</li>
 *   <li>Each key represents the minimum key in that child's range</li>
 *   <li>No special handling for the first child - all children use the same key-location pair logic</li>
 * </ul>
 */
public final class Page {

    public enum PageStatus {
        VALID,
        INVALID
    }

    public enum PageLifecycle {
        CLEAN,
        DIRTY,
        FLUSHABLE,
        WRITING,
        FLUSHED,
    }

    private static final Comparator<IndexKey> KEY_COMPARATOR = Comparator.naturalOrder();

    private PageType pageType;
    private final long pageId;
    private final ArrayList<IndexPair> entries;
    private int usedSize;
    private final PageCapacityConfig config;

    private SegmentLocation location;
    private SegmentLocation oldLocation;

    private volatile PageStatus status = PageStatus.VALID;
    private volatile PageLifecycle lifecycle = PageLifecycle.CLEAN;

    private volatile IndexKey oldMappingKey = null;

    public Page(Page other) {
        this(other.pageType, other.pageId, other.config, null);
        other.entries.forEach(e -> entries.add(IndexPair.copy(e)));
        this.usedSize = calculateUsedSize();
        this.location = other.location;
        this.oldLocation = other.oldLocation;
    }

    public Page(PageType pageType, long pageId, PageCapacityConfig config, List<IndexPair> entries) {
        if (pageType == null) {
            throw new IllegalArgumentException("pageType must not be null");
        }
        this.pageType = pageType;
        this.pageId = pageId;
        this.config = config;
        this.entries = entries == null ? new ArrayList<>() : new ArrayList<>(entries);
        this.usedSize = calculateUsedSize();
        this.location = VirtualSegmentLocation.create(pageId);
    }

    public static Page createPage(PageType type, long pageId, PageCapacityConfig config, List<IndexPair> entries) {
        return new Page(type, pageId, config, entries);
    }

    public static Page deserialize(byte[] data, PageCapacityConfig config) {
        if (data == null || data.length == 0) {
            throw new IllegalArgumentException("Invalid page data");
        }

        try {
            org.hyperkv.lsmplus.proto.Page.PageProto pageProto = org.hyperkv.lsmplus.proto.Page.PageProto.parseFrom(data);
            return fromProto(pageProto, config);
        } catch (Exception e) {
            throw new IllegalArgumentException("Failed to deserialize page", e);
        }
    }

    public synchronized IndexPair put(IndexPair indexPair) {
        if (indexPair == null) {
            throw new IllegalArgumentException("indexPair must not be null");
        }
        if (indexPair.key() == null) {
            throw new IllegalArgumentException("key must not be null");
        }
        switch (indexPair) {
            case IndexPair.ValueEntry valueEntry when valueEntry.value() == null ->
                    throw new IllegalArgumentException("value must not be null");
            case IndexPair.LocationEntry locationEntry when locationEntry.location() == null ->
                    throw new IllegalArgumentException("location must not be null");
            case IndexPair.ValueEntry v when pageType != PageType.LEAF || v.value().equals(IndexValue.tombstone()) ->
                    throw new IllegalArgumentException("Cannot put IndexValue on non-leaf page");
            case IndexPair.LocationEntry _ when pageType == PageType.LEAF ->
                    throw new IllegalArgumentException("Cannot put IndexLocation on leaf page");
            default -> {
            }
        }

        var key = indexPair.key();
        int index = binarySearch(key);

        IndexPair newPair = IndexPair.copy(indexPair);
        IndexPair oldPair = null;
        if (index >= 0) {
            oldPair = entries.set(index, newPair);
            usedSize += calculateEntrySize(newPair) - calculateEntrySize(oldPair);
            if (index == 0) {
                oldMappingKey = oldPair.key();
            }
        } else {
            entries.add(-index - 1, newPair);
            usedSize += calculateEntrySize(newPair);
        }
        setLifecycle(PageLifecycle.DIRTY);
        return oldPair;
    }


    public synchronized IndexPair apply(ChangeDelta.Operation type, IndexKey key, IndexPair indexPair) {
        IndexPair v = null;
        if (indexPair == null && type != ChangeDelta.Operation.DELETE) {
            throw new IllegalArgumentException("indexPair must not be null");
        }
        if (type == ChangeDelta.Operation.PUT) {
            v = put(indexPair);
        } else if (type == ChangeDelta.Operation.UPDATE) {
            int index = getEntryIndex(key);
            if (index >= 0) {
                v = entries.get(index);
                entries.set(index, indexPair);
            }
        } else if (type == ChangeDelta.Operation.DELETE) {
            v = delete(key);
        }
        return v;
    }

    public List<IndexPair> applyAll(List<ChangeDelta> updates) {
        return updates.stream().map(u ->
                apply(u.getOperation(), u.getTargetKey(), u.getNewIndexPair())).collect(Collectors.toList());
    }

    public synchronized void put(IndexKey key, IndexValue value) {
        put(IndexPair.of(key, value));
    }

    public synchronized void put(IndexKey key, SegmentLocation location) {
        put(IndexPair.of(key, location));
    }

    public synchronized IndexValue get(IndexKey key) {
        if (key == null) {
            throw new IllegalArgumentException("key must not be null");
        }
        if (pageType != PageType.LEAF) {
            throw new IllegalStateException("Cannot get IndexValue from non-leaf page");
        }
        
        int index = binarySearch(key);
        if (index >= 0) {
            IndexPair pair = entries.get(index);
            if (pair instanceof IndexPair.ValueEntry ve) {
                return ve.value();
            }
        }
        return null;
    }

    public synchronized SegmentLocation getChildLocation(IndexKey key) {
        if (!isIndex()) {
            throw new IllegalStateException("Cannot get child location from non-index page");
        }
        
        if (key == null) {
            throw new IllegalArgumentException("key must not be null");
        }
        
        int index = binarySearch(key);
        if (index >= 0) {
            IndexPair pair = entries.get(index);
            if (pair instanceof IndexPair.LocationEntry le) {
                return le.location();
            }
        } else {
            int insertionPoint = -index - 1;
            if (insertionPoint > 0) {
                IndexPair pair = entries.get(insertionPoint - 1);
                if (pair instanceof IndexPair.LocationEntry le) {
                    return le.location();
                }
            } else if (!entries.isEmpty()) {
                IndexPair pair = entries.get(0);
                if (pair instanceof IndexPair.LocationEntry le) {
                    return le.location();
                }
            }
        }
        return null;
    }

    public synchronized IndexPair delete(IndexKey key) {
        if (key == null) {
            throw new IllegalArgumentException("key must not be null");
        }

        int index = binarySearch(key);
        if (index >= 0) {
            IndexPair removed = entries.remove(index);
            usedSize -= calculateEntrySize(removed);
            setLifecycle(PageLifecycle.DIRTY);
            if (index == 0) {
                oldMappingKey = removed.key();
            }
            return removed;
        }
        return null;
    }

    public synchronized List<IndexPair> rangeQuery(IndexKey start, IndexKey end) {
        return rangeQuery(start, end, true);
    }

    public synchronized List<IndexPair> rangeQuery(IndexKey start, IndexKey end, boolean startInclusive) {
        if (entries.isEmpty()) {
            return Collections.emptyList();
        }

        int startIndex = 0;
        int endIndex = entries.size();

        if (start != null) {
            int idx = binarySearch(start);
            if (idx >= 0) {
                startIndex = startInclusive ? idx : idx + 1;
            } else {
                startIndex = -idx - 1;
            }
        }

        if (end != null) {
            int idx = binarySearch(end);
            endIndex = idx >= 0 ? idx : -idx - 1;
        }

        if (startIndex >= endIndex) {
            return Collections.emptyList();
        }

        return new ArrayList<>(entries.subList(startIndex, endIndex));
    }

    public synchronized List<IndexPair> getAllEntries() {
        return new ArrayList<>(entries);
    }

    public synchronized IndexPair getEntryAt(int index) {
        if (index < 0 || index >= entries.size()) {
            return null;
        }
        return entries.get(index);
    }

    public IndexKey getMaxKey() {
        return entries.isEmpty()? null : entries.getLast().key();
    }

    public IndexKey getMinKey() {
        if (entries.isEmpty()) {
            return null;
        }
        return entries.get(0).key();
    }

    public boolean isOverFull() {
        if (config.getMaxEntries(pageType) != Integer.MAX_VALUE) {
            return entries.size() >= getMaxEntries();
        }
        return usedSize >= getMaxSize();
    }

    public boolean hasSpaceForEntry(IndexPair indexPair) {
        return hasSpaceForUpdates(Collections.singletonList(Pair.of(Common.OperationType.PUT, indexPair)));
    }

    public synchronized boolean hasSpaceForUpdates(List<Pair<Common.OperationType, IndexPair>> updates) {
        if (entries.size() + updates.size() >= getMaxEntries()) {
            int delta = 0;
            for (var u : updates) {
                int index = binarySearch(u.second().key());
                if (index < 0 && u.first() == Common.OperationType.PUT) {
                    delta++;
                } else if (index >= 0 && u.first() == Common.OperationType.DELETE) {
                    delta--;
                }
            }
            return delta + entries.size() <= getMaxEntries();
        }
        int entrySize = updates.stream()
                .mapToInt(p-> p.first() == Common.OperationType.PUT ? calculateEntrySize(p.second()) : 0).sum();
        for (var u : updates) {
            int index = binarySearch(u.second().key());
            if (index >= 0) {
                entrySize -= calculateEntrySize(entries.get(index));
            }
        }
        return usedSize + entrySize <= getMaxSize();
    }

    public boolean isEmpty() {
        return entries.isEmpty();
    }

    public PageType getPageType() {
        return pageType;
    }

    public long getPageId() {
        return pageId;
    }

    public int getMaxSize() {
        return config.getMaxSize(pageType);
    }

    public int getMaxEntries() {
        return config.getMaxEntries(pageType);
    }

    public int getUsedSize() {
        return usedSize;
    }

    public PageStatus getStatus() {
        return status;
    }

    public PageLifecycle getLifecycle() {
        return lifecycle;
    }

    public void setStatus(PageStatus status) {
        this.status = status;
    }

    public void setLifecycle(PageLifecycle lifecycle) {
        this.lifecycle = lifecycle;
        if (lifecycle == PageLifecycle.DIRTY && !VirtualSegmentLocation.isVirtual(location)) {
            oldLocation = location;
            location = VirtualSegmentLocation.create(pageId);
        }
        if (lifecycle == PageLifecycle.FLUSHABLE && !isLeaf()) {
            // check all entries are valid for non leaf page.
            for ( var e: entries ) {
                if (VirtualSegmentLocation.isVirtual(((IndexPair.LocationEntry)e).location())) {
                    throw new IllegalArgumentException("Invalid location entry: " + e);
                }
            }
        }
    }

    public SegmentLocation getOldLocation() {
        return oldLocation;
    }

    public void clearOldLocation() {
        oldLocation = null;
    }

    public int getEntryCount() {
        return entries.size();
    }

    public int getFreeSpace() {
        return getMaxSize() - usedSize;
    }

    public boolean isLeaf() {
        return pageType == PageType.LEAF;
    }

    public boolean isRoot() {
        return pageType == PageType.ROOT;
    }

    public boolean isBranch() {
        return pageType == PageType.BRANCH;
    }

    public boolean isIndex() {
        return pageType == PageType.ROOT || pageType == PageType.BRANCH;
    }

    public synchronized byte[] toByteArray() {
        return toProto().toByteArray();
    }

    public synchronized org.hyperkv.lsmplus.proto.Page.PageProto toProto() {
        org.hyperkv.lsmplus.proto.Page.PageProto.Builder builder = org.hyperkv.lsmplus.proto.Page.PageProto.newBuilder()
                .setPageType(pageType.toProtoType())
                .setPageId(pageId)
                .setUsedSize(usedSize);

        if (!entries.isEmpty()) {
            int[] offsets = new int[entries.size()];
            ByteBuffer entriesBuf = ByteBuffer.allocate(calculateSerializedEntriesSize());
            entriesBuf.order(ByteOrder.LITTLE_ENDIAN);

            for (int i = 0; i < entries.size(); i++) {
                offsets[i] = entriesBuf.position();
                byte[] entryBytes = entries.get(i).toProto().toByteArray();
                entriesBuf.put(entryBytes);
            }

            ByteBuffer offsetsBuf = ByteBuffer.allocate(offsets.length * 4);
            offsetsBuf.order(ByteOrder.LITTLE_ENDIAN);
            for (int offset : offsets) {
                offsetsBuf.putInt(offset);
            }

            builder.setEntryOffsets(com.google.protobuf.ByteString.copyFrom(offsetsBuf.array()));
            builder.setEntries(com.google.protobuf.ByteString.copyFrom(entriesBuf.array(), 0, entriesBuf.position()));
        }

        return builder.build();
    }

    private int calculateSerializedEntriesSize() {
        int size = 0;
        for (IndexPair pair : entries) {
            size += pair.toProto().getSerializedSize();
        }
        return size;
    }

    public static Page fromProto(org.hyperkv.lsmplus.proto.Page.PageProto proto, PageCapacityConfig config) {
        PageType pageType = PageType.fromProto(proto.getPageType());
        long pageId = proto.getPageId();

        ArrayList<IndexPair> entries = new ArrayList<>();
        IndexKey maxKey = null;

        if (!proto.getEntryOffsets().isEmpty() && !proto.getEntries().isEmpty()) {
            byte[] offsetsBytes = proto.getEntryOffsets().toByteArray();
            byte[] entriesBytes = proto.getEntries().toByteArray();

            int numEntries = offsetsBytes.length / 4;
            ByteBuffer offsetsBuf = ByteBuffer.wrap(offsetsBytes).order(ByteOrder.LITTLE_ENDIAN);
            int[] offsets = new int[numEntries];
            for (int i = 0; i < numEntries; i++) {
                offsets[i] = offsetsBuf.getInt();
            }

            for (int i = 0; i < numEntries; i++) {
                int start = offsets[i];
                int end = (i + 1 < numEntries) ? offsets[i + 1] : entriesBytes.length;
                byte[] entryBytes = new byte[end - start];
                System.arraycopy(entriesBytes, start, entryBytes, 0, entryBytes.length);
                try {
                    Keyvalue.KeyValuePairProto entryProto = Keyvalue.KeyValuePairProto.parseFrom(entryBytes);
                    IndexPair pair = IndexPair.fromProto(entryProto);
                    entries.add(pair);
                    IndexKey key = pair.key();
                    if (maxKey == null || key.compareTo(maxKey) > 0) {
                        maxKey = key;
                    }
                } catch (com.google.protobuf.InvalidProtocolBufferException e) {
                    throw new IllegalArgumentException("Failed to parse entry at index " + i, e);
                }
            }
        }

        Page page = new Page(pageType, pageId, config, entries);
        page.usedSize = proto.getUsedSize();
        return page;
    }

    public synchronized Page split(long newPageId) {
        if (entries.size() < 2) {
            throw new IllegalStateException("Cannot split page with less than 2 entries");
        }

        int splitPoint = findSplitPoint();
        ArrayList<IndexPair> rightEntries = new ArrayList<>(entries.subList(splitPoint, entries.size()));
        entries.subList(splitPoint, entries.size()).clear();

        usedSize = calculateUsedSize();

        setLifecycle(PageLifecycle.DIRTY);

        var rightPage = new Page(pageType, newPageId, config, rightEntries);
        rightPage.setLifecycle(PageLifecycle.DIRTY);
        return rightPage;
    }

    public synchronized Page splitTo(Page targetPage) {
        if (entries.size() < 2) {
            throw new IllegalStateException("Cannot split page with less than 2 entries");
        }

        int splitPoint = findSplitPoint();
        ArrayList<IndexPair> rightEntries = new ArrayList<>(entries.subList(splitPoint, entries.size()));
        entries.subList(splitPoint, entries.size()).clear();

        usedSize = calculateUsedSize();

        setLifecycle(PageLifecycle.DIRTY);

        targetPage.entries.clear();
        targetPage.entries.addAll(rightEntries);
        targetPage.usedSize = calculateUsedSize();

        setLifecycle(PageLifecycle.DIRTY);
        targetPage.setLifecycle(PageLifecycle.DIRTY);
        return targetPage;
    }

    public synchronized void merge(Page rightPage) {
        if (rightPage == null) {
            throw new IllegalArgumentException("rightPage must not be null");
        }
        if (pageType != rightPage.pageType) {
            throw new IllegalArgumentException("Cannot merge pages of different types");
        }
        
        entries.addAll(rightPage.getAllEntries());
        usedSize = calculateUsedSize();
        setLifecycle(PageLifecycle.DIRTY);
    }

    public boolean isUnderfull() {
        if (entries.isEmpty()) {
            return true;
        }
        int maxEntries = getMaxEntries();
        if (maxEntries != Integer.MAX_VALUE) {
            return entries.size() < maxEntries * 0.33;
        }
        return usedSize < getMaxSize() * 0.33;
    }

    private int findSplitPoint() {
        int maxEntries = getMaxEntries();
        if (maxEntries != Integer.MAX_VALUE) {
            return findSplitPointByEntryCount();
        }
        return findSplitPointBySize();
    }

    private int findSplitPointByEntryCount() {
        return entries.size() / 2;
    }

    private int findSplitPointBySize() {
        int targetSize = usedSize / 2;
        int cumulativeSize = 0;
        
        for (int i = 0; i < entries.size(); i++) {
            cumulativeSize += calculateEntrySize(entries.get(i));
            if (cumulativeSize >= targetSize) {
                return i + 1;
            }
        }
        
        return entries.size() / 2;
    }

    private int binarySearch(IndexKey key) {
        int low = 0;
        int high = entries.size() - 1;

        while (low <= high) {
            int mid = (low + high) >>> 1;
            IndexKey midKey = entries.get(mid).key();
            int cmp = KEY_COMPARATOR.compare(midKey, key);

            if (cmp < 0) {
                low = mid + 1;
            } else if (cmp > 0) {
                high = mid - 1;
            } else {
                return mid;
            }
        }
        return -(low + 1);
    }
    public int getEntryIndex(IndexKey key) {
        return binarySearch(key);
    }

    public void updateIndexPair(int index, IndexPair pair) {
        if (index < 0 || index >= entries.size()) {
            throw new KVStoreRuntimeException(ErrorCode.INTERNAL_ERROR, "Invalid index: " + index);
        }
        entries.set(index, pair);
        setLifecycle(PageLifecycle.DIRTY);
    }
    private IndexPair findEntry(IndexKey key) {
        int index = binarySearch(key);
        return index >= 0 ? entries.get(index) : null;
    }

    public void setPageType(PageType pageType) {
        this.pageType = pageType;
    }


    private int calculateEntrySize(IndexPair pair) {
        if (pair == null || pair.key() == null) {
            return 0;
        }
        int valueSize = switch (pair) {
            case IndexPair.ValueEntry ve -> ve.value().getValueData().length;
            case IndexPair.LocationEntry le -> 32;
        };
        return pair.key().getKeyData().length + valueSize + 8;
    }

    private int calculateUsedSize() {
        int size = 0;
        for (IndexPair pair : entries) {
            size += calculateEntrySize(pair);
        }
        return size;
    }

    public void setLocation(SegmentLocation location) {
        this.location = location;
    }

    public SegmentLocation getLocation() {
        return location;
    }

    public boolean hasOldMappingKey() {
        return oldMappingKey != null;
    }

    public IndexKey getOldMappingKey() {
        return oldMappingKey;
    }

    @Override
    public String toString() {
        return "Page{type=" + pageType +
               ", id=" + pageId +
               ", entries=" + entries.size() +
               ", used=" + usedSize + "/" + config.getMaxSize(pageType) + "}";
    }

    public enum PageType {
        LEAF(Common.PageType.PAGE_LEAF),
        BRANCH(Common.PageType.PAGE_BRANCH),
        ROOT(Common.PageType.PAGE_ROOT);

        private final Common.PageType protoType;

        PageType(Common.PageType protoType) {
            this.protoType = protoType;
        }

        public Common.PageType toProtoType() {
            return protoType;
        }

        public static PageType fromProto(Common.PageType protoType) {
            for (PageType type : values()) {
                if (type.protoType == protoType) {
                    return type;
                }
            }
            throw new IllegalArgumentException("Unknown PageType: " + protoType);
        }
    }

    public static Keyvalue.KeyProto toKeyProto(IndexKey key) {
        return Keyvalue.KeyProto.newBuilder()
                .setKeyType(Common.KeyType.ORDERED_BYTES)
                .setKeyData(com.google.protobuf.ByteString.copyFrom(key.getKeyData()))
                .build();
    }

    public static IndexKey fromKeyProto(Keyvalue.KeyProto keyProto) {
        return IndexKey.orderedBytes(keyProto.getKeyData().toByteArray());
    }

    public static Keyvalue.ValueProto toValueProto(IndexValue value) {
        Keyvalue.ValueProto.Builder builder = Keyvalue.ValueProto.newBuilder();
        if (value.isTombstone()) {
            builder.setValueType(Common.ValueType.TOMBSTONE);
        } else {
            builder.setValueType(Common.ValueType.NORMAL);
            builder.setValueData(com.google.protobuf.ByteString.copyFrom(value.getValueData()));
        }
        return builder.build();
    }

    public static IndexValue fromValueProto(Keyvalue.ValueProto valueProto) {
        if (valueProto.getValueType() == Common.ValueType.TOMBSTONE) {
            return IndexValue.tombstone();
        }
        return IndexValue.normal(valueProto.getValueData().toByteArray());
    }

    public static Keyvalue.SegmentLocationProto toSegmentLocationProto(SegmentLocation location) {
        return Keyvalue.SegmentLocationProto.newBuilder()
                .setChunkIdMostSig(location.getChunkId().getMostSignificantBits())
                .setChunkIdLeastSig(location.getChunkId().getLeastSignificantBits())
                .setOffset(location.getOffset())
                .setLength(location.getLength())
                .build();
    }

    public static SegmentLocation fromSegmentLocationProto(Keyvalue.SegmentLocationProto proto) {
        UUID chunkId = new UUID(proto.getChunkIdMostSig(), proto.getChunkIdLeastSig());
        return new SegmentLocation(chunkId, proto.getOffset(), proto.getLength());
    }

    public static int adjustIndexToChild(int index) {
        if (index >= 0) {
            return index;
        }
        index = -index - 2;
        if (index < 0)
            index = 0;
        return index;
    }
}
