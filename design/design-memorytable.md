# 内存表设计

## 1. 概述

MemoryTable 是 LSM Tree 架构中的内存组件，提供：
- **快速写入**：内存操作，无磁盘 I/O
- **有序存储**：基于 TreeMap，支持范围查询
- **写入缓冲**：批量 Flush 到 B+Tree，减少磁盘写入
- **多份机制**：支持多个 MemoryTable，提高并发性能

```
┌─────────────────────────────────────────────────────┐
│              MemoryTableManager                      │
│  ┌───────────────────────────────────────────────┐  │
│  │  activeTable: MemoryTable (可写)              │  │
│  │  ┌─────┬─────┬─────┬─────┬─────┐             │  │
│  │  │ k:1 │ k:3 │ k:5 │ k:7 │ ... │             │  │
│  │  │ v:A │ v:B │ DEL │ v:D │     │             │  │
│  │  └─────┴─────┴─────┴─────┴─────┘             │  │
│  └───────────────────────────────────────────────┘  │
│                                                      │
│  sealedTables: List<MemoryTable> (只读，等待 Dump)  │
│  ┌───────────────────────────────────────────────┐  │
│  │  Sealed Table 1  │  Sealed Table 2  │  ...    │  │
│  └───────────────────────────────────────────────┘  │
│                                                      │
│  threshold: 单个 MemoryTable 的 Flush 阈值          │
└─────────────────────────────────────────────────────┘
```

### 1.1 多 MemoryTable 机制

**设计背景**：
- Dump 速度不一定很快，需要避免阻塞写入
- 支持多个 MemoryTable 并存，提高并发性能

**机制说明**：
- **Active Table**：当前活跃的可写 MemoryTable
- **Sealed Tables**：已封存只读的 MemoryTable 列表，等待 Dump
- **Seal 操作**：当 Active Table 达到阈值时，封存为只读，创建新的 Active Table

## 2. 类设计

### 2.1 IndexValue

IndexValue 是 MemoryTable 存储的值类型，支持 NORMAL 和 TOMBSTONE 两种类型。详见 [Key-Value 存储格式设计](design-key-value.md)。

| 类型 | 描述 |
|------|------|
| NORMAL | 正常值，包含实际数据 |
| TOMBSTONE | 删除标记，标记 key 已删除 |

**核心方法**：
- `isTombstone()`: 判断是否为删除标记
- `getData()`: 获取 Protobuf 编码的数据（NORMAL 类型）
- `createNormal(data)`: 创建正常值
- `createTombstone()`: 创建删除标记

### 2.2 JournalReplayPoint

记录 Entry 在 Journal 中的位置。

| 属性 | 类型 | 描述 |
|------|------|------|
| regionMajor | long | Journal Region 主版本号 |
| regionMinor | long | Journal Region 次版本号（保留） |
| offset | int | Chunk 内偏移量 |

### 2.3 MemoryTableState

MemoryTable 的状态枚举。

| 状态 | 描述 |
|------|------|
| ACTIVE | 活跃状态，可写入 |
| SEALED | 已封存，只读，等待 Dump |

### 2.4 MemoryTable

单个内存表，支持 ACTIVE 和 SEALED 两种状态。

| 属性 | 类型 | 描述 |
|------|------|------|
| data | TreeMap<IndexKey, IndexValue> | 有序键值存储 |
| size | int | 当前内存占用大小 |
| threshold | int | Seal 阈值（字节） |
| state | MemoryTableState | 当前状态（ACTIVE/SEALED） |
| firstReplayPoint | JournalReplayPoint | 第一个写入的回放点 |
| lastReplayPoint | JournalReplayPoint | 最后一个写入的回放点 |

| 方法 | 返回类型 | 描述 |
|------|----------|------|
| put | void | 插入键值对（仅 ACTIVE 状态可用） |
| delete | void | 插入 Tombstone（仅 ACTIVE 状态可用） |
| get | IndexValue | 获取值（可能为 Tombstone） |
| rangeQuery | List<Map.Entry<IndexKey, IndexValue>> | 范围查询 |
| getRange | NavigableMap<IndexKey, IndexValue> | 获取范围内的键值对 |
| shouldSeal | boolean | 是否达到 Seal 阈值 |
| seal | void | 封存为只读状态 |
| isSealed | boolean | 是否已封存 |
| clear | void | 清空内存表 |
| getData | TreeMap<IndexKey, IndexValue> | 获取所有数据 |
| getReplayPointRange | JournalReplayPoint[] | 获取回放点范围 |

### 2.5 MemoryTableManager

管理多个 MemoryTable，协调写入和 Dump。

| 属性 | 类型 | 描述 |
|------|------|------|
| activeTable | MemoryTable | 当前活跃的可写 MemoryTable |
| sealedTables | List<MemoryTable> | 已封存的只读 MemoryTable 列表 |
| threshold | int | 单个 MemoryTable 的 Seal 阈值 |
| maxSealedTables | int | 最大封存表数量（防止 OOM） |

