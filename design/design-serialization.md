# 序列化协议设计

## 1. 概述

本文档定义 KVStore 的序列化协议，包括 Protobuf 消息定义、数据格式规范、版本兼容性等。

**设计原则**：
- **统一格式**：所有数据使用 Protobuf 编码，确保格式一致性
- **版本兼容**：支持向前和向后兼容的版本管理
- **性能优化**：减少序列化开销，提高系统性能
- **可扩展性**：支持未来功能扩展

## 2. Protobuf 消息定义

### 2.1 统一的 Key/Value 定义

所有模块（Journal、Page、MemoryTable、Backup）共用同一套 Key/Value Protobuf 定义，确保数据格式全局一致。

```protobuf
syntax = "proto3";

package org.hyperkv.lsmplus.proto;

// ===== 全局统一的 Key/Value 定义 =====

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

// 统一的键消息（所有模块共用）
message KeyProto {
    KeyType key_type = 1;             // 键类型
    bytes key_data = 2;               // 键数据
}

// 统一的值消息（所有模块共用）
message ValueProto {
    ValueType value_type = 1;         // 值类型（NORMAL 或 TOMBSTONE）
    bytes value_data = 2;             // 值数据（TOMBSTONE 时为空）
}

// 键值对消息（Journal、LeafPage、IndexPage 统一使用）
// 叶页：key + value；索引页：key + location
message KeyValuePairProto {
    KeyProto key = 1;                 // 键
    oneof entry_value {
        ValueProto value = 2;         // 叶页数据 / Journal 操作数据
        SegmentLocationProto location = 3; // 索引页子页位置
    }
}

// SegmentLocation 消息（固定 24 bytes）
message SegmentLocationProto {
    int64 chunk_id_most_sig = 1;     // Chunk UUID 高 64 位
    int64 chunk_id_least_sig = 2;    // Chunk UUID 低 64 位
    int32 offset = 3;                // 偏移量
    int32 length = 4;                // 数据长度
}

// 压缩类型枚举（ValueProto.value_data 可选压缩）
enum CompressionType {
    NONE = 0;           // 无压缩
    GZIP = 1;           // GZIP 压缩
    LZ4 = 2;            // LZ4 压缩
    SNAPPY = 3;         // Snappy 压缩
}
```

### 2.2 Journal Entry 序列化

Journal Entry 使用统一的 `JournalEntryProto` 消息。通过 `operation_type` 区分操作类型，通过 `repeated KeyValuePairProto entries` 表达一个或多个操作：PUT/DELETE 包含 1 个 entry，BATCH 包含多个 entry。DELETE 操作的 entry 中 value 的 value_type 为 TOMBSTONE。

```protobuf
// Journal 操作类型
enum OperationType {
    PUT = 0;            // 插入/更新（entries 含 1 个元素）
    DELETE = 1;         // 删除（entries 含 1 个元素，value 为 TOMBSTONE）
    BATCH = 2;          // 批量（entries 含多个元素）
}

// 统一的 Journal Entry 消息（Write Item 的 Body）
message JournalEntryProto {
    OperationType operation_type = 1; // 操作类型
    int64 timestamp = 2;             // 操作时间戳
    int64 sequence_number = 3;       // 序列号
    repeated KeyValuePairProto entries = 4; // 操作列表
    // PUT: 1 个 entry（key + NORMAL value）
    // DELETE: 1 个 entry（key + TOMBSTONE value）
    // BATCH: N 个 entry（每个可以是 NORMAL 或 TOMBSTONE）
}
```

**使用方式**：
- **PUT**：operation_type=PUT, entries=[{key, value(NORMAL)}]
- **DELETE**：operation_type=DELETE, entries=[{key, value(TOMBSTONE)}]
- **BATCH**：operation_type=BATCH, entries=[{k1,v1(NORMAL)}, {k2,v2(TOMBSTONE)}, ...]

### 2.3 Page 数据序列化

