# 监控体系设计

## 1. 概述

本文档定义 KVStore 模块的监控体系，包括：
- **Performance Counter**：性能计数器，记录操作延迟分布
- **Metrics 采集**：系统指标采集框架
- **API 接口**：通过 API 暴露监控数据，供 Service 层调用
- **历史数据**：记录历史监控数据到日志

**设计原则**：KVStore 作为独立模块，不提供 REST API，仅通过 API 接口暴露监控数据。Service 层负责将监控数据通过 REST API 或其他方式对外暴露。

```
┌─────────────────────────────────────────────────────────────┐
│                    监控体系架构                              │
├─────────────────────────────────────────────────────────────┤
│                                                              │
│  KVStore Module                                             │
│  ┌─────────────────────────────────────────────────────┐    │
│  │  数据采集层                                          │    │
│  │  ┌─────────────────────────────────────────────┐    │    │
│  │  │  Performance Counter  │  System Metrics  │  Health  │
│  │  │  - 操作计数           │  - 内存使用      │  - 检查  │
│  │  │  - 延迟分布           │  - 磁盘使用      │          │
│  │  │  - P50/P75/P90/P99    │  - Chunk 统计    │          │
│  │  └─────────────────────────────────────────────┘    │    │
│  │                          │                           │    │
│  │                          ▼                           │    │
│  │  数据聚合层                                          │    │
│  │  ┌─────────────────────────────────────────────┐    │    │
│  │  │              MetricsRegistry                 │    │    │
│  │  │  - 注册管理所有指标                          │    │    │
│  │  │  - 定时聚合（10秒窗口）                      │    │    │
│  │  │  - 历史数据快照                              │    │    │
│  │  └─────────────────────────────────────────────┘    │    │
│  │                          │                           │    │
│  │         ┌────────────────┼────────────────┐         │    │
│  │         │                │                │         │    │
│  │         ▼                ▼                ▼         │    │
│  │  ┌─────────────┐  ┌─────────────┐  ┌─────────────┐ │    │
│  │  │ API 接口    │  │ 历史日志    │  │ Listener    │ │    │
│  │  │ getMetrics  │  │ metrics.log │  │ 回调        │ │    │
│  │  │ getHealth   │  │             │  │             │ │    │
│  │  └─────────────┘  └─────────────┘  └─────────────┘ │    │
│  └─────────────────────────────────────────────────────┘    │
│                              │                               │
│                              │ API 调用                      │
│                              ▼                               │
│  Service Layer                                              │
│  ┌─────────────────────────────────────────────────────┐    │
│  │  REST API /metrics, /health, Prometheus 格式等      │    │
│  └─────────────────────────────────────────────────────┘    │
│                                                              │
└─────────────────────────────────────────────────────────────┘
```

## 2. Performance Counter 设计

### 2.1 核心概念

Performance Counter 用于记录操作的延迟分布，支持：
- **操作计数**：固定时间窗口内的操作次数
- **延迟统计**：平均值、最大值、最小值
- **百分位分布**：P50、P75、P90、P99
- **历史快照**：定期记录历史数据

### 2.2 Histogram 实现

使用滑动窗口直方图实现延迟分布统计：

