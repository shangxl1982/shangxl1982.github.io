# KVStore 主类设计

## 1. 概述

KVStore 是系统的主入口，协调各组件完成键值存储功能：
- **生命周期管理**：提供 start、init、shutdown 等生命周期接口
- **写入协调**：Journal → MemoryTableManager → B+Tree
- **读取协调**：MemoryTableManager → B+Tree
- **Dump 管理**：Sealed MemoryTable 到持久化存储
- **快速启动**：基于页面自包含地址设计，启动毫秒级

**设计原则**：KVStore 作为独立模块，不提供 REST API，仅通过 API 接口暴露功能。Service 层负责控制 KVStore 的生命周期，并通过 REST API 对外暴露服务。

```
┌─────────────────────────────────────────────────────┐
│                      KVStore                         │
│                                                      │
│  ┌──────────┐  ┌──────────────────┐  ┌──────────┐  │
│  │ Journal  │  │MemoryTableManager│  │ B+Tree   │  │
│  └──────────┘  │  ┌────────────┐  │  └──────────┘  │
│       │        │  │ActiveTable │  │        │        │
│       │        │  └────────────┘  │        │        │
│       │        │  ┌────────────┐  │        │        │
│       │        │  │SealedTables│  │        │        │
│       │        │  └────────────┘  │        │        │
│       │        └──────────────────┘        │        │
│       └──────────────┬─────────────────────┘        │
│                      │                              │
│                      ▼                              │
│              ┌──────────────┐                       │
│              │ ChunkManager │                       │
│              └──────────────┘                       │
└─────────────────────────────────────────────────────┘
```

## 2. 类设计

### 2.1 KVStore

| 属性 | 类型 | 描述 |
|------|------|------|
| journal | Journal | 操作日志 |
| memoryTableManager | MemoryTableManager | 内存表管理器 |
| bPlusTree | BPlusTree | B+树持久化存储 |
| chunkManager | ChunkManager | 存储管理器 |
| configManager | ConfigManager | 配置管理器 |
| metricsRegistry | MetricsRegistry | 监控注册器 |
| backupManager | BackupManager | 备份管理器 |
| state | KVStoreState | 当前状态 |
| sealThreshold | int | 单个 MemoryTable 的 Seal 阈值（字节） |
| maxSealedTables | int | 最大封存表数量 |

| 方法 | 返回类型 | 描述 |
|------|----------|------|
| start | void | 启动 KVStore |
| init | void | 初始化 KVStore（加载元数据） |
| shutdown | void | 关闭 KVStore |
| put | void | 插入键值对 |
| get | IndexValue | 获取值（处理 Tombstone） |
| delete | void | 删除键值对（写入 Tombstone） |
| batch | void | 批量操作（原子性） |
| rangeQuery | List<IndexValue> | 范围查询（过滤 Tombstone） |
| dump | void | 将 sealed tables 刷入 B+Tree |
| getConfigManager | ConfigManager | 获取配置管理器 |
| getMetricsRegistry | MetricsRegistry | 获取监控注册器 |
| getBackupManager | BackupManager | 获取备份管理器 |
| getState | KVStoreState | 获取当前状态 |

### 2.2 KVStoreState

KVStore 的生命周期状态枚举，控制哪些操作在什么阶段可以执行。状态转换是单向的（CREATED → RUNNING → STOPPED），异常情况下也可能从 INITIALIZING 直接进入 STOPPED。

```
enum KVStoreState {
    CREATED,      // 已创建，未初始化
    INITIALIZING, // 初始化中
    RUNNING,      // 运行中
    STOPPING,     // 停止中
    STOPPED       // 已停止
}
```

## 3. 核心操作流程

### 3.1 写入流程 (put)

写入操作遵循 WAL（Write-Ahead Logging）原则：**先写 Journal，再更新 MemoryTable**。这保证了即使在 MemoryTable 更新前崩溃，也能通过 Journal 回放恢复数据。写入完成后检查 MemoryTable 是否达到 Seal 阈值，达到则封存当前表并创建新表，确保写入不被阻塞。

```
put(key, value)
    │
    ▼
┌─────────────────────────────────────┐
│  1. 写入 Journal                    │
│     replayPoint = journal.write(    │
│         PUT, key, value)            │
└─────────────────────────────────────┘
    │
    ▼
┌─────────────────────────────────────┐
│  2. 写入 MemoryTableManager         │
│     memoryTableManager.put(         │
│         key, value, replayPoint)    │
└─────────────────────────────────────┘
    │
    ▼
┌─────────────────────────────────────┐
│  3. 检查是否需要 Seal               │
│     if (memoryTableManager          │
│         .shouldSeal())              │
│         memoryTableManager.seal()   │
└─────────────────────────────────────┘
```

### 3.2 删除流程 (delete)

删除操作不会立即从 B+Tree 中移除数据，而是在 MemoryTable 中写入一个 Tombstone 标记。Tombstone 与正常写入共享同一条 WAL 路径（先 Journal 后 MemoryTable），在后续 Dump 时才会从 B+Tree 中物理删除对应的 key。

```
delete(key)
    │
    ▼
┌─────────────────────────────────────┐
│  1. 写入 Journal                    │
│     replayPoint = journal.write(    │
│         DELETE, key)                │
└─────────────────────────────────────┘
    │
    ▼
┌─────────────────────────────────────┐
│  2. 写入 Tombstone 到 MemoryTable  │
│     memoryTableManager.delete(      │
│         key, replayPoint)           │
└─────────────────────────────────────┘
    │
    ▼
┌─────────────────────────────────────┐
│  3. 检查是否需要 Seal               │
│     if (memoryTableManager          │
│         .shouldSeal())              │
│         memoryTableManager.seal()   │
└─────────────────────────────────────┘
```

### 3.3 批量操作流程 (batch)

Batch 操作将多个 put/delete 打包为一个原子单元。原子性由 Journal 层保证：整个 batch 作为单个 BATCH 类型的 Write Item 写入，要么全部成功要么全部失败。写入 Journal 成功后，再逐条应用子操作到 MemoryTable。崩溃恢复时回放到 BATCH Write Item，展开所有子操作应用到 MemoryTable。

