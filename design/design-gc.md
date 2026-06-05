# GC（垃圾回收）设计

## 1. 概述

GC（Garbage Collection）负责回收不再使用的存储空间，包括：

- **Chunk 生命周期管理**：从 OPEN 到 SEALED 到 DELETED 的状态转换
- **空间回收**：识别和回收无效数据占用的空间
- **Occupancy 跟踪**：精确跟踪每个 Chunk 的有效数据量
- **版本管理**：基于 Tree 版本的 GC 策略

```
┌─────────────────────────────────────────────────────────────┐
│                      GC 架构概览                             │
├─────────────────────────────────────────────────────────────┤
│                                                              │
│  Chunk Lifecycle                                            │
│  ┌──────┐    seal()    ┌────────┐    gc()    ┌─────────┐  │
│  │ OPEN │ ───────────► │ SEALED │ ─────────► │ DELETING │  │
│  └──────┘              └────────┘            └─────────┘  │
│      │                      │                      │       │
│      │                      │                      ▼       │
│      │                      │               ┌─────────┐   │
│      │                      └──────────────►│ DELETED │   │
│      │                                      └─────────┘   │
│      │                                                    │
│      │  extend()                                          │
│      └─────────────────────────────────────►              │
│                                            (保持 OPEN)     │
│                                                           │
│  GC 流程                                                  │
│  ┌──────────────┐   scan    ┌──────────────┐            │
│  │ Occupancy    │ ────────► │ GC Candidate │            │
│  │ Tracking     │           │ Selection    │            │
│  └──────────────┘           └──────────────┘            │
│         │                          │                      │
│         │                          ▼                      │
│         │                  ┌──────────────┐             │
│         │                  │ Full GC      │             │
│         │                  │ Partial GC   │             │
│         │                  │ Hole Punching│             │
│         │                  └──────────────┘             │
│         │                          │                      │
│         └──────────────────────────┘                      │
│                                                              │
└─────────────────────────────────────────────────────────────┘
```

## 2. Chunk 生命周期管理

### 2.1 Chunk 类型

Chunk 分为 INDEX（存储索引页）、LEAF（存储叶页）和 JOURNAL（存储操作日志）三种类型。INDEX 和 LEAF 共存于 data/ 目录下，通过 Chunk Header 中的 ChunkType 字段区分。分离类型便于针对性优化 GC 策略（如 INDEX Chunk 通常较小，LEAF 数据量大）。

```
Chunk 类型定义：

enum ChunkType {
    INDEX,     // 存储 Index Page 数据
    LEAF,      // 存储 Leaf Page 数据
    JOURNAL    // 存储 Journal 数据
}

类型说明：
  - INDEX Chunk: 仅存储索引页数据
  - LEAF Chunk: 仅存储叶页数据
  - JOURNAL Chunk: 存储操作日志
  - INDEX 和 LEAF Chunk 共存于 data/ 目录，通过 ChunkType 区分

分类存储优势：
  - 针对性优化：INDEX 和 LEAF 可以有不同的 GC 策略
  - 空间管理：INDEX 通常较小，LEAF 数据量大
  - 独立管理：GC 可以针对不同 ChunkType 分别处理
```

### 2.2 Chunk 状态

Chunk 的生命周期状态：OPEN（可写）→ SEALED（已封存，不可写）→ DELETING（正在删除）→ DELETED（已删除）。OPEN 状态下可通过 extend() 延长保活时间，但一旦 SEALED 就不可逆。Seal 触发条件：Chunk 写满、超过保活时间、或使用方主动调用。

```
Chunk 状态定义：

enum ChunkStatus {
    OPEN,      // 可写状态
    SEALED,    // 已封存，不可写
    DELETING,  // 正在删除
    DELETED    // 已删除
}

状态转换：
  OPEN ──seal()──► SEALED ──gc()──► DELETING ──delete()──► DELETED
    │
    │ extend()
    │
    └──(延长 OPEN 时间)──► OPEN

转换条件：
  OPEN → SEALED:
    - Chunk 写满时自动 seal
    - 超过保活时间自动 seal
    - 使用方主动调用 seal()
  
  OPEN → OPEN:
    - 使用方调用 extend() 延长保活时间
    - 仅在 OPEN 状态下可用
  
  SEALED → DELETING:
    - GC 流程开始删除 Chunk
  
  DELETING → DELETED:
    - Chunk 文件删除完成

重要说明：
  - extend() 只能在 OPEN 状态下调用
  - SEALED 状态不可逆转回 OPEN
  - 一旦封存，Chunk 永久不可写
```

### 2.3 Chunk 编号