```
class Histogram {
    
    属性：
      - buckets: long[]           // 延迟桶，按延迟范围分组
      - bucketBounds: long[]      // 桶边界（微秒）
      - count: long               // 总计数
      - sum: long                 // 总延迟（用于计算平均值）
      - min: long                 // 最小延迟
      - max: long                 // 最大延迟
      - windowSize: long          // 时间窗口大小（毫秒）
      - windowStart: long         // 当前窗口起始时间
    
    方法：
      - record(value: long): void  // 记录一个延迟值
      - getCount(): long           // 获取操作计数
      - getMean(): double          // 获取平均延迟
      - getMin(): long             // 获取最小延迟
      - getMax(): long             // 获取最大延迟
      - getPercentile(p: double): long  // 获取百分位延迟
      - snapshot(): HistogramSnapshot   // 获取快照
      - reset(): void              // 重置计数器
}

延迟桶边界设计（微秒）：
  bucketBounds = [
      1,      // 0-1 μs
      5,      // 1-5 μs
      10,     // 5-10 μs
      25,     // 10-25 μs
      50,     // 25-50 μs
      100,    // 50-100 μs
      250,    // 100-250 μs
      500,    // 250-500 μs
      1000,   // 0.5-1 ms
      2500,   // 1-2.5 ms
      5000,   // 2.5-5 ms
      10000,  // 5-10 ms
      25000,  // 10-25 ms
      50000,  // 25-50 ms
      100000, // 50-100 ms
      250000, // 100-250 ms
      500000, // 250-500 ms
      1000000,// 0.5-1 s
      5000000 // 1-5 s
  ]
```

### 2.3 PerformanceCounter 类

PerformanceCounter 封装了一个操作的完整性能统计：Histogram 记录延迟分布（P50/P75/P90/P99），errorCount 记录错误次数。提供 recordSuccess(latencyMicros) 和 recordError() 两个记录方法，以及 getSnapshot() 获取当前窗口的统计快照。

```
class PerformanceCounter {
    
    属性：
      - name: String              // 计数器名称
      - description: String       // 描述
      - histogram: Histogram      // 延迟直方图
      - errorCount: long          // 错误计数
      - lastUpdateTime: long      // 最后更新时间
    
    方法：
      - recordSuccess(latencyMicros: long): void
      - recordError(): void
      - getSnapshot(): CounterSnapshot
}

class CounterSnapshot {
    
    属性：
      - name: String
      - timestamp: long
      - count: long
      - errorCount: long
      - mean: double
      - min: long
      - max: long
      - p50: long
      - p75: long
      - p90: long
      - p99: long
}
```

### 2.4 主要操作的 Performance Counter

| Counter 名称 | 描述 | 记录时机 |
|-------------|------|----------|
| kvstore.put | Put 操作延迟 | put() 调用完成 |
| kvstore.get | Get 操作延迟 | get() 调用完成 |
| kvstore.delete | Delete 操作延迟 | delete() 调用完成 |
| kvstore.batch | Batch 操作延迟 | batch() 调用完成 |
| kvstore.range | Range Query 延迟 | rangeQuery() 调用完成 |
| journal.write | Journal 写入延迟 | journal.write() 完成 |
| journal.rotate | Journal Rotation 延迟 | rotateChunk() 完成 |
| memorytable.seal | MemoryTable Seal 延迟 | seal() 完成 |
| tree.dump | Tree Dump 延迟 | dump() 完成 |
| tree.search | Tree Search 延迟 | bPlusTree.search() 完成 |
| chunk.write | Chunk 写入延迟 | chunk.write() 完成 |
| chunk.allocate | Chunk 分配延迟 | allocateChunk() 完成 |
| gc.full | Full GC 延迟 | performFullGC() 完成 |
| gc.partial | Partial GC 延迟 | performPartialGC() 完成 |
| batch.write | BatchWriter 批量写入延迟 | processBatch() 完成 |
| batch.merge | BatchWriter 请求合并延迟 | mergeRequests() 完成 |
| queue.wait | 写请求排队等待延迟 | 请求入队到开始处理 |

## 3. Metrics 采集框架

### 3.1 MetricsRegistry

MetricsRegistry 是监控系统的核心注册器，管理所有的 PerformanceCounter、Gauge 和 HealthCheck。提供定时采集功能（默认 10 秒窗口），每次采集生成 MetricsSnapshot（含所有 Counter 快照、Gauge 值和健康检查结果），保存到历史列表并通知监听器。

