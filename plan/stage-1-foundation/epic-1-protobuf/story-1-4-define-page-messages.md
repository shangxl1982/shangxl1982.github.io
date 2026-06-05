# Story 1-4: Define Page Messages

## Story

As a developer, I want to define Page Protobuf messages so that B+Tree pages can be serialized and persisted.

## Acceptance Criteria

- [ ] PageProto message defined with page_type, page_id, max_size, used_size, entries
- [ ] PageType enum used (LEAF, INDEX)
- [ ] Entries use KeyValuePairProto
- [ ] Leaf page entries use value field
- [ ] Index page entries use location field
- [ ] Protobuf file compiles without errors

## Technical Details

### File: page.proto

```protobuf
syntax = "proto3";

package org.hyperkv.lsmplus.proto;

import "common.proto";
import "keyvalue.proto";

message PageProto {
    PageType page_type = 1;
    int32 page_id = 2;
    int32 max_size = 3;
    int32 used_size = 4;
    repeated KeyValuePairProto entries = 5;
}
```

## Testing

- Create LeafPage with value entries
- Create IndexPage with location entries
- Verify serialization round-trip

## Effort Estimate

0.5 day
