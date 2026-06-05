# Story 3-5: Implement Chunk Lifecycle

## Story

As a developer, I want to implement chunk lifecycle management so that chunks transition correctly between states.

## Acceptance Criteria

- [ ] OPEN state: can read and write
- [ ] SEALED state: can read only, no more writes
- [ ] DELETING state: marked for GC, no reads/writes
- [ ] DELETED state: file removed, no operations
- [ ] seal() transitions OPEN → SEALED
- [ ] delete() transitions SEALED → DELETING
- [ ] cleanup() transitions DELETING → DELETED
- [ ] All transitions are atomic
- [ ] Unit tests verify all transitions

## Technical Details

### State Machine

```
┌─────────┐
│   OPEN  │
└────┬────┘
     │ write()
     │
     ▼
┌─────────┐
│  SEALED │
└────┬────┘
     │ delete()
     │
     ▼
┌──────────┐
│ DELETING │
└────┬─────┘
     │ cleanup()
     │
     ▼
┌──────────┐
│ DELETED  │
└──────────┘
```

### Implementation

```java
public enum ChunkStatus {
    OPEN, SEALED, DELETING, DELETED
}

public class Chunk {
    private ChunkStatus status;
    
    public void seal() {
        synchronized (lock) {
            if (status != ChunkStatus.OPEN) {
                throw new IllegalStateException("Cannot seal chunk in " + status + " state");
            }
            status = ChunkStatus.SEALED;
        }
    }
    
    public void delete() {
        synchronized (lock) {
            if (status != ChunkStatus.SEALED) {
                throw new IllegalStateException("Cannot delete chunk in " + status + " state");
            }
            status = ChunkStatus.DELETING;
        }
    }
    
    public void cleanup() {
        synchronized (lock) {
            if (status != ChunkStatus.DELETING) {
                throw new IllegalStateException("Cannot cleanup chunk in " + status + " state");
            }
            file.delete();
            status = ChunkStatus.DELETED;
        }
    }
}
```

## Testing

- testOpenToSealed()
- testSealedToDeleting()
- testDeletingToDeleted()
- testInvalidTransitions()
- testConcurrentTransitions()

## Effort Estimate

1 day
