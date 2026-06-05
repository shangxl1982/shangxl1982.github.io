# B+树元数据设计

## 1. 概述

B+树元数据包含树的关键信息，如根页位置、版本信息、Journal回放点等，是B+树启动和恢复的重要依据。

## 2. 核心数据结构

### 2.1 TreeMetadata (Tree 元数据)

| 属性 | 类型 | 描述 |
|------|------|------|
| version | long | 版本号 |
| leafPageMaxSize | int | LeafPage 最大容量（字节，默认 8KB） |
| indexPageMaxSize | int | IndexPage 最大容量（字节，默认 64KB） |
| rootLocation | SegmentLocation | 根页位置（始终为 IndexPage） |
| journalReplayPoint | JournalReplayPoint | Journal 回放点（Region + Offset） |
| mns | long | Min Not Sealed number（最小未封存 Chunk 编号） |
| createdAt | long | 创建时间戳 |
| stats | TreeStats | 树统计信息 |

### 2.2 JournalReplayPoint

| 属性 | 类型 | 描述 |
|------|------|------|
| regionMajor | long | Journal Region 主版本号 |
| regionMinor | long | Journal Region 次版本号（保留） |
| offset | int | Chunk 内偏移量 |

### 2.3 TreeStats (树统计信息)

| 属性 | 类型 | 描述 |
|------|------|------|
| leafPageCount | long | 叶页数量 |
| indexPageCount | long | 索引页数量 |
| totalEntries | long | 总条目数 |
| height | int | 树高度 |

## 3. 元数据文件

### 3.1 tree-metadata.pb 结构

`tree-metadata.pb` 位于存储根目录，使用 Protobuf 二进制格式存储。

```
{storagePath}/tree-metadata.pb
```

文件使用 `TreeMetadataFile` 消息序列化（详见 [序列化协议设计](design-serialization.md) 2.6.1 节）。包含 magic、format_version、leafPageMaxSize、indexPageMaxSize、版本列表（按版本降序，每个 entry 含 version、rootLocation、replayPoint、mns、stats）和 maxVersions。

### 3.2 元数据管理规则

| 规则 | 说明 |
|------|------|
| 版本递增 | 每次 Tree Dump，version + 1 |
| 新版本在前 | entries 数组按版本降序排列 |
| 版本保留 | 保留最近 N 个版本（默认 10） |
| 原子写入 | 使用临时文件 + rename 保证原子性 |
| Journal 回放点 | 每次 Tree Dump 成功后，记录当前 Journal Region + Offset |
| MNS 记录 | 每次 Tree Dump 成功后，记录当前 MNS（Min Not Sealed number） |

### 3.3 Journal 回放点说明

journalReplayPoint 记录了 Tree 版本对应的 Journal 回放起点（Region major + offset）。崩溃恢复时直接根据 regionMajor 定位到对应的 JOURNAL Chunk，从 offset 开始回放，无需二分查找。这使得恢复时间只取决于未 Dump 的增量数据量，与总数据量无关。

```
┌─────────────────────────────────────────────────────┐
│          Journal 回放点的作用                        │
├─────────────────────────────────────────────────────┤
│                                                      │
│  Journal Region 组织：                               │
│  ┌───────────────────────────────────────────────┐  │
│  │ Region 1 (major=1)        Region 2 (major=2)  │  │
│  │ ┌─────┬─────┬─────┐      ┌─────┬─────┬─────┐ │  │
│  │ │ PUT │ PUT │ DEL │      │ PUT │ PUT │ DEL │ │  │
│  │ └─────┴─────┴─────┘      └─────┴─────┴─────┘ │  │
│  │         ↑ offset=12345                        │  │
│  └───────────────────────────────────────────────┘  │
│                                                      │
│  journalReplayPoint = {                             │
│    regionMajor: 1,                                  │
│    regionMinor: 0,                                  │
│    offset: 12345                                    │
│  }                                                  │
│                                                      │
│  崩溃恢复时：                                        │
│  1. 加载 B+Tree 版本（rootLocation）                │
│  2. 根据 regionMajor 直接定位 Chunk                 │
│  3. 从 offset 开始读取并回放 Journal                │
│  4. 无需二分查找，直接定位回放点                    │
│                                                      │
└─────────────────────────────────────────────────────┘
```

