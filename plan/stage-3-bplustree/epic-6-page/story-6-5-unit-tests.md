# Story 6-5: Unit Tests for Pages

## Story

As a developer, I want comprehensive unit tests for Pages so that all components are verified.

## Acceptance Criteria

- [ ] PageTest covers all page methods
- [ ] LeafPageTest covers all leaf page methods
- [ ] IndexPageTest covers all index page methods
- [ ] PageSplitTest covers all split methods
- [ ] Integration test for complete page operations
- [ ] Test coverage > 90%

## Technical Details

### Test Structure

```
src/test/java/org/hyperkv/lsmplus/core/bplustree/page/
├── PageTest.java
├── LeafPageTest.java
├── IndexPageTest.java
├── PageSplitTest.java
└── PagesIntegrationTest.java
```

### Test Cases

1. **PageTest**
   - testCreatePage()
   - testToByteArray()
   - testFromByteArray()
   - testAddEntry()
   - testHasSpace()
   - testFixedSize20Bytes()

2. **LeafPageTest**
   - testPutAndGet()
   - testDelete()
   - testRangeQuery()
   - testMultipleEntries()
   - testSerialization()

3. **IndexPageTest**
   - testPutAndGetChildLocation()
   - testRangeQuery()
   - testMultipleEntries()
   - testSerialization()
   - testLeftmostChild()

4. **PageSplitTest**
   - testSplitLeafPage()
   - testSplitIndexPage()
   - testMedianKeyCorrect()
   - testLeftPageEntries()
   - testRightPageEntries()

5. **PagesIntegrationTest**
   - testCompletePageOperations()
   - testMultipleSplits()
   - testPageRecovery()

## Effort Estimate

2 days
