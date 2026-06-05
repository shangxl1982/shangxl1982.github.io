# Config 框架设计

## 1. 概述

本文档定义 KVStore 的配置管理框架，包括：
- **配置参数管理**：统一的配置参数定义和存储
- **动态调整支持**：不停机变更配置
- **配置监听机制**：定时读取 + Listener 模式
- **配置生效策略**：根据情况动态激活配置
- **API 接口**：通过 API 暴露配置管理能力，供 Service 层调用

**设计原则**：KVStore 作为独立模块，不提供 REST API，仅通过 API 接口暴露配置管理能力。Service 层负责将配置管理通过 REST API 或其他方式对外暴露。

```
┌─────────────────────────────────────────────────────────────┐
│                    Config 框架架构                           │
├─────────────────────────────────────────────────────────────┤
│                                                              │
│  配置来源                                                    │
│  ┌─────────────┐  ┌─────────────┐                          │
│  │ 配置文件    │  │ 环境变量    │                          │
│  │ (JSON)      │  │             │                          │
│  └──────┬──────┘  └──────┬──────┘                          │
│         │                │                                  │
│         └────────────────┘                                  │
│                          │                                  │
│                          ▼                                  │
│                 ┌─────────────────┐                         │
│                 │  ConfigManager  │                         │
│                 │                 │                         │
│                 │ - 加载配置      │                         │
│                 │ - 定时刷新      │                         │
│                 │ - 通知监听器    │                         │
│                 │ - 暴露 API      │                         │
│                 └────────┬────────┘                         │
│                          │                                  │
│                          ▼                                  │
│                 ┌─────────────────┐                         │
│                 │  Service 层     │                         │
│                 │  (REST API)     │                         │
│                 └────────┬────────┘                         │
│                          │                                  │
│         ┌────────────────┼────────────────┐                 │
│         │                │                │                 │
│         ▼                ▼                ▼                 │
│  ┌─────────────┐  ┌─────────────┐  ┌─────────────┐         │
│  │ KVStore     │  │ MemoryTable │  │ GC          │         │
│  │ Listener    │  │ Listener    │  │ Listener    │         │
│  └─────────────┘  └─────────────┘  └─────────────┘         │
│                                                              │
└─────────────────────────────────────────────────────────────┘
```

## 2. 配置参数定义

### 2.1 配置分类

| 分类 | 描述 | 示例参数 |
|------|------|----------|
| **KVStore** | KVStore 级别配置 | storagePath, maxVersions |
| **MemoryTable** | 内存表配置 | sealThreshold, maxSealedTables |
| **BPlusTree** | B+树配置 | leafPageMaxSize, indexPageMaxSize, maxCacheSize |
| **Journal** | 日志配置 | maxChunkSize, maxRetry |
| **Chunk** | 存储块配置 | chunkSize, preallocate |
| **GC** | 垃圾回收配置 | gcScheduleInterval, partialGCRatio |
| **Concurrency** | 并发控制配置 | batchSize, timeWindow, maxBatchSize |
| **ErrorHandling** | 错误处理配置 | maxRetry, retryInterval |
| **DataIntegrity** | 数据完整性配置 | pageSize, verifyOnRead |
| **Monitoring** | 监控配置 | snapshotInterval, restPort |
| **Backup** | 备份配置 | lockTimeout, batchSize |
| **Recovery** | 恢复配置 | maxJournalReplaySize |

### 2.2 配置参数列表

#### KVStore 配置

| 参数 | 类型 | 默认值 | 动态 | 描述 |
|------|------|--------|------|------|
| storagePath | String | ./data | ❌ | 存储目录 |
| maxVersions | int | 10 | ✅ | 保留的最大版本数 |

#### MemoryTable 配置

| 参数 | 类型 | 默认值 | 动态 | 描述 |
|------|------|--------|------|------|
| sealThreshold | long | 67108864 | ✅ | Seal 阈值（64MB） |
| maxSealedTables | int | 3 | ✅ | 最大封存表数量 |

