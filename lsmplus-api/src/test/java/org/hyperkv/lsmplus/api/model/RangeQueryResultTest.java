package org.hyperkv.lsmplus.api.model;

import org.junit.jupiter.api.Test;

import java.nio.charset.StandardCharsets;
import java.util.AbstractMap;
import java.util.ArrayList;
import java.util.List;
import java.util.Map;

import static org.junit.jupiter.api.Assertions.*;

class RangeQueryResultTest {

    @Test
    void testEmpty() {
        RangeQueryResult result = RangeQueryResult.empty();

        assertTrue(result.isEmpty());
        assertEquals(0, result.getCount());
        assertEquals(0, result.getTotalCount());
        assertFalse(result.hasMore());
        assertNull(result.getLastKey());
    }

    @Test
    void testOfList() {
        List<Map.Entry<IndexKey, IndexValue>> entries = new ArrayList<>();
        entries.add(createEntry("key1", "value1"));
        entries.add(createEntry("key2", "value2"));

        RangeQueryResult result = RangeQueryResult.of(entries);

        assertEquals(2, result.getCount());
        assertEquals(2, result.getTotalCount());
        assertFalse(result.hasMore());
        assertNotNull(result.getLastKey());
    }

    @Test
    void testOfWithTotalCountAndHasMore() {
        List<Map.Entry<IndexKey, IndexValue>> entries = new ArrayList<>();
        entries.add(createEntry("key1", "value1"));

        RangeQueryResult result = RangeQueryResult.of(entries, 100, true);

        assertEquals(1, result.getCount());
        assertEquals(100, result.getTotalCount());
        assertTrue(result.hasMore());
    }

    @Test
    void testContinuationToken() {
        List<Map.Entry<IndexKey, IndexValue>> entries = new ArrayList<>();
        entries.add(createEntry("key1", "value1"));
        entries.add(createEntry("key2", "value2"));

        IndexKey token = IndexKey.orderedBytes("key2".getBytes(StandardCharsets.UTF_8));
        RangeQueryResult result = RangeQueryResult.of(entries, 100, true, token);

        assertTrue(result.hasMore());
        assertNotNull(result.getContinuationToken());
        assertArrayEquals("key2".getBytes(StandardCharsets.UTF_8), result.getContinuationToken().getKeyData());
    }

    @Test
    void testContinuationTokenNullWhenNoMore() {
        List<Map.Entry<IndexKey, IndexValue>> entries = new ArrayList<>();
        entries.add(createEntry("key1", "value1"));

        RangeQueryResult result = RangeQueryResult.of(entries, 1, false);

        assertFalse(result.hasMore());
        assertNull(result.getContinuationToken());
    }

    @Test
    void testContinuationTokenAutoSetWhenHasMore() {
        List<Map.Entry<IndexKey, IndexValue>> entries = new ArrayList<>();
        entries.add(createEntry("key1", "value1"));
        entries.add(createEntry("key2", "value2"));

        RangeQueryResult result = new RangeQueryResult(entries, 100, true);

        assertTrue(result.hasMore());
        assertNotNull(result.getContinuationToken());
        assertArrayEquals("key2".getBytes(StandardCharsets.UTF_8), result.getContinuationToken().getKeyData());
    }

    @Test
    void testWithContinuationToken() {
        List<Map.Entry<IndexKey, IndexValue>> entries = new ArrayList<>();
        entries.add(createEntry("key1", "value1"));

        RangeQueryResult result = RangeQueryResult.of(entries);
        assertNull(result.getContinuationToken());

        IndexKey token = IndexKey.orderedBytes("key1".getBytes(StandardCharsets.UTF_8));
        RangeQueryResult withToken = result.withContinuationToken(token);
        assertNotNull(withToken.getContinuationToken());
    }

    @Test
    void testGetEntries() {
        List<Map.Entry<IndexKey, IndexValue>> entries = new ArrayList<>();
        entries.add(createEntry("key1", "value1"));
        entries.add(createEntry("key2", "value2"));

        RangeQueryResult result = RangeQueryResult.of(entries);

        List<Map.Entry<IndexKey, IndexValue>> retrieved = result.getEntries();
        assertEquals(2, retrieved.size());
        assertThrows(UnsupportedOperationException.class, () -> retrieved.add(null));
    }

    @Test
    void testWithHasMore() {
        List<Map.Entry<IndexKey, IndexValue>> entries = new ArrayList<>();
        entries.add(createEntry("key1", "value1"));

        RangeQueryResult result = RangeQueryResult.of(entries);
        assertFalse(result.hasMore());

        RangeQueryResult withMore = result.withHasMore(true);
        assertTrue(withMore.hasMore());
        assertEquals(result.getCount(), withMore.getCount());
    }

    @Test
    void testLimitTo() {
        List<Map.Entry<IndexKey, IndexValue>> entries = new ArrayList<>();
        for (int i = 0; i < 10; i++) {
            entries.add(createEntry("key" + i, "value" + i));
        }

        RangeQueryResult result = RangeQueryResult.of(entries);

        RangeQueryResult limited = result.limitTo(5);
        assertEquals(5, limited.getCount());
        assertTrue(limited.hasMore());

        RangeQueryResult notLimited = result.limitTo(20);
        assertEquals(10, notLimited.getCount());
        assertFalse(notLimited.hasMore());
    }

    @Test
    void testNullEntries() {
        RangeQueryResult result = RangeQueryResult.of(null);

        assertTrue(result.isEmpty());
        assertEquals(0, result.getCount());
    }

    @Test
    void testLastKey() {
        List<Map.Entry<IndexKey, IndexValue>> entries = new ArrayList<>();
        entries.add(createEntry("key1", "value1"));
        entries.add(createEntry("key2", "value2"));
        entries.add(createEntry("key3", "value3"));

        RangeQueryResult result = RangeQueryResult.of(entries);

        IndexKey lastKey = result.getLastKey();
        assertNotNull(lastKey);
        assertArrayEquals("key3".getBytes(StandardCharsets.UTF_8), lastKey.getKeyData());
    }

    @Test
    void testLastKeyEmpty() {
        RangeQueryResult result = RangeQueryResult.empty();
        assertNull(result.getLastKey());
    }

    private Map.Entry<IndexKey, IndexValue> createEntry(String key, String value) {
        return new AbstractMap.SimpleEntry<>(
                IndexKey.orderedBytes(key.getBytes(StandardCharsets.UTF_8)),
                IndexValue.normal(value.getBytes(StandardCharsets.UTF_8))
        );
    }
}