Page 数据直接复用 `KeyValuePairProto`。叶页的 entry 使用 `key + value`（oneof 选 ValueProto），索引页的 entry 使用 `key + location`（oneof 选 SegmentLocationProto）。不再需要单独的 LeafEntryProto 和 IndexEntryProto。
We put the entries encoded with KeyValuePairProto and concat them together. The entry_offsets field records the offset of each entry in the page data. The entry_offset is a 4 bytes integer. we encode the entry_offsets
field in little-endian using array of bytes. So the entry_offsets field can be loaded into an array of int directly by using array copy.
```protobuf
// Page 类型枚举
enum PageType {
    LEAF = 0;           // 叶页
    INDEX = 1;          // 索引页
}

// 完整的 Page 消息
message PageProto {
    PageType page_type = 1;          // 页面类型
    int64 page_id = 2;               // 页面 ID
    int32 used_size = 3;             // 当前已使用字节数
    bytes entry_offsets = 4;         // record the entry offsets in page data, each offset is 4 bytes integer, using array of bytes to store which can be loaded into an array of int directly by using array copy.
    bytes entries = 5;               // 条目列表, concat with KeyValuePairProto
    // 叶页：每个 entry 的 oneof 为 value（ValueProto）
    // 索引页：每个 entry 的 oneof 为 location（SegmentLocationProto）
}
```

### 2.4 SegmentLocation

SegmentLocationProto 是数据在 Chunk 中的定位信息，被 KeyValuePairProto（索引页条目的 oneof location）和 TreeMetadataEntry（元数据根页位置）共同引用。固定 24 bytes：UUID 高低各 8 bytes + offset 4 bytes + length 4 bytes。

### 2.5 设计优势

- **全局统一**：KeyProto/ValueProto/KeyValuePairProto 在 Journal、Page、Backup 中复用，不会出现格式不一致
- **简洁表达**：JournalEntryProto 用 repeated entries 统一 PUT/DELETE/BATCH，无需分支消息
- **向前兼容**：Protobuf 自动处理字段新增，旧版本数据仍可解析
- **工具友好**：可通过 parser tools 离线解析所有 .pb 文件和 Write Item Body

### 2.6 元数据序列化

所有元数据文件统一使用 Protobuf 二进制格式存储（`.pb` 扩展名），不再使用 JSON。Protobuf 格式紧凑、解析快速、支持向前兼容。可通过开发 parser tools 离线解析 `.pb` 文件用于调试和运维。

#### 2.6.1 Tree 元数据（tree-metadata.pb）

```protobuf
// tree-metadata.pb 文件的顶层消息
// make index max size, leaf max size configurable as configure option. also 
// max_versions is configurable as configure option. a default value is 30.
message TreeMetadataFile {
    int32 magic = 1;                   // 魔数，固定值用于格式识别
    int32 format_version = 2;          // 文件格式版本号
    repeated TreeMetadataEntry entries = 3;  // 版本列表（按版本降序）
}

// 单个 Tree 版本的元数据
message TreeMetadataEntry {
    int64 version = 1;               // 版本号
    SegmentLocation root_location = 2; // 根页位置（始终为 IndexPage）
    JournalReplayPoint replay_point = 3; // Journal 回放点
    int64 mns = 4;                    // Min Not Sealed number
    int64 created_at = 5;             // 创建时间
    TreeStats stats = 6;              // 统计信息
}

// Journal 回放点
message JournalReplayPoint {
    int64 region_major = 1;          // Region 主版本号
    int64 region_minor = 2;          // Region 次版本号
    int32 offset = 3;                // 偏移量
}

// 树统计信息
message TreeStats {
    int64 leaf_page_count = 1;       // 叶页数量
    int64 index_page_count = 2;       // 索引页数量
    int64 total_entries = 3;          // 总条目数
    int32 height = 4;                 // 树高度
    int64 total_size = 5;            // 总大小
}
```

#### 2.6.2 Journal Region 索引（journal-region.pb）

```protobuf
// journal-region.pb 文件的顶层消息
message JournalRegionIndex {
    int32 magic = 1;                   // 魔数
    int32 format_version = 2;          // 文件格式版本号
    int64 instance_id_most_sig = 3;    // KVStore 实例 UUID 高 64 位
    int64 instance_id_least_sig = 4;   // KVStore 实例 UUID 低 64 位
    repeated JournalRegionEntry entries = 5; // Region 条目列表
}

// 单个 Region 条目
message JournalRegionEntry {
    int64 region_major = 1;           // Region 主版本号
    int64 region_minor = 2;           // Region 次版本号
    int64 chunk_id_most_sig = 3;      // 对应 Chunk UUID 高 64 位
    int64 chunk_id_least_sig = 4;     // 对应 Chunk UUID 低 64 位
    int32 offset = 5;                 // Chunk 内偏移量
    int32 length = 6;                 // Region 数据长度（-1 表示整个 Chunk）
    int64 created_at = 7;             // 创建时间戳
}
```

#### 2.6.3 Chunk 元数据（chunk-metadata.pb）

