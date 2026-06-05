# Implementation Plan: LSM Plus Utility Classes

**Branch**: `012-lsmplus-utils` | **Date**: 2026-04-17 | **Spec**: [spec.md](file:///home/wisefox/git/hyperkvstore/specs/012-lsmplus-utils/spec.md)
**Input**: Feature specification from `/specs/012-lsmplus-utils/spec.md`

## Summary

Implement common utility classes and helper functions shared across all LSM tree components. Provides byte array utilities (comparison, concatenation, encoding), checksum utilities (CRC32, MD5, SHA-256), custom exception classes, thread and concurrency utilities, I/O utilities for file operations, and time/date utilities for timestamp handling.

## Technical Context

**Language/Version**: Java 25  
**Primary Dependencies**: JUnit 6.0.0  
**Storage**: N/A (utility functions)  
**Testing**: JUnit 5 with Mockito  
**Target Platform**: Linux server (JVM)  
**Project Type**: Library  
**Performance Goals**: <100ns/KB byte comparison, <1ms/MB CRC32, <10μs exception creation  
**Constraints**: Thread-safe, minimal overhead, zero resource leaks  
**Scale/Scope**: 6 utility classes (ByteArrayUtils, ChecksumUtils, LSMException, ThreadUtils, IOUtils, TimeUtils)  

## Constitution Check

✅ **Library-First**: Standalone utility library
✅ **Test-First**: TDD with comprehensive unit tests
✅ **Simplicity**: Focused on common utilities
✅ **Observability**: N/A (utility functions)
✅ **Versioning**: N/A (stateless utilities)

## Project Structure

```text
lsmplus-utils/
├── src/
│   ├── main/java/org/hyperkv/lsmplus/utils/
│   │   ├── ByteArrayUtils.java        # Byte array operations
│   │   ├── ChecksumUtils.java         # CRC32, MD5, SHA-256
│   │   ├── LSMException.java          # Base exception class
│   │   ├── ThreadUtils.java           # Thread pools, locks
│   │   ├── IOUtils.java               # File I/O utilities
│   │   └── TimeUtils.java             # Timestamp utilities
│   └── test/java/org/hyperkv/lsmplus/utils/
│       ├── ByteArrayUtilsTest.java
│       ├── ChecksumUtilsTest.java
│       ├── ThreadUtilsTest.java
│       └── IOUtilsTest.java
└── build.gradle.kts
```

## Phase 0: Research & Design Decisions

### Research Tasks

1. **Byte Array Comparison**: Lexicographic comparison with type awareness
2. **Checksum Performance**: CRC32 for speed, SHA-256 for security
3. **Exception Hierarchy**: Base exception with specific subclasses
4. **Thread Pool Management**: Cached or fixed thread pools
5. **I/O Utilities**: Buffered I/O with proper resource cleanup

### Design Decisions

1. **Static Methods**: All utilities as static methods
2. **Thread-Safe**: All methods thread-safe by default
3. **Resource Management**: Use try-with-resources pattern
4. **Null Handling**: Throw IllegalArgumentException for nulls
5. **Documentation**: Comprehensive Javadoc with examples

## Phase 1: Design & Contracts

**Public API**:
- `ByteArrayUtils.compare(byte[], byte[])` - Compare byte arrays
- `ByteArrayUtils.concat(byte[]...)` - Concatenate arrays
- `ChecksumUtils.crc32(byte[])` - Compute CRC32
- `ChecksumUtils.md5(byte[])` - Compute MD5
- `IOUtils.readFile(String)` - Read file to byte array
- `IOUtils.writeFile(String, byte[])` - Write byte array to file
- `TimeUtils.currentTimestamp()` - Get current timestamp

## Dependencies

**External**: JUnit 6.0.0, Mockito 5.11.0

## Success Metrics

- ✅ Byte comparison <100ns/KB
- ✅ CRC32 computation <1ms/MB
- ✅ Exception creation <10μs
- ✅ Thread pool submission <1μs
- ✅ File I/O >500MB/s
- ✅ Zero resource leaks
