# B+树设计

## 1. 概述

B+树是 LSM Tree 架构中的持久化存储组件，提供：
- **高效查询**：O(log n) 查找复杂度
- **范围查询**：支持高效范围扫描
- **持久化**：通过 PageManager 和 ChunkManager 存储到磁盘
- **快速启动**：页面自包含地址，无需加载大映射表
- **多版本支持**：每次 Tree Dump 生成新版本，支持版本回溯

**重要**：B+树的 Page 中**不存储 Tombstone**。所有写入 Page 的 IndexValue 都是 NORMAL 类型。Tombstone 仅存在于 MemoryTable 中，在 Dump 时会触发 B+Tree 的删除操作。

```
                    ┌─────────────┐
                    │  Root Page  │
                    │   (Index)   │
                    └──────┬──────┘
              ┌────────────┼────────────┐
              │            │            │
              ▼            ▼            ▼
        ┌──────────┐ ┌──────────┐ ┌──────────┐
        │Index Page│ │Index Page│ │Index Page│
        └────┬─────┘ └────┬─────┘ └────┬─────┘
             │            │            │
             ▼            ▼            ▼
        ┌──────────┐ ┌──────────┐ ┌──────────┐
        │Leaf Page │ │Leaf Page │ │Leaf Page │
        │(NORMAL)  │ │(NORMAL)  │ │(NORMAL)  │
        └──────────┘ └──────────┘ └──────────┘
```

## 2. 核心设计：页面自包含地址

索引页条目直接存储子页的物理位置（SegmentLocation），而非逻辑 ID。

**优势**：
- 无需维护 pageId → location 的映射表
- 启动只需加载根页位置，毫秒级启动
- 支持任意规模数据，启动时间恒定

详见 [Page 设计](design-page.md)。

## 3. 类设计

### 3.1 BPlusTree

| 属性 | 类型 | 描述 |
|------|------|------|
| currentVersion | long | 当前版本号 |
| rootLocation | SegmentLocation | 当前版本根页物理位置（始终为 IndexPage） |
| pageManager | PageManager | 页面管理器 |
| leafPageMaxSize | int | LeafPage 最大容量（默认 8KB） |
| indexPageMaxSize | int | IndexPage 最大容量（默认 64KB） |

| 方法 | 返回类型 | 描述 |
|------|----------|------|
| search | IndexPair | 查找条目 |
| rangeQuery | List<IndexPair> | 范围查询 |
| dump | void | 批量更新（Tree Dump） |
| getVersion | long | 获取当前版本号 |
| getRootLocation | SegmentLocation | 获取根页位置 |

**设计说明**：B+Tree 不提供独立的 insert/delete 方法。所有数据变更都通过 `dump()` 批量进行——将 sealed MemoryTable 中的 NORMAL 条目插入 B+Tree，将 TOMBSTONE 条目从 B+Tree 物理删除。这保证了 Append-Only 的写入模式和版本一致性。

### 3.2 PageManager

| 属性 | 类型 | 描述 |
|------|------|------|
| chunkManager | ChunkManager | 存储管理器 |
| readCache | PageCache | 读缓存（LRU，key 为 SegmentLocation 拼接，详见第 9 节） |

| 方法 | 返回类型 | 描述 |
|------|----------|------|
| getPage | Page | 根据 SegmentLocation 获取页面（优先读缓存） |
| savePage | SegmentLocation | 序列化并写入页面，返回新位置 |

### 3.3 WriteBuffer (写缓存)

| 属性 | 类型 | 描述 |
|------|------|------|
| pages | Map<Integer, Page> | 页面缓存 |
| pageMaxKeys | Map<Integer, IndexKey> | 页面最大 key |
| pendingSplits | List<SplitInfo> | 待处理的分裂信息（parentPageId, splitKey, newPageId） |
| maxSize | int | 最大缓存页面数 |
| currentKey | IndexKey | 当前处理到的 key |

| 方法 | 返回类型 | 描述 |
|------|----------|------|
| put | void | 放入页面 |
| get | Page | 获取页面 |
| getFlushablePages | List<Page> | 获取可刷盘页面 |
| remove | void | 移除页面 |
| isFull | boolean | 缓存是否已满 |

## 4. 元数据管理

B+树元数据管理详细设计请参考 [B+树元数据设计](design-bplustree-metadata.md)。

## 5. Tree Dump 流程

### 5.1 概述

Tree Dump 是将 sealed memory tables 批量更新到 B+树的操作：
1. 合并所有 sealed memory tables 为有序序列
2. 按 key 顺序更新 B+树叶页
3. 使用写缓存管理正在更新的页面
4. 批量刷盘，生成新版本

