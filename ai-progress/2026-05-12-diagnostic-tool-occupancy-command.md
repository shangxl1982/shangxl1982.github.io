# 2026-05-12 - Add Occupancy Checking Functionality to Diagnostic Tool

## Summary
Added a new `occupancy` command to the DiagnosticTool that allows users to check occupancy metadata for each tree version dump. This command reads and displays occupancy files stored in the `occupancy/` directory.

## Changes Made

### 1. DiagnosticTool.java
- **Added OccupancyCommand**: New subcommand `occupancy` to read and display occupancy metadata
  - Supports `-v/--version` option to check a specific tree version
  - Supports `-j/--json` option for JSON output format
  - Reads occupancy files from `<dataDir>/occupancy/` directory
  - Displays MNS (Maximum Namespace Size), deltas, and decommission pages

- **Added readOccupancyMetadata method**: Implements the core functionality
  - Scans occupancy directory for `.pb` files
  - Parses `OccupancyRecord` protobuf messages
  - Displays information in both text and JSON formats
  - Shows occupancy deltas (write/decommission operations)
  - Shows decommission page details

- **Fixed pre-existing compilation errors**:
  - Removed `maxEntries` field from `PageInfo` and `TreePathPageInfo` classes (not present in PageProto)
  - Changed `PAGE_INDEX` to check for `PAGE_BRANCH` or `PAGE_ROOT` (correct enum values)
  - Added missing `Arrays` import

### 2. DiagnosticToolOccupancyTest.java
- Created comprehensive test suite for the new occupancy command
- Tests include:
  - Handling missing occupancy directory
  - Handling empty occupancy directory
  - Reading multiple occupancy files
  - Filtering by specific version
  - JSON output format

## Usage

### Basic usage (all versions):
```bash
./gradlew :tools:run --args="occupancy /path/to/data/dir"
```

### Check specific version:
```bash
./gradlew :tools:run --args="occupancy /path/to/data/dir -v 2"
```

### JSON output:
```bash
./gradlew :tools:run --args="occupancy /path/to/data/dir -j"
```

## Output Format

### Text Format:
```
=== Occupancy Metadata ===
Directory: /path/to/data/dir/occupancy
File Count: 3

--- Tree Version #1 ---
MNS: 1000
Timestamp: 2026-05-12 10:30:45.123
Delta Count: 2
Occupancy Deltas:
  Delta #1:
    Chunk ID: 123e4567-e89b-12d3-a456-426614174000
    Delta Size: 4.00 KB (write)
  Delta #2:
    Chunk ID: 987e6543-e21b-32d1-b654-746325174000
    Delta Size: 2.00 KB (decommission)
Decommission Pages: 1
  Page #1:
    Chunk ID: 123e4567-e89b-12d3-a456-426614174000
    Offset: 100
    Length: 512 B
```

### JSON Format:
```json
{
  "directory": "/path/to/data/dir/occupancy",
  "fileCount": 3,
  "records": [
    {
      "version": 1,
      "mns": 1000,
      "timestamp": "2026-05-12 10:30:45.123",
      "deltaCount": 2,
      "deltas": [
        {
          "chunkId": "123e4567-e89b-12d3-a456-426614174000",
          "deltaSize": 4096
        },
        {
          "chunkId": "987e6543-e21b-32d1-b654-746325174000",
          "deltaSize": -2048
        }
      ],
      "decommissionPageCount": 1,
      "decommissionPages": [
        {
          "chunkId": "123e4567-e89b-12d3-a456-426614174000",
          "offset": 100,
          "length": 512
        }
      ]
    }
  ]
}
```

## Testing
- All tests pass successfully
- Verified with `DiagnosticToolOccupancyTest` test suite
- Tested with both text and JSON output formats
- Tested version filtering functionality

## Related Files
- [DiagnosticTool.java](../tools/src/main/java/org/hyperkv/lsmplus/tools/DiagnosticTool.java)
- [DiagnosticToolOccupancyTest.java](../tools/src/test/java/org/hyperkv/lsmplus/tools/DiagnosticToolOccupancyTest.java)
- [OccupancyFile.java](../lsmplus-storage/src/main/java/org/hyperkv/lsmplus/storage/OccupancyFile.java)
- [OccupancyManager.java](../lsmplus-kvstore/src/main/java/org/hyperkv/lsmplus/gc/OccupancyManager.java)
