# 错误处理设计

## 1. 概述

本文档定义 KVStore 的错误处理机制，包括：
- **异常体系**：统一的异常层级结构
- **stopServing 机制**：系统级故障时的安全停机
- **错误分级**：可恢复错误 vs 不可恢复错误
- **重试策略**：各类操作的重试机制
- **故障恢复**：Dump 失败、元数据写入失败的处理

## 2. 异常体系

### 2.1 异常层级结构

KVStore 定义统一的异常层级，所有内部逻辑生成的异常都基于 `KVStoreException`。

```
┌─────────────────────────────────────────────────────────────┐
│                    异常层级结构                              │
├─────────────────────────────────────────────────────────────┤
│                                                              │
│  java.lang.Exception                                         │
│       │                                                      │
│       └── KVStoreException (根异常)                          │
│              │                                               │
│              ├── KVStoreRuntimeException (运行时异常)         │
│              │      │                                        │
│              │      ├── IllegalStateException                 │
│              │      │      - 状态不正确                      │
│              │      │      - 服务已停止                      │
│              │      │                                        │
│              │      ├── InvalidArgumentException              │
│              │      │      - 参数无效                        │
│              │      │      - 配置错误                        │
│              │      │                                        │
│              │      └── OperationRejectedException           │
│              │             - 内存不足拒绝                    │
│              │             - 服务繁忙拒绝                    │
│              │                                               │
│              ├── StorageException (存储层异常)               │
│              │      │                                        │
│              │      ├── ChunkAllocationException             │
│              │      │      - Chunk 分配失败                  │
│              │      │                                        │
│              │      ├── ChunkWriteException                  │
│              │      │      - Chunk 写入失败                  │
│              │      │                                        │
│              │      └── DiskFullException                    │
│              │             - 磁盘空间不足                    │
│              │                                               │
│              ├── JournalException (Journal 异常)             │
│              │      │                                        │
│              │      ├── JournalWriteException                │
│              │      │      - Journal 写入失败                │
│              │      │                                        │
│              │      └── JournalReplayException               │
│              │             - Journal 回放失败                │
│              │                                               │
│              ├── TreeException (B+Tree 异常)                 │
│              │      │                                        │
│              │      ├── DumpException                        │
│              │      │      - Dump 操作失败                   │
│              │      │                                        │
│              │      └── TreeCorruptException                 │
│              │             - Tree 数据损坏                   │
│              │                                               │
│              ├── MetadataException (元数据异常)              │
│              │      │                                        │
│              │      ├── MetadataWriteException               │
│              │      │      - 元数据写入失败                  │
│              │      │                                        │
│              │      └── MetadataCorruptException             │
│              │             - 元数据损坏                      │
│              │                                               │
│              └── DataIntegrityException (数据完整性异常)     │
│                     │                                        │
│                     ├── CRC32MismatchException               │
│                     │      - CRC32 校验失败                  │
│                     │                                        │
│                     ├── InvalidMagicException                │
│                     │      - Magic 不匹配                    │
│                     │                                        │
│                     └── DataCorruptException                 │
│                            - 数据损坏                        │
│                                                              │
└─────────────────────────────────────────────────────────────┘
```

### 2.2 异常基类定义

#### KVStoreException

```
class KVStoreException extends Exception {
    
    属性：
      - errorCode: ErrorCode        // 错误码
      - recoverable: boolean        // 是否可恢复
      - context: Map<String, Object> // 上下文信息
    
    构造函数：
      - KVStoreException(message)
      - KVStoreException(message, cause)
      - KVStoreException(errorCode, message)
      - KVStoreException(errorCode, message, cause)
    
    方法：
      - getErrorCode(): ErrorCode
      - isRecoverable(): boolean
      - getContext(): Map<String, Object>
      - addContext(key, value): KVStoreException
}
```

#### KVStoreRuntimeException

```
class KVStoreRuntimeException extends RuntimeException {
    
    属性：
      - errorCode: ErrorCode
      - context: Map<String, Object>
    
    // 用于编程错误、状态错误等
    // 不需要调用方显式捕获
}
```

### 2.3 错误码定义

ErrorCode 枚举按模块分段编号：通用错误 1xxx、存储层错误 2xxx、Journal 错误 3xxx、B+Tree 错误 4xxx、元数据错误 5xxx、数据完整性错误 6xxx。每个错误码携带 recoverable 标志，区分可恢复错误（客户端重试）和不可恢复错误（触发 stopServing）。

