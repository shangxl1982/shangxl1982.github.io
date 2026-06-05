package org.hyperkv.lsmplus.memory;

import org.hyperkv.lsmplus.api.model.IndexKey;
import org.hyperkv.lsmplus.api.model.IndexValue;
import org.hyperkv.lsmplus.api.model.RangeQueryOptions;
import org.hyperkv.lsmplus.api.model.RangeQueryResult;
import org.hyperkv.lsmplus.journal.JournalReplayPoint;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;

import java.nio.charset.StandardCharsets;
import java.util.List;
import java.util.Map;

import static org.junit.jupiter.api.Assertions.*;

class MemoryTableTest {

    private MemoryTable table;
    private JournalReplayPoint replayPoint;

    @BeforeEach
    void setUp() {
        table = new MemoryTable(1024 * 1024);
        replayPoint = new JournalReplayPoint(1, 0, 4096);
    }

    @Test
    void testPutAndGet() {
        IndexKey key = IndexKey.orderedBytes("test-key".getBytes(StandardCharsets.UTF_8));
        IndexValue value = IndexValue.normal("test-value".getBytes(StandardCharsets.UTF_8));

        table.put(key, value, replayPoint);

        IndexValue retrieved = table.get(key);
        assertNotNull(retrieved);
        assertFalse(retrieved.isTombstone());
        assertArrayEquals(value.getValueData(), retrieved.getValueData());
    }

    @Test
    void testDelete() {
        IndexKey key = IndexKey.orderedBytes("delete-key".getBytes(StandardCharsets.UTF_8));
        IndexValue value = IndexValue.normal("delete-value".getBytes(StandardCharsets.UTF_8));

        table.put(key, value, replayPoint);
        assertNotNull(table.get(key));

        table.delete(key, replayPoint);

        IndexValue retrieved = table.get(key);
        assertNotNull(retrieved);
        assertTrue(retrieved.isTombstone());
    }

    @Test
    void testRangeQuery() {
        for (int i = 0; i < 10; i++) {
            IndexKey key = IndexKey.orderedBytes(("key-" + String.format("%02d", i)).getBytes(StandardCharsets.UTF_8));
            IndexValue value = IndexValue.normal(("value-" + i).getBytes(StandardCharsets.UTF_8));
            table.put(key, value, replayPoint);
        }

        IndexKey start = IndexKey.orderedBytes("key-03".getBytes(StandardCharsets.UTF_8));
        IndexKey end = IndexKey.orderedBytes("key-07".getBytes(StandardCharsets.UTF_8));

        List<Map.Entry<IndexKey, IndexValue>> results = table.rangeQuery(start, end);

        assertEquals(4, results.size());
    }

    @Test
    void testRangeQueryAll() {
        for (int i = 0; i < 5; i++) {
            IndexKey key = IndexKey.orderedBytes(("key-" + i).getBytes(StandardCharsets.UTF_8));
            IndexValue value = IndexValue.normal(("value-" + i).getBytes(StandardCharsets.UTF_8));
            table.put(key, value, replayPoint);
        }

        List<Map.Entry<IndexKey, IndexValue>> results = table.rangeQuery(null, null);

        assertEquals(5, results.size());
    }

    @Test
    void testSeal() {
        assertFalse(table.isSealed());

        table.seal();

        assertTrue(table.isSealed());
    }

    @Test
    void testPutAfterSealThrows() {
        IndexKey key = IndexKey.orderedBytes("key".getBytes(StandardCharsets.UTF_8));
        IndexValue value = IndexValue.normal("value".getBytes(StandardCharsets.UTF_8));

        table.seal();

        assertThrows(IllegalStateException.class, () -> table.put(key, value, replayPoint));
    }

    @Test
    void testShouldSeal() {
        MemoryTable smallTable = new MemoryTable(100);

        assertFalse(smallTable.shouldSeal());

        IndexKey key = IndexKey.orderedBytes("key".getBytes(StandardCharsets.UTF_8));
        IndexValue value = IndexValue.normal(new byte[200]);
        smallTable.put(key, value, replayPoint);

        assertTrue(smallTable.shouldSeal());
    }