#### BPlusTree 配置

| 参数 | 类型 | 默认值 | 动态 | 描述 |
|------|------|--------|------|------|
| leafPageMaxSize | int | 8192 | ❌ | LeafPage 最大容量（字节，8KB） |
| indexPageMaxSize | int | 65536 | ❌ | IndexPage 最大容量（字节，64KB） |
| maxCacheSize | int | 1000 | ✅ | 页面缓存大小 |

#### Journal 配置

| 参数 | 类型 | 默认值 | 动态 | 描述 |
|------|------|--------|------|------|
| maxChunkSize | long | 67108864 | ❌ | 单 Chunk 最大大小（64MB） |
| maxRetry | int | 3 | ✅ | 写入最大重试次数 |
| retryInterval | long | 100 | ✅ | 重试间隔（ms） |
| truncateRetentionDays | int | 14 | ✅ | Journal 保留天数 |

#### Chunk 配置

| 参数 | 类型 | 默认值 | 动态 | 描述 |
|------|------|--------|------|------|
| chunkSize | long | 67108864 | ❌ | 单 Chunk 最大大小（64MB） |
| preallocate | boolean | true | ✅ | 是否预分配 Standby Chunk |
| maxRetry | int | 3 | ✅ | 分配最大重试次数 |
| retryInterval | long | 100 | ✅ | 重试间隔（ms） |

#### GC 配置

| 参数 | 类型 | 默认值 | 动态 | 描述 |
|------|------|--------|------|------|
| gcScheduleInterval | long | 3600000 | ✅ | GC 调度间隔（1小时） |
| partialGCRatio | double | 0.05 | ✅ | Partial GC 触发阈值 |
| chunkKeepAliveTime | long | 1800000 | ✅ | Chunk 保活时间（30分钟） |
| chunkSealCheckInterval | long | 300000 | ✅ | Chunk Seal 检查间隔（5分钟） |
| holePunchingEnabled | boolean | false | ✅ | 是否启用 Hole Punching |

#### Concurrency 配置

| 参数 | 类型 | 默认值 | 动态 | 描述 |
|------|------|--------|------|------|
| batchSize | int | 32 | ✅ | BatchWriter 批量大小 |
| timeWindow | long | 1 | ✅ | BatchWriter 时间窗口（ms） |
| maxBatchSize | int | 128 | ✅ | 单次批量上限 |

#### 错误处理配置

| 参数 | 类型 | 默认值 | 动态 | 描述 |
|------|------|--------|------|------|
| metadata.maxRetry | int | 5 | ✅ | 元数据写入最大重试次数 |
| metadata.retryInterval | long | 100 | ✅ | 重试间隔（ms） |
| dump.maxRetry | int | 3 | ✅ | Dump Page 写入最大重试次数 |
| dump.retryInterval | long | 100 | ✅ | 重试间隔（ms） |
| dump.autoRetry | boolean | true | ✅ | Dump 失败后是否自动重试 |

#### 数据完整性配置

| 参数 | 类型 | 默认值 | 动态 | 描述 |
|------|------|--------|------|------|
| writeItem.pageSize | int | 4096 | ❌ | Write Item 对齐大小 |
| writeItem.verifyOnRead | boolean | true | ✅ | 读取时是否验证 CRC32 |
| writeItem.verifyOnWrite | boolean | true | ✅ | 写入后是否验证 CRC32 |

#### 监控配置

| 参数 | 类型 | 默认值 | 动态 | 描述 |
|------|------|--------|------|------|
| monitoring.enabled | boolean | true | ✅ | 是否启用监控 |
| monitoring.snapshotInterval | long | 10000 | ✅ | 快照间隔（ms） |
| monitoring.historySize | int | 360 | ✅ | 历史记录数量 |
| monitoring.logPath | String | ./logs/metrics.log | ✅ | 监控日志路径 |
| monitoring.logRetentionDays | int | 7 | ✅ | 日志保留天数 |

