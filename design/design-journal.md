# 操作日志设计

## 1. 概述

Journal（操作日志）是 LSM Tree 架构中的数据安全组件，提供：
- **持久化**：所有写操作先记录到日志
- **崩溃恢复**：系统重启后通过回放日志恢复数据
- **统一存储**：使用 Chunk 作为底层存储，与 Page 数据共享存储层

### 1.1 架构图

Journal 的整体架构：操作日志按追加方式写入 JOURNAL 类型的 Chunk，通过 Region 概念进行逻辑分区（一个 Region 对应一个 Chunk，major 号递增），Region Index 文件记录所有 Region 与 Chunk 的映射关系。Journal 与 Page 数据共享 ChunkManager 统一存储层。

```
┌─────────────────────────────────────────────────────┐
│                      Journal                         │
│  ┌───────────────────────────────────────────────┐  │
│  │              Operation Log                     │  │
│  │  ┌─────────┬─────────┬─────────┬─────────┐   │  │
│  │  │ PUT k=1 │ PUT k=2 │ DEL k=1 │ PUT k=3 │   │  │
│  │  │ v="A"   │ v="B"   │         │ v="C"   │   │  │
│  │  └─────────┴─────────┴─────────┴─────────┘   │  │
│  │              (追加写入，顺序存储)              │  │
│  └───────────────────────────────────────────────┘  │
│                                                      │
│  currentRegion: JournalRegion (major, minor)        │
│  regionIndex: List<JournalRegionEntry>              │
└─────────────────────────────────────────────────────┘
            │
            ▼ 使用 ChunkManager 统一存储
┌─────────────────────────────────────────────────────┐
│                   ChunkManager                       │
│  ┌─────────────┐  ┌─────────────┐  ┌─────────────┐ │
│  │ JOURNAL     │  │ JOURNAL     │  │ DATA        │ │
│  │ Chunk-1     │  │ Chunk-2     │  │ Chunk-3     │ │
│  │ Region 1    │  │ Region 2    │  │ Pages       │ │
│  └─────────────┘  └─────────────┘  └─────────────┘ │
└─────────────────────────────────────────────────────┘
```

### 1.2 Journal Region 概念

**Journal Region** 是日志的逻辑分区单位，用于简化回放点定位：

```
┌─────────────────────────────────────────────────────┐
│                  Journal Region                      │
├─────────────────────────────────────────────────────┤
│  major: long    - 每次 rotate chunk 时 +1          │
│  minor: long    - 保留以做后用                      │
└─────────────────────────────────────────────────────┘

Region 与 Chunk 的关系:
  Region 1 (major=1) → Chunk-A
  Region 2 (major=2) → Chunk-B
  Region 3 (major=3) → Chunk-C
```

**优势**：
- 粗粒度管理，避免 LSN 的复杂性
- 直接定位回放点，无需二分查找
- 支持精确的日志清理边界

## 2. 类设计

### 2.1 Operation（操作类型）

| 类型 | Protobuf 枚举值 | 描述 |
|------|-----------------|------|
| PUT | 0 | 插入/更新操作 |
| DELETE | 1 | 删除操作 |
| BATCH | 2 | 批量操作（包含多个 PUT/DELETE 子操作） |

> 每个操作（PUT / DELETE / BATCH）独立对应一个 Write Item。BATCH 操作将多个子操作打包在一个 Write Item 中，保证原子性。BatchWriter 不拆分 batch，忠实记录为单个 BATCH 类型的 Write Item。

### 2.2 JournalRegion

| 属性 | 类型 | 描述 |
|------|------|------|
| major | long | 主版本号，每次 rotate chunk 时 +1 |
| minor | long | 次版本号，保留以做后用 |

### 2.3 JournalRegionEntry

当 Journal Chunk 写满时记录的元数据条目。

| 属性 | 类型 | 描述 |
|------|------|------|
| major | long | Region 主版本号 |
| minor | long | Region 次版本号 |
| chunkId | UUID | 对应的 Chunk ID |
| createdAt | long | 创建时间戳 |

### 2.4 Journal