| 方法 | 返回类型 | 描述 |
|------|----------|------|
| put | void | 写入键值对（写入 activeTable） |
| delete | void | 写入 Tombstone（写入 activeTable） |
| get | IndexValue | 获取值（遍历所有表，可能为 Tombstone） |
| rangeQuery | List<Map.Entry<IndexKey, IndexValue>> | 范围查询（合并所有表） |
| seal | void | 封存当前 activeTable，创建新的 |
| getSealedTables | List<MemoryTable> | 获取所有封存表 |
| removeSealedTable | void | 移除已 Dump 的封存表 |
| shouldSeal | boolean | 是否需要封存 |
| shouldDump | boolean | 是否需要 Dump |

## 3. 核心操作流程

### 3.1 MemoryTableManager 写入流程 (put)

MemoryTableManager 的写入操作先检查当前 Active MemoryTable 是否需要 Seal（达到大小阈值），如果需要则先封存再写入新表。写入时将 key-value 和 Journal 回放点一起记录到 Active Table 中，写入后再次检查 Seal 条件。双重检查确保大批量写入时不会遗漏 Seal 时机。

```
put(key, value, replayPoint)
    │
    ▼
┌─────────────────────────────────────┐
│  1. 检查是否需要 Seal               │
│     if (activeTable.shouldSeal())   │
│         seal()                      │
└─────────────────────────────────────┘
    │
    ▼
┌─────────────────────────────────────┐
│  2. 写入 Active Table               │
│     activeTable.put(                │
│         key, value, replayPoint)    │
└─────────────────────────────────────┘
    │
    ▼
┌─────────────────────────────────────┐
│  3. 再次检查是否需要 Seal           │
│     if (activeTable.shouldSeal())   │
│         seal()                      │
└─────────────────────────────────────┘
```

### 3.2 MemoryTableManager 删除流程 (delete)

删除操作与写入流程结构相同，区别在于向 Active MemoryTable 中写入的是 Tombstone 标记而非实际数据。Tombstone 在 MemoryTable 中占用内存空间，同样参与 Seal 阈值计算。

```
delete(key, replayPoint)
    │
    ▼
┌─────────────────────────────────────┐
│  1. 检查是否需要 Seal               │
│     if (activeTable.shouldSeal())   │
│         seal()                      │
└─────────────────────────────────────┘
    │
    ▼
┌─────────────────────────────────────┐
│  2. 写入 Tombstone 到 Active Table  │
│     activeTable.delete(             │
│         key, replayPoint)           │
└─────────────────────────────────────┘
    │
    ▼
┌─────────────────────────────────────┐
│  3. 再次检查是否需要 Seal           │
│     if (activeTable.shouldSeal())   │
│         seal()                      │
└─────────────────────────────────────┘
```

### 3.3 Seal 操作流程

Seal 操作将当前 Active MemoryTable 标记为只读（SEALED），添加到 Sealed Tables 列表中，然后创建一个新的空 Active MemoryTable 继续接收写入。Seal 操作本身非常轻量（纳秒级：设置标志位 + 对象分配），相对于 Journal 落盘的毫秒级开销可以忽略，因此同步执行即可，无需异步化。Seal 后如果 Sealed Tables 数量达到 maxSealedTables，会触发 Dump 操作。

```
seal()
    │
    ▼
┌─────────────────────────────────────┐
│  1. 封存当前 Active Table           │
│     activeTable.seal()              │
│     // state = SEALED               │
│     // 变为只读                     │
└─────────────────────────────────────┘
    │
    ▼
┌─────────────────────────────────────┐
│  2. 添加到 Sealed Tables 列表       │
│     sealedTables.add(activeTable)   │
└─────────────────────────────────────┘
    │
    ▼
┌─────────────────────────────────────┐
│  3. 创建新的 Active Table           │
│     activeTable = new MemoryTable(  │
│         threshold)                  │
│     // state = ACTIVE               │
└─────────────────────────────────────┘
    │
    ▼
┌─────────────────────────────────────┐
│  4. 检查是否需要 Dump               │
│     if (sealedTables.size() >=      │
│         maxSealedTables)            │
│         触发 Dump 操作              │
└─────────────────────────────────────┘
```

### 3.4 MemoryTableManager 读取流程 (get)

读取时按新鲜度顺序查找：先查 Active Table（最新数据），未命中则按时间倒序遍历 Sealed Tables。任一表中找到结果即返回（可能是 Tombstone，由调用者判断）。所有表都未命中则返回 null，调用者继续查 B+Tree。Sealed Tables 是只读的，读取完全无锁。

