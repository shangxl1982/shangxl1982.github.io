# 数据完整性保护设计

## 1. 概述

本文档定义 KVStore 的数据完整性保护机制，包括：
- **Write Item 结构**：统一的数据写入格式
- **CRC32 校验**：数据完整性验证
- **4K 对齐**：性能优化与部分写入检测
- **部分写入处理**：崩溃恢复时的数据处理

```
┌─────────────────────────────────────────────────────────────┐
│                    数据完整性架构                            │
├─────────────────────────────────────────────────────────────┤
│                                                              │
│  写入流程：                                                  │
│  ┌──────────┐    ┌──────────┐    ┌──────────┐             │
│  │ 原始数据  │ ──►│Write Item│ ──►│  Chunk   │             │
│  │          │    │ 构造     │    │  写入    │             │
│  └──────────┘    └──────────┘    └──────────┘             │
│                       │                                      │
│                       ▼                                      │
│              ┌──────────────────┐                           │
│              │ Header + Body +  │                           │
│              │ CRC32 + Padding  │                           │
│              └──────────────────┘                           │
│                                                              │
│  读取流程：                                                  │
│  ┌──────────┐    ┌──────────┐    ┌──────────┐             │
│  │  Chunk   │ ──►│Write Item│ ──►│ CRC32    │             │
│  │  读取    │    │ 解析     │    │  校验    │             │
│  └──────────┘    └──────────┘    └──────────┘             │
│                                        │                     │
│                                        ▼                     │
│                              ┌──────────────────┐           │
│                              │ 成功 / 失败      │           │
│                              │ (stopServing)    │           │
│                              └──────────────────┘           │
│                                                              │
└─────────────────────────────────────────────────────────────┘
```

## 2. Write Item 结构

### 2.1 结构定义

Write Item 是 Chunk 上所有写入数据的统一封装格式，采用 **TLV (Type-Length-Value)** 结构。

```
┌─────────────────────────────────────────────────────────────┐
│                    Write Item 结构                           │
├─────────────────────────────────────────────────────────────┤
│                                                              │
│  ┌─────────────────────────────────────────────────────┐    │
│  │                    Header (12 bytes)                 │    │
│  ├────────────┬────────────┬───────────────────────────┤    │
│  │ Magic      │ Type       │ Length                    │    │
│  │ (2 bytes)  │ (2 bytes)  │ (4 bytes)                 │    │
│  │ 0xABCD     │            │ Body 总长度               │    │
│  └────────────┴────────────┴───────────────────────────┘    │
│                                                              │
│  ┌─────────────────────────────────────────────────────┐    │
│  │                    Body (Variable)                   │    │
│  ├─────────────────────────────────────────────────────┤    │
│  │ 原始数据：                                          │    │
│  │ - Journal Entry                                    │    │
│  │ - Page Data                                        │    │
│  │ - 其他数据                                          │    │
│  └─────────────────────────────────────────────────────┘    │
│                                                              │
│  ┌─────────────────────────────────────────────────────┐    │
│  │                    Tailer (4 bytes)                  │    │
│  ├─────────────────────────────────────────────────────┤    │
│  │ CRC32                                               │    │
│  │ (Header + Body 的校验和)                            │    │
│  └─────────────────────────────────────────────────────┘    │
│                                                              │
│  ┌─────────────────────────────────────────────────────┐    │
│  │                    Padding (Variable)                │    │
│  ├─────────────────────────────────────────────────────┤    │
│  │ 填充数据（全 0）                                    │    │
│  │ 使 Write Item 总长度对齐到 4KB                      │    │
│  └─────────────────────────────────────────────────────┘    │
│                                                              │
└─────────────────────────────────────────────────────────────┘
```

### 2.2 字段说明

| 字段 | 大小 | 描述 |
|------|------|------|
| Magic | 2 bytes | 魔数，固定为 0xABCD，用于快速识别 Write Item |
| Type | 2 bytes | 数据类型（见下表） |
| Length | 4 bytes | Body 的长度（不包含 Header、Tailer、Padding） |
| Body | Variable | 原始数据 |
| CRC32 | 4 bytes | Header + Body 的 CRC32 校验和 |
| Padding | Variable | 填充数据，使总长度对齐到 4KB |

### 2.3 数据类型定义