```
┌─────────────────────────────────────────────────────┐
│                  Tree Dump 流程                      │
├─────────────────────────────────────────────────────┤
│                                                      │
│  Sealed Memory Tables                               │
│  ┌──────────┐ ┌──────────┐ ┌──────────┐            │
│  │ MemTable │ │ MemTable │ │ MemTable │            │
│  │   (1)    │ │   (2)    │ │   (3)    │            │
│  └────┬─────┘ └────┬─────┘ └────┬─────┘            │
│       │            │            │                   │
│       └────────────┼────────────┘                   │
│                    │                                 │
│                    ▼                                 │
│            ┌──────────────┐                         │
│            │  Merge Sort  │                         │
│            │  (按 key)    │                         │
│            └──────┬───────┘                         │
│                   │                                  │
│                   ▼                                  │
│            ┌──────────────┐                         │
│            │ 有序 Entry   │                         │
│            │   Stream     │                         │
│            └──────┬───────┘                         │
│                   │                                  │
│                   ▼                                  │
│  ┌─────────────────────────────────────────────┐    │
│  │              Write Buffer                   │    │
│  │  ┌───────┐ ┌───────┐ ┌───────┐             │    │
│  │  │ Page1 │ │ Page2 │ │ Page3 │ ...         │    │
│  │  │max:10 │ │max:20 │ │max:30 │             │    │
│  │  └───────┘ └───────┘ └───────┘             │    │
│  │       ↓         ↓         ↓                 │    │
│  │  currentKey = 15                            │    │
│  │  (Page1 可刷盘，因为 max:10 < 15)           │    │
│  └─────────────────────────────────────────────┘    │
│                   │                                  │
│                   ▼                                  │
│            ┌──────────────┐                         │
│            │ Batch Flush  │                         │
│            │  to Chunks   │                         │
│            └──────┬───────┘                         │
│                   │                                  │
│                   ▼                                  │
│            ┌──────────────┐                         │
│            │ New Version  │                         │
│            │  metadata    │                         │
│            └──────────────┘                         │
│                                                      │
└─────────────────────────────────────────────────────┘
```

### 5.2 写缓存刷盘算法

**核心思想**：按 key 顺序处理，当处理位置超过页面 maxKey 时，该页面可刷盘。

```
┌─────────────────────────────────────────────────────┐
│              写缓存刷盘算法                          │
├─────────────────────────────────────────────────────┤
│                                                      │
│  处理顺序：key = 5, 8, 12, 15, 18, 22, 25...       │
│                                                      │
│  Write Buffer 状态：                                │
│  ┌─────────────────────────────────────────────┐    │
│  │ Page A: keys [1-10],   maxKey = 10          │    │
│  │ Page B: keys [11-20],  maxKey = 20          │    │
│  │ Page C: keys [21-30],  maxKey = 30          │    │
│  └─────────────────────────────────────────────┘    │
│                                                      │
│  currentKey = 15 时：                               │
│  ┌─────────────────────────────────────────────┐    │
│  │ Page A: maxKey(10) < currentKey(15)         │    │
│  │         → 可刷盘 ✓                          │    │
│  │ Page B: maxKey(20) > currentKey(15)         │    │
│  │         → 可能还有更新，保留                │    │
│  │ Page C: maxKey(30) > currentKey(15)         │    │
│  │         → 可能还有更新，保留                │    │
│  └─────────────────────────────────────────────┘    │
│                                                      │
│  currentKey = 25 时：                               │
│  ┌─────────────────────────────────────────────┐    │
│  │ Page A: 已刷盘                              │    │
│  │ Page B: maxKey(20) < currentKey(25)         │    │
│  │         → 可刷盘 ✓                          │    │
│  │ Page C: maxKey(30) > currentKey(25)         │    │
│  │         → 可能还有更新，保留                │    │
│  └─────────────────────────────────────────────┘    │
│                                                      │
└─────────────────────────────────────────────────────┘
```

### 5.3 详细流程

Tree Dump 的详细流程：先确定 Journal 回放点（取所有 sealed table 中最新的 lastReplayPoint，Dump 后这些数据已持久化到 B+Tree，崩溃恢复时从此点开始回放即可恢复后续数据），然后合并排序所有 sealed table 的 Entry。按 key 顺序逐条处理：Tombstone 触发 B+Tree 物理删除，Normal 值触发插入或更新。处理过程中使用 WriteBuffer 缓存正在修改的页面，当 WriteBuffer 满时按 maxKey 判断哪些页面已完成修改可以刷盘。全部处理完后刷盘剩余页面，原子更新 Tree 元数据（版本号、rootLocation、journalReplayPoint），记录 MNS 和 Occupancy 变更。