    @Test
    void testTombstone() {
        IndexKey key = IndexKey.orderedBytes("tombstone-key".getBytes(StandardCharsets.UTF_8));
        IndexValue value = IndexValue.normal("value".getBytes(StandardCharsets.UTF_8));

        table.put(key, value, replayPoint);
        table.delete(key, replayPoint);

        IndexValue retrieved = table.get(key);
        assertNotNull(retrieved);
        assertTrue(retrieved.isTombstone());
    }

    @Test
    void testReplayPoints() {
        IndexKey key1 = IndexKey.orderedBytes("key1".getBytes(StandardCharsets.UTF_8));
        IndexValue value1 = IndexValue.normal("value1".getBytes(StandardCharsets.UTF_8));
        JournalReplayPoint point1 = new JournalReplayPoint(1, 0, 4096);

        IndexKey key2 = IndexKey.orderedBytes("key2".getBytes(StandardCharsets.UTF_8));
        IndexValue value2 = IndexValue.normal("value2".getBytes(StandardCharsets.UTF_8));
        JournalReplayPoint point2 = new JournalReplayPoint(1, 0, 8192);

        table.put(key1, value1, point1);
        table.put(key2, value2, point2);

        assertEquals(point1, table.getFirstReplayPoint());
        assertEquals(point2, table.getLastReplayPoint());
    }

    @Test
    void testEntryCount() {
        assertEquals(0, table.getEntryCount());

        for (int i = 0; i < 5; i++) {
            IndexKey key = IndexKey.orderedBytes(("key-" + i).getBytes(StandardCharsets.UTF_8));
            IndexValue value = IndexValue.normal(("value-" + i).getBytes(StandardCharsets.UTF_8));
            table.put(key, value, replayPoint);
        }

        assertEquals(5, table.getEntryCount());
    }

    @Test
    void testCurrentSize() {
        int initialSize = table.getCurrentSize();

        IndexKey key = IndexKey.orderedBytes("size-key".getBytes(StandardCharsets.UTF_8));
        IndexValue value = IndexValue.normal(new byte[100]);
        table.put(key, value, replayPoint);

        assertTrue(table.getCurrentSize() > initialSize);
    }

    @Test
    void testOverwrite() {
        IndexKey key = IndexKey.orderedBytes("overwrite-key".getBytes(StandardCharsets.UTF_8));
        IndexValue value1 = IndexValue.normal("value1".getBytes(StandardCharsets.UTF_8));
        IndexValue value2 = IndexValue.normal("value2".getBytes(StandardCharsets.UTF_8));

        table.put(key, value1, replayPoint);
        table.put(key, value2, replayPoint);

        IndexValue retrieved = table.get(key);
        assertArrayEquals(value2.getValueData(), retrieved.getValueData());
        assertEquals(1, table.getEntryCount());
    }

    @Test
    void testGetRange() {
        for (int i = 0; i < 10; i++) {
            IndexKey key = IndexKey.orderedBytes(("key-" + String.format("%02d", i)).getBytes(StandardCharsets.UTF_8));
            IndexValue value = IndexValue.normal(("value-" + i).getBytes(StandardCharsets.UTF_8));
            table.put(key, value, replayPoint);
        }

        IndexKey start = IndexKey.orderedBytes("key-02".getBytes(StandardCharsets.UTF_8));
        IndexKey end = IndexKey.orderedBytes("key-05".getBytes(StandardCharsets.UTF_8));

        var range = table.getRange(start, end);

        assertEquals(3, range.size());
    }

    @Test
    void testNullKeyThrows() {
        IndexValue value = IndexValue.normal("value".getBytes(StandardCharsets.UTF_8));
        assertThrows(IllegalArgumentException.class, () -> table.put(null, value, replayPoint));
    }

    @Test
    void testNullValueThrows() {
        IndexKey key = IndexKey.orderedBytes("key".getBytes(StandardCharsets.UTF_8));
        assertThrows(IllegalArgumentException.class, () -> table.put(key, null, replayPoint));
    }

    @Test
    void testRangeQueryWithOptions() {
        for (int i = 0; i < 10; i++) {
            IndexKey key = IndexKey.orderedBytes(("key-" + String.format("%02d", i)).getBytes(StandardCharsets.UTF_8));
            IndexValue value = IndexValue.normal(("value-" + i).getBytes(StandardCharsets.UTF_8));
            table.put(key, value, replayPoint);
        }

        IndexKey start = IndexKey.orderedBytes("key-03".getBytes(StandardCharsets.UTF_8));
        IndexKey end = IndexKey.orderedBytes("key-07".getBytes(StandardCharsets.UTF_8));

        RangeQueryOptions options = RangeQueryOptions.builder()
                .start(start)
                .end(end)
                .build();

        RangeQueryResult result = table.rangeQuery(options);

        assertEquals(4, result.getCount());
        assertFalse(result.hasMore());
    }