| 属性 | 类型 | 描述 |
|------|------|------|
| chunkManager | ChunkManager | 底层存储管理器 |
| currentChunk | Chunk | 当前写入的 JOURNAL Chunk |
| currentRegion | JournalRegion | 当前 Region |
| regionIndex | List<JournalRegionEntry> | Region 索引 |
| maxChunkSize | long | 单 Chunk 最大大小（默认 64MB） |

| 方法 | 返回类型 | 描述 |
|------|----------|------|
| write | JournalReplayPoint | 写入单个操作（PUT/DELETE），返回回放点 |
| writeBatch | JournalReplayPoint | 写入 BATCH 操作（多个子操作打包为一个 Write Item），返回回放点 |
| batchWrite | List<JournalReplayPoint> | 批量写入多个独立 Write Item（聚合 I/O），返回每个操作的回放点 |
| buildWriteItem | byte[] | 将单个操作构造为 Write Item 字节数组 |
| replayFrom | void | 从指定 Region + Offset 开始回放日志到 MemoryTableManager |
| rotateChunk | void | 轮转 Chunk，major++ |
| close | void | 关闭 Journal |
| truncate | void | 清理已 Dump 的日志 |

### 2.5 JournalReplayPoint

记录在 B+Tree 元数据中的回放点。

| 属性 | 类型 | 描述 |
|------|------|------|
| region | JournalRegion | 回放起始 Region |
| offset | int | Chunk 内偏移量 |

## 3. 数据结构

### 3.1 Journal Entry 格式

Journal Entry 统一使用 Protobuf 编码（`JournalEntryProto` 消息），作为 Write Item 的 Body 写入 Chunk。三种操作类型（PUT/DELETE/BATCH）共用同一个消息结构，通过 `operation_type` 区分，通过 `repeated KeyValuePairProto entries` 表达一个或多个操作。详细 Protobuf Schema 见 [序列化协议设计](design-serialization.md) 2.1~2.2 节。

每个操作（PUT / DELETE / BATCH）独立对应一个 Write Item（Write Item 提供 Magic/CRC32/4K 对齐保护，详见 [数据完整性保护设计](design-data-integrity.md)）。

```
JournalEntryProto 消息结构：

PUT 操作：
  operation_type = PUT
  timestamp = 操作时间戳
  sequence_number = 序列号
  entries = [{key: KeyProto, value: ValueProto(NORMAL)}]

DELETE 操作：
  operation_type = DELETE
  timestamp = 操作时间戳
  sequence_number = 序列号
  entries = [{key: KeyProto, value: ValueProto(TOMBSTONE)}]

BATCH 操作：
  operation_type = BATCH
  timestamp = 操作时间戳
  sequence_number = 序列号
  entries = [
    {key: k1, value: v1(NORMAL)},
    {key: k2, value: v2(TOMBSTONE)},
    ...
  ]

Write Item 构成：
  Header(Magic 2B + Type 2B + Length 4B)
  + Body(JournalEntryProto 的 Protobuf 序列化字节)
  + CRC32(4B)
  + Padding(4K 对齐)
```

### 3.2 Journal Region Index 文件

`journal-region.pb` 文件存储所有 Region Entry，位于存储根目录，使用 Protobuf 二进制格式。

```
{storagePath}/journal-region.pb
```

文件使用 `JournalRegionIndex` 消息序列化（详见 [序列化协议设计](design-serialization.md) 2.6.2 节）。包含 magic、format_version、instanceId（KVStore UUID）和 Region 条目列表（每个条目含 region_major/minor、chunkId、offset、length、createdAt）。

**字段说明**：

| 字段 | 描述 |
|------|------|
| instance_id | KVStore 实例 UUID，标识归属 |
| entries | Region Entry 列表，按 major 排序 |
| region_major/minor | Region 版本号，major 每次 rotateChunk 递增 |
| chunk_id | 对应的 Chunk UUID |
| offset | Chunk 内偏移量（当前固定为 0） |
| length | Region 数据长度（-1 表示整个 Chunk） |

**设计说明**：

```
当前设计特点：
  - 一个 Region 对应一个 Chunk
  - offset 固定为 0（从 Chunk 开头开始）
  - length 固定为 -1（表示整个 Chunk）

未来扩展：
  - 支持一个 Chunk 包含多个 Region
  - offset 和 length 将用于定位具体 Region 数据
  - 实现更细粒度的空间管理
```

