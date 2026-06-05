# Page 设计

## 1. 概述

Page 是 B+树的基本存储单元，分为三种类型：
- **叶页（LEAF）**：存储实际的键值对数据（使用 IndexKey 和 IndexValue）
- **分支页（BRANCH）**：中间索引页，存储键和子页的物理位置（使用 IndexKey 和 SegmentLocation）
- **根页（ROOT）**：顶层索引页，存储键和子页的物理位置（使用 IndexKey 和 SegmentLocation）

**重要**：Page 中**不存储 Tombstone**。所有写入 Page 的 IndexValue 都是 NORMAL 类型。Tombstone 仅存在于 MemoryTable 中，在 Dump 时会触发 B+Tree 的删除操作。

**树结构保证**：B+树至少包含一个根页和一个叶页，或者为空树。不存在只有叶页没有根页的情况。

```
┌─────────────────────────────────────────────────────┐
│                   Page 体系                          │
├─────────────────────────────────────────────────────┤
│                                                      │
│  ┌─────────────────────────────────────────────┐    │
│  │              Page (基类)                     │    │
│  │  - pageId: int                              │    │
│  │  - pageType: PageType                       │    │
│  │  - entries: List<IndexPair>                 │    │
│  │  - maxSize: int                               │    │
│  │  - usedSize: int                              │    │
│  │  - isDirty: boolean                         │    │
│  └─────────────────────────────────────────────┘    │
│                       │                              │
│           ┌───────────┼───────────┐                 │
│           │           │           │                 │
│           ▼           ▼           ▼                 │
│  ┌─────────────────┐ ┌─────────────────┐ ┌─────────────────┐
│  │   LeafPage      │ │   BranchPage    │ │   RootPage      │
│  │                 │ │                 │ │                 │
│  │  存储 <IndexKey, │ │  存储 <IndexKey,│ │  存储 <IndexKey,│
│  │   IndexValue>   │ │  SegmentLocation>│ │  SegmentLocation>│
│  │  (仅 NORMAL)    │ │  (中间索引层)    │ │  (顶层索引页)    │
│  └─────────────────┘ └─────────────────┘ └─────────────────┘
│                                                      │
└─────────────────────────────────────────────────────┘
```

## 2. 类设计

### 2.1 Page (基类)

页面的 overflow/underflow 判断基于**字节大小**而非条目数量，因为 Key 和 Value 是变长的，固定条目数无法准确反映页面容量。IndexPage 和 LeafPage 有不同的 maxSize：IndexPage 较大（64KB），因为索引条目固定大小且需要更高的扇出度；LeafPage 较小（8KB），因为叶页数据量大、数量多，更小的页面有利于缓存和并发。

**Overflow / Underflow 规则**：
- **Overflow**：usedSize > maxSize → 触发分裂
- **Underflow**：usedSize < maxSize / 4 → 触发重平衡（阈值设为 1/4 而非 1/2，减少不必要的合并操作，降低写放大）

| 属性 | 类型 | 描述 |
|------|------|------|
| pageId | int | 页面 ID（用于缓存键） |
| pageType | PageType | 页面类型（LEAF/INDEX） |
| maxSize | int | 页面最大容量（IndexPage=64KB, LeafPage=8KB） |
| usedSize | int | 当前已使用字节数 |
| entries | List<KeyValuePairProto> | 条目列表（叶页用 value，索引页用 location） |
| offsets | int[] | 条目偏移量数组，快速定位每个entry的位置 |
| isDirty | boolean | 是否为脏页 |

| 方法 | 返回类型 | 描述 |
|------|----------|------|
| insert | boolean | 插入条目 |
| search | IndexPair | 查找条目 |
| removeEntry | boolean | 删除条目 |
| split | Page | 分裂页面（usedSize > maxSize 时触发） |
| getFirstEntry | IndexPair | 获取首个条目 |
| getLastEntry | IndexPair | 获取末尾条目 |
| getEntryCount | int | 获取条目数量 |
| getUsedSize | int | 获取当前已使用字节数 |
| isOverflow | boolean | usedSize > maxSize |
| isUnderflow | boolean | usedSize < maxSize / 4 |
| updateOffsets | void | 更新条目偏移量数组 |
| getEntryOffset | int | 获取指定索引条目的偏移量 |

### 2.2 LeafPage (叶页)

继承 Page，存储实际数据。

| 属性 | 类型 | 描述 |
|------|------|------|
| (继承) | - | pageId, pageType, maxSize=8KB, usedSize, entries, isDirty |