```
enum ErrorCode {
    
    // 通用错误 (1xxx)
    INTERNAL_ERROR(1001, "Internal error", false),
    INVALID_ARGUMENT(1002, "Invalid argument", true),
    SERVICE_STOPPED(1003, "Service stopped", false),
    OPERATION_REJECTED(1004, "Operation rejected", true),
    
    // 存储层错误 (2xxx)
    CHUNK_ALLOCATION_FAILED(2001, "Chunk allocation failed", false),
    CHUNK_WRITE_FAILED(2002, "Chunk write failed", false),
    DISK_FULL(2003, "Disk full", false),
    CHUNK_NOT_FOUND(2004, "Chunk not found", true),
    
    // Journal 错误 (3xxx)
    JOURNAL_WRITE_FAILED(3001, "Journal write failed", false),
    JOURNAL_REPLAY_FAILED(3002, "Journal replay failed", false),
    JOURNAL_TRUNCATE_FAILED(3003, "Journal truncate failed", true),
    
    // B+Tree 错误 (4xxx)
    DUMP_FAILED(4001, "Dump failed", true),
    TREE_CORRUPT(4002, "Tree corrupt", false),
    PAGE_NOT_FOUND(4003, "Page not found", true),
    
    // 元数据错误 (5xxx)
    METADATA_WRITE_FAILED(5001, "Metadata write failed", false),
    METADATA_CORRUPT(5002, "Metadata corrupt", false),
    
    // 数据完整性错误 (6xxx)
    CRC32_MISMATCH(6001, "CRC32 mismatch", false),
    INVALID_MAGIC(6002, "Invalid magic", false),
    DATA_CORRUPT(6003, "Data corrupt", false);
    
    属性：
      - code: int           // 错误码数值
      - message: String     // 默认消息
      - recoverable: boolean // 是否可恢复
}
```

### 2.4 异常使用示例

展示异常的典型使用方式：抛出异常时通过 addContext() 链式添加上下文信息（chunkId、offset、retryCount 等）便于排查；捕获异常时根据 isRecoverable() 决定是重试恢复还是触发 stopServing。

```
// 抛出异常
throw new JournalWriteException(
    ErrorCode.JOURNAL_WRITE_FAILED,
    "Failed to write journal entry after " + retryCount + " retries")
    .addContext("chunkId", chunkId)
    .addContext("offset", offset)
    .addContext("retryCount", retryCount);

// 捕获并处理异常
try {
    journal.write(entry);
} catch (JournalWriteException e) {
    log.error("Journal write failed: {}", e.getMessage());
    if (e.isRecoverable()) {
        // 尝试恢复
        retryOrFallback(e);
    } else {
        // 触发 stopServing
        stopServing(e);
    }
}
```

### 2.5 异常处理原则

| 原则 | 说明 |
|------|------|
| **统一异常类型** | 所有异常都继承自 KVStoreException |
| **携带上下文** | 异常应包含足够的上下文信息便于排查 |
| **区分可恢复性** | 通过 isRecoverable() 区分是否可恢复 |
| **错误码标准化** | 使用标准错误码，便于监控和告警 |
| **日志记录** | 异常抛出时记录完整日志 |

```
┌─────────────────────────────────────────────────────────────┐
│                    错误处理架构                              │
├─────────────────────────────────────────────────────────────┤
│                                                              │
│  错误来源                     处理策略                       │
│  ┌─────────────────┐         ┌─────────────────┐           │
│  │ Journal 写入    │ ───────►│ 重试 + Rotation │           │
│  └─────────────────┘         └────────┬────────┘           │
│                                       │                     │
│  ┌─────────────────┐         ┌────────▼────────┐           │
│  │ Chunk 分配      │ ───────►│ 重试 + stopServing│          │
│  └─────────────────┘         └────────┬────────┘           │
│                                       │                     │
│  ┌─────────────────┐         ┌────────▼────────┐           │
│  │ Dump 操作       │ ───────►│ 重试 + 回滚     │           │
│  └─────────────────┘         └────────┬────────┘           │
│                                       │                     │
│  ┌─────────────────┐         ┌────────▼────────┐           │
│  │ 元数据写入      │ ───────►│ 重试 + stopServing│          │
│  └─────────────────┘         └────────┬────────┘           │
│                                       │                     │
│                                       ▼                     │
│                              ┌─────────────────┐           │
│                              │   stopServing   │           │
│                              │  (停止所有请求)  │           │
│                              └─────────────────┘           │
│                                                              │
└─────────────────────────────────────────────────────────────┘
```

