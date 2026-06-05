# 包结构设计

## 1. 概述

本文档定义 KVStore 系统的顶层包结构，采用 `org.hyperkv.lsmplus` 作为根包名，实现模块化、层次化的代码组织。

**设计原则**：
- **模块化**：按功能模块划分包结构
- **层次化**：从抽象到具体的层次关系
- **可扩展性**：支持未来功能扩展
- **命名规范**：遵循 Java 包命名规范

## 2. 顶层包结构

### 2.1 根包定义

根包 org.hyperkv.lsmplus 下按功能模块划分：api（公共接口）、core（核心实现含 B+Tree 和并发控制）、storage（Chunk 和元数据管理）、memory（MemoryTable）、journal（操作日志）、config（配置管理）、monitoring（监控）、backup（备份恢复）、service（REST/gRPC 服务）、utils（工具类）、exception（异常体系）。

```
org.hyperkv.lsmplus
├── api              # 公共 API 接口
├── core             # 核心实现模块
├── storage          # 存储层模块
├── memory           # 内存管理模块
├── journal          # 日志模块
├── config           # 配置管理模块
├── monitoring       # 监控模块
├── backup           # 备份恢复模块
├── service          # 服务层模块
├── utils            # 工具类模块
└── exception        # 异常类模块
```

### 2.2 详细包结构

详细包结构展示了每个模块的类文件组织。核心设计：api 包定义稳定接口隔离实现细节；core.bplustree 包含 BPlusTree、PageManager、PageCache、WriteBuffer 和 page 子包；storage 包含 ChunkManager、ChunkType、ChunkHeader、WriteItem、SegmentLocation 和 gc 子包；journal.entry 包含 OperationType 枚举（4 bytes 对齐）。

```
org.hyperkv.lsmplus
│
├── api                          # 公共 API 接口
│   ├── KVStoreApi.java         # KVStore 主接口
│   ├── ConfigApi.java          # 配置管理接口
│   ├── MonitoringApi.java      # 监控接口
│   ├── BackupApi.java          # 备份恢复接口
│   └── model                   # 数据模型
│       ├── IndexKey.java       # 索引键
│       ├── IndexValue.java     # 索引值
│       ├── OperationType.java  # 操作类型
│       └── Snapshot.java       # 快照
│
├── core                         # 核心实现模块
│   ├── KVStore.java            # KVStore 主实现类
│   ├── KVStoreImpl.java        # KVStore 实现
│   ├── concurrency             # 并发控制
│   │   ├── WriteRequestQueue.java
│   │   ├── BatchWriter.java
│   │   └── SnapshotManager.java
│   └── bplustree               # B+树实现
│       ├── BPlusTree.java
│       ├── PageManager.java
│       ├── PageCache.java         # 页面缓存（LRU，key 为 SegmentLocation）
│       ├── WriteBuffer.java       # 写缓存
│       └── page                # 页面相关
│           ├── Page.java
│           ├── LeafPage.java
│           ├── IndexPage.java
│           └── PageType.java
│
├── storage                      # 存储层模块
│   ├── ChunkManager.java       # Chunk 管理器
│   ├── Chunk.java              # 存储块
│   ├── ChunkType.java          # Chunk 类型枚举（INDEX/LEAF/JOURNAL）
│   ├── ChunkHeader.java        # Chunk Header 结构（4096 bytes）
│   ├── WriteItem.java          # Write Item 封装格式
│   ├── SegmentLocation.java    # 段位置信息
│   ├── metadata                # 元数据管理
│   │   ├── TreeMetadata.java
│   │   ├── TreeMetadataManager.java
│   │   └── ChunkMetadata.java
│   └── gc                      # 垃圾回收
│       ├── GarbageCollector.java
│       ├── ChunkLifecycleManager.java
│       └── OccupancyTracker.java
│
├── memory                       # 内存管理模块
│   ├── MemoryTableManager.java # 内存表管理器
│   ├── MemoryTable.java        # 内存表
│   ├── WriteBuffer.java        # 写缓存
│   └── cache                   # 缓存管理
│       ├── PageCache.java
│       └── CachePolicy.java
│
├── journal                      # 日志模块
│   ├── Journal.java            # 日志管理器
│   ├── JournalWriter.java      # 日志写入器
│   ├── JournalReplayPoint.java # 回放点
│   ├── JournalRegion.java      # 日志区域
│   └── entry                   # 日志条目
│       ├── JournalEntry.java
│       ├── BatchEntry.java
│       ├── OperationEntry.java
│       └── OperationType.java  # 操作类型枚举（PUT/DELETE/BATCH，4 bytes）
│
├── config                       # 配置管理模块
│   ├── ConfigManager.java      # 配置管理器
│   ├── Config.java             # 配置类
│   ├── DynamicConfig.java      # 动态配置
│   └── listener                # 配置监听器
│       ├── ConfigListener.java
│       └── ConfigChangeEvent.java
│
├── monitoring                   # 监控模块
│   ├── MetricsRegistry.java    # 指标注册器
│   ├── PerformanceCounter.java # 性能计数器
│   ├── HealthChecker.java      # 健康检查
│   └── metric                  # 指标定义
│       ├── CounterMetric.java
│       ├── GaugeMetric.java
│       └── HistogramMetric.java
│
├── backup                       # 备份恢复模块
│   ├── BackupManager.java      # 备份管理器
│   ├── RecoveryManager.java    # 恢复管理器
│   ├── FullBackup.java         # 全量备份
│   ├── IncrementalBackup.java  # 增量备份
│   └── strategy                # 备份策略
│       ├── BackupStrategy.java
│       └── RecoveryStrategy.java
│
├── service                      # 服务层模块
│   ├── KVStoreService.java     # KVStore 服务
│   ├── RestApiService.java     # REST API 服务
│   ├── GrpcService.java        # gRPC 服务
│   └── handler                 # 请求处理器
│       ├── PutHandler.java
│       ├── GetHandler.java
│       ├── DeleteHandler.java
│       └── BatchHandler.java
│
├── utils                        # 工具类模块
│   ├── Serializer.java         # 序列化工具
│   ├── ProtobufUtil.java       # Protobuf 工具
│   ├── CRC32Util.java          # CRC32 工具
│   ├── HashUtil.java           # 哈希工具
│   ├── CompressionUtil.java    # 压缩工具
│   └── validation              # 验证工具
│       ├── Validator.java
│       └── ValidationRule.java
│
└── exception                    # 异常类模块
    ├── KVStoreException.java   # 根异常
    ├── KVStoreRuntimeException.java
    ├── StorageException.java   # 存储异常
    ├── JournalException.java   # 日志异常
    ├── TreeException.java      # 树异常
    └── ErrorCode.java          # 错误码枚举
```