每个 Chunk 有一个单调递增的全局唯一编号（chunkNumber），用于计算 MNS（Min Not Sealed number）。MNS 是最小的 OPEN 状态 Chunk 编号，用于 GC 判断：编号小于 MNS 的 Chunk 在 Dump 时已经是 SEALED 状态，不会被后续版本写入新数据，因此可以安全地进行 GC。

**崩溃重启时的 MNS 恢复**：崩溃后所有现有 Chunk 都视为 SEALED（不继续写入可能有不完整数据的 Chunk），重启时从 chunk-metadata.pb 恢复最大 chunkNumber，然后分配新的 OPEN Chunk。此时运行时 MNS = 新 Chunk 的编号，所有旧 Chunk 编号都 < MNS。对于 GC 来说，它使用的是 tree-metadata.pb 中每个版本记录的**历史 MNS**，不受崩溃影响。

```
Chunk 编号规则：

chunkNumber: long
  - 单调递增
  - 全局唯一
  - 从 1 开始

MNS (Min Not Sealed number):
  - 最小未封存的 Chunk 编号
  - 用于 GC 判断

示例：
  Chunk 1: SEALED
  Chunk 2: SEALED
  Chunk 3: OPEN      ← MNS = 3
  Chunk 4: OPEN
  
  当前 MNS = 3

MNS 边界条件：

情况1：所有 Chunk 都是 SEALED
  Chunk 1: SEALED
  Chunk 2: SEALED
  Chunk 3: SEALED
  
  MNS = currentMaxChunkNumber + 1 = 4
  
  此时所有 Chunk 都满足 chunkNumber < MNS
  都可以作为 GC Candidate（根据 occupancyRatio 决定 GC 策略）

情况2：没有 Chunk
  MNS = 1 (初始值)
  
  没有可 GC 的 Chunk

情况3：第一个 Chunk 就是 OPEN
  Chunk 1: OPEN
  
  MNS = 1
  
  没有 chunkNumber < 1 的 Chunk
  没有可 GC 的 Chunk
```

### 2.4 Chunk 保活机制

Chunk 创建时设置保活截止时间（默认 30 分钟），后台线程定期检查（5 分钟间隔）是否超时。超时的 OPEN Chunk 自动 Seal。使用方可通过 extend() 延长保活时间，避免正在使用的 Chunk 被意外 Seal。

```
Chunk 保活时间：

创建 Chunk 时：
  - 记录 keepAliveTime
  - 默认值：30 分钟
  - 记录 createdAt 时间戳

Extend 保活时间：
  - 使用方调用 extend(chunkId, duration)
  - 更新 keepAliveTime = now() + duration
  - 延长 Chunk 的 OPEN 状态

自动 Seal：
  - 后台线程定期检查
  - if (now() > keepAliveTime && status == OPEN):
        seal(chunk)
  - 检查间隔：5 分钟

手动 Seal：
  - Chunk 写满时自动 seal
  - 使用方主动调用 seal(chunkId)
```

### 2.5 Chunk 元数据

Chunk 元数据包含身份信息（chunkId、chunkNumber、chunkType、ownerId、namespaceId）、状态信息（status、createdAt、keepAliveTime）和空间信息（totalSize、usedSize、occupancySize）。元数据在内存中维护 Map 结构，定期持久化到 chunk-metadata.pb 文件（使用临时文件 + rename 保证原子性）。

```
Chunk 元数据结构：

class ChunkMetadata {
    chunkId: UUID
    chunkNumber: long
    chunkType: ChunkType
    ownerId: UUID           // KVStore 实例 UUID
    namespaceId: UUID       // 命名空间 UUID
    status: ChunkStatus
    createdAt: long
    keepAliveTime: long
    totalSize: long        // Chunk 最大容量（如 64MB）
    usedSize: long         // 已写入的数据大小
    occupancySize: long    // 有效数据大小（<= usedSize）
    pendingGC: int          // Partial GC 等待标志（0=false, 1=true, 4 bytes 对齐）
}

字段说明：
  - totalSize: Chunk 的最大容量，固定值（如 64MB）
  - usedSize: 已经写入的总数据大小（含 Padding），随着写入增长
  - occupancySize: 有效数据占用的空间（含 Padding），可能小于 usedSize
  
  关系：occupancySize <= usedSize <= totalSize
  所有大小均按 Write Item 对齐后计算（含 Padding），保持口径一致。
  
  示例：
    totalSize = 64MB
    usedSize = 50MB (已写入 50MB 数据)
    occupancySize = 30MB (其中 30MB 是有效数据，20MB 已被 decommission)
    
    occupancyRatio = 30MB / 64MB = 46.875%

元数据存储：

文件位置：
  {storagePath}/chunk-metadata.pb

文件使用 `ChunkMetadataFile` 消息序列化（详见 [序列化协议设计](design-serialization.md) 2.6.3 节），Protobuf 二进制格式。

更新时机：
  - Chunk 创建时
  - Chunk 状态变更时 (OPEN → SEALED → DELETING → DELETED)
  - Chunk occupancy 变更时（批量更新）
  - Chunk pendingGC 标志变更时

更新策略：
  - 内存中维护 Map<UUID, ChunkMetadata>
  - 定期持久化到文件（如每次 Dump 后）
  - 使用临时文件 + rename 保证原子性
  
  持久化流程：
    1. 写入到临时文件 chunk-metadata.pb.tmp
    2. fsync 确保数据落盘
    3. rename 到 chunk-metadata.pb
    4. fsync 父目录

加载时机：
  - 系统启动时
  - 从文件加载所有 Chunk 元数据
  - 重建内存中的 Map
```