```
dump(sealedMemoryTables)
    │
    ▼
┌─────────────────────────────────────┐
│  1. 确定新的回放点                  │
│     newReplayPoint = max(           │
│         table.lastReplayPoint       │
│         for table in                │
│             sealedMemoryTables)     │
│     (取所有 sealed table 中最新的   │
│      lastReplayPoint。恢复时从此点  │
│      开始回放，最后一条可能被重复   │
│      回放但因 MemoryTable 写入幂等  │
│      不影响正确性)                  │
└─────────────────────────────────────┘
    │
    ▼
┌─────────────────────────────────────┐
│  2. 合并并排序 Entry                │
│     sortedEntries = mergeSort(      │
│         sealedMemoryTables)         │
└─────────────────────────────────────┘
    │
    ▼
┌─────────────────────────────────────┐
│  3. 初始化写缓存                    │
│     writeBuffer = new WriteBuffer() │
│     newVersion = currentVersion + 1 │
└─────────────────────────────────────┘
    │
    ▼
┌─────────────────────────────────────┐
│  4. 遍历有序 Entry                  │
│     for entry in sortedEntries:     │
│         processEntry(entry)         │
│         tryFlushPages()             │
└─────────────────────────────────────┘
    │
    ▼
┌─────────────────────────────────────┐
│  5. 处理剩余页面                    │
│     flushAllPages()                 │
└─────────────────────────────────────┘
    │
    ▼
┌─────────────────────────────────────┐
│  6. 更新元数据                      │
│     saveMetadata(newVersion,        │
│         newReplayPoint)             │
│     currentVersion = newVersion     │
│     journalReplayPoint = newReplayPoint│
└─────────────────────────────────────┘
    │
    ▼
┌─────────────────────────────────────┐
│  7. 记录 MNS                        │
│     mns = chunkManager.getMNS()     │
│     saveMNS(newVersion, mns)        │
└─────────────────────────────────────┘
    │
    ▼
┌─────────────────────────────────────┐
│  8. 记录 Occupancy 变更             │
│     occupancyDeltas = collectOccupancyDeltas()│
│     decommissionPages = collectDecommissionPages()│
│     saveOccupancyRecord(newVersion, │
│         mns, occupancyDeltas,       │
│         decommissionPages)          │
└─────────────────────────────────────┘
```

### 5.4 Occupancy 跟踪

每次 Tree Dump 时记录 Chunk 空间使用的增量变化（OccupancyDelta）：新写入的 Page 增加对应 Chunk 的 occupancy，被替换（decommission）的旧 Page 减少对应 Chunk 的 occupancy。**所有大小按 Write Item 对齐后计算（含 Padding）**，写入和 decommission 使用相同口径，确保 occupancy 能精确归零。同时记录被 decommission 的 Page 精确位置，用于 Hole Punching GC 的延迟打洞。这些信息存储在 occupancy/{version}.pb 中，GC 时根据累计的 delta 判断哪些 Chunk 可以回收。

```
Occupancy 变更记录：

每次 Dump 记录以下信息：

1. MNS (Min Not Sealed number)
   - 当前最小未封存的 Chunk 编号
   - 用于 GC 判断

2. Occupancy Deltas
   - Decommission Page: 减少对应 Chunk 的 occupancy
   - New Page: 增加对应 Chunk 的 occupancy

3. Decommission Pages
   - 记录所有被 decommission 的 Page 的精确位置
   - 用于 Hole Punching GC 的延迟打洞

Occupancy Delta 结构：

class OccupancyDelta {
    chunkId: UUID
    deltaSize: long  // 正数表示增加，负数表示减少
}

示例：
  [
    {chunkId: "chunk-1", deltaSize: -4096},  // decommission page (alignedSize)
    {chunkId: "chunk-2", deltaSize: +8192},  // new page (alignedSize)
    {chunkId: "chunk-3", deltaSize: -2048}   // decommission page (alignedSize)
  ]
```

#### **Decommission Page 识别**

```
Decommission Page 场景：

1. Page 更新
   - 原位置：chunk1, offset=1024, length=4096
   - 新位置：chunk2, offset=2048, length=4096
   - 记录：chunk1 occupancy -4096
   - 记录：chunk2 occupancy +4096

2. Page 合并（重平衡）
   - 合并 Page A 和 Page B 到 Page C
   - 记录：chunk(A) occupancy - size(A)
   - 记录：chunk(B) occupancy - size(B)
   - 记录：chunk(C) occupancy + size(C)

3. Page 删除
   - 删除 Page D
   - 记录：chunk(D) occupancy - size(D)

识别方式：
  - 在 savePage() 时，记录新 Page 的 location
  - 在 removePage() 时，记录旧 Page 的 location
  - 收集所有变更，形成 deltas 列表
```

#### **Occupancy 记录存储**

```
存储位置：
  - 文件：{storagePath}/occupancy/{version}.pb
  - 每次 Dump 生成一个记录文件
  - 使用 OccupancyRecord 消息序列化
    （详见 design-serialization.md 2.6.4 节）

字段说明：
  - version: Tree 版本号
  - mns: 当前 MNS
  - deltas: Chunk 的 occupancy 变更列表
  - decommissionPages: Decommission 的 Page 列表
    - 用于 Hole Punching GC 的延迟打洞
    - 记录每个被 decommission 的 Page 的精确位置

用途：
  - GC 时计算 Chunk 的 occupancySize
  - 判断 Chunk 是否可以被回收
  - Hole Punching GC 时执行延迟打洞
```

### 5.5 处理单个 Entry