#### 备份配置

| 参数 | 类型 | 默认值 | 动态 | 描述 |
|------|------|--------|------|------|
| backup.lockTimeout | long | 3600000 | ✅ | 备份锁定超时时间（ms） |
| backup.batchSize | int | 10000 | ✅ | 恢复时 Batch 插入大小 |
| backup.verifyChecksum | boolean | true | ✅ | 是否验证备份校验和 |
| backup.compressData | boolean | false | ✅ | 是否压缩备份数据 |
| recovery.maxJournalReplaySize | long | 1073741824 | ✅ | 最大 Journal Replay 大小 |

## 3. 配置文件格式

### 3.1 JSON 格式

配置文件采用 JSON 格式，避免 YAML 缩进语法在人工编辑时容易出错的问题。

```json
{
  "kvstore": {
    "storagePath": "./data",
    "maxVersions": 10
  },
  "memorytable": {
    "sealThreshold": 67108864,
    "maxSealedTables": 3
  },
  "bplustree": {
    "leafPageMaxSize": 8192,
    "indexPageMaxSize": 65536,
    "maxCacheSize": 1000
  },
  "journal": {
    "maxChunkSize": 67108864,
    "maxRetry": 3,
    "retryInterval": 100,
    "truncateRetentionDays": 14
  },
  "chunk": {
    "chunkSize": 67108864,
    "preallocate": true,
    "maxRetry": 3,
    "retryInterval": 100
  },
  "gc": {
    "gcScheduleInterval": 3600000,
    "partialGCRatio": 0.05,
    "chunkKeepAliveTime": 1800000,
    "chunkSealCheckInterval": 300000,
    "holePunchingEnabled": false
  },
  "errorHandling": {
    "metadata": {
      "maxRetry": 5,
      "retryInterval": 100
    },
    "dump": {
      "maxRetry": 3,
      "retryInterval": 100,
      "autoRetry": true
    }
  },
  "dataIntegrity": {
    "writeItem": {
      "leafPageMaxSize": 8192,
    "indexPageMaxSize": 65536,
      "verifyOnRead": true,
      "verifyOnWrite": true
    }
  },
  "monitoring": {
    "enabled": true,
    "snapshotInterval": 10000,
    "historySize": 360,
    "logPath": "./logs/metrics.log",
    "logRetentionDays": 7
  },
  "backup": {
    "lockTimeout": 3600000,
    "batchSize": 10000,
    "verifyChecksum": true,
    "compressData": false
  },
  "recovery": {
    "maxJournalReplaySize": 1073741824
  }
}
```

**JSON 格式优势**：
| 优势 | 说明 |
|------|------|
| **无缩进依赖** | 使用花括号界定层级，避免缩进错误 |
| **广泛支持** | 所有编程语言都有成熟的 JSON 解析库 |
| **严格语法** | 语法错误更容易被工具检测 |
| **工具友好** | IDE 支持格式化、校验、自动补全 |
| **可读性好** | 结构清晰，易于理解 |
```

### 3.2 环境变量覆盖

环境变量优先级高于配置文件：

```
环境变量命名规则：
  KVSTORE_<SECTION>_<KEY>

示例：
  KVSTORE_KVSTORE_STORAGE_PATH=/data/kvstore
  KVSTORE_MEMORYTABLE_SEAL_THRESHOLD=134217728
  KVSTORE_GC_PARTIAL_GC_RATIO=0.1
```

## 4. ConfigManager 设计

### 4.1 类设计

ConfigManager 负责配置的加载、缓存、定时刷新和变更通知。核心属性：configPath（配置文件路径）、config（当前生效的配置对象）、listeners（配置监听器列表）、scheduler（定时刷新调度器）。提供类型安全的 get 方法（getInt/getLong/getBoolean 等）和监听器注册/注销接口。

```
class ConfigManager {
    
