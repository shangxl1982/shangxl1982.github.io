# 并发控制设计

## 1. 概述

本文档定义 KVStore 的并发控制机制，采用请求队列和批量合并的方式，提高写入吞吐量，同时保证数据安全。

**设计原则**：
- **无锁读操作**：读操作基于快照机制，完全无锁
- **请求队列化**：写操作通过请求队列串行化，避免锁竞争
- **批量合并**：合并多个写请求，减少 Journal 写入次数
- **同步等待**：客户端提交写请求后同步等待完成，确保返回时数据已持久化

## 2. 并发架构

### 2.1 整体并发模型

系统的并发模型分为写路径和读路径。写路径：客户端请求进入 WriteRequestQueue（无锁 ConcurrentLinkedQueue），BatchWriter 后台线程批量收集请求，每个请求独立构造 Write Item，多个 Write Item 聚合后一次性写入存储，然后批量更新 MemoryTable，完成后通知所有等待的客户端。读路径：基于快照机制完全无锁——获取 B+Tree Root 和 MemoryTable 引用的快照后，整个读操作使用同一快照，保证数据一致性。

```
┌─────────────────────────────────────────────────────────────┐
│                    并发控制架构                              │
├─────────────────────────────────────────────────────────────┤
│                                                              │
│  写操作路径：                                                │
│  ┌──────────┐    ┌─────────────────┐    ┌─────────────┐    │
│  │ 写请求   │ ──►│ WriteRequest    │ ──►│ RequestQueue│    │
│  │          │    │   Queue         │    │   (无锁)     │    │
│  └──────────┘    └─────────────────┘    └──────┬──────┘    │
│         │                                     │             │
│         │  同步等待                             ▼             │
│         │  Future.get()               ┌─────────────┐        │
│         │                            │ BatchWriter │        │
│         │                            │  (后台线程) │        │
│         │                            └──────┬──────┘        │
│         │                                     │             │
│         │                                     ▼             │
│         │                            ┌─────────────┐        │
│         │                            │ Journal     │        │
│         │                            │  批量写入    │        │
│         │                            └──────┬──────┘        │
│         │                                     │             │
│         │                                     ▼             │
│         │                            ┌─────────────┐        │
│         │                            │MemoryTable   │        │
│         │                            │  批量更新    │        │
│         │                            └──────┬──────┘        │
│         │                                     │             │
│         │◄────────────── Future.complete() ───┘             │
│                                                              │
│  读操作路径：                                                │
│  ┌──────────┐    ┌──────────┐    ┌──────────┐             │
│  │ 读请求   │ ──►│ 快照读   │ ──►│ 查询     │             │
│  │          │    │ (无锁)   │    │  数据    │             │
│  └──────────┘    └──────────┘    └──────────┘             │
│         │                │                │                 │
│         │                │                ▼                 │
│         │                │        ┌──────────┐             │
│         │                └───────►│MemoryTable│            │
│         │                          │  查询    │             │
│         │                          └──────────┘             │
│         │                │                │                 │
│         │                │                ▼                 │
│         │                │        ┌──────────┐             │
│         │                └───────►│ B+Tree   │             │
│         │                          │  查询    │             │
│         │                          └──────────┘             │
│                                                              │
└─────────────────────────────────────────────────────────────┘
```

### 2.2 请求队列架构

WriteRequestQueue 使用 ConcurrentLinkedQueue 实现无锁入队。BatchWriter 后台线程按两个条件收集请求：达到 batchSize（默认 32）或超过 timeWindow（默认 1ms）。**每个请求独立构造一个 Write Item**：PUT 和 DELETE 各自对应一个 Write Item，BATCH 操作将其子操作打包为一个 BATCH 类型的 Write Item（不拆分）。BatchWriter 将多个 Write Item 聚合后**一次性写入存储**（类似 writev），减少系统调用次数。写入完成后获取 MemoryTable 写锁批量更新所有请求，最后通知所有等待的客户端。