```
get(key)
    │
    ▼
┌─────────────────────────────────────┐
│  1. 查询 Active Table               │
│     value = activeTable.get(key)    │
└─────────────────────────────────────┘
    │
    ├─── value 存在 ────► return value
    │                       (可能是 Tombstone)
    │
    └─── value 不存在
              │
              ▼
    ┌─────────────────────────────────────┐
    │  2. 遍历 Sealed Tables（按时间倒序）│
    │     for table in sealedTables:      │
    │         value = table.get(key)      │
    │         if (value != null)          │
    │             return value            │
    │             (可能是 Tombstone)      │
    └─────────────────────────────────────┘
              │
              └─── 所有表都未找到 ────► return null
```

### 3.5 MemoryTable 写入流程 (put)

单个 MemoryTable 的写入操作：先检查状态是否为 ACTIVE（SEALED 状态不接受写入），然后计算内存增量（如果是更新已有 key，需减去旧值大小），写入 TreeMap 并更新回放点范围（firstReplayPoint 和 lastReplayPoint），用于后续 Dump 时确定 Journal 回放起点。

```
put(key, value, replayPoint)
    │
    ▼
┌─────────────────────────────────────┐
│  1. 检查状态                        │
│     if (state != ACTIVE)            │
│         throw IllegalStateException  │
└─────────────────────────────────────┘
    │
    ▼
┌─────────────────────────────────────┐
│  2. 计算大小增量                     │
│     delta = value.size()            │
│     if (oldValue exists)            │
│         delta -= oldValue.size()    │
└─────────────────────────────────────┘
    │
    ▼
┌─────────────────────────────────────┐
│  3. 写入 TreeMap                    │
│     data.put(key, value)            │
│     size += delta                   │
└─────────────────────────────────────┘
    │
    ▼
┌─────────────────────────────────────┐
│  4. 更新回放点范围                  │
│     if (firstReplayPoint == null)   │
│         firstReplayPoint = replayPoint│
│     lastReplayPoint = replayPoint   │
└─────────────────────────────────────┘
```

### 3.6 MemoryTable 删除流程 (delete)

删除操作创建一个 TombstoneIndexValue 写入 TreeMap，覆盖原有值（如果存在）。Tombstone 虽然不含实际数据，但仍占用 TreeMap 条目空间，参与 size 计算和 Seal 阈值判断。

```
delete(key, replayPoint)
    │
    ▼
┌─────────────────────────────────────┐
│  1. 检查状态                        │
│     if (state != ACTIVE)            │
│         throw IllegalStateException  │
└─────────────────────────────────────┘
    │
    ▼
┌─────────────────────────────────────┐
│  2. 创建 Tombstone                  │
│     tombstone = IndexValue          │
│         .createTombstone()          │
└─────────────────────────────────────┘
    │
    ▼
┌─────────────────────────────────────┐
│  3. 计算大小增量                     │
│     delta = tombstone.size()        │
│     if (oldValue exists)            │
│         delta -= oldValue.size()    │
└─────────────────────────────────────┘
    │
    ▼
┌─────────────────────────────────────┐
│  4. 写入 TreeMap（覆盖旧值）        │
│     data.put(key, tombstone)        │
│     size += delta                   │
└─────────────────────────────────────┘
    │
    ▼
┌─────────────────────────────────────┐
│  5. 更新回放点范围                  │
│     lastReplayPoint = replayPoint   │
└─────────────────────────────────────┘
```

### 3.7 MemoryTable Seal 操作流程

Seal 仅将状态从 ACTIVE 改为 SEALED，操作是幂等的（重复调用不报错）。一旦 SEALED，MemoryTable 变为只读，不再接受 put/delete 操作，可以被多个读线程安全地并发访问。

```
seal()
    │
    ▼
┌─────────────────────────────────────┐
│  1. 检查当前状态                    │
│     if (state == SEALED)            │
│         return  // 已封存，幂等     │
└─────────────────────────────────────┘
    │
    ▼
┌─────────────────────────────────────┐
│  2. 更改状态                        │
│     state = SEALED                  │
│     // 变为只读，不再接受写入       │
└─────────────────────────────────────┘
```

### 3.8 MemoryTableManager 范围查询流程 (rangeQuery)

范围查询需要合并所有 MemoryTable 的数据。合并顺序从最旧到最新（先遍历 Sealed Tables 正序，最后遍历 Active Table），使用 Map.put() 覆盖旧值，保证同一 key 只保留最新版本。结果中可能包含 Tombstone，由 KVStore 层的 rangeQuery() 负责过滤。