**Batch 操作特性**：
- 一个 batch 包含多个 put 和 delete 子操作
- 整个 batch 作为一个 BATCH 类型的 Write Item 写入 Journal
- 写入成功则所有子操作都成功，失败则全部失败
- BatchWriter 不拆分 batch，忠实记录为单个原子操作

```
batch(operations)
    │
    ▼
┌─────────────────────────────────────┐
│  1. 写入 Journal（原子性）          │
│     replayPoint = journal.writeBatch(│
│         operations)                 │
│     // 整个 batch = 一个 Write Item │
│     // 成功则全部成功，失败则全部回滚│
└─────────────────────────────────────┘
    │
    ▼
┌─────────────────────────────────────┐
│  2. 逐条写入 MemoryTableManager     │
│     for op in operations:           │
│         if (op.type == PUT):        │
│             memoryTableManager.put( │
│                 op.key, op.value,   │
│                 replayPoint)        │
│         else:                       │
│             memoryTableManager.delete(│
│                 op.key, replayPoint)│
└─────────────────────────────────────┘
    │
    ▼
┌─────────────────────────────────────┐
│  3. 检查是否需要 Seal               │
│     if (memoryTableManager          │
│         .shouldSeal())              │
│         memoryTableManager.seal()   │
└─────────────────────────────────────┘
```

**Batch 操作示例**：
```
operations = [
    {type: PUT, key: "user:1", value: "Alice"},
    {type: PUT, key: "user:2", value: "Bob"},
    {type: DELETE, key: "user:3"}
]

batch(operations)
// 要么全部成功，要么全部失败
```

### 3.4 读取流程 (get)

读取按照**新鲜度优先**的顺序查询：先查 Active MemoryTable（最新写入），再查 Sealed MemoryTables（按时间倒序），最后查 B+Tree（已持久化数据）。在 MemoryTable 中找到的条目可能是 Tombstone（表示已删除），此时直接返回 null。B+Tree 中不存储 Tombstone，查到即为有效数据。

```
get(key)
    │
    ▼
┌─────────────────────────────────────┐
│  1. 查询 MemoryTableManager         │
│     entry = memoryTableManager      │
│         .get(key)                   │
│     // 遍历 activeTable 和          │
│     // sealedTables（按时间倒序）   │
└─────────────────────────────────────┘
    │
    ├─── entry 存在
    │         │
    │         ├─── entry.isDeleted()
    │         │         │
    │         │         ▼
    │         │    return null  (已删除)
    │         │
    │         └─── !entry.isDeleted()
    │                   │
    │                   ▼
    │              return entry.value
    │
    └─── entry 不存在
              │
              ▼
    ┌─────────────────────────────────────┐
    │  2. 查询 B+Tree                     │
    │     entry = bPlusTree.search(key)   │
    │     // B+Tree 中不存储 Tombstone    │
    └─────────────────────────────────────┘
              │
              ├─── value == null
              │         │
              │         ▼
              │    return null  (从未存在)
              │
              └─── value 存在
                        │
                        ▼
                   return value
```

### 3.5 范围查询流程 (rangeQuery)

范围查询需要合并 MemoryTable 和 B+Tree 两个数据源的结果。合并策略是 **MemoryTable 优先**：对于同一个 key，MemoryTable 中的版本更新，覆盖 B+Tree 中的旧版本。MemoryTable 的结果可能包含 Tombstone，需要在最终结果中过滤掉（Tombstone 表示该 key 已删除，不应出现在范围查询结果中）。B+Tree 中不包含 Tombstone，查到的都是有效数据。

```
rangeQuery(startKey, endKey)
    │
    ▼
┌─────────────────────────────────────┐
│  1. 从 MemoryTableManager 获取范围  │
│     memEntries = memoryTableManager │
│         .rangeQuery(startKey, endKey)│
│     // 返回 List<Map.Entry<IndexKey, IndexValue>>│
│     // 合并所有 MemoryTable 的数据  │
└─────────────────────────────────────┘
    │
    ▼
┌─────────────────────────────────────┐
│  2. 从 B+Tree 获取范围数据          │
│     treeEntries = bPlusTree         │
│         .rangeQuery(startKey, endKey)│
│     // B+Tree 中只有 NORMAL 类型的值│
└─────────────────────────────────────┘
    │
    ▼
┌─────────────────────────────────────┐
│  3. 合并结果（MemoryTable 优先）    │
│     results = []                    │
│     seenKeys = Set()                │
│                                      │
│     for entry in memEntries:        │
│         seenKeys.add(entry.key)     │
│         if (!entry.value.isTombstone())│
│             results.add(entry.value)│
│                                      │
│     for entry in treeEntries:       │
│         if (!seenKeys.contains(key))│
│             // B+Tree 中无 Tombstone│
│             results.add(entry.value)│
└─────────────────────────────────────┘
    │
    ▼
┌─────────────────────────────────────┐
│  4. 返回结果                        │
│     return results                  │
└─────────────────────────────────────┘
```

### 3.6 Dump 流程

Dump 是将 Sealed MemoryTable 中的数据批量持久化到 B+Tree 的过程。Dump 完成后会更新 Tree 元数据（版本号、journalReplayPoint），然后清理已 Dump 的 MemoryTable 并可选地 truncate 对应的 Journal。Dump 过程中 Tombstone 会被转化为 B+Tree 的物理删除操作，B+Tree 中不会保留 Tombstone。Dump 失败不影响数据安全——Sealed Tables 保留在内存中，下次 Dump 会重试。

