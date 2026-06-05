# Story 4-4: Implement Region Management

## Story

As a developer, I want to implement region management so that Journal can organize writes into regions.

## Acceptance Criteria

- [ ] JournalRegion class created
- [ ] Region index file created and maintained
- [ ] Region can add entries
- [ ] Region can persist index
- [ ] Region can load index from file
- [ ] Region supports multiple chunks
- [ ] Unit tests verify all methods

## Technical Details

### Class: JournalRegion

```java
package org.hyperkv.lsmplus.journal;

public class JournalRegion {
    private final int major;
    private final int minor;
    private final File indexFile;
    private final List<SegmentLocation> entries;
    
    public JournalRegion(int major, int minor, File indexFile);
    public void addEntry(SegmentLocation location);
    public List<SegmentLocation> getEntries();
    public void persist();
    public static JournalRegion load(File indexFile);
}
```

### Region Index Format

```
Magic (4 bytes): 0x12345678
Format Version (4 bytes): 0x0001
Region Major (8 bytes)
Region Minor (8 bytes)
Entry Count (4 bytes)
Entries (Entry Count * 24 bytes each)
```

## Testing

- testCreateRegion()
- testAddEntry()
- testPersistAndLoad()
- testMultipleEntries()
- testRegionRecovery()

## Effort Estimate

1 day