| Type 值 | 名称 | 描述 |
|---------|------|------|
| 0x0001 | JOURNAL_ENTRY | Journal 操作日志 |
| 0x0002 | PAGE_DATA | Page 数据（叶页或索引页） |
| 0x0003 | METADATA | 元数据（保留） |
| 0x0004 | INDEX_DATA | 索引数据（保留） |

### 2.4 总长度计算

Write Item 总长度计算：Header(8B: Magic 2B + Type 2B + Length 4B) + Body(变长) + Tailer(CRC32 4B) + Padding(变长) = 向上对齐到 4KB。Padding 使用全 0 字节填充。这样每个 Write Item 占用整数个 4KB 块，与文件系统块大小对齐，便于部分写入检测和性能优化。

```
Write Item 总长度计算：

headerSize = 12 bytes
bodySize = data.length
tailerSize = 4 bytes
paddingSize = ?

totalSizeWithoutPadding = headerSize + bodySize + tailerSize
                         = 12 + bodySize + 4
                         = 16 + bodySize

// 向上对齐到 4KB
pageSize = 4096
totalSize = ceil(totalSizeWithoutPadding / pageSize) * pageSize

paddingSize = totalSize - totalSizeWithoutPadding
```

**示例**：
```
Body 长度 = 3000 bytes
totalSizeWithoutPadding = 16 + 3000 = 3016 bytes
totalSize = ceil(3016 / 4096) * 4096 = 1 * 4096 = 4096 bytes
paddingSize = 4096 - 3016 = 1080 bytes

Body 长度 = 8000 bytes
totalSizeWithoutPadding = 16 + 8000 = 8016 bytes
totalSize = ceil(8016 / 4096) * 4096 = 2 * 4096 = 8192 bytes
paddingSize = 8192 - 8016 = 176 bytes
```

## 3. CRC32 校验

### 3.1 校验范围

CRC32 校验覆盖 **Header + Body**，不包含 Tailer 和 Padding。

```
┌─────────────────────────────────────────────────────────────┐
│                    CRC32 校验范围                            │
├─────────────────────────────────────────────────────────────┤
│                                                              │
│  ┌─────────────────────────────────────────────────────┐    │
│  │ Header + Body                                       │    │
│  │ ◄──────────────── CRC32 校验范围 ────────────────► │    │
│  └─────────────────────────────────────────────────────┘    │
│                                                              │
│  CRC32 = crc32(header + body)                               │
│                                                              │
└─────────────────────────────────────────────────────────────┘
```

### 3.2 校验流程

#### 写入时

```
createWriteItem(type, data)
    │
    ▼
┌─────────────────────────────────────┐
│  1. 构造 Header                     │
│     header.magic = 0xABCD           │
│     header.type = type              │
│     header.length = data.length     │
└─────────────────────────────────────┘
    │
    ▼
┌─────────────────────────────────────┐
│  2. 计算 CRC32                      │
│     crc32 = CRC32.compute(          │
│         header + data)              │
└─────────────────────────────────────┘
    │
    ▼
┌─────────────────────────────────────┐
│  3. 计算 Padding                    │
│     totalSize = 16 + data.length    │
│     alignedSize = alignTo4K(        │
│         totalSize)                  │
│     paddingSize = alignedSize -     │
│         totalSize                   │
└─────────────────────────────────────┘
    │
    ▼
┌─────────────────────────────────────┐
│  4. 组装 Write Item                 │
│     writeItem = header + data +     │
│         crc32 + padding(zeros)      │
│     return writeItem                │
└─────────────────────────────────────┘
```

#### 读取时

```
readWriteItem(chunk, offset)
    │
    ▼
┌─────────────────────────────────────┐
│  1. 读取 Header                     │
│     header = read(chunk, offset, 12)│
│     magic = header.readShort(0)     │
│     type = header.readShort(2)      │
│     length = header.readInt(4)      │
└─────────────────────────────────────┘
    │
    ▼
┌─────────────────────────────────────┐
│  2. 验证 Magic                      │
│     if (magic != 0xABCD):           │
│         throw new InvalidDataException(│
│             "Invalid magic: " + magic)│
└─────────────────────────────────────┘
    │
    ▼
┌─────────────────────────────────────┐
│  3. 读取 Body 和 CRC32              │
│     body = read(chunk, offset + 12, │
│         length)                     │
│     storedCrc32 = read(chunk,       │
│         offset + 12 + length, 4)    │
└─────────────────────────────────────┘
    │
    ▼
┌─────────────────────────────────────┐
│  4. 验证 CRC32                      │
│     computedCrc32 = CRC32.compute(  │
│         header + body)              │
│     if (computedCrc32 != storedCrc32):│
│         throw new CRC32MismatchException(│
│             "CRC32 mismatch: " +    │
│             "expected=" + storedCrc32 +│
│             ", actual=" + computedCrc32)│
└─────────────────────────────────────┘
    │
    ▼
┌─────────────────────────────────────┐
│  5. 返回数据                        │
│     return WriteItem(type, body)    │
└─────────────────────────────────────┘
```