```
dump()
    │
    ▼
┌─────────────────────────────────────┐
│  1. 获取 Sealed Tables              │
│     sealedTables = memoryTableManager│
│         .getSealedTables()          │
└─────────────────────────────────────┘
    │
    ▼
┌─────────────────────────────────────┐
│  2. 调用 B+Tree Dump                │
│     bPlusTree.dump(sealedTables)    │
│     // 合并所有 sealed tables       │
│     // Tombstone → B+Tree.delete()  │
│     // NORMAL → B+Tree.insert()     │
│     // B+Tree Page 中无 Tombstone   │
└─────────────────────────────────────┘
    │
    ▼
┌─────────────────────────────────────┐
│  3. 清理已 Dump 的 MemoryTable      │
│     for table in sealedTables:      │
│         memoryTableManager          │
│             .removeSealedTable(table)│
└─────────────────────────────────────┘
    │
    ▼
┌─────────────────────────────────────┐
│  4. 清理 Journal（可选）            │
│     journal.truncate(flushedRegion) │
└─────────────────────────────────────┘
```

## 4. 数据流图

### 4.1 写入数据流

写入数据流展示了一次 put 操作从客户端到磁盘的完整路径：KVStore 接收请求后并行写入 Journal（持久化保障）和 MemoryTable（内存索引），当 MemoryTable 积累足够数据后触发 Dump 写入 B+Tree，最终数据落到 Chunk 文件中。

```
┌─────────┐     put(k,v)      ┌─────────┐
│  Client │ ─────────────────►│ KVStore │
└─────────┘                   └────┬────┘
                                   │
                    ┌──────────────┼──────────────┐
                    │              │              │
                    ▼              ▼              ▼
              ┌──────────┐  ┌───────────┐  ┌───────────┐
              │ Journal  │  │MemoryTable│  │ (pending) │
              │  write   │  │  Manager  │  │   dump    │
              │          │  │   put     │  │           │
              └──────────┘  └───────────┘  └───────────┘
                    │                              │
                    ▼                              ▼
              ┌──────────┐                  ┌───────────┐
              │  Chunk   │                  │  B+Tree   │
              │ Manager  │                  │   dump    │
              └──────────┘                  └───────────┘
                                                  │
                                                  ▼
                                            ┌───────────┐
                                            │  Chunk    │
                                            │ Manager   │
                                            └───────────┘
```

### 4.2 读取数据流

读取数据流展示了 get 操作的查找路径：先查 MemoryTableManager（Active + Sealed Tables），未命中再查 B+Tree。MemoryTable 中可能返回 Tombstone（已删除标记），B+Tree 中不存储 Tombstone。两层查找覆盖了内存中未持久化的数据和已 Dump 到磁盘的数据。

```
┌─────────┐     get(k)       ┌─────────┐
│  Client │ ────────────────►│ KVStore │
└─────────┘                  └────┬────┘
                                  │
                                  ▼
                           ┌───────────────────┐
                           │MemoryTableManager │
                           │   get (遍历所有表)│
                           └─────────┬─────────┘
                                     │
                    ┌────────────────┴────────────────┐
                    │                                 │
              找到 Entry                        未找到
                    │                                 │
                    ▼                                 ▼
              ┌───────────┐                   ┌───────────┐
              │ 检查      │                   │  B+Tree   │
              │ isDeleted │                   │  search   │
              └─────┬─────┘                   └─────┬─────┘
                    │                                 │
            ┌───────┴───────┐               ┌───────┴───────┐
            │               │               │               │
        deleted         normal         找到          未找到
            │               │               │               │
            ▼               ▼               ▼               ▼
        return null    return v       检查deleted    return null
                                           │
                                    ┌──────┴──────┐
                                    │             │
                                deleted       normal
                                    │             │
                                    ▼             ▼
                                return null   return v
```

## 5. 删除操作完整流程

### 5.1 删除场景表

| MemoryTableManager | B+Tree | get(key) 结果 | Dump 动作 |
|--------------------|--------|---------------|-----------|
| Entry(value) | - | value | 插入到 B+Tree |
| Tombstone | - | null | 从 B+Tree 删除 |
| - | Entry(value) | value | - |
| - | Tombstone | null | - |
| Tombstone | Entry(value) | null | 物理删除 |
| Entry(value) | Tombstone | value | 更新值 |

### 5.2 Tombstone 处理流程

Tombstone 的完整生命周期：delete 操作在 MemoryTable 中创建 Tombstone 条目，get 时检测到 Tombstone 返回 null，Dump 时遍历 MemoryTable 将 Tombstone 转化为 B+Tree 的物理删除操作。Tombstone 不会写入 B+Tree Page，只存在于 MemoryTable 中。

```
                    写入 Tombstone
                         │
                         ▼
┌─────────────────────────────────────────────────────┐
│              MemoryTableManager                      │
│  ┌─────────────────────────────────────────────┐    │
│  │  key → Entry(deleted=true, value=null)      │    │
│  └─────────────────────────────────────────────┘    │
└─────────────────────────────────────────────────────┘
                         │
                         │ dump()
                         ▼
┌─────────────────────────────────────────────────────┐
│                    B+Tree                            │
│                                                      │
│  Tombstone → 物理删除 key                           │
│  正常值 → 插入/更新 key                             │
│                                                      │
└─────────────────────────────────────────────────────┘
```

## 6. 生命周期管理

### 6.1 生命周期状态转换

KVStore 的状态转换图。正常路径为 CREATED → INITIALIZING → RUNNING → STOPPING → STOPPED。初始化失败或 CREATED 状态下的异常都会直接进入 STOPPED。状态转换通过原子操作保证线程安全。

```
┌─────────────────────────────────────────────────────────────┐
│                    KVStore 生命周期                          │
├─────────────────────────────────────────────────────────────┤
│                                                              │
│  CREATED ──────► INITIALIZING ──────► RUNNING              │
│     │                  │                    │               │
│     │                  │                    │               │
│     │                  ▼                    │               │
│     │            (init失败)                 │               │
│     │                  │                    │               │
│     │                  ▼                    ▼               │
│     └──────────────► STOPPED ◄───────── STOPPING           │
│                                                              │
│  状态说明：                                                  │
│  - CREATED: 已创建对象，未初始化                             │
│  - INITIALIZING: 正在初始化，加载元数据                      │
│  - RUNNING: 正常运行，可接受请求                             │
│  - STOPPING: 正在停止，处理收尾工作                          │
│  - STOPPED: 已停止，不可接受请求                             │
│                                                              │
└─────────────────────────────────────────────────────────────┘
```

### 6.2 start() 启动流程