## 3. stopServing 机制

### 3.1 定义

**stopServing** 是 KVStore 级别的安全停机动作：
- 停止接受新的读写请求
- 拒绝所有正在排队的请求
- 保留已持久化数据的一致性
- 记录错误日志，便于排查问题

### 3.2 触发条件

| 场景 | 触发条件 | 是否可恢复 |
|------|----------|------------|
| Journal 写入失败 | 重试 N 次后仍失败 | ❌ 不可恢复 |
| Chunk 分配失败 | 重试 N 次后仍失败 | ❌ 不可恢复 |
| 元数据写入失败 | 重试 N 次后仍失败 | ❌ 不可恢复 |
| 数据校验失败 | CRC32 校验错误 | ❌ 不可恢复 |
| 磁盘空间不足 | 无法分配新 Chunk | ❌ 不可恢复 |

### 3.3 stopServing 流程

stopServing 是系统级安全停机的最后手段，当不可恢复的错误（如 Journal 写入失败、数据校验失败）发生且重试耗尽时触发。流程：设置 STOPPED 状态 → 记录错误日志 → 拒绝所有新请求 → 清空 WriteRequestQueue（拒绝等待中的请求）→ 等待进行中的 Dump/GC 完成 → 触发告警。

```
stopServing(reason)
    │
    ▼
┌─────────────────────────────────────┐
│  1. 设置状态标志                    │
│     state = STOPPED                 │
│     stopReason = reason             │
└─────────────────────────────────────┘
    │
    ▼
┌─────────────────────────────────────┐
│  2. 记录错误日志                    │
│     log.error("stopServing: " +     │
│         reason)                     │
│     记录堆栈信息                    │
└─────────────────────────────────────┘
    │
    ▼
┌─────────────────────────────────────┐
│  3. 拒绝所有新请求                  │
│     所有 API 调用返回错误：         │
│     "KVStore is stopped"            │
└─────────────────────────────────────┘
    │
    ▼
┌─────────────────────────────────────┐
│  4. 清空 WriteRequestQueue          │
│     // 拒绝队列中所有等待的请求     │
│     for request in queue.drain():   │
│         request.promise             │
│             .completeExceptionally( │
│             new KVStoreStoppedException│
│                 (stopReason))        │
└─────────────────────────────────────┘
    │
    ▼
┌─────────────────────────────────────┐
│  5. 等待进行中的操作完成            │
│     等待 BatchWriter 当前批次完成   │
│     等待 Dump 完成（如果有）        │
│     等待 GC 完成（如果有）          │
└─────────────────────────────────────┘
    │
    ▼
┌─────────────────────────────────────┐
│  6. 触发告警（可选）                │
│     发送告警通知                    │
│     通知运维人员                    │
└─────────────────────────────────────┘
```

### 3.4 状态检查

所有 KVStore 公开 API 的入口都执行状态检查：如果状态为 STOPPED，立即抛出 KVStoreStoppedException 并携带 stopReason，拒绝处理请求。这是一个快速失败机制，避免在异常状态下执行可能损坏数据的操作。

```
所有公开 API 入口：

put(key, value):
    if (state == STOPPED):
        throw new KVStoreStoppedException(stopReason)
    // 正常处理...

get(key):
    if (state == STOPPED):
        throw new KVStoreStoppedException(stopReason)
    // 正常处理...
```

## 4. Journal 写入失败处理

### 4.1 失败场景

| 场景 | 原因 | 处理策略 |
|------|------|----------|
| Chunk 写满 | 当前 Chunk 空间不足 | 自动 Rotation |
| Chunk 分配失败 | 磁盘空间不足或 IO 错误 | 重试 → stopServing |
| IO 错误 | 磁盘故障 | 重试 → stopServing |

### 4.2 处理流程

Journal 写入失败的处理：先尝试 Chunk Rotation（关闭当前 Chunk、分配新 Chunk），成功则重试写入。Rotation 也失败则进入重试循环（最多 maxRetry=3 次，间隔 retryInterval=100ms）。所有重试都失败后触发 stopServing。