    @Test
    void testRangeQueryWithLimit() {
        for (int i = 0; i < 10; i++) {
            IndexKey key = IndexKey.orderedBytes(("key-" + String.format("%02d", i)).getBytes(StandardCharsets.UTF_8));
            IndexValue value = IndexValue.normal(("value-" + i).getBytes(StandardCharsets.UTF_8));
            table.put(key, value, replayPoint);
        }

        RangeQueryOptions options = RangeQueryOptions.builder()
                .limit(5)
                .build();

        RangeQueryResult result = table.rangeQuery(options);

        assertEquals(5, result.getCount());
        assertTrue(result.hasMore());
    }

    @Test
    void testRangeQueryWithPrefix() {
        table.put(IndexKey.orderedBytes("user:001".getBytes(StandardCharsets.UTF_8)), 
                  IndexValue.normal("value1".getBytes(StandardCharsets.UTF_8)), replayPoint);
        table.put(IndexKey.orderedBytes("user:002".getBytes(StandardCharsets.UTF_8)), 
                  IndexValue.normal("value2".getBytes(StandardCharsets.UTF_8)), replayPoint);
        table.put(IndexKey.orderedBytes("admin:001".getBytes(StandardCharsets.UTF_8)), 
                  IndexValue.normal("value3".getBytes(StandardCharsets.UTF_8)), replayPoint);
        table.put(IndexKey.orderedBytes("user:003".getBytes(StandardCharsets.UTF_8)), 
                  IndexValue.normal("value4".getBytes(StandardCharsets.UTF_8)), replayPoint);

        RangeQueryOptions options = RangeQueryOptions.prefix("user:".getBytes(StandardCharsets.UTF_8));

        RangeQueryResult result = table.rangeQuery(options);

        assertEquals(3, result.getCount());
        for (Map.Entry<IndexKey, IndexValue> entry : result.getEntries()) {
            String keyStr = new String(entry.getKey().getKeyData(), StandardCharsets.UTF_8);
            assertTrue(keyStr.startsWith("user:"));
        }
    }

    @Test
    void testRangeQueryWithPrefixAndLimit() {
        for (int i = 0; i < 10; i++) {
            table.put(IndexKey.orderedBytes(("user:" + String.format("%03d", i)).getBytes(StandardCharsets.UTF_8)), 
                      IndexValue.normal(("value-" + i).getBytes(StandardCharsets.UTF_8)), replayPoint);
        }

        RangeQueryOptions options = RangeQueryOptions.builder()
                .prefix("user:".getBytes(StandardCharsets.UTF_8))
                .limit(3)
                .build();

        RangeQueryResult result = table.rangeQuery(options);

        assertEquals(3, result.getCount());
        assertTrue(result.hasMore());
    }

    @Test
    void testGetFirstAndLastEntry() {
        table.put(IndexKey.orderedBytes("key-b".getBytes(StandardCharsets.UTF_8)), 
                  IndexValue.normal("value-b".getBytes(StandardCharsets.UTF_8)), replayPoint);
        table.put(IndexKey.orderedBytes("key-a".getBytes(StandardCharsets.UTF_8)), 
                  IndexValue.normal("value-a".getBytes(StandardCharsets.UTF_8)), replayPoint);
        table.put(IndexKey.orderedBytes("key-c".getBytes(StandardCharsets.UTF_8)), 
                  IndexValue.normal("value-c".getBytes(StandardCharsets.UTF_8)), replayPoint);

        Map.Entry<IndexKey, IndexValue> first = table.getFirstEntry();
        Map.Entry<IndexKey, IndexValue> last = table.getLastEntry();

        assertNotNull(first);
        assertNotNull(last);
        assertEquals("key-a", new String(first.getKey().getKeyData(), StandardCharsets.UTF_8));
        assertEquals("key-c", new String(last.getKey().getKeyData(), StandardCharsets.UTF_8));
    }
}