### 3.3 CRC32 算法

使用标准的 CRC32 算法（IEEE 802.3）：

```
CRC32 参数：
  - 多项式：0xEDB88320
  - 初始值：0xFFFFFFFF
  - 异或输出：0xFFFFFFFF
  - 输入反转：是
  - 输出反转：是

Java 实现：
  java.util.zip.CRC32

性能：
  - 硬件加速：现代 CPU 支持 CRC32 指令
  - 软件实现：约 1-2 GB/s
```

## 4. 4K 对齐

### 4.1 对齐原因

| 原因 | 说明 |
|------|------|
| **性能优化** | 与文件系统块大小对齐，减少 IO 次数 |
| **部分写入检测** | 便于检测写入过程中的崩溃 |
| **空间管理** | 简化 Chunk 偏移量计算 |

### 4.2 对齐策略

对齐规则：每个 Write Item 总长度必须是 4KB（4096 bytes）的整数倍。不足的部分用 Padding 填充。计算公式：alignedSize = ceil(size / 4096) * 4096。对齐的目的是与文件系统块大小匹配、简化偏移量计算、便于检测部分写入。

```
对齐规则：

1. 每个 Write Item 的总长度必须是 4KB 的整数倍
2. 不足 4KB 的部分用 Padding 填充
3. Padding 使用全 0 字节

对齐计算：
  alignedSize = ((size + 4095) / 4096) * 4096
  paddingSize = alignedSize - size
```

### 4.3 对齐示例

三个典型对齐示例：(1) Body 100B → 总 116B → 对齐到 4096B，Padding 3980B（小数据浪费较大）；(2) Body 4000B → 总 4016B → 对齐到 8192B，Padding 4176B；(3) Body 10000B → 总 10016B → 对齐到 12288B，Padding 2272B。数据越大，Padding 开销比例越低。

```
示例 1：小数据
  Body 长度 = 100 bytes
  Header + Body + CRC32 = 12 + 100 + 4 = 116 bytes
  对齐后 = 4096 bytes
  Padding = 3980 bytes

示例 2：中等数据
  Body 长度 = 4000 bytes
  Header + Body + CRC32 = 12 + 4000 + 4 = 4016 bytes
  对齐后 = 8192 bytes
  Padding = 176 bytes

示例 3：大数据
  Body 长度 = 10000 bytes
  Header + Body + CRC32 = 12 + 10000 + 4 = 10016 bytes
  对齐后 = 12288 bytes (3 * 4096)
  Padding = 2272 bytes
```

## 5. 部分写入处理

### 5.1 部分写入场景

部分写入发生在写入过程中系统崩溃：

```
正常写入：
  Chunk: [Item1][Item2][Item3][Item4]...
         └────── 完整 ──────┘

崩溃场景：
  Chunk: [Item1][Item2][Item3][部分写入...]
         └────── 完整 ──────┘ └─ 不完整 ─┘
```

### 5.2 检测策略

**关键点**：只读取 Chunk 从头开始的所有**完整** Write Item。

```
读取完整 Write Item 流程：

readAllWriteItems(chunk)
    │
    ▼
┌─────────────────────────────────────┐
│  1. 从 Chunk 开头开始读取           │
│     offset = 0                      │
│     items = []                      │
└─────────────────────────────────────┘
    │
    ▼
┌─────────────────────────────────────┐
│  2. 循环读取 Write Item             │
│     while (offset < chunk.size):    │
│         try:                        │
│             item = readWriteItem(   │
│                 chunk, offset)      │
│             items.add(item)         │
│             offset += item.alignedSize│
│         catch (Exception e):        │
│             // 遇到不完整或损坏数据 │
│             log.warn(               │
│                 "Incomplete or " +  │
│                 "corrupted data " + │
│                 "at offset: " +     │
│                 offset)             │
│             break  // 停止读取      │
└─────────────────────────────────────┘
    │
    ▼
┌─────────────────────────────────────┐
│  3. 返回完整的 Write Item 列表      │
│     return items                    │
└─────────────────────────────────────┘
```

