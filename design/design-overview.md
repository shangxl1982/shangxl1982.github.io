# 项目概览

## 1. 项目简介

本项目实现基于 B+ Tree 的键值存储系统，包含以下核心组件：

| 组件 | 职责 |
|------|------|
| **Service** | 服务层，提供 REST API，控制 KVStore 生命周期 |
| **KVStore** | 核心存储模块，提供数据操作 API |
| **Journal** | 持久化写操作日志，确保数据安全 |
| **MemoryTableManager** | 管理多个 MemoryTable，协调写入和 Dump |
| **MemoryTable** | 内存中有序数据结构，支持快速写入 |
| **B+ Tree** | 持久化存储结构，分为叶页、索引页、根页 |
| **GC** | 垃圾回收机制，管理 Chunk 生命周期 |
| **Config** | 配置管理框架，支持动态调整 |
| **Monitoring** | 监控体系，性能指标采集与暴露 |
| **Backup** | 备份与恢复机制，支持全量和增量备份 |

**架构分层**：
- **Service 层**：负责 REST API 暴露、请求验证、错误处理、KVStore 生命周期管理
- **KVStore 模块**：独立模块，通过 API 接口暴露功能，不提供 REST API

**核心设计原则**：
- **Tombstone 仅存在于 MemoryTable**：删除标记不会写入 B+Tree 的 Page
- **多 MemoryTable 机制**：支持 Seal 和 Dump 操作，提高并发性能
- **页面自包含地址**：索引页直接存储子页物理位置，实现毫秒级启动
- **Append-Only 设计**：所有写入都是追加操作，优化写入性能
- **多版本管理**：保留多个版本，支持快照读和 GC

## 2. 模块结构

| 模块名 | 职责 | 文件路径 |
|--------|------|----------|
| KVStore | 主存储接口 | src/KVStore.java |
| Journal | 操作日志 | src/journal/Journal.java |
| MemoryTableManager | 内存表管理器 | src/memorytable/MemoryTableManager.java |
| MemoryTable | 内存表 | src/memorytable/MemoryTable.java |
| Page | 统一页面模块 | src/bplustree/page/Page.java |
| BPlusTree | B+树实现 | src/bplustree/BPlusTree.java |
| PageManager | 页面管理器 | src/bplustree/PageManager.java |
| Serializer | 序列化工具 | src/utils/Serializer.java |
| Chunk | 存储块 | src/storage/Chunk.java |
| ChunkManager | 存储块管理器 | src/storage/ChunkManager.java |
| SegmentLocation | 段位置信息 | src/storage/SegmentLocation.java |
| MetricsRegistry | 监控注册器 | src/monitoring/MetricsRegistry.java |

## 3. 设计文档索引

| 文档 | 内容 |
|------|------|
| [design-service.md](design-service.md) | Service 层设计（REST API、生命周期管理、请求处理） |
| [design-key-value.md](design-key-value.md) | Key-Value 存储格式（IndexKey、IndexValue、Tombstone） |
| [design-storage.md](design-storage.md) | 存储层设计（Chunk、ChunkManager、SegmentLocation） |
| [design-memorytable.md](design-memorytable.md) | 内存表设计（MemoryTable、MemoryTableManager） |
| [design-page.md](design-page.md) | Page 设计（叶页、索引页、Offset Array） |
| [design-bplustree.md](design-bplustree.md) | B+树设计（Tree Dump、WriteBuffer） |
| [design-bplustree-metadata.md](design-bplustree-metadata.md) | B+树元数据（版本管理、Journal 回放点） |
| [design-journal.md](design-journal.md) | 操作日志设计（Region、Batch 操作、Truncate 策略） |
| [design-kvstore.md](design-kvstore.md) | KVStore 主类与核心流程（生命周期 API、数据操作） |
| [design-gc.md](design-gc.md) | GC 设计（Chunk 生命周期、Occupancy 跟踪、空间回收） |
| [design-error-handling.md](design-error-handling.md) | 错误处理设计（stopServing、重试策略、双 Buffer） |
| [design-data-integrity.md](design-data-integrity.md) | 数据完整性保护（Write Item、CRC32、4K 对齐） |
| [design-config.md](design-config.md) | Config 框架设计（动态配置、监听器、API 接口） |
| [design-monitoring.md](design-monitoring.md) | 监控体系设计（Performance Counter、API 接口、健康检查） |
| [design-backup.md](design-backup.md) | 备份与恢复设计（全量备份、增量备份、Tree 回滚恢复） |
| [design-concurrency.md](design-concurrency.md) | 并发控制设计（请求队列、BatchWriter、快照读） |
| [design-serialization.md](design-serialization.md) | 序列化协议设计（Protobuf 消息定义、版本兼容） |
| [design-testing.md](design-testing.md) | 测试策略设计（单元测试、集成测试、性能测试） |
| [design-package-structure.md](design-package-structure.md) | 包结构设计（模块划分、依赖关系、命名规范） |

## 4. 架构图


