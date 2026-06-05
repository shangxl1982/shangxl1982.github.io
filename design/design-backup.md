# 备份与恢复设计

## 1. 概述

本文档定义 KVStore 的备份与恢复机制，包括：
- **全量备份**：备份完整的 Tree 数据和 Journal 数据
- **增量备份**：仅备份 Journal 增量数据
- **Tree 回滚恢复**：利用多版本机制快速恢复
- **完整恢复**：从备份数据重建 KVStore
- **API 接口**：通过 API 暴露备份与恢复能力，供 Service 层调用

**设计原则**：KVStore 作为独立模块，不提供 REST API，仅通过 API 接口暴露备份与恢复能力。Service 层负责将备份与恢复功能通过 REST API 或其他方式对外暴露。

```
┌─────────────────────────────────────────────────────────────┐
│                    备份恢复架构                              │
├─────────────────────────────────────────────────────────────┤
│                                                              │
│  备份类型                                                    │
│  ┌─────────────────────────────────────────────────────┐    │
│  │  全量备份 (Full Backup)                              │    │
│  │  ├── Tree Snapshot (Leaf KV 数据，有序)              │    │
│  │  ├── Journal Data (从 replay point 到截止点)         │    │
│  │  └── Backup Metadata                                 │    │
│  └─────────────────────────────────────────────────────┘    │
│                                                              │
│  ┌─────────────────────────────────────────────────────┐    │
│  │  增量备份 (Incremental Backup)                       │    │
│  │  ├── Journal Data (从上个截止点到当前截止点)          │    │
│  │  └── Backup Metadata                                 │    │
│  └─────────────────────────────────────────────────────┘    │
│                                                              │
│  恢复策略                                                    │
│  ┌─────────────────────────────────────────────────────┐    │
│  │  策略1: Tree 回滚恢复                                │    │
│  │  - 适用场景：当前 Tree 损坏，早期版本完好            │    │
│  │  - 恢复方式：回退到完好版本 + Replay Journal          │    │
│  │  - 恢复速度：快                                      │    │
│  └─────────────────────────────────────────────────────┘    │
│                                                              │
│  ┌─────────────────────────────────────────────────────┐    │
│  │  策略2: 完整恢复                                      │    │
│  │  - 适用场景：所有 Tree 损坏，有备份                   │    │
│  │  - 恢复方式：清空 KVStore + Batch 插入 + Replay      │    │
│  │  - 恢复速度：较慢                                    │    │
│  └─────────────────────────────────────────────────────┘    │
│                                                              │
└─────────────────────────────────────────────────────────────┘
```

## 2. 备份设计原理

### 2.1 多版本机制

Tree 的设计本身就是多版本的，每个 Tree Dump 产生一个新版本：

```
┌─────────────────────────────────────────────────────────────┐
│                    Tree 多版本机制                           │
├─────────────────────────────────────────────────────────────┤
│                                                              │
│  Version 3 (最新)                                           │
│  ┌─────────────────────────────────────────────────────┐    │
│  │  Root → Index Pages → Leaf Pages                    │    │
│  │  Journal Replay Point: Region 5, Offset 10000       │    │
│  │  MNS: 7                                             │    │
│  └─────────────────────────────────────────────────────┘    │
│                          ↑                                   │
│  Version 2                 │ Dump                            │
│  ┌─────────────────────────────────────────────────────┐    │
│  │  Root → Index Pages → Leaf Pages                    │    │
│  │  Journal Replay Point: Region 3, Offset 5000        │    │
│  │  MNS: 5                                             │    │
│  └─────────────────────────────────────────────────────┘    │
│                          ↑                                   │
│  Version 1                 │ Dump                            │
│  ┌─────────────────────────────────────────────────────┐    │
│  │  Root → Index Pages → Leaf Pages                    │    │
│  │  Journal Replay Point: Region 1, Offset 2000        │    │
│  │  MNS: 3                                             │    │
│  └─────────────────────────────────────────────────────┘    │
│                                                              │
│  每个版本 = 一个 Snapshot                                   │
│  可以作为备份的起点                                         │
│                                                              │
└─────────────────────────────────────────────────────────────┘
```