```
请求队列处理流程：
┌─────────────────────────────────────────────────────────────┐
│                   请求队列处理流程                            │
├─────────────────────────────────────────────────────────────┤
│                                                              │
│  ┌─────────────────────────────────────────────────────┐    │
│  │              WriteRequest Queue                      │    │
│  │  ┌───────┐ ┌───────┐ ┌───────┐ ┌───────┐           │    │
│  │  │ PUT   │ │ PUT   │ │ DEL   │ │ PUT   │           │    │
│  │  │ k1,v1 │ │ k2,v2 │ │ k3    │ │ k4,v4 │           │    │
│  │  └───────┘ └───────┘ └───────┘ └───────┘           │    │
│  └─────────────────────────────────────────────────────┘    │
│                          │                                  │
│                          ▼                                  │
│  ┌─────────────────────────────────────────────────────┐    │
│  │               BatchWriter (后台线程)                  │    │
│  │  1. 批量收集请求（timeWindow 或 batchSize）         │    │
│  │  2. 每个请求独立构造一个 Write Item                 │    │
│  │     （不合并，Journal 忠实记录每个操作）            │    │
│  │  3. 多个 Write Item 一次性写入存储                  │    │
│  │     （聚合 I/O，减少系统调用）                      │    │
│  └─────────────────────────────────────────────────────┘    │
│                          │                                  │
│                          ▼                                  │
│  ┌─────────────────────────────────────────────────────┐    │
│  │             批量更新 MemoryTable                     │    │
│  │  - 获取写锁，逐条应用所有操作到 MemoryTable        │    │
│  │  - 一次写锁处理整个 batch，减少锁获取次数          │    │
│  │  - 完成后通知所有等待的客户端                       │    │
│  └─────────────────────────────────────────────────────┘    │
│                                                              │
└─────────────────────────────────────────────────────────────┘
```

## 3. 核心并发机制

### 3.1 快照读 (Snapshot Read)

**设计目标**：读操作完全无锁，提供一致的数据视图

```
快照读机制：

读操作通过获取当前状态的快照来实现无锁读：
  1. 获取 B+Tree Root 快照（AtomicReference，原子操作）
  2. 获取 MemoryTable 引用列表快照
  3. 整个读操作使用同一个快照
  4. 保证看到一致的状态

读操作流程：
  get(key):
    // 1. 查询 Active MemoryTable（读锁）
    value = activeTable.get(key)
    if (value != null): return value

    // 2. 查询 Sealed MemoryTables（无锁，只读）
    for table in sealedTables:
        value = table.get(key)
        if (value != null): return value

    // 3. 查询 B+Tree（快照读，无锁）
    rootSnapshot = readRoot.get()  // 原子操作
    return rootSnapshot.search(key)

快照读优势：
  - 读操作对 Sealed Tables 和 B+Tree 完全无锁，零阻塞
  - 仅查询 Active MemoryTable 时需要读锁
  - 提供一致的数据视图
  - 支持高并发读取
```

### 3.2 请求队列机制

**设计目标**：通过队列串行化写操作，利用批量合并减少 I/O

```
class WriteRequestQueue {
    属性：
      - queue: ConcurrentLinkedQueue<WriteRequest> // 无锁队列
      - batchSizeThreshold: int                    // 批量大小阈值
      - timeWindowThreshold: long                  // 时间窗口阈值
      - batchWriter: BatchWriter                   // 批量写入器

    方法：
      - offer(request): boolean       // 添加写请求
      - processBatch(): void          // 处理批量请求
      - getPendingCount(): int        // 获取待处理请求数
}

class WriteRequest {
    属性：
      - sequenceNumber: long          // 序列号（单调递增）
      - operationType: OperationType  // 操作类型
      - key: IndexKey                 // 键
      - value: IndexValue             // 值
      - timestamp: long               // 时间戳
      - promise: CompletableFuture<Void> // 完成承诺

    方法：
      - await(): void                 // 同步等待完成
}

写操作流程（同步等待）：
  put(key, value):
    1. 创建 WriteRequest 对象（含 CompletableFuture）
    2. 添加到 WriteRequestQueue
    3. 调用 promise.get() 同步等待
       // 客户端线程阻塞，直到 BatchWriter 处理完成
    4. BatchWriter 完成后 promise.complete()
    5. 客户端返回（此时数据已持久化到 Journal + MemoryTable）
```