start() 是 KVStore 的入口方法，负责创建目录、初始化 ConfigManager 和 MetricsRegistry，然后调用 init() 加载数据。start() 只能在 CREATED 状态下调用，确保不会重复启动。

```
start()
    │
    ▼
┌─────────────────────────────────────┐
│  1. 检查当前状态                    │
│     if (state != CREATED):          │
│         throw IllegalStateException(│
│             "KVStore already started")│
└─────────────────────────────────────┘
    │
    ▼
┌─────────────────────────────────────┐
│  2. 创建必要目录                    │
│     createDirectories(              │
│         config.storagePath)         │
│     createDirectories(              │
│         config.monitoring.logPath)  │
└─────────────────────────────────────┘
    │
    ▼
┌─────────────────────────────────────┐
│  3. 初始化 ConfigManager            │
│     configManager = new ConfigManager(│
│         configPath)                 │
│     configManager.load()            │
└─────────────────────────────────────┘
    │
    ▼
┌─────────────────────────────────────┐
│  4. 初始化 MetricsRegistry          │
│     metricsRegistry = new MetricsRegistry(│
│         configManager)              │
│     metricsRegistry.start()         │
└─────────────────────────────────────┘
    │
    ▼
┌─────────────────────────────────────┐
│  5. 调用 init()                     │
│     init()                          │
└─────────────────────────────────────┘
```

### 6.3 init() 初始化流程（快速启动）

init() 是核心初始化方法，按顺序创建 ChunkManager、加载 B+Tree 元数据、初始化 Journal 和 MemoryTableManager，然后回放 Journal 恢复未持久化的数据。得益于页面自包含地址设计，B+Tree 初始化只需加载根页位置，不需要加载整棵树，实现毫秒级启动。Journal 回放从 tree-metadata.pb 中记录的 journalReplayPoint 开始，只回放 Dump 后的增量数据。

```
init()
    │
    ▼
┌─────────────────────────────────────┐
│  1. 更新状态                        │
│     state = INITIALIZING            │
└─────────────────────────────────────┘
    │
    ▼
┌─────────────────────────────────────┐
│  2. 创建 ChunkManager               │
│     chunkManager = new ChunkManager(│
│         config.storagePath)         │
│     (无需加载所有 Chunk，按需打开)  │
└─────────────────────────────────────┘
    │
    ▼
┌─────────────────────────────────────┐
│  3. 加载 Tree 元数据                │
│     treeMetadata = loadMetadata(    │
│         "tree-metadata.pb")       │
│     if (treeMetadata == null):      │
│         // 首次启动，创建默认元数据 │
│         treeMetadata = createDefaultMetadata()│
└─────────────────────────────────────┘
    │
    ▼
┌─────────────────────────────────────┐
│  4. 初始化 B+Tree                   │
│     bPlusTree = new BPlusTree(      │
│         chunkManager,               │
│         treeMetadata.rootLocation,  │
│         config)                     │
└─────────────────────────────────────┘
    │
    ▼
┌─────────────────────────────────────┐
│  5. 初始化 Journal                  │
│     journal = new Journal(          │
│         chunkManager,               │
│         config)                     │
│     // 加载 Journal Region 列表     │
│     journal.loadRegions()           │
└─────────────────────────────────────┘
    │
    ▼
┌─────────────────────────────────────┐
│  6. 初始化 MemoryTableManager       │
│     memoryTableManager = new        │
│         MemoryTableManager(config)  │
└─────────────────────────────────────┘
    │
    ▼
┌─────────────────────────────────────┐
│  7. 初始化 BackupManager            │
│     backupManager = new BackupManager(│
│         this, config)               │
└─────────────────────────────────────┘
    │
    ▼
┌─────────────────────────────────────┐
│  8. Replay Journal（如果需要）      │
│     replayPoint = treeMetadata      │
│         .journalReplayPoint         │
│     if (replayPoint != null):       │
│         journal.replay(             │
│             replayPoint,            │
│             memoryTableManager)     │
└─────────────────────────────────────┘
    │
    ▼
┌─────────────────────────────────────┐
│  9. 启动后台任务                    │
│     startDumpScheduler()            │
│     startGCScheduler()              │
│     startChunkSealChecker()         │
└─────────────────────────────────────┘
    │
    ▼
┌─────────────────────────────────────┐
│  10. 更新状态                       │
│     state = RUNNING                 │
└─────────────────────────────────────┘
```

### 6.4 shutdown() 关闭流程

shutdown() 执行优雅关闭：先停止接受新请求，等待进行中的请求完成，然后将所有内存数据（包括 Active MemoryTable）Seal 并 Dump 到 B+Tree，最后依次关闭后台任务、监控、Journal、B+Tree 和 ChunkManager。关闭时执行 Dump 确保不丢失未持久化的数据。