| 方法 | 返回类型 | 描述 |
|------|----------|------|
| insert | boolean | 插入键值对 |
| search | IndexPair | 查找键对应的条目 |
| rangeQuery | List<IndexPair> | 范围查询 |
| split | LeafPage | 分裂叶页 |

### 2.3 IndexPage (索引页)

继承 Page，存储子页位置。

| 属性 | 类型 | 描述 |
|------|------|------|
| (继承) | - | pageId, pageType, maxSize=64KB, usedSize, entries, offsets, isDirty |

| 方法 | 返回类型 | 描述 |
|------|----------|------|
| insert | boolean | 插入索引条目 |
| findChildLocation | SegmentLocation | 根据键查找子页位置 |
| split | IndexPage | 分裂索引页 |

### 2.4 PageType 枚举

| 值 | 描述 |
|----|------|
| LEAF | 叶页：存储实际数据 |
| INDEX | 索引页：存储键和子页位置 |

### 2.5 IndexPair (统一条目表示)

| 类型 | 描述 |
|------|------|
| LeafPair | 叶页条目：<IndexKey, IndexValue> |
| IndexPairImpl | 索引页条目：<IndexKey, SegmentLocation> |

### 2.6 EntryType 枚举

| 值 | 描述 |
|----|------|
| KEY_VALUE | 正常键值对 |
| TOMBSTONE | 删除标记（通过 IndexValue 的 metadata 表示） |

### 2.7 SegmentLocation (物理位置)

| 属性 | 类型 | 大小 | 描述 |
|------|------|------|------|
| chunkId | UUID | 16 bytes | Chunk ID（两个 long） |
| offset | int | 4 bytes | Chunk 内偏移（最大 64MB） |
| length | int | 4 bytes | 数据长度 |

**总计**: 24 bytes

## 3. 数据结构

### 3.1 叶页结构

叶页的磁盘布局：Header（pageType 4B + pageId 4B）+ Entry Count（4B）+ Offset Array（4B × 条目数，用于快速定位每个 Entry）+ Entries（每个 Entry 包含 IndexKey 和 IndexValue）。所有 Entry 按 key 有序排列，支持二分查找。Page 中只存储 NORMAL 类型的 IndexValue，不存储 Tombstone。

```
┌─────────────────────────────────────────────────────┐
│                    Leaf Page                         │
├─────────────────────────────────────────────────────┤
│  Header                                             │
│  ┌──────────┬──────────┐                           │
│  │ pageType │  pageId  │                           │
│  │  4 bytes │  4 bytes │                           │
│  └──────────┴──────────┘                           │
├─────────────────────────────────────────────────────┤
│  Entry Count: 4 bytes                               │
├─────────────────────────────────────────────────────┤
│  Offset Array: 4 bytes × Entry Count               │
│  (每个entry在页面中的偏移量)                        │
├─────────────────────────────────────────────────────┤
│  Entries                                            │
│  ┌─────────────────────────────────────┐            │
│  │ IndexKey (type: ORDERED_BYTES)      │            │
│  │ Value Type: 4 bytes (NORMAL)         │            │
│  │ Value Length: 4 bytes               │            │
│  │ Value Data: Protobuf encoded        │            │
│  ├─────────────────────────────────────┤            │
│  │ IndexKey (type: ORDERED_BYTES)      │            │
│  │ Value Type: 4 bytes (NORMAL)         │            │
│  │ Value Length: 4 bytes               │            │
│  │ Value Data: Protobuf encoded        │            │
│  ├─────────────────────────────────────┤            │
│  │ IndexKey (type: ORDERED_BYTES)      │            │
│  │ Value Type: 4 bytes (NORMAL)         │            │
│  │ Value Length: 4 bytes               │            │
│  │ Value Data: Protobuf encoded        │            │
│  └─────────────────────────────────────┘            │
│  注意：Page 中只存储 NORMAL 类型的 IndexValue       │
│       Tombstone 不会写入 Page                       │
└─────────────────────────────────────────────────────┘
```

### 3.2 索引页结构

索引页的磁盘布局与叶页类似，但 Entry 内容不同：每个 Entry 包含一个 IndexKey 和一个 SegmentLocation（子页的物理位置，24 bytes）。通过 key 可以定位到对应的子页范围，实现 B+Tree 的逐层导航。