```
rangeQuery(startKey, endKey)
    │
    ▼
┌─────────────────────────────────────┐
│  1. 合并所有表的数据                │
│     results = Map()                 │
│                                      │
│     // 从最旧的 Sealed Table 开始   │
│     // 按时间正序遍历，新数据覆盖旧 │
│     for table in sealedTables       │
│         (从最旧到最新):             │
│         entries = table.getRange(   │
│             startKey, endKey)       │
│         for entry in entries:       │
│             results.put(entry.key, entry.value)│
│                                      │
│     // 最后查询 Active Table（最新） │
│     // Active Table 的数据覆盖所有旧数据│
│     entries = activeTable.getRange( │
│         startKey, endKey)           │
│     for entry in entries:           │
│         results.put(entry.key, entry.value)│
└─────────────────────────────────────┘
    │
    ▼
┌─────────────────────────────────────┐
│  2. 返回结果                        │
│     return results.entries()        │
│     // 返回 List<Map.Entry<IndexKey, IndexValue>>│
│     // 包含 Tombstone，由调用者过滤 │
└─────────────────────────────────────┘
```

## 4. 状态转换

### 4.1 MemoryTable 状态转换图

MemoryTable 只有两个状态：ACTIVE（可写）和 SEALED（只读）。Seal 操作是单向的、不可逆的。SEALED 后的 MemoryTable 等待 Dump 完成后被移除，内存释放。Dump 完成后的 MemoryTable 对象由 GC 自然回收。

```
                    seal()
    ┌─────────┐ ──────────────────► ┌─────────┐
    │ ACTIVE  │                      │ SEALED  │
    │ (可写)  │                      │ (只读)  │
    └─────────┘                      └─────────┘
         ▲                                  │
         │                                  │
         │          Dump 完成               │
         │          removeSealedTable()     │
         └──────────────────────────────────┘
```

### 4.2 IndexValue 状态转换图

IndexValue 在 MemoryTable 中的状态转换：不存在 → put → NORMAL；NORMAL → delete → TOMBSTONE；TOMBSTONE → put → NORMAL。delete 一个不存在的 key 也会创建 TOMBSTONE（幂等）。同一个 key 的最新状态总是覆盖旧状态。

```
                    put(key, value1)
    ┌─────────┐ ──────────────────► ┌───────────────────┐
    │  不存在  │                      │ IndexValue(value1)│
    └─────────┘                      └───────────────────┘
         ▲                                  │
         │                                  │
         │          delete(key)             │ put(key, value2)
         │                                  ▼
    ┌──────────────┐                  ┌───────────────────┐
    │  Tombstone   │ ◄─────────────── │ IndexValue(value2)│
    └──────────────┘   delete(key)    └───────────────────┘
         │                                  ▲
         │                                  │
         │          put(key, value3)        │
         └──────────────────────────────────┘
```

### 4.3 操作语义表

| 当前状态 | 操作 | 结果状态 | 说明 |
|----------|------|----------|------|
| 不存在 | put(k, v) | IndexValue(v) | 新增 |
| 不存在 | delete(k) | Tombstone | 记录删除 |
| IndexValue(v1) | put(k, v2) | IndexValue(v2) | 更新 |
| IndexValue(v1) | delete(k) | Tombstone | 标记删除 |
| Tombstone | put(k, v) | IndexValue(v) | 重新插入 |
| Tombstone | delete(k) | Tombstone | 幂等 |

## 5. Seal 与 Dump 触发机制

```
┌─────────────────────────────────────────────────────┐
│              Seal 与 Dump 触发流程                   │
├─────────────────────────────────────────────────────┤
│                                                      │
│  每次写入后检查:                                     │
│                                                      │
│  if (activeTable.shouldSeal())                      │
│      │                                               │
│      ├─── Seal 操作 ────► seal()                    │
│      │   - 封存当前 activeTable                     │
│      │   - 创建新的 activeTable                     │
│      │                                               │
│      └─── 检查 Dump 条件                            │
│          if (sealedTables.size() >= maxSealedTables)│
│              触发 Dump 操作                         │
│              - 合并所有 sealedTables                │
│              - 写入 B+Tree                          │
│              - 清空 sealedTables                    │
│                                                      │
└─────────────────────────────────────────────────────┘
```

## 6. 配置参数

| 配置项 | 默认值 | 描述 |
|--------|--------|------|
| threshold | 64MB | 单个 MemoryTable 触发 Seal 的阈值 |
| maxSealedTables | 3 | 最大封存表数量，超过触发 Dump |
| initialCapacity | 16 | TreeMap 初始容量 |

## 7. 与其他模块的交互

```
                    写入流程
┌──────────┐    put/delete    ┌────────────────────┐
│ KVStore  │ ───────────────► │ MemoryTableManager │
└──────────┘                  └────────────────────┘
      │                              │
      │                              │ isFull() → seal()
      │                              ▼
      │                       ┌────────────────────┐
      │                       │ Sealed Tables      │
      │                       │ (等待 Dump)        │
      │                       └────────────────────┘
      │                              │
      │         getSealedTables()    │
      │ ◄────────────────────────────┘
      │
      │  dump to B+Tree
      ▼
┌──────────┐
│ B+Tree   │
└──────────┘
```

## 8. 并发控制设计

