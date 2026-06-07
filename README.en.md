# HyperKVStore

## Project Overview

HyperKVStore is a high-performance key-value storage system that combines an LSM (Log-Structured Merge-tree) structure with B+Tree for persistent storage, supporting high-throughput write operations and efficient read/write performance.

## Module Structure

```
hyperstore/
├── lsmplus-api/          # API model definitions
├── lsmplus-config/       # Configuration management
├── lsmplus-exception/    # Exception definitions
├── lsmplus-kvstore/      # Core KVStore implementation
├── lsmplus-monitoring/   # Monitoring and metrics
├── lsmplus-service/      # Service layer
├── lsmplus-storage/      # Storage layer
├── lsmplus-utils/        # Utility classes
└── tools/                # Diagnostic tools
```

## Core Features

- **LSM + B+Tree Hybrid Architecture**: In-memory tables use LSM structure; persistent storage uses B+Tree
- **Write-Ahead Logging (WAL)**: Data persistence and crash recovery via Journal
- **Automatic Compaction**: Supports automatic dump and merge operations
- **Multi-Version Management**: Supports multi-version data and snapshot reads
- **GC Support**: Automatic garbage collection of invalid data
- **Comprehensive Monitoring**: Performance metrics and health checks

## Quick Start

### Build the Project

```bash
./gradlew build
```

### Run Tests

```bash
./gradlew test
```

### Basic Usage

```java
import org.hyperkv.lsmplus.core.KVStore;
import org.hyperkv.lsmplus.api.model.IndexKey;
import org.hyperkv.lsmplus.api.model.IndexValue;

// Create a KVStore instance
KVStore store = new KVStore(new File("/data/kvstore"));
store.start();

// Write data
IndexKey key = IndexKey.orderedBytes("hello".getBytes());
IndexValue value = IndexValue.normal("world".getBytes());
store.put(key, value);

// Read data
IndexValue result = store.get(key);

// Range query
store.rangeQuery(startKey, endKey);

// Shutdown
store.shutdown();
```

## Configuration Parameters

| Parameter | Default Value | Description |
|-----------|---------------|-------------|
| memoryTableMaxSize | 134217728 | Maximum size of in-memory table (128MB) |
| maxSealedTables | 10 | Maximum number of sealed tables |
| leafPageMaxSize | 8192 | Maximum size of leaf pages |
| indexPageMaxSize | 65536 | Maximum size of index pages |

## Test Coverage

The project includes comprehensive unit and integration tests covering the following scenarios:

- Full B+Tree integration tests
- In-memory table management and sealing mechanism
- Journal write and replay
- Storage layer read/write operations
- Concurrency control and snapshot reads
- GC and space reclamation

## License

Apache License 2.0