```
shutdown()
    │
    ▼
┌─────────────────────────────────────┐
│  1. 检查当前状态                    │
│     if (state != RUNNING):          │
│         throw IllegalStateException(│
│             "KVStore not running")  │
└─────────────────────────────────────┘
    │
    ▼
┌─────────────────────────────────────┐
│  2. 更新状态                        │
│     state = STOPPING                │
└─────────────────────────────────────┘
    │
    ▼
┌─────────────────────────────────────┐
│  3. 停止接受新请求                  │
│     // 设置标志位，拒绝新请求       │
│     stopAcceptingRequests()         │
└─────────────────────────────────────┘
    │
    ▼
┌─────────────────────────────────────┐
│  4. 等待进行中的请求完成            │
│     waitForPendingRequests()        │
│     // 设置超时时间                 │
└─────────────────────────────────────┘
    │
    ▼
┌─────────────────────────────────────┐
│  5. Dump Sealed MemoryTables        │
│     sealedTables = memoryTableManager│
│         .getSealedTables()          │
│     if (!sealedTables.isEmpty())    │
│         dump()                      │
│     (dump 会触发 Tree Dump，        │
│      更新 journalReplayPoint)       │
└─────────────────────────────────────┘
    │
    ▼
┌─────────────────────────────────────┐
│  6. Seal 并 Dump Active Table       │
│     if (!activeTable.isEmpty())     │
│         memoryTableManager.seal()   │
│         dump()                      │
└─────────────────────────────────────┘
    │
    ▼
┌─────────────────────────────────────┐
│  7. 停止后台任务                    │
│     stopDumpScheduler()             │
│     stopGCScheduler()               │
│     stopChunkSealChecker()          │
└─────────────────────────────────────┘
    │
    ▼
┌─────────────────────────────────────┐
│  8. 关闭监控                        │
│     metricsRegistry.stop()          │
└─────────────────────────────────────┘
    │
    ▼
┌─────────────────────────────────────┐
│  9. 关闭 Journal                    │
│     journal.close()                 │
└─────────────────────────────────────┘
    │
    ▼
┌─────────────────────────────────────┐
│  10. 关闭 B+Tree                    │
│     bPlusTree.close()               │
│     (metadata 已在 Tree Dump 时保存)│
└─────────────────────────────────────┘
    │
    ▼
┌─────────────────────────────────────┐
│  11. 关闭 ChunkManager              │
│     chunkManager.close()            │
└─────────────────────────────────────┘
    │
    ▼
┌─────────────────────────────────────┐
│  12. 更新状态                       │
│     state = STOPPED                 │
└─────────────────────────────────────┘
```

### 6.5 崩溃恢复机制

崩溃恢复利用 WAL + 多版本 Tree 的机制：启动时加载最新的 B+Tree 版本（通过 tree-metadata.pb 中的 rootLocation），然后从该版本记录的 journalReplayPoint 开始回放 Journal，恢复崩溃前未 Dump 的数据到 MemoryTable 中。整个恢复过程不需要扫描全部数据，只回放增量 Journal。

```
┌─────────────────────────────────────────────────────┐
│              崩溃恢复流程                            │
├─────────────────────────────────────────────────────┤
│                                                      │
│  正常运行时：                                        │
│  ┌─────────────────────────────────────────────┐    │
│  │ Journal: Region 1, Region 2, Region 3...    │    │
│  │           ↓                                 │    │
│  │ MemoryTableManager: 数据累积                │    │
│  │           ↓                                 │    │
│  │ Tree Dump: 写入 B+Tree                      │    │
│  │           ↓                                 │    │
│  │ journalReplayPoint = {regionMajor: 3, ...}  │    │
│  └─────────────────────────────────────────────┘    │
│                                                      │
│  崩溃后恢复：                                        │
│  ┌─────────────────────────────────────────────┐    │
│  │ 1. 加载 B+Tree (version 3)                  │    │
│  │    rootLocation = ...                       │    │
│  │    journalReplayPoint = {                   │    │
│  │      regionMajor: 3, offset: 5000           │    │
│  │    }                                        │    │
│  │                                             │    │
│  │ 2. 回放 Journal (Region >= 3)               │    │
│  │    根据 regionMajor 直接定位 Chunk          │    │
│  │    从 offset 开始读取...                    │    │
│  │           ↓                                 │    │
│  │ MemoryTableManager: 恢复未 Dump 的数据      │    │
│  │                                             │    │
│  │ 3. 系统恢复完成                             │    │
│  └─────────────────────────────────────────────┘    │
│                                                      │
└─────────────────────────────────────────────────────┘
```

### 6.6 启动时间对比

| 数据规模 | 传统方案（加载映射表） | 新方案（自包含地址） |
|----------|------------------------|----------------------|
| 100 万页面 | ~1 秒 | < 10 ms |
| 1 亿页面 | ~2 分钟 | < 10 ms |
| 10 亿页面 | ~20 分钟 | < 10 ms |

**关键优化**：
- 元数据文件仅存储 `rootLocation` 和 `nextPageId`
- 页面地址嵌入在索引页条目中，无需映射表
- 页面按需加载，启动时不预加载

## 7. 元数据文件

### 7.1 元数据文件概览

| 文件 | 所属模块 | 描述 |
|------|----------|------|
| tree-metadata.pb | B+Tree | 多版本 Tree 元数据，含 journalReplayPoint |
| journal-*.log | Journal | 操作日志文件 |

详见 [B+Tree 设计](design-bplustree.md) 和 [Journal 设计](design-journal.md)。

## 8. 配置参数

| 配置项 | 默认值 | 描述 |
|--------|--------|------|
| storagePath | ./data | 存储目录 |
| sealThreshold | 64MB | 单个 MemoryTable 的 Seal 阈值 |
| maxSealedTables | 3 | 最大封存表数量，超过触发 Dump |
| leafPageMaxSize | 8KB | LeafPage 最大容量 |
| indexPageMaxSize | 64KB | IndexPage 最大容量 |
| maxCacheSize | 1000 | 页面缓存大小 |
| chunkSize | 64MB | 单个 Chunk 最大大小 |

## 9. 与其他模块的交互

```
                    ┌──────────────────────────────────┐
                    │             KVStore              │
                    └──────────────────────────────────┘
                                     │
          ┌──────────────────────────┼──────────────────────────┐
          │                          │                          │
          ▼                          ▼                          ▼
   ┌────────────┐            ┌──────────────────┐      ┌────────────┐
   │  Journal   │            │MemoryTableManager│      │  B+Tree    │
   │            │            │                  │      │            │
   │ - write()  │            │ - put()          │      │ - dump()   │
   │ - replay() │            │ - get()          │      │ - search() │
   │ - truncate │            │ - delete()       │      │ - delete() │
   │            │            │ - seal()         │      │            │
   └────────────┘            └──────────────────┘      └────────────┘
          │                                                    │
          │                    ┌───────────────┐               │
          └───────────────────►│ ChunkManager  │◄──────────────┘
                               └───────────────┘
                                       │
                                       ▼
                               ┌───────────────┐
                               │    Chunks     │
                               │  (磁盘文件)   │
                               │  UUID 命名    │
                               └───────────────┘
```

## 10. 并发控制设计

> 本节描述 KVStore 内部的并发控制实现。上层的请求队列和批量合并机制详见 [并发控制设计](design-concurrency.md)。