处理单条 Entry 时，先通过 B+Tree 索引定位目标叶页，检查 WriteBuffer 中是否已缓存该页面（缓存命中则直接使用，否则从磁盘加载并缓存）。**关键约束**：如果待插入 key 小于目标叶页的最小 entry，说明 key 实际属于前一个叶页的范围，必须定位到前一个叶页执行插入。这保证了 B+Tree 的有序性约束——每个叶页的 key 范围由其父索引页的 entry 界定，不能越界。

```
processEntry(entry: IndexPair)
    │
    ▼
┌─────────────────────────────────────┐
│  1. 查找目标叶页                    │
│     location = findLeafPageLocation(entry.getKey())│
└─────────────────────────────────────┘
    │
    ▼
┌─────────────────────────────────────┐
│  2. 加载页面到写缓存                │
│     if (writeBuffer.contains(pageId)):│
│         page = writeBuffer.get(pageId)│
│     else:                           │
│         page = loadPage(location)   │
│         writeBuffer.put(page)       │
└─────────────────────────────────────┘
    │
    ▼
┌─────────────────────────────────────┐
│  3. 检查 key 是否小于页面最小 entry │
│     if (page.getEntryCount() > 0 && │
│         entry.getKey() <            │
│             page.getFirstEntry()    │
│                 .getKey()):         │
│         // key 属于前一个叶页       │
│         prevPage = findPrevLeafPage(│
│             page, parentStack)      │
│         page = prevPage             │
└─────────────────────────────────────┘
    │
    ▼
┌─────────────────────────────────────┐
│  4. 更新页面                        │
│     if (isTombstone(entry.getValue())):│
│         page.removeEntry(entry.getKey())│
│     else:                           │
│         page.insert(entry.getKey(), entry.getValue())│
│                                      │
│     更新 pageMaxKeys[pageId]        │
│     writeBuffer.currentKey = entry.getKey()│
└─────────────────────────────────────┘
```

### 5.6 尝试刷盘

刷盘判断的核心逻辑：当 WriteBuffer 已满时，检查哪些页面的 maxKey 小于当前处理位置（currentKey）。这些页面不会再被后续 Entry 修改，可以安全刷盘。刷盘时将页面写入 Chunk（生成新的 SegmentLocation），然后调用 updateParent 更新父索引页中的子页位置。刷盘后从 WriteBuffer 中移除该页面，释放空间。

```
tryFlushPages()
    │
    ▼
┌─────────────────────────────────────┐
│  检查是否需要刷盘                   │
│  if (!writeBuffer.isFull()):        │
│      return                         │
└─────────────────────────────────────┘
    │
    ▼
┌─────────────────────────────────────┐
│  获取可刷盘页面                     │
│  flushable = writeBuffer            │
│      .getFlushablePages()           │
│  // maxKey < currentKey 的页面      │
└─────────────────────────────────────┘
    │
    ▼
┌─────────────────────────────────────┐
│  批量写入                           │
│  for page in flushable:             │
│      location = savePage(page)      │
│      updateParent(page, location)   │
│      writeBuffer.remove(page)       │
└─────────────────────────────────────┘
```

### 5.7 页面分裂处理

当向页面插入 Entry 后 usedSize 超过 maxSize 时触发分裂：按字节大小从中间找到分裂点，将分裂点之后的条目移到新创建的页面，记录分裂的 key 和新页面的 ID 到 pendingSplits 列表中。分裂信息延迟到刷盘时由 updateParent 处理——在父索引页中插入新的 key-location 条目。如果父页面也 overflow，会递归分裂。

```
handleSplit(page)
    │
    ▼
┌─────────────────────────────────────┐
│  1. 分裂页面                        │
│     newPage = page.split()          │
│     splitKey = newPage.getFirstKey()│
└─────────────────────────────────────┘
    │
    ▼
┌─────────────────────────────────────┐
│  2. 更新写缓存                      │
│     writeBuffer.put(newPage)        │
│     更新 pageMaxKeys                │
└─────────────────────────────────────┘
    │
    ▼
┌─────────────────────────────────────┐
│  3. 记录分裂信息                    │
│     (延迟到刷盘时更新父页)          │
│     pendingSplits.add(              │
│         parentPageId, splitKey,     │
│         newPageId)                  │
└─────────────────────────────────────┘
```

### 5.8 updateParent 机制

刷盘页面后需要更新父索引页中对应的 SegmentLocation，使其指向新的物理位置。

```
updateParent(page, newLocation)
    │
    ▼
┌─────────────────────────────────────┐
│  1. 查找父页面                      │
│     parent = findParent(page)       │
│     // 通过 Dump 时维护的父子关系  │
│     // 栈或 parentMap 记录遍历路径 │
└─────────────────────────────────────┘
    │
    ▼
┌─────────────────────────────────────┐
│  2. 更新父页面中的子页位置          │
│     parent.updateChildLocation(     │
│         page.getFirstKey(),         │
│         newLocation)                │
│     parent.isDirty = true           │
└─────────────────────────────────────┘
    │
    ▼
┌─────────────────────────────────────┐
│  3. 处理 pendingSplits              │
│     for split in pendingSplits      │
│         where parent matches:       │
│         parent.insert(              │
│             split.splitKey,         │
│             split.newPageLocation)  │
│         pendingSplits.remove(split) │
│     // 如果 parent 满了，递归分裂  │
└─────────────────────────────────────┘
    │
    ▼
┌─────────────────────────────────────┐
│  4. 父页面也加入 WriteBuffer        │
│     writeBuffer.put(parent)         │
│     // 父页面在后续刷盘时写入      │
│     // 级联更新直到根页            │
└─────────────────────────────────────┘
```

