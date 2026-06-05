# Story 6-1: Implement Page Base Class

## Story

As a developer, I want to implement the Page base class so that common page functionality can be shared.

## Acceptance Criteria

- [ ] Page base class created
- [ ] Header fields: PageType, PageID, MaxSize, UsedSize, EntryCount
- [ ] toByteArray() method serializes page
- [ ] fromByteArray() method parses page
- [ ] addEntry() method adds entry to page
- [ ] getEntries() method returns all entries
- [ ] hasSpace() method checks if page has space
- [ ] Unit tests verify all methods

## Technical Details

### Class: Page

```java
package org.hyperkv.lsmplus.core.bplustree.page;

public abstract class Page {
    public static final int HEADER_SIZE = 20;
    
    protected final PageType pageType;
    protected final int pageId;
    protected final int maxSize;
    protected int usedSize;
    protected int entryCount;
    protected final List<KeyValuePair> entries;
    
    public Page(PageType type, int pageId, int maxSize);
    public abstract byte[] toByteArray();
    public static Page fromByteArray(byte[] data);
    public void addEntry(KeyValuePair entry);
    public List<KeyValuePair> getEntries();
    public boolean hasSpace(KeyValuePair entry);
    public int getUsedSize();
    public int getEntryCount();
}
```

### Header Format

```
Offset 0-3:   pageType (4 bytes)
Offset 4-7:   pageId (4 bytes)
Offset 8-11:  maxSize (4 bytes)
Offset 12-15: usedSize (4 bytes)
Offset 16-19: entryCount (4 bytes)
```

## Testing

- testCreatePage()
- testToByteArray()
- testFromByteArray()
- testAddEntry()
- testHasSpace()
- testFixedSize20Bytes()

## Effort Estimate

1 day
