# Story 2-2: Implement CRC32 Utilities

## Story

As a developer, I want to implement CRC32 utilities so that data integrity can be verified.

## Acceptance Criteria

- [ ] CRC32Util class created
- [ ] calculate(byte[]) method computes CRC32
- [ ] calculate(byte[], offset, length) method supports partial arrays
- [ ] validate(byte[], expectedCrc32) method verifies CRC32
- [ ] CRC32 is calculated using standard CRC32 algorithm
- [ ] Unit tests verify correctness

## Technical Details

### Class: CRC32Util

```java
package org.hyperkv.lsmplus.utils;

public class CRC32Util {
    
    public static int calculate(byte[] data);
    
    public static int calculate(byte[] data, int offset, int length);
    
    public static boolean validate(byte[] data, int expectedCrc32);
    
    public static boolean validate(byte[] data, int offset, int length, int expectedCrc32);
}
```

## Testing

- testCalculateSimpleData()
- testCalculateEmptyData()
- testCalculatePartialArray()
- testValidateCorrectCRC32()
- testValidateIncorrectCRC32()
- testKnownValues() // Test against known CRC32 values

## Effort Estimate

0.5 day
