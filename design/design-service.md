# Service 层设计

## 1. 概述

本文档定义 KVStore 的 Service 层设计，包括：
- **架构分层**：KVStore 模块与 Service 层的职责划分
- **生命周期管理**：Service 层控制 KVStore 的启动和关闭
- **REST API**：Service 层提供的 HTTP 接口
- **请求处理**：请求路由、参数验证、错误处理

**设计原则**：
- KVStore 作为独立模块，不提供 REST API，仅通过 API 接口暴露功能
- Service 层负责控制 KVStore 的生命周期（start、init、shutdown）
- Service 层负责将 KVStore 功能通过 REST API 对外暴露
- Service 层负责请求验证、错误处理、日志记录等横切关注点

```
┌─────────────────────────────────────────────────────────────┐
│                    架构分层                                  │
├─────────────────────────────────────────────────────────────┤
│                                                              │
│  ┌─────────────────────────────────────────────────────┐    │
│  │                    Client                           │    │
│  │              (HTTP / gRPC Client)                   │    │
│  └──────────────────────┬──────────────────────────────┘    │
│                         │                                    │
│                         ▼                                    │
│  ┌─────────────────────────────────────────────────────┐    │
│  │                 Service 层                          │    │
│  │  ┌─────────────────────────────────────────────┐    │    │
│  │  │  - REST API 暴露                            │    │    │
│  │  │  - 请求验证                                 │    │    │
│  │  │  - 错误处理                                 │    │    │
│  │  │  - 日志记录                                 │    │    │
│  │  │  - KVStore 生命周期管理                     │    │    │
│  │  └─────────────────────────────────────────────┘    │    │
│  └──────────────────────┬──────────────────────────────┘    │
│                         │                                    │
│                         ▼                                    │
│  ┌─────────────────────────────────────────────────────┐    │
│  │                 KVStore 模块                        │    │
│  │  ┌─────────────────────────────────────────────┐    │    │
│  │  │  - 数据操作 (put/get/delete/batch)          │    │    │
│  │  │  - 配置管理 (ConfigManager)                 │    │    │
│  │  │  - 监控 (MetricsRegistry)                   │    │    │
│  │  │  - 备份恢复 (BackupManager)                 │    │    │
│  │  │  - 生命周期接口 (start/init/shutdown)       │    │    │
│  │  └─────────────────────────────────────────────┘    │    │
│  └─────────────────────────────────────────────────────┘    │
│                                                              │
└─────────────────────────────────────────────────────────────┘
```

## 2. 职责划分

### 2.1 KVStore 模块职责

| 职责 | 描述 |
|------|------|
| 数据存储 | 键值对的存储、检索、删除 |
| 数据持久化 | Journal 写入、B+Tree Dump |
| 并发控制 | 读写锁、快照读 |
| 垃圾回收 | Chunk 生命周期管理 |
| 配置管理 | 配置加载、动态更新 |
| 监控 | 性能指标采集、健康检查 |
| 备份恢复 | 全量/增量备份、恢复 |

### 2.2 Service 层职责

| 职责 | 描述 |
|------|------|
| 生命周期管理 | 启动、初始化、关闭 KVStore |
| REST API 暴露 | HTTP 接口定义和实现 |
| 请求验证 | 参数校验、权限检查 |
| 错误处理 | 异常捕获、错误响应格式化 |
| 日志记录 | 请求日志、审计日志 |
| 限流熔断 | 请求限流、熔断保护 |
| 监控集成 | 将 KVStore 监控数据暴露为 Prometheus 格式 |

## 3. Service 层类设计

### 3.1 KVStoreService

KVStoreService 是 Service 层的核心类，持有 KVStore 实例、HTTP 服务器、请求日志记录器和限流器。负责 KVStore 的生命周期管理（start/stop）、REST API 路由注册和请求处理管道的组装。

```
class KVStoreService {
    
    属性：
      - kvStore: KVStore              // KVStore 实例
      - config: ServiceConfig         // Service 配置
      - httpServer: HttpServer        // HTTP 服务器
      - requestLogger: RequestLogger  // 请求日志记录器
      - rateLimiter: RateLimiter      // 限流器
    
    方法：
      - start(): void                 // 启动服务
      - stop(): void                  // 停止服务
      - isRunning(): boolean          // 检查服务状态
}
```

### 3.2 ServiceConfig

Service 层的配置类，包含 HTTP 服务器配置（端口、绑定地址）、请求处理配置（超时、最大 Body 大小、限流）和 KVStore 配置文件路径。与 KVStore 内部配置分离，由 Service 层独立管理。