```
journal.write(entry)
    │
    ▼
┌─────────────────────────────────────┐
│  1. 尝试写入当前 Chunk              │
│     try:                            │
│         currentChunk.write(entry)   │
│         return success              │
│     catch (ChunkFullException):     │
│         // 进入 Rotation 流程       │
│     catch (IOException e):          │
│         // 进入重试流程             │
└─────────────────────────────────────┘
    │
    ├─── Chunk 写满 ───►
    │
    ▼
┌─────────────────────────────────────┐
│  2. Chunk Rotation                  │
│     rotateChunk()                   │
│     // 关闭当前 Chunk               │
│     // 分配新 Chunk                 │
│     // 更新 Region Index            │
└─────────────────────────────────────┘
    │
    ├─── Rotation 成功 ───► 重试写入
    │
    ├─── Rotation 失败 ───►
    │
    ▼
┌─────────────────────────────────────┐
│  3. 重试机制                        │
│     retryCount = 0                  │
│     while (retryCount < maxRetry):  │
│         try:                        │
│             rotateChunk()           │
│             newChunk.write(entry)   │
│             return success          │
│         catch (Exception e):        │
│             retryCount++            │
│             sleep(retryInterval)    │
└─────────────────────────────────────┘
    │
    ├─── 重试成功 ───► return success
    │
    ├─── 重试失败 ───►
    │
    ▼
┌─────────────────────────────────────┐
│  4. stopServing                     │
│     stopServing(                    │
│         "Journal write failed after │
│          " + maxRetry + " retries") │
└─────────────────────────────────────┘
```

### 4.3 配置参数

| 参数 | 默认值 | 描述 |
|------|--------|------|
| journal.maxRetry | 3 | Journal 写入最大重试次数 |
| journal.retryInterval | 100ms | 重试间隔 |

## 5. Chunk 空间不足处理

### 5.1 双 Buffer 模式

为了不中断写入流程，采用**双 Buffer 模式**：
- **Active Chunk**：当前正在使用的 Chunk
- **Standby Chunk**：预先分配的备用 Chunk

```
┌─────────────────────────────────────────────────────────────┐
│                    双 Buffer 模式                            │
├─────────────────────────────────────────────────────────────┤
│                                                              │
│  正常状态：                                                  │
│  ┌────────────────┐     ┌────────────────┐                 │
│  │ Active Chunk   │     │ Standby Chunk  │                 │
│  │ (正在写入)     │     │ (预先分配)     │                 │
│  │ 使用率: 80%    │     │ 使用率: 0%     │                 │
│  └────────────────┘     └────────────────┘                 │
│                                                              │
│  Active Chunk 写满时：                                       │
│  ┌────────────────┐     ┌────────────────┐                 │
│  │ Old Chunk      │     │ Active Chunk   │ ← 立即切换      │
│  │ (已满，SEALED) │     │ (开始写入)     │                 │
│  │ 使用率: 100%   │     │ 使用率: 0%     │                 │
│  └────────────────┘     └────────────────┘                 │
│         │                      │                            │
│         │                      ▼                            │
│         │              ┌────────────────┐                  │
│         │              │ 分配新 Standby │                  │
│         │              │ Chunk          │                  │
│         │              └────────────────┘                  │
│         │                                                    │
│                                                              │
└─────────────────────────────────────────────────────────────┘
```

### 5.2 切换流程

Chunk 切换流程：检查 Standby Chunk 是否可用（没有则紧急分配）→ 原子切换 Active 和 Standby → 封存旧 Chunk → 异步分配新 Standby。异步分配失败只记录告警，下次切换时会紧急分配。

```
switchChunk()
    │
    ▼
┌─────────────────────────────────────┐
│  1. 检查 Standby Chunk              │
│     if (standbyChunk == null):      │
│         // 紧急分配                 │
│         standbyChunk =              │
│             allocateNewChunk()      │
└─────────────────────────────────────┘
    │
    ▼
┌─────────────────────────────────────┐
│  2. 原子切换                        │
│     oldChunk = activeChunk          │
│     activeChunk = standbyChunk      │
│     standbyChunk = null             │
└─────────────────────────────────────┘
    │
    ▼
┌─────────────────────────────────────┐
│  3. 封存旧 Chunk                    │
│     oldChunk.seal()                 │
│     // 更新元数据                   │
└─────────────────────────────────────┘
    │
    ▼
┌─────────────────────────────────────┐
│  4. 异步分配新 Standby Chunk        │
│     asyncAllocateStandbyChunk()     │
│     // 不阻塞当前写入               │
└─────────────────────────────────────┘
```

