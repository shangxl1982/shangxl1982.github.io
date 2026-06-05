# 测试策略设计

## 1. 概述

本文档定义 KVStore 的测试策略，包括单元测试、集成测试、性能测试等，确保代码质量和系统稳定性。

**测试目标**：
- **功能正确性**：确保所有功能按预期工作
- **性能稳定性**：验证系统在不同负载下的表现
- **数据一致性**：保证数据在各种场景下的一致性
- **容错能力**：验证系统的错误处理和恢复能力

## 2. 测试分层架构

### 2.1 测试金字塔

测试分三层：底层是大量的单元测试（覆盖所有代码路径，运行快），中间是集成测试（验证模块间交互和外部依赖，运行中速），顶层是少量端到端测试（验证核心业务流程，运行慢）。单元测试占比最大，保证快速反馈。

```
┌─────────────────────────────────────────────────────────────┐
│                    测试金字塔                                │
├─────────────────────────────────────────────────────────────┤
│                                                              │
│  ┌─────────────────────────────────────────────────────┐    │
│  │               端到端测试 (E2E)                       │    │
│  │  - 数量少，覆盖核心业务流程                          │    │
│  │  - 验证系统整体功能                                  │    │
│  │  - 运行较慢                                          │    │
│  └─────────────────────────────────────────────────────┘    │
│                                                              │
│  ┌─────────────────────────────────────────────────────┐    │
│  │               集成测试 (Integration)                 │    │
│  │  - 验证模块间交互                                    │    │
│  │  - 测试数据库、文件系统等外部依赖                    │    │
│  │  - 运行速度中等                                      │    │
│  └─────────────────────────────────────────────────────┘    │
│                                                              │
│  ┌─────────────────────────────────────────────────────┐    │
│  │               单元测试 (Unit)                        │    │
│  │  - 数量多，覆盖所有代码路径                          │    │
│  │  - 隔离测试单个类或方法                              │    │
│  │  - 运行速度快                                        │    │
│  └─────────────────────────────────────────────────────┘    │
│                                                              │
└─────────────────────────────────────────────────────────────┘
```

### 2.2 测试目录结构

测试代码按类型组织：unit/（按模块分子目录）、integration/（KVStore 整体、Dump 流程、恢复流程）、performance/（benchmark、stress、longevity）、e2e/（API 测试、场景测试）。与 src/main/java 的包结构对应。

```
src/
├── main/
│   └── java/
│       └── kvstore/
└── test/
    └── java/
        └── kvstore/
            ├── unit/                    # 单元测试
            │   ├── bplustree/           # B+Tree 单元测试
            │   ├── memorytable/         # 内存表单元测试
            │   ├── journal/             # Journal 单元测试
            │   ├── storage/             # 存储层单元测试
            │   └── utils/               # 工具类单元测试
            ├── integration/             # 集成测试
            │   ├── kvstore/             # KVStore 集成测试
            │   ├── dump/                # Dump 流程集成测试
            │   └── recovery/            # 恢复流程集成测试
            ├── performance/             # 性能测试
            │   ├── benchmark/           # 基准测试
            │   ├── stress/              # 压力测试
            │   └── longevity/           # 长时间运行测试
            └── e2e/                     # 端到端测试
                ├── api/                 # API 测试
                └── scenario/            # 场景测试
```

## 3. 单元测试设计

### 3.1 测试框架选择

测试框架组合：JUnit 5（测试运行和生命周期管理）、Mockito（依赖 Mock）、AssertJ（流式断言，可读性好）、TestContainers（集成测试中的容器化依赖）。Maven/Gradle 管理测试依赖，与生产依赖分离。

```
测试框架：
  - JUnit 5：测试运行框架
  - Mockito：Mock 框架
  - AssertJ：断言库
  - TestContainers：容器化测试（集成测试）

依赖管理：
  - Maven 或 Gradle 管理测试依赖
  - 分离测试和生产依赖
```

### 3.2 核心类单元测试

#### 3.2.1 MemoryTable 测试

MemoryTable 单元测试覆盖：put/get 基本读写、Tombstone 标记和查询、范围查询结果验证、Seal 操作后的只读行为、Seal 阈值触发、写入 SEALED 表时抛异常。使用工厂方法创建测试数据。