```
class ServiceConfig {
    
    属性：
      - httpPort: int                 // HTTP 端口，默认 8080
      - httpHost: String              // HTTP 绑定地址，默认 0.0.0.0
      - kvstoreConfigPath: String     // KVStore 配置文件路径
      - requestTimeout: long          // 请求超时时间，默认 30000ms
      - maxRequestBodySize: int       // 最大请求体大小，默认 10MB
      - rateLimit: int                // 请求限流，默认 10000/s
      - logRequests: boolean          // 是否记录请求日志，默认 true
      - prometheusEnabled: boolean    // 是否启用 Prometheus 端点，默认 true
}
```

## 4. 生命周期管理

### 4.1 启动流程

Service 启动流程：加载 Service 配置 → 创建 KVStore 实例 → 调用 kvStore.start()（内部执行 init、Journal 回放等）→ 注册配置和监控监听器 → 创建并启动 HTTP 服务器、注册路由 → 服务就绪。

```
start()
    │
    ▼
┌─────────────────────────────────────┐
│  1. 加载 Service 配置               │
│     config = loadServiceConfig()    │
└─────────────────────────────────────┘
    │
    ▼
┌─────────────────────────────────────┐
│  2. 创建 KVStore 实例               │
│     kvStore = new KVStore(          │
│         config.kvstoreConfigPath)   │
└─────────────────────────────────────┘
    │
    ▼
┌─────────────────────────────────────┐
│  3. 启动 KVStore                    │
│     kvStore.start()                 │
│     // start() 内部会调用 init()    │
└─────────────────────────────────────┘
    │
    ▼
┌─────────────────────────────────────┐
│  4. 注册监听器                      │
│     registerConfigListener()        │
│     registerMetricsListener()       │
└─────────────────────────────────────┘
    │
    ▼
┌─────────────────────────────────────┐
│  5. 启动 HTTP 服务器                │
│     httpServer = createHttpServer() │
│     registerRoutes()                │
│     httpServer.start()              │
└─────────────────────────────────────┘
    │
    ▼
┌─────────────────────────────────────┐
│  6. 服务就绪                        │
│     log.info("Service started")     │
└─────────────────────────────────────┘
```

### 4.2 停止流程

Service 停止流程：停止接受新 HTTP 请求 → 等待进行中的请求完成（30 秒超时）→ 关闭 HTTP 服务器 → 调用 kvStore.shutdown()（内部执行 Seal + Dump + 关闭各组件）→ 服务已停止。

```
stop()
    │
    ▼
┌─────────────────────────────────────┐
│  1. 停止接受新请求                  │
│     httpServer.stopAccepting()      │
└─────────────────────────────────────┘
    │
    ▼
┌─────────────────────────────────────┐
│  2. 等待进行中的请求完成            │
│     waitForPendingRequests(         │
│         timeout: 30000)             │
└─────────────────────────────────────┘
    │
    ▼
┌─────────────────────────────────────┐
│  3. 关闭 HTTP 服务器                │
│     httpServer.stop()               │
└─────────────────────────────────────┘
    │
    ▼
┌─────────────────────────────────────┐
│  4. 关闭 KVStore                    │
│     kvStore.shutdown()              │
└─────────────────────────────────────┘
    │
    ▼
┌─────────────────────────────────────┐
│  5. 服务已停止                      │
│     log.info("Service stopped")     │
└─────────────────────────────────────┘
```

## 5. REST API 设计

### 5.1 数据操作接口

REST API 提供标准的 KV 操作：PUT /kv/{key}（写入）、GET /kv/{key}（读取）、DELETE /kv/{key}（删除）、POST /kv/batch（批量操作）、GET /kv/range（范围查询）。所有接口返回统一的 JSON 格式，错误时返回错误码和描述。

```
PUT /api/v1/kv/{key}
Request:
  Body: value (raw bytes or base64 encoded)
Response:
  200 OK - 成功
  400 Bad Request - 参数错误
  500 Internal Server Error - 服务器错误

GET /api/v1/kv/{key}
Response:
  200 OK - 返回值
    Body: value (raw bytes or base64 encoded)
  404 Not Found - key 不存在
  500 Internal Server Error - 服务器错误

DELETE /api/v1/kv/{key}
Response:
  200 OK - 成功
  404 Not Found - key 不存在
  500 Internal Server Error - 服务器错误

POST /api/v1/kv/batch
Request:
  Body: {
    "operations": [
      {"type": "PUT", "key": "k1", "value": "v1"},
      {"type": "DELETE", "key": "k2"}
    ]
  }
Response:
  200 OK - 成功
  400 Bad Request - 参数错误
  500 Internal Server Error - 服务器错误

GET /api/v1/kv/range
Query Parameters:
  - startKey: 起始 key
  - endKey: 结束 key
  - limit: 返回数量限制（可选）
Response:
  200 OK - 返回键值对列表
    Body: {
      "entries": [
        {"key": "k1", "value": "v1"},
        {"key": "k2", "value": "v2"}
      ]
    }
  400 Bad Request - 参数错误
  500 Internal Server Error - 服务器错误
```