### 5.3 部分写入检测细节

四种不完整 Write Item 的检测方式：(1) Magic 不匹配 → 数据不完整或损坏；(2) Length 超出 Chunk 边界 → Body 不完整；(3) CRC32 校验失败 → 数据损坏；(4) Chunk 结尾（offset >= chunk.size）→ 正常结束。除第 4 种外都记录日志并停止读取。

```
检测不完整 Write Item：

1. Magic 不匹配
   - 读取的 Magic != 0xABCD
   - 说明数据不完整或损坏

2. 长度超出 Chunk 边界
   - offset + 12 + length > chunk.size
   - 说明 Body 不完整

3. CRC32 校验失败
   - computedCrc32 != storedCrc32
   - 说明数据损坏

4. Chunk 结尾
   - offset >= chunk.size
   - 正常结束
```

### 5.4 崩溃恢复处理

崩溃恢复时**不扫描所有 Chunk**，而是根据 tree-metadata.pb 中的 journalReplayPoint 和 journal-region.pb 中的 Region 索引精确定位需要回放的 JOURNAL Chunk 范围。从 replayPoint 指定的 Region/Offset 开始，逐个读取 Write Item 并校验 CRC32，只回放完整的 Journal Entry 到 MemoryTable。Page 类型的 Chunk 不参与崩溃恢复——Page 数据通过 tree-metadata.pb 中的 rootLocation 按需访问。不完整写入的 Write Item 被忽略，后续被 GC 回收。

```
崩溃恢复时的数据处理：

recoverFromCrash()
    │
    ▼
┌─────────────────────────────────────┐
│  1. 加载元数据定位回放范围          │
│     replayPoint = treeMetadata      │
│         .journalReplayPoint         │
│     regionIndex = loadRegionIndex() │
│     // 根据 replayPoint.regionMajor│
│     // 定位起始 JOURNAL Chunk       │
└─────────────────────────────────────┘
    │
    ▼
┌─────────────────────────────────────┐
│  2. 从 replayPoint 开始读取         │
│     chunk = regionIndex.findChunk(  │
│         replayPoint.regionMajor)    │
│     items = readWriteItems(chunk,   │
│         replayPoint.offset)         │
│     // 使用部分写入检测策略         │
│     // 只读取完整的 Write Item      │
└─────────────────────────────────────┘
    │
    ▼
┌─────────────────────────────────────┐
│  3. 继续读取后续 Region 的 Chunk    │
│     for region in regionIndex       │
│         .after(replayPoint.major):  │
│         chunk = getChunk(region)    │
│         items += readWriteItems(    │
│             chunk, 0)               │
└─────────────────────────────────────┘
    │
    ▼
┌─────────────────────────────────────┐
│  4. 回放完整的 Journal Entry        │
│     for item in items:              │
│         entry = parseJournalEntry(  │
│             item.body)              │
│         applyToMemoryTable(entry)   │
└─────────────────────────────────────┘
    │
    ▼
┌─────────────────────────────────────┐
│  5. 忽略部分写入的数据              │
│     // CRC32 校验失败的 Write Item  │
│     // 被忽略，不回放               │
│     // 后续会被 GC 回收             │
└─────────────────────────────────────┘
```

## 6. 错误处理

### 6.1 CRC32 校验失败

CRC32 校验失败是**严重错误**，需要向上层报告，最终在 KVStore 层级 stopServing。

```
CRC32 校验失败处理：

onCRC32Error(chunk, offset, expected, actual)
    │
    ▼
┌─────────────────────────────────────┐
│  1. 记录错误日志                    │
│     log.error(                      │
│         "CRC32 mismatch in chunk " +│
│         chunk.id + " at offset " +  │
│         offset + ": expected=" +    │
│         expected + ", actual=" +    │
│         actual)                     │
└─────────────────────────────────────┘
    │
    ▼
┌─────────────────────────────────────┐
│  2. 触发 stopServing                │
│     stopServing(                    │
│         "Data corruption detected: "│
│         + "CRC32 mismatch")         │
└─────────────────────────────────────┘
```