```java
class MemoryTableTest {
    
    @Test
    void testPutAndGet() {
        // 测试插入和查询功能
        MemoryTable table = new MemoryTable(64 * 1024 * 1024);
        IndexKey key = createKey("test");
        IndexValue value = createValue("data");
        
        table.put(key, value, createReplayPoint());
        IndexValue result = table.get(key);
        
        assertThat(result).isEqualTo(value);
    }
    
    @Test
    void testTombstone() {
        // 测试删除标记功能
        MemoryTable table = new MemoryTable(64 * 1024 * 1024);
        IndexKey key = createKey("test");
        
        table.delete(key, createReplayPoint());
        IndexValue result = table.get(key);
        
        assertThat(result.isTombstone()).isTrue();
    }
    
    @Test
    void testRangeQuery() {
        // 测试范围查询
        MemoryTable table = new MemoryTable(64 * 1024 * 1024);
        // 插入有序数据
        for (int i = 0; i < 100; i++) {
            table.put(createKey("key" + i), createValue("value" + i), createReplayPoint());
        }
        
        List<Map.Entry<IndexKey, IndexValue>> results = 
            table.rangeQuery(createKey("key10"), createKey("key20"));
        
        assertThat(results).hasSize(11); // key10 到 key20
    }
    
    @Test
    void testSealOperation() {
        // 测试封存操作
        MemoryTable table = new MemoryTable(64 * 1024); // 小阈值便于测试
        // 插入数据直到达到阈值
        while (!table.shouldSeal()) {
            table.put(createRandomKey(), createRandomValue(), createReplayPoint());
        }
        
        table.seal();
        assertThat(table.isSealed()).isTrue();
        
        // 验证封存后不能写入
        assertThrows(IllegalStateException.class, () -> {
            table.put(createKey("new"), createValue("data"), createReplayPoint());
        });
    }
}
```

#### 3.2.2 B+Tree 测试

B+Tree 单元测试覆盖：insert/search 基本操作、页面分裂（使用小 maxSize 触发）、分裂后数据完整性验证、范围查询结果和顺序验证。使用格式化 key（如 key%03d）确保字典序与数值序一致。

```java
class BPlusTreeTest {
    
    @Test
    void testInsertAndSearch() {
        // 测试插入和搜索功能
        BPlusTree tree = new BPlusTree(64);
        IndexKey key = createKey("test");
        IndexValue value = createValue("data");
        
        tree.insert(key, value);
        IndexPair result = tree.search(key);
        
        assertThat(result.getValue()).isEqualTo(value);
    }
    
    @Test
    void testPageSplit() {
        // 测试页面分裂
        BPlusTree tree = new BPlusTree(512); // 小 leafPageMaxSize 便于测试分裂
        
        // 插入足够数据触发分裂
        for (int i = 0; i < 10; i++) {
            tree.insert(createKey("key" + i), createValue("value" + i));
        }
        
        // 验证树结构正确
        assertThat(tree.getHeight()).isGreaterThan(1);
        
        // 验证所有数据可访问
        for (int i = 0; i < 10; i++) {
            IndexPair result = tree.search(createKey("key" + i));
            assertThat(result).isNotNull();
        }
    }
    
    @Test
    void testRangeQuery() {
        // 测试范围查询
        BPlusTree tree = new BPlusTree(64);
        
        // 插入有序数据
        for (int i = 0; i < 100; i++) {
            tree.insert(createKey(String.format("key%03d", i)), createValue("value" + i));
        }
        
        List<IndexPair> results = tree.rangeQuery(createKey("key010"), createKey("key020"));
        
        assertThat(results).hasSize(11);
        assertThat(results.get(0).getKey()).isEqualTo(createKey("key010"));
        assertThat(results.get(10).getKey()).isEqualTo(createKey("key020"));
    }
}
```

#### 3.2.3 Journal 测试

Journal 单元测试覆盖：write/replay 往返测试（写入后回放，验证 MemoryTable 中的数据）、Batch 操作原子性测试（Batch 内的 PUT 和 DELETE 正确回放）。使用临时目录创建 Journal 实例。

