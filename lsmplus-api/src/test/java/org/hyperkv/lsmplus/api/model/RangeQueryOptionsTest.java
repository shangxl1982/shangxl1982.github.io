package org.hyperkv.lsmplus.api.model;

import org.junit.jupiter.api.Test;

import java.nio.charset.StandardCharsets;
import java.util.AbstractMap;
import java.util.ArrayList;
import java.util.List;
import java.util.Map;

import static org.junit.jupiter.api.Assertions.*;

class RangeQueryOptionsTest {

    @Test
    void testDefaultOptions() {
        RangeQueryOptions options = RangeQueryOptions.DEFAULT;

        assertNull(options.getStart());
        assertNull(options.getEnd());
        assertEquals(0, options.getLimit());
        assertNull(options.getPrefix());
        assertFalse(options.hasLimit());
        assertFalse(options.hasPrefix());
        assertFalse(options.hasRange());
    }

    @Test
    void testBuilder() {
        IndexKey start = IndexKey.orderedBytes("start".getBytes(StandardCharsets.UTF_8));
        IndexKey end = IndexKey.orderedBytes("end".getBytes(StandardCharsets.UTF_8));
        byte[] prefix = "prefix".getBytes(StandardCharsets.UTF_8);

        RangeQueryOptions options = RangeQueryOptions.builder()
                .start(start)
                .end(end)
                .limit(100)
                .prefix(prefix)
                .build();

        assertEquals(start, options.getStart());
        assertEquals(end, options.getEnd());
        assertEquals(100, options.getLimit());
        assertArrayEquals(prefix, options.getPrefix());
        assertTrue(options.hasLimit());
        assertTrue(options.hasPrefix());
        assertTrue(options.hasRange());
    }

    @Test
    void testRangeFactory() {
        IndexKey start = IndexKey.orderedBytes("start".getBytes(StandardCharsets.UTF_8));
        IndexKey end = IndexKey.orderedBytes("end".getBytes(StandardCharsets.UTF_8));

        RangeQueryOptions options = RangeQueryOptions.range(start, end);

        assertEquals(start, options.getStart());
        assertEquals(end, options.getEnd());
        assertFalse(options.hasLimit());
        assertFalse(options.hasPrefix());
    }

    @Test
    void testPrefixFactory() {
        byte[] prefix = "user:".getBytes(StandardCharsets.UTF_8);

        RangeQueryOptions options = RangeQueryOptions.prefix(prefix);

        assertNull(options.getStart());
        assertNull(options.getEnd());
        assertArrayEquals(prefix, options.getPrefix());
        assertTrue(options.hasPrefix());
    }

    @Test
    void testLimitFactory() {
        RangeQueryOptions options = RangeQueryOptions.limit(50);

        assertEquals(50, options.getLimit());
        assertTrue(options.hasLimit());
    }

    @Test
    void testLimitMustBePositive() {
        assertThrows(IllegalArgumentException.class, () -> RangeQueryOptions.limit(0));
        assertThrows(IllegalArgumentException.class, () -> RangeQueryOptions.limit(-1));
    }

    @Test
    void testBuilderLimitMustBePositive() {
        assertThrows(IllegalArgumentException.class, () -> RangeQueryOptions.builder().limit(0));
    }

    @Test
    void testEffectiveStartWithPrefix() {
        byte[] prefix = "user:".getBytes(StandardCharsets.UTF_8);
        IndexKey start = IndexKey.orderedBytes("admin".getBytes(StandardCharsets.UTF_8));

        RangeQueryOptions options = RangeQueryOptions.builder()
                .start(start)
                .prefix(prefix)
                .build();

        IndexKey effectiveStart = options.getEffectiveStart();
        assertNotNull(effectiveStart);
        assertArrayEquals(prefix, effectiveStart.getKeyData());
    }

    @Test
    void testEffectiveEndWithPrefix() {
        byte[] prefix = "user:".getBytes(StandardCharsets.UTF_8);

        RangeQueryOptions options = RangeQueryOptions.prefix(prefix);

        IndexKey effectiveEnd = options.getEffectiveEnd();
        assertNotNull(effectiveEnd);
        byte[] endData = effectiveEnd.getKeyData();
        assertTrue(endData.length >= prefix.length);
    }