### 5.2 监控接口

监控 API：GET /metrics 返回 JSON 格式的性能指标和系统状态，GET /metrics/prometheus 返回 Prometheus 兼容格式，GET /health 返回健康检查结果（200 健康/503 不健康）。这些接口直接调用 KVStore 的 MetricsRegistry API。

```
GET /api/v1/metrics
Response:
  200 OK - 返回监控数据
    Body: {
      "timestamp": 1704096000000,
      "counters": {
        "put": {"count": 1000, "p50": 5, "p99": 50},
        "get": {"count": 5000, "p50": 1, "p99": 10}
      },
      "gauges": {
        "memoryUsed": 1073741824,
        "sealedTables": 2
      }
    }

GET /api/v1/metrics/prometheus
Response:
  200 OK - Prometheus 格式监控数据
    Body: |
      # HELP kvstore_put_total Total put operations
      # TYPE kvstore_put_total counter
      kvstore_put_total 1000
      # HELP kvstore_get_total Total get operations
      # TYPE kvstore_get_total counter
      kvstore_get_total 5000

GET /api/v1/health
Response:
  200 OK - 健康检查通过
    Body: {
      "status": "healthy",
      "checks": {
        "storage": {"status": "healthy"},
        "memory": {"status": "healthy"},
        "journal": {"status": "healthy"}
      }
    }
  503 Service Unavailable - 健康检查失败
    Body: {
      "status": "unhealthy",
      "checks": {
        "storage": {"status": "unhealthy", "message": "Disk full"}
      }
    }
```

### 5.3 配置接口

配置 API：GET /config 返回所有配置，GET /config/{key} 返回指定配置（含类型和是否支持动态生效），POST /config/reload 重新加载配置文件并返回变更列表。配置变更通过 KVStore 的 ConfigManager API 实现。

```
GET /api/v1/config
Response:
  200 OK - 返回所有配置
    Body: {
      "kvstore": {...},
      "memorytable": {...},
      ...
    }

GET /api/v1/config/{key}
Response:
  200 OK - 返回指定配置
    Body: {
      "key": "memorytable.sealThreshold",
      "value": 67108864,
      "type": "long",
      "dynamic": true
    }

POST /api/v1/config/reload
Response:
  200 OK - 重新加载成功
    Body: {
      "success": true,
      "changes": [
        {
          "key": "memorytable.sealThreshold",
          "oldValue": 67108864,
          "newValue": 134217728
        }
      ]
    }
```

### 5.4 备份恢复接口

备份恢复 API：POST /backup/full 和 /backup/incremental 触发备份，GET /backup/{id}/validate 验证备份完整性，POST /recovery/rollback 和 /recovery/full 执行恢复，GET /recovery/versions 列出可回滚的版本。这些接口调用 KVStore 的 BackupManager API。

```
POST /api/v1/backup/full
Request:
  Body: {"targetPath": "/backup/kvstore"}
Response:
  200 OK - 备份成功
    Body: {
      "backupId": "backup_20240101_120000",
      "backupPath": "/backup/kvstore/backup_20240101_120000",
      "backupType": "FULL",
      "treeEntryCount": 500000,
      "sizeInBytes": 1073741824
    }

POST /api/v1/backup/incremental
Request:
  Body: {
    "targetPath": "/backup/kvstore",
    "parentBackupId": "backup_20240101_120000"
  }
Response:
  200 OK - 备份成功

GET /api/v1/backup/{backupId}/validate
Response:
  200 OK - 验证结果
    Body: {
      "valid": true,
      "checksumMatch": true
    }

POST /api/v1/recovery/rollback
Request:
  Body: {"targetVersion": 2}
Response:
  200 OK - 恢复成功
    Body: {
      "success": true,
      "recoveredVersion": 2
    }

POST /api/v1/recovery/full
Request:
  Body: {
    "backupChain": [
      "/backup/kvstore/backup_20240101_120000",
      "/backup/kvstore/backup_20240102_120000_inc"
    ]
  }
Response:
  200 OK - 恢复成功

GET /api/v1/recovery/versions
Response:
  200 OK - 返回可回滚版本列表
    Body: {
      "versions": [
        {"version": 3, "healthy": true},
        {"version": 2, "healthy": true},
        {"version": 1, "healthy": false}
      ]
    }
```

## 6. 请求处理流程

### 6.1 请求处理管道

请求处理经过四级管道：限流器（检查 QPS，超额返回 429）→ 日志记录器（记录请求开始、参数）→ 参数验证器（校验必填项、格式、大小）→ 业务处理器（调用 KVStore API）。每级都可以拦截请求返回错误，统一由错误处理器格式化响应。

