# Story 6-4: Implement Page Split

## Story

As a developer, I want to implement the page split logic so that pages can grow beyond their maximum size.

## Acceptance Criteria

- [ ] Split method creates two pages
- [ ] Median key moved to parent
- [ ] Left page contains keys < median
- [ ] Right page contains keys >= median
- [ ] Page pointers updated correctly
- [ ] Unit tests verify all methods

## Technical Details

### Split Algorithm

```
1. Find median entry
2. Create new right page
3. Move entries >= median to right page
4. Update left page used size
5. Return right page and median key
```

### Implementation

```java
public class Page {
    public Page split() {
        int medianIndex = entries.size() / 2;
        KeyValuePair medianEntry = entries.get(medianIndex);
        
        Page rightPage = createRightPage(medianIndex);
        
        // Update left page
        entries = entries.subList(0, medianIndex);
        updateUsedSize();
        
        return rightPage;
    }
}
```

## Testing

- testSplitLeafPage()
- testSplitIndexPage()
- testMedianKeyCorrect()
- testLeftPageEntries()
- testRightPageEntries()

## Effort Estimate

1.5 days