```java
class JournalTest {
    
    @Test
    void testWriteAndReplay() {
        // 测试写入和回放功能
        Journal journal = createJournal();
        MemoryTableManager tableManager = createTableManager();
        
        // 写入操作
        JournalReplayPoint point1 = journal.write(OperationType.PUT, 
            createKey("key1"), createValue("value1"));
        JournalReplayPoint point2 = journal.write(OperationType.PUT, 
            createKey("key2"), createValue("value2"));
        
        // 回放操作
        journal.replayFrom(point1, tableManager);
        
        // 验证数据
        assertThat(tableManager.get(createKey("key1"))).isNotNull();
        assertThat(tableManager.get(createKey("key2"))).isNotNull();
    }
    
    @Test
    void testBatchOperation() {
        // 测试批量操作原子性
        Journal journal = createJournal();
        MemoryTableManager tableManager = createTableManager();
        
        List<JournalOperation> operations = Arrays.asList(
            new JournalOperation(OperationType.PUT, createKey("key1"), createValue("value1")),
            new JournalOperation(OperationType.PUT, createKey("key2"), createValue("value2")),
            new JournalOperation(OperationType.DELETE, createKey("key1"), null)
        );
        
        JournalReplayPoint point = journal.writeBatch(operations);
        journal.replayFrom(point, tableManager);
        
        // 验证批量操作的原子性
        assertThat(tableManager.get(createKey("key1"))).isNull(); // 被删除
        assertThat(tableManager.get(createKey("key2"))).isNotNull();
    }
}
```

## 4. 集成测试设计

### 4.1 KVStore 集成测试

KVStore 集成测试覆盖完整流程：Put/Get/Delete 端到端、Dump + 重启恢复（验证数据持久化）、多线程并发读写（验证并发安全和数据一致性）。使用临时目录隔离测试数据。

```java
class KVStoreIntegrationTest {
    
    @Test
    void testPutGetDelete() {
        // 测试完整的 Put/Get/Delete 流程
        KVStore kvStore = createKVStore();
        
        // 插入数据
        kvStore.put(createKey("user:1"), createValue("Alice"));
        
        // 查询数据
        IndexValue result = kvStore.get(createKey("user:1"));
        assertThat(result.getData()).isEqualTo("Alice");
        
        // 删除数据
        kvStore.delete(createKey("user:1"));
        
        // 验证删除
        assertThat(kvStore.get(createKey("user:1"))).isNull();
    }
    
    @Test
    void testDumpAndRecovery() {
        // 测试 Dump 和恢复流程
        KVStore kvStore = createKVStore();
        
        // 插入数据
        for (int i = 0; i < 1000; i++) {
            kvStore.put(createKey("key" + i), createValue("value" + i));
        }
        
        // 触发 Dump
        kvStore.dump();
        
        // 重启 KVStore 模拟崩溃恢复
        kvStore.shutdown();
        kvStore = createKVStore(); // 重新创建
        
        // 验证数据恢复
        for (int i = 0; i < 1000; i++) {
            IndexValue result = kvStore.get(createKey("key" + i));
            assertThat(result).isNotNull();
        }
    }
    
    @Test
    void testConcurrentOperations() {
        // 测试并发操作
        KVStore kvStore = createKVStore();
        
        int threadCount = 10;
        int operationsPerThread = 100;
        
        ExecutorService executor = Executors.newFixedThreadPool(threadCount);
        List<Future<?>> futures = new ArrayList<>();
        
        for (int i = 0; i < threadCount; i++) {
            final int threadId = i;
            futures.add(executor.submit(() -> {
                for (int j = 0; j < operationsPerThread; j++) {
                    String key = "thread" + threadId + "_key" + j;
                    String value = "thread" + threadId + "_value" + j;
                    
                    kvStore.put(createKey(key), createValue(value));
                    
                    // 随机读取
                    if (j % 10 == 0) {
                        kvStore.get(createKey(key));
                    }
                    
                    // 随机删除
                    if (j % 20 == 0 && j > 0) {
                        kvStore.delete(createKey("thread" + threadId + "_key" + (j - 1)));
                    }
                }
            }));
        }
        
        // 等待所有线程完成
        for (Future<?> future : futures) {
            future.get();
        }
        
        executor.shutdown();
        
        // 验证数据一致性
        // ...
    }
}
```

### 4.2 存储层集成测试

存储层集成测试覆盖：Chunk 生命周期（OPEN → write → SEALED → 不可写）、GC 回收流程（创建 Chunk → 标记可回收 → 执行 Full GC → 验证文件删除）。

