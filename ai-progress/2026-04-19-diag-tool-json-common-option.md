# Diagnostic Tool JSON Output Enhancement

## Changes

### Made JSON output a common option using picocli @Mixin
- Created `JsonOption` inner class with `--json` option
- Added `@Mixin JsonOption` to all commands that support JSON output
- Added `setJsonOutput()` helper method to set the static flag

### Commands with JSON support
All commands now support `-j, --json` option:
- `tree-meta` - Tree metadata
- `chunk-meta` - Chunk metadata  
- `journal-region` - Journal region metadata
- `chunk` - Chunk content parsing
- `all` - All metadata and chunks
- `tree-traverse` - Tree traversal
- `tree-path` - Tree path following

### Fixed chunk command JSON output
Refactored `parseChunkFile` method to support JSON output:
- Created data classes: `ChunkInfo`, `WriteItemInfo`, `JournalEntryInfo`, `PageInfo`, `EntryDetailInfo`
- Added `parseWriteItemBodyToInfo()` to parse write item body into structured data
- Added `printChunkInfoJson()` for JSON output
- Added `printChunkInfoText()` for text output
- Added helper methods for JSON building: `appendJournalEntryJson()`, `appendPageInfoJson()`, `appendEntryDetailJson()`, `escapeJson()`

### Fixed class name conflicts
- Renamed tree-path classes to avoid conflict with chunk parsing classes:
  - `PageInfo` -> `TreePathPageInfo` (for tree-path)
  - `EntryInfo` -> `TreePathEntryInfo` (for tree-path)
  - `PageInfo` and `EntryDetailInfo` remain for chunk parsing

## Usage Examples

```bash
# JSON output for tree metadata
diag-tool tree-meta /data --json

# JSON output for chunk content
diag-tool chunk /data/data/chunk_xxx.dat --json

# JSON output with detail
diag-tool chunk /data/data/chunk_xxx.dat --json --detail

# JSON output for tree path
diag-tool tree-path /data -p "0-1-2" --json
```

## JSON Output Format for Chunk

```json
{
  "file": "/path/to/chunk.dat",
  "size": 1048576,
  "header": {
    "chunkId": "uuid-string",
    "chunkType": "TREE_DATA",
    "ownerId": "uuid-string",
    "namespaceId": "uuid-string",
    "validDataSize": 524288
  },
  "totalItems": 10,
  "items": [
    {
      "index": 0,
      "offset": 64,
      "type": "PAGE_DATA",
      "typeCode": 2,
      "bodyLength": 4096,
      "totalSize": 4104,
      "crc32": "0x12345678",
      "pageData": {
        "pageId": 1,
        "pageType": "PAGE_INDEX",
        "usedSize": 4000,
        "maxEntries": 100,
        "entryCount": 50,
        "entries": [
          {
            "keyType": "ORDERED_BYTES",
            "keyData": "base64-encoded",
            "location": {
              "chunkId": "uuid-string",
              "offset": 8192,
              "length": 2048
            }
          }
        ]
      }
    }
  ]
}
```