**关键点**：Dump 过程中维护一个遍历路径栈（或 parentMap），记录每个页面的父页面引用，使 updateParent 可以快速定位父页面。

### 5.9 findNextLeafPage

范围查询时通过父索引页定位下一个叶页，无需叶页链表。

```
findNextLeafPage(page, parentStack)
    │
    ▼
┌─────────────────────────────────────┐
│  1. 从 parentStack 获取父索引页     │
│     parent = parentStack.peek()     │
│     if (parent == null):            │
│         return null  // 根页即叶页  │
└─────────────────────────────────────┘
    │
    ▼
┌─────────────────────────────────────┐
│  2. 在父页面中找当前叶页的下一个    │
│     siblings                        │
│     nextLocation = parent           │
│         .findNextChildLocation(     │
│             page.getFirstKey())     │
└─────────────────────────────────────┘
    │
    ├─── nextLocation 存在
    │         │
    │         ▼
    │    return pageManager.getPage(
    │        nextLocation)
    │
    └─── nextLocation 不存在
              │
              ▼
         // 当前父页面的最后一个子页
         // 需要向上回溯到祖父页面
         // 递归查找父页面的下一个兄弟
         return findNextLeafPage(
             parent, parentStack)
```

**说明**：范围查询时维护一个遍历路径栈（parentStack），用于向上回溯查找下一个叶页。不需要叶页之间的链表指针，减少 Dump 时的维护开销。

## 6. 重平衡流程

### 6.1 概述

B+Tree 在删除操作后可能出现页面 underflow（usedSize < maxSize / 4），需要通过重平衡来维护树结构。

**重平衡策略**：采用**与兄弟节点合并**的方式，不使用借用策略。

```
重平衡触发条件：
  - 页面 usedSize < maxSize / 4

重平衡策略：
  策略1：与左兄弟合并（优先）
  策略2：与右兄弟合并（次选）
  
不采用的策略：
  - 从兄弟节点借用（实现复杂，收益低）

根节点约束：
  - 根节点始终是 IndexPage，不能是 LeafPage
  - 根节点 underflow 时，如果只剩一个子节点，
    不降级为叶页，而是保持单子节点的索引页
```

### 6.2 重平衡流程

重平衡在删除操作导致页面 usedSize < maxSize / 4 时触发。先查找左兄弟或右兄弟节点，优先与左兄弟合并，其次与右兄弟合并。合并后更新父节点（删除多余的索引条目），如果父节点也因此 underflow，则递归重平衡。根节点是 IndexPage，只剩一个子节点时保持不变（不降级为叶页）。

```
rebalance(page)
    │
    ▼
┌─────────────────────────────────────┐
│  1. 检查是否需要重平衡              │
│     if (page.usedSize >=             │
│         maxSize / 4):               │
│         return  // 无需重平衡       │
└─────────────────────────────────────┘
    │
    ▼
┌─────────────────────────────────────┐
│  2. 查找兄弟节点                    │
│     parent = getParent(page)        │
│     leftSibling = findLeftSibling() │
│     rightSibling = findRightSibling()│
└─────────────────────────────────────┘
    │
    ▼
┌─────────────────────────────────────┐
│  3. 选择合并目标                    │
│     if (leftSibling != null):       │
│         mergeWithLeft(page, left)   │
│     else if (rightSibling != null): │
│         mergeWithRight(page, right) │
│     else:                           │
│         // 根节点特殊处理           │
│         handleRootUnderflow()       │
└─────────────────────────────────────┘
```

### 6.3 与左兄弟合并

将当前 underflow 页面的所有条目追加到左兄弟页面，然后从父索引页中删除指向当前页面的条目，标记当前页面待删除。如果删除父页面条目后父页面也 underflow，递归触发父页面的重平衡。

```
mergeWithLeft(page, leftSibling)
    │
    ▼
┌─────────────────────────────────────┐
│  1. 合并页面                        │
│     // 将当前页面所有条目追加到左兄弟│
│     for entry in page.entries:      │
│         leftSibling.add(entry)      │
└─────────────────────────────────────┘
    │
    ▼
┌─────────────────────────────────────┐
│  2. 更新父节点                      │
│     parent = getParent(page)        │
│     // 删除父节点中指向当前页的条目 │
│     parent.removeEntry(page.key)    │
│     // 标记父节点需要重平衡         │
│     if (parent.usedSize < maxSize / 4):   │
│         rebalance(parent)           │
└─────────────────────────────────────┘
    │
    ▼
┌─────────────────────────────────────┐
│  3. 标记页面删除                    │
│     // 页面将在下次刷盘时被清理     │
│     markPageForDeletion(page)       │
└─────────────────────────────────────┘
```