    属性：
      - configPath: String              // 配置文件路径
      - config: Config                  // 当前配置
      - listeners: List<ConfigListener> // 配置监听器列表
      - scheduler: ScheduledExecutorService // 定时调度器
      - lastModified: long              // 配置文件最后修改时间
    
    方法：
      - load(): void                    // 加载配置
      - reload(): void                  // 重新加载配置
      - get(key: String): Object        // 获取配置值
      - getInt(key: String): int        // 获取整数配置
      - getLong(key: String): long      // 获取长整数配置
      - getDouble(key: String): double  // 获取浮点数配置
      - getBoolean(key: String): boolean // 获取布尔配置
      - getString(key: String): String  // 获取字符串配置
      - addListener(listener: ConfigListener): void // 添加监听器
      - removeListener(listener: ConfigListener): void // 移除监听器
      - startWatch(): void              // 开始监听配置变化
      - stopWatch(): void               // 停止监听
}
```

### 4.2 配置加载流程

配置加载按优先级叠加：先加载默认配置，再从 JSON 配置文件覆盖，最后用环境变量覆盖（KVSTORE_ 前缀）。加载后执行验证（必填项、值范围、类型检查、依赖关系），验证失败则使用默认值或拒绝启动。记录文件最后修改时间用于后续变更检测。

```
load()
    │
    ▼
┌─────────────────────────────────────┐
│  1. 读取配置文件                    │
│     file = File(configPath)         │
│     if (!file.exists()):            │
│         // 使用默认配置             │
│         config = defaultConfig()    │
│         return                      │
└─────────────────────────────────────┘
    │
    ▼
┌─────────────────────────────────────┐
│  2. 解析 JSON 文件                  │
│     json = JSON.parse(file)         │
│     config = Config.fromJson(json)  │
└─────────────────────────────────────┘
    │
    ▼
┌─────────────────────────────────────┐
│  3. 应用环境变量覆盖                │
│     for entry in System.env:        │
│         if (entry.key.startsWith(   │
│             "KVSTORE_")):           │
│             configKey = parseEnvKey(│
│                 entry.key)          │
│             config.set(             │
│                 configKey,          │
│                 entry.value)        │
└─────────────────────────────────────┘
    │
    ▼
┌─────────────────────────────────────┐
│  4. 验证配置                        │
│     validate(config)                │
│     // 检查必填项                   │
│     // 检查值范围                   │
└─────────────────────────────────────┘
    │
    ▼
┌─────────────────────────────────────┐
│  5. 记录最后修改时间                │
│     lastModified = file.lastModified│
└─────────────────────────────────────┘
```

### 4.3 定时刷新机制

ConfigManager 每 5 秒检查一次配置文件的修改时间。如果检测到变化，重新加载配置，计算新旧配置的差异（diff），只通知变更的配置项。每个 ConfigListener 根据变更项执行相应操作（立即生效、延迟生效或记录警告）。

```
startWatch()
    │
    ▼
┌─────────────────────────────────────┐
│  启动定时任务：                     │
│  scheduler.scheduleAtFixedRate(     │
│      this::checkAndReload,          │
│      checkInterval,                 │
│      checkInterval,                 │
│      TimeUnit.MILLISECONDS)         │
│                                      │
│  checkInterval 默认：5000ms (5秒)   │
└─────────────────────────────────────┘

checkAndReload()
    │
    ▼
┌─────────────────────────────────────┐
│  1. 检查文件修改时间                │
│     file = File(configPath)         │
│     if (file.lastModified <=        │
│         lastModified):              │
│         return  // 无变化           │
└─────────────────────────────────────┘
    │
    ▼
┌─────────────────────────────────────┐
│  2. 重新加载配置                    │
│     oldConfig = this.config         │
│     reload()                        │
└─────────────────────────────────────┘
    │
    ▼