## 3. Occupancy 跟踪

### 3.1 Occupancy 概念

Occupancy 是 Chunk 中有效数据的大小。每次 Dump 时新写入 Page 增加 occupancy，旧 Page 被替换（decommission）则减少 occupancy。**occupancy 统一按 Write Item 的对齐后大小计算（含 Header、CRC32 和 Padding）**，即 Chunk 中实际占用的空间。写入和 decommission 使用相同的计算口径，确保累计不产生误差，Chunk 空间释放后 occupancy 能精确归零。occupancyRatio = occupancySize / totalSize，用于判断 GC 策略：0% 执行 Full GC，< 5% 执行 Partial GC，>= 5% 暂不处理。

```
Occupancy 定义：

occupancySize: Chunk 中有效数据占用的空间
  - 按 Write Item 对齐后大小计算（含 Padding）
  - 新写 Page：增加 alignedSize（= Write Item 总大小）
  - Decommission Page：减少 alignedSize
  - 写入和 decommission 使用相同口径，确保归零
  - 用于判断 Chunk 是否可以被 GC

occupancyRatio = occupancySize / totalSize
  - 0%：Chunk 完全空，可回收
  - < 5%：Chunk 几乎空，可 Partial GC
  - > 5%：Chunk 有效数据较多，暂不处理
```

### 3.2 Occupancy 变更记录

每次 Tree Dump 产生一个 DumpOccupancyRecord，记录该次 Dump 引起的所有 Chunk occupancy 变化（正数增加、负数减少）和当前 MNS。记录存储在 occupancy/{version}.pb 文件中，GC 时累计计算各 Chunk 的最终 occupancy。

```
每次 Tree Dump 记录：

class OccupancyDelta {
    chunkId: UUID
    deltaSize: long  // 正数表示增加，负数表示减少
}

class DumpOccupancyRecord {
    treeVersion: long
    mns: long  // 当前的 MNS
    deltas: List<OccupancyDelta>
    
    // 示例
    deltas = [
        {chunkId: "chunk-1", deltaSize: -4096},  // decommission page
        {chunkId: "chunk-2", deltaSize: +8192},  // new page
        {chunkId: "chunk-3", deltaSize: -2048}   // decommission page
    ]
}

存储位置：
  - 文件：{storagePath}/occupancy/{version}.pb
  - 每次 Dump 生成一个记录文件
```

### 3.3 Occupancy 累计计算

Occupancy 从 0 开始，每次 Dump 累加对应的 delta。示例：Chunk 初始 occupancy=0，Dump 1 写入 Page A(4096B) 后变为 4096，Dump 2 替换 Page A(-4096) 并写入 Page B(8192B) 后变为 8192，Dump 3 替换 Page B(-8192) 后变为 0，此时 Chunk 完全空可回收。防御性编程确保 occupancy 不会出现负数。

```
Occupancy 计算流程：

初始状态：
  chunk.occupancySize = 0

每次 Dump：
  for delta in dumpRecord.deltas:
      chunk = getChunk(delta.chunkId)
      
      // 检查 Chunk 是否存在（防御性编程）
      if (chunk == null || chunk.status == DELETED):
          continue  // 跳过已删除的 Chunk
      
      chunk.occupancySize += delta.deltaSize
      
      // 确保不会出现负数
      if (chunk.occupancySize < 0):
          chunk.occupancySize = 0

示例（所有大小均为 Write Item 对齐后大小，含 Padding）：
  Chunk 1 初始：occupancySize = 0
  
  Dump 1:
    - 写入 Page A，alignedSize = 4096（原始数据 + Header + CRC32 + Padding）
    - occupancySize = 4096
  
  Dump 2:
    - Decommission Page A，alignedSize = 4096
    - 写入 Page B，alignedSize = 8192
    - occupancySize = 4096 - 4096 + 8192 = 8192
  
  Dump 3:
    - Decommission Page B，alignedSize = 8192
    - occupancySize = 8192 - 8192 = 0
    - Chunk 1 完全空，可回收
    
  关键点：写入和 decommission 使用相同的 alignedSize，
  不会因为 Padding 产生累计误差。
```