```
┌─────────────────────────────────────────────────────────────┐
│                        KVStore API                          │
│                    (put / get / delete)                     │
└─────────────────────────────────────────────────────────────┘
                              │
            ┌─────────────────┼─────────────────┐
            │                 │                 │
            ▼                 ▼                 ▼
    ┌───────────┐     ┌─────────────────┐    ┌───────────┐
    │  Journal  │     │MemoryTableManager│   │  B+Tree   │
    │  (日志)   │     │ ┌─────────────┐ │    │ (持久化)  │
    │           │     │ │ActiveTable  │ │    │           │
    │           │     │ └─────────────┘ │    │ (无Tombstone)│
    │           │     │ ┌─────────────┐ │    │           │
    │           │     │ │SealedTables │ │    │           │
    │           │     │ └─────────────┘ │    │           │
    └───────────┘     └─────────────────┘    └───────────┘
            │                 │                 │
            └─────────────────┼─────────────────┘
                              │
                              ▼
                    ┌─────────────────┐
                    │  ChunkManager   │
                    │   (存储管理)    │
                    └─────────────────┘
                              │
                              ▼
                    ┌─────────────────┐
                    │     Chunks      │
                    │   (磁盘文件)    │
                    └─────────────────┘
```

### 4.1 数据流向

写入流程：put → Journal.write → MemoryTableManager.put → MemoryTable.put，达到阈值后 Seal → Dump → B+Tree。读取流程：get → MemoryTableManager.get（优先查内存），未命中则 B+Tree.search（查磁盘）。写入是 WAL 模式（先日志后内存），读取是内存优先模式。

```
写入流程：
put(key, value) → Journal.write() → MemoryTableManager.put() → MemoryTable.put()
                                                            ↓
                                              (达到阈值) → Seal → Dump → B+Tree

读取流程：
get(key) → MemoryTableManager.get() → (找到则返回)
              ↓ (未找到)
           B+Tree.search() → 返回结果
```

### 4.2 Tombstone 处理

Tombstone 的数据流：delete 操作在 MemoryTable 中标记为 TOMBSTONE，Dump 时 Tombstone 条目触发 B+Tree 的物理删除（removeEntry），NORMAL 条目触发插入（insert）。最终 B+Tree Page 中只有 NORMAL 类型的数据，不存储 Tombstone。

```
MemoryTable (内存)              B+Tree Page (持久化)
┌───────────────────┐           ┌───────────────────┐
│ TreeMap<IndexKey, │   Dump    │ <IndexKey,        │
│   IndexValue>     │ ────────► │   IndexValue>     │
│ - NORMAL          │           │ (仅 NORMAL)       │
│ - TOMBSTONE       │           │                   │
└───────────────────┘           └───────────────────┘
        │
        │ Tombstone → B+Tree.delete(key)
        │ NORMAL → B+Tree.insert(key, value)
        ▼
```

## 5. 性能分析

### 5.1 时间复杂度

| 操作 | MemoryTable | B+Tree |
|------|-------------|--------|
| 写入 | O(log n) | O(log n) |
| 读取 | O(log n) | O(log n) |
| 范围查询 | O(log n + k) | O(log n + k) |
| 删除 | O(log n) | O(log n) |

> n = 数据量，k = 范围内元素数量

### 5.2 空间复杂度

| 组件 | 空间复杂度 |
|------|------------|
| Journal | O(n) |
| MemoryTable | O(n) |
| B+Tree | O(n) |

### 5.3 性能优化点

- **批量写入**: Dump 操作批量处理，减少 I/O 次数
- **内存优先**: 读操作优先查 MemoryTable，减少磁盘访问
- **顺序写入**: Chunk 采用追加写入，优化磁盘性能
- **多 MemoryTable**: Seal 操作快速封存，Dump 可异步进行

## 6. 测试计划

### 6.1 基础功能测试

- 插入测试：插入多个键值对
- 删除测试：删除键值对（Tombstone）
- 查询测试：查询存在和不存在的 key
- 范围查询：查询不同范围的 key
- 删除后查询：验证删除的 key 返回 null
- 删除后重新插入：验证重新插入正常工作

### 6.2 边界情况测试

- 空存储测试
- MemoryTable Seal 测试
- MemoryTable Dump 测试
- 叶页分裂测试
- 索引页分裂测试
- 叶页合并测试（删除后）
- 索引页合并测试（删除后）
- 兄弟节点借用测试
- 删除不存在的 key 测试

### 6.3 删除操作测试

| 测试场景 | 预期结果 |
|----------|----------|
| 删除存在的 key | 写入 Tombstone |
| 删除不存在的 key | 无错误 |
| 删除后 get | 返回 null |
| 删除后范围查询 | key 被排除 |
| 删除后 Dump | 物理删除生效（从 B+Tree 删除） |
| 删除后重新插入 | 新值被存储 |

### 6.4 性能测试

- 大量数据插入性能
- 大量数据查询性能
- 范围查询性能
- 大量数据删除性能
- Seal 和 Dump 性能

## 7. 依赖项

| 依赖 | 版本 | 用途 |
|------|------|------|
| Java 标准库 | JDK 25 | 核心功能 |
| Protocol Buffers | 3.21.12 | 序列化框架 |
| Protobuf 编译器 | 3.21.12 | 生成 Java 代码 |

## 8. 扩展可能性

- 支持泛型键值类型
- 实现压缩算法减少存储空间
- 支持并发操作
- 实现缓存机制提高读性能
- 支持事务处理