┌─────────────────────────────────────┐
│  3. 通知监听器                      │
│     for listener in listeners:      │
│         changes = diff(             │
│             oldConfig, config)      │
│         if (!changes.isEmpty()):    │
│             listener.onConfigChange(│
│                 changes)            │
└─────────────────────────────────────┘
```

## 5. 配置监听器设计

### 5.1 ConfigListener 接口

ConfigListener 定义 onConfigChange 回调方法，接收 ConfigChange 列表。每个 ConfigChange 包含 key、oldValue、newValue 和 dynamic 标志（标识是否支持动态生效）。实现者根据 change 列表更新对应模块的运行时参数。

```
interface ConfigListener {
    
    方法：
      - onConfigChange(changes: List<ConfigChange>): void
        // 配置变更回调
}

class ConfigChange {
    
    属性：
      - key: String           // 配置键
      - oldValue: Object      // 旧值
      - newValue: Object      // 新值
      - dynamic: boolean      // 是否支持动态生效
}
```

### 5.2 监听器示例

以 MemoryTableConfigListener 为例：收到 sealThreshold 变更时更新 MemoryTableManager 的阈值（立即生效），收到 maxSealedTables 变更时同样立即更新。监听器只处理自己关心的配置项，忽略无关变更。

```
class MemoryTableConfigListener 
    implements ConfigListener {
    
    onConfigChange(changes):
        │
        ▼
    ┌─────────────────────────────────────┐
    │  处理 sealThreshold 变更            │
    │  change = changes.find(             │
    │      "memorytable.sealThreshold")   │
    │  if (change != null):               │
    │      memoryTableManager             │
    │          .updateSealThreshold(      │
    │              change.newValue)       │
    └─────────────────────────────────────┘
        │
        ▼
    ┌─────────────────────────────────────┐
    │  处理 maxSealedTables 变更          │
    │  change = changes.find(             │
    │      "memorytable.maxSealedTables") │
    │  if (change != null):               │
    │      memoryTableManager             │
    │          .updateMaxSealedTables(    │
    │              change.newValue)       │
    └─────────────────────────────────────┘
}
```

### 5.3 各模块监听器

| 模块 | 监听配置 | 生效方式 |
|------|----------|----------|
| KVStore | maxVersions | 下次 GC 时生效 |
| MemoryTableManager | sealThreshold, maxSealedTables | 立即生效 |
| BPlusTree | maxCacheSize | 下次缓存淘汰时生效 |
| Journal | maxRetry, retryInterval, truncateRetentionDays | 立即生效 |
| ChunkManager | preallocate, maxRetry, retryInterval | 立即生效 |
| GC | gcScheduleInterval, partialGCRatio, chunkKeepAliveTime | 下次调度时生效 |
| ErrorHandling | maxRetry, retryInterval | 立即生效 |
| DataIntegrity | verifyOnRead, verifyOnWrite | 立即生效 |

## 6. 动态配置生效策略

### 6.1 立即生效

适用于：阈值、重试次数、开关等

```
立即生效示例：

// MemoryTableManager
updateSealThreshold(newThreshold):
    this.sealThreshold = newThreshold
    // 下次写入检查时使用新阈值

// Journal
updateMaxRetry(newMaxRetry):
    this.maxRetry = newMaxRetry
    // 下次重试时使用新值
```

### 6.2 延迟生效

适用于：调度间隔、缓存大小等

```
延迟生效示例：

// GC Scheduler
updateGCScheduleInterval(newInterval):
    // 取消旧的调度任务
    scheduler.cancel(gcTask)
    // 使用新间隔创建新任务
    gcTask = scheduler.scheduleAtFixedRate(
        this::scheduleGC,
        newInterval,
        newInterval,
        TimeUnit.MILLISECONDS)

// Page Cache
updateMaxCacheSize(newSize):
    // 更新阈值
    this.maxCacheSize = newSize
    // 下次缓存淘汰时使用新阈值
