# Implementation Plan: LSM Plus API Models

**Branch**: `002-lsmplus-api` | **Date**: 2026-04-17 | **Spec**: [spec.md](file:///home/wisefox/git/hyperkvstore/specs/002-lsmplus-api/spec.md)
**Input**: Feature specification from `/specs/002-lsmplus-api/spec.md`

## Summary

Implement API models and protobuf definitions for LSM tree key-value store, providing type-safe key-value models (IndexKey, IndexValue) with protobuf serialization, common enumeration types, and message formats for journal entries, pages, and metadata structures. This module serves as the foundation for all other LSM Plus components.

## Technical Context

**Language/Version**: Java 25  
**Primary Dependencies**: Google Protocol Buffers 3.34.1, JUnit 6.0.0, Mockito 5.11.0  
**Storage**: N/A (in-memory models with serialization support)  
**Testing**: JUnit 5 with Mockito  
**Target Platform**: Linux server (JVM)  
**Project Type**: Library  
**Performance Goals**: <1μs serialization, <100ns key comparison, 30-50% smaller than JSON  
**Constraints**: Max key size 64KB, max value size 4MB, zero data loss  
**Scale/Scope**: 8 protobuf message types, 8 enumeration types, 2 Java model classes  

## Constitution Check

*GATE: Must pass before Phase 0 research. Re-check after Phase 1 design.*

✅ **Library-First**: This is a standalone library module with clear purpose (API models)
✅ **Test-First**: Will follow TDD approach with comprehensive unit tests
✅ **Simplicity**: Minimal dependencies, focused on data models and serialization
✅ **Observability**: Models include validation and error reporting
✅ **Versioning**: Protobuf reserved fields ensure backward compatibility

## Project Structure

### Documentation (this feature)

```text
specs/002-lsmplus-api/
├── plan.md              # This file
├── research.md          # Phase 0 output
├── data-model.md        # Phase 1 output
├── quickstart.md        # Phase 1 output
├── contracts/           # Phase 1 output
└── tasks.md             # Phase 2 output
```

### Source Code (repository root)

```text
lsmplus-api/
├── src/
│   ├── main/
│   │   ├── java/
│   │   │   └── org/hyperkv/lsmplus/api/
│   │   │       └── model/
│   │   │           ├── IndexKey.java          # Key model with type and comparison
│   │   │           └── IndexValue.java        # Value model with tombstone support
│   │   └── proto/
│   │       ├── common.proto                   # Common enumerations
│   │       ├── keyvalue.proto                 # Key-value messages
│   │       ├── journal.proto                  # Journal entry messages
│   │       ├── page.proto                     # Page structure messages
│   │       └── metadata.proto                 # Chunk and backup metadata
│   └── test/
│       └── java/
│           └── org/hyperkv/lsmplus/api/
│               └── model/
│                   ├── IndexKeyTest.java      # Key model tests
│                   └── IndexValueTest.java    # Value model tests
└── build.gradle.kts                           # Gradle build with protobuf plugin
```

**Structure Decision**: Single library module following existing project structure. Protobuf definitions in `src/main/proto/` with generated Java classes. Model classes in `src/main/java/` wrapping protobuf messages with type-safe API.

## Complexity Tracking

> No constitution violations detected. This is a straightforward library module.

## Phase 0: Research & Design Decisions

### Research Tasks

1. **Protobuf Best Practices for Java**
   - Decision: Use protobuf-java 3.34.1 with builder pattern
   - Rationale: Mature library, excellent performance, wide adoption
   - Alternatives: JSON (slower, larger), MessagePack (less tooling)

2. **Key Comparison Strategy**
   - Decision: Byte array comparison with type-aware ordering
   - Rationale: ORDERED_BYTES uses lexicographic comparison, CUSTOM uses user-defined
   - Alternatives: Hash-based (no ordering), string-based (encoding issues)

3. **Validation Approach**
   - Decision: Validate in model constructors, fail fast
   - Rationale: Prevent invalid data from entering system
   - Alternatives: Validate on serialization (too late), validate on use (inconsistent)

4. **Compression Strategy**
   - Decision: Support multiple algorithms via enum, apply at serialization
   - Rationale: Flexibility for different use cases
   - Alternatives: Single algorithm (inflexible), no compression (inefficient)

5. **Backward Compatibility**
   - Decision: Use reserved field numbers 100-200 for future expansion
   - Rationale: Protobuf best practice for evolution
   - Alternatives: No reserved fields (breaking changes), larger range (wasteful)

### Design Decisions

1. **Immutable Models**: IndexKey and IndexValue are immutable after construction
2. **Factory Methods**: Use static factory methods (orderedBytes(), custom()) for clarity
3. **Protobuf Wrapping**: Java models wrap protobuf messages, not extend them
4. **Validation**: Null checks in constructors, size limits enforced
5. **Comparison**: Implement Comparable<IndexKey> for natural ordering

## Phase 1: Design & Contracts

### Data Model

See [data-model.md](file:///home/wisefox/git/hyperkvstore/specs/002-lsmplus-api/data-model.md) for detailed entity definitions.

**Core Entities**:
- **IndexKey**: Immutable key with type (ORDERED_BYTES/CUSTOM) and byte data
- **IndexValue**: Immutable value with type (NORMAL/TOMBSTONE) and optional byte data
- **KeyProto**: Protobuf message for key serialization
- **ValueProto**: Protobuf message for value serialization with compression support
- **JournalEntryProto**: Protobuf message for write-ahead log entries
- **PageProto**: Protobuf message for B+Tree page storage
- **ChunkMetadataProto**: Protobuf message for chunk lifecycle tracking
- **SegmentLocationProto**: Protobuf message for data location references

### Contracts

**Public API**:
- `IndexKey.orderedBytes(byte[] data)` - Create ordered key
- `IndexKey.custom(byte[] data)` - Create custom key
- `IndexKey.toProto()` - Serialize to protobuf
- `IndexKey.fromProto(KeyProto)` - Deserialize from protobuf
- `IndexValue.normal(byte[] data)` - Create normal value
- `IndexValue.tombstone()` - Create tombstone marker
- `IndexValue.toProto()` - Serialize to protobuf
- `IndexValue.fromProto(ValueProto)` - Deserialize from protobuf

**Serialization Contract**:
- All models MUST serialize to protobuf with zero data loss
- All models MUST support round-trip serialization (serialize → deserialize → equals)
- All protobuf messages MUST be backward compatible across versions

### Quickstart

See [quickstart.md](file:///home/wisefox/git/hyperkvstore/specs/002-lsmplus-api/quickstart.md) for usage examples.

## Phase 2: Implementation Tasks

*Tasks will be generated by `/speckit.tasks` command after plan approval.*

## Dependencies

**Internal Dependencies**: None (this is the foundation module)

**External Dependencies**:
- protobuf-java 3.34.1 (serialization)
- JUnit 6.0.0 (testing)
- Mockito 5.11.0 (mocking in tests)

## Risks & Mitigations

| Risk | Impact | Mitigation |
|------|--------|------------|
| Protobuf schema changes break compatibility | High | Use reserved fields, version all messages |
| Key comparison performance | Medium | Benchmark and optimize byte comparison |
| Large value serialization | Medium | Implement streaming for values >1MB |
| Compression overhead | Low | Make compression optional, benchmark overhead |

## Success Metrics

- ✅ All protobuf messages compile and generate Java classes
- ✅ IndexKey and IndexValue pass all unit tests
- ✅ Serialization round-trip succeeds for 1M operations
- ✅ Key comparison completes in <100ns
- ✅ Protobuf messages 30-50% smaller than JSON equivalent
- ✅ Zero data loss in serialization/deserialization
