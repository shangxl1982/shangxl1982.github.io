# Story 1-2: Define Key/Value Messages

## Story

As a developer, I want to define Key and Value Protobuf messages so that all modules can use consistent data formats.

## Acceptance Criteria

- [ ] KeyProto message defined with key_type and key_data fields
- [ ] ValueProto message defined with value_type and value_data fields
- [ ] KeyValuePairProto message defined with key and entry_value (oneof)
- [ ] SegmentLocationProto message defined (24 bytes fixed size)
- [ ] All messages have documentation comments
- [ ] Protobuf file compiles without errors
- [ ] Java classes generated successfully

## Technical Details

### File: keyvalue.proto

```protobuf
syntax = "proto3";

package org.hyperkv.lsmplus.proto;

import "common.proto";

message KeyProto {
    KeyType key_type = 1;
    bytes key_data = 2;
}

message ValueProto {
    ValueType value_type = 1;
    bytes value_data = 2;
}

message KeyValuePairProto {
    KeyProto key = 1;
    oneof entry_value {
        ValueProto value = 2;
        SegmentLocationProto location = 3;
    }
}

message SegmentLocationProto {
    int64 chunk_id_most_sig = 1;
    int64 chunk_id_least_sig = 2;
    int32 offset = 3;
    int32 length = 4;
}
```

## Implementation Notes

1. Create `src/main/proto/keyvalue.proto`
2. Import common.proto for enum types
3. Ensure SegmentLocationProto is exactly 24 bytes
4. Add documentation comments

## Testing

- Verify message compiles
- Check generated Java classes
- Validate SegmentLocationProto is 24 bytes (16 bytes UUID + 4 bytes offset + 4 bytes length)

## Effort Estimate

0.5 day