> 本节描述 MemoryTable 内部的锁策略。上层的请求队列和 BatchWriter 机制详见 [并发控制设计](design-concurrency.md)。BatchWriter 一次获取写锁，批量更新多个请求，减少锁获取次数。

### 8.1 并发控制策略

MemoryTableManager 采用**读写锁 + 状态分离**的并发控制策略：

```
┌─────────────────────────────────────────────────────────────┐
│              MemoryTableManager 并发架构                     │
├─────────────────────────────────────────────────────────────┤
│                                                              │
│  写路径                        读路径                        │
│  ┌──────────────┐             ┌──────────────┐             │
│  │   Write      │             │   Read       │             │
│  │   Lock       │             │   Lock       │             │
│  └──────┬───────┘             └──────┬───────┘             │
│         │                            │                      │
│         ▼                            ▼                      │
│  ┌──────────────┐             ┌──────────────┐             │
│  │    Active    │◄────────────┤              │             │
│  │ MemoryTable  │  读写锁     │              │             │
│  │  (可写)      │             │              │             │
│  └──────────────┘             │              │             │
│         │                     │              │             │
│         │ Seal                │              │             │
│         ▼                     │              │             │
│  ┌──────────────┐             │              │             │
│  │    New       │             │              │             │
│  │    Active    │             │              │             │
│  └──────────────┘             │              │             │
│                               │              │             │
│  ┌────────────────────────────┴──────────────┘             │
│  │         Sealed MemoryTables (只读)                      │
│  │  ┌──────┐ ┌──────┐ ┌──────┐                           │
│  │  │ MT1  │ │ MT2  │ │ MT3  │  无需加锁                 │
│  │  └──────┘ └──────┘ └──────┘                           │
│  └────────────────────────────────────────────────────────┘
│                                                              │
└─────────────────────────────────────────────────────────────┘
```

### 8.2 锁策略详解

#### **Active MemoryTable 锁策略**

```
操作类型        锁类型           并发度           说明
──────────────────────────────────────────────────────────
put()          写锁            串行化           写写互斥
delete()       写锁            串行化           写写互斥
get()          读锁            读读并发         读写互斥
rangeQuery()   读锁            读读并发         读写互斥
seal()         写锁            串行化           状态变更

关键点：
- 写操作必须获取写锁，保证写写互斥
- 读操作获取读锁，支持读读并发
- 读写互斥，保证数据一致性
```

#### **Sealed MemoryTables 无锁访问**

```
Sealed Tables 特性：
  - 状态：SEALED（只读）
  - 并发访问：完全无锁
  - 性能：最优

访问模式：
  读线程1 ──► Sealed Table 1 (无锁)
  读线程2 ──► Sealed Table 2 (无锁)
  读线程3 ──► Sealed Table 3 (无锁)

优势：
  - 多个读操作完全并发
  - 无锁竞争
  - 性能最优
```

### 8.3 Seal 操作并发处理

#### **Seal 操作流程**

```
Seal 操作流程图：

写线程                Active MT           Sealed List
  │                      │                    │
  │   writeLock()        │                    │
  ├─────────────────────►│                    │
  │   (获得写锁)         │                    │
  │                      │                    │
  │   put(key, value)    │                    │
  ├─────────────────────►│                    │
  │                      │                    │
  │   shouldSeal()?      │                    │
  ├─────────────────────►│                    │
  │   ◄── true ──────────┤                    │
  │                      │                    │
  │   seal()             │                    │
  ├─────────────────────►│                    │
  │                      │   state=SEALED     │
  │                      ├───────────────────►│
  │                      │   (添加到列表)     │
  │                      │                    │
  │   new MemoryTable()  │                    │
  ├─────────────────────►│                    │
  │   (创建新Active)     │                    │
  │                      │                    │
  │   unlock()           │                    │
  └─────────────────────►│                    │
```

#### **Seal 操作性能分析**

```
Seal 操作开销分解：

1. 设置状态标志：
   - activeMemoryTable.seal()
   - state = SEALED
   - 开销：纳秒级

2. 添加到 Sealed List：
   - sealedTables.add(activeMemoryTable)
   - List.add() 操作
   - 开销：纳秒级

3. 创建新 MemoryTable：
   - activeMemoryTable = new MemoryTable(threshold)
   - 对象分配
   - 开销：纳秒级

总开销：100-1000 纳秒

对比 Journal 落盘：
  - Journal.write(): 1-10 毫秒
  - 差异：10000-100000 倍

结论：
  Seal 操作开销相对 Journal 落盘可完全忽略
  无需异步 Seal、双缓冲等复杂设计
```

### 8.4 并发场景分析

#### **场景1：并发写入**