### 6.4 与右兄弟合并

将右兄弟页面的所有条目追加到当前页面，然后从父索引页中删除指向右兄弟的条目，标记右兄弟待删除。后续处理与左兄弟合并相同。

```
mergeWithRight(page, rightSibling)
    │
    ▼
┌─────────────────────────────────────┐
│  1. 合并页面                        │
│     // 将右兄弟所有条目追加到当前页 │
│     for entry in rightSibling.entries:│
│         page.add(entry)             │
└─────────────────────────────────────┘
    │
    ▼
┌─────────────────────────────────────┐
│  2. 更新父节点                      │
│     parent = getParent(rightSibling)│
│     // 删除父节点中指向右兄弟的条目 │
│     parent.removeEntry(rightSibling.key)│
│     // 标记父节点需要重平衡         │
│     if (parent.usedSize < maxSize / 4):   │
│         rebalance(parent)           │
└─────────────────────────────────────┘
    │
    ▼
┌─────────────────────────────────────┐
│  3. 标记页面删除                    │
│     markPageForDeletion(rightSibling)│
└─────────────────────────────────────┘
```

### 6.5 根节点 Underflow 处理

根节点始终是 IndexPage（不能是 LeafPage）。根节点 underflow 时，即使只剩一个子节点也保持为 IndexPage，不降级。这简化了树的结构约束——根节点类型恒定，不需要处理根节点在 IndexPage 和 LeafPage 之间切换的复杂逻辑。

```
handleRootUnderflow()
    │
    ▼
┌─────────────────────────────────────┐
│  根节点处理（根节点始终是 IndexPage）│
│                                      │
│  根节点只剩一个子节点：              │
│    - 保持为 IndexPage，不降级       │
│    - 允许 underflow（usedSize <     │
│      maxSize / 4）                 │
│    - 不做树高度调整                 │
└─────────────────────────────────────┘
```

### 6.6 重平衡示例

通过具体示例演示叶页 underflow 后的重平衡过程：删除 key 导致页面 usedSize 低于 maxSize / 4，选择与左兄弟合并，合并后更新父索引页，如果父页面也 underflow 则递归重平衡。

```
示例：叶页 underflow

合并前：
                    [50]
                   /    \
              [30]      [70]
             /   \     /   \
          [20]  [40] [60]  [80]
          
删除 key=60 后，[60] 页面 underflow：

                    [50]
                   /    \
              [30]      [70]
             /   \     /   \
          [20]  [40] [60]  [80]
                     ↑
                  underflow

与左兄弟 [40] 合并：

                    [50]
                   /    \
              [30]      [70]
             /   \         \
          [20]  [40,60]    [80]
                 ↑
              合并后

更新父节点，删除 key=50：

                    [30]
                   /    \
              [20,40,60]  [70,80]
                 ↑
            根节点 underflow，继续重平衡
```

### 6.7 重平衡性能分析

重平衡的时间复杂度为 O(log n)（查找兄弟节点 + 更新父节点），磁盘 I/O 为 3-4 次（读兄弟 + 写合并页 + 更新父页）。合并操作可以在 Dump 时批量延迟执行，减少重平衡频率。

```
时间复杂度：
  - 查找兄弟节点：O(log n)
  - 合并页面：O(n)，n 为页面条目数
  - 更新父节点：O(log n)
  - 总体：O(log n)

空间复杂度：
  - 额外空间：O(1)
  - 不需要临时缓冲区

磁盘 I/O：
  - 读兄弟节点：1-2 次
  - 写合并后的页面：1 次
  - 更新父节点：1 次
  - 总体：3-4 次 I/O

优化建议：
  - 合并操作可以批量执行
  - 延迟重平衡到下次 Dump
  - 减少重平衡频率
```

## 7. 查询流程

### 7.1 单点查询

从根页开始，沿索引页逐层向下查找，通过 findChildLocation 定位子页位置，直到到达叶页。在叶页中通过二分查找定位目标 key。整个过程是只读的，使用快照 Root，不需要加锁。得益于 Page Cache，热点页面的读取开销为微秒级。

```
search(key)
    │
    ▼
┌─────────────────────────────────────┐
│  从根页开始                         │
│  location = rootLocation            │
│  page = getPage(location)           │
└─────────────────────────────────────┘
    │
    ▼
┌─────────────────────────────────────┐
│  循环直到叶页                       │
│  while (page is not leaf):          │
│      location = page.findChildLocation(key)│
│      page = getPage(location)       │
└─────────────────────────────────────┘
    │
    ▼
┌─────────────────────────────────────┐
│  在叶页中查找                       │
│  entry = page.search(key)           │
│  return entry                       │
└─────────────────────────────────────┘
```

