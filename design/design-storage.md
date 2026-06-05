# 存储层设计

## 1. 概述

存储层负责数据的持久化存储，采用 **Chunk** 作为基本存储单元，通过 **ChunkManager** 统一管理。

**统一存储**：Page 数据和 Journal 数据都存储在 Chunk 中，通过目录分类存储，避免启动时扫描分类。

```
┌─────────────────────────────────────────────────────┐
│                   ChunkManager                       │
│  ┌─────────────┐  ┌─────────────┐  ┌─────────────┐ │
│  │ Chunk UUID-1│  │ Chunk UUID-2│  │ Chunk UUID-3│ │
│  │  (DATA)     │  │  (JOURNAL)  │  │  (DATA)     │ │
│  │   64MB      │  │   64MB      │  │   64MB      │ │
│  └─────────────┘  └─────────────┘  └─────────────┘ │
└─────────────────────────────────────────────────────┘
                         │
                         ▼
              ┌───────────────────┐
              │    Disk Layout    │
              ├───────────────────┤
              │ data/             │
              │   chunk_{uuid}.dat│
              │ journal/          │
              │   chunk_{uuid}.dat│
              └───────────────────┘
```

### 1.1 目录结构

存储层采用两级目录结构：data/ 存放 INDEX 和 LEAF 类型的 Chunk 文件（通过 Chunk Header 中的 ChunkType 区分），journal/ 存放 JOURNAL 类型的 Chunk 文件。树元数据（tree-metadata.pb）和 Journal Region 索引（journal-region.pb）放在存储根目录下。这种结构简化了启动时的 Chunk 分类加载逻辑。

```
{storagePath}/
├── data/                      # DATA 目录（INDEX + LEAF Chunk）
│   ├── chunk_550e8400-e29b-41d4-a716-446655440000.dat
│   ├── chunk_550e8400-e29b-41d4-a716-446655440001.dat
│   └── ...
├── journal/                   # JOURNAL 类型 Chunk 目录
│   ├── chunk_550e8400-e29b-41d4-a716-446655440002.dat
│   ├── chunk_550e8400-e29b-41d4-a716-446655440003.dat
│   └── ...
├── occupancy/                 # Occupancy 记录目录
│   ├── 1.pb                   # 版本 1 的 occupancy 记录
│   ├── 2.pb                   # 版本 2 的 occupancy 记录
│   └── ...
├── tree-metadata.pb           # B+Tree 元数据（Protobuf）
├── journal-region.pb          # Journal Region 索引（Protobuf）
└── chunk-metadata.pb          # Chunk 元数据（Protobuf）
```

**设计优势**：
- INDEX 和 LEAF Chunk 共存于 `data/` 目录，通过 Chunk Header 中的 ChunkType 区分
- 启动时扫描 `data/` 目录加载所有 DATA Chunk，按 ChunkType 分类到 indexChunks/leafChunks
- `journal/` 目录独立存放 JOURNAL Chunk
- 简化目录管理，减少目录数量

## 2. 类设计

### 2.1 ChunkType

| 类型 | 值 | 描述 |
|------|-----|------|
| INDEX | 0 | 存储 Index Page 数据 |
| LEAF | 1 | 存储 Leaf Page 数据 |
| JOURNAL | 2 | 存储 Journal 数据 |

**设计说明**：
- INDEX 和 LEAF 分别存储索引页和叶页数据
- 分离存储便于针对性优化和管理
- JOURNAL 存储操作日志数据

### 2.2 SegmentLocation

段位置信息，定位数据在 Chunk 中的具体位置。

| 属性 | 类型 | 大小 | 描述 |
|------|------|------|------|
| chunkId | UUID | 16 bytes | Chunk 唯一标识（两个 long） |
| offset | int | 4 bytes | 在 Chunk 中的起始偏移（最大 64MB） |
| length | int | 4 bytes | 数据长度 |

**总计**: 24 bytes