```

### 6.3 不支持动态生效

适用于：存储路径、B+树阶数等

```
不支持动态生效示例：

// KVStore
updateStoragePath(newPath):
    // 记录警告日志
    log.warn(
        "storagePath cannot be changed " +
        "dynamically, restart required")
    // 不更新配置
    // 或者抛出异常

// BPlusTree
updateLeafPageMaxSize(newSize):
    // 记录警告日志
    log.warn(
        "leafPageMaxSize cannot be changed " +
        "dynamically, restart required")
    // 不更新配置
```

## 7. 配置变更流程

```
┌─────────────────────────────────────────────────────────────┐
│                    配置变更流程                              │
├─────────────────────────────────────────────────────────────┤
│                                                              │
│  1. 用户修改配置文件                                         │
│     vim kvstore-config.json                                  │
│                                                              │
│  2. ConfigManager 检测到变化（定时检查）                     │
│     ┌─────────────────────────────────────────────┐         │
│     │ checkAndReload()                            │         │
│     │   - 检查文件修改时间                        │         │
│     │   - 重新加载配置                            │         │
│     └─────────────────────────────────────────────┘         │
│                          │                                   │
│                          ▼                                   │
│  3. 计算配置差异                                             │
│     ┌─────────────────────────────────────────────┐         │
│     │ changes = diff(oldConfig, newConfig)        │         │
│     │ // 只包含变更的配置项                       │         │
│     └─────────────────────────────────────────────┘         │
│                          │                                   │
│                          ▼                                   │
│  4. 通知监听器                                               │
│     ┌─────────────────────────────────────────────┐         │
│     │ for listener in listeners:                  │         │
│     │     listener.onConfigChange(changes)        │         │
│     └─────────────────────────────────────────────┘         │
│                          │                                   │
│                          ▼                                   │
│  5. 监听器处理变更                                           │
│     ┌─────────────────────────────────────────────┐         │
│     │ - 立即生效：直接更新                        │         │
│     │ - 延迟生效：标记待更新                      │         │
│     │ - 不支持：记录警告                          │         │
│     └─────────────────────────────────────────────┘         │
│                                                              │
└─────────────────────────────────────────────────────────────┘
```

## 8. 配置验证

### 8.1 验证规则

配置验证包含四层检查：必填项检查（storagePath 不能为空）、值范围检查（maxVersions >= 1、sealThreshold > 0）、类型检查（leafPageMaxSize 必须是整数）、依赖关系检查（holePunchingEnabled 需要文件系统支持）。验证在加载时和动态更新时都执行。

```
validate(config)
    │
    ▼
┌─────────────────────────────────────┐
│  1. 必填项检查                      │
│     if (config.storagePath == null):│
│         throw new ConfigException(  │
│             "storagePath is required")│
└─────────────────────────────────────┘
    │
    ▼
┌─────────────────────────────────────┐
│  2. 值范围检查                      │
│     if (config.maxVersions < 1):    │
│         throw new ConfigException(  │
│             "maxVersions must >= 1")│
│     if (config.sealThreshold <= 0): │
│         throw new ConfigException(  │
│             "sealThreshold must > 0")│
└─────────────────────────────────────┘
    │
    ▼