### 2.2 备份数据组成

全量备份包含三部分：tree/ 目录存放 B+Tree 叶节点 KV 数据（有序二进制文件），journal/ 目录存放从 replayPoint 到截止点的 Journal Region 数据，backup.metadata.pb 记录备份元信息。增量备份只包含 journal/ 和 metadata，不含 tree/ 数据。

```
全量备份数据结构：

backup_20240101_120000/
├── backup.metadata.pb          # 备份元数据
├── tree/
│   └── data.bin            # Tree Leaf KV 数据（有序）
└── journal/
    ├── region_1.bin        # Journal Region 1 数据
    ├── region_2.bin        # Journal Region 2 数据
    └── region_3.bin        # Journal Region 3 数据

增量备份数据结构：

backup_20240102_120000_inc/
├── backup.metadata.pb          # 备份元数据
└── journal/
    ├── region_4.bin        # Journal Region 4 数据
    └── region_5.bin        # Journal Region 5 数据
```

### 2.3 备份元数据

备份元数据（backup.metadata.pb）记录备份的唯一标识、类型（FULL/INCREMENTAL）、关联的 Tree 版本和 MNS、Journal 回放起点和截止点、父备份 ID（增量备份）、Tree 统计信息和校验和。恢复时通过元数据确定回放范围和验证数据完整性。

```
backup.metadata.pb 结构：

{
  "backupId": "backup_20240101_120000",
  "backupType": "FULL",              // FULL 或 INCREMENTAL
  "createdAt": 1704096000000,
  "treeVersion": 3,
  "treeMNS": 7,
  "journalReplayPoint": {
    "regionMajor": 5,
    "regionMinor": 0,
    "offset": 10000
  },
  "journalCutoffPoint": {            // Journal 截止点
    "regionMajor": 8,
    "regionMinor": 0,
    "offset": 25000
  },
  "parentBackupId": null,            // 增量备份时指向父备份
  "treeStats": {
    "leafPageCount": 10000,
    "indexPageCount": 100,
    "totalEntries": 500000,
    "height": 3
  },
  "checksum": "abc123..."            // 备份校验和
}
```

## 3. 全量备份流程

### 3.1 流程图

全量备份流程：获取最新 Tree 版本并锁定（防止 GC 释放数据）→ 创建备份目录 → 遍历 B+Tree 叶节点按序写入 KV 数据 → 记录 Journal 截止点 → 复制 Journal Region 数据 → 写入备份元数据 → 解锁版本。备份期间不阻塞读写操作。

```
fullBackup(targetPath)
    │
    ▼
┌─────────────────────────────────────┐
│  1. 获取最新 Tree Version           │
│     targetVersion = getLatestVersion()│
│     // 例如：Version 3              │
└─────────────────────────────────────┘
    │
    ▼
┌─────────────────────────────────────┐
│  2. 锁定 Version                    │
│     lockVersion(targetVersion)      │
│     // 防止 GC 在备份期间释放数据   │
│     // 增加 Version 引用计数        │
└─────────────────────────────────────┘
    │
    ▼
┌─────────────────────────────────────┐
│  3. 创建备份目录                    │
│     backupDir = targetPath +        │
│         "/backup_" + timestamp      │
│     createDirectory(backupDir)      │
└─────────────────────────────────────┘
    │
    ▼
┌─────────────────────────────────────┐
│  4. 备份 Tree 数据                  │
│     treeDataStream = openStream(    │
│         backupDir + "/tree/data.bin")│
│     iterateTree(targetVersion,      │
│         treeDataStream)             │
│     // 仅保存 Leaf KV 数据          │
│     // 数据有序，便于快速恢复       │
└─────────────────────────────────────┘
    │
    ▼
┌─────────────────────────────────────┐
│  5. 记录 Journal 截止点             │
│     cutoffPoint = getCurrentJournal()│
│     // 记录备份发起时的 Journal 位置│
└─────────────────────────────────────┘
    │
    ▼
┌─────────────────────────────────────┐
│  6. 备份 Journal 数据               │
│     replayPoint = getReplayPoint(   │
│         targetVersion)              │
│     copyJournalData(                │
│         from: replayPoint,          │
│         to: cutoffPoint,            │
│         target: backupDir + "/journal")│
└─────────────────────────────────────┘
    │
    ▼
┌─────────────────────────────────────┐
│  7. 写入备份元数据                  │
│     metadata = {                    │
│         backupId, backupType,       │
│         treeVersion, treeMNS,       │
│         journalReplayPoint,         │
│         journalCutoffPoint, ...     │
│     }                               │
│     writeMetadata(backupDir,        │
│         metadata)                   │
└─────────────────────────────────────┘
    │
    ▼
┌─────────────────────────────────────┐
│  8. 解锁 Version                    │
│     unlockVersion(targetVersion)    │
│     // 减少 Version 引用计数        │
└─────────────────────────────────────┘
    │
    ▼
┌─────────────────────────────────────┐
│  9. 返回备份结果                    │
│     return BackupResult(            │
│         backupId, backupDir,        │
│         metadata)                   │
└─────────────────────────────────────┘
```