**设计说明**：
- chunkId 使用 UUID 确保全局唯一，避免 ID 冲突
- offset 使用 4 bytes，因为 Chunk 最大 64MB (2^26)，4 bytes 足够
- SegmentLocation 可直接嵌入索引页 Entry，实现页面自包含地址

### 2.3 Chunk

存储块，单个文件最大 64MB。

| 属性 | 类型 | 描述 |
|------|------|------|
| chunkId | UUID | Chunk 唯一标识 |
| chunkType | ChunkType | Chunk 类型（INDEX/LEAF/JOURNAL） |
| ownerId | UUID | KVStore 实例 UUID，标识 Chunk 归属 |
| namespaceId | UUID | 命名空间 UUID，为多 Namespace/DB 预留 |
| chunkFile | File | 对应的磁盘文件 |
| validDataSize | int | 有效数据大小 |
| maxSize | int | 最大容量（默认 64MB） |

| 方法 | 返回类型 | 描述 |
|------|----------|------|
| write | SegmentLocation | 写入数据，返回位置信息 |
| read | byte[] | 根据偏移和长度读取数据 |
| close | void | 关闭 Chunk |
| getRemainingSpace | int | 获取剩余空间 |
| isFull | boolean | 是否已满 |

### 2.4 Chunk 状态管理

#### **Chunk 状态定义**

```
enum ChunkStatus {
    OPEN,      // 可写状态
    SEALED,    // 已封存，不可写
    DELETING,  // 正在删除
    DELETED    // 已删除
}
```

#### **Chunk 状态转换**

```
状态转换流程：

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

#### **Chunk 编号**

```
Chunk 编号规则：

chunkNumber: long
  - 单调递增
  - 全局唯一
  - 从 1 开始

MNS (Min Not Sealed number):
  - 最小未封存的 Chunk 编号
  - 用于 GC 判断
  - 计算方式：min(chunkNumber where status == OPEN)

示例：
  Chunk 1: SEALED
  Chunk 2: SEALED
  Chunk 3: OPEN      ← MNS = 3
  Chunk 4: OPEN
  
  当前 MNS = 3
```

#### **Chunk 保活机制**

```
保活时间管理：

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
```

#### **Chunk 元数据**

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
    totalSize: long
    usedSize: long
    occupancySize: long     // 有效数据大小
}

元数据存储：
  - 文件：{storagePath}/chunk-metadata.pb
  - 格式：Protobuf 二进制
  - 更新时机：状态变更、occupancy 变更
```

#### **Chunk 扩展属性**

| 属性 | 类型 | 描述 |
|------|------|------|
| chunkNumber | long | Chunk 编号（单调递增） |
| status | ChunkStatus | Chunk 状态 |
| createdAt | long | 创建时间戳 |
| keepAliveTime | long | 保活截止时间 |
| occupancySize | long | 有效数据大小 |

#### **Chunk 扩展方法**

| 方法 | 返回类型 | 描述 |
|------|----------|------|
| seal | void | 封存 Chunk，变为不可写 |
| extend | void | 延长保活时间 |
| getOccupancyRatio | float | 获取有效数据比例 |

### 2.5 ChunkManager

存储块管理器，统一管理所有 Chunk。

| 属性 | 类型 | 描述 |
|------|------|------|
| storagePath | String | 存储根目录路径 |
| dataPath | String | DATA Chunk 目录路径（`data/`） |
| journalPath | String | JOURNAL Chunk 目录路径（`journal/`） |
| indexChunks | Map<UUID, Chunk> | INDEX Chunk 映射 |
| leafChunks | Map<UUID, Chunk> | LEAF Chunk 映射 |
| journalChunks | Map<UUID, Chunk> | JOURNAL Chunk 映射 |
| currentIndexChunk | Chunk | 当前写入的 INDEX Chunk |
| currentLeafChunk | Chunk | 当前写入的 LEAF Chunk |
| currentJournalChunk | Chunk | 当前写入的 JOURNAL Chunk |
| maxChunkSize | int | 单个 Chunk 最大容量 |