## 4. MNS 与版本管理

### 4.1 MNS 记录

MNS（Min Not Sealed number）在每次 Tree Dump 时记录到 tree-metadata.pb 中。MNS 的含义是：小于 MNS 的 Chunk 在 Dump 时已经 SEALED，不会被后续写入操作使用，因此 GC 可以安全地处理这些 Chunk。

```
MNS (Min Not Sealed number) 定义：

MNS = min(chunkNumber where status == OPEN)

作用：
  - 小于 MNS 的 Chunk：不会被新版本写入，可被 GC
  - 大于等于 MNS 的 Chunk：可能被新版本写入，不可 GC

记录时机：
  - 每次 Tree Dump 时记录当前 MNS
  - 存储在 tree-metadata.pb 中
```

### 4.2 版本与 MNS 关系

通过时间线示例说明版本与 MNS 的关系：每次 Dump 记录当时的 MNS，GC 回收旧版本时根据该版本的 MNS 确定哪些 Chunk 可以处理。MNS 保证了 GC 不会误删仍在被新版本引用的 Chunk。

```
版本管理示例：

时间线：
  T1: Tree v1 Dump
      - Chunk 1 (OPEN), Chunk 2 (OPEN)
      - MNS = 1 (最小未封存的 Chunk 编号)
      - Dump v1 记录：mns = 1
  
  T2: Tree v2 Dump
      - Chunk 1 (SEALED), Chunk 2 (OPEN), Chunk 3 (OPEN)
      - MNS = 2
      - Dump v2 记录：mns = 2
  
  T3: Tree v3 Dump
      - Chunk 1 (SEALED), Chunk 2 (SEALED), Chunk 3 (OPEN), Chunk 4 (OPEN)
      - MNS = 3
      - Dump v3 记录：mns = 3

MNS 的含义：
  - MNS 记录了 Dump 时的最小未封存 Chunk 编号
  - 小于 MNS 的 Chunk：在 Dump 时已经是 SEALED 状态
  - 大于等于 MNS 的 Chunk：在 Dump 时是 OPEN 状态

GC 判断逻辑：
  当回收版本 V 时：
    1. 读取版本 V 的 mns
    2. 对于所有 SEALED 状态的 Chunk：
       - 如果 chunkNumber < mns：可以被 GC 处理
       - 如果 chunkNumber >= mns：不能被 GC 处理
    
  原因：
    - chunkNumber < mns 的 Chunk 在版本 V Dump 时已经是 SEALED
    - 这些 Chunk 不会被后续版本写入新数据
    - 因此可以安全地被 GC 处理

具体示例：
  当前版本：v3 (maxVersions = 2)
  需要回收：v1 (v3 - 2 = v1)
  
  v1 的 mns = 1：
    - Chunk 1: number=1 < mns=1? No (不满足)
    - Chunk 2: number=2 < mns=1? No (不满足)
    - 没有可 GC 的 Chunk
  
  v2 的 mns = 2：
    - Chunk 1: number=1 < mns=2? Yes (可 GC)
    - Chunk 2: number=2 < mns=2? No (不满足)
    - Chunk 1 可以被 GC 处理
  
  v3 的 mns = 3：
    - Chunk 1: number=1 < mns=3? Yes (可 GC)
    - Chunk 2: number=2 < mns=3? Yes (可 GC)
    - Chunk 3: number=3 < mns=3? No (不满足)
    - Chunk 1 和 Chunk 2 可以被 GC 处理
```

### 4.3 GC 版本策略

版本保留策略：保留最近 maxVersions（默认 10）个版本。当版本数超限时触发 GC，回收最旧版本。回收时获取该版本的 MNS，选择编号小于 MNS 且 SEALED 的 Chunk 作为 Candidate，按 occupancyRatio 分类执行 Full GC 或 Partial GC。

```
版本保留策略：

maxVersions: 保留的最大版本数（默认 10）

当前版本：C
最旧有效版本：C - maxVersions + 1

GC 触发条件：
  - 版本数 > maxVersions
  - 需要回收最旧版本

GC 流程：
  1. 获取最旧版本的 MNS
     oldestVersion = C - maxVersions
     mns = getMNS(oldestVersion)
  
  2. 选择 GC Candidate
     candidates = chunks.where(
         chunk.number < mns && 
         chunk.status == SEALED
     )
  
  3. 执行 GC
     for chunk in candidates:
         performGC(chunk)
```

## 5. GC 流程

### 5.1 GC Candidate 选择

