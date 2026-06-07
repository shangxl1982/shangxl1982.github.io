

# HyperKVStore

## 项目简介

HyperKVStore 是一个高性能的键值存储系统，采用 LSM (Log-Structured Merge-tree) 树结构与 B+Tree 持久化存储相结合的设计，支持高吞吐量的写入操作和高效的读写性能。

## 模块结构

```
hyperstore/
├── lsmplus-api/          # API 模型定义
├── lsmplus-config/       # 配置管理
├── lsmplus-exception/    # 异常定义
├── lsmplus-kvstore/      # 核心 KVStore 实现
├── lsmplus-monitoring/  # 监控与指标
├── lsmplus-service/       # 服务层
├── lsmplus-storage/      # 存储层
├── lsmplus-utils/        # 工具类
└── tools/               # 诊断工具
```

## 核心特性

- **LSM + B+Tree 混合架构**：内存表使用 LSM 结构，持久化使用 B+Tree
- **写前日志 (WAL)**：通过 Journal 实现数据的持久化和崩溃恢复
- **自动压缩**: 支持自动 dump 和合并操作
- **多版本管理**: 支持多版本数据和快照读
- **GC 支持**: 自动垃圾回收无效数据
- **完整监控**: 性能指标和健康检查

## 快速开始

### 构建项目

```bash
./gradlew build
```

### 运行测试

```bash
./gradlew test
```

### 基本使用

```java
import org.hyperkv.lsmplus.core.KVStore;
import org.hyperkv.lsmplus.api.model.IndexKey;
import org.hyperkv.lsmplus.api.model.IndexValue;

// 创建 KVStore 实例
KVStore store = new KVStore(new File("/data/kvstore"));
store.start();

// 写入数据
IndexKey key = IndexKey.orderedBytes("hello".getBytes());
IndexValue value = IndexValue.normal("world".getBytes());
store.put(key, value);

// 读取数据
IndexValue result = store.get(key);

// 范围查询
store.rangeQuery(startKey, endKey);

// 关闭
store.shutdown();
```

## 配置参数

| 参数 | 默认值 | 说明 |
|------|--------|------|
| memoryTableMaxSize | 134217728 | 内存表最大大小 (128MB) |
| maxSealedTables | 10 | 最大封存的表数量 |
| leafPageMaxSize | 8192 | 叶页面最大大小 |
| indexPageMaxSize | 65536 | 索引页面最大大小 |

## 测试覆盖

项目包含了完整的单元测试和集成测试，覆盖以下场景：

- B+Tree 完整集成测试
- 内存表管理和密封机制
- Journal 写入和回放
- 存储层读写操作
- 并发控制和快照读
- GC 和空间回收

## 许可证

Apache License 2.0