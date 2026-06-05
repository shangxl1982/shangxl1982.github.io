# Implementation Plan: LSM Plus KV Service Layer

**Branch**: `010-lsmplus-service` | **Date**: 2026-04-17 | **Spec**: [spec.md](file:///home/wisefox/git/hyperkvstore/specs/010-lsmplus-service/spec.md)
**Input**: Feature specification from `/specs/010-lsmplus-service/spec.md`

## Summary

Implement service layer providing request/response API for key-value operations. Exposes simple API for GET, PUT, DELETE operations, supports batch operations with atomic semantics, provides clear error responses, validates requests before processing, and supports request context and metadata for multi-tenancy and tracing.

## Technical Context

**Language/Version**: Java 25  
**Primary Dependencies**: lsmplus-core, lsmplus-api, JUnit 6.0.0  
**Storage**: Delegates to core KV store  
**Testing**: JUnit 5 with Mockito  
**Target Platform**: Linux server (JVM)  
**Project Type**: Library  
**Performance Goals**: <1ms single request, >50K ops/s batch, <100μs error response  
**Constraints**: Request size limits (4MB batch, 1MB single), timeout enforcement  
**Scale/Scope**: 5 core classes (KVService, KVRequest, KVResponse, BatchOperationItem, RequestContext)  

## Constitution Check

✅ **Library-First**: Standalone service layer library
✅ **Test-First**: TDD with unit and integration tests
✅ **Simplicity**: Thin wrapper around core KV store
✅ **Observability**: Request metrics, error logging
✅ **Versioning**: API versioned for compatibility

## Project Structure

```text
lsmplus-service/
├── src/
│   ├── main/java/org/hyperkv/lsmplus/service/
│   │   ├── KVService.java             # Main service interface
│   │   ├── KVRequest.java             # Request object
│   │   ├── KVResponse.java            # Response object
│   │   ├── BatchOperationItem.java    # Batch item
│   │   └── RequestContext.java        # Context (tenant, trace ID)
│   └── test/java/org/hyperkv/lsmplus/service/
│       └── KVServiceTest.java
└── build.gradle.kts
```

## Phase 0: Research & Design Decisions

### Research Tasks

1. **Request/Response Model**: Immutable request/response objects
2. **Error Handling**: Structured error responses with codes
3. **Validation Strategy**: Validate early, fail fast
4. **Context Propagation**: Thread-local or explicit context
5. **Timeout Handling**: Enforce timeouts at service layer

### Design Decisions

1. **Immutable Requests**: Request objects immutable after creation
2. **Structured Errors**: ErrorResponse with code, message, details
3. **Early Validation**: Validate before delegating to core
4. **Explicit Context**: RequestContext passed explicitly
5. **Timeout Enforcement**: Use timeouts from request context

## Phase 1: Design & Contracts

**Public API**:
- `KVService.execute(KVRequest)` - Execute single operation
- `KVService.executeBatch(List<KVRequest>)` - Execute batch atomically
- `KVRequest.get(IndexKey)` - Create GET request
- `KVRequest.put(IndexKey, IndexValue)` - Create PUT request
- `KVRequest.delete(IndexKey)` - Create DELETE request

## Dependencies

**Internal**: lsmplus-core, lsmplus-api  
**External**: JUnit 6.0.0, Mockito 5.11.0

## Success Metrics

- ✅ Request processing <1ms
- ✅ Batch throughput >50K ops/s
- ✅ Error response <100μs
- ✅ Request validation <50μs
- ✅ Handle 10K concurrent requests
