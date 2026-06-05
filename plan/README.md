# HyperKVStore Development Plan

## Overview

This document outlines the development plan for HyperKVStore, a B+Tree-based key-value storage system. The development is organized into 6 stages, each containing multiple epics and stories.

## Architecture Summary

```
┌─────────────────────────────────────────────────────────────┐
│                        Service Layer                         │
│                    (REST API, Lifecycle)                     │
└─────────────────────────────────────────────────────────────┘
                              │
┌─────────────────────────────────────────────────────────────┐
│                        KVStore Core                          │
│           (Put/Get/Delete/Batch, Dump, Recovery)            │
└─────────────────────────────────────────────────────────────┘
                              │
        ┌─────────────────────┼─────────────────────┐
        │                     │                     │
        ▼                     ▼                     ▼
┌───────────────┐    ┌─────────────────┐    ┌───────────────┐
│    Journal    │    │ MemoryTable     │    │    B+Tree     │
│    (WAL)      │    │   Manager       │    │  (Persisted)  │
└───────────────┘    └─────────────────┘    └───────────────┘
        │                     │                     │
        └─────────────────────┼─────────────────────┘
                              │
                              ▼
                    ┌─────────────────┐
                    │  ChunkManager   │
                    │   (Storage)     │
                    └─────────────────┘
```

## Development Stages

| Stage | Name | Goal | Duration |
|-------|------|------|----------|
| 1 | Foundation Infrastructure | Build storage foundation, serialization, and data integrity | 3-4 weeks |
| 2 | Core Components | Implement Journal and MemoryTable | 2-3 weeks |
| 3 | B+Tree Implementation | Build B+Tree with pages and dump mechanism | 3-4 weeks |
| 4 | KVStore Core | Integrate all components into KVStore | 2-3 weeks |
| 5 | Advanced Features | Add GC, Backup, Monitoring, Config | 3-4 weeks |
| 6 | Service Layer & Production | REST API, testing, production readiness | 2-3 weeks |

## Stage Dependencies

```
Stage 1 (Foundation)
    │
    ├── Epic 1: Protobuf Serialization
    ├── Epic 2: Data Integrity (Write Item, CRC32)
    └── Epic 3: Storage Layer (Chunk, ChunkManager)
            │
            ▼
Stage 2 (Core Components)
    │
    ├── Epic 4: Journal (WAL)
    └── Epic 5: MemoryTable
            │
            ▼
Stage 3 (B+Tree)
    │
    ├── Epic 6: Page (Leaf, Index)
    └── Epic 7: B+Tree Core (Tree, Dump, WriteBuffer)
            │
            ▼
Stage 4 (KVStore Core)
    │
    ├── Epic 8: KVStore Main (Integration)
    └── Epic 9: Concurrency Control
            │
            ▼
Stage 5 (Advanced Features)
    │
    ├── Epic 10: Garbage Collection
    ├── Epic 11: Backup & Recovery
    ├── Epic 12: Monitoring
    └── Epic 13: Configuration Management
            │
            ▼
Stage 6 (Service Layer)
    │
    ├── Epic 14: Service Layer (REST API)
    └── Epic 15: Testing & Production Readiness
```

## Key Design Principles

1. **Tombstone Only in MemoryTable**: Delete markers never written to B+Tree pages
2. **Page Self-Contained Address**: Index pages store child page locations directly
3. **Append-Only Design**: All writes are append operations
4. **WAL First**: Journal written before MemoryTable update
5. **Multi-Version Support**: Each Tree Dump creates a new version

## Technology Stack

| Component | Technology |
|-----------|------------|
| Language | Java 25 |
| Serialization | Protocol Buffers 3.21.12 |
| Build Tool | Maven |
| Testing | JUnit 5, Mockito, AssertJ |
| Monitoring | Prometheus format |

## Directory Structure

```
plan/
├── README.md                    # This file
├── stage-1-foundation/          # Stage 1: Foundation Infrastructure
│   ├── stage-plan.md           # Stage overview
│   ├── epic-1-protobuf/        # Epic: Protobuf Serialization
│   ├── epic-2-data-integrity/  # Epic: Data Integrity
│   └── epic-3-storage-layer/   # Epic: Storage Layer
├── stage-2-core-components/     # Stage 2: Core Components
├── stage-3-bplustree/          # Stage 3: B+Tree Implementation
├── stage-4-kvstore-core/       # Stage 4: KVStore Core
├── stage-5-advanced-features/  # Stage 5: Advanced Features
└── stage-6-service-layer/      # Stage 6: Service Layer
```

## Getting Started

1. Read the design documents in `design/` folder
2. Start with Stage 1: Foundation Infrastructure
3. Each epic folder contains:
   - `epic-description.md`: Epic overview and goals
   - `story-*.md`: Individual stories with acceptance criteria

## Progress Tracking

Progress is tracked through:
- Story completion (all acceptance tests passing)
- Epic completion (all stories done)
- Stage completion (all epics done + integration tests)