### 5.3 异步分配 Standby Chunk

Standby Chunk 的异步分配在后台线程执行，带重试机制。分配失败只记录告警不影响当前写入，下次切换时会紧急同步分配。

```
asyncAllocateStandbyChunk():
    │
    ▼
┌─────────────────────────────────────┐
│  后台线程执行：                     │
│                                      │
│  retryCount = 0                      │
│  while (retryCount < maxRetry):     │
│      try:                            │
│          standbyChunk =              │
│              chunkManager            │
│                  .allocateChunk()    │
│          return                      │
│      catch (Exception e):            │
│          retryCount++                │
│          log.warn(                   │
│              "Allocate standby " +   │
│              "chunk failed, retry")  │
│          sleep(retryInterval)        │
│                                      │
│  // 重试失败，记录告警              │
│  log.error(                          │
│      "Failed to allocate " +         │
│      "standby chunk after " +        │
│      maxRetry + " retries")          │
│  // 下次切换时会紧急分配            │
└─────────────────────────────────────┘
```

### 5.4 配置参数

| 参数 | 默认值 | 描述 |
|------|--------|------|
| chunk.preallocate | true | 是否预分配 Standby Chunk |
| chunk.maxRetry | 3 | Chunk 分配最大重试次数 |
| chunk.retryInterval | 100ms | 重试间隔 |

## 6. Dump 失败处理

### 6.1 失败场景

| 场景 | 原因 | 处理策略 |
|------|------|----------|
| Page 写失败 | IO 错误 | 重试 → Dump 失败 |
| Page 读失败 | 数据损坏或 IO 错误 | 重试 → Dump 失败 |
| 元数据写失败 | IO 错误 | 重试 → Dump 失败 |

### 6.2 失败影响分析

**关键点**：Dump 失败后，已写入的 Page 数据**不生效**。

```
Dump 失败的数据处理：

1. 已写入的 Page 数据：
   - 无法通过任何方式索引（元数据未更新）
   - 不会增加新的 occupancy reference
   - 在 GC 计算时不考虑这部分数据

2. 状态回滚：
   - Tree version 不增加
   - Root location 不更新
   - Journal replay point 不更新

3. 后续处理：
   - 可以重新启动整个 Dump 流程
   - 已写入的 Page 数据会被覆盖或被 GC 回收
```

### 6.3 处理流程

Dump 失败处理流程：每个 Entry 处理都带重试（maxRetry=3），单个 Entry 失败则整个 Dump 标记为失败。失败后不更新元数据、不增加 version、保留 Sealed Tables。如果 autoRetry=true，会调度下次 Dump 重试。已写入的 Page 数据会被后续 GC 回收。

```
dump(sealedTables)
    │
    ▼
┌─────────────────────────────────────┐
│  1. 初始化 Dump 上下文              │
│     dumpContext = new DumpContext() │
│     dumpContext.version =           │
│         currentVersion + 1          │
└─────────────────────────────────────┘
    │
    ▼
┌─────────────────────────────────────┐
│  2. 处理 Entry（带重试）            │
│     for entry in sortedEntries:     │
│         retryCount = 0              │
│         while (retryCount < maxRetry):│
│             try:                    │
│                 processEntry(entry) │
│                 break               │
│             catch (Exception e):    │
│                 retryCount++        │
│                 if (retryCount >= maxRetry):│
│                     throw DumpFailedException│
└─────────────────────────────────────┘
    │
    ├─── 处理成功 ───► 继续
    │
    ├─── 处理失败 ───►
    │
    ▼
┌─────────────────────────────────────┐
│  3. Dump 失败处理                   │
│     log.error("Dump failed", e)     │
│     // 不更新元数据                 │
│     // 不增加 version               │
│     // 保留 sealedTables            │
└─────────────────────────────────────┘
    │
    ▼
┌─────────────────────────────────────┐
│  4. 重启 Dump（可选）               │
│     if (autoRetry):                 │
│         scheduleDumpRetry()         │
│     // 下次 Dump 会重新处理         │
│     // 已写入的数据会被覆盖         │
└─────────────────────────────────────┘
```

