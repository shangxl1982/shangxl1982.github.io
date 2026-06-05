package org.hyperkv.lsmplus.api.model;

import java.util.ArrayList;
import java.util.Collections;
import java.util.List;
import java.util.Map;

public final class RangeQueryResult {

    private final List<Map.Entry<IndexKey, IndexValue>> entries;
    private final int totalCount;
    private final boolean hasMore;
    private final IndexKey lastKey;
    private final IndexKey continuationToken;

    public RangeQueryResult(List<Map.Entry<IndexKey, IndexValue>> entries, int totalCount, boolean hasMore) {
        this.entries = entries != null ? Collections.unmodifiableList(entries) : Collections.emptyList();
        this.totalCount = totalCount;
        this.hasMore = hasMore;
        this.lastKey = !this.entries.isEmpty() ? this.entries.get(this.entries.size() - 1).getKey() : null;
        this.continuationToken = hasMore ? this.lastKey : null;
    }

    public RangeQueryResult(List<Map.Entry<IndexKey, IndexValue>> entries, int totalCount, boolean hasMore, IndexKey continuationToken) {
        this.entries = entries != null ? Collections.unmodifiableList(entries) : Collections.emptyList();
        this.totalCount = totalCount;
        this.hasMore = hasMore;
        this.lastKey = !this.entries.isEmpty() ? this.entries.get(this.entries.size() - 1).getKey() : null;
        this.continuationToken = continuationToken;
    }

    public static RangeQueryResult empty() {
        return new RangeQueryResult(Collections.emptyList(), 0, false);
    }

    public static RangeQueryResult of(List<Map.Entry<IndexKey, IndexValue>> entries) {
        return new RangeQueryResult(entries, entries != null ? entries.size() : 0, false);
    }

    public static RangeQueryResult of(List<Map.Entry<IndexKey, IndexValue>> entries, int totalCount, boolean hasMore) {
        return new RangeQueryResult(entries, totalCount, hasMore);
    }

    public static RangeQueryResult of(List<Map.Entry<IndexKey, IndexValue>> entries, int totalCount, boolean hasMore, IndexKey continuationToken) {
        return new RangeQueryResult(entries, totalCount, hasMore, continuationToken);
    }

    public List<Map.Entry<IndexKey, IndexValue>> getEntries() {
        return entries;
    }

    public int getCount() {
        return entries.size();
    }

    public int getTotalCount() {
        return totalCount;
    }

    public boolean hasMore() {
        return hasMore;
    }

    public IndexKey getLastKey() {
        return lastKey;
    }

    public IndexKey getContinuationToken() {
        return continuationToken;
    }

    public boolean isEmpty() {
        return entries.isEmpty();
    }

    public RangeQueryResult withHasMore(boolean hasMore) {
        return new RangeQueryResult(entries, totalCount, hasMore);
    }

    public RangeQueryResult withContinuationToken(IndexKey token) {
        return new RangeQueryResult(entries, totalCount, hasMore, token);
    }

    public RangeQueryResult limitTo(int maxCount) {
        if (entries.size() <= maxCount) {
            return this;
        }
        List<Map.Entry<IndexKey, IndexValue>> limited = new ArrayList<>(entries.subList(0, maxCount));
        IndexKey token = limited.get(limited.size() - 1).getKey();
        return new RangeQueryResult(limited, totalCount, true, token);
    }

    @Override
    public String toString() {
        return "RangeQueryResult{count=" + entries.size() + 
               ", totalCount=" + totalCount + 
               ", hasMore=" + hasMore + 
               ", hasToken=" + (continuationToken != null) + "}";
    }
}