```
class MetricsRegistry {
    
    属性：
      - counters: Map<String, PerformanceCounter>
      - gauges: Map<String, Gauge>
      - healthChecks: Map<String, HealthCheck>
      - listeners: List<MetricsListener>  // 监听器列表
      - snapshotInterval: long    // 快照间隔（默认 10 秒）
      - scheduler: ScheduledExecutorService
      - history: List<MetricsSnapshot>  // 历史快照
      - maxHistorySize: int       // 最大历史记录数
    
    方法：
      - counter(name: String): PerformanceCounter
      - gauge(name: String, supplier: Supplier<Long>): void
      - healthCheck(name: String, check: HealthCheck): void
      - addListener(listener: MetricsListener): void
      - removeListener(listener: MetricsListener): void
      - getSnapshot(): MetricsSnapshot
      - getHistory(duration: long): List<MetricsSnapshot>
      - start(): void             // 开始定时采集
      - stop(): void              // 停止采集
}

class MetricsSnapshot {
    
    属性：
      - timestamp: long
      - counters: Map<String, CounterSnapshot>
      - gauges: Map<String, Long>
      - healthChecks: Map<String, HealthCheckResult>
}
```

### 3.2 Gauge 指标

Gauge 用于记录瞬时值：

| Gauge 名称 | 描述 | 类型 |
|-----------|------|------|
| memory.used | 已使用内存 | long |
| memory.free | 空闲内存 | long |
| memory.max | 最大可用内存 | long |
| disk.used | 磁盘已使用空间 | long |
| disk.free | 磁盘剩余空间 | long |
| chunk.total | Chunk 总数 | long |
| chunk.open | OPEN 状态 Chunk 数 | long |
| chunk.sealed | SEALED 状态 Chunk 数 | long |
| memorytable.active.size | Active MemoryTable 大小 | long |
| memorytable.sealed.count | Sealed MemoryTable 数量 | long |
| tree.version | 当前 Tree 版本 | long |
| tree.leaf.pages | 叶页数量 | long |
| tree.index.pages | 索引页数量 | long |
| cache.hit.rate | 缓存命中率 | double |
| journal.region | 当前 Journal Region | long |
| cache.size | PageCache 当前条目数 | long |
| cache.max.size | PageCache 最大条目数 | long |
| queue.pending | WriteRequestQueue 待处理请求数 | long |
| batch.size.avg | 最近批量大小平均值 | double |

### 3.3 MetricsListener 接口

用于支持 Service 层订阅监控数据变化：

```
interface MetricsListener {
    
    方法：
      - onSnapshot(snapshot: MetricsSnapshot): void
      - onHealthCheckChanged(name: String, result: HealthCheckResult): void
}
```

### 3.4 定时采集流程

定时采集每 10 秒执行一次：收集所有 Counter 快照并重置计数器（开始新窗口）→ 收集所有 Gauge 瞬时值 → 执行所有健康检查 → 创建 MetricsSnapshot → 保存到历史列表（最多保留 360 条，即 1 小时）→ 通知所有 MetricsListener → 写入 metrics.log 日志。