**文件操作**：

```
加载时机：
  - Journal 初始化时加载
  - 用于定位回放点
  - 构建 Region Index Map

持久化时机：
  - 每次 rotateChunk 时更新
  - 使用临时文件 + rename 保证原子性
  - 写入流程：journal-region.pb.tmp → fsync → rename

文件路径：
  - 临时文件：{storagePath}/journal-region.pb.tmp
  - 正式文件：{storagePath}/journal-region.pb
```

## 4. 核心操作流程

### 4.1 写入流程

Journal 写入时先检查当前 Chunk 是否已满，满了则执行 Chunk 轮转（关闭当前 Chunk，记录 Region Entry，major++，分配新 Chunk）。然后将操作构造为 Journal Entry，通过 ChunkManager 以 Write Item 格式写入当前 JOURNAL Chunk。写入成功后返回 JournalReplayPoint（Region + Offset），供 MemoryTable 记录关联关系。

```
write(opType, keyData, valueData)
    │
    ▼
┌─────────────────────────────────────┐
│  1. 检查当前 Chunk 是否已满         │
│     if (currentChunk.isFull())      │
│         rotateChunk()               │
└─────────────────────────────────────┘
    │
    ▼
┌─────────────────────────────────────┐
│  2. 构造 Journal Entry              │
│     entry = JournalEntry(           │
│         opType,                     │
│         timestamp = now(),          │
│         keyData,                    │
│         valueData)                  │
└─────────────────────────────────────┘
    │
    ▼
┌─────────────────────────────────────┐
│  3. 写入 ChunkManager               │
│     location = chunkManager         │
│         .writeJournal(entry)        │
└─────────────────────────────────────┘
    │
    ▼
┌─────────────────────────────────────┐
│  4. 构造并返回 ReplayPoint          │
│     return {                        │
│         regionMajor,                │
│         regionMinor,                │
│         offset: location.offset     │
│     }                               │
└─────────────────────────────────────┘
```

### 4.2 BATCH 操作写入流程 (writeBatch)

writeBatch 将多个 PUT/DELETE 子操作打包为一个 BATCH 类型的 Write Item 写入。整个 BATCH 作为一个原子操作——要么全部成功写入，要么全部失败。BATCH 与 PUT/DELETE 一样，每个操作对应一个 Write Item。

```
writeBatch(operations)
    │
    ▼
┌─────────────────────────────────────┐
│  1. 检查当前 Chunk 是否有空间       │
│     if (currentChunk.remaining      │
│         < estimatedSize):           │
│         rotateChunk()               │
└─────────────────────────────────────┘
    │
    ▼
┌─────────────────────────────────────┐
│  2. 构造 BATCH Journal Entry        │
│     entry = JournalEntry(           │
│         opType = BATCH,             │
│         timestamp = now(),          │
│         operationCount = ops.size,  │
│         operations = ops)           │
│     // 所有子操作打包到一个 entry   │
└─────────────────────────────────────┘
    │
    ▼
┌─────────────────────────────────────┐
│  3. 作为单个 Write Item 写入        │
│     location = chunkManager         │
│         .writeJournal(entry)        │
│     // 一个 BATCH = 一个 Write Item │
│     // 原子性：成功则全部成功       │
└─────────────────────────────────────┘
    │
    ▼
┌─────────────────────────────────────┐
│  4. 构造并返回 ReplayPoint          │
│     return {                        │
│         regionMajor,                │
│         regionMinor,                │
│         offset: location.offset     │
│     }                               │
└─────────────────────────────────────┘
```

### 4.3 聚合 I/O 写入流程 (batchWrite)

BatchWriter 调用 batchWrite 将多个独立的 Write Item（每个可能是 PUT、DELETE 或 BATCH）聚合为一次存储 I/O。每个 Write Item 独立（含自己的 CRC32 和 4K 对齐），聚合 I/O 减少系统调用次数。崩溃恢复时逐个 Write Item 校验。