### 6.4 Page 写入重试

单个 Page 写入失败时的重试机制：最多重试 maxRetry 次，每次间隔 retryInterval。所有重试失败后抛出 PageWriteFailedException，由 Dump 流程决定是否整体失败。

```
writePage(page)
    │
    ▼
┌─────────────────────────────────────┐
│  重试机制：                         │
│  retryCount = 0                      │
│  while (retryCount < maxRetry):     │
│      try:                            │
│          location = chunkManager    │
│              .writePage(page)        │
│          return location            │
│      catch (IOException e):          │
│          retryCount++                │
│          log.warn(                   │
│              "Write page failed, " + │
│              "retry: " + retryCount) │
│          sleep(retryInterval)        │
│                                      │
│  throw new PageWriteFailedException(│
│      "Failed after " + maxRetry +   │
│      " retries")                    │
└─────────────────────────────────────┘
```

### 6.5 配置参数

| 参数 | 默认值 | 描述 |
|------|--------|------|
| dump.maxRetry | 3 | 单个 Page 写入最大重试次数 |
| dump.retryInterval | 100ms | 重试间隔 |
| dump.autoRetry | true | Dump 失败后是否自动重试 |

## 7. 元数据写入失败处理

### 7.1 元数据类型

| 类型 | 文件 | 失败影响 |
|------|------|----------|
| Tree 元数据 | tree-metadata.pb | 新版本不生效 |
| Journal Region | journal-region.pb | Journal 系统失败 |
| Chunk 元数据 | chunk-metadata.pb | 取决于场景 |

### 7.2 Tree 元数据写入失败

tree-metadata.pb 写入使用临时文件 + fsync + rename 保证原子性。写入失败后进行重试（maxRetry=5）。所有重试失败后不触发 stopServing（与 Journal 元数据不同），因为新版本不生效但旧版本仍然可用，下次 Dump 可以重新尝试。

```
saveTreeMetadata(newVersion, rootLocation, replayPoint)
    │
    ▼
┌─────────────────────────────────────┐
│  1. 写入临时文件                    │
│     tempFile = "tree-metadata.pb.tmp"│
│     writeToFile(tempFile, metadata) │
└─────────────────────────────────────┘
    │
    ▼
┌─────────────────────────────────────┐
│  2. fsync 确保数据落盘              │
│     fsync(tempFile)                 │
└─────────────────────────────────────┘
    │
    ▼
┌─────────────────────────────────────┐
│  3. 原子 rename                     │
│     rename(tempFile, "tree-metadata.pb")│
└─────────────────────────────────────┘
    │
    ├─── 成功 ───► 更新内存状态
    │
    ├─── 失败 ───►
    │
    ▼
┌─────────────────────────────────────┐
│  4. 重试机制                        │
│     retryCount = 0                  │
│     while (retryCount < maxRetry):  │
│         try:                        │
│             // 重新执行 1-3         │
│             return success          │
│         catch (Exception e):        │
│             retryCount++            │
│             sleep(retryInterval)    │
└─────────────────────────────────────┘
    │
    ├─── 重试成功 ───► 更新内存状态
    │
    ├─── 重试失败 ───►
    │
    ▼
┌─────────────────────────────────────┐
│  5. 失败处理                        │
│     // Tree version 不增加          │
│     // Root location 不更新         │
│     // Dump 的数据不生效            │
│     log.error(                      │
│         "Failed to save tree " +    │
│         "metadata after " +         │
│         maxRetry + " retries")      │
│     // 不触发 stopServing           │
│     // 下次 Dump 可以重试           │
└─────────────────────────────────────┘
```

**关键点**：
- Tree 元数据写入失败**不触发 stopServing**
- 新版本不生效，保持旧版本状态
- 下次 Dump 可以重新尝试

### 7.3 Journal Region 元数据写入失败

journal-region.pb 写入失败是严重错误——Journal 系统无法正确轮转 Chunk，后续写入可能丢失数据。重试失败后触发 stopServing。这与 Tree 元数据不同，Tree 元数据失败只是新版本不生效，而 Journal 元数据失败影响数据安全。

