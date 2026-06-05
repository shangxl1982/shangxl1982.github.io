# BPlusTree Refactoring - Separate ReaderTree and PersistingTree

**Date:** 2026-05-08

## Summary

Refactored the BPlusTree.java implementation to separate concerns by creating two specialized classes:
- **ReaderTree**: Handles all read operations (search, rangeQuery, rangeIterator)
- **PersistingTree**: Handles all write/persist operations (getWriterRoot, setWriterRoot, promoteRoot, etc.)

The BPlusTree class now acts as a coordinator/facade that provides access to both specialized classes while maintaining backward compatibility.

## Changes Made

### 1. Created ReaderTree Class
**File:** `lsmplus-kvstore/src/main/java/org/hyperkv/lsmplus/bplustree/ReaderTree.java`

**Responsibilities:**
- Search operations (search, searchInTree)
- Range query operations (rangeQuery with multiple overloads)
- Iterator operations (rangeIterator)
- Reader root management (getReaderRoot, setReaderRoot)
- Performance monitoring for read operations

**Key Features:**
- Thread-safe reader root using AtomicReference
- Optimized range query with context management
- Iterator implementation for lazy loading
- Performance counters for monitoring

### 2. Created PersistingTree Class
**File:** `lsmplus-kvstore/src/main/java/org/hyperkv/lsmplus/bplustree/PersistingTree.java`

**Responsibilities:**
- Writer root management (getWriterRoot, setWriterRoot)
- Root promotion (promoteRoot)
- Metadata management (version, height, entry count)
- Tree restoration (startFrom)
- Page ID management
- Configuration access (capacity config, page sizes)

**Key Features:**
- Manages writer root and root location
- Coordinates with ReaderTree during root promotion
- Handles tree metadata persistence
- Page ID allocation and restoration

### 3. Refactored BPlusTree Class
**File:** `lsmplus-kvstore/src/main/java/org/hyperkv/lsmplus/bplustree/BPlusTree.java`

**Changes:**
- Now contains ReaderTree and PersistingTree as composition
- Delegates all read operations to ReaderTree
- Delegates all write operations to PersistingTree
- Maintains backward compatibility with existing API
- Added getter methods for ReaderTree and PersistingTree

**Benefits:**
- Clear separation of concerns
- Easier to understand and maintain
- Better testability
- No breaking changes to existing code

## Testing

All existing tests pass without modification:
- TreeDumperTest: ✓ PASSED
- BPlusTreeTest: ✓ PASSED
- BPlusTreeFullIntegrationTest: ✓ PASSED
- All lsmplus-kvstore tests: ✓ PASSED

## Impact Analysis

### No Changes Required
- **TreeDumper**: Works seamlessly through BPlusTree's facade methods
- **KVStore**: No changes needed, uses BPlusTree as before
- **Tests**: All tests pass without modification

### Benefits
1. **Better Separation of Concerns**: Read and write operations are now clearly separated
2. **Improved Maintainability**: Each class has a focused responsibility
3. **Enhanced Testability**: Can test read and write operations independently
4. **No Breaking Changes**: Existing code continues to work without modification
5. **Clearer Architecture**: The relationship between reader and writer is now explicit

## Architecture

```
BPlusTree (Facade/Coordinator)
├── ReaderTree (Read Operations)
│   ├── search()
│   ├── rangeQuery()
│   ├── rangeIterator()
│   └── readerRoot management
└── PersistingTree (Write Operations)
    ├── getWriterRoot() / setWriterRoot()
    ├── promoteRoot()
    ├── metadata management
    └── writerRoot management
```

## Future Considerations

1. **Independent Evolution**: ReaderTree and PersistingTree can now evolve independently
2. **Performance Optimization**: Can optimize read and write paths separately
3. **Testing**: Can create more focused unit tests for each class
4. **Documentation**: Can document read vs write operations more clearly

## Conclusion

The refactoring successfully separates the read and write concerns of the BPlusTree while maintaining backward compatibility. All tests pass, and the code is now more maintainable and easier to understand.