GC Candidate 选择分三步：获取目标版本的 MNS（最小未封存 Chunk 编号），筛选出 SEALED 且编号小于 MNS 的 Chunk（这些 Chunk 不会再被新版本写入），然后按 occupancyRatio 升序排列。occupancyRatio 为 0 的 Chunk 直接执行 Full GC（删除文件），小于 5% 的执行 Partial GC（迁移有效数据后删除），其余暂不处理。

```
GC Candidate 选择流程：

selectGCCandidates(targetVersion):
    │
    ▼
┌─────────────────────────────────────┐
│  1. 获取目标版本的 MNS              │
│     mns = getMNS(targetVersion)     │
└─────────────────────────────────────┘
    │
    ▼
┌─────────────────────────────────────┐
│  2. 筛选 SEALED 状态的 Chunk        │
│     sealedChunks = chunks.where(    │
│         status == SEALED            │
│     )                               │
└─────────────────────────────────────┘
    │
    ▼
┌─────────────────────────────────────┐
│  3. 筛选 chunkNumber < MNS 的 Chunk │
│     candidates = sealedChunks.where(│
│         chunkNumber < mns           │
│     )                               │
└─────────────────────────────────────┘
    │
    ▼
┌─────────────────────────────────────┐
│  4. 按 occupancyRatio 升序排序      │
│     candidates.sort(                │
│         occupancyRatio, ASC         │
│     )                               │
│     // 优先处理 occupancyRatio 小的 │
└─────────────────────────────────────┘
    │
    ▼
┌─────────────────────────────────────┐
│  5. 分类                            │
│     fullGC = occupancyRatio == 0    │
│     partialGC = occupancyRatio < 0.05│
│     skip = occupancyRatio >= 0.05   │
└─────────────────────────────────────┘
```

### 5.2 Full GC

Full GC 处理 occupancyRatio 为 0 的 Chunk（即 Chunk 中所有数据都已被 decommission）。操作非常简单：验证 Chunk 状态和 occupancy，更新状态为 DELETING，删除 Chunk 文件，更新状态为 DELETED，从元数据中移除。时间复杂度 O(1)，是最高效的空间回收方式。

```
Full GC 流程（occupancyRatio == 0）：

performFullGC(chunk):
    │
    ▼
┌─────────────────────────────────────┐
│  1. 检查 Chunk 状态                 │
│     if (chunk.status != SEALED):    │
│         return                      │
└─────────────────────────────────────┘
    │
    ▼
┌─────────────────────────────────────┐
│  2. 检查 Occupancy                  │
│     if (chunk.occupancySize != 0):  │
│         return                      │
└─────────────────────────────────────┘
    │
    ▼
┌─────────────────────────────────────┐
│  3. 更新状态为 DELETING             │
│     chunk.status = DELETING         │
│     persistMetadata()               │
└─────────────────────────────────────┘
    │
    ▼
┌─────────────────────────────────────┐
│  4. 删除 Chunk 文件                 │
│     deleteFile(chunk.path)          │
└─────────────────────────────────────┘
    │
    ▼
┌─────────────────────────────────────┐
│  5. 更新状态为 DELETED              │
│     chunk.status = DELETED          │
│     persistMetadata()               │
└─────────────────────────────────────┘
    │
    ▼
┌─────────────────────────────────────┐
│  6. 从元数据中移除                  │
│     removeChunkMetadata(chunk.id)   │
└─────────────────────────────────────┘

性能：
  - 时间复杂度：O(1)
  - 磁盘 I/O：删除文件 + 更新元数据
  - 适用场景：Chunk 完全空
```

### 5.3 Partial GC

Partial GC 处理 occupancyRatio 小于 5% 的 Chunk。由于这些 Chunk 中仍有少量有效 Page，不能直接删除文件，需要先迁移有效数据。关键优化是**批量处理**：收集所有候选 Chunk，一次性扫描 B+Tree 找出所有指向这些 Chunk 的 Page 引用，将这些 Page 加入下次 Dump 的重写队列。下次 Dump 时会将这些 Page 写入新 Chunk，释放旧 Chunk 的引用。Dump 完成后检查 Chunk occupancy 是否降为 0，是则执行 Full GC。