### 3.3 批量 I/O 机制

**设计目标**：将多个独立的 Write Item 聚合为一次存储写入，减少系统调用次数。每个请求（PUT / DELETE / BATCH）独立构造一个 Write Item，Journal 忠实记录每个请求。BATCH 操作不拆分，其子操作打包在同一个 Write Item 中保证原子性。

```
class BatchWriter {
    属性：
      - queue: WriteRequestQueue      // 请求队列
      - batchSize: int                // 批量大小（默认 32）
      - timeWindow: long              // 时间窗口（默认 1ms）
      - executor: ScheduledExecutorService // 定时执行器

    方法：
      - start(): void                 // 启动批量写入器
      - stop(): void                  // 停止批量写入器
      - processBatch(): void          // 处理批量请求
}

关键设计约束：
  - 每个请求独立生成一个 Write Item
  - BATCH 操作不拆分，子操作打包在一个 Write Item 中
  - 多个 Write Item 通过 journal.batchWrite()
    一次性写入存储（聚合 I/O）
  - Journal 忠实记录所有操作，支持精确回放
```

### 3.4 BatchWriter 处理流程

BatchWriter 的单次处理流程：(1) 在 timeWindow（默认 1ms）内从队列收集请求，直到达到 batchSize（默认 32）或超时；(2) 每个请求独立构造 Write Item（PUT/DELETE 各一个，BATCH 打包子操作为一个）；(3) 通过 journal.batchWrite() 将所有 Write Item 一次性写入 JOURNAL Chunk（聚合 I/O）；(4) 获取 MemoryTable 写锁，逐条应用所有操作（BATCH 展开子操作逐条应用），检查 Seal 条件；(5) 释放锁后逐一调用 promise.complete() 通知客户端。写入失败时整个 batch 的所有请求收到异常。

```
processBatch()
    │
    ▼
┌─────────────────────────────────────┐
│  1. 收集请求                        │
│     batch = []                      │
│     while (batch.size < batchSize   │
│         && elapsed < timeWindow):   │
│         request = queue.poll()      │
│         if (request != null):       │
│             batch.add(request)      │
└─────────────────────────────────────┘
    │
    ▼
┌─────────────────────────────────────┐
│  2. 每个请求独立构造 Write Item     │
│     writeItems = []                 │
│     for request in batch:           │
│         item = journal.buildWriteItem(│
│             request.type,           │
│             request.key,            │
│             request.value)          │
│         writeItems.add(item)        │
│     // 不合并，保持原始顺序         │
└─────────────────────────────────────┘
    │
    ▼
┌─────────────────────────────────────┐
│  3. 批量写入 Journal                │
│     replayPoints = journal          │
│         .batchWrite(writeItems)     │
│     // 多个 Write Item 一次性写入   │
│     // 聚合 I/O，减少系统调用       │
│     // 每个 Write Item 独立，有自己  │
│     // 的 CRC32 和 4K 对齐          │
└─────────────────────────────────────┘
    │
    ▼
┌─────────────────────────────────────┐
│  4. 批量更新 MemoryTable            │
│     writeLock()                     │
│     for i, request in batch:        │
│         if (request.type == PUT):   │
│             memoryTable.put(        │
│                 request.key,        │
│                 request.value,      │
│                 replayPoints[i])    │
│         else:                       │
│             memoryTable.delete(     │
│                 request.key,        │
│                 replayPoints[i])    │
│     // 检查是否需要 Seal            │
│     if (shouldSeal()): seal()       │
│     writeUnlock()                   │
└─────────────────────────────────────┘
    │
    ▼
┌─────────────────────────────────────┐
│  5. 通知所有客户端                  │
│     for request in batch:           │
│         request.promise.complete()  │
│     // 所有等待的客户端线程被唤醒   │
└─────────────────────────────────────┘
```

## 4. MemoryTable 并发控制

