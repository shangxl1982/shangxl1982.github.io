# Epic 6: Page

## Overview

Implement the Page data structure for B+Tree. Pages are the fundamental units of storage in the B+Tree, with Leaf pages storing data and Index pages storing navigation information.

## Goals

1. Implement Page base class with common functionality
2. Implement LeafPage with key-value pairs
3. Implement IndexPage with key-location pairs
4. Implement page split logic
5. Implement page serialization

## Scope

### In Scope
- Page base class
- LeafPage implementation
- IndexPage implementation
- Page split/merge logic
- Page serialization

### Out of Scope
- Page caching (future enhancement)
- Page compression (future enhancement)

## Technical Design

### Page Format

```
┌─────────────────────────────────────────────────────────────┐
│                    Page Format                               │
├─────────────────────────────────────────────────────────────┤
│  Page Header (fixed size)                                    │
│  ├── PageType (4 bytes)                                      │
│  ├── PageID (4 bytes)                                        │
│  ├── MaxSize (4 bytes)                                       │
│  ├── UsedSize (4 bytes)                                      │
│  └── EntryCount (4 bytes)                                    │
├─────────────────────────────────────────────────────────────┤
│  Entries (variable size)                                     │
│  └── Key-Value pairs (Leaf) or Key-Location pairs (Index)   │
└─────────────────────────────────────────────────────────────┘
```

### Leaf Page Format

```
┌─────────────────────────────────────────────────────────────┐
│                    Leaf Page                                 │
├─────────────────────────────────────────────────────────────┤
│  Header (20 bytes)                                           │
│  ├── PageType = LEAF                                         │
│  ├── PageID                                                  │
│  ├── MaxSize (e.g., 64KB)                                    │
│  ├── UsedSize                                                │
│  └── EntryCount                                              │
├─────────────────────────────────────────────────────────────┤
│  Entries (variable)                                          │
│  └── [Key, Value] pairs                                      │
└─────────────────────────────────────────────────────────────┘
```

### Index Page Format

```
┌─────────────────────────────────────────────────────────────┐
│                    Index Page                                │
├─────────────────────────────────────────────────────────────┤
│  Header (20 bytes)                                           │
│  ├── PageType = INDEX                                        │
│  ├── PageID                                                  │
│  ├── MaxSize (e.g., 64KB)                                    │
│  ├── UsedSize                                                │
│  └── EntryCount                                              │
├─────────────────────────────────────────────────────────────┤
│  Entries (variable)                                          │
│  └── [Key, Location] pairs                                   │
│      (Location = SegmentLocation to child page)             │
└─────────────────────────────────────────────────────────────┘
```

## Stories

| Story ID | Name | Priority |
|----------|------|----------|
| 6-1 | Implement Page Base Class | High |
| 6-2 | Implement LeafPage | High |
| 6-3 | Implement IndexPage | High |
| 6-4 | Implement Page Split | High |
| 6-5 | Unit Tests for Pages | High |

## Dependencies

- Epic 1: Protobuf Serialization
- Epic 2: Data Integrity

## Acceptance Criteria

- [ ] Page can be created and serialized
- [ ] LeafPage stores key-value pairs
- [ ] IndexPage stores key-location pairs
- [ ] Page split works correctly
- [ ] All unit tests pass

## Risk Mitigation

| Risk | Mitigation |
|------|------------|
| Page overflow | Split page when full |
| Partial writes | CRC32 validation |
| Memory corruption | Validate page header |