```
┌─────────────────────────────────────────────────────────────┐
│                    请求处理管道                              │
├─────────────────────────────────────────────────────────────┤
│                                                              │
│  请求 ──► 限流 ──► 日志 ──► 验证 ──► 处理 ──► 响应        │
│                                                              │
│  ┌─────────┐  ┌─────────┐  ┌─────────┐  ┌─────────┐        │
│  │ 限流器  │  │日志记录 │  │参数验证 │  │业务处理 │        │
│  │         │  │         │  │         │  │         │        │
│  │检查限流 │  │记录请求 │  │校验参数 │  │调用KVStore│       │
│  │拒绝超额 │  │记录耗时 │  │格式转换 │  │返回结果 │        │
│  └─────────┘  └─────────┘  └─────────┘  └─────────┘        │
│                                                              │
│  错误处理：每个阶段都可能产生错误，统一由错误处理器处理     │
│                                                              │
└─────────────────────────────────────────────────────────────┘
```

### 6.2 错误处理

Service 层的错误响应格式统一为 JSON：包含 error.code（如 INVALID_PARAMETER/KEY_NOT_FOUND/RATE_LIMIT_EXCEEDED）、error.message、error.details、timestamp 和 requestId。KVStore 内部异常通过 ErrorCode 映射到 HTTP 状态码（400/404/429/500/503）。

```
错误响应格式：
{
  "error": {
    "code": "INVALID_PARAMETER",
    "message": "Key cannot be empty",
    "details": {
      "field": "key",
      "constraint": "not_empty"
    }
  },
  "timestamp": 1704096000000,
  "requestId": "req-12345"
}

错误码定义：
| 错误码 | HTTP 状态码 | 描述 |
|--------|-------------|------|
| INVALID_PARAMETER | 400 | 参数错误 |
| KEY_NOT_FOUND | 404 | Key 不存在 |
| BACKUP_NOT_FOUND | 404 | 备份不存在 |
| RATE_LIMIT_EXCEEDED | 429 | 请求限流 |
| INTERNAL_ERROR | 500 | 内部错误 |
| SERVICE_UNAVAILABLE | 503 | 服务不可用 |
```

## 7. 监听器集成

### 7.1 配置变更监听

Service 注册 ConfigListener 监听 KVStore 配置变更，收到通知后记录变更日志（便于审计），可选地推送变更事件到外部监控系统。

```
class KVStoreService {
    
    registerConfigListener() {
        kvStore.getConfigManager().addListener(new ConfigListener() {
            @Override
            public void onConfigChange(List<ConfigChange> changes) {
                // 记录配置变更日志
                log.info("Config changed: {}", changes);
                
                // 可选：推送配置变更事件到监控系统
                pushConfigChangeEvent(changes);
            }
        });
    }
}
```

### 7.2 监控数据监听

Service 注册 MetricsListener 监听定时采集的 MetricsSnapshot，可选地推送到外部监控系统。当健康检查状态从 UP 变为 DOWN/DEGRADED 时，触发告警通知运维人员。

```
class KVStoreService {
    
    registerMetricsListener() {
        kvStore.getMetricsRegistry().addListener(new MetricsListener() {
            @Override
            public void onSnapshot(MetricsSnapshot snapshot) {
                // 可选：推送监控数据到外部系统
                pushToMonitoringSystem(snapshot);
            }
            
            @Override
            public void onHealthCheckChanged(String name, HealthCheckResult result) {
                // 健康状态变更告警
                if (result.status == HealthStatus.UNHEALTHY) {
                    alertManager.sendAlert(name, result.message);
                }
            }
        });
    }
}
```

## 8. 部署配置

### 8.1 配置文件示例

部署配置分为 Service 配置（HTTP 端口、限流、日志等）和 KVStore 配置路径。两个配置文件分离管理：Service 配置控制对外暴露方式，KVStore 配置控制存储引擎行为。

```json
{
  "service": {
    "httpPort": 8080,
    "httpHost": "0.0.0.0",
    "requestTimeout": 30000,
    "maxRequestBodySize": 10485760,
    "rateLimit": 10000,
    "logRequests": true,
    "prometheusEnabled": true
  },
  "kvstore": {
    "configPath": "./config/kvstore-config.json"
  }
}
```

### 8.2 启动命令

通过 java -jar 启动，指定 Service 配置文件路径和日志配置文件路径。支持环境变量覆盖配置项。

```
java -jar kvstore-service.jar \
  --config ./config/service-config.json \
  --log-config ./config/logback.xml
```

## 9. 相关文档

- [KVStore 设计](design-kvstore.md)：KVStore 主类与核心流程
- [Config 设计](design-config.md)：配置管理框架
- [Monitoring 设计](design-monitoring.md)：监控体系设计
- [Backup 设计](design-backup.md)：备份与恢复设计