### 3.2 Tree 数据迭代

遍历指定版本的 B+Tree，深度优先访问所有叶页，按 key 顺序输出每个 Entry（Key Length + Key Data + Value Type + Value Length + Value Data）。有序输出便于恢复时高效地批量插入。只输出 NORMAL 类型的数据（B+Tree 中不存储 Tombstone）。

```
iterateTree(version, outputStream)
    │
    ▼
┌─────────────────────────────────────┐
│  1. 获取 Tree Root                  │
│     root = getRootLocation(version) │
└─────────────────────────────────────┘
    │
    ▼
┌─────────────────────────────────────┐
│  2. 深度优先遍历                    │
│     stack = [root]                  │
│     while (stack not empty):        │
│         location = stack.pop()      │
│         page = readPage(location)   │
│                                      │
│         if (page is LeafPage):      │
│             // 写入 Leaf KV 数据    │
│             for entry in page.entries:│
│                 writeEntry(         │
│                     outputStream,   │
│                     entry.key,      │
│                     entry.value)    │
│         else:  // IndexPage         │
│             // 将子页加入栈         │
│             for child in page.children:│
│                 stack.push(child)   │
└─────────────────────────────────────┘

写入格式（每个 Entry）：
┌─────────────────────────────────────┐
│  Key Length (4 bytes)               │
│  Key Data (variable)                │
│  Value Type (4 bytes)               │
│  Value Length (4 bytes)             │
│  Value Data (variable)              │
└─────────────────────────────────────┘
```

### 3.3 Journal 数据复制

按 Region 粒度复制 Journal 数据：首尾 Region 可能是部分复制（通过 offset 控制），中间 Region 完整复制。这确保备份包含从 Tree 版本的 replayPoint 到备份发起时的所有增量操作。

```
copyJournalData(from, to, targetDir)
    │
    ▼
┌─────────────────────────────────────┐
│  1. 获取 Journal Region 列表        │
│     regions = getRegions(           │
│         from.regionMajor,           │
│         to.regionMajor)             │
└─────────────────────────────────────┘
    │
    ▼
┌─────────────────────────────────────┐
│  2. 逐个 Region 复制                │
│     for region in regions:          │
│         sourcePath = getChunkPath(  │
│             region.chunkId)         │
│         targetPath = targetDir +    │
│             "/region_" + region.major│
│                                      │
│         if (region.major ==         │
│             from.regionMajor):      │
│             // 部分复制             │
│             copyPartial(            │
│                 sourcePath,         │
│                 targetPath,         │
│                 startOffset:        │
│                     from.offset)    │
│         elif (region.major ==       │
│             to.regionMajor):        │
│             // 部分复制             │
│             copyPartial(            │
│                 sourcePath,         │
│                 targetPath,         │
│                 endOffset:          │
│                     to.offset)      │
│         else:                       │
│             // 完整复制             │
│             copyFull(               │
│                 sourcePath,         │
│                 targetPath)         │
└─────────────────────────────────────┘
```

## 4. 增量备份流程

### 4.1 前置条件检查