```
start()
    │
    ▼
┌─────────────────────────────────────┐
│  启动定时任务                       │
│  scheduler.scheduleAtFixedRate(     │
│      this::collectAndSnapshot,      │
│      snapshotInterval,              │
│      snapshotInterval,              │
│      TimeUnit.MILLISECONDS)         │
│                                      │
│  snapshotInterval = 10000 (10秒)    │
└─────────────────────────────────────┘

collectAndSnapshot()
    │
    ▼
┌─────────────────────────────────────┐
│  1. 收集所有 Counter 快照           │
│     counterSnapshots = {}           │
│     for counter in counters:        │
│         snapshot = counter          │
│             .getSnapshot()          │
│         counterSnapshots.put(       │
│             counter.name, snapshot) │
│         counter.reset()  // 重置    │
└─────────────────────────────────────┘
    │
    ▼
┌─────────────────────────────────────┐
│  2. 收集所有 Gauge 值               │
│     gaugeValues = {}                │
│     for gauge in gauges:            │
│         value = gauge.getValue()    │
│         gaugeValues.put(            │
│             gauge.name, value)      │
└─────────────────────────────────────┘
    │
    ▼
┌─────────────────────────────────────┐
│  3. 执行健康检查                    │
│     healthResults = {}              │
│     for check in healthChecks:      │
│         result = check.execute()    │
│         healthResults.put(          │
│             check.name, result)     │
└─────────────────────────────────────┘
    │
    ▼
┌─────────────────────────────────────┐
│  4. 创建快照                        │
│     snapshot = MetricsSnapshot(     │
│         timestamp = now(),          │
│         counters = counterSnapshots,│
│         gauges = gaugeValues,       │
│         healthChecks = healthResults)│
└─────────────────────────────────────┘
    │
    ▼
┌─────────────────────────────────────┐
│  5. 保存历史                        │
│     history.add(snapshot)           │
│     if (history.size() > maxHistory):│
│         history.remove(0)           │
└─────────────────────────────────────┘
    │
    ▼
┌─────────────────────────────────────┐
│  6. 通知监听器                      │
│     for listener in listeners:      │
│         listener.onSnapshot(snapshot)│
└─────────────────────────────────────┘
    │
    ▼
┌─────────────────────────────────────┐
│  7. 写入日志                        │
│     writeMetricsLog(snapshot)       │
└─────────────────────────────────────┘
```

## 4. API 接口设计

### 4.1 MetricsRegistry API

MetricsRegistry 通过 API 对外暴露监控数据：getSnapshot() 获取当前快照、getHistory() 获取历史快照、checkHealth() 执行健康检查。Service 层调用这些 API 将数据转换为 REST 响应（JSON 或 Prometheus 格式）。

```
interface MetricsRegistry {
    
    获取当前快照
    getSnapshot(): MetricsSnapshot
    
    获取历史快照
    getHistory(duration: long): List<MetricsSnapshot>
    
    获取指定 Counter 快照
    getCounterSnapshot(name: String): CounterSnapshot
    
    获取所有 Gauge 值
    getGauges(): Map<String, Long>
    
    执行健康检查
    checkHealth(): Map<String, HealthCheckResult>
    
    执行指定健康检查
    checkHealth(name: String): HealthCheckResult
    
    添加监听器
    addListener(listener: MetricsListener): void
    
    移除监听器
    removeListener(listener: MetricsListener): void
}
```

### 4.2 MetricsSnapshot 数据结构

MetricsSnapshot 包含采集时间戳、所有 Counter 的快照（名称→CounterSnapshot 映射）、所有 Gauge 的值（名称→Long 映射）和健康检查结果。提供 toJson() 和 toPrometheus() 两种序列化方法。

```
class MetricsSnapshot {
    
    属性：
      - timestamp: long
      - counters: Map<String, CounterSnapshot>
      - gauges: Map<String, Long>
      - healthChecks: Map<String, HealthCheckResult>
    
    方法：
      - toJson(): String           // 序列化为 JSON
      - toPrometheus(): String     // 序列化为 Prometheus 格式
}
```

### 4.3 CounterSnapshot 数据结构

CounterSnapshot 记录一个时间窗口内的操作统计：count（总次数）、errorCount（错误次数）、mean/min/max（平均/最小/最大延迟）、P50/P75/P90/P99（百分位延迟，微秒）。

```
class CounterSnapshot {
    
    属性：
      - name: String
      - timestamp: long
      - count: long
      - errorCount: long
      - mean: double
      - min: long
      - max: long
      - p50: long
      - p75: long
      - p90: long
      - p99: long
    
    方法：
      - toJson(): String
}
```

### 4.4 JSON 输出格式

MetricsSnapshot 的 JSON 输出包含 timestamp、counters（每个 Counter 的 count/errorCount/mean/min/max/p50-p99）、gauges（内存/磁盘/Chunk/MemoryTable/Tree/Cache 等瞬时值）和 healthChecks（每项检查的 status/message/details）。