```protobuf
// chunk-metadata.pb 文件的顶层消息
message ChunkMetadataFile {
    int32 magic = 1;                   // 魔数
    int32 format_version = 2;          // 文件格式版本号
    repeated ChunkMetadata chunks = 3; // Chunk 元数据列表
}

// 单个 Chunk 的元数据
message ChunkMetadata {
    int64 chunk_id_most_sig = 1;      // Chunk UUID 高 64 位
    int64 chunk_id_least_sig = 2;     // Chunk UUID 低 64 位
    int64 chunk_number = 3;           // Chunk 编号
    ChunkType chunk_type = 4;         // Chunk 类型
    int64 owner_id_most_sig = 5;      // Owner UUID 高 64 位
    int64 owner_id_least_sig = 6;     // Owner UUID 低 64 位
    int64 namespace_id_most_sig = 7;  // Namespace UUID 高 64 位
    int64 namespace_id_least_sig = 8; // Namespace UUID 低 64 位
    ChunkStatus status = 9;           // Chunk 状态
    int64 created_at = 10;            // 创建时间
    int64 keep_alive_time = 11;       // 保活时间
    int64 total_size = 12;            // 总大小
    int64 used_size = 13;             // 已使用大小
    int64 occupancy_size = 14;        // 有效数据大小
    int32 pending_gc = 15;            // Partial GC 等待标志
}
```

#### 2.6.4 Occupancy 记录（occupancy/{version}.pb）

```protobuf
// occupancy/{version}.pb 文件的顶层消息
message OccupancyRecord {
    int64 version = 1;                // Tree 版本号
    int64 mns = 2;                    // 当前 MNS
    int64 timestamp = 3;              // 记录时间戳
    repeated OccupancyDelta deltas = 4;          // Chunk occupancy 变更
    repeated DecommissionPage decommission_pages = 5; // 被 decommission 的 Page
}

message OccupancyDelta {
    int64 chunk_id_most_sig = 1;      // Chunk UUID 高 64 位
    int64 chunk_id_least_sig = 2;     // Chunk UUID 低 64 位
    int64 delta_size = 3;             // 变更大小（正数增加，负数减少）
}

message DecommissionPage {
    int64 chunk_id_most_sig = 1;      // Chunk UUID 高 64 位
    int64 chunk_id_least_sig = 2;     // Chunk UUID 低 64 位
    int32 offset = 3;                 // 页面在 Chunk 中的偏移
    int32 length = 4;                 // 页面长度
}
```

#### 2.6.5 备份元数据（backup.metadata.pb.pb）

```protobuf
// backup.metadata.pb.pb 文件的顶层消息
message BackupMetadata {
    string backup_id = 1;             // 备份唯一标识
    BackupType backup_type = 2;       // 备份类型
    int64 created_at = 3;             // 创建时间
    int64 tree_version = 4;           // 关联的 Tree 版本
    int64 tree_mns = 5;               // 关联的 MNS
    JournalReplayPoint replay_point = 6;  // Journal 回放起点
    JournalReplayPoint cutoff_point = 7;  // Journal 截止点
    string parent_backup_id = 8;      // 父备份 ID（增量备份）
    TreeStats tree_stats = 9;         // Tree 统计信息
    bytes checksum = 10;              // 备份校验和
}

enum BackupType {
    FULL = 0;           // 全量备份
    INCREMENTAL = 1;    // 增量备份
}
```

**持久化规则**：
- 所有 `.pb` 文件的写入使用**临时文件 + fsync + rename**保证原子性
- 写入流程：`xxx.pb.tmp` → fsync → rename 为 `xxx.pb` → fsync 父目录
- 每个文件都有 magic 和 format_version 字段用于格式识别和版本兼容

#### 2.6.6 公共枚举定义

```protobuf
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
```

## 3. 序列化流程

### 3.1 写入序列化流程

写入序列化路径：业务对象 → Protobuf 消息 → 序列化为字节数组 → 添加 CRC32 校验和 → 封装为 Write Item（含 Header + Body + Padding）→ 写入 Chunk。每一步都可能抛出序列化异常，由上层错误处理框架统一处理。

```
写入序列化流程：
  1. 构造业务数据对象
  2. 转换为 Protobuf 消息
  3. 序列化为字节数组
  4. 添加 CRC32 校验和
  5. 构造 Write Item
  6. 写入 Chunk

示例（Put 操作）：
  KeyValuePair → JournalOperation → JournalEntry → Write Item → Chunk
```

### 3.2 读取反序列化流程