### 4.1 锁策略

MemoryTable 的并发控制详见 [内存表设计](design-memorytable.md) 第 8 节。

```
MemoryTable 状态管理：
  - Active Table：可读写，使用读写锁
  - Sealed Tables：只读，无需锁保护

锁策略：
  - 读锁：共享锁，允许多个读操作并发
  - 写锁：互斥锁，BatchWriter 批量获取一次写锁处理多个请求

关键点：
  - BatchWriter 一次获取写锁，批量更新多个请求
  - 减少锁获取/释放次数，提高效率
  - 读操作仅需读锁访问 Active Table
  - Sealed Tables 完全无锁
```

### 4.2 B+Tree Root 版本管理

B+Tree 的并发控制详见 [KVStore 设计](design-kvstore.md) 第 10 节。

```
双 Root 机制：
  - readRoot (AtomicReference)：读操作使用，已持久化的只读版本
  - dumpRoot：Dump 过程中构建的临时版本

读操作：获取 readRoot 快照后无锁访问
Dump 操作：完成后原子替换 readRoot

优势：
  - 读操作和 Dump 完全并行
  - Root 替换是原子操作
  - 读操作看到一致的状态
```

## 5. 死锁预防

### 5.1 锁获取顺序

**严格遵循锁层次结构**：
```
锁获取顺序（从上到下）：
  1. WriteRequestQueue（无锁，ConcurrentLinkedQueue）
  2. MemoryTableManager 写锁
  3. 单个 MemoryTable 读锁
  4. B+Tree 页面缓存锁（ConcurrentHashMap 分段锁）

关键约束：
  - BatchWriter 是唯一的写入者，自然避免写写死锁
  - 读操作只获取读锁，不会与其他读操作死锁
  - 严格按层次获取锁，避免交叉等待
```

### 5.2 超时机制

所有锁操作都设有超时：MemoryTableManager 写锁 10 秒，MemoryTable 读锁 5 秒。超时后记录警告日志并抛出 OperationTimeoutException，客户端可重试。超时机制防止死锁和长时间阻塞。

```
锁超时配置：
  - MemoryTableManager 写锁超时：10秒
  - MemoryTable 读锁超时：5秒

超时处理：
  - 记录警告日志
  - 抛出 OperationTimeoutException
  - 客户端可重试操作
```

## 6. 性能优化

### 6.1 批量处理策略

批量处理通过三种条件触发：达到数量阈值（batchSize=32）、超过时间窗口（timeWindow=1ms）、系统空闲时主动处理。maxBatchSize=128 限制单次批量上限，防止延迟过大。批量的核心收益是将多个 Write Item 聚合为一次存储 I/O（类似 writev），每个操作仍独立保留在 Journal 中。

```
批量处理触发条件：
  - 数量阈值：达到 batchSize 时立即处理
  - 时间窗口：超过 timeWindow 时强制处理
  - 系统空闲：检测到系统空闲时主动处理

配置参数：
  - batchSize: 32（默认批量大小）
  - timeWindow: 1ms（最大等待时间）
  - maxBatchSize: 128（最大批量大小）
```

### 6.2 无锁数据结构

系统中使用无锁数据结构的场景：WriteRequestQueue 使用 ConcurrentLinkedQueue，性能计数器使用 AtomicLong，B+Tree Root 使用 AtomicReference，Dump 进行标志使用 AtomicBoolean，缓存统计使用 ConcurrentHashMap。这些避免了在高频路径上的锁竞争。

```
使用无锁数据结构的场景：
  - WriteRequestQueue：使用 ConcurrentLinkedQueue
  - 性能计数器：使用 AtomicLong
  - 缓存统计：使用 ConcurrentHashMap
  - 状态标志：使用 AtomicBoolean
  - B+Tree Root：使用 AtomicReference
```

### 6.3 性能特性