```
MetricsSnapshot.toJson() 输出示例：

{
  "timestamp": 1704067200000,
  "counters": {
    "kvstore.put": {
      "count": 1523,
      "errorCount": 2,
      "mean": 125.5,
      "min": 10,
      "max": 15000,
      "p50": 100,
      "p75": 150,
      "p90": 250,
      "p99": 5000
    },
    "kvstore.get": {
      "count": 8521,
      "errorCount": 0,
      "mean": 45.2,
      "min": 5,
      "max": 2000,
      "p50": 30,
      "p75": 50,
      "p90": 100,
      "p99": 500
    }
  },
  "gauges": {
    "memory.used": 1073741824,
    "memory.free": 536870912,
    "memory.max": 2147483648,
    "disk.used": 10737418240,
    "disk.free": 107374182400,
    "chunk.total": 25,
    "chunk.open": 3,
    "chunk.sealed": 22,
    "memorytable.active.size": 33554432,
    "memorytable.sealed.count": 2,
    "tree.version": 5,
    "cache.hit.rate": 0.85
  },
  "healthChecks": {
    "storage": {
      "status": "UP",
      "message": "Storage is healthy",
      "details": {
        "diskFree": 107374182400,
        "diskUsed": 10737418240
      }
    },
    "memory": {
      "status": "UP",
      "message": "Memory usage is normal",
      "details": {
        "usedPercent": 50.0
      }
    }
  }
}
```

### 4.5 Prometheus 格式输出

Prometheus 格式输出遵循 OpenMetrics 规范：Counter 类型输出 _count 和 _sum 指标，Summary 类型输出各 quantile 的值，Gauge 类型直接输出当前值。每个指标附带 HELP 和 TYPE 注释。

```
MetricsSnapshot.toPrometheus() 输出示例：

# HELP kvstore_put_count Total count of put operations
# TYPE kvstore_put_count counter
kvstore_put_count 1523

# HELP kvstore_put_latency Latency of put operations
# TYPE kvstore_put_latency summary
kvstore_put_latency{quantile="0.5"} 100
kvstore_put_latency{quantile="0.75"} 150
kvstore_put_latency{quantile="0.9"} 250
kvstore_put_latency{quantile="0.99"} 5000
kvstore_put_latency_sum 191123.5
kvstore_put_latency_count 1523

# HELP memory_used_bytes Used memory in bytes
# TYPE memory_used_bytes gauge
memory_used_bytes 1073741824

# HELP chunk_total Total number of chunks
# TYPE chunk_total gauge
chunk_total 25
```

## 5. 历史数据日志

### 5.1 日志配置

监控日志配置：文件路径为 {storagePath}/logs/metrics.log，按天滚动，保留 7 天。格式为 JSON Lines（每行一条 JSON），便于工具解析。

```
日志文件：
  - 路径：{storagePath}/logs/metrics.log
  - 滚动策略：按天滚动
  - 保留天数：7 天

日志格式：JSON Lines
```

### 5.2 日志记录示例

metrics.log 采用 JSON Lines 格式（每行一条 JSON），按天滚动，保留 7 天。每 10 秒写入一条记录，包含 timestamp、counters 和 gauges。便于离线分析和问题回溯。

```json
{"timestamp":1704067200000,"counters":{"kvstore.put":{"count":1523,"errorCount":2,"mean":125.5,"min":10,"max":15000,"p50":100,"p75":150,"p90":250,"p99":5000},"kvstore.get":{"count":8521,"errorCount":0,"mean":45.2,"min":5,"max":2000,"p50":30,"p75":50,"p90":100,"p99":500}},"gauges":{"memory.used":1073741824,"memory.free":536870912,"chunk.total":25,"chunk.open":3,"chunk.sealed":22,"tree.version":5,"cache.hit.rate":0.85}}
```

### 5.3 日志写入策略

每次定时采集后将 MetricsSnapshot 序列化为单行 JSON 写入日志文件，立即 flush 确保数据不丢失。日志记录不阻塞采集流程。