```
saveJournalRegionIndex()
    │
    ▼
┌─────────────────────────────────────┐
│  1. 写入临时文件                    │
│     tempFile = "journal-region.pb.tmp"│
│     writeToFile(tempFile, index)    │
└─────────────────────────────────────┘
    │
    ▼
┌─────────────────────────────────────┐
│  2. fsync 确保数据落盘              │
│     fsync(tempFile)                 │
└─────────────────────────────────────┘
    │
    ▼
┌─────────────────────────────────────┐
│  3. 原子 rename                     │
│     rename(tempFile, "journal-region.pb")│
└─────────────────────────────────────┘
    │
    ├─── 成功 ───► return success
    │
    ├─── 失败 ───► 重试
    │
    ▼
┌─────────────────────────────────────┐
│  4. 重试失败后 stopServing          │
│     // Journal 系统失败             │
│     // 无法继续写入                 │
│     stopServing(                    │
│         "Failed to save journal " + │
│         "region index")             │
└─────────────────────────────────────┘
```

**关键点**：
- Journal Region 元数据写入失败**触发 stopServing**
- Journal 系统是数据安全的基础，无法继续运行

### 7.4 Chunk 元数据写入失败

Chunk 元数据写入失败根据场景有不同影响：Chunk 分配失败——无法创建新 Chunk，重试失败后 stopServing；状态更新失败（如 OPEN→SEALED）——可以多次重试；Chunk 删除失败（GC 过程中）——不影响系统运行，可以重试。

```
saveChunkMetadata(chunk)
    │
    ▼
┌─────────────────────────────────────┐
│  场景分析：                         │
│                                      │
│  1. Chunk 分配失败：                │
│     - 无法创建新 Chunk              │
│     - 重试失败后 stopServing        │
│                                      │
│  2. Chunk 状态更新失败：            │
│     - 如 OPEN → SEALED              │
│     - 可以多次重试                  │
│     - 重试失败后 stopServing        │
│                                      │
│  3. Chunk 删除失败：                │
│     - GC 流程中                     │
│     - 可以重试                      │
│     - 不影响系统运行                │
└─────────────────────────────────────┘
```

### 7.5 配置参数

| 参数 | 默认值 | 描述 |
|------|--------|------|
| metadata.maxRetry | 5 | 元数据写入最大重试次数 |
| metadata.retryInterval | 100ms | 重试间隔 |

## 8. 错误分级总结

```
┌─────────────────────────────────────────────────────────────┐
│                      错误分级表                              │
├───────────────┬───────────────────┬────────────────────────┤
│ 错误类型      │ 处理策略          │ 是否 stopServing       │
├───────────────┼───────────────────┼────────────────────────┤
│ Journal 写入  │ 重试 + Rotation   │ 重试失败后是           │
│ Chunk 写满    │ 自动 Rotation     │ 否                     │
│ Chunk 分配    │ 重试              │ 重试失败后是           │
│ Page 写入     │ 重试              │ 否（Dump 失败）        │
│ Page 读取     │ 重试              │ 否（Dump 失败）        │
│ Dump 失败     │ 重启 Dump         │ 否                     │
│ Tree 元数据   │ 重试              │ 否                     │
│ Journal 元数据│ 重试              │ 重试失败后是           │
│ Chunk 元数据  │ 重试              │ 取决于场景             │
│ 数据校验      │ 无                │ 是                     │
└───────────────┴───────────────────┴────────────────────────┘
```

## 9. 监控与告警

### 9.1 错误指标

| 指标 | 描述 |
|------|------|
| journal.write.errors | Journal 写入错误次数 |
| journal.write.retries | Journal 写入重试次数 |
| chunk.allocate.errors | Chunk 分配错误次数 |
| dump.errors | Dump 失败次数 |
| metadata.write.errors | 元数据写入错误次数 |
| stopServing.count | stopServing 触发次数 |

### 9.2 告警规则

| 告警 | 条件 | 级别 |
|------|------|------|
| Journal 写入重试频繁 | 1分钟内重试 > 10 次 | WARNING |
| Chunk 分配失败 | 分配失败 > 1 次 | ERROR |
| Dump 失败 | Dump 失败 > 1 次 | ERROR |
| stopServing 触发 | stopServing 触发 | CRITICAL |

## 10. 相关文档

- [数据完整性保护](design-data-integrity.md)：Write Item 设计、CRC32 校验
- [Journal 设计](design-journal.md)：Journal 写入流程
- [GC 设计](design-gc.md)：Chunk 生命周期管理
- [Config 框架](design-config.md)：配置参数管理