```
Partial GC 流程（occupancyRatio < 5%）：

批量处理优化：
  - 先收集一批 occupancyRatio < 5% 的 Chunk
  - 做一次 Tree Scan，找出所有这些 Chunk 的有效 Page
  - 避免对每个 Chunk 单独扫描 Tree

performPartialGC(chunks):
    │
    ▼
┌─────────────────────────────────────┐
│  1. 过滤已处理的 Chunk              │
│     candidates = chunks.where(      │
│         pendingGC == false &&       │
│         status == SEALED &&         │
│         occupancyRatio < 0.05 &&    │
│         occupancyRatio > 0          │
│     )                               │
└─────────────────────────────────────┘
    │
    ▼
┌─────────────────────────────────────┐
│  2. 如果没有候选，直接返回          │
│     if (candidates.isEmpty()):      │
│         return                      │
└─────────────────────────────────────┘
    │
    ▼
┌─────────────────────────────────────┐
│  3. 一次性扫描 Tree 找到所有有效 Page│
│     chunkPagesMap = scanTreeForChunks(│
│         candidates                  │
│     )                               │
│     // 返回 Map<ChunkId, List<Page>>│
└─────────────────────────────────────┘
    │
    ▼
┌─────────────────────────────────────┐
│  4. 对每个 Chunk 处理其有效 Page    │
│     for (chunk, pages) in           │
│         chunkPagesMap:              │
│         chunk.pendingGC = true      │
│         for page in pages:          │
│             addToNextDumpQueue(page)│
│     persistMetadata()               │
└─────────────────────────────────────┘
    │
    ▼
┌─────────────────────────────────────┐
│  5. 等待下次 Dump 完成              │
│     // 下次 Dump 会重写这些 Page    │
│     // 释放这些 Chunk 的引用        │
└─────────────────────────────────────┘

批量 Tree Scanner 实现：

scanTreeForChunks(targetChunks):
    │
    ▼
┌─────────────────────────────────────┐
│  1. 准备目标 Chunk ID 集合          │
│     targetChunkIds = Set<UUID>      │
│     for chunk in targetChunks:      │
│         targetChunkIds.add(chunk.id)│
│     chunkPagesMap = Map<UUID, List> │
└─────────────────────────────────────┘
    │
    ▼
┌─────────────────────────────────────┐
│  2. 从 Root 开始遍历                │
│     root = getRoot()                │
│     queue = [root]                  │
└─────────────────────────────────────┘
    │
    ▼
┌─────────────────────────────────────┐
│  3. 遍历所有 IndexPage              │
│     while (queue not empty):        │
│         page = queue.dequeue()      │
│         if (page is IndexPage):     │
│             for entry in page:      │
│                 loc = entry.location│
│                 if (loc.chunkId in  │
│                     targetChunkIds):│
│                     // 找到目标 Chunk│
│                     chunkPagesMap   │
│                         .get(loc.chunkId)│
│                         .add(loc)   │
│                 else:               │
│                     // 继续向下遍历 │
│                     queue.add(      │
│                         getPage(loc))│
└─────────────────────────────────────┘
    │
    ▼
┌─────────────────────────────────────┐
│  4. 返回结果                        │
│     return chunkPagesMap            │
│     // Map<ChunkId, List<PageLoc>>  │
└─────────────────────────────────────┘

性能优化：
  - 时间复杂度：O(n)，n 为 Tree 中的 Page 数
  - 磁盘 I/O：一次扫描读取所有 IndexPage
  - 批量处理：一次扫描处理多个 Chunk
  - 适用场景：有多个 occupancyRatio < 5% 的 Chunk

性能对比：
  单个处理：
    - 10 个 Chunk，每个单独扫描
    - 总耗时：10 * O(n)
  
  批量处理：
    - 10 个 Chunk，一次扫描
    - 总耗时：O(n)
    - 性能提升：10 倍
```

Partial GC 状态管理：

```
Chunk 状态：
  - status: SEALED (保持不变)
  - pendingGC: int (新增字段，0=false, 1=true)
    - true: 已加入 Partial GC 等待队列
    - false: 正常状态

状态转换：
  正常 SEALED ──Partial GC 开始──► pendingGC = true
  pendingGC = true ──下次 Dump 完成──► 检查 occupancy
    - 如果 occupancySize == 0: 执行 Full GC
    - 如果 occupancySize > 0: pendingGC = false, 等待下次机会

下次 Dump 完成后的处理：

onDumpComplete():
    │
    ▼
┌─────────────────────────────────────┐
│  1. 检查所有 pendingGC 的 Chunk     │
│     pendingChunks = chunks.where(   │
│         pendingGC == true           │
│     )                               │
└─────────────────────────────────────┘
    │
    ▼
┌─────────────────────────────────────┐
│  2. 对每个 Chunk 检查 occupancy     │
│     for chunk in pendingChunks:     │
│         if (chunk.occupancySize == 0):│
│             performFullGC(chunk)    │
│         else:                       │
│             chunk.pendingGC = false │
│             persistMetadata()       │
└─────────────────────────────────────┘
```

### 5.4 Hole Punching GC（可选）

Hole Punching 是一种可选的空间回收机制，利用 Linux fallocate(FALLOC_FL_PUNCH_HOLE) 系统调用将 Chunk 文件中已 decommission 的 Page 区域打洞释放磁盘空间，而不需要删除整个文件。关键约束：不能在 Dump 时立即打洞（旧版本可能仍引用数据），必须延迟到 GC 处理该版本时执行。每次 Dump 记录 decommissionPages 列表到 occupancy/{version}.pb，GC 回收旧版本时读取该列表并执行打洞。

