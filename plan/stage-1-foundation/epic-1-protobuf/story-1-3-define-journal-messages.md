# Story 1-3: Define Journal Messages

## Story

As a developer, I want to define Journal entry Protobuf messages so that write operations can be serialized and persisted.

## Acceptance Criteria

- [ ] JournalEntryProto message defined
- [ ] OperationType field supports PUT, DELETE, BATCH
- [ ] Timestamp and sequence_number fields included
- [ ] Entries field uses repeated KeyValuePairProto
- [ ] Message supports all three operation types correctly
- [ ] Protobuf file compiles without errors

## Technical Details

### File: journal.proto

```protobuf
syntax = "proto3";

package org.hyperkv.lsmplus.proto;

import "common.proto";
import "keyvalue.proto";

message JournalEntryProto {
    OperationType operation_type = 1;
    int64 timestamp = 2;
    int64 sequence_number = 3;
    repeated KeyValuePairProto entries = 4;
}

message JournalReplayPointProto {
    int64 region_major = 1;
    int64 region_minor = 2;
    int32 offset = 3;
}
```

## Testing

- Test PUT operation: operation_type=PUT, entries=[{key, value(NORMAL)}]
- Test DELETE operation: operation_type=DELETE, entries=[{key, value(TOMBSTONE)}]
- Test BATCH operation: operation_type=BATCH, entries=[multiple items]

## Effort Estimate

0.5 day