```
writeMetricsLog(snapshot)
    │
    ▼
┌─────────────────────────────────────┐
│  1. 构建 JSON 对象                  │
│     json = {                        │
│         "timestamp": snapshot.timestamp,
│         "counters": {...},          │
│         "gauges": {...}             │
│     }                               │
└─────────────────────────────────────┘
    │
    ▼
┌─────────────────────────────────────┐
│  2. 序列化为单行 JSON               │
│     line = JSON.stringify(json)     │
└─────────────────────────────────────┘
    │
    ▼
┌─────────────────────────────────────┐
│  3. 写入日志文件                    │
│     writer.write(line + "\n")       │
│     writer.flush()                  │
└─────────────────────────────────────┘
```

## 6. 健康检查设计

### 6.1 HealthCheck 接口

HealthCheck 接口定义 check() 方法，返回 HealthCheckResult（status: UP/DOWN/DEGRADED + message + details）。内置检查项包括：StorageHealthCheck（磁盘空间 > 10%）、MemoryHealthCheck（内存使用 < 90%）、JournalHealthCheck（Journal 可写入）等。

```
interface HealthCheck {
    
    方法：
      - getName(): String
      - check(): HealthCheckResult
}

class HealthCheckResult {
    
    属性：
      - status: HealthStatus  // UP, DOWN, DEGRADED
      - message: String
      - details: Map<String, Object>
      - timestamp: long
    
    方法：
      - toJson(): String
}

enum HealthStatus {
    UP,       // 健康
    DOWN,     // 不健康
    DEGRADED  // 降级
}
```

### 6.2 内置健康检查

| 检查项 | 描述 | 判断条件 |
|--------|------|----------|
| StorageHealthCheck | 存储健康检查 | 磁盘空间 > 10% |
| MemoryHealthCheck | 内存健康检查 | 内存使用 < 90% |
| JournalHealthCheck | Journal 健康检查 | Journal 可写入 |
| TreeHealthCheck | Tree 健康检查 | Tree 可访问 |
| ChunkHealthCheck | Chunk 健康检查 | Chunk 可分配 |

### 6.3 健康检查实现示例

以 StorageHealthCheck 为例展示健康检查实现：获取磁盘使用率 → 判断健康状态（< 5% DOWN / < 10% DEGRADED / 其他 UP）→ 返回 HealthCheckResult（含 status、message 和 details）。

```
class StorageHealthCheck implements HealthCheck {
    
    getName(): "storage"
    
    check(): HealthCheckResult
        │
        ▼
    ┌─────────────────────────────────────┐
    │  1. 获取磁盘空间信息               │
    │     diskUsage = getDiskUsage(       │
    │         config.storagePath)         │
    │     freePercent = diskUsage.free    │
    │         / diskUsage.total * 100     │
    └─────────────────────────────────────┘
        │
        ▼
    ┌─────────────────────────────────────┐
    │  2. 判断健康状态                    │
    │     if (freePercent < 5):           │
    │         status = DOWN               │
    │         message = "Disk almost full"│
    │     elif (freePercent < 10):        │
    │         status = DEGRADED           │
    │         message = "Disk space low"  │
    │     else:                           │
    │         status = UP                 │
    │         message = "Storage is healthy"│
    └─────────────────────────────────────┘
        │
        ▼
    ┌─────────────────────────────────────┐
    │  3. 返回结果                        │
    │     return HealthCheckResult(       │
    │         status = status,            │
    │         message = message,          │
    │         details = {                 │
    │             "diskFree": diskUsage.free,
    │             "diskUsed": diskUsage.used,
    │             "freePercent": freePercent│
    │         }                           │
    │     )                               │
    └─────────────────────────────────────┘
}
```

## 7. 使用示例

### 7.1 在代码中使用 Performance Counter

在 KVStore 操作中嵌入性能计量：操作开始时记录 System.nanoTime()，成功完成后调用 counter.recordSuccess(latencyMicros)，异常时调用 counter.recordError()。计量代码应尽量轻量，不影响操作本身的延迟。