```
Hole Punching GC（文件系统支持）：

适用条件：
  - Chunk 存储在文件系统上
  - 文件系统支持 FALLOC_FL_PUNCH_HOLE
  - Linux: fallocate() with FALLOC_FL_PUNCH_HOLE

重要限制：
  - 不能在当前版本 Dump 时立即打洞
  - 必须延迟到 GC 处理该版本时执行
  - 原因：保留多个版本时，旧版本可能仍需引用数据

延迟打洞机制：

Dump 时：
  1. 记录 Decommission Pages 列表
     - 每个 Page 的 location (chunkId, offset, length)
     - 存储在 occupancy/{version}.pb 中
  
  2. 不立即执行打洞操作
     - 等待版本被 GC 回收时再执行

GC 时：
  1. 当版本 C-N 被 GC 回收时
  2. 读取该版本的 decommissionPages 列表
  3. 对这些 Page 执行真正的打洞操作

流程：

performHolePunchingGC(version):
    │
    ▼
┌─────────────────────────────────────┐
│  1. 读取版本的 Decommission Pages   │
│     decommissionPages =             │
│         loadDecommissionPages(version)│
└─────────────────────────────────────┘
    │
    ▼
┌─────────────────────────────────────┐
│  2. 按 Chunk 分组                   │
│     groupedByChunk =                │
│         groupBy(decommissionPages,  │
│             page => page.chunkId)   │
└─────────────────────────────────────┘
    │
    ▼
┌─────────────────────────────────────┐
│  3. 对每个 Chunk 执行打洞           │
│     for (chunkId, pages) in         │
│         groupedByChunk:             │
│         chunk = getChunk(chunkId)   │
│         if (chunk == null ||        │
│             chunk.status == DELETED):│
│             continue  // 跳过已删除 │
│         for page in pages:          │
│             punchHole(              │
│                 chunk.fd,           │
│                 page.offset,        │
│                 page.length         │
│             )                       │
└─────────────────────────────────────┘
    │
    ▼
┌─────────────────────────────────────┐
│  4. 检查 Chunk 是否全为 0           │
│     if (isAllZeros(chunk)):         │
│         performFullGC(chunk)        │
│     else:                           │
│         // 不全为 0，说明还有其他   │
│         // 版本的数据，不做处理     │
└─────────────────────────────────────┘

Punch Hole API：

punchHole(fd, offset, length):
    // Linux fallocate
    fallocate(
        fd,
        FALLOC_FL_PUNCH_HOLE | FALLOC_FL_KEEP_SIZE,
        offset,
        length
    )
    
    // 效果：
    // - 将文件指定区域置为 0
    // - 释放底层磁盘块
    // - 文件大小不变

Decommission Pages 记录格式：

使用 OccupancyRecord 消息中的 decommission_pages 字段
（详见 design-serialization.md 2.6.4 节）。
每个 DecommissionPage 包含 chunk_id、offset 和 length。

优势：
  - 实时回收空间（延迟执行）
  - 不影响旧版本数据可用性
  - 对齐部分自动清零

劣势：
  - 需要文件系统支持
  - 可能产生碎片
  - 需要处理对齐问题
  - 需要额外存储 decommissionPages 列表
```

## 6. GC 调度

### 6.1 GC 触发条件

GC 在以下条件触发：版本数超过 maxVersions 时自动回收最旧版本，后台线程定期检查（默认 1 小时），或用户手动调用。触发后计算需要回收的最旧版本号（currentVersion - maxVersions），选择 GC Candidate 并按策略执行 Full GC 或 Partial GC。

```
GC 触发条件：

1. 版本数超限
   - currentVersionCount = getTreeVersionCount()
   - if (currentVersionCount > maxVersions):
         triggerGC()
   - 回收最旧的版本：oldestVersion = currentVersion - maxVersions

2. 定期调度
   - 后台线程定期检查
   - 默认间隔：1 小时
   - 检查条件：版本数超限

3. 手动触发
   - 用户调用 triggerGC()
   - 可指定回收的版本范围

GC 触发判断逻辑：

needGC():
    currentVersionCount = getTreeVersionCount()
    return currentVersionCount > maxVersions

triggerGC():
    if (!needGC()):
        return
    
    oldestVersion = currentVersion - maxVersions
    if (oldestVersion < 1):
        return
    
    candidates = selectGCCandidates(oldestVersion)
    // 执行 GC 流程
```

### 6.2 GC 调度流程