```java
class StorageIntegrationTest {
    
    @Test
    void testChunkLifecycle() {
        // 测试 Chunk 生命周期
        ChunkManager chunkManager = createChunkManager();
        
        // 创建 Chunk
        Chunk chunk = chunkManager.allocateChunk(ChunkType.LEAF);
        assertThat(chunk.getStatus()).isEqualTo(ChunkStatus.OPEN);
        
        // 写入数据
        byte[] data = "test data".getBytes();
        SegmentLocation location = chunk.write(data);
        
        // 读取数据
        byte[] result = chunk.read(location);
        assertThat(result).isEqualTo(data);
        
        // 封存 Chunk
        chunk.seal();
        assertThat(chunk.getStatus()).isEqualTo(ChunkStatus.SEALED);
        
        // 验证封存后不能写入
        assertThrows(IllegalStateException.class, () -> {
            chunk.write("new data".getBytes());
        });
    }
    
    @Test
    void testGarbageCollection() {
        // 测试垃圾回收
        ChunkManager chunkManager = createChunkManager();
        GarbageCollector gc = createGarbageCollector(chunkManager);
        
        // 创建多个 Chunk
        List<Chunk> chunks = new ArrayList<>();
        for (int i = 0; i < 5; i++) {
            Chunk chunk = chunkManager.allocateChunk(ChunkType.LEAF);
            chunks.add(chunk);
            
            // 写入一些数据
            chunk.write(("data" + i).getBytes());
            chunk.seal();
        }
        
        // 标记某些 Chunk 为可回收
        // ...
        
        // 执行 GC
        gc.performFullGC();
        
        // 验证 Chunk 被正确回收
        // ...
    }
}
```

### 4.3 存储格式集成测试

存储格式测试验证磁盘数据结构：Chunk Header 为 4096 bytes 且字段布局正确（ChunkID/ChunkType/OwnerID/NamespaceID/ValidDataSize/Reserved 全 0）；Write Item 格式正确（从 offset 4096 开始，总大小 4KB 对齐）；Journal OpType 和 Key/Value Type 为 4 bytes。

```java
class StorageFormatIntegrationTest {
    
    @Test
    void testChunkHeaderFormat() {
        // 验证 Chunk Header 为 4096 bytes
        ChunkManager chunkManager = createChunkManager();
        Chunk chunk = chunkManager.allocateLeafChunk();
        
        // 读取 Header
        byte[] header = chunk.readRaw(0, 4096);
        
        // 验证字段布局
        UUID chunkId = readUUID(header, 0);          // offset 0, 16 bytes
        int chunkType = readInt(header, 16);          // offset 16, 4 bytes
        UUID ownerId = readUUID(header, 20);          // offset 20, 16 bytes
        UUID namespaceId = readUUID(header, 36);      // offset 36, 16 bytes
        int validDataSize = readInt(header, 52);      // offset 52, 4 bytes
        
        assertThat(chunkId).isNotNull();
        assertThat(chunkType).isEqualTo(ChunkType.LEAF.value());
        assertThat(ownerId).isNotNull();
        assertThat(validDataSize).isEqualTo(0);
        
        // 验证 Reserved 区域全 0
        for (int i = 56; i < 4096; i++) {
            assertThat(header[i]).isEqualTo((byte) 0);
        }
    }
    
    @Test
    void testWriteItemFormat() {
        // 验证 Write Item 格式：Header(8B) + Body + CRC32(4B) + Padding
        ChunkManager chunkManager = createChunkManager();
        byte[] testData = "test data".getBytes();
        
        SegmentLocation location = chunkManager.writeLeafPage(testData);
        
        // 验证 offset 从 4096 开始（Chunk Header 之后）
        assertThat(location.getOffset()).isGreaterThanOrEqualTo(4096);
        
        // 验证 Write Item 总大小为 4KB 的整数倍
        assertThat(location.getLength() % 4096).isEqualTo(0);
    }
    
    @Test
    void testJournalOpTypeAlignment() {
        // 验证 Journal OpType 为 4 bytes
        Journal journal = createJournal();
        JournalReplayPoint point = journal.write(
            OperationType.PUT,
            createKey("key1"),
            createValue("value1"));
        
        // 回放验证数据完整性
        MemoryTableManager tableManager = createTableManager();
        journal.replayFrom(point, tableManager);
        assertThat(tableManager.get(createKey("key1"))).isNotNull();
    }
    
    @Test
    void testKeyValueFieldAlignment() {
        // 验证 Key/Value Type 字段为 4 bytes
        IndexKey key = createKey("test");
        byte[] serialized = key.serialize();
        
        // Type 字段占 4 bytes
        int keyType = ByteBuffer.wrap(serialized, 0, 4).getInt();
        assertThat(keyType).isEqualTo(KeyType.ORDERED_BYTES.value());
        
        // Data Length 字段占 4 bytes
        int dataLength = ByteBuffer.wrap(serialized, 4, 4).getInt();
        assertThat(dataLength).isEqualTo("test".getBytes().length);
    }
}
```