┌─────────────────────────────────────┐
│  3. 类型检查                        │
│     if (!(config.leafPageMaxSize    │
│         is int)):                   │
│         throw new ConfigException(  │
│             "leafPageMaxSize must   │
│              be integer")           │
└─────────────────────────────────────┘
    │
    ▼
┌─────────────────────────────────────┐
│  4. 依赖关系检查                    │
│     if (config.holePunchingEnabled):│
│         // 检查文件系统是否支持    │
│         if (!fileSystemSupportsHolePunching()):│
│             throw new ConfigException(│
│                 "File system does not " +│
│                 "support hole punching")│
└─────────────────────────────────────┘
```

### 8.2 验证失败处理

验证失败的处理分两种场景：启动时验证失败使用默认值或拒绝启动（取决于配置项的重要性）；运行时动态更新验证失败则保持旧配置不变，记录错误日志。两种情况都不会导致系统崩溃。

```
配置验证失败：

1. 启动时验证失败：
   - 记录错误日志
   - 使用默认值
   - 或者拒绝启动

2. 运行时验证失败（动态更新）：
   - 记录错误日志
   - 保持旧配置
   - 不更新配置
```

## 9. 配置管理 API

KVStore 通过 API 接口暴露配置管理能力，供 Service 层调用。

### 9.1 ConfigManager API

ConfigManager 对外暴露的配置管理接口，提供类型安全的配置读取和监听器管理。

```
interface ConfigManager {
    
    获取所有配置
    getAllConfig(): Config
    
    获取指定配置
    get(key: String): Object
    
    获取指定配置（带类型）
    getInt(key: String): int
    getLong(key: String): long
    getDouble(key: String): double
    getBoolean(key: String): boolean
    getString(key: String): String
    
    添加配置监听器
    addListener(listener: ConfigListener): void
    
    移除配置监听器
    removeListener(listener: ConfigListener): void
    
    重新加载配置文件
    reload(): List<ConfigChange>
}
```

### 9.2 ConfigListener 接口

配置变更监听器接口定义，实现者通过 onConfigChange 回调处理配置变更。

```
interface ConfigListener {
    
    配置变更回调
    onConfigChange(changes: List<ConfigChange>): void
}

class ConfigChange {
    key: String           // 配置键
    oldValue: Object      // 旧值
    newValue: Object      // 新值
    dynamic: boolean      // 是否支持动态生效
}
```

### 9.3 Service 层使用示例

Service 层调用 ConfigManager API 实现配置管理 REST 接口的示例代码。

```
class KVStoreService {
    
    private KVStore kvStore;
    
    // REST API: GET /config
    public String getAllConfig() {
        Config config = kvStore.getConfigManager().getAllConfig();
        return config.toJson();
    }
    
    // REST API: GET /config/{key}
    public String getConfig(String key) {
        Object value = kvStore.getConfigManager().get(key);
        return JSON.stringify(value);
    }
    
    // REST API: POST /config/reload
    public String reloadConfig() {
        List<ConfigChange> changes = kvStore.getConfigManager().reload();
        return JSON.stringify(changes);
    }
    
    // 注册配置监听器
    public void init() {
        kvStore.getConfigManager().addListener(new ConfigListener() {
            @Override
            public void onConfigChange(List<ConfigChange> changes) {
                // 可以推送到监控系统或触发告警
                notifyConfigChanges(changes);
            }
        });
    }
}
```

## 10. 配置参数汇总

### 10.1 按模块汇总

| 模块 | 参数数量 | 动态参数 | 静态参数 |
|------|----------|----------|----------|
| KVStore | 2 | 1 | 1 |
| MemoryTable | 2 | 2 | 0 |
| BPlusTree | 2 | 1 | 1 |
| Journal | 4 | 3 | 1 |
| Chunk | 4 | 3 | 1 |
| GC | 5 | 5 | 0 |
| ErrorHandling | 5 | 5 | 0 |
| DataIntegrity | 3 | 2 | 1 |
| **总计** | **27** | **22** | **5** |

### 10.2 按类型汇总

| 类型 | 参数数量 | 示例 |
|------|----------|------|
| String | 1 | storagePath |
| int | 11 | maxVersions, leafPageMaxSize, maxRetry |
| long | 7 | sealThreshold, gcScheduleInterval |
| double | 1 | partialGCRatio |
| boolean | 7 | preallocate, holePunchingEnabled |

## 11. 相关文档

- [错误处理设计](design-error-handling.md)：错误重试配置
- [GC 设计](design-gc.md)：GC 调度配置
- [Journal 设计](design-journal.md)：Journal 配置
- [存储层设计](design-storage.md)：Chunk 配置