```
┌─────────────────────────────────────────────────────┐
│                   Index Page                         │
├─────────────────────────────────────────────────────┤
│  Header                                             │
│  ┌──────────┬──────────┐                           │
│  │ pageType │  pageId  │                           │
│  │  4 bytes │  4 bytes │                           │
│  └──────────┴──────────┘                           │
├─────────────────────────────────────────────────────┤
│  Entry Count: 4 bytes                               │
├─────────────────────────────────────────────────────┤
│  Offset Array: 4 bytes × Entry Count               │
│  (每个entry在页面中的偏移量)                        │
├─────────────────────────────────────────────────────┤
│  Index Entries                                      │
│  ┌─────────────────────────────────────────────┐    │
│  │ IndexKey (type: ORDERED_BYTES)              │    │
│  │ childLocation:                               │    │
│  │   ├── chunkId (UUID): 16 bytes              │    │
│  │   ├── offset: 4 bytes                       │    │
│  │   └── length: 4 bytes                       │    │
│  ├─────────────────────────────────────────────┤    │
│  │ IndexKey → location(chunkId, offset, len)   │    │
│  ├─────────────────────────────────────────────┤    │
│  │ IndexKey → location(chunkId, offset, len)   │    │
│  ├─────────────────────────────────────────────┤    │
│  │ IndexKey → location(chunkId, offset, len)   │    │
│  └─────────────────────────────────────────────┘    │
└─────────────────────────────────────────────────────┘
```

### 3.3 索引页查找逻辑

在索引页中查找目标 key 对应的子页位置：遍历所有条目，找到第一个大于目标 key 的条目，返回其 SegmentLocation。如果所有条目的 key 都小于目标 key，返回最后一个条目的 location。这实现了 B+Tree 的标准路由逻辑。

```
findChildLocation(key: IndexKey)
    │
    ▼
┌─────────────────────────────────────┐
│  遍历所有索引条目                     │
│  for entry in entries:              │
│      if key.compareTo(entry.getKey()) < 0:│
│          return entry.getLocation() │
└─────────────────────────────────────┘
    │
    ▼
┌─────────────────────────────────────┐
│  如果所有 key 都小于目标 key        │
│  返回最后一个条目的 location         │
└─────────────────────────────────────┘
```

**示例**：
- 查找 key=25：返回第一个大于 25 的 key 对应的 childLocation
- 查找 key=40：返回第一个大于 40 的 key 对应的 childLocation
- 查找 key=60：返回最后一个条目的 childLocation

## 4. Protobuf Schema

Page 直接复用全局统一的 `KeyValuePairProto`，不再需要单独的 LeafEntryProto 和 IndexEntryProto。叶页条目的 oneof 选 `value`（ValueProto），索引页条目的 oneof 选 `location`（SegmentLocationProto）。详细定义见 [序列化协议设计](design-serialization.md) 2.1~2.3 节。

```protobuf
// KeyValuePairProto（全局统一，Journal 和 Page 共用）
// message KeyValuePairProto {
//     KeyProto key = 1;
//     oneof entry_value {
//         ValueProto value = 2;              // 叶页 / Journal
//         SegmentLocationProto location = 3; // 索引页
//     }
// }

// 完整的 Page 消息
message PageProto {
  PageType page_type = 1;
  int32 page_id = 2;
  int32 max_size = 3;               // 页面最大容量（字节）
  int32 used_size = 4;              // 当前已使用字节数
  repeated KeyValuePairProto entries = 5; // 条目列表
  // 叶页：entry.value 为 ValueProto
  // 索引页：entry.location 为 SegmentLocationProto
}

enum PageType {
  LEAF = 0;
  INDEX = 1;
}
```

## 5. 核心操作

### 5.1 插入流程

叶页插入：通过二分查找定位插入位置，如果 key 已存在则更新值，否则在正确位置插入新条目保持有序。插入后标记页面为脏页（isDirty），后续刷盘时写入新的物理位置。

```
insert(key: IndexKey, value: IndexValue)
    │
    ▼
┌─────────────────────────────────────┐
│  1. 二分查找插入位置                │
│     pos = binarySearch(key)         │
└─────────────────────────────────────┘
    │
    ▼
┌─────────────────────────────────────┐
│  2. 检查是否已存在                  │
│     if (pos >= 0 && entries[pos].getKey().equals(key)):│
│         更新现有条目                │
│         return true                 │
└─────────────────────────────────────┘
    │
    ▼
┌─────────────────────────────────────┐
│  3. 插入新条目                      │
│     entries.add(pos, new LeafPair(key, value))│
│     isDirty = true                  │
│     return true                     │
└─────────────────────────────────────┘
```

### 5.2 查找流程

叶页查找：通过二分查找在有序条目列表中定位目标 key，找到则返回对应的 IndexPair，未找到返回 null。时间复杂度 O(log n)，n 为页面内条目数。

