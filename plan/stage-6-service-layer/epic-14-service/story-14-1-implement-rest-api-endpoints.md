# Story 14-1: Implement REST API Endpoints

## Story

As a developer, I want to implement REST API Endpoints so that KVStore can be accessed over HTTP with comprehensive API coverage.

## Acceptance Criteria

- [ ] KVStoreService class created with HTTP server management
- [ ] All REST API endpoints implemented (/api/v1/kv, /api/v1/config, /api/v1/backup, /api/v1/recovery)
- [ ] Request processing pipeline implemented (rate limiting → logging → validation → business logic)
- [ ] ServiceConfig class with HTTP server configuration
- [ ] All endpoints follow REST conventions with proper HTTP status codes
- [ ] Unit tests verify all methods

## Technical Details

### KVStoreService Class

```java
public class KVStoreService {
    private final KVStore kvStore;
    private final ServiceConfig config;
    private final HttpServer httpServer;
    private final RequestLogger requestLogger;
    private final RateLimiter rateLimiter;
    
    public void start();
    public void stop();
    public boolean isRunning();
}

public class ServiceConfig {
    private int httpPort = 8080;
    private String httpHost = "0.0.0.0";
    private String kvstoreConfigPath;
    private long requestTimeout = 30000;
    private int maxRequestBodySize = 10485760; // 10MB
    private int rateLimit = 10000;
    private boolean logRequests = true;
    private boolean prometheusEnabled = true;
}
```

### REST API Endpoints

```
数据操作接口:
PUT    /api/v1/kv/{key}           # Insert key-value
GET    /api/v1/kv/{key}           # Get value by key
DELETE /api/v1/kv/{key}           # Delete key
POST   /api/v1/kv/batch           # Batch operations
GET    /api/v1/kv/range           # Range query

配置接口:
GET    /api/v1/config             # Get all config
GET    /api/v1/config/{key}       # Get specific config
POST   /api/v1/config/reload      # Reload config

备份恢复接口:
POST   /api/v1/backup/full        # Full backup
POST   /api/v1/backup/incremental # Incremental backup
GET    /api/v1/backup/{id}/validate # Validate backup
POST   /api/v1/recovery/rollback  # Rollback recovery
POST   /api/v1/recovery/full      # Full recovery
GET    /api/v1/recovery/versions  # List rollback versions

监控接口:
GET    /api/v1/metrics            # JSON metrics
GET    /api/v1/metrics/prometheus # Prometheus metrics
GET    /api/v1/health             # Health check
```

### Request Processing Pipeline

```
1. 限流器 (Rate Limiter) - 检查 QPS，超额返回 429
2. 日志记录器 (Request Logger) - 记录请求开始、参数
3. 参数验证器 (Parameter Validator) - 校验必填项、格式、大小
4. 业务处理器 (Business Handler) - 调用 KVStore API
```

## Testing

- testKVStoreServiceCreation()
- testAllApiEndpoints()
- testRequestProcessingPipeline()
- testRateLimiting()
- testParameterValidation()
- testErrorResponses()

## Effort Estimate

2 days