### 3.4 MNS 说明

MNS（Min Not Sealed number）记录 Tree Dump 时的最小 OPEN Chunk 编号。GC 用它判断哪些 Chunk 可以安全回收：编号小于 MNS 的 Chunk 在 Dump 时已经是 SEALED，不会被后续版本写入新数据。示例：如果 Dump 时 MNS=3，那么 Chunk 1 和 2（编号 < 3）可以被 GC 处理。

```
MNS (Min Not Sealed number) 定义：

MNS = min(chunkNumber where status == OPEN)

作用：
  - 记录 Tree Dump 时的最小未封存 Chunk 编号
  - 用于 GC 判断哪些 Chunk 可以被回收
  - 小于 MNS 的 Chunk 不会被新版本写入

示例：
  Tree Version 3:
    - Chunk 1: SEALED
    - Chunk 2: SEALED
    - Chunk 3: OPEN      ← MNS = 3
    - Chunk 4: OPEN
  
  记录：mns = 3
  
  GC 判断：
    - 回收 Version 1 (当前 Version 3)
    - Version 1 的 mns = 1
    - Chunk 1: number=1 < mns=1? No
    - 实际判断：chunkNumber < mns
    - Chunk 1: number=1 < mns=1? No
    - Chunk 2: number=2 < mns=1? No
    - 没有可回收的 Chunk
  
  另一个示例：
    - 回收 Version 1 (当前 Version 3)
    - Version 1 的 mns = 3
    - Chunk 1: number=1 < mns=3? Yes, 可回收
    - Chunk 2: number=2 < mns=3? Yes, 可回收
```

### 3.5 启动加载流程

启动时加载 tree-metadata.pb，选择最新版本（entries[0]）获取 rootLocation、journalReplayPoint 和缓存配置，初始化 B+Tree 和 LRU Cache。也可指定历史版本用于回滚恢复。加载后返回 journalReplayPoint 供 KVStore 回放 Journal 使用。

```
init()
    │
    ▼
┌─────────────────────────────────────┐
│  1. 加载 tree-metadata.pb         │
│     解析 JSON，获取版本列表         │
└─────────────────────────────────────┘
    │
    ▼
┌─────────────────────────────────────┐
│  2. 选择版本                        │
│     默认使用最新版本（entries[0]）  │
│     也可指定历史版本                │
└─────────────────────────────────────┘
    │
    ▼
┌─────────────────────────────────────┐
│  3. 初始化 B+Tree                   │
│     currentVersion = entry.version  │
│     rootLocation = entry.rootLocation│
│     journalReplayPoint =            │
│         entry.journalReplayPoint    │
└─────────────────────────────────────┘
    │
    ▼
┌─────────────────────────────────────┐
│  4. 初始化缓存                      │
│     readCache = new LRUCache()      │
└─────────────────────────────────────┘
    │
    ▼
┌─────────────────────────────────────┐
│  5. 返回 journalReplayPoint         │
│     (供 KVStore 回放 Journal 使用)  │
└─────────────────────────────────────┘
```

## 4. 与其他模块的交互

- **B+Tree**：读写元数据文件，管理版本信息
- **KVStore**：使用元数据中的 journalReplayPoint 进行崩溃恢复
- **PageManager**：根据元数据中的 rootLocation 加载根页

## 5. 相关文档

- [B+树设计](design-bplustree.md)：整体 B+树设计
- [Page 设计](design-page.md)：叶页和索引页的详细设计
- [存储设计](design-storage.md)：Chunk 和 ChunkManager 设计