增量备份前必须验证：父备份存在、父备份是全量备份（增量备份不支持嵌套）、父备份的 Journal 截止点对应的数据未被 GC 回收。任何条件不满足都拒绝创建增量备份。

```
incrementalBackup(targetPath, parentBackupId)
    │
    ▼
┌─────────────────────────────────────┐
│  1. 检查父备份是否存在              │
│     parentBackup = loadBackup(      │
│         parentBackupId)             │
│     if (parentBackup == null):      │
│         throw BackupException(      │
│             "Parent backup not found")│
└─────────────────────────────────────┘
    │
    ▼
┌─────────────────────────────────────┐
│  2. 检查父备份是否为全量备份        │
│     if (parentBackup.type != FULL): │
│         throw BackupException(      │
│             "Parent must be full backup")│
└─────────────────────────────────────┘
    │
    ▼
┌─────────────────────────────────────┐
│  3. 检查父备份是否可访问            │
│     // 父备份的截止点不能被 GC 释放 │
│     cutoffPoint = parentBackup      │
│         .journalCutoffPoint         │
│     if (isJournalGced(cutoffPoint)):│
│         throw BackupException(      │
│             "Parent backup journal  │
│              has been GCed")        │
└─────────────────────────────────────┘
    │
    ▼
    继续增量备份流程...
```

### 4.2 增量备份流程图

增量备份流程比全量简单：不需要遍历 Tree，只复制从父备份截止点到当前截止点之间的 Journal Region 数据。元数据中记录 parentBackupId，恢复时通过备份链追溯到全量备份。

```
incrementalBackup(targetPath, parentBackupId)
    │
    ▼
┌─────────────────────────────────────┐
│  1. 前置条件检查（见上文）          │
└─────────────────────────────────────┘
    │
    ▼
┌─────────────────────────────────────┐
│  2. 创建备份目录                    │
│     backupDir = targetPath +        │
│         "/backup_" + timestamp +    │
│         "_inc"                      │
└─────────────────────────────────────┘
    │
    ▼
┌─────────────────────────────────────┐
│  3. 记录 Journal 截止点             │
│     cutoffPoint = getCurrentJournal()│
└─────────────────────────────────────┘
    │
    ▼
┌─────────────────────────────────────┐
│  4. 复制增量 Journal 数据           │
│     fromPoint = parentBackup        │
│         .journalCutoffPoint         │
│     copyJournalData(                │
│         from: fromPoint,            │
│         to: cutoffPoint,            │
│         target: backupDir + "/journal")│
└─────────────────────────────────────┘
    │
    ▼
┌─────────────────────────────────────┐
│  5. 写入备份元数据                  │
│     metadata = {                    │
│         backupId,                   │
│         backupType: INCREMENTAL,    │
│         parentBackupId,             │
│         journalCutoffPoint, ...     │
│     }                               │
│     writeMetadata(backupDir,        │
│         metadata)                   │
└─────────────────────────────────────┘
    │
    ▼
┌─────────────────────────────────────┐
│  6. 返回备份结果                    │
└─────────────────────────────────────┘
```

## 5. 恢复策略

### 5.1 策略1：Tree 回滚恢复

适用场景：当前 Tree 数据损坏，但保留的版本中有完好版本。

```
rollbackRecovery(targetVersion)
    │
    ▼
┌─────────────────────────────────────┐
│  1. 检查目标版本是否完好            │
│     if (not isVersionHealthy(       │
│             targetVersion)):        │
│         throw RecoveryException(    │
│             "Target version corrupt")│
└─────────────────────────────────────┘
    │
    ▼
┌─────────────────────────────────────┐
│  2. 回退 Tree 版本                  │
│     setTreeVersion(targetVersion)   │
│     // 将当前 Tree 指向目标版本     │
└─────────────────────────────────────┘
    │
    ▼
┌─────────────────────────────────────┐
│  3. 获取 Replay 起点                │
│     replayPoint = getReplayPoint(   │
│         targetVersion)              │
└─────────────────────────────────────┘
    │
    ▼
┌─────────────────────────────────────┐
│  4. Replay Journal                  │
│     journalEntries = readJournal(   │
│         from: replayPoint,          │
│         to: currentJournalEnd)      │
│     for entry in journalEntries:    │
│         applyEntry(entry)           │
│         // PUT/DELETE 操作应用到    │
│         // MemoryTable              │
└─────────────────────────────────────┘
    │
    ▼
┌─────────────────────────────────────┐
│  5. 触发 Dump                       │
│     // 将 Replay 后的数据持久化     │
│     triggerDump()                   │
└─────────────────────────────────────┘
    │
    ▼
┌─────────────────────────────────────┐
│  6. 恢复完成                        │
│     return RecoveryResult(          │
│         recoveredVersion,           │
│         replayedEntries)            │
└─────────────────────────────────────┘
```