```
时序图：

写线程1            写线程2            Active MT
  │                  │                   │
  │  writeLock()     │                   │
  ├─────────────────────────────────────►│
  │  (获得锁)        │                   │
  │                  │  writeLock()      │
  │                  ├──────────────────►│
  │                  │  (阻塞等待)       │
  │  put(k1, v1)     │                   │
  ├─────────────────────────────────────►│
  │  unlock()        │                   │
  ├─────────────────────────────────────►│
  │                  │  (获得锁)         │
  │                  │  put(k2, v2)      │
  │                  ├──────────────────►│
  │                  │  unlock()         │
  │                  ├──────────────────►│

结果：写操作串行化，数据一致
```

#### **场景2：并发读写**

```
时序图：

写线程            读线程            Active MT
  │                 │                   │
  │  writeLock()    │                   │
  ├────────────────────────────────────►│
  │  (获得锁)       │                   │
  │                 │  readLock()       │
  │                 ├──────────────────►│
  │                 │  (阻塞，读写互斥) │
  │  put(k1, v1)    │                   │
  ├─────────────────────────────────────►│
  │  unlock()       │                   │
  ├─────────────────────────────────────►│
  │                 │  (获得读锁)       │
  │                 │  get(k1)          │
  │                 ├──────────────────►│
  │                 │  ◄── v1 ──────────┤
  │                 │  unlock()         │
  │                 ├──────────────────►│

结果：读写互斥，读操作看到最新数据
```

#### **场景3：并发读取 Sealed Tables**

```
时序图：

读线程1           读线程2           Sealed Tables
  │                 │                   │
  │  get(k1)        │                   │
  ├─────────────────────────────────────►│
  │  (无锁访问)     │                   │
  │                 │  get(k2)          │
  │                 ├──────────────────►│
  │                 │  (无锁访问)       │
  │  ◄── v1 ────────┤                   │
  │                 │  ◄── v2 ──────────┤

结果：多个读操作完全并发，性能最优
```

### 8.5 Dump 期间的并发处理

#### **Dump 操作流程**

```
Dump 操作流程图：

Dump线程          MemoryTableMgr      Sealed List
  │                    │                   │
  │  readLock()        │                   │
  ├───────────────────►│                   │
  │  (获取读锁)        │                   │
  │                    │                   │
  │  getSealedTables() │                   │
  ├───────────────────►│                   │
  │  ◄── snapshot ─────┤                   │
  │  (List快照)        │                   │
  │                    │                   │
  │  unlock()          │                   │
  ├───────────────────►│                   │
  │                    │                   │
  │  merge to B+Tree   │                   │
  │  ...               │                   │
  │                    │                   │
  │  writeLock()       │                   │
  ├───────────────────►│                   │
  │  (获取写锁)        │                   │
  │                    │                   │
  │  removeSealed()    │                   │
  ├───────────────────────────────────────►│
  │  (移除已Dump)      │                   │
  │                    │                   │
  │  unlock()          │                   │
  └───────────────────►│                   │
```

#### **Dump 期间写入处理**

```
时序图：

Dump线程          写线程            MemoryTableMgr
  │                 │                     │
  │  readLock()     │                     │
  ├───────────────────────────────────────►│
  │  (获取读锁)     │                     │
  │                 │  writeLock()        │
  │                 ├────────────────────►│
  │                 │  (阻塞，等待读锁释放)│
  │  getSealed()    │                     │
  ├───────────────────────────────────────►│
  │  unlock()       │                     │
  ├───────────────────────────────────────►│
  │                 │  (获得写锁)         │
  │                 │  put(k1, v1)        │
  │                 ├────────────────────►│
  │                 │  unlock()           │
  │                 ├────────────────────►│

关键点：
- Dump 获取 Sealed Tables 快照后立即释放读锁
- 写操作可以继续进行
- Dump 和写入并行执行
```

### 8.6 设计优势

#### **优势1：简洁高效**

```
设计特点：
  - 使用标准读写锁
  - Seal 同步执行（纳秒级）
  - 简单 List 快照
  - 无复杂状态管理

代码复杂度：
  - 核心代码：~100 行
  - 易于理解和维护
  - 测试简单
```

#### **优势2：性能优异**

```
并发性能：
  - 读读并发：高（读锁共享）
  - 读写并发：中等（读写互斥）
  - 写写并发：低（串行化）
  - Sealed Tables：完全并发（无锁）

关键路径延迟：
  - 写操作：Journal 落盘（毫秒级）+ 写锁（纳秒级）
  - 读操作：读锁（纳秒级）+ TreeMap 查询（微秒级）
  - Seal 操作：纳秒级（相对 Journal 可忽略）
```

#### **优势3：避免过度设计**

```
不采用的复杂方案：

1. 分段锁：
   - TreeMap 的 range 操作难以实现
   - 复杂度高，收益低

2. Copy-on-Write：
   - 每次复制整个 TreeMap
   - Overhead 远大于锁

3. 乐观锁：
   - TreeMap 不支持 CAS
   - 实现复杂度高

4. 引用计数：
   - Java GC 自动管理内存
   - 增加复杂度，易出错

5. 异步 Seal：
   - Seal 操作纳秒级
   - 相对 Journal 可忽略
   - 无需异步化
```