```
batchWrite(writeItems)
    │
    ▼
┌─────────────────────────────────────┐
│  1. 检查当前 Chunk 剩余空间         │
│     totalSize = sum(item.size       │
│         for item in writeItems)     │
│     if (currentChunk.remaining      │
│         < totalSize):               │
│         rotateChunk()               │
└─────────────────────────────────────┘
    │
    ▼
┌─────────────────────────────────────┐
│  2. 聚合写入                        │
│     buffer = concat(writeItems)     │
│     chunkManager.write(buffer)      │
│     // 一次 I/O 写入多个 Write Item │
│     // 每个 Item 独立 CRC32 + 4K 对齐│
└─────────────────────────────────────┘
    │
    ▼
┌─────────────────────────────────────┐
│  3. 构造并返回每个操作的 ReplayPoint│
│     replayPoints = []               │
│     offset = currentOffset          │
│     for item in writeItems:         │
│         replayPoints.add({          │
│             regionMajor,            │
│             regionMinor,            │
│             offset: offset          │
│         })                          │
│         offset += item.alignedSize  │
│     return replayPoints             │
└─────────────────────────────────────┘
```

### 4.4 Chunk 轮转流程

Chunk 轮转在当前 JOURNAL Chunk 写满时触发：关闭当前 Chunk，记录一条 JournalRegionEntry（含 Region major/minor 和 chunkId），持久化 Region Index 文件，递增 Region major，然后分配新的 JOURNAL Chunk。一个 Region 对应一个 Chunk，通过 major 号可以直接定位到对应的 Chunk 文件。

```
rotateChunk()
    │
    ▼
┌─────────────────────────────────────┐
│  1. 关闭当前 Chunk                  │
│     currentChunk.close()            │
└─────────────────────────────────────┘
    │
    ▼
┌─────────────────────────────────────┐
│  2. 记录 Region Entry               │
│     entry = JournalRegionEntry(     │
│         major = currentRegion.major,│
│         minor = currentRegion.minor,│
│         chunkId = currentChunk.id)  │
│     regionIndex.add(entry)          │
│     persistRegionIndex()            │
└─────────────────────────────────────┘
    │
    ▼
┌─────────────────────────────────────┐
│  3. Region major++                  │
│     currentRegion.major++           │
└─────────────────────────────────────┘
    │
    ▼
┌─────────────────────────────────────┐
│  4. 分配新 JOURNAL Chunk            │
│     currentChunk = chunkManager     │
│         .allocateJournalChunk()     │
└─────────────────────────────────────┘
```

### 4.5 回放流程（崩溃恢复）

崩溃恢复时从 tree-metadata.pb 中的 journalReplayPoint 开始回放。先通过 Region major 定位对应的 Chunk，从 offset 处开始读取 Journal Entry（每个 Entry 是一个独立的 Write Item，可能是 PUT、DELETE 或 BATCH），然后继续读取后续 Region 的 Chunk 直到末尾。每条 Entry 按类型重新应用到 MemoryTableManager：PUT/DELETE 直接应用，BATCH 逐条展开其子操作应用。不完整的 Write Item（CRC32 校验失败）会被忽略，只回放完整写入的数据。

```
replayFrom(memoryTableManager, replayPoint)
    │
    ▼
┌─────────────────────────────────────┐
│  1. 根据 Region 查找 Chunk          │
│     entry = regionIndex             │
│         .findByMajor(replayPoint    │
│             .region.major)          │
│     chunk = chunkManager            │
│         .getChunk(entry.chunkId)    │
└─────────────────────────────────────┘
    │
    ▼
┌─────────────────────────────────────┐
│  2. 从 offset 开始读取              │
│     entries = readEntries(          │
│         chunk,                      │
│         replayPoint.offset)         │
└─────────────────────────────────────┘
    │
    ▼
┌─────────────────────────────────────┐
│  3. 继续读取后续 Region 的 Chunk    │
│     for entry in regionIndex        │
│         .where(major > targetMajor):│
│         entries += readEntries(     │
│             entry.chunkId)          │
└─────────────────────────────────────┘
    │
    ▼
┌─────────────────────────────────────┐
│  4. 重放操作到 MemoryTableManager   │
│     for entry in entries:           │
│         if (entry.opType == PUT)    │
│             memoryTableManager.put( │
│                 entry.key,          │
│                 entry.value)        │
│         else if (entry.opType == DELETE)│
│             memoryTableManager.delete(│
│                 entry.key)          │
│         else if (entry.opType == BATCH)│
│             for op in entry.operations:│
│                 if (op.opType == PUT)│
│                     memoryTableManager.put(│
│                         op.key, op.value)│
│                 else                │
│                     memoryTableManager.delete(│
│                         op.key)     │
└─────────────────────────────────────┘
    │
    ▼
┌─────────────────────────────────────┐
│  5. 恢复 Journal 状态               │
│     currentRegion.major = maxMajor+1│
│     打开最后一个 Chunk 继续写入     │
└─────────────────────────────────────┘
```