**目录与类型映射说明**：
- `data/` 目录存放 INDEX 和 LEAF 两种类型的 Chunk，通过 Chunk Header 中的 ChunkType 区分
- `journal/` 目录存放 JOURNAL 类型的 Chunk
- 加载时扫描 `data/` 目录，根据 ChunkType 分别放入 indexChunks 或 leafChunks

| 方法 | 返回类型 | 描述 |
|------|----------|------|
| initialize | void | 初始化，按目录加载 Chunk，根据 ChunkType 分类 |
| getChunk | Chunk | 根据 UUID 和类型获取 Chunk |
| allocateIndexChunk | Chunk | 分配新 INDEX Chunk（存入 `data/`） |
| allocateLeafChunk | Chunk | 分配新 LEAF Chunk（存入 `data/`） |
| allocateJournalChunk | Chunk | 分配新 JOURNAL Chunk（存入 `journal/`） |
| writeIndexPage | SegmentLocation | 写入 Index Page 数据 |
| writeLeafPage | SegmentLocation | 写入 Leaf Page 数据 |
| writeJournal | SegmentLocation | 写入 Journal 数据 |
| read | byte[] | 根据位置信息读取数据 |
| getJournalChunks | List<Chunk> | 获取所有 JOURNAL Chunk（按 major 排序） |
| close | void | 关闭所有 Chunk |
| sealChunk | void | 封存指定 Chunk |
| extendChunk | void | 延长 Chunk 保活时间 |
| getMNS | long | 获取当前 MNS（Min Not Sealed number） |
| getSealedChunks | List<Chunk> | 获取所有 SEALED 状态的 Chunk |
| updateOccupancy | void | 更新 Chunk 的 occupancy size |

## 3. 数据结构

### 3.1 Chunk 文件结构

Chunk 文件由 4KB 对齐的 Header 和若干 4KB 对齐的 Write Item 组成。Header 包含 Chunk 身份信息（ID、Type、Owner、Namespace）和有效数据大小，剩余空间填 0 预留。Write Item 从 offset 4096 开始，每个 Write Item 的总大小都是 4KB 的整数倍，使整个文件从头到尾 4KB 对齐。

```
┌─────────────────────────────────────────────────────┐
│                    Chunk File                        │
├─────────────────────────────────────────────────────┤
│  Chunk Header (4096 bytes, 4KB 对齐)                │
│  ┌──────────────────────────────────────────────┐   │
│  │ Chunk ID (UUID)           16 bytes           │   │
│  │ Chunk Type                 4 bytes           │   │
│  │ Owner ID (UUID)           16 bytes           │   │
│  │ Namespace ID (UUID)       16 bytes           │   │
│  │ Valid Data Size            4 bytes           │   │
│  └──────────────────────────────────────────────┘   │
│  Reserved: 4040 bytes (全 0，为未来扩展预留)        │
├─────────────────────────────────────────────────────┤
│  Write Items (Variable)                             │
│  ┌─────────────────────────────────────┐            │
│  │          Write Item 1               │            │
│  └─────────────────────────────────────┘            │
│  ┌─────────────────────────────────────┐            │
│  │          Write Item 2               │            │
│  └─────────────────────────────────────┘            │
│  ...                                                │
└─────────────────────────────────────────────────────┘
```

#### Chunk Header 字段说明

| 字段 | 大小 | 描述 |
|------|------|------|
| Chunk ID | 16 bytes | Chunk 唯一标识（UUID） |
| Chunk Type | 4 bytes | Chunk 类型（INDEX / LEAF / JOURNAL），4 bytes 对齐 |
| Owner ID | 16 bytes | KVStore 实例 UUID，标识 Chunk 归属 |
| Namespace ID | 16 bytes | 命名空间 UUID，为多 Namespace/DB 预留 |
| Valid Data Size | 4 bytes | 已写入数据的总大小（含 Padding，按 Write Item 对齐后计算） |
| Reserved | 4040 bytes | 全 0 填充，为未来扩展预留 |

