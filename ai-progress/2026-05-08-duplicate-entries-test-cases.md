# Test Cases Added for Duplicate Entries Bug - 2026-05-08

## Summary
Added comprehensive test cases to prevent regression of the duplicate entries bug in page splits.

## Test Cases Added

### 1. testNoDuplicateEntriesAfterPageSplit
**Purpose:** Verify that a single page split doesn't create duplicate entries in the parent.

**Scenario:**
1. Insert 50 keys to create initial tree
2. Insert 100 more keys to trigger page splits
3. Verify root page has no duplicate keys
4. Verify all 150 keys are searchable

**Key Assertions:**
- Root page is an index page (not leaf)
- Each key in root appears only once
- All keys are searchable and found

**Code:**
```java
Set<IndexKey> uniqueKeys = new HashSet<>();
for (IndexPair entry : rootEntries) {
    IndexKey key = entry.key();
    assertFalse(uniqueKeys.contains(key), 
        "Duplicate key found in root page: " + key);
    uniqueKeys.add(key);
}
```

### 2. testNoDuplicateEntriesAfterMultipleSplits
**Purpose:** Verify that multiple consecutive page splits don't create duplicates.

**Scenario:**
1. Insert 20 keys to create initial tree
2. Insert 5 batches of 30 keys each (150 total)
3. After each batch, verify no duplicates
4. Verify all 170 keys are searchable

**Key Features:**
- Tests multiple rounds of splits
- Checks for duplicates after each batch
- Uses smaller page size (512 bytes) to trigger more splits

**Code:**
```java
for (int batch = 0; batch < 5; batch++) {
    // Insert batch of keys
    // ...
    // Verify no duplicates after each batch
    verifyNoDuplicateEntries(tree);
}
```

### 3. testNoDuplicateEntriesAfterMinKeyChange
**Purpose:** Verify that min key changes during splits don't create duplicates.

**Scenario:**
1. Insert keys 10-29 (min key = key00010)
2. Insert keys 0-9 and 30-49 (new min key = key00000)
3. This causes min key change during split
4. Verify no duplicates in parent pages
5. Verify all 50 keys are searchable

**Key Features:**
- Tests the specific scenario where min key changes
- This is the exact scenario that would have caused duplicates before the fix
- Verifies DELETE operation is applied correctly

**Code:**
```java
// First batch: keys 10-29 (min key = key00010)
// Second batch: keys 0-9, 30-49 (new min key = key00000)
// This triggers min key change during split
verifyNoDuplicateEntries(tree);
```

## Helper Methods

### verifyNoDuplicateEntries(BPlusTree tree)
- Entry point for duplicate verification
- Checks if root exists and is index page
- Delegates to verifyNoDuplicateEntriesInPage

### verifyNoDuplicateEntriesInPage(Page page, int level)
- Recursively verifies no duplicates in index pages
- Compares total entries vs unique keys
- Provides detailed error messages with level and counts

**Code:**
```java
List<IndexPair> entries = page.getAllEntries();
Set<IndexKey> uniqueKeys = new HashSet<>();

for (IndexPair entry : entries) {
    IndexKey key = entry.key();
    assertFalse(uniqueKeys.contains(key), 
        String.format("Duplicate key found in index page at level %d: %s. Total entries: %d, Unique keys: %d",
            level, key, entries.size(), uniqueKeys.size()));
    uniqueKeys.add(key);
}

assertEquals(entries.size(), uniqueKeys.size(), 
    String.format("Page at level %d has duplicate entries. Total: %d, Unique: %d",
        level, entries.size(), uniqueKeys.size()));
```

## Test Coverage

### Scenarios Covered
1. ✅ Single page split
2. ✅ Multiple consecutive page splits
3. ✅ Min key change during split
4. ✅ Root page splits
5. ✅ Index page splits at various levels

### Edge Cases Tested
1. ✅ Small page sizes (more frequent splits)
2. ✅ Large number of keys (150-170 keys)
3. ✅ Keys inserted in non-sequential order
4. ✅ Min key changes from key00010 to key00000

## Why These Tests Matter

### Regression Prevention
- These tests would have caught the original bug
- They verify the DELETE operation is applied before PUT
- They catch any future regressions in split logic

### Documentation
- Tests serve as documentation of expected behavior
- Show how page splits should work correctly
- Demonstrate the DELETE-then-PUT pattern

### Confidence
- Running these tests gives confidence in split logic
- Can refactor code without fear of introducing duplicates
- Validates tree structure integrity

## Running the Tests

```bash
# Run all duplicate entry tests
./gradlew :lsmplus-kvstore:test --tests "TreeDumperTest.testNoDuplicateEntries*"

# Run specific test
./gradlew :lsmplus-kvstore:test --tests "TreeDumperTest.testNoDuplicateEntriesAfterPageSplit"

# Run all TreeDumper tests
./gradlew :lsmplus-kvstore:test --tests TreeDumperTest
```

## Test Results
All 27 tests in TreeDumperTest pass, including the 3 new duplicate entry tests.

## Files Modified
- `lsmplus-kvstore/src/test/java/org/hyperkv/lsmplus/bplustree/TreeDumperTest.java`
  - Added 3 new test methods
  - Added 2 helper methods for verification
  - Added necessary imports (IndexPair, Page, HashSet, Set)

## Future Improvements
Could add additional tests for:
- Root split with multiple levels
- Concurrent splits at different levels
- Splits during delete operations
- Splits with very large keys