### 10.1 并发控制架构

KVStore 内部采用**读写分离 + Copy-on-Write**的并发控制策略。写操作由 BatchWriter 单线程执行，通过读写锁保护 Active MemoryTable：

```
┌─────────────────────────────────────────────────────────────┐
│                         KVStore                              │
├─────────────────────────────────────────────────────────────┤
│                                                              │
│  写路径                        读路径                        │
│  ┌──────────────┐             ┌──────────────┐             │
│  │   Journal    │             │              │             │
│  │   Write      │             │   Read       │             │
│  └──────┬───────┘             │   (快照读)   │             │
│         │                     │              │             │
│         ▼                     └──────┬───────┘             │
│  ┌──────────────┐                    │                      │
│  │    Active    │◄─────Lock─────────┤                      │
│  │ MemoryTable  │                    │                      │
│  └──────────────┘                    │                      │
│         │                            │                      │
│         │ Seal                       │                      │
│         ▼                            ▼                      │
│  ┌──────────────┐            ┌──────────────┐             │
│  │    New       │            │   Sealed     │             │
│  │    Active    │            │   Tables     │ (只读)      │
│  └──────────────┘            └──────────────┘             │
│                                      │                      │
│                                      ▼                      │
│                              ┌──────────────┐              │
│                              │   B+Tree     │              │
│                              │ (快照Root)   │              │
│                              └──────────────┘              │
└─────────────────────────────────────────────────────────────┘
```

### 10.2 核心设计原则

#### **原则1：读写路径分离**

```
写路径：
  Journal.write() → Active MemoryTable.put() → 完成

读路径：
  Active MemoryTable.get() → Sealed Tables.get() → B+Tree.search()

关键点：
- 写路径和读路径在 Active MemoryTable 处交汇
- 使用读写锁保证并发安全
- Sealed Tables 和 B+Tree 只读，无需加锁
```

#### **原则2：B+Tree Root 版本分离**

```
读路径 Root (readRoot):
  - 已持久化的最新只读版本
  - 使用 AtomicReference 持有
  - 读操作获取快照后无锁访问

Dump 路径 Root (dumpRoot):
  - Dump 过程中构建的临时版本
  - 随 Dump 进行不断更新
  - 完成后原子替换 readRoot

版本切换：
  readRoot.set(newTree)  // 原子操作
```

#### **原则3：快照读保证一致性**

```
读操作流程：
  1. 获取 B+Tree Root 快照（原子操作）
  2. 整个读操作使用同一个快照
  3. 保证看到一致的状态

优势：
  - 读操作无锁
  - 延迟可预测
  - 不会看到中间状态
```

### 10.3 MemoryTable 并发控制

#### **锁策略**

```
Active MemoryTable:
  - 写操作：写锁（写写互斥）
  - 读操作：读锁（读读并发）
  - 读写互斥：保证数据一致性

Sealed MemoryTables:
  - 只读状态，无需加锁
  - 多个读操作完全并发
  - 性能最优
```

#### **Seal 操作并发处理**

```
Seal 操作时序图：

写线程                Active MT           Sealed List
  │                      │                    │
  │   writeLock()        │                    │
  ├─────────────────────►│                    │
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
  │                      │                    │
  │   new MemoryTable()  │                    │
  ├─────────────────────►│                    │
  │                      │                    │
  │   unlock()           │                    │
  └─────────────────────►│                    │

关键点：
- Seal 操作在写锁保护下进行
- 操作极快（纳秒级），相对 Journal 落盘（毫秒级）可忽略
- 无需异步或双缓冲等复杂设计
```

### 10.4 B+Tree Root 版本管理

#### **双 Root 机制**

```
┌─────────────────────────────────────────────────────────────┐
│                      B+Tree Root 管理                        │
├─────────────────────────────────────────────────────────────┤
│                                                              │
│  读路径 Root (AtomicReference)    Dump 路径 Root            │
│  ┌──────────────────┐             ┌──────────────────┐     │
│  │  Version 3       │             │  Version 4       │     │
│  │  (persisted)     │◄────────────│  (building)      │     │
│  │  只读            │  原子替换   │  临时            │     │
│  └──────────────────┘             └──────────────────┘     │
│         ▲                                  │                 │
│         │                                  │                 │
│    读操作使用                          Dump操作使用          │
│                                                              │
└─────────────────────────────────────────────────────────────┘
```

#### **Dump 操作时序图**

```
Dump线程          ReadRoot          DumpRoot          Journal
  │                  │                 │                 │
  │  get() v3        │                 │                 │
  ├─────────────────►│                 │                 │
  │                  │                 │                 │
  │  merge(sealed)   │                 │                 │
  ├──────────────────────────────────►│                 │
  │                  │                 │                 │
  │  persist()       │                 │                 │
  ├──────────────────────────────────►│                 │
  │                  │                 │                 │
  │  set(newTree v4) │                 │                 │
  ├─────────────────►│                 │                 │
  │  (原子操作)      │                 │                 │
  │                  │                 │                 │
  │  truncate()      │                 │                 │
  ├─────────────────────────────────────────────────────►│
  │                  │                 │                 │

关键点：
- 读操作和 Dump 操作完全并行
- Root 替换是原子操作
- 读操作要么看到 v3，要么看到 v4，不会看到中间状态
```

### 10.5 并发场景分析

#### **场景1：并发写入**

```
写线程1            写线程2            Active MT
  │                  │                   │
  │  writeLock()     │                   │
  ├─────────────────────────────────────►│
  │  (获得锁)        │                   │
  │                  │  writeLock()      │
  │                  ├──────────────────►│
  │                  │  (阻塞)           │
  │  put(k1, v1)     │                   │
  ├─────────────────────────────────────►│
  │  unlock()        │                   │
  ├─────────────────────────────────────►│
  │                  │  (获得锁)         │
  │                  │  put(k2, v2)      │
  │                  ├──────────────────►│
  │                  │  unlock()         │
  │                  ├──────────────────►│

结果：写操作串行化，正确
```

#### **场景2：并发读写**

