# Epic 2: Data Integrity

## Overview

Implement data integrity protection mechanisms including Write Item format, CRC32 validation, and 4K alignment. This ensures data written to disk can be verified and recovered.

## Goals

1. Implement Write Item format (Header + Body + CRC32 + Padding)
2. Implement CRC32 calculation and validation
3. Implement 4K alignment for all writes
4. Implement partial write detection
5. Create data integrity utilities

## Scope

### In Scope
- Write Item format implementation
- CRC32 calculation and validation
- 4K alignment utilities
- Magic number validation
- Partial write detection

### Out of Scope
- Compression (future enhancement)
- Encryption (future enhancement)

## Technical Design

### Write Item Format

```
┌─────────────────────────────────────────────────────────────┐
│                    Write Item Format                         │
├─────────────────────────────────────────────────────────────┤
│  Header (8 bytes)                                            │
│  ├── Magic (2 bytes): 0xABCD                                 │
│  ├── Version (2 bytes): 0x0001                               │
│  └── Body Length (4 bytes): N                                │
├─────────────────────────────────────────────────────────────┤
│  Body (N bytes)                                              │
│  └── Serialized Protobuf data                                │
├─────────────────────────────────────────────────────────────┤
│  CRC32 (4 bytes)                                             │
│  └── CRC32 of Header + Body                                  │
├─────────────────────────────────────────────────────────────┤
│  Padding (variable)                                          │
│  └── Align to 4K boundary                                    │
└─────────────────────────────────────────────────────────────┘
```

### Key Constants

| Constant | Value | Description |
|----------|-------|-------------|
| MAGIC | 0xABCD | Write Item identifier |
| VERSION | 0x0001 | Format version |
| ALIGNMENT | 4096 | 4K alignment |
| HEADER_SIZE | 8 | Header size in bytes |
| CRC32_SIZE | 4 | CRC32 size in bytes |

## Stories

| Story ID | Name | Priority |
|----------|------|----------|
| 2-1 | Implement WriteItem Class | High |
| 2-2 | Implement CRC32 Utilities | High |
| 2-3 | Implement 4K Alignment | High |
| 2-4 | Implement Magic Validation | High |
| 2-5 | Unit Tests for Data Integrity | High |

## Dependencies

- Epic 1: Protobuf Serialization (for body serialization)

## Acceptance Criteria

- [ ] WriteItem class can create and parse write items
- [ ] CRC32 validation works correctly
- [ ] All writes are 4K aligned
- [ ] Partial writes can be detected
- [ ] Magic number validation works
- [ ] All unit tests pass

## Risk Mitigation

| Risk | Mitigation |
|------|------------|
| CRC32 collision | Accept very low probability, document limitation |
| Performance impact | Profile and optimize if needed |
| Alignment overhead | Document space overhead in design |
