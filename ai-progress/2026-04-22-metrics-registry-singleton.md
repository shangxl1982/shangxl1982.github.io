# Performance Counter Singleton Refactoring

**Date**: 2026-04-22

## Summary

Refactored MetricsRegistry to be a global singleton with null-safe counter access, making it easy to add performance counters anywhere in the code without worrying about NPE.

## Changes Made

### 1. NoOpPerformanceCounter ([NoOpPerformanceCounter.java](file:///home/wisefox/git/hyperkvstore/lsmplus-monitoring/src/main/java/org/hyperkv/lsmplus/monitoring/NoOpPerformanceCounter.java))

Created a no-op implementation of PerformanceCounter that:
- Provides safe fallback when MetricsRegistry is not initialized
- Implements all methods as no-ops (do nothing)
- Returns empty/zero snapshots
- Singleton instance for efficiency

### 2. MetricsRegistry Singleton Pattern ([MetricsRegistry.java](file:///home/wisefox/git/hyperkvstore/lsmplus-monitoring/src/main/java/org/hyperkv/lsmplus/monitoring/MetricsRegistry.java))

#### Static Singleton Methods
- `getInstance()` - Returns the singleton instance (null if not initialized)
- `initialize(String namespace)` - Initializes the singleton with namespace
- `initialize(String namespace, long snapshotInterval, int maxHistorySize)` - Full initialization
- `initialize()` - Default initialization with "hyperkv" namespace
- `shutdown()` - Stops and clears the singleton instance

#### Null-Safe Counter Access
- `getCounter(String name, String description)` - Returns PerformanceCounter (NoOp if not initialized)
- `getCounter(String name)` - Simplified version with empty description

#### Thread Safety
- Volatile instance field for visibility
- Synchronized initialization to prevent race conditions
- Double-checked locking pattern

### 3. KVStore Integration ([KVStore.java](file:///home/wisefox/git/hyperkvstore/lsmplus-kvstore/src/main/java/org/hyperkv/lsmplus/core/KVStore.java))

Updated to use singleton pattern:
- Removed `metricsRegistry` field
- Uses `MetricsRegistry.initialize()` in `start()`
- Uses `MetricsRegistry.getCounter()` for counter creation
- Uses `MetricsRegistry.shutdown()` in `shutdown()`

### 4. Test Updates

#### MetricsRegistrySingletonTest
Tests for singleton behavior:
- `testGetInstanceReturnsNullBeforeInitialization()`
- `testGetCounterReturnsNoOpBeforeInitialization()`
- `testInitializeCreatesSingleton()`
- `testInitializeThrowsExceptionIfAlreadyInitialized()`
- `testGetCounterReturnsRealCounterAfterInitialization()`
- `testShutdownClearsInstance()`
- `testMultipleGetCounterCallsReturnSameCounter()`
- `testNoOpCounterDoesNotThrow()`

#### UsageExampleTest
Demonstrates usage patterns:
- `demonstrateNullSafeCounterUsage()` - Shows NoOp behavior
- `demonstrateSingletonUsage()` - Shows normal usage
- `demonstrateEasyUsageAnywhere()` - Shows business logic integration

#### Updated Existing Tests
- MetricsRegistryTest - Uses singleton initialization
- PerformanceCounterTest - Uses singleton initialization

## Usage Examples

### Before Initialization (Null-Safe)

```java
// Can be called anywhere, even before initialization
PerformanceCounter counter = MetricsRegistry.getCounter("my_op", "My operation");

// No NPE, returns NoOpPerformanceCounter
counter.recordSuccess(100);  // Does nothing
counter.recordError();        // Does nothing
```

### After Initialization

```java
// Initialize once at application startup
MetricsRegistry.initialize("myapp");

// Get counters anywhere in the code
PerformanceCounter counter = MetricsRegistry.getCounter("operation", "Description");

// Use normally
long startTime = System.nanoTime();
try {
    // ... do work ...
    counter.recordSuccess((System.nanoTime() - startTime) / 1000);
} catch (Exception e) {
    counter.recordError();
}
```

### In Business Logic

```java
public class MyService {
    private final PerformanceCounter operationCounter = 
        MetricsRegistry.getCounter("my_service_operation", "Service operation");
    
    public void doWork() {
        long startTime = System.nanoTime();
        try {
            // ... business logic ...
            operationCounter.recordSuccess((System.nanoTime() - startTime) / 1000);
        } catch (Exception e) {
            operationCounter.recordError();
            throw e;
        }
    }
}
```

## Benefits

1. **Global Access**: No need to pass MetricsRegistry around
2. **Null-Safe**: No NPE if used before initialization
3. **Easy Integration**: Simple one-line counter creation
4. **Thread-Safe**: Proper synchronization for singleton
5. **Flexible**: Can be initialized with different configurations
6. **Testable**: Easy to reset between tests with shutdown()

## Design Patterns Used

1. **Singleton Pattern**: Global instance with controlled initialization
2. **Null Object Pattern**: NoOpPerformanceCounter for safe fallback
3. **Double-Checked Locking**: Thread-safe lazy initialization
4. **Factory Method**: Static getCounter() creates appropriate counter

## Migration Guide

### Old Pattern
```java
private MetricsRegistry metricsRegistry = new MetricsRegistry("namespace");
private PerformanceCounter counter = metricsRegistry.counter("name", "desc");
```

### New Pattern
```java
// In application startup:
MetricsRegistry.initialize("namespace");

// Anywhere in code:
PerformanceCounter counter = MetricsRegistry.getCounter("name", "desc");
```

## Testing

All tests pass:
- `./gradlew :lsmplus-monitoring:test` - All monitoring tests pass
- `./gradlew :lsmplus-kvstore:test` - KVStore integration tests pass
- `./gradlew build -x test` - Build successful

## Future Enhancements

1. Add configuration-based initialization
2. Add support for multiple namespaces
3. Add counter hierarchies (parent/child relationships)
4. Add automatic counter discovery via annotations
5. Add counter aggregation across instances
6. Add support for custom NoOp implementations

## Related Files

- Implementation: [MetricsRegistry.java](file:///home/wisefox/git/hyperkvstore/lsmplus-monitoring/src/main/java/org/hyperkv/lsmplus/monitoring/MetricsRegistry.java)
- NoOp Counter: [NoOpPerformanceCounter.java](file:///home/wisefox/git/hyperkvstore/lsmplus-monitoring/src/main/java/org/hyperkv/lsmplus/monitoring/NoOpPerformanceCounter.java)
- Tests: [MetricsRegistrySingletonTest.java](file:///home/wisefox/git/hyperkvstore/lsmplus-monitoring/src/test/java/org/hyperkv/lsmplus/monitoring/MetricsRegistrySingletonTest.java)
- Examples: [UsageExampleTest.java](file:///home/wisefox/git/hyperkvstore/lsmplus-monitoring/src/test/java/org/hyperkv/lsmplus/monitoring/UsageExampleTest.java)