### 7.2 范围查询

范围查询先像单点查询一样定位到起始 key 所在的叶页，然后从该叶页开始遍历：在页面内顺序扫描符合范围的 Entry，到达页面末尾时通过 findNextLeafPage（利用父索引页定位下一个叶页）继续遍历，直到 key 超出 endKey 范围。遍历过程维护 parentStack 用于跨页面跳转。

```
rangeQuery(startKey, endKey)
    │
    ▼
┌─────────────────────────────────────┐
│  1. 找到起始叶页                    │
│     location = findLeafPageLocation(startKey)│
│     page = getPage(location)        │
└─────────────────────────────────────┘
    │
    ▼
┌─────────────────────────────────────┐
│  2. 遍历叶页                        │
│     results = []                    │
│     while (page != null):           │
│         for entry in page:          │
│             if entry.key > endKey:  │
│                 return results      │
│             if entry.key >= startKey:│
│                 results.add(entry)  │
│         location = findNextLeafPage(page)│
│         page = getPage(location)    │
└─────────────────────────────────────┘
```

## 8. 配置参数

| 配置项 | 默认值 | 描述 |
|--------|--------|------|
| leafPageMaxSize | 8192 | LeafPage 最大容量（字节，8KB） |
| indexPageMaxSize | 65536 | IndexPage 最大容量（字节，64KB） |
| maxReadCacheSize | 10000 | 读缓存最大页面数 |
| maxWriteBufferSize | 100 | 写缓存最大页面数 |
| maxVersions | 10 | 保留的最大版本数 |

## 9. Page Cache 设计

### 9.1 Cache Key 设计

**关键设计**：Page Cache 的 key 使用 SegmentLocation 的 3 个元素拼接，而非 pageId。

```
Cache Key 格式：

key = "{chunkId}:{offset}:{length}"

示例：
- "550e8400-e29b-41d4-a716-446655440000:1024:4096"
- "550e8400-e29b-41d4-a716-446655440001:2048:4096"

为什么不用 pageId？
  - Append-Only 设计：每次更新页面，生成新的 SegmentLocation
  - 同一个 pageId 可能对应多个版本
  - 使用 SegmentLocation 可以精确区分不同版本
  - 保证读操作获取到正确的版本

示例场景：
  pageId=1 的页面更新流程：
  
  版本1：location = (chunk1, 1024, 4096)
    key = "chunk1:1024:4096"
    value = Page(id=1, entries=[...])
  
  版本2：location = (chunk1, 5120, 4096)
    key = "chunk1:5120:4096"
    value = Page(id=1, entries=[...])
  
  两个版本在 Cache 中共存，读操作根据 location 选择正确版本
```

### 9.2 LRU Cache 实现

PageCache 使用 LinkedHashMap 实现 LRU 淘汰策略。缓存命中时将页面移到链表头部，缓存满时淘汰链表尾部（最久未使用）的页面。读取页面时先查 Cache，命中则直接返回（微秒级），未命中则从磁盘读取后加入 Cache。

```
LRU Cache 结构：

class PageCache {
    // 最大缓存页面数
    maxSize: int = 10000
    
    // LRU 链表（双向链表）
    lruList: LinkedHashMap<CacheKey, Page>
    
    // 当前缓存大小
    currentSize: int = 0
    
    // 缓存命中统计
    hitCount: long = 0
    missCount: long = 0
    
    // 获取页面
    get(location: SegmentLocation): Page? {
        key = buildKey(location)
        page = lruList.get(key)
        
        if (page != null):
            // 缓存命中，移到链表头部
            lruList.moveToHead(key)
            hitCount++
            return page
        else:
            // 缓存未命中
            missCount++
            return null
    }
    
    // 添加页面
    put(location: SegmentLocation, page: Page) {
        key = buildKey(location)
        
        // 检查是否需要淘汰
        if (currentSize >= maxSize):
            evict()
        
        // 添加到缓存
        lruList.put(key, page)
        currentSize++
    }
    
    // 淘汰页面
    evict() {
        // 移除链表尾部（最久未使用）
        evictedKey = lruList.removeTail()
        currentSize--
    }
    
    // 构建 Cache Key
    buildKey(location: SegmentLocation): String {
        return "${location.chunkId}:${location.offset}:${location.length}"
    }
}

缓存命中率：
  hitRate = hitCount / (hitCount + missCount)
```

### 9.3 Cache 更新策略

Cache 在三种场景下更新：读取页面时缓存未命中会加载并缓存；Dump 时写入新页面会同时加入 Cache；页面更新（Append-Only）生成新 location 后缓存新版本，旧版本自然被 LRU 淘汰。