### 4.6 日志清理流程

日志清理（truncate）删除不再需要的旧 Journal Chunk。通过对比保留期限和当前最旧的 journalReplayPoint，确定哪些 Region 可以安全删除。删除操作按 Chunk 粒度进行：删除 Chunk 文件，从 Region Index 中移除对应条目，最后持久化更新后的 Region Index。

```
truncate(dumpedRegion)
    │
    ▼
┌─────────────────────────────────────┐
│  1. 找到可删除的 Region 范围        │
│     entriesToDelete = regionIndex   │
│         .where(major < dumpedRegion)│
└─────────────────────────────────────┘
    │
    ▼
┌─────────────────────────────────────┐
│  2. 删除对应的 Chunk                │
│     for entry in entriesToDelete:   │
│         chunkManager.delete(        │
│             entry.chunkId)          │
│         regionIndex.remove(entry)   │
└─────────────────────────────────────┘
    │
    ▼
┌─────────────────────────────────────┐
│  3. 持久化更新后的 Region Index     │
│     persistRegionIndex()            │
└─────────────────────────────────────┘
```

## 5. 初始化流程

```
init(chunkManager)
    │
    ▼
┌─────────────────────────────────────┐
│  1. 加载 Region Index 文件          │
│     regionIndex = loadRegionIndex() │
└─────────────────────────────────────┘
    │
    ▼
┌─────────────────────────────────────┐
│  2. 确定当前 Region                 │
│     if (regionIndex.isEmpty()):     │
│         currentRegion.major = 1     │
│     else:                           │
│         currentRegion.major =       │
│             regionIndex.last()      │
│                 .major + 1          │
└─────────────────────────────────────┘
    │
    ▼
┌─────────────────────────────────────┐
│  3. 打开或创建当前 Chunk            │
│     if (regionIndex.isEmpty()):     │
│         currentChunk = chunkManager │
│             .allocateJournalChunk() │
│     else:                           │
│         currentChunk = chunkManager │
│             .getChunk(lastChunkId)  │
└─────────────────────────────────────┘
```

## 6. 配置参数

| 配置项 | 默认值 | 描述 |
|--------|--------|------|
| maxChunkSize | 64MB | 单个 Chunk 最大大小 |
| syncOnWrite | false | 每次写入是否 fsync |

## 7. 崩溃恢复场景

### 7.1 正常启动

正常启动时的 Journal 恢复流程：初始化 ChunkManager → 加载 B+Tree 元数据获取 journalReplayPoint → 初始化 Journal 并加载 Region Index → 从 replayPoint 开始回放 Journal 到 MemoryTable → 系统就绪。整个过程依赖 Region 的直接定位能力，无需二分查找。

```
┌─────────────────────────────────────────────────────┐
│                  正常启动流程                        │
├─────────────────────────────────────────────────────┤
│                                                      │
│  1. 初始化 ChunkManager                             │
│     - 扫描所有 Chunk 文件                           │
│     - 按 ChunkType 分类                             │
│                                                      │
│  2. 加载 B+Tree 元数据                              │
│     - 读取 tree-metadata.pb                       │
│     - 获取 journalReplayPoint                       │
│                                                      │
│  3. 初始化 Journal                                  │
│     - 加载 Region Index                             │
│     - 恢复 currentRegion                            │
│                                                      │
│  4. 回放 Journal                                    │
│     - 从 replayPoint 直接定位                       │
│     - 恢复到 MemoryTable                            │
│                                                      │
│  5. 系统就绪                                        │
│                                                      │
└─────────────────────────────────────────────────────┘
```

