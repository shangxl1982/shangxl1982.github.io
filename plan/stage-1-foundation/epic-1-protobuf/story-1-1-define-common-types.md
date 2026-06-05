# Story 1-1: Define Common Types

## Story

As a developer, I want to define common Protobuf types so that they can be reused across all message definitions according to the design specifications.

## Acceptance Criteria

- [ ] KeyType enum defined with ORDERED_BYTES and CUSTOM values
- [ ] ValueType enum defined with NORMAL and TOMBSTONE values
- [ ] OperationType enum defined with PUT, DELETE, and BATCH values
- [ ] PageType enum defined with LEAF and INDEX values
- [ ] ChunkType enum defined with INDEX, LEAF, and JOURNAL values
- [ ] ChunkStatus enum defined with OPEN, SEALED, DELETING, and DELETED values
- [ ] BackupType enum defined with FULL and INCREMENTAL values
- [ ] CompressionType enum defined with NONE, GZIP, LZ4, and SNAPPY values
- [ ] All enums have clear documentation comments in Chinese as per design
- [ ] Protobuf file compiles without errors
- [ ] Field numbers 1-100 reserved for future use

## Technical Details

### File: common.proto

```protobuf
syntax = "proto3";

package org.hyperkv.lsmplus.proto;

// 键类型枚举
enum KeyType {
    ORDERED_BYTES = 0;  // 有序字节（直接排序）
    CUSTOM = 1;         // 自定义格式
}

// 值类型枚举
enum ValueType {
    NORMAL = 0;         // 正常值
    TOMBSTONE = 1;      // 删除标记
}

// Journal 操作类型
enum OperationType {
    PUT = 0;            // 插入/更新（entries 含 1 个元素）
    DELETE = 1;         // 删除（entries 含 1 个元素，value 为 TOMBSTONE）
    BATCH = 2;          // 批量（entries 含多个元素）
}

// Page 类型枚举
enum PageType {
    LEAF = 0;           // 叶页
    INDEX = 1;          // 索引页
}

// Chunk 类型枚举
enum ChunkType {
    INDEX = 0;    // 索引页数据
    LEAF = 1;     // 叶页数据
    JOURNAL = 2;  // Journal 数据
}

// Chunk 状态枚举
enum ChunkStatus {
    OPEN = 0;      // 可写状态
    SEALED = 1;    // 已封存
    DELETING = 2;  // 正在删除
    DELETED = 3;   // 已删除
}

// 备份类型枚举
enum BackupType {
    FULL = 0;           // 全量备份
    INCREMENTAL = 1;    // 增量备份
}

// 压缩类型枚举（ValueProto.value_data 可选压缩）
enum CompressionType {
    NONE = 0;           // 无压缩
    GZIP = 1;           // GZIP 压缩
    LZ4 = 2;            // LZ4 压缩
    SNAPPY = 3;         // Snappy 压缩
}
```

## Implementation Notes

1. Create `src/main/proto/common.proto`
2. Add documentation comments for each enum and value
3. Use `proto3` syntax
4. Reserve values 100-200 for future use in each enum

## Testing

- Compile proto file: `protoc --java_out=src/main/java common.proto`
- Verify generated Java enum classes
- Verify enum values match design document

## Effort Estimate

0.5 day