```
class KVStore {
    
    put(key, value)
        │
        ▼
    ┌─────────────────────────────────────┐
    │  1. 记录开始时间                    │
    │     startTime = System.nanoTime()   │
    └─────────────────────────────────────┘
        │
        ▼
    ┌─────────────────────────────────────┐
    │  2. 执行操作                        │
    │     try:                            │
    │         journal.write(PUT, key, value)│
    │         memoryTableManager.put(key, value)│
    │     catch (Exception e):            │
    │         metrics.counter("kvstore.put")│
    │             .recordError()          │
    │         throw e                     │
    └─────────────────────────────────────┘
        │
        ▼
    ┌─────────────────────────────────────┐
    │  3. 记录延迟                        │
    │     latencyMicros = (               │
    │         System.nanoTime() -         │
    │         startTime) / 1000           │
    │     metrics.counter("kvstore.put")  │
    │         .recordSuccess(latencyMicros)│
    └─────────────────────────────────────┘
}
```

### 7.2 注册 Gauge

Gauge 通过 lambda 注册，每次采集时调用 lambda 获取当前值。例如 memory.used 注册 Runtime.totalMemory() - Runtime.freeMemory()，chunk.total 注册 chunkManager.getAllChunks().size()。Gauge 的值反映注册时间点的瞬时状态。

```
metrics.gauge("memory.used", () -> {
    Runtime runtime = Runtime.getRuntime();
    return runtime.totalMemory() - runtime.freeMemory();
});

metrics.gauge("memory.free", () -> {
    Runtime runtime = Runtime.getRuntime();
    return runtime.freeMemory();
});

metrics.gauge("memory.max", () -> {
    Runtime runtime = Runtime.getRuntime();
    return runtime.maxMemory();
});

metrics.gauge("chunk.total", () -> {
    return chunkManager.getAllChunks().size();
});

metrics.gauge("chunk.open", () -> {
    return chunkManager.getChunksByStatus(OPEN).size();
});
```

### 7.3 注册健康检查

健康检查通过 metrics.healthCheck(name, check) 注册，每次定时采集时自动执行所有检查。内置检查项覆盖存储、内存、Journal、Tree 和 Chunk 五个维度。

```
metrics.healthCheck("storage", new StorageHealthCheck(config));
metrics.healthCheck("memory", new MemoryHealthCheck(config));
metrics.healthCheck("journal", new JournalHealthCheck(journal));
metrics.healthCheck("tree", new TreeHealthCheck(bPlusTree));
```

### 7.4 Service 层使用示例

Service 层将 MetricsRegistry 的 API 封装为 REST 端点：GET /metrics 返回 JSON 格式快照，GET /metrics/prometheus 返回 Prometheus 格式，GET /health 返回健康检查结果。还可注册 MetricsListener 将数据推送到外部监控系统。

```
class KVStoreService {
    
    private KVStore kvStore;
    
    // REST API: GET /metrics
    public String getMetrics() {
        MetricsSnapshot snapshot = kvStore.getMetricsRegistry().getSnapshot();
        return snapshot.toJson();
    }
    
    // REST API: GET /metrics/prometheus
    public String getMetricsPrometheus() {
        MetricsSnapshot snapshot = kvStore.getMetricsRegistry().getSnapshot();
        return snapshot.toPrometheus();
    }
    
    // REST API: GET /health
    public String getHealth() {
        Map<String, HealthCheckResult> results = kvStore.getMetricsRegistry().checkHealth();
        return JSON.stringify(results);
    }
    
    // 注册监听器
    public void init() {
        kvStore.getMetricsRegistry().addListener(new MetricsListener() {
            @Override
            public void onSnapshot(MetricsSnapshot snapshot) {
                // 可以推送到监控系统
                pushToMonitoringSystem(snapshot);
            }
        });
    }
}
```

## 8. 配置参数