## 5. 性能测试设计

### 5.1 基准测试

使用 JMH 框架进行基准测试：Put 操作吞吐量（ops/sec）、Get 操作平均延迟（微秒）、Range Query 样本延迟（毫秒）。预热后测量，避免 JIT 编译影响结果。

```java
class KVStoreBenchmark {
    
    @Benchmark
    @BenchmarkMode(Mode.Throughput)
    @OutputTimeUnit(TimeUnit.SECONDS)
    public void benchmarkPutOperation(Blackhole blackhole) {
        // Put 操作基准测试
        KVStore kvStore = createKVStore();
        
        for (int i = 0; i < 10000; i++) {
            IndexKey key = createKey("benchmark_key" + i);
            IndexValue value = createValue("benchmark_value" + i);
            
            kvStore.put(key, value);
            blackhole.consume(key);
            blackhole.consume(value);
        }
    }
    
    @Benchmark
    @BenchmarkMode(Mode.AverageTime)
    @OutputTimeUnit(TimeUnit.MICROSECONDS)
    public void benchmarkGetOperation(Blackhole blackhole) {
        // Get 操作基准测试
        KVStore kvStore = createKVStoreWithData(10000);
        
        for (int i = 0; i < 1000; i++) {
            IndexKey key = createKey("key" + (i % 10000));
            IndexValue value = kvStore.get(key);
            blackhole.consume(value);
        }
    }
    
    @Benchmark
    @BenchmarkMode(Mode.SampleTime)
    @OutputTimeUnit(TimeUnit.MILLISECONDS)
    public void benchmarkRangeQuery(Blackhole blackhole) {
        // 范围查询基准测试
        KVStore kvStore = createKVStoreWithData(100000);
        
        for (int i = 0; i < 100; i++) {
            IndexKey start = createKey("key" + (i * 1000));
            IndexKey end = createKey("key" + (i * 1000 + 100));
            
            List<IndexValue> results = kvStore.rangeQuery(start, end);
            blackhole.consume(results);
        }
    }
}
```

### 5.2 压力测试

高并发压力测试：50 个线程，每线程 1000 次操作（写入/随机读/随机删除混合），使用 CountDownLatch 同步启动。统计成功和失败次数，验证零错误、数据一致性。超时 5 分钟。

```java
class StressTest {
    
    @Test
    void testHighConcurrencyStress() {
        // 高并发压力测试
        KVStore kvStore = createKVStore();
        
        int threadCount = 50;
        int operationsPerThread = 1000;
        
        ExecutorService executor = Executors.newFixedThreadPool(threadCount);
        CountDownLatch startLatch = new CountDownLatch(1);
        CountDownLatch endLatch = new CountDownLatch(threadCount);
        
        AtomicInteger successCount = new AtomicInteger(0);
        AtomicInteger errorCount = new AtomicInteger(0);
        
        for (int i = 0; i < threadCount; i++) {
            final int threadId = i;
            executor.submit(() -> {
                try {
                    startLatch.await();
                    
                    for (int j = 0; j < operationsPerThread; j++) {
                        try {
                            String key = "stress_" + threadId + "_" + j;
                            String value = "value_" + threadId + "_" + j;
                            
                            kvStore.put(createKey(key), createValue(value));
                            successCount.incrementAndGet();
                            
                            // 随机操作混合
                            if (j % 5 == 0) {
                                kvStore.get(createKey(key));
                            }
                            if (j % 10 == 0 && j > 0) {
                                kvStore.delete(createKey("stress_" + threadId + "_" + (j - 1)));
                            }
                            
                        } catch (Exception e) {
                            errorCount.incrementAndGet();
                            log.error("Operation failed", e);
                        }
                    }
                    
                } catch (InterruptedException e) {
                    Thread.currentThread().interrupt();
                } finally {
                    endLatch.countDown();
                }
            });
        }
        
        // 开始测试
        startLatch.countDown();
        
        // 等待测试完成
        endLatch.await(5, TimeUnit.MINUTES);
        
        // 验证结果
        assertThat(errorCount.get()).isEqualTo(0);
        assertThat(successCount.get()).isEqualTo(threadCount * operationsPerThread);
        
        executor.shutdown();
    }
}
```