### 8.7 性能特性总结

并发性能总结：写操作受 Journal I/O 限制（毫秒级），锁竞争（纳秒级）可忽略；读 Active Table 需要读锁但开销极小；读 Sealed Tables 和 B+Tree 完全无锁；Seal 操作纳秒级同步执行；Dump 后台执行不阻塞读写。真正的性能瓶颈是 Journal 落盘的磁盘 I/O。

```
操作类型              并发度           锁竞争         性能
────────────────────────────────────────────────────────
写操作                串行化           高             受 Journal 限制
读操作  高           中             良好
读操作       完全并发         无             优秀
Seal 操作             串行化           低             纳秒级
Dump 操作             并行             无             后台执行

瓶颈分析：
  - 真正瓶颈：Journal 落盘（磁盘 I/O）
  - 非瓶颈：锁竞争、Seal 操作、List 操作

优化方向：
  - Journal 性能优化（批量写入、异步刷盘）
  - B+Tree 缓存优化
  - Dump 并行化
```

## 9. 内存管理策略

### 9.1 内存限制配置

内存管理采用三级阈值：totalMemoryLimit（总上限，默认 512MB）、busyThreshold（忙碌阈值，75%）和 criticalThreshold（危急阈值，90%）。不同阈值触发不同的应对策略，实现内存使用的优雅降级。

```
内存阈值配置：

totalMemoryLimit: 总内存限制
  - 默认值：512 MB
  - 包含 Active + Sealed MemoryTables

busyThreshold: 忙碌阈值
  - 默认值：totalMemoryLimit * 0.75 (384 MB)
  - 触发条件：拒绝低优先级请求

criticalThreshold: 危急阈值
  - 默认值：totalMemoryLimit * 0.9 (460 MB)
  - 触发条件：拒绝所有请求

配置示例：
  totalMemoryLimit = 512 MB
  busyThreshold = 384 MB (75%)
  criticalThreshold = 460 MB (90%)
```

### 9.2 内存监控

实时监控当前内存占用（Active + Sealed Tables 总和），提供 NORMAL/BUSY/CRITICAL 三种状态判断。使用 AtomicLong 保证监控数据的线程安全。

```
实时内存监控：

class MemoryMonitor {
    // 当前总内存占用
    currentMemory: AtomicLong
    
    // Active MemoryTable 大小
    activeTableSize: long
    
    // Sealed Tables 总大小
    sealedTablesSize: long
    
    // 更新内存统计
    updateMemoryUsage():
        currentMemory.set(
            activeTableSize + sealedTablesSize
        )
    
    // 检查内存状态
    checkMemoryStatus(): MemoryStatus {
        memory = currentMemory.get()
        
        if (memory >= criticalThreshold):
            return CRITICAL
        else if (memory >= busyThreshold):
            return BUSY
        else:
            return NORMAL
    }
}

enum MemoryStatus {
    NORMAL,    // 正常
    BUSY,      // 忙碌
    CRITICAL   // 危急
}
```

### 9.3 内存压力应对策略

#### **策略1：BUSY 状态处理**

```
BUSY 状态触发条件：
  - currentMemory >= busyThreshold
  - currentMemory < criticalThreshold

应对措施：
  1. 拒绝低优先级请求
     - 返回 MemoryBusyException
     - 建议客户端稍后重试
  
  2. 接受高优先级请求
     - 正常处理
     - 但可能触发强制 Dump
  
  3. 触发强制 Dump
     - 异步执行 Dump
     - 释放 Sealed Tables 内存

请求优先级定义：
  HIGH_PRIORITY:
    - 系统关键操作
    - 用户显式请求
  
  LOW_PRIORITY:
    - 后台任务
    - 批量导入操作
```

#### **策略2：CRITICAL 状态处理**

```
CRITICAL 状态触发条件：
  - currentMemory >= criticalThreshold

应对措施：
  1. 拒绝所有请求
     - 返回 MemoryCriticalException
     - 系统进入保护模式
  
  2. 阻塞写入
     - 等待 Dump 完成
     - 释放内存后恢复
  
  3. 紧急 Dump
     - 同步执行 Dump
     - 阻塞直到完成

处理流程：
  if (memoryStatus == CRITICAL):
      // 阻塞写入
      writeLock.lock()
      try:
          // 同步 Dump
          dumpSync()
          // 等待内存释放
          while (currentMemory >= busyThreshold):
              wait()
      finally:
          writeLock.unlock()
```

### 9.4 内存压力应对流程

每次写入请求前检查内存状态：NORMAL 正常处理；BUSY 检查优先级后决定是处理还是拒绝；CRITICAL 直接拒绝并触发同步 Dump。流程图展示了完整的决策路径。

