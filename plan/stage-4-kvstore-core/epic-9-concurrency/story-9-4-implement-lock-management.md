# Story 9-4: Implement Lock Management

## Story

As a developer, I want to implement the lock management so that concurrent access can be controlled.

## Acceptance Criteria

- [ ] Read/write locks implemented
- [ ] Lock hierarchy enforced
- [ ] Lock timeout implemented
- [ ] Deadlock prevention
- [ ] Unit tests verify all methods

## Technical Details

### Lock Hierarchy

```
1. WriteRequestQueue (no locks, ConcurrentLinkedQueue)
2. MemoryTableManager write lock
3. MemoryTable read lock
4. B+Tree page cache lock
```

### Implementation

```java
public class MemoryTableManager {
    private final ReadWriteLock lock = new ReentrantReadWriteLock();
    
    public IndexValue get(IndexKey key) {
        lock.readLock().lock();
        try {
            return doGet(key);
        } finally {
            lock.readLock().unlock();
        }
    }
    
    public void put(IndexKey key, IndexValue value) {
        lock.writeLock().lock();
        try {
            doPut(key, value);
        } finally {
            lock.writeLock().unlock();
        }
    }
}
```

## Testing

- testReadLock()
- testWriteLock()
- testLockHierarchy()
- testLockTimeout()
- testDeadlockPrevention()

## Effort Estimate

1 day