### 7.2 崩溃后恢复

四种崩溃恢复场景：(1) Journal 写入后 MemoryTable 未更新——从 replayPoint 回放恢复；(2) Tree Dump 过程中崩溃——使用上一个版本的 B+Tree，回放 Journal 恢复；(3) Journal Chunk 部分写入——CRC32 校验失败，丢弃不完整条目；(4) Region Index 损坏——扫描所有 JOURNAL Chunk 重建索引。

```
┌─────────────────────────────────────────────────────┐
│                  崩溃恢复流程                        │
├─────────────────────────────────────────────────────┤
│                                                      │
│  场景1: Journal 写入后，MemoryTable 未更新          │
│  ─────► 从 replayPoint 直接定位并回放               │
│                                                      │
│  场景2: Tree Dump 过程中崩溃                        │
│  ─────► 使用上一个版本的 B+Tree                     │
│         回放 Journal 恢复未 Dump 的数据             │
│                                                      │
│  场景3: Journal Chunk 部分写入（未完整关闭）        │
│  ─────► Checksum 校验失败，丢弃不完整条目           │
│                                                      │
│  场景4: Region Index 损坏                           │
│  ─────► 扫描所有 JOURNAL Chunk 重建索引            │
│                                                      │
└─────────────────────────────────────────────────────┘
```

## 8. 数据一致性保证

### 8.1 写入顺序

写入顺序保证数据安全：必须先 Journal 写入成功，再更新 MemoryTable，最后返回客户端。如果 Journal 写入失败则返回错误、数据不变；如果 MemoryTable 更新失败则可通过 Journal 回放恢复。这是 WAL（Write-Ahead Logging）原则的核心。

```
写入顺序保证:

1. Journal 写入成功
        │
        ▼
2. MemoryTable 更新
        │
        ▼
3. 返回成功给客户端

如果步骤1失败 → 返回错误，数据不变
如果步骤2失败 → 回放 Journal 恢复
```

### 8.2 Checksum 校验

CRC32 校验由 Write Item 层统一提供，覆盖 Header + Body。写入时计算并存储 CRC32，读取时重新计算并比对。不匹配则说明数据损坏，回放时丢弃该条目及后续数据。

```
CRC32 校验由 Write Item 层统一提供（详见 design-data-integrity.md）：

写入时:
  Journal Entry 序列化为 Body
  Write Item 封装 Body，计算 CRC32(Header + Body)
  写入 Chunk

读取时:
  从 Chunk 读取 Write Item
  验证 CRC32(Header + Body) == storedCrc32
  if 不匹配: throw CorruptionException
  解析 Body 为 Journal Entry
```

## 9. 与其他模块的交互

```
                    写入流程
┌──────────┐     write(op)      ┌──────────┐
│ KVStore  │ ─────────────────► │ Journal  │
└──────────┘                    └──────────┘
      │                               │
      │                               │ writeJournal
      │                               ▼
      │                         ┌──────────┐
      │                         │  Chunk   │
      │                         │ Manager  │
      │                         └──────────┘
      │
      │  update MemoryTable
      ▼
┌──────────┐
│MemoryTable│
└──────────┘


                    恢复流程
┌──────────┐  replayFrom(replayPoint) ┌──────────┐
│ KVStore  │ ◄─────────────────────── │ Journal  │
└──────────┘                          └──────────┘
      │                                     │
      │                                     │ findByMajor
      │                                     ▼
      │                               ┌──────────┐
      │                               │  Region  │
      │                               │  Index   │
      │                               └──────────┘
      │
      │  重放操作
      ▼
┌──────────┐
│MemoryTable│
└──────────┘
```

## 10. 注意事项

1. **写入顺序**：必须先写 Journal，再更新 MemoryTable
2. **Checksum**：每个条目都要校验，防止数据损坏
3. **清理策略**：Tree Dump 后及时 truncate Journal，避免无限增长
4. **性能权衡**：syncOnWrite 影响性能，但保证数据安全
5. **Chunk 轮转**：单 Chunk 达到 maxSize 时自动轮转，Region major++
6. **Region 定位**：通过 major 直接定位 Chunk，无需二分查找
7. **统一存储**：Journal 和 Page 共享 ChunkManager，简化存储层