```
内存压力应对流程图：

写入请求
    │
    ▼
┌─────────────────────────────────────┐
│  检查内存状态                       │
│  status = checkMemoryStatus()       │
└─────────────────────────────────────┘
    │
    ├─ NORMAL ────────────────────────►│
    │                                   │
    │                                   ▼
    │                         ┌─────────────────┐
    │                         │  正常处理请求   │
    │                         │  写入 Journal   │
    │                         │  更新 MemoryTable│
    │                         └─────────────────┘
    │
    ├─ BUSY ──────────────────────────►│
    │                                   │
    │                                   ▼
    │                         ┌─────────────────┐
    │                         │  检查优先级     │
    │                         └─────────────────┘
    │                                   │
    │                        ┌──────────┴──────────┐
    │                        │                     │
    │                     HIGH                  LOW
    │                        │                     │
    │                        ▼                     ▼
    │              ┌─────────────────┐   ┌──────────────┐
    │              │  正常处理       │   │  拒绝请求    │
    │              │  触发异步 Dump  │   │  返回异常    │
    │              └─────────────────┘   └──────────────┘
    │
    └─ CRITICAL ───────────────────────►│
                                        │
                                        ▼
                              ┌─────────────────┐
                              │  拒绝所有请求   │
                              │  同步 Dump      │
                              │  阻塞等待       │
                              └─────────────────┘
```

### 9.5 内存监控指标

MemoryTable 内存管理的监控指标：当前内存使用量、使用率、Active/Sealed Table 大小和数量、Dump 触发次数（按原因分类）、内存拒绝次数（按 BUSY/CRITICAL 分类）。

```
监控指标：

1. 当前内存使用量
   - currentMemoryUsage
   - 单位：MB
   - 更新频率：实时

2. 内存使用率
   - memoryUsageRatio = currentMemory / totalMemoryLimit
   - 单位：%
   - 更新频率：实时

3. Active MemoryTable 大小
   - activeTableSize
   - 单位：MB

4. Sealed Tables 总大小
   - sealedTablesSize
   - 单位：MB

5. Sealed Tables 数量
   - sealedTablesCount

6. Dump 触发次数
   - dumpTriggerCount
   - 按原因分类：
     - sizeTriggered: 大小触发
     - countTriggered: 数量触发
     - memoryTriggered: 内存压力触发

7. 内存拒绝次数
   - memoryRejectCount
   - 按类型分类：
     - busyReject: BUSY 状态拒绝
     - criticalReject: CRITICAL 状态拒绝
```

### 9.6 配置参数

| 配置项 | 默认值 | 描述 |
|--------|--------|------|
| totalMemoryLimit | 512 MB | 总内存限制 |
| busyThreshold | 384 MB (75%) | 忙碌阈值 |
| criticalThreshold | 460 MB (90%) | 危急阈值 |
| maxSealedTables | 3 | 最大 Sealed Tables 数量 |
| maxSealedMemory | 256 MB | Sealed Tables 最大总大小 |
| dumpCheckInterval | 100 ms | Dump 检查间隔 |

### 9.7 最佳实践

内存管理最佳实践：定期监控内存趋势、达到阈值立即触发 Dump、合理配置 busyThreshold(75-80%) 和 criticalThreshold(85-95%)、客户端实现指数退避重试。

```
内存管理最佳实践：

1. 监控内存使用
   - 定期检查内存状态
   - 记录内存使用趋势
   - 设置告警阈值

2. 及时释放内存
   - 达到阈值立即触发 Dump
   - 避免内存累积过多

3. 合理配置阈值
   - busyThreshold: 75% - 80%
   - criticalThreshold: 85% - 95%
   - 留有缓冲空间

4. 优雅降级
   - BUSY 状态拒绝低优先级请求
   - CRITICAL 状态拒绝所有请求
   - 避免系统崩溃

5. 客户端重试
   - 捕获 MemoryBusyException
   - 指数退避重试
   - 最大重试次数限制
```

## 10. 注意事项

1. **内存管理**：需要跟踪所有 MemoryTable 的总 size，避免 OOM
2. **并发控制**：
   - Active MemoryTable 使用读写锁，写操作串行化，读操作并发
   - Sealed MemoryTables 只读，完全无锁访问
   - Dump 期间获取 Sealed Tables 快照后立即释放锁
3. **Seal 性能**：Seal 操作极快（纳秒级），相对 Journal 落盘可忽略，同步执行即可
4. **Dump 非阻塞**：Dump 使用独立的 B+Tree Root，不阻塞读写操作
5. **避免过度设计**：
   - 不使用分段锁（TreeMap 的 range 操作难以实现）
   - 不使用引用计数（Java GC 自动管理内存）
   - 不使用异步 Seal（开销可忽略）
6. **性能瓶颈**：真正的瓶颈是 Journal 落盘（磁盘 I/O），而非锁竞争或 Seal 操作