并发性能特性：写操作由 BatchWriter 收集后批量处理，延迟为 timeWindow(1ms) + 批量 Journal I/O；读 Active Table 读读并发（读锁共享）；读 Sealed Tables 和 B+Tree 完全并发（无锁）；Dump 完全并发（独立 Root）。N 个写请求的 Write Item 聚合为一次存储 I/O，减少系统调用但每个操作独立记录在 Journal 中。

```
操作类型              并发度           说明
──────────────────────────────────────────────────────
写操作                批量串行化       BatchWriter 单线程处理
读 Active Table       读读并发         读锁共享
读 Sealed Tables      完全并发         无锁
读 B+Tree             完全并发         快照读
Dump 操作             完全并发         独立 Root

关键路径延迟：
  - 写操作：等待 BatchWriter 处理（≤ 1ms + 批量 I/O）
  - 读操作：读锁（纳秒级）+ TreeMap 查询（微秒级）
  - 批量优化：N 个请求的 Write Item 聚合为一次 I/O
```

## 7. 错误处理

### 7.1 批量操作原子性

BatchWriter 的批量写入中，每个操作独立生成一个 Write Item，每个 Write Item 有自己的 CRC32 校验。多个 Write Item 通过一次 I/O 写入存储，但崩溃恢复时每个 Write Item 独立校验，不完整的 Item 被丢弃，完整的 Item 正常回放。

```
批量 I/O 写入保证：
  - 每个操作独立生成一个 Write Item（含 CRC32）
  - 多个 Write Item 聚合为一次存储 I/O
  - 所有 Write Item 写入成功后才更新 MemoryTable
  - 写入失败时所有等待的客户端收到异常
  - 崩溃恢复时逐个 Write Item 校验，部分写入不影响已完成的 Item
```

### 7.2 异常处理

BatchWriter 的异常处理策略：Journal batchWrite 失败时所有请求的 promise.completeExceptionally()，客户端收到异常可重试；MemoryTable 更新失败（理论上不应发生，因为是纯内存操作）触发 stopServing；锁超时记录告警日志并抛出 OperationTimeoutException。

```
异常处理策略：
  - Journal 写入失败：
    - 所有 batch 中的请求 promise.completeExceptionally()
    - 客户端收到异常，可重试
  - MemoryTable 更新失败：
    - 理论上不应该发生（内存操作）
    - 如果发生，触发 stopServing
  - 锁获取超时：
    - 记录警告日志
    - 抛出 OperationTimeoutException
    - 客户端可重试
```

## 8. 监控指标

### 8.1 并发性能指标

并发控制相关的关键监控指标，包括批量大小、处理延迟、队列深度和锁等待时间。

```
关键监控指标：
  - batch.size：批量大小分布
  - batch.latency：批量处理延迟
  - queue.pending：队列等待请求数
  - write.wait.time：写请求等待时间
  - read.lock.wait.time：读锁等待时间
  - merge.ratio：请求合并率
  - snapshot.count：快照创建频率
```

## 9. 配置参数

### 9.1 并发相关配置

并发控制的可配置参数，包括批量大小、时间窗口、锁超时和并发度限制。

```
并发配置参数：
  - concurrency.batchSize: 32                # 批量大小（默认 32）
  - concurrency.timeWindow: 1                # 时间窗口(ms，默认 1ms)
  - concurrency.maxBatchSize: 128            # 最大批量大小
  - concurrency.writeLockTimeout: 10000      # 写锁超时(ms)
  - concurrency.readLockTimeout: 5000        # 读锁超时(ms)
  - concurrency.maxConcurrentReads: 1000     # 最大并发读操作
```

## 10. 测试策略

### 10.1 并发测试场景

并发测试用例列表，覆盖多线程读写、批量合并正确性、超时处理、快照一致性和 Dump 期间并发。

```
并发测试用例：
  - 多线程并发读写测试
  - 批量合并正确性测试
  - 同步等待超时测试
  - 快照一致性测试
  - 性能基准测试（吞吐量、延迟）
  - Dump 期间并发读写测试
```

## 11. 相关文档

- [KVStore 设计](design-kvstore.md)：并发控制架构（第 10 节）
- [内存表设计](design-memorytable.md)：MemoryTable 并发控制（第 8 节）