**恢复时间分析**：
- Tree 回滚：毫秒级（仅修改指针）
- Journal Replay：取决于 Journal 数据量
- 总恢复时间：通常较快

### 5.2 策略2：完整恢复

适用场景：所有 Tree 版本损坏，但有完整备份。

```
fullRecovery(backupChain)
    │
    ▼
┌─────────────────────────────────────┐
│  1. 验证备份链完整性                │
│     // 全量备份 + 所有增量备份      │
│     validateBackupChain(backupChain)│
│     // 检查每个备份的校验和         │
└─────────────────────────────────────┘
    │
    ▼
┌─────────────────────────────────────┐
│  2. 停止 KVStore 服务               │
│     stopServing()                   │
└─────────────────────────────────────┘
    │
    ▼
┌─────────────────────────────────────┐
│  3. 清空现有数据                    │
│     clearAllData()                  │
│     // 删除所有 Chunk、元数据等     │
└─────────────────────────────────────┘
    │
    ▼
┌─────────────────────────────────────┐
│  4. 恢复 Tree 数据                  │
│     fullBackup = backupChain        │
│         .getFullBackup()            │
│     treeDataStream = openStream(    │
│         fullBackup.path +           │
│         "/tree/data.bin")           │
│                                      │
│     // 使用 Batch 接口插入          │
│     batch = createBatch()           │
│     while (treeDataStream.hasNext()):│
│         entry = treeDataStream.next()│
│         batch.put(entry.key,        │
│             entry.value)            │
│         if (batch.size() >=         │
│             BATCH_THRESHOLD):       │
│             executeBatch(batch)     │
│             batch = createBatch()   │
│     executeBatch(batch)  // 最后一批│
└─────────────────────────────────────┘
    │
    ▼
┌─────────────────────────────────────┐
│  5. Replay Journal 数据             │
│     // 按顺序 Replay 所有备份的     │
│     // Journal 数据                 │
│     for backup in backupChain:      │
│         journalDir = backup.path +  │
│             "/journal"              │
│         for regionFile in           │
│             listFiles(journalDir):  │
│             entries = readJournal(  │
│                 regionFile)         │
│             for entry in entries:   │
│                 applyEntry(entry)   │
└─────────────────────────────────────┘
    │
    ▼
┌─────────────────────────────────────┐
│  6. 触发 Dump                       │
│     triggerDump()                   │
│     // 持久化恢复后的数据           │
└─────────────────────────────────────┘
    │
    ▼
┌─────────────────────────────────────┐
│  7. 重启服务                        │
│     startServing()                  │
└─────────────────────────────────────┘
    │
    ▼
┌─────────────────────────────────────┐
│  8. 恢复完成                        │
│     return RecoveryResult(          │
│         recoveredVersion,           │
│         treeEntries,                │
│         journalEntries)             │
└─────────────────────────────────────┘
```

**恢复时间分析**：
- Tree 数据恢复：取决于数据量，有序数据恢复较快
- Journal Replay：取决于 Journal 数据量
- 总恢复时间：较慢，但可完整恢复

## 6. 备份链管理

### 6.1 备份链概念

备份链由一个全量备份和若干增量备份组成。全量备份包含完整的 Tree 快照 + Journal 数据，每个增量备份只包含上一个截止点之后的 Journal 增量。完整恢复需要整个备份链：先恢复全量备份的 Tree 数据，再按顺序回放所有增量备份的 Journal。