```
search(key: IndexKey)
    │
    ▼
┌─────────────────────────────────────┐
│  二分查找                           │
│  pos = binarySearch(key)            │
└─────────────────────────────────────┘
    │
    ▼
┌─────────────────────────────────────┐
│  检查是否找到                       │
│  if (pos >= 0 && entries[pos].getKey().equals(key)):│
│      return entries[pos]            │
│  else:                              │
│      return null                    │
└─────────────────────────────────────┘
```

### 5.3 分裂流程

页面 overflow（usedSize > maxSize）时触发分裂：从中间位置找到分裂点（使累计字节数接近 usedSize/2），将分裂点之后的条目移到新页面，原页面保留前半部分。分裂基于字节大小而非条目数，确保两个页面的空间利用率均衡。新页面返回给调用方，由 B+Tree 的 handleSplit 更新父索引页。

```
split()
    │
    ▼
┌─────────────────────────────────────┐
│  1. 创建新页面                      │
│     newPage = new Page(maxSize)     │
└─────────────────────────────────────┘
    │
    ▼
┌─────────────────────────────────────┐
│  2. 找到分裂点（按字节大小均分）   │
│     targetSize = usedSize / 2       │
│     splitIndex = 找到累计大小 >=    │
│         targetSize 的第一个 entry   │
│     newPage.entries = entries       │
│         .subList(splitIndex,        │
│             entries.size())         │
│     entries = entries               │
│         .subList(0, splitIndex)     │
│     更新两个页面的 usedSize         │
└─────────────────────────────────────┘
    │
    ▼
┌─────────────────────────────────────┐
│  3. 返回新页面                      │
│     return newPage                  │
└─────────────────────────────────────┘
```

### 5.4 删除流程

叶页删除：通过二分查找定位目标 key，找到则移除对应条目，更新 usedSize 并标记脏页。删除后如果页面 underflow（usedSize < maxSize / 4），触发 B+Tree 层面的重平衡（与兄弟节点合并）。

```
removeEntry(key: IndexKey)
    │
    ▼
┌─────────────────────────────────────┐
│  1. 二分查找                        │
│     pos = binarySearch(key)         │
└─────────────────────────────────────┘
    │
    ▼
┌─────────────────────────────────────┐
│  2. 删除条目                        │
│     if (pos >= 0):                  │
│         entries.remove(pos)         │
│         isDirty = true              │
│         return true                 │
│     return false                    │
└─────────────────────────────────────┘
```

## 6. 页面大小计算

### 6.1 叶页容量（maxSize = 8KB）

```
Header: 12 bytes (pageType 4B + pageId 4B + entryCount 4B)
Entry: Key(4B type + 4B len + data) + Value(4B type + 4B len + data)

平均 key 长度假设 16 bytes，平均 value 长度假设 50 bytes：
单个 Entry ≈ (4+4+16) + (4+4+50) = 82 bytes

可用空间 = 8192 - 12 = 8180 bytes
平均条目数 ≈ 8180 / 82 ≈ 99 条
overflow 阈值 = usedSize > 8192 bytes
underflow 阈值 = usedSize < 2048 bytes (maxSize / 4)
```

### 6.2 索引页容量（maxSize = 64KB）

索引页容量计算：Header 12B + 每个 Entry 28B（key 4B + SegmentLocation 24B）+ Last Child Location 24B。

```
Header: 12 bytes (pageType 4B + pageId 4B + entryCount 4B)
Entry: 28 bytes (key + SegmentLocation)
Last Child Location: 24 bytes

可用空间 = 65536 - 12 - 24 = 65500 bytes
平均条目数 ≈ 65500 / 28 ≈ 2339 条
overflow 阈值 = usedSize > 65536 bytes
underflow 阈值 = usedSize < 16384 bytes (maxSize / 4)
```

## 7. 配置参数

| 配置项 | 默认值 | 描述 |
|--------|--------|------|
| leafPageMaxSize | 8KB | LeafPage 最大容量（字节） |
| indexPageMaxSize | 64KB | IndexPage 最大容量（字节） |

## 8. 设计要点

1. **自包含地址**：索引页条目直接存储子页物理位置，无需映射表
2. **Tombstone 处理**：Page 不存储 Tombstone；Dump 时 Tombstone 触发物理删除操作
3. **统一接口**：叶页和索引页共享基类，简化 B+树操作
4. **高效查找**：条目有序，支持二分查找
5. **无叶页链表**：叶页不存储 next 指针，范围查询通过父索引页定位下一个叶页

## 9. 相关文档

- [B+树设计](design-bplustree.md)：B+树整体设计
- [B+树元数据设计](design-bplustree-metadata.md)：版本管理
- [Key-Value 存储格式](design-key-value.md)：IndexKey 和 IndexValue 的详细设计
- [存储设计](design-storage.md)：Chunk 和 SegmentLocation 设计