## 11. Journal Truncate 策略

### 11.1 概述

Journal 数据是 KVStore 数据安全的基础，如果有全部 Journal，就可以重建整个 KVStore。Journal Truncate 的目的是回收不再需要的 Journal 空间，同时保证数据安全。

```
┌─────────────────────────────────────────────────────────────┐
│                  Journal Truncate 架构                       │
├─────────────────────────────────────────────────────────────┤
│                                                              │
│  Journal 保留策略：                                          │
│  ┌─────────────────────────────────────────────────────┐    │
│  │ 策略1：基于版本（Version-based）                     │    │
│  │   - 最旧的一个 version 不再使用的 journal 可以回收  │    │
│  │   - 与 GC 版本管理配合                              │    │
│  └─────────────────────────────────────────────────────┘    │
│                                                              │
│  ┌─────────────────────────────────────────────────────┐    │
│  │ 策略2：基于时间（Time-based）✓ 优先采用             │    │
│  │   - 14 天内生成的 journal 不要回收                  │    │
│  │   - 简单可靠，易于理解                              │    │
│  └─────────────────────────────────────────────────────┘    │
│                                                              │
│  回收粒度：                                                  │
│  ┌─────────────────────────────────────────────────────┐    │
│  │ 按 Chunk 粒度回收                                    │    │
│  │   - 一个 Region 对应一个 Chunk                      │    │
│  │   - 等同于按 Region 回收                            │    │
│  └─────────────────────────────────────────────────────┘    │
│                                                              │
└─────────────────────────────────────────────────────────────┘
```

### 11.2 Truncate 策略详解

#### 策略1：基于版本（Version-based）

```
基于版本的 Truncate：

触发条件：
  - 当 Tree GC 回收了某个旧版本时
  - 该版本对应的 Journal 可以被回收

判断逻辑：
  oldestVersion = currentVersion - maxVersions
  if (oldestVersion < 1):
      return  // 没有可回收的版本
  
  // 找到该版本的 journalReplayPoint
  replayPoint = getTreeMetadata(oldestVersion)
      .journalReplayPoint
  
  // 回收该 Region 之前的所有 Journal
  truncateBefore(replayPoint.regionMajor)

优势：
  - 与 GC 版本管理紧密配合
  - 精确控制保留的版本数

劣势：
  - 需要维护版本与 Journal 的映射关系
  - 实现相对复杂
```

#### 策略2：基于时间（Time-based）✓ 优先采用

```
基于时间的 Truncate：

触发条件：
  - 定期检查（如每天一次）
  - 或者 Journal 空间超过阈值

判断逻辑：
  retentionDays = config.truncateRetentionDays  // 默认 14 天
  cutoffTime = now() - retentionDays * 24 * 60 * 60 * 1000
  
  // 找到创建时间早于 cutoffTime 的 Region
  for entry in regionIndex:
      if (entry.createdAt < cutoffTime):
          // 检查是否可以安全删除
          if (canSafelyDelete(entry)):
              truncateRegion(entry)

canSafelyDelete(entry):
    // 检查该 Region 是否还在使用
    // 即是否有未 Dump 的数据依赖该 Region
    oldestReplayPoint = getOldestReplayPoint()
    return entry.major < oldestReplayPoint.regionMajor

优势：
  - 简单直观，易于理解和实现
  - 不依赖版本管理
  - 提供足够的安全边际（14 天）

劣势：
  - 可能保留更多的 Journal 数据
  - 空间利用率略低
```

### 11.3 Truncate 流程

Journal Truncate 的具体执行步骤：计算保留边界（时间或版本）→ 获取最旧 ReplayPoint → 筛选可删除的 Region（同时满足时间和安全条件）→ 按 Chunk 粒度删除文件和 Region Index 条目 → 持久化更新后的 Region Index。