```
备份链示例：

Full Backup 1          Incremental 1        Incremental 2
┌──────────────┐      ┌──────────────┐     ┌──────────────┐
│ Tree Data    │      │ Journal      │     │ Journal      │
│ Journal R1-5 │ ───► │ Journal R6-8 │ ──► │ Journal R9-10│
│ Cutoff: R5   │      │ Cutoff: R8   │     │ Cutoff: R10  │
└──────────────┘      └──────────────┘     └──────────────┘
      │                      │                    │
      └──────────────────────┴────────────────────┘
                        │
                        ▼
              完整恢复需要整个备份链
```

### 6.2 备份链验证

验证备份链完整性：检查链中有全量备份 → 验证每个增量备份的 parentBackupId 指向前一个备份 → 验证 Journal 截止点连续（无间隙）→ 验证每个备份的 checksum 正确。任何验证失败都拒绝恢复。

```
validateBackupChain(backupChain)
    │
    ▼
┌─────────────────────────────────────┐
│  1. 检查备份链完整性                │
│     if (backupChain.isEmpty()):     │
│         return false                │
│                                      │
│     fullBackup = backupChain        │
│         .getFullBackup()            │
│     if (fullBackup == null):        │
│         return false                │
└─────────────────────────────────────┘
    │
    ▼
┌─────────────────────────────────────┐
│  2. 验证备份连续性                  │
│     prevBackup = fullBackup         │
│     for backup in backupChain       │
│             .getIncrementalBackups():│
│                                      │
│         // 检查父备份引用           │
│         if (backup.parentBackupId   │
│             != prevBackup.backupId):│
│             return false            │
│                                      │
│         // 检查 Journal 连续性      │
│         if (backup.journalStartPoint│
│             != prevBackup           │
│             .journalCutoffPoint):   │
│             return false            │
│                                      │
│         prevBackup = backup         │
└─────────────────────────────────────┘
    │
    ▼
┌─────────────────────────────────────┐
│  3. 验证每个备份的校验和            │
│     for backup in backupChain:      │
│         if (not verifyChecksum(     │
│                 backup)):           │
│             return false            │
└─────────────────────────────────────┘
    │
    ▼
    return true
```

## 7. Version 锁定机制

### 7.1 锁定目的

防止 GC 在备份期间释放正在使用的 Version 数据。

```
┌─────────────────────────────────────────────────────────────┐
│                    Version 锁定机制                          │
├─────────────────────────────────────────────────────────────┤
│                                                              │
│  Version 引用计数：                                          │
│  ┌─────────────────────────────────────────────────────┐    │
│  │  Version 3: refCount = 2                            │    │
│  │    - 正常使用: 1                                    │    │
│  │    - 备份锁定: 1                                    │    │
│  │    → GC 不会处理                                    │    │
│  └─────────────────────────────────────────────────────┘    │
│                                                              │
│  ┌─────────────────────────────────────────────────────┐    │
│  │  Version 2: refCount = 1                            │    │
│  │    - 备份锁定: 1                                    │    │
│  │    → GC 不会处理                                    │    │
│  └─────────────────────────────────────────────────────┘    │
│                                                              │
│  ┌─────────────────────────────────────────────────────┐    │
│  │  Version 1: refCount = 0                            │    │
│  │    → 可以被 GC 处理                                 │    │
│  └─────────────────────────────────────────────────────┘    │
│                                                              │
└─────────────────────────────────────────────────────────────┘
```

### 7.2 锁定实现

版本锁定使用 Map<Long, AtomicInteger> 记录每个版本的引用计数。lockVersion() 增加计数，unlockVersion() 减少计数。设有超时机制（默认 1 小时），超时自动解锁防止异常情况下永久阻塞 GC。

