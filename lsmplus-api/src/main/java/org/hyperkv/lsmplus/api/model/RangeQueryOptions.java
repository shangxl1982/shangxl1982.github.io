package org.hyperkv.lsmplus.api.model;

import java.util.Arrays;

public final class RangeQueryOptions {

    public static final RangeQueryOptions DEFAULT = new RangeQueryOptions(null, null, 0, null, null, true);

    private final IndexKey start;
    private final IndexKey end;
    private final int limit;
    private final byte[] prefix;
    private final IndexKey continuationToken;
    private final boolean startInclusive;

    private RangeQueryOptions(IndexKey start, IndexKey end, int limit, byte[] prefix, 
                              IndexKey continuationToken, boolean startInclusive) {
        this.start = start;
        this.end = end;
        this.limit = limit;
        this.prefix = prefix != null ? prefix.clone() : null;
        this.continuationToken = continuationToken;
        this.startInclusive = startInclusive;
    }

    private RangeQueryOptions(IndexKey start, IndexKey end, int limit, byte[] prefix) {
        this(start, end, limit, prefix, null, true);
    }

    public static Builder builder() {
        return new Builder();
    }

    public static RangeQueryOptions range(IndexKey start, IndexKey end) {
        return new RangeQueryOptions(start, end, 0, null);
    }

    public static RangeQueryOptions prefix(byte[] prefix) {
        return new RangeQueryOptions(null, null, 0, prefix);
    }

    public static RangeQueryOptions limit(int limit) {
        if (limit <= 0) {
            throw new IllegalArgumentException("limit must be positive");
        }
        return new RangeQueryOptions(null, null, limit, null);
    }

    public static RangeQueryOptions fromToken(IndexKey token) {
        return new RangeQueryOptions(null, null, 0, null, token, false);
    }

    public IndexKey getStart() {
        return start;
    }

    public IndexKey getEnd() {
        return end;
    }

    public int getLimit() {
        return limit;
    }

    public byte[] getPrefix() {
        return prefix != null ? prefix.clone() : null;
    }

    public IndexKey getContinuationToken() {
        return continuationToken;
    }

    public boolean isStartInclusive() {
        return startInclusive;
    }

    public boolean hasLimit() {
        return limit > 0;
    }

    public boolean hasPrefix() {
        return prefix != null && prefix.length > 0;
    }

    public boolean hasRange() {
        return start != null || end != null;
    }

    public boolean hasContinuationToken() {
        return continuationToken != null;
    }

    public IndexKey getEffectiveStart() {
        IndexKey effectiveStart = start;

        if (continuationToken != null) {
            effectiveStart = continuationToken;
        }

        if (hasPrefix()) {
            IndexKey prefixStart = IndexKey.orderedBytes(prefix);
            if (effectiveStart == null || prefixStart.compareTo(effectiveStart) > 0) {
                return prefixStart;
            }
        }
        return effectiveStart;
    }

    public boolean isEffectiveStartInclusive() {
        if (continuationToken != null) {
            return false;
        }
        if (hasPrefix() && start == null) {
            return true;
        }
        return startInclusive;
    }

    public IndexKey getEffectiveEnd() {
        if (hasPrefix()) {
            byte[] prefixEnd = calculatePrefixEnd(prefix);
            if (prefixEnd == null) {
                return end;
            }
            IndexKey prefixEndKey = IndexKey.orderedBytes(prefixEnd);
            if (end == null || prefixEndKey.compareTo(end) < 0) {
                return prefixEndKey;
            }
        }
        return end;
    }

    private static byte[] calculatePrefixEnd(byte[] prefix) {
        byte[] result = new byte[prefix.length];
        System.arraycopy(prefix, 0, result, 0, prefix.length);
        
        for (int i = result.length - 1; i >= 0; i--) {
            if (result[i] < Byte.MAX_VALUE) {
                result[i]++;
                return result;
            }
            result[i] = Byte.MIN_VALUE;
        }
        
        return null;
    }

    public boolean matchesPrefix(IndexKey key) {
        if (key == null) {
            return false;
        }
        if (!hasPrefix()) {
            return true;
        }
        byte[] keyData = key.getKeyData();
        if (keyData.length < prefix.length) {
            return false;
        }
        for (int i = 0; i < prefix.length; i++) {
            if (keyData[i] != prefix[i]) {
                return false;
            }
        }
        return true;
    }

    @Override
    public String toString() {
        return "RangeQueryOptions{start=" + start + 
               ", end=" + end + 
               ", limit=" + limit + 
               ", prefix=" + Arrays.toString(prefix) +
               ", continuationToken=" + continuationToken +
               ", startInclusive=" + startInclusive + "}";
    }

    public static final class Builder {
        private IndexKey start;
        private IndexKey end;
        private int limit;
        private byte[] prefix;
        private IndexKey continuationToken;
        private boolean startInclusive = true;

        private Builder() {
        }

        public Builder start(IndexKey start) {
            this.start = start;
            return this;
        }

        public Builder end(IndexKey end) {
            this.end = end;
            return this;
        }

        public Builder range(IndexKey start, IndexKey end) {
            this.start = start;
            this.end = end;
            return this;
        }

        public Builder limit(int limit) {
            if (limit <= 0) {
                throw new IllegalArgumentException("limit must be positive");
            }
            this.limit = limit;
            return this;
        }

        public Builder prefix(byte[] prefix) {
            this.prefix = prefix != null ? prefix.clone() : null;
            return this;
        }

        public Builder continuationToken(IndexKey token) {
            this.continuationToken = token;
            this.startInclusive = false;
            return this;
        }

        public Builder startInclusive(boolean inclusive) {
            this.startInclusive = inclusive;
            return this;
        }

        public RangeQueryOptions build() {
            return new RangeQueryOptions(start, end, limit, prefix, continuationToken, startInclusive);
        }
    }
}