## 6. 测试配置和工具

### 6.1 测试配置文件

测试配置文件定义各层测试的超时、并行、清理策略、预热参数和资源限制。YAML 格式，与生产配置分离。

```yaml
# test-config.yaml
testing:
  unit:
    timeout: 30000           # 单元测试超时时间(ms)
    parallel: true           # 是否并行执行
  integration:
    timeout: 120000          # 集成测试超时时间(ms)
    cleanup: true            # 测试后清理
  performance:
    warmup: 1000             # 预热迭代次数
    measurement: 5000        # 测量迭代次数
    forks: 3                 # 分叉数
  e2e:
    timeout: 300000          # 端到端测试超时时间(ms)
    retry: 3                 # 重试次数

logging:
  level: INFO               # 测试日志级别
  file: target/test.log     # 日志文件路径

resources:
  temp_dir: target/temp     # 临时目录
  max_memory: 512m          # 最大内存限制
```

### 6.2 测试工具类

测试工具类提供创建测试数据（key/value/replayPoint）、创建测试 KVStore 实例（使用临时目录）和清理测试数据的工厂方法，减少测试代码重复。

```java
class TestUtils {
    
    // 创建测试数据
    public static IndexKey createKey(String key) {
        return new OrderedBytesKey(key.getBytes(StandardCharsets.UTF_8));
    }
    
    public static IndexValue createValue(String value) {
        return IndexValue.createNormal(value.getBytes(StandardCharsets.UTF_8));
    }
    
    public static JournalReplayPoint createReplayPoint() {
        return new JournalReplayPoint(1, 0, 0);
    }
    
    // 创建测试 KVStore 实例
    public static KVStore createKVStore() {
        Config config = Config.builder()
            .storagePath("target/test-data" + System.currentTimeMillis())
            .sealThreshold(64 * 1024 * 1024)
            .build();
        
        return new KVStore(config);
    }
    
    // 清理测试数据
    public static void cleanupTestData(String path) {
        try {
            Files.walk(Paths.get(path))
                 .sorted(Comparator.reverseOrder())
                 .map(Path::toFile)
                 .forEach(File::delete);
        } catch (IOException e) {
            // 忽略清理错误
        }
    }
}
```

## 7. 持续集成

### 7.1 CI/CD 流水线

GitHub Actions 流水线：代码提交后自动执行单元测试（mvn test）→ 集成测试（mvn verify）→ 性能测试（JMH benchmark）→ 上传测试报告。使用 JDK 11，Ubuntu 环境。

```yaml
# .github/workflows/ci.yml
name: CI Pipeline

on:
  push:
    branches: [ main, develop ]
  pull_request:
    branches: [ main ]

jobs:
  test:
    runs-on: ubuntu-latest
    
    steps:
    - uses: actions/checkout@v2
    
    - name: Set up JDK 11
      uses: actions/setup-java@v2
      with:
        java-version: '11'
        distribution: 'adopt'
    
    - name: Run Unit Tests
      run: mvn test
    
    - name: Run Integration Tests
      run: mvn verify -DskipUnitTests
    
    - name: Run Performance Tests
      run: mvn jmh:benchmark -DskipTests
    
    - name: Upload Test Results
      uses: actions/upload-artifact@v2
      with:
        name: test-results
        path: target/**/*.xml
```

## 8. 测试报告和质量指标

### 8.1 测试覆盖率目标

覆盖率目标：行覆盖 >= 80%、分支覆盖 >= 75%、方法覆盖 >= 85%、类覆盖 >= 90%。质量门禁条件：所有测试通过 + 覆盖率达标 + 无严重代码异味 + 性能基准通过。

```
测试覆盖率目标：
  - 行覆盖率：≥ 80%
  - 分支覆盖率：≥ 75%
  - 方法覆盖率：≥ 85%
  - 类覆盖率：≥ 90%
```

### 8.2 质量门禁

CI/CD 质量门禁条件：所有单元测试通过 + 所有集成测试通过 + 覆盖率达标 + 无严重代码异味 + 性能基准测试通过。任何一项不满足则阻止合并。

```
质量门禁条件：
  - 所有单元测试通过
  - 所有集成测试通过
  - 测试覆盖率达标
  - 无严重代码异味
  - 性能基准测试通过
```