```
class VersionLockManager {
    
    属性：
      - versionLocks: Map<Long, AtomicInteger>  // versionId -> refCount
    
    方法：
      - lockVersion(versionId: long): void
      - unlockVersion(versionId: long): void
      - isLocked(versionId: long): boolean
}

lockVersion(versionId)
    │
    ▼
┌─────────────────────────────────────┐
│  增加引用计数                       │
│  versionLocks.computeIfAbsent(      │
│      versionId,                     │
│      k -> new AtomicInteger(0)      │
│  ).incrementAndGet()                │
└─────────────────────────────────────┘

unlockVersion(versionId)
    │
    ▼
┌─────────────────────────────────────┐
│  减少引用计数                       │
│  refCount = versionLocks            │
│      .get(versionId)                │
│      .decrementAndGet()             │
│  if (refCount == 0):                │
│      versionLocks.remove(versionId) │
└─────────────────────────────────────┘
```

### 7.3 GC 与锁定的协调

GC 回收版本前检查锁定状态：如果版本被锁定（引用计数 > 0），跳过该版本的 GC，等待下次调度。这保证了备份期间不会丢失数据，同时不阻塞 GC 对其他未锁定版本的回收。

```
GC 处理前检查：

canGCVersion(versionId)
    │
    ▼
┌─────────────────────────────────────┐
│  检查引用计数                       │
│  refCount = versionLocks            │
│      .getOrDefault(versionId, 0)    │
│  return refCount == 0               │
└─────────────────────────────────────┘

GC 流程修改：

performGC()
    │
    ▼
┌─────────────────────────────────────┐
│  for version in versionsToGC:       │
│      if (not canGCVersion(version)):│
│          skip this version          │
│          continue                   │
│      // 执行 GC 操作                │
│      gcVersion(version)             │
└─────────────────────────────────────┘
```

## 8. 备份计划建议

### 8.1 备份策略

备份策略建议：每周执行一次全量备份 + 每天执行增量备份。全量备份周期取决于数据量（数据量大时延长周期减少备份时间），增量备份周期取决于 RPO（恢复点目标）要求。保留最近 N 个备份链，过期备份自动清理。

```
┌─────────────────────────────────────────────────────────────┐
│                    备份策略建议                              │
├─────────────────────────────────────────────────────────────┤
│                                                              │
│  推荐策略：                                                  │
│  ┌─────────────────────────────────────────────────────┐    │
│  │  全量备份：每周一次                                  │    │
│  │  增量备份：每天一次                                  │    │
│  │  保留周期：全量 4 周，增量 1 周                      │    │
│  └─────────────────────────────────────────────────────┘    │
│                                                              │
│  恢复时间考量：                                              │
│  ┌─────────────────────────────────────────────────────┐    │
│  │  全量备份 Tree 数据：有序，恢复快                    │    │
│  │  Journal 数据：无序，需要 Replay                     │    │
│  │                                                      │    │
│  │  若全量备份与当前跨度过大：                          │    │
│  │    - Journal 数据量大                                │    │
│  │    - Replay 时间长                                   │    │
│  │                                                      │    │
│  │  建议：定期做全量备份，控制增量备份数量              │    │
│  └─────────────────────────────────────────────────────┘    │
│                                                              │
└─────────────────────────────────────────────────────────────┘
```

### 8.2 备份时间窗口

建议在低峰期执行备份以减少对在线流量的影响。虽然备份不阻塞读写，但遍历 Tree 会产生额外的磁盘 I/O 和 CPU 开销。监控备份期间的性能指标（延迟、吞吐量），如果影响过大可限制备份的 I/O 速率。

```
备份时间选择：

┌─────────────────────────────────────────────────────────────┐
│  时间轴                                                      │
├─────────────────────────────────────────────────────────────┤
│                                                              │
│  00:00 ────────────────────────────────────────────── 24:00 │
│    │                                              │          │
│    │  ┌─────────────────────────────────────┐    │          │
│    │  │  低峰期：02:00 - 05:00              │    │          │
│    │  │  推荐备份时间窗口                   │    │          │
│    │  └─────────────────────────────────────┘    │          │
│    │                                              │          │
│    │  备份影响：                                  │          │
│    │  - Tree 迭代：读操作，影响较小              │          │
│    │  - Journal 复制：文件复制，I/O 开销         │          │
│    │  - Version 锁定：阻止 GC，可能增加空间      │          │
│    │                                              │          │
└─────────────────────────────────────────────────────────────┘
```

