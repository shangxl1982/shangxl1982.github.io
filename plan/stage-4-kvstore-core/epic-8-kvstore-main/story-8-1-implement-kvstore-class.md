# Story 8-1: Implement KVStore Class

## Story

As a developer, I want to implement the KVStore class so that all components can be integrated according to the design specifications.

## Acceptance Criteria

- [ ] KVStore class created with all required components
- [ ] KVStoreState enum implemented (CREATED → INITIALIZING → RUNNING → STOPPING → STOPPED)
- [ ] start() method initializes all components with proper WAL sequence
- [ ] shutdown() method stops all components gracefully
- [ ] put() method implemented with Journal → MemoryTable WAL sequence
- [ ] get() method implemented with MemoryTable → B+Tree search order
- [ ] delete() method implemented with Tombstone creation
- [ ] batch() method implemented with atomicity guarantee
- [ ] dump() method implemented for sealed MemoryTable persistence
- [ ] Unit tests verify all methods and state transitions

## Technical Details

### Class: KVStore

```java
package org.hyperkv.lsmplus.core;

public class KVStore {
    private final File dataDir;
    private final Config config;
    private ChunkManager chunkManager;
    private Journal journal;
    private MemoryTableManager memoryTableManager;
    private BPlusTree bPlusTree;
    private WriteRequestQueue requestQueue;
    private BatchWriter batchWriter;
    private final AtomicReference<KVStoreState> state;
    
    public KVStore(File dataDir, Config config);
    public void start();
    public void init();
    public void shutdown();
    public void put(IndexKey key, IndexValue value);
    public IndexValue get(IndexKey key);
    public void delete(IndexKey key);
    public void batch(List<BatchOperation> operations);
    public void dump();
    public KVStoreState getState();
}

enum KVStoreState {
    CREATED,      // 已创建，未初始化
    INITIALIZING, // 初始化中
    RUNNING,      // 运行中
    STOPPING,     // 停止中
    STOPPED       // 已停止
}
```

### Write Path with WAL (Write-Ahead Logging)

```java
public void put(IndexKey key, IndexValue value) {
    // 1. 检查状态，确保在 RUNNING 状态
    if (state.get() != KVStoreState.RUNNING) {
        throw new IllegalStateException("KVStore not running");
    }
    
    // 2. 构造 Journal Entry
    JournalEntry entry = createPutEntry(key, value);
    
    // 3. 写入 Journal（WAL 原则：先写日志，再更新内存）
    JournalReplayPoint replayPoint = journal.write(entry);
    
    // 4. 更新 MemoryTable
    memoryTableManager.put(key, value);
    
    // 5. 检查是否需要 Seal 当前 MemoryTable
    if (memoryTableManager.shouldSealCurrentTable()) {
        memoryTableManager.sealCurrentTable();
    }
}

public void delete(IndexKey key) {
    // 1. 写入 DELETE 操作到 Journal
    JournalEntry entry = createDeleteEntry(key);
    journal.write(entry);
    
    // 2. 在 MemoryTable 中创建 Tombstone
    memoryTableManager.delete(key);
}
```

### Initialization Flow

```java
public void start() {
    // 1. 状态转换到 INITIALIZING
    state.compareAndSet(KVStoreState.CREATED, KVStoreState.INITIALIZING);
    
    // 2. Initialize ChunkManager
    chunkManager = new ChunkManager(dataDir);
    
    // 3. Initialize Journal
    journal = new Journal(chunkManager);
    
    // 4. Initialize MemoryTableManager
    memoryTableManager = new MemoryTableManager(config);
    
    // 5. Initialize B+Tree
    bPlusTree = new BPlusTree(chunkManager, config);
    
    // 6. 状态转换到 RUNNING
    state.set(KVStoreState.RUNNING);
}
```

## Testing

- testCreateKVStore()
- testStart()
- testShutdown()
- testPutGetDelete()
- testBatch()
- testDump()

## Effort Estimate

2 days
