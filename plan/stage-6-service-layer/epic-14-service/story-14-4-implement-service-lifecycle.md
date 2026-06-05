# Story 14-4: Implement Service Lifecycle

## Story

As a developer, I want to implement Service Lifecycle so that the service can be properly started, managed, and stopped with graceful shutdown.

## Acceptance Criteria

- [ ] KVStore lifecycle management implemented (start → init → shutdown)
- [ ] Graceful shutdown implemented (stop accepting requests, finish in-flight requests)
- [ ] Service state transitions managed (IDLE → STARTING → RUNNING → STOPPING → STOPPED)
- [ ] Health check endpoint implemented with comprehensive status checks
- [ ] Service configuration loading and validation during startup
- [ ] Unit tests verify all lifecycle methods

## Technical Details

### Service Lifecycle Flow

```java
public void start() {
    // 1. 检查服务状态，确保未启动
    if (isRunning()) {
        throw new IllegalStateException("Service already running");
    }
    
    // 2. 加载 Service 配置
    ServiceConfig config = loadServiceConfig();
    
    // 3. 创建并启动 KVStore
    kvStore = createKVStore(config.getKvstoreConfigPath());
    kvStore.start();
    kvStore.init();
    
    // 4. 启动 HTTP 服务器
    httpServer = createHttpServer(config);
    registerRoutes();
    httpServer.start();
    
    // 5. 服务就绪
    log.info("Service started successfully");
}

public void stop() {
    // 1. 停止接受新请求
    httpServer.stopAcceptingRequests();
    
    // 2. 等待处理中的请求完成（超时保护）
    waitForInFlightRequests(30, TimeUnit.SECONDS);
    
    // 3. 停止 HTTP 服务器
    httpServer.stop();
    
    // 4. 停止 KVStore
    kvStore.shutdown();
    
    // 5. 服务停止
    log.info("Service stopped gracefully");
}
```

### Health Check Implementation

```java
public HealthStatus checkHealth() {
    // 检查 KVStore 健康状态
    HealthStatus kvStoreHealth = kvStore.getHealthChecker().checkHealth();
    
    // 检查 HTTP 服务器健康状态
    HealthStatus httpHealth = httpServer.checkHealth();
    
    // 检查系统资源（内存、磁盘）
    HealthStatus systemHealth = checkSystemResources();
    
    // 综合健康状态
    return aggregateHealthStatus(kvStoreHealth, httpHealth, systemHealth);
}
```

## Testing

- testServiceStartup()
- testServiceShutdown()
- testGracefulShutdown()
- testHealthCheckComprehensive()
- testConfigurationLoading()
- testStateTransitions()
- testErrorRecovery()

## Effort Estimate

1.5 days