GC 调度流程：先判断是否需要 GC（版本数是否超限），获取最旧版本及其 MNS，选择候选 Chunk 并按 occupancyRatio 分类处理（Full GC / Partial GC），最后清理旧版本元数据。整个流程在后台线程执行，不阻塞读写操作。

```
GC 调度流程：

scheduleGC():
    │
    ▼
┌─────────────────────────────────────┐
│  1. 检查是否需要 GC                 │
│     if (!needGC()):                 │
│         return                      │
└─────────────────────────────────────┘
    │
    ▼
┌─────────────────────────────────────┐
│  2. 获取最旧版本                    │
│     oldestVersion = currentVersion - maxVersions│
│     if (oldestVersion < 1):         │
│         return                      │
└─────────────────────────────────────┘
    │
    ▼
┌─────────────────────────────────────┐
│  3. 选择 GC Candidates              │
│     candidates = selectGCCandidates(│
│         oldestVersion               │
│     )                               │
└─────────────────────────────────────┘
    │
    ▼
┌─────────────────────────────────────┐
│  4. 分类处理                        │
│     fullGC = candidates.where(      │
│         occupancyRatio == 0)        │
│     partialGC = candidates.where(   │
│         0 < occupancyRatio < 0.05)  │
│                                     │
│     // Full GC                      │
│     for chunk in fullGC:            │
│         performFullGC(chunk)        │
│                                     │
│     // Partial GC（批量处理）       │
│     if (!partialGC.isEmpty()):      │
│         performPartialGC(partialGC) │
└─────────────────────────────────────┘
    │
    ▼
┌─────────────────────────────────────┐
│  5. 清理最旧版本元数据              │
│     removeVersionMetadata(          │
│         oldestVersion               │
│     )                               │
└─────────────────────────────────────┘
```

### 6.3 GC 并发控制

GC 使用 AtomicBoolean 标志位保证同一时间只有一个 GC 在执行。GC 操作不需要获取 KVStore 的读写锁，因为它只处理 SEALED 状态的 Chunk（不会被新写入），且 occupancy 计算基于已持久化的版本元数据。GC 失败不影响系统正常运行，下次调度时会重试。

```
GC 并发控制：

gcInProgress: AtomicBoolean = false

scheduleGC():
    if (!gcInProgress.compareAndSet(false, true)):
        return  // GC 已在进行
    
    try:
        // 执行 GC 流程
        ...
    finally:
        gcInProgress.set(false)

保证：
  - 同一时间只有一个 GC 在执行
  - GC 不阻塞读写操作
  - GC 失败不影响系统运行
```

## 7. 配置参数

| 配置项 | 默认值 | 描述 |
|--------|--------|------|
| maxVersions | 10 | 保留的最大版本数 |
| chunkKeepAliveTime | 30 min | Chunk 保活时间 |
| chunkSealCheckInterval | 5 min | Chunk Seal 检查间隔 |
| gcScheduleInterval | 1 hour | GC 调度间隔 |
| partialGCRatio | 0.05 | Partial GC 触发阈值（5%） |
| holePunchingEnabled | false | 是否启用 Hole Punching |

## 8. 监控指标

```
GC 监控指标：

1. Chunk 统计
   - totalChunks: 总 Chunk 数
   - openChunks: OPEN 状态 Chunk 数
   - sealedChunks: SEALED 状态 Chunk 数
   - deletedChunks: DELETED 状态 Chunk 数

2. 空间统计
   - totalSpace: 总空间
   - usedSpace: 已用空间
   - freeSpace: 可用空间
   - wastedSpace: 浪费空间（occupancySize=0 的 Chunk）

3. GC 统计
   - gcCount: GC 执行次数
   - fullGCCount: Full GC 次数
   - partialGCCount: Partial GC 次数
   - recoveredSpace: 回收的空间大小

4. 性能指标
   - avgGCDuration: 平均 GC 耗时
   - maxGCDuration: 最大 GC 耗时
   - gcThroughput: GC 吞吐量（MB/s）
```

## 9. 最佳实践

```
GC 最佳实践：

1. 合理配置版本数
   - maxVersions: 10-20
   - 平衡空间和性能

2. 及时触发 GC
   - 定期调度
   - 版本数超限时自动触发

3. 监控空间使用
   - 设置告警阈值
   - 定期检查 wasted space

4. 优化 Partial GC
   - 批量处理多个 Chunk
   - 避免频繁 Partial GC

5. 谨慎使用 Hole Punching
   - 评估文件系统支持
   - 监控碎片情况
```

## 10. 相关文档

- [存储设计](design-storage.md)：Chunk 和 ChunkManager 设计
- [B+Tree 设计](design-bplustree.md)：Tree Dump 流程
- [B+Tree 元数据设计](design-bplustree-metadata.md)：版本管理
- [KVStore 设计](design-kvstore.md)：整体架构