```
truncateJournal()
    │
    ▼
┌─────────────────────────────────────┐
│  1. 确定保留边界                    │
│     retentionDays = config          │
│         .truncateRetentionDays      │
│     cutoffTime = now() -            │
│         retentionDays * 24 * 60 *   │
│         60 * 1000                   │
└─────────────────────────────────────┘
    │
    ▼
┌─────────────────────────────────────┐
│  2. 获取当前最旧的 ReplayPoint      │
│     oldestReplayPoint =             │
│         getOldestReplayPoint()      │
│     // 从所有 Tree 版本中找到       │
│     // 最早的 journalReplayPoint    │
└─────────────────────────────────────┘
    │
    ▼
┌─────────────────────────────────────┐
│  3. 筛选可删除的 Region             │
│     candidates = []                 │
│     for entry in regionIndex:       │
│         // 时间条件                 │
│         if (entry.createdAt >=      │
│             cutoffTime):            │
│             continue  // 保留       │
│                                      │
│         // 安全条件                 │
│         if (entry.major >=          │
│             oldestReplayPoint       │
│                 .regionMajor):      │
│             continue  // 保留       │
│                                      │
│         candidates.add(entry)       │
└─────────────────────────────────────┘
    │
    ▼
┌─────────────────────────────────────┐
│  4. 按 Chunk 粒度删除               │
│     for entry in candidates:        │
│         // 删除 Chunk 文件          │
│         chunkManager.deleteChunk(   │
│             entry.chunkId)          │
│                                      │
│         // 从 Region Index 移除     │
│         regionIndex.remove(entry)   │
│                                      │
│         log.info(                   │
│             "Truncated journal " +  │
│             "region: " + entry.major)│
└─────────────────────────────────────┘
    │
    ▼
┌─────────────────────────────────────┐
│  5. 持久化更新后的 Region Index     │
│     persistRegionIndex()            │
└─────────────────────────────────────┘
```

### 11.4 安全性保证

Truncate 的安全性通过四重检查保证：时间检查（超过保留期）、ReplayPoint 检查（不删除仍在使用的 Journal）、原子性保证（先删 Chunk 再更新 Region Index，使用 tmp + rename）、崩溃恢复（Region Index 更新失败时下次启动重建）。

```
安全性检查：

1. 时间检查
   - 只删除超过保留期的 Journal
   - 默认 14 天，提供足够的安全边际

2. ReplayPoint 检查
   - 确保不会删除仍在使用的 Journal
   - 通过 oldestReplayPoint 判断

3. 原子性保证
   - 先删除 Chunk 文件
   - 再更新 Region Index
   - 使用临时文件 + rename 保证原子性

4. 崩溃恢复
   - 如果 Region Index 更新失败
   - 下次启动时会重建索引
   - 已删除的 Chunk 会被忽略
```

### 11.5 配置参数

| 参数 | 默认值 | 描述 |
|------|--------|------|
| truncateRetentionDays | 14 | Journal 保留天数 |
| truncateCheckInterval | 86400000 | Truncate 检查间隔（24小时） |
| truncateEnabled | true | 是否启用 Journal Truncate |

### 11.6 监控指标

| 指标 | 描述 |
|------|------|
| journal.truncate.count | Truncate 执行次数 |
| journal.truncate.regions | Truncate 的 Region 数量 |
| journal.truncate.bytes | Truncate 回收的字节数 |
| journal.total.size | Journal 总大小 |
| journal.oldest.region | 最旧的 Region 编号 |

### 11.7 最佳实践

Journal 管理最佳实践：生产环境保留 14 天，开发/测试可缩短；定期检查（每天一次）或空间超限时触发 Truncate；监控 Journal 空间使用率（> 80% 告警）；紧急磁盘空间不足时可手动触发但需确保有备份。

```
Journal Truncate 最佳实践：

1. 保留期设置
   - 生产环境：14 天（默认）
   - 开发环境：7 天
   - 测试环境：3 天

2. 触发时机
   - 定期检查（每天一次）
   - Journal 空间超过阈值（如 100GB）
   - 手动触发

3. 监控告警
   - Journal 空间使用率 > 80% 告警
   - Truncate 失败告警
   - Journal 总大小异常告警

4. 紧急情况
   - 磁盘空间不足时，可以手动触发 Truncate
   - 可以临时减少保留期（不推荐）
   - 确保有足够的备份
```
