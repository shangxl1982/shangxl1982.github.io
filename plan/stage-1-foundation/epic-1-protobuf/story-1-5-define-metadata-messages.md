# Story 1-5: Define Metadata Messages

## Story

As a developer, I want to define metadata Protobuf messages so that system state can be persisted and recovered.

## Acceptance Criteria

- [ ] TreeMetadataFile message defined with magic, format_version, entries list
- [ ] TreeMetadataEntry message defined with version, root_location, replay_point, mns, stats
- [ ] JournalRegionIndex message defined
- [ ] JournalRegionEntry message defined
- [ ] ChunkMetadataFile message defined
- [ ] ChunkMetadata message defined
- [ ] OccupancyRecord message defined
- [ ] BackupMetadata message defined
- [ ] All messages compile without errors

## Technical Details

### File: metadata.proto

```protobuf
syntax = "proto3";

package org.hyperkv.lsmplus.proto;

import "common.proto";
import "keyvalue.proto";

message TreeMetadataFile {
    int32 magic = 1;
    int32 format_version = 2;
    int32 leaf_page_max_size = 3;
    int32 index_page_max_size = 4;
    repeated TreeMetadataEntry entries = 5;
    int32 max_versions = 6;
}

message TreeMetadataEntry {
    int64 version = 1;
    SegmentLocationProto root_location = 2;
    JournalReplayPointProto replay_point = 3;
    int64 mns = 4;
    int64 created_at = 5;
    TreeStats stats = 6;
}

message TreeStats {
    int64 leaf_page_count = 1;
    int64 index_page_count = 2;
    int64 total_entries = 3;
    int32 height = 4;
    int64 total_size = 5;
}

message JournalRegionIndex {
    int32 magic = 1;
    int32 format_version = 2;
    int64 instance_id_most_sig = 3;
    int64 instance_id_least_sig = 4;
    repeated JournalRegionEntry entries = 5;
}

message JournalRegionEntry {
    int64 region_major = 1;
    int64 region_minor = 2;
    int64 chunk_id_most_sig = 3;
    int64 chunk_id_least_sig = 4;
    int32 offset = 5;
    int32 length = 6;
    int64 created_at = 7;
}

message ChunkMetadataFile {
    int32 magic = 1;
    int32 format_version = 2;
    repeated ChunkMetadata chunks = 3;
}

message ChunkMetadata {
    int64 chunk_id_most_sig = 1;
    int64 chunk_id_least_sig = 2;
    int64 chunk_number = 3;
    ChunkType chunk_type = 4;
    int64 owner_id_most_sig = 5;
    int64 owner_id_least_sig = 6;
    int64 namespace_id_most_sig = 7;
    int64 namespace_id_least_sig = 8;
    ChunkStatus status = 9;
    int64 created_at = 10;
    int64 keep_alive_time = 11;
    int64 total_size = 12;
    int64 used_size = 13;
    int64 occupancy_size = 14;
    int32 pending_gc = 15;
}

message OccupancyRecord {
    int64 version = 1;
    int64 mns = 2;
    int64 timestamp = 3;
    repeated OccupancyDelta deltas = 4;
    repeated DecommissionPage decommission_pages = 5;
}

message OccupancyDelta {
    int64 chunk_id_most_sig = 1;
    int64 chunk_id_least_sig = 2;
    int64 delta_size = 3;
}

message DecommissionPage {
    int64 chunk_id_most_sig = 1;
    int64 chunk_id_least_sig = 2;
    int32 offset = 3;
    int32 length = 4;
}
```

## Effort Estimate

1 day
