# Story 7-2: Implement PageManager

## Story

As a developer, I want to implement the PageManager class so that pages can be loaded and saved.

## Acceptance Criteria

- [ ] PageManager class created
- [ ] loadPage(PageID) method loads page from storage
- [ ] savePage(Page) method saves page to storage
- [ ] allocatePage() method allocates new page
- [ ] deletePage(PageID) method deletes page
- [ ] Unit tests verify all methods

## Technical Details

### Class: PageManager

```java
package org.hyperkv.lsmplus.core.bplustree;

public class PageManager {
    private final ChunkManager chunkManager;
    private final Map<Integer, Page> pageCache;
    
    public PageManager(ChunkManager chunkManager);
    public Page loadPage(int pageId);
    public void savePage(Page page);
    public int allocatePage(PageType type);
    public void deletePage(int pageId);
}
```

### Page Storage

```
Page stored as WriteItem in Chunk
- ChunkType: INDEX or LEAF
- Location: SegmentLocation
- Page ID: embedded in page data
```

## Testing

- testLoadAndSavePage()
- testAllocatePage()
- testDeletePage()
- testMultiplePages()
- testPageRecovery()

## Effort Estimate

1.5 days