## 3. 包依赖关系

### 3.1 依赖层次结构

依赖流向从上到下：service → core → memory/journal → storage → utils。api/utils/exception 被所有模块依赖。严格禁止逆向依赖（如 storage 不能依赖 core），避免循环引用。config 和 monitoring 被需要配置和监控的模块横向依赖。

```
依赖流向：上层 → 下层

service (服务层)
    ↓
core (核心层)
    ↓
memory (内存层) → journal (日志层)
    ↓
storage (存储层)
    ↓
utils (工具层)
```

### 3.2 模块间依赖关系

模块依赖图：service 依赖 core/config/monitoring/backup；core 依赖 memory/storage；memory 依赖 journal；所有模块依赖 api/utils/exception。这种层次结构确保底层模块可以独立测试和复用。

```
模块依赖图：

service
├── core
│   ├── memory
│   │   └── journal
│   └── storage
├── config
├── monitoring
└── backup

api (被所有模块依赖)
utils (被所有模块依赖)
exception (被所有模块依赖)
```

## 4. 包命名规范

### 4.1 包名规则

包命名规范：全部小写字母，点分隔符，避免下划线，按功能模块划分。示例：org.hyperkv.lsmplus.core.bplustree.page（B+Tree 页面相关类）、org.hyperkv.lsmplus.storage.metadata（存储元数据管理类）。

```
包名规则：
- 全部小写字母
- 使用点分隔符
- 避免使用下划线
- 按功能模块划分

示例：
org.hyperkv.lsmplus.core.bplustree.page
org.hyperkv.lsmplus.storage.metadata
```

### 4.2 类命名规范

类命名规范：使用驼峰命名法；接口可选 I 前缀（如 KVStoreApi）；抽象类以 Abstract 开头（如 AbstractPage）；实现类以 Impl 结尾（如 KVStoreImpl）；枚举类直接使用概念名称（如 OperationType、ChunkStatus）。

```
类命名规则：
- 使用驼峰命名法
- 接口以 "I" 开头（可选）
- 抽象类以 "Abstract" 开头
- 实现类以 "Impl" 结尾
- 枚举类以 "Enum" 结尾（可选）

示例：
KVStoreApi (接口)
KVStoreImpl (实现类)
AbstractPage (抽象类)
OperationType (枚举)
```

## 5. 模块职责划分

### 5.1 api 模块

**职责**：定义公共接口和数据模型
- 提供稳定的 API 接口
- 定义数据模型和枚举类型
- 隔离实现细节

### 5.2 core 模块

**职责**：核心业务逻辑实现
- KVStore 主实现
- 并发控制机制
- B+树算法实现

### 5.3 storage 模块

**职责**：持久化存储管理
- Chunk 生命周期管理
- 元数据持久化
- 垃圾回收机制