读取反序列化路径：从 Chunk 读取 Write Item → 验证 Magic 和 CRC32 → 解析 Protobuf 消息头确定类型 → 反序列化为业务对象 → 返回调用方。CRC32 校验失败说明数据损坏，触发错误处理。

```
读取反序列化流程：
  1. 从 Chunk 读取 Write Item
  2. 验证 CRC32 校验和
  3. 解析 Protobuf 消息头
  4. 反序列化为业务对象
  5. 返回给调用方

示例（Get 操作）：
  Chunk → Write Item → JournalEntry → JournalOperation → KeyValuePair
```

## 4. 版本兼容性

### 4.1 向前兼容策略

Protobuf 向前兼容规则：新增字段必须为 optional（proto3 默认）、不能删除已有字段、字段编号不能重复使用、枚举值只能新增不能删除。读取时忽略未知字段，使用默认值填充缺失字段，记录版本不匹配警告。

```
向前兼容规则：
  - 新增字段必须为 optional
  - 不能删除已有字段
  - 字段编号不能重复使用
  - 枚举值只能新增，不能删除

兼容性检查：
  - 读取时忽略未知字段
  - 使用默认值填充缺失字段
  - 记录版本不匹配警告
```

### 4.2 版本管理

VersionInfo 消息记录协议版本和软件版本，嵌入到元数据文件中。读取数据时检查版本兼容性，不兼容时拒绝加载并提示升级。capabilities 字段列出支持的功能（如压缩算法、加密方式等）。

```protobuf
// 版本信息消息
message VersionInfo {
    string protocol_version = 1;     // 协议版本
    string software_version = 2;     // 软件版本
    int64 created_at = 3;             // 创建时间
    map<string, string> capabilities = 10; // 支持的能力
}
```

## 5. 性能优化

### 5.1 序列化优化

序列化性能优化：使用预分配的字节缓冲区避免频繁内存分配；批量序列化减少对象创建开销；小数据不压缩（避免压缩开销超过收益），大数据使用 LZ4 压缩；配置可选的压缩阈值（默认 1024 bytes）。

```
性能优化策略：
  - 使用预分配的字节缓冲区
  - 避免不必要的对象创建
  - 批量序列化操作
  - 使用高效的压缩算法

压缩策略：
  - 小数据不压缩
  - 大数据使用 LZ4 压缩
  - 配置可选的压缩阈值
```

### 5.2 缓存优化

序列化缓存策略：缓存常用的 Protobuf 消息（如 SegmentLocation 等小对象），使用对象池减少 GC 压力，预计算序列化大小避免 ByteBuffer 扩容。这些优化在高吞吐场景下减少序列化的 CPU 和内存开销。

```
序列化缓存：
  - 缓存常用的 Protobuf 消息
  - 使用对象池减少 GC 压力
  - 预计算序列化大小
```

## 6. 错误处理

### 6.1 序列化错误

序列化错误类型：数据格式错误（Protobuf 解析失败）、版本不兼容（字段缺失或类型变更）、校验和失败（CRC32 不匹配）、内存不足（序列化大对象时 OOM）。所有错误记录详细日志并提供友好的错误信息，支持重试机制。

```
序列化错误类型：
  - 数据格式错误
  - 版本不兼容
  - 校验和失败
  - 内存不足

错误处理策略：
  - 记录详细错误日志
  - 提供友好的错误信息
  - 支持重试机制
```

## 7. 测试策略

### 7.1 序列化测试

序列化测试覆盖：基本数据类型的序列化/反序列化往返测试、复杂嵌套对象测试、向前/向后兼容性测试（新增字段后旧数据仍可解析）、性能基准测试（吞吐量和延迟）、错误场景测试（损坏数据、截断数据）。

```
测试场景：
  - 基本数据类型序列化
  - 复杂对象序列化
  - 版本兼容性测试
  - 性能基准测试
  - 错误场景测试
```

## 8. 配置参数

### 8.1 序列化相关配置

序列化配置参数：buffer_size（缓冲区大小，默认 8192）、compression_threshold（启用压缩的最小数据大小，默认 1024）、compression_algorithm（压缩算法，默认 LZ4）、enable_caching（启用对象缓存，默认 true）、version_check（启用版本检查，默认 true）。

```
序列化配置：
  - serialization.buffer_size: 8192        # 缓冲区大小
  - serialization.compression_threshold: 1024 # 压缩阈值
  - serialization.compression_algorithm: "LZ4" # 压缩算法
  - serialization.enable_caching: true     # 启用缓存
  - serialization.cache_size: 1000         # 缓存大小
  - serialization.version_check: true      # 版本检查
```