> 所有字段按 4 bytes 对齐，UUID 为两个 long (8+8)，天然满足。

**设计原则**：
- Chunk Header 只存储 Chunk 自身的身份和归属信息
- 不包含上层业务逻辑数据（如 Journal Region、Entry Count 等）
- 上层模块（Journal、PageManager）的元数据由各自的元数据文件管理

**Chunk Header 与 Write Item 的关系**：
- Chunk Header 固定 4096 bytes (4KB)，与 Write Item 统一 4K 对齐
- Write Item 紧跟 Header 之后，从 offset 4096 开始
- 每个 Write Item 的**总大小**也是 4KB 的整数倍（含 Padding）
- SegmentLocation 中的 offset 是 Chunk 内的绝对偏移（包含 Header）
- 整个 Chunk 文件从头到尾都是 4KB 对齐的

### 3.2 Write Item 结构

每次写入 Chunk 的数据都封装为 Write Item 格式。详见 [数据完整性保护设计](design-data-integrity.md)。

```
┌─────────────────────────────────────────────────────┐
│                    Write Item                        │
├────────────┬────────────┬────────────┬─────────────┤
│   Magic    │   Type     │   Length   │             │
│  2 bytes   │  2 bytes   │  4 bytes   │             │
│  (0xABCD)  │            │            │             │
├────────────┴────────────┴────────────┤             │
│            Body (Variable)            │             │
├───────────────────────────────────────┤             │
│            CRC32 (4 bytes)            │             │
├───────────────────────────────────────┤             │
│            Padding (4K 对齐)          │             │
└───────────────────────────────────────┘

Magic: 0xABCD，快速识别 Write Item
Type: 数据类型（JOURNAL_ENTRY / PAGE_DATA 等）
Length: Body 的字节长度
Body: 实际序列化数据（Page 或 Journal Entry）
CRC32: Header + Body 的 CRC32 校验和
Padding: 填充至 4KB 对齐
```

### 3.3 SegmentLocation 结构

SegmentLocation 是数据在 Chunk 中的定位信息，由 chunkId（UUID 16 bytes）、offset（4 bytes）和 length（4 bytes）组成，固定 24 bytes。它直接嵌入到 B+Tree 索引页的 Entry 中，实现页面自包含地址——不需要额外的映射表，系统启动只需加载根页位置即可。

```
┌─────────────────────────────────────────────────────┐
│                SegmentLocation                       │
├─────────────────────────────────────────────────────┤
│  Chunk ID (UUID)                                    │
│  ┌─────────────────────────────────────────────┐    │
│  │ mostSigBits:   8 bytes (long)               │    │
│  │ leastSigBits:  8 bytes (long)               │    │
│  └─────────────────────────────────────────────┘    │
├─────────────────────────────────────────────────────┤
│  Offset: 4 bytes (最大支持 4GB，实际 Chunk 64MB)   │
├─────────────────────────────────────────────────────┤
│  Length: 4 bytes                                    │
└─────────────────────────────────────────────────────┘

总计: 24 bytes
```

### 3.4 SegmentLocation 在索引页中的使用

索引页的每个 Entry 包含一个 key 和一个 SegmentLocation（指向子页的物理位置）。这种设计使得从根页到叶页的查找路径上，每一级都能直接获取下一级页面的物理位置，无需查表。单个索引 Entry 为 28 bytes（4B key + 24B SegmentLocation）。

```
索引页 Entry 直接包含 SegmentLocation：

┌─────────────────────────────────────────────────────┐
│              Index Entry (新设计)                    │
├─────────────────────────────────────────────────────┤
│  Key: 4 bytes                                       │
├─────────────────────────────────────────────────────┤
│  Child Location (SegmentLocation):                  │
│  ┌─────────────────────────────────────────────┐    │
│  │ Chunk ID (UUID): 16 bytes                   │    │
│  │ Offset: 4 bytes                             │    │
│  │ Length: 4 bytes                             │    │
│  └─────────────────────────────────────────────┘    │
└─────────────────────────────────────────────────────┘

总计: 28 bytes per index entry
```

## 4. 核心算法

### 4.1 初始化流程

ChunkManager 初始化时创建 `data/` 和 `journal/` 两个目录（如果不存在），然后加载 chunk-metadata.pb 恢复所有 Chunk 的元数据（编号、类型、状态等）。**崩溃恢复时所有现有 Chunk 强制设为 SEALED 状态**——崩溃前正在写入的 Chunk 可能有不完整数据，不能继续写入。随后扫描 `data/` 和 `journal/` 目录加载 Chunk 文件并按 ChunkType 分类。最后从 chunk-metadata.pb 恢复最大 chunkNumber，为每种类型分配新的 OPEN Chunk 用于后续写入。

```
initialize()
    │
    ▼
┌─────────────────────────────────────┐
│  1. 创建目录结构                    │
│     dataPath = storagePath + "/data"│
│     journalPath = storagePath +     │
│         "/journal"                  │
│     createDirIfNotExists(dataPath)  │
│     createDirIfNotExists(journalPath)│
└─────────────────────────────────────┘
    │
    ▼
┌─────────────────────────────────────┐
│  2. 加载 DATA Chunks（按 ChunkType  │
│     分类到 indexChunks/leafChunks） │
│     for file in dataPath:           │
│         chunk = loadChunk(file)     │
│         if (chunk.chunkType == INDEX):│
│             indexChunks.put(chunk.id,│
│                 chunk)              │
│         else if (chunk.chunkType == LEAF):│
│             leafChunks.put(chunk.id,│
│                 chunk)              │
└─────────────────────────────────────┘
    │
    ▼
┌─────────────────────────────────────┐
│  3. 加载 JOURNAL Chunks             │
│     for file in journalPath:        │
│         chunk = loadChunk(file)     │
│         journalChunks.put(chunk.id, │
│             chunk)                  │
└─────────────────────────────────────┘
    │
    ▼
┌─────────────────────────────────────┐
│  4. 强制 Seal 所有现有 Chunk         │
│     for chunk in all loaded chunks: │
│         chunk.status = SEALED       │
│     // 崩溃前 OPEN 的 Chunk 可能    │
│     // 有不完整数据，不继续写入     │
└─────────────────────────────────────┘
    │
    ▼
┌─────────────────────────────────────┐
│  5. 恢复 chunkNumber 并分配新 Chunk │
│     maxNumber = max(chunkNumber     │
│         from chunk-metadata.pb)     │
│     nextChunkNumber = maxNumber + 1 │
│     currentIndexChunk =             │
│         allocateNewChunk(INDEX)     │
│     currentLeafChunk =              │
│         allocateNewChunk(LEAF)      │
│     currentJournalChunk =           │
│         allocateNewChunk(JOURNAL)   │
└─────────────────────────────────────┘
```

### 4.2 写入流程

写入时先将数据封装为 Write Item（Magic + Type + Length + Body + CRC32 + Padding，对齐到 4KB），然后检查当前 Chunk 是否有足够空间。空间不足时根据 ChunkType 自动分配新 Chunk。写入完成后返回 SegmentLocation，记录数据在 Chunk 中的绝对偏移和长度，供上层模块（B+Tree 索引页、Journal Region 索引）引用。

```
write(data, chunkType)
    │
    ▼
┌─────────────────────────────────────┐
│  1. 计算所需空间                     │
│     // Write Item: Magic(2B) +      │
│     // Type(2B) + Length(4B) +      │
│     // Body + CRC32(4B) + Padding   │
│     totalSize = alignTo4K(          │
│         12 + data.length + 4)       │
└─────────────────────────────────────┘
    │
    ▼
┌─────────────────────────────────────┐
│  2. 检查当前 Chunk 剩余空间          │
│     currentChunk.remainingSpace     │
└─────────────────────────────────────┘
    │
    ├─── 空间充足 ──────────────────────┐
    │                                   │
    ▼                                   ▼
┌─────────────────┐         ┌─────────────────────────┐
│ 写入当前 Chunk  │         │ 3. 分配新 Chunk         │
└─────────────────┘         │    if (chunkType == INDEX)│
    │                       │      allocateIndexChunk()│
    │                       │    else if (chunkType == LEAF)│
    │                       │      allocateLeafChunk() │
    │                       │    else                 │
    │                       │      allocateJournalChunk()│
    │                       └─────────────────────────┘
    │                                   │
    └───────────────────────────────────┘
                    │
                    ▼
         ┌─────────────────────────┐
         │ 4. 构造 Write Item      │
         │    - Magic (0xABCD)     │
         │    - Type               │
         │    - Length             │
         │    - Body (Data)        │
         │    - CRC32 Checksum     │
         │    - Padding (4K 对齐)  │
         └─────────────────────────┘
                    │
                    ▼
         ┌─────────────────────────┐
         │ 5. 返回 SegmentLocation │
         │    (包含 UUID, offset,  │
         │     length)             │
         └─────────────────────────┘
```

### 4.3 读取流程

读取通过 SegmentLocation 定位数据：先根据 chunkId 找到对应的 Chunk 文件，再从 offset 位置读取指定长度的数据。读取后解析 Write Item Header，验证 Magic 和 CRC32 校验和。校验失败说明数据损坏，抛出 CorruptionException 由上层决定是否 stopServing。

```
read(location)
    │
    ▼
┌─────────────────────────────────────┐
│  1. 根据 UUID 获取 Chunk            │
│     chunk = getChunk(location.chunkId)│
└─────────────────────────────────────┘
    │
    ▼
┌─────────────────────────────────────┐
│  2. 读取 Write Item 数据            │
│     itemData = chunk.read(          │
│         location.offset,            │
│         location.length)            │
└─────────────────────────────────────┘
    │
    ▼
┌─────────────────────────────────────┐
│  3. 解析 Write Item Header          │
│     - 验证 Magic (0xABCD)           │
│     - 获取 Type                     │
│     - 获取 Length                   │
└─────────────────────────────────────┘
    │
    ▼
┌─────────────────────────────────────┐
│  4. 验证 CRC32                      │
│     if (CRC32(header + body) !=     │
│         storedCrc32)                │
│         throw CorruptionException   │
└─────────────────────────────────────┘
    │
    ▼
┌─────────────────────────────────────┐
│  5. 返回 Body 部分                  │
└─────────────────────────────────────┘
```

### 4.4 Chunk 分配流程

分配新 Chunk 时生成 UUID 作为文件名，在对应目录（INDEX/LEAF → `data/`，JOURNAL → `journal/`）创建文件，写入 4KB Chunk Header（含 ChunkID、ChunkType、OwnerID、NamespaceID 等身份信息，Reserved 区域填 0），然后注册到 ChunkManager 的映射中。INDEX 和 LEAF 的分配逻辑相同，仅 ChunkType 字段不同。

```
allocateIndexChunk() / allocateLeafChunk()
    │
    ▼
┌─────────────────────────────────────┐
│  1. 生成新 UUID                     │
│     chunkId = UUID.randomUUID()     │
└─────────────────────────────────────┘
    │
    ▼
┌─────────────────────────────────────┐
│  2. 创建 Chunk 文件                 │
│     file = dataPath +               │
│         "/chunk_{uuid}.dat"         │
│     // INDEX 和 LEAF 都存入 data/ 目录│
└─────────────────────────────────────┘
    │
    ▼
┌─────────────────────────────────────┐
│  3. 写入 Chunk Header (4096 bytes) │
│     - Chunk ID (UUID, 16 bytes)     │
│     - ChunkType = INDEX 或 LEAF     │
│       (4 bytes)                     │
│     - Owner ID (KVStore UUID)       │
│     - Namespace ID                  │
│     - Valid Data Size = 0           │
│     - Reserved (全 0 填充至 4KB)    │
└─────────────────────────────────────┘
    │
    ▼
┌─────────────────────────────────────┐
│  4. 创建 Chunk 对象并加入管理       │
│     if (chunkType == INDEX):        │
│         indexChunks.put(chunkId,     │
│             chunk)                  │
│         currentIndexChunk = chunk   │
│     else:                           │
│         leafChunks.put(chunkId,     │
│             chunk)                  │
│         currentLeafChunk = chunk    │
└─────────────────────────────────────┘
    │
    ▼
┌─────────────────────────────────────┐
│  5. 返回新 Chunk                    │
└─────────────────────────────────────┘
```

```
allocateJournalChunk()
    │
    ▼
┌─────────────────────────────────────┐
│  1. 生成新 UUID                     │
│     chunkId = UUID.randomUUID()     │
└─────────────────────────────────────┘
    │
    ▼
┌─────────────────────────────────────┐
│  2. 创建 Chunk 文件                 │
│     file = journalPath +            │
│         "/chunk_{uuid}.dat"         │
└─────────────────────────────────────┘
    │
    ▼
┌─────────────────────────────────────┐
│  3. 写入 Chunk Header (4096 bytes) │
│     - Chunk ID (UUID, 16 bytes)     │
│     - ChunkType = JOURNAL (4 bytes) │
│     - Owner ID (KVStore UUID)       │
│     - Namespace ID                  │
│     - Valid Data Size = 0           │
│     - Reserved (全 0 填充至 4KB)    │
└─────────────────────────────────────┘
    │
    ▼
┌─────────────────────────────────────┐
│  4. 创建 Chunk 对象并加入管理       │
│     journalChunks.put(chunkId,      │
│         chunk)                      │
│     currentJournalChunk = chunk     │
└─────────────────────────────────────┘
    │
    ▼
┌─────────────────────────────────────┐
│  5. 返回新 Chunk                    │
└─────────────────────────────────────┘
```

## 5. 配置参数

| 配置项 | 默认值 | 描述 |
|--------|--------|------|
| maxChunkSize | 64MB | 单个 Chunk 最大容量 |
| storagePath | ./data | 存储根目录 |
| checksumAlgorithm | CRC32 | 数据校验算法 |

> `dataPath`（`{storagePath}/data/`）和 `journalPath`（`{storagePath}/journal/`）由 `storagePath` 自动派生，不单独配置。

## 6. 错误处理

| 错误类型 | 处理方式 |
|----------|----------|
| Chunk 文件不存在 | 自动创建新 Chunk |
| 磁盘空间不足 | 抛出 StorageException |
| Checksum 校验失败 | 抛出 CorruptionException |
| Chunk 已满 | 自动分配新 Chunk |
| UUID 冲突 | 理论上不可能，UUID 碰撞概率极低 |

## 7. 与其他模块的交互

```
┌──────────────┐     write(data)      ┌──────────────┐
│   Journal    │ ──────────────────►  │              │
└──────────────┘                      │              │
                                      │ ChunkManager │
┌──────────────┐     savePage(page)   │              │
│ PageManager  │ ──────────────────►  │              │
└──────────────┘                      └──────────────┘
                                            │
                                            ▼
                                      ┌──────────────┐
                                      │    Chunks    │
                                      │ (UUID named) │
                                      └──────────────┘
```

## 8. SegmentLocation 的优势

| 特性 | 说明 |
|------|------|
| 全局唯一 | UUID 确保跨机器、跨时间的唯一性 |
| 自包含 | 可直接嵌入索引页，无需额外映射表 |
| 固定大小 | 24 bytes，便于序列化和内存管理 |
| 快速启动 | 系统启动只需加载根页位置，无需加载全部映射 |