### 5.4 memory 模块

**职责**：内存数据管理
- MemoryTable 管理
- 写缓存优化
- 页面缓存管理

### 5.5 journal 模块

**职责**：操作日志管理
- 日志写入和回放
- 日志区域管理
- 崩溃恢复支持

### 5.6 config 模块

**职责**：配置管理
- 配置加载和解析
- 动态配置更新
- 配置监听机制

### 5.7 monitoring 模块

**职责**：系统监控
- 性能指标采集
- 健康状态检查
- 监控数据暴露

### 5.8 backup 模块

**职责**：备份和恢复
- 全量和增量备份
- 数据恢复机制
- 备份策略管理

### 5.9 service 模块

**职责**：服务层实现
- REST API 服务
- gRPC 服务
- 请求处理和路由

### 5.10 utils 模块

**职责**：通用工具类
- 序列化和反序列化
- 数据校验和压缩
- 通用算法实现

### 5.11 exception 模块

**职责**：异常体系定义
- 统一的异常层级
- 错误码管理
- 异常处理策略

## 6. 构建配置

### 6.1 Maven 模块结构

Maven 多模块构建：父 POM 定义公共依赖和插件，子模块对应各个包（lsmplus-api、lsmplus-core、lsmplus-storage 等）。这样每个模块可以独立构建、测试和发布，也支持选择性引入（如只引入 lsmplus-api 用于客户端）。

```xml
<!-- pom.xml -->
<project>
    <groupId>org.hyperkv</groupId>
    <artifactId>lsmplus</artifactId>
    <version>1.0.0</version>
    <packaging>pom</packaging>
    
    <modules>
        <module>lsmplus-api</module>
        <module>lsmplus-core</module>
        <module>lsmplus-storage</module>
        <module>lsmplus-memory</module>
        <module>lsmplus-journal</module>
        <module>lsmplus-config</module>
        <module>lsmplus-monitoring</module>
        <module>lsmplus-backup</module>
        <module>lsmplus-service</module>
        <module>lsmplus-utils</module>
        <module>lsmplus-exception</module>
    </modules>
</project>
```

### 6.2 依赖管理

Maven 依赖管理示例：core 模块依赖 api 和 memory 模块，通过 ${project.version} 统一版本号。外部依赖（Protobuf、JUnit 等）在父 POM 的 dependencyManagement 中统一管理版本，子模块只声明 groupId 和 artifactId。

```xml
<!-- 依赖关系示例 -->
<dependencies>
    <!-- core 模块依赖 -->
    <dependency>
        <groupId>org.hyperkv</groupId>
        <artifactId>lsmplus-api</artifactId>
        <version>${project.version}</version>
    </dependency>
    <dependency>
        <groupId>org.hyperkv</groupId>
        <artifactId>lsmplus-memory</artifactId>
        <version>${project.version}</version>
    </dependency>
    <!-- ... 其他依赖 -->
</dependencies>
```

## 7. 开发规范

### 7.1 包访问控制

访问控制原则：包内类默认使用包级可见性（不加 public），对外暴露的 API 使用 public。内部实现类（如 WriteBuffer、PageCache）不暴露给其他模块。这减少了模块间的耦合，允许内部重构不影响外部使用。

```
访问控制原则：
- 包内类默认使用包级可见性
- 对外暴露的API使用public修饰
- 内部实现使用protected或包级可见性
- 避免使用public修饰内部实现类
```

### 7.2 代码组织规范

代码组织原则：每个包对应一个明确的功能模块，类文件按功能分组存放，避免循环依赖，保持包结构扁平化（避免过深的嵌套层次）。新增功能应先确定归属模块，再在对应包中添加类。

```
代码组织原则：
- 每个包对应一个明确的功能模块
- 类文件按功能分组存放
- 避免循环依赖
- 保持包结构的扁平化
```

## 8. 扩展性考虑

### 8.1 未来扩展点

可扩展的模块：插件系统支持自定义存储引擎（替换默认的 Chunk 实现）；协议扩展支持 Avro/FlatBuffers 等序列化格式；监控扩展支持 Datadog/Grafana 等后端；备份扩展支持 S3/GCS 等云存储。

```
可扩展的模块：
- 插件系统：支持自定义存储引擎
- 协议扩展：支持更多序列化协议
- 监控扩展：支持更多监控后端
- 备份扩展：支持云存储备份
```

### 8.2 版本兼容性

版本兼容策略：api 模块保持向后兼容（不删除已有接口方法）；内部实现模块可自由变更；使用语义化版本号（major.minor.patch）管理 API 变更；提供迁移工具和文档帮助用户升级。

```
版本兼容策略：
- api 模块保持向后兼容
- 内部实现可以自由变更
- 使用版本号管理API变更
- 提供迁移工具和文档
```