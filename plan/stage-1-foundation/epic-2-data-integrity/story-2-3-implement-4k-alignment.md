# Story 2-3: Implement 4K Alignment

## Story

As a developer, I want to implement 4K alignment utilities so that all writes are properly aligned.

## Acceptance Criteria

- [ ] alignTo4K(int size) method returns 4K-aligned size
- [ ] is4KAligned(int size) method checks alignment
- [ ] calculatePadding(int size) method returns padding needed
- [ ] Unit tests verify alignment correctness
- [ ] Performance overhead is minimal

## Technical Details

```java
package org.hyperkv.lsmplus.utils;

public class AlignmentUtil {
    public static final int ALIGNMENT = 4096;
    
    public static int alignTo4K(int size) {
        return (size + ALIGNMENT - 1) & ~(ALIGNMENT - 1);
    }
    
    public static boolean is4KAligned(int size) {
        return (size & (ALIGNMENT - 1)) == 0;
    }
    
    public static int calculatePadding(int size) {
        return alignTo4K(size) - size;
    }
}
```

## Testing

- testAlignTo4K()
- testIs4KAligned()
- testCalculatePadding()
- testEdgeCases() // 0, 4096, 4097, 8192

## Effort Estimate

0.5 day