### 6.2 Magic 不匹配

Magic 不匹配可能表示：
- 数据损坏
- 部分写入
- Chunk 格式错误

```
Magic 不匹配处理：

onMagicMismatch(chunk, offset, magic)
    │
    ▼
┌─────────────────────────────────────┐
│  1. 判断是否为 Chunk 结尾           │
│     if (isChunkEnd(chunk, offset)): │
│         // 正常结束                 │
│         return                      │
└─────────────────────────────────────┘
    │
    ▼
┌─────────────────────────────────────┐
│  2. 记录警告日志                    │
│     log.warn(                       │
│         "Invalid magic in chunk " + │
│         chunk.id + " at offset " +  │
│         offset + ": 0x" +           │
│         Integer.toHexString(magic)) │
└─────────────────────────────────────┘
    │
    ▼
┌─────────────────────────────────────┐
│  3. 停止读取该 Chunk                │
│     // 可能是部分写入               │
│     // 后续数据不再处理             │
└─────────────────────────────────────┘
```

### 6.3 错误分级

| 错误类型 | 严重程度 | 处理方式 |
|----------|----------|----------|
| Magic 不匹配（Chunk 中间） | WARNING | 停止读取该 Chunk |
| Magic 不匹配（Chunk 结尾） | INFO | 正常结束 |
| CRC32 校验失败 | CRITICAL | stopServing |
| 长度超出边界 | WARNING | 停止读取该 Chunk |

## 7. 性能考虑

### 7.1 CRC32 计算开销

CRC32 的计算性能分析：现代 CPU 支持硬件加速，软件实现约 1-2 GB/s，对系统整体性能影响可忽略。

```
CRC32 性能：

数据大小    计算时间    吞吐量
────────────────────────────────
1 KB        ~1 μs       ~1 GB/s
4 KB        ~4 μs       ~1 GB/s
16 KB       ~16 μs      ~1 GB/s
64 KB       ~64 μs      ~1 GB/s

优化方案：
  1. 使用硬件加速（CRC32 指令）
  2. 并行计算（多线程）
  3. 缓存计算结果（读缓存）
```

### 7.2 4K 对齐空间开销

4K 对齐的空间开销分析：平均 Body 4KB 时约 33% 空间用于 Header + CRC32 + Padding。优化方案：批量写入合并多个小数据；启用压缩减少 Body 大小；根据工作负载调整对齐大小（如 1KB 对齐减少浪费）。实际场景中 Page 数据通常在 4KB 左右，浪费比例可接受。

```
空间开销分析：

平均 Body 大小：4 KB
Header + CRC32：16 bytes
平均 Padding：~2 KB（平均 0.5 个 4KB 块）

空间开销比例：
  (16 + 2048) / (4096 + 16 + 2048) ≈ 33%

优化方案：
  1. 批量写入（合并多个小数据）
  2. 压缩（减少 Body 大小）
  3. 调整对齐大小（如 1KB 对齐）
```

### 7.3 写入性能优化

Write Item 写入的优化策略：批量写入合并多个 Item 减少 I/O 次数；异步 CRC32 计算不阻塞主流程；Write Buffer 缓存待写入数据批量刷盘。

```
写入优化策略：

1. 批量写入
   - 合并多个 Write Item 一起写入
   - 减少 IO 次数

2. 异步 CRC32 计算
   - 在后台线程计算 CRC32
   - 不阻塞主写入流程

3. Write Buffer
   - 缓存待写入的数据
   - 批量刷盘
```

## 8. 配置参数

| 参数 | 默认值 | 描述 |
|------|--------|------|
| writeItem.pageSize | 4096 | Write Item 对齐大小（字节） |
| writeItem.magic | 0xABCD | Magic 数值 |
| writeItem.verifyOnRead | true | 读取时是否验证 CRC32 |
| writeItem.verifyOnWrite | true | 写入后是否验证 CRC32 |

## 9. 相关文档

- [错误处理设计](design-error-handling.md)：stopServing 机制
- [存储层设计](design-storage.md)：Chunk 管理
- [Journal 设计](design-journal.md)：Journal 写入流程
- [Page 设计](design-page.md)：Page 数据格式