```
Cache 更新场景：

场景1：读取页面
  getPage(location):
      // 1. 尝试从 Cache 读取
      page = cache.get(location)
      
      if (page != null):
          return page  // 缓存命中
      
      // 2. 从磁盘读取
      page = readFromDisk(location)
      
      // 3. 添加到 Cache
      cache.put(location, page)
      
      return page

场景2：写入页面（Dump）
  savePage(page):
      // 1. 序列化页面
      data = serialize(page)
      
      // 2. 根据 Page 类型写入对应 Chunk
      if (page is IndexPage):
          location = chunkManager.writeIndexPage(data)
      else if (page is LeafPage):
          location = chunkManager.writeLeafPage(data)
      
      // 3. 添加到 Cache
      cache.put(location, page)
      
      return location

场景3：页面更新
  updatePage(page):
      // Append-Only：生成新的 location
      newLocation = savePage(page)
      
      // 旧版本保留在 Cache 中
      // 新版本添加到 Cache
      cache.put(newLocation, page)
      
      return newLocation
```

### 9.4 Cache 淘汰策略

LRU 淘汰在缓存满时触发，选择最久未使用的页面移除。淘汰前检查页面是否为脏页（Dump 过程中的未刷盘页面），脏页需要先刷盘再淘汰。配置 cacheEvictThreshold 控制触发淘汰的缓存使用率。

```
淘汰策略：LRU (Least Recently Used)

淘汰时机：
  - 缓存满时（currentSize >= maxSize）
  - 主动清理时（手动触发）

淘汰流程：
  1. 选择最久未使用的页面
     - LRU 链表尾部
     - 最近最少访问
  
  2. 检查页面状态
     - 脏页：先刷盘再淘汰
     - 干净页：直接淘汰
  
  3. 从 Cache 移除
     - 从 LRU 链表删除
     - currentSize--

淘汰示例：
  Cache 状态（maxSize=3）：
    [Page1] ← [Page2] ← [Page3]
     最近使用    ...     最久使用
  
  访问 Page2：
    [Page2] ← [Page1] ← [Page3]
  
  添加 Page4（触发淘汰）：
    [Page4] ← [Page2] ← [Page1]
    Page3 被淘汰
```

### 9.5 Cache 性能优化

优化策略包括：顺序扫描时预读相邻页面减少 Cache Miss；统计页面访问频率保护热点页面；使用 ConcurrentHashMap 分段锁支持并发访问。目标缓存命中率 > 90%，命中时访问延迟 < 1 微秒。

```
优化策略：

1. 预读优化
   - 顺序扫描时预读相邻页面
   - 基于 access pattern 预测
   - 减少 Cache Miss

2. 热点页面保护
   - 统计页面访问频率
   - 热点页面不易被淘汰
   - 提高缓存命中率

3. 分层缓存（可选）
   - L1 Cache: 最近访问的页面
   - L2 Cache: 较久访问的页面
   - 提高缓存效率

4. 并发优化
   - 使用 ConcurrentHashMap
   - 分段锁减少竞争
   - 提高并发性能

性能指标：
  - 缓存命中率：> 90%
  - 平均访问延迟：< 1μs (缓存命中)
  - 缓存容量：10000 页面
```

### 9.6 Cache 监控指标

PageCache 的关键监控指标，用于评估缓存效率和调优缓存大小。

```
监控指标：

1. 缓存命中率
   - hitRate = hitCount / totalAccess
   - 目标：> 90%

2. 缓存大小
   - currentSize / maxSize
   - 目标：70% - 90%

3. 淘汰次数
   - evictCount
   - 频率过高说明缓存不足

4. 平均访问延迟
   - avgLatency
   - 缓存命中：< 1μs
   - 缓存未命中：1-10ms

5. 页面访问分布
   - 热点页面 Top 10
   - 冷页面比例

6. 缓存效率
   - efficiency = hitRate * (1 - evictRate)
   - 综合评价缓存性能
```

### 9.7 配置参数

| 配置项 | 默认值 | 描述 |
|--------|--------|------|
| maxReadCacheSize | 10000 | 读缓存最大页面数 |
| cacheEvictThreshold | 0.9 | 缓存淘汰阈值（90%） |
| preadEnabled | false | 是否启用预读 |
| preadSize | 3 | 预读页面数量 |

## 10. 与其他模块的交互

```
┌──────────────┐   dump(sorted)    ┌──────────────┐
│   KVStore    │ ────────────────► │   BPlusTree  │
│              │                   │              │
│              │   search(key)     │              │
│              │ ────────────────► │              │
└──────────────┘                   └──────────────┘
                                         │
                                         │ getPage(location)
                                         │ savePage(page) → location
                                         ▼
                                  ┌──────────────┐
                                  │  PageManager │
                                  │              │
                                  │ readCache    │
                                  │ writeBuffer  │
                                  └──────────────┘
                                         │
                                         │ read/write
                                         ▼
                                  ┌──────────────┐
                                  │ ChunkManager │
                                  └──────────────┘
```

## 11. 相关文档

- [B+树元数据设计](design-bplustree-metadata.md)：树元数据管理详细设计
- [Page 设计](design-page.md)：叶页和索引页的详细设计
- [存储设计](design-storage.md)：Chunk 和 ChunkManager 设计
- [KVStore 设计](design-kvstore.md)：整体架构和流程