```
写线程            读线程            Active MT
  │                 │                   │
  │  writeLock()    │                   │
  ├────────────────────────────────────►│
  │  (获得锁)       │                   │
  │                 │  readLock()       │
  │                 ├──────────────────►│
  │                 │  (阻塞，读写互斥) │
  │  put(k1, v1)    │                   │
  ├────────────────────────────────────►│
  │  unlock()       │                   │
  ├────────────────────────────────────►│
  │                 │  (获得锁)         │
  │                 │  get(k1)          │
  │                 ├──────────────────►│
  │                 │  unlock()         │
  │                 ├──────────────────►│

结果：读写互斥，读操作看到最新数据，正确
```

#### **场景3：Dump 期间的读写**

```
Dump线程          读线程            ReadRoot
  │                 │                   │
  │  get() v3       │                   │
  ├────────────────────────────────────►│
  │                 │  get() v3         │
  │                 ├──────────────────►│
  │                 │  (使用 v3 快照)   │
  │  merge()        │                   │
  │  ...            │  ...              │
  │  set(v4)        │                   │
  ├────────────────────────────────────►│
  │  (原子替换)     │                   │
  │                 │  get() v4         │
  │                 ├──────────────────►│
  │                 │  (使用 v4 快照)   │

结果：读操作不受 Dump 影响，正确
```

### 10.6 性能特性

#### **并发度分析**

```
操作类型              并发度           锁竞争
─────────────────────────────────────────────
写操作                串行化           高（写锁）
读操作  高           中（读锁）
读操作       完全并发         无
读操作          完全并发         无
Dump 操作             完全并发         无（独立 Root）
```

#### **关键路径性能**

```
写操作延迟：
  Journal 落盘: 1-10ms (主要开销)
  写锁获取: 纳秒级 (可忽略)
  MemoryTable 更新: 纳秒级 (可忽略)

读操作延迟：
  读锁获取: 纳秒级 (可忽略)
  MemoryTable 查询: 微秒级
  B+Tree 查询: 微秒级 (缓存命中)

Seal 操作：
  Flag 设置 + 对象分配: 纳秒级
  相对 Journal 落盘: 可忽略不计
```

### 10.7 Dump 并发边界

#### **Dump 线程控制**

```
Dump 执行模式：
  - 单一线程全程控制
  - 保证不会发生并发 Dump
  - 使用 AtomicBoolean 标志位控制

控制流程：
  dumpInProgress: AtomicBoolean = false
  
  dump():
    if (!dumpInProgress.compareAndSet(false, true)):
        return  // 已有 Dump 在进行
    
    try:
        // 执行 Dump 流程
        ...
    finally:
        dumpInProgress.set(false)
```

#### **边界1：Dump 期间新的 Seal**

```
时序图：

Dump线程          写线程            MemoryTableMgr
  │                 │                     │
  │  readLock()     │                     │
  ├───────────────────────────────────────►│
  │  (获取读锁)     │                     │
  │                 │                     │
  │  getSealed()    │                     │
  ├───────────────────────────────────────►│
  │  ◄── [MT1, MT2] ┤                     │
  │  (获取快照)     │                     │
  │                 │                     │
  │  unlock()       │                     │
  ├───────────────────────────────────────►│
  │  (释放读锁)     │                     │
  │                 │  writeLock()        │
  │                 ├────────────────────►│
  │                 │  (获得写锁)         │
  │                 │  put(k1, v1)        │
  │                 │  shouldSeal()?      │
  │                 ├────────────────────►│
  │                 │  seal()             │
  │                 ├────────────────────►│
  │                 │  (创建 MT3)         │
  │                 │  unlock()           │
  │                 ├────────────────────►│
  │  merge(MT1,MT2) │                     │
  │  ...            │                     │

关键点：
  - Dump 获取 Sealed Tables 快照后立即释放读锁
  - 新的 Seal 操作可以继续
  - 新 Seal 的 MT3 不会被当前 Dump 处理
  - 下次 Dump 会处理 MT3
```

#### **边界2：Dump 失败处理**

```
Dump 失败场景：
  - B+Tree 写入失败
  - 磁盘空间不足
  - 其他异常

处理策略：
  try:
      // Dump 流程
      newRoot = merge(sealedTables)
      persist(newRoot)
      atomicSet(readRoot, newRoot)
      removeSealedTables(sealedTables)
  except:
      // 失败处理
      // 1. 不更新 readRoot
      // 2. Sealed Tables 不被清理
      // 3. 下次 Dump 会重试
      log.error("Dump failed", exception)
      return

保证：
  - 失败不影响读操作（继续使用旧 Root）
  - 失败不影响写操作（继续写入 Active）
  - 数据不会丢失（Sealed Tables 保留）
```

#### **边界3：Dump 触发时机**

```
触发条件：
  1. Sealed Tables 数量达到阈值
     sealedTables.size() >= maxSealedTables
  
  2. Sealed Tables 总大小达到阈值
     sealedTables.totalSize() >= maxSealedMemory
  
  3. 手动触发
     kvStore.triggerDump()

触发位置：
  - put() 操作后检查
  - delete() 操作后检查
  - 不在 get() 操作中检查（避免影响读性能）

避免频繁 Dump：
  - 使用时间窗口限制
  - 最小间隔：minDumpInterval (默认 1 秒)
  - 避免写入量大的场景频繁 Dump
```

### 10.8 设计优势

#### **优势1：读写分离，性能优异**

```
读操作：
  - Active MemoryTable: 读锁（读读并发）
  - Sealed Tables: 无锁（只读）
  - B+Tree: 无锁（快照读）

写操作：
  - Journal: 无锁（内部原子性）
  - Active MemoryTable: 写锁（写写互斥）

并发性能：
  - 读读并发：高
  - 读写并发：中等
  - 写写并发：低（串行化）
```

#### **优势2：Dump 不阻塞读写**

```
传统方案：
  - Dump 需要加全局锁
  - 读写操作被阻塞

本方案：
  - Dump 使用独立的 dumpRoot
  - 读操作使用 readRoot
  - 完全并行，无阻塞
```

#### **优势3：原子版本切换**