## 9. API 设计

KVStore 通过 API 接口暴露备份与恢复能力，供 Service 层调用。

### 9.1 备份 API

BackupManager 对外暴露的备份操作 API，供 Service 层调用。

```
interface BackupManager {
    
    全量备份
    fullBackup(targetPath: String): BackupResult
    
    增量备份
    incrementalBackup(
        targetPath: String,
        parentBackupId: String
    ): BackupResult
    
    验证备份
    validateBackup(backupPath: String): boolean
    
    获取备份信息
    getBackupInfo(backupPath: String): BackupMetadata
    
    列出备份链
    listBackupChain(backupPath: String): List<BackupMetadata>
}

class BackupResult {
    backupId: String
    backupPath: String
    backupType: BackupType      // FULL / INCREMENTAL
    createdAt: long
    treeVersion: long
    treeEntryCount: long
    journalEntryCount: long
    sizeInBytes: long
}
```

### 9.2 恢复 API

BackupManager 对外暴露的恢复操作 API，支持 Tree 回滚和完整恢复两种策略。

```
interface RecoveryService {
    
    Tree 回滚恢复
    rollbackRecovery(targetVersion: long): RecoveryResult
    
    完整恢复
    fullRecovery(backupChain: List<String>): RecoveryResult
    
    检查版本健康状态
    checkVersionHealth(version: long): HealthStatus
    
    获取可回滚版本列表
    getRollbackVersions(): List<Long>
}

class RecoveryResult {
    success: boolean
    recoveredVersion: long
    treeEntries: long
    journalEntries: long
    durationMs: long
    errorMessage: String        // 如果失败
}
```

### 9.3 Service 层使用示例

Service 层调用 BackupManager API 实现 REST 接口的示例代码。

```
class KVStoreService {
    
    private KVStore kvStore;
    
    // REST API: POST /backup/full
    public String fullBackup(String targetPath) {
        BackupResult result = kvStore.getBackupManager().fullBackup(targetPath);
        return JSON.stringify(result);
    }
    
    // REST API: POST /backup/incremental
    public String incrementalBackup(String targetPath, String parentBackupId) {
        BackupResult result = kvStore.getBackupManager()
            .incrementalBackup(targetPath, parentBackupId);
        return JSON.stringify(result);
    }
    
    // REST API: GET /backup/{backupId}/validate
    public String validateBackup(String backupPath) {
        boolean valid = kvStore.getBackupManager().validateBackup(backupPath);
        return JSON.stringify(Map.of("valid", valid));
    }
    
    // REST API: POST /recovery/rollback
    public String rollbackRecovery(long targetVersion) {
        RecoveryResult result = kvStore.getBackupManager()
            .rollbackRecovery(targetVersion);
        return JSON.stringify(result);
    }
    
    // REST API: POST /recovery/full
    public String fullRecovery(List<String> backupChain) {
        RecoveryResult result = kvStore.getBackupManager()
            .fullRecovery(backupChain);
        return JSON.stringify(result);
    }
    
    // REST API: GET /recovery/versions
    public String getRollbackVersions() {
        List<Long> versions = kvStore.getBackupManager().getRollbackVersions();
        return JSON.stringify(versions);
    }
}
```

## 10. 配置参数

| 参数 | 默认值 | 描述 |
|------|--------|------|
| backup.lockTimeout | 3600000 | 备份锁定超时时间（ms） |
| backup.batchSize | 10000 | 恢复时 Batch 插入大小 |
| backup.verifyChecksum | true | 是否验证备份校验和 |
| backup.compressData | false | 是否压缩备份数据 |
| recovery.maxJournalReplaySize | 1073741824 | 最大 Journal Replay 大小 |

## 11. 相关文档

- [GC 设计](design-gc.md)：Version 管理与 GC 协调
- [Journal 设计](design-journal.md)：Journal 数据结构
- [B+Tree 元数据设计](design-bplustree-metadata.md)：Version 与 Replay Point
- [错误处理设计](design-error-handling.md)：恢复过程中的错误处理
