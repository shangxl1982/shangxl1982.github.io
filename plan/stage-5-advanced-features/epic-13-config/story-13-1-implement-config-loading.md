# Story 13-1: Implement Config Loading

## Story

As a developer, I want to implement Config Loading so that configuration can be loaded from JSON files with environment variable support.

## Acceptance Criteria

- [ ] ConfigManager class created with configuration management
- [ ] load() method loads config from JSON format
- [ ] Environment variable overrides supported (KVSTORE_<SECTION>_<KEY>)
- [ ] All 27 configuration parameters loaded with default values
- [ ] Type-safe get methods implemented (getInt, getLong, getBoolean, getString)
- [ ] Unit tests verify all methods

## Technical Details

### ConfigManager Class

```java
public class ConfigManager {
    private final String configPath;
    private Config config;
    private final List<ConfigListener> listeners;
    private final ScheduledExecutorService scheduler;
    private long lastModified;
    
    public void load();
    public Config getAllConfig();
    public int getInt(String key);
    public long getLong(String key);
    public boolean getBoolean(String key);
    public String getString(String key);
    public void addListener(ConfigListener listener);
    public void removeListener(ConfigListener listener);
    public void reload();
}
```

### JSON Configuration Format

```json
{
  "kvstore": {
    "storagePath": "./data",
    "maxVersions": 10
  },
  "memorytable": {
    "sealThreshold": 67108864,
    "maxSealedTables": 3
  },
  "bplustree": {
    "leafPageMaxSize": 65536,
    "indexPageMaxSize": 65536,
    "maxCacheSize": 134217728
  },
  "journal": {
    "maxChunkSize": 67108864,
    "maxRetry": 3,
    "retryInterval": 1000,
    "truncateRetentionDays": 7
  },
  "gc": {
    "gcScheduleInterval": 3600000,
    "partialGCRatio": 0.05,
    "chunkKeepAliveTime": 86400000
  },
  "monitoring": {
    "snapshotInterval": 10000,
    "logPath": "./logs/metrics.log",
    "logRetentionDays": 7
  },
  "backup": {
    "lockTimeout": 3600000,
    "batchSize": 10000,
    "verifyChecksum": true,
    "compressData": false
  }
}
```

### Environment Variable Support

```
KVSTORE_KVSTORE_STORAGE_PATH=/data/kvstore
KVSTORE_MEMORYTABLE_SEAL_THRESHOLD=134217728
KVSTORE_GC_PARTIAL_GC_RATIO=0.1
```

## Testing

- testConfigManagerCreation()
- testLoadFromJson()
- testEnvironmentVariableOverrides()
- testTypeSafeGetters()
- testDefaultValues()
- testInvalidConfig()
- testMultipleConfigs()

## Effort Estimate

1.5 days