    @Test
    void testMatchesPrefix() {
        byte[] prefix = "user:".getBytes(StandardCharsets.UTF_8);
        RangeQueryOptions options = RangeQueryOptions.prefix(prefix);

        IndexKey matchingKey = IndexKey.orderedBytes("user:123".getBytes(StandardCharsets.UTF_8));
        IndexKey nonMatchingKey = IndexKey.orderedBytes("admin:456".getBytes(StandardCharsets.UTF_8));
        IndexKey shortKey = IndexKey.orderedBytes("us".getBytes(StandardCharsets.UTF_8));

        assertTrue(options.matchesPrefix(matchingKey));
        assertFalse(options.matchesPrefix(nonMatchingKey));
        assertFalse(options.matchesPrefix(shortKey));
    }

    @Test
    void testMatchesPrefixNoPrefix() {
        RangeQueryOptions options = RangeQueryOptions.DEFAULT;

        IndexKey key = IndexKey.orderedBytes("anykey".getBytes(StandardCharsets.UTF_8));

        assertTrue(options.matchesPrefix(key));
        assertFalse(options.matchesPrefix(null));
    }

    @Test
    void testPrefixEndCalculation() {
        byte[] prefix = "abc".getBytes(StandardCharsets.UTF_8);
        RangeQueryOptions options = RangeQueryOptions.prefix(prefix);

        IndexKey effectiveEnd = options.getEffectiveEnd();
        assertNotNull(effectiveEnd);

        byte[] endData = effectiveEnd.getKeyData();
        assertEquals('a', endData[0]);
        assertEquals('b', endData[1]);
        assertEquals('c' + 1, endData[2]);
    }

    @Test
    void testContinuationToken() {
        IndexKey token = IndexKey.orderedBytes("last-key".getBytes(StandardCharsets.UTF_8));
        RangeQueryOptions options = RangeQueryOptions.builder()
                .continuationToken(token)
                .build();

        assertTrue(options.hasContinuationToken());
        assertEquals(token, options.getContinuationToken());
        assertEquals(token, options.getEffectiveStart());
        assertFalse(options.isEffectiveStartInclusive());
    }

    @Test
    void testFromTokenFactory() {
        IndexKey token = IndexKey.orderedBytes("token".getBytes(StandardCharsets.UTF_8));
        RangeQueryOptions options = RangeQueryOptions.fromToken(token);

        assertTrue(options.hasContinuationToken());
        assertEquals(token, options.getContinuationToken());
        assertFalse(options.isStartInclusive());
    }

    @Test
    void testStartInclusiveDefault() {
        IndexKey start = IndexKey.orderedBytes("start".getBytes(StandardCharsets.UTF_8));
        RangeQueryOptions options = RangeQueryOptions.builder()
                .start(start)
                .build();

        assertTrue(options.isStartInclusive());
        assertTrue(options.isEffectiveStartInclusive());
    }

    @Test
    void testStartInclusiveExplicit() {
        IndexKey start = IndexKey.orderedBytes("start".getBytes(StandardCharsets.UTF_8));
        RangeQueryOptions options = RangeQueryOptions.builder()
                .start(start)
                .startInclusive(false)
                .build();

        assertFalse(options.isStartInclusive());
        assertFalse(options.isEffectiveStartInclusive());
    }

    @Test
    void testContinuationTokenOverridesStartInclusive() {
        IndexKey token = IndexKey.orderedBytes("token".getBytes(StandardCharsets.UTF_8));
        RangeQueryOptions options = RangeQueryOptions.builder()
                .continuationToken(token)
                .startInclusive(true)
                .build();

        assertFalse(options.isEffectiveStartInclusive());
    }

    @Test
    void testPrefixEndOverflow() {
        byte[] prefix = new byte[]{Byte.MAX_VALUE, Byte.MAX_VALUE, Byte.MAX_VALUE};
        RangeQueryOptions options = RangeQueryOptions.prefix(prefix);

        IndexKey effectiveEnd = options.getEffectiveEnd();
        assertNull(effectiveEnd);
    }
}