```
使用 AtomicReference：
  - 版本切换是原子操作
  - 读操作看到一致的状态
  - 无需复杂的锁机制
```

#### **优势4：简洁可靠**

```
设计特点：
  - 使用标准读写锁
  - Seal 同步执行（纳秒级）
  - 简单 List 快照
  - 快照读保证一致性

避免过度设计：
  - 无分段锁（TreeMap 不支持）
  - 无引用计数（GC 足够）
  - 无异步 Seal（开销可忽略）
```

## 11. 注意事项

1. **写入顺序**：必须先写 Journal，再更新 MemoryTable，保证数据持久性
2. **Tombstone 处理**：读取时检查 deleted 标志，返回 null 表示已删除
3. **并发控制**：
   - Active MemoryTable 使用读写锁，保证并发安全
   - Sealed MemoryTables 只读，无需加锁
   - B+Tree 使用快照读，读操作无锁
4. **Dump 非阻塞**：Dump 使用独立的 Root 版本，不阻塞读写操作
5. **快速启动**：得益于页面自包含地址，启动只需加载根页位置，毫秒级启动
6. **崩溃恢复**：从 journalReplayPoint 开始回放 Journal，恢复未 Dump 的数据
7. **元数据安全**：Tree Dump 时原子更新 tree-metadata.pb，保证一致性
8. **Seal 操作**：Seal 操作极快（纳秒级），相对 Journal 落盘可忽略，无需异步化
9. **Root 版本切换**：使用 AtomicReference 原子替换，读操作看到一致的状态
10. **性能瓶颈**：真正的瓶颈是 Journal 落盘（磁盘 I/O），而非锁竞争

## 12. API 接口设计

KVStore 通过 API 接口暴露功能，供 Service 层调用。

### 12.1 数据操作 API

KVStore 对外暴露的核心接口定义，包含生命周期管理（start/init/shutdown）、数据操作（put/get/delete/batch/rangeQuery）和子模块获取方法。

```
interface KVStore {
    
    生命周期管理
    start(): void
    init(): void
    shutdown(): void
    getState(): KVStoreState
    
    数据操作
    put(key: byte[], value: byte[]): void
    get(key: byte[]): IndexValue
    delete(key: byte[]): void
    batch(operations: List<Operation>): void
    rangeQuery(startKey: byte[], endKey: byte[]): List<IndexValue>
    
    管理操作
    dump(): void
    
    获取子模块
    getConfigManager(): ConfigManager
    getMetricsRegistry(): MetricsRegistry
    getBackupManager(): BackupManager
}
```

### 12.2 子模块 API

KVStore 子模块的接口定义：ConfigManager（配置管理）、MetricsRegistry（监控数据）、BackupManager（备份恢复）。Service 层通过这些接口访问 KVStore 的管理功能。

```
// 配置管理
interface ConfigManager {
    getAllConfig(): Config
    get(key: String): Object
    addListener(listener: ConfigListener): void
    removeListener(listener: ConfigListener): void
    reload(): List<ConfigChange>
}

// 监控
interface MetricsRegistry {
    getSnapshot(): MetricsSnapshot
    getHistory(duration: long): List<MetricsSnapshot>
    checkHealth(): Map<String, HealthCheckResult>
    addListener(listener: MetricsListener): void
    removeListener(listener: MetricsListener): void
}

// 备份管理
interface BackupManager {
    fullBackup(targetPath: String): BackupResult
    incrementalBackup(targetPath: String, parentBackupId: String): BackupResult
    validateBackup(backupPath: String): boolean
    getBackupInfo(backupPath: String): BackupMetadata
    
    rollbackRecovery(targetVersion: long): RecoveryResult
    fullRecovery(backupChain: List<String>): RecoveryResult
    checkVersionHealth(version: long): HealthStatus
    getRollbackVersions(): List<Long>
}
```

### 12.3 Service 层使用示例

Service 层创建和使用 KVStore 实例的示例代码，展示 start/shutdown 生命周期管理和 REST 接口实现。

```
class KVStoreService {
    
    private KVStore kvStore;
    
    // 初始化服务
    public void start() {
        kvStore = new KVStore(configPath);
        kvStore.start();
    }
    
    // 关闭服务
    public void stop() {
        kvStore.shutdown();
    }
    
    // REST API: PUT /kv/{key}
    public void put(String key, String value) {
        kvStore.put(key.getBytes(), value.getBytes());
    }
    
    // REST API: GET /kv/{key}
    public String get(String key) {
        IndexValue value = kvStore.get(key.getBytes());
        return value != null ? new String(value.getData()) : null;
    }
    
    // REST API: DELETE /kv/{key}
    public void delete(String key) {
        kvStore.delete(key.getBytes());
    }
    
    // REST API: GET /metrics
    public String getMetrics() {
        MetricsSnapshot snapshot = kvStore.getMetricsRegistry().getSnapshot();
        return snapshot.toJson();
    }
    
    // REST API: GET /health
    public String getHealth() {
        Map<String, HealthCheckResult> results = 
            kvStore.getMetricsRegistry().checkHealth();
        return JSON.stringify(results);
    }
    
    // REST API: GET /config
    public String getConfig() {
        Config config = kvStore.getConfigManager().getAllConfig();
        return config.toJson();
    }
    
    // REST API: POST /backup/full
    public String fullBackup(String targetPath) {
        BackupResult result = kvStore.getBackupManager().fullBackup(targetPath);
        return JSON.stringify(result);
    }
    
    // REST API: POST /recovery/rollback
    public String rollbackRecovery(long targetVersion) {
        RecoveryResult result = 
            kvStore.getBackupManager().rollbackRecovery(targetVersion);
        return JSON.stringify(result);
    }
}
```

## 13. 相关文档

- [Config 设计](design-config.md)：配置管理框架
- [Monitoring 设计](design-monitoring.md)：监控体系设计
- [Backup 设计](design-backup.md)：备份与恢复设计
- [Journal 设计](design-journal.md)：操作日志设计
- [B+Tree 设计](design-bplustree.md)：B+树持久化存储
- [GC 设计](design-gc.md)：垃圾回收机制