| 参数 | 默认值 | 描述 |
|------|--------|------|
| monitoring.enabled | true | 是否启用监控 |
| monitoring.snapshotInterval | 10000 | 快照间隔（毫秒） |
| monitoring.historySize | 360 | 历史记录数量（10秒间隔，约1小时） |
| monitoring.logPath | ./logs/metrics.log | 监控日志路径 |
| monitoring.logRetentionDays | 7 | 日志保留天数 |

## 9. 监控指标完整列表

### 9.1 Performance Counters

| 名称 | 描述 | 单位 |
|------|------|------|
| kvstore.put | Put 操作延迟 | 微秒 |
| kvstore.get | Get 操作延迟 | 微秒 |
| kvstore.delete | Delete 操作延迟 | 微秒 |
| kvstore.batch | Batch 操作延迟 | 微秒 |
| kvstore.range | Range Query 延迟 | 微秒 |
| journal.write | Journal 写入延迟 | 微秒 |
| journal.rotate | Journal Rotation 延迟 | 微秒 |
| journal.sync | Journal Sync 延迟 | 微秒 |
| memorytable.seal | MemoryTable Seal 延迟 | 微秒 |
| tree.dump | Tree Dump 延迟 | 微秒 |
| tree.search | Tree Search 延迟 | 微秒 |
| tree.insert | Tree Insert 延迟 | 微秒 |
| tree.delete | Tree Delete 延迟 | 微秒 |
| chunk.write | Chunk 写入延迟 | 微秒 |
| chunk.read | Chunk 读取延迟 | 微秒 |
| chunk.allocate | Chunk 分配延迟 | 微秒 |
| chunk.seal | Chunk Seal 延迟 | 微秒 |
| gc.full | Full GC 延迟 | 微秒 |
| gc.partial | Partial GC 延迟 | 微秒 |
| gc.hole_punch | Hole Punching 延迟 | 微秒 |

### 9.2 Gauges

| 名称 | 描述 | 单位 |
|------|------|------|
| memory.used | 已使用内存 | 字节 |
| memory.free | 空闲内存 | 字节 |
| memory.max | 最大可用内存 | 字节 |
| memory.used_percent | 内存使用率 | 百分比 |
| disk.used | 磁盘已使用空间 | 字节 |
| disk.free | 磁盘剩余空间 | 字节 |
| disk.used_percent | 磁盘使用率 | 百分比 |
| chunk.total | Chunk 总数 | 个 |
| chunk.open | OPEN 状态 Chunk 数 | 个 |
| chunk.sealed | SEALED 状态 Chunk 数 | 个 |
| chunk.deleting | DELETING 状态 Chunk 数 | 个 |
| chunk.total_size | Chunk 总大小 | 字节 |
| memorytable.active.size | Active MemoryTable 大小 | 字节 |
| memorytable.active.entries | Active MemoryTable 条目数 | 个 |
| memorytable.sealed.count | Sealed MemoryTable 数量 | 个 |
| memorytable.sealed.total_size | Sealed MemoryTable 总大小 | 字节 |
| tree.version | 当前 Tree 版本 | - |
| tree.leaf.pages | 叶页数量 | 个 |
| tree.index.pages | 索引页数量 | 个 |
| tree.height | 树高度 | - |
| tree.entries | 总条目数 | 个 |
| cache.read.size | 读缓存大小 | 个 |
| cache.read.hit_count | 读缓存命中次数 | 次 |
| cache.read.miss_count | 读缓存未命中次数 | 次 |
| cache.read.hit_rate | 读缓存命中率 | 百分比 |
| journal.region | 当前 Journal Region | - |
| journal.offset | 当前 Journal Offset | 字节 |
| journal.chunk_size | Journal Chunk 大小 | 字节 |

## 10. 相关文档

- [Config 框架设计](design-config.md)：监控配置参数
- [错误处理设计](design-error-handling.md)：错误计数与告警
- [KVStore 设计](design-kvstore.md)：核心操作流程
- [Service 设计](design-service.md)：REST API 暴露
