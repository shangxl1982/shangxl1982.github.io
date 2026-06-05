# Diagnostic Tool tree-path Enhancement

## Changes

### Added JSON output support for tree-path command
- Added `-j, --json` option to output in JSON format
- JSON output includes all page information in a structured format

### Added --all option for showing pages in path
- Added `-a, --all` option to show all pages in the path
- Default behavior (without --all): shows only the last page in the path
- With --all: shows all pages from root to the target page

### Modified files
- `tools/src/main/java/org/hyperkv/lsmplus/tools/DiagnosticTool.java`
  - Updated `TreePathCommand` class with new options
  - Refactored `followTreePath` method to collect page info and output at the end
  - Added `PageInfo` and `EntryInfo` inner classes for structured data
  - Added `printTreePathJson` method for JSON output
  - Added `printTreePathText` method for text output
  - Added helper methods: `formatKeyData`, `bytesToBase64`

## Usage Examples

```bash
# Show only the last page in path (default)
java -jar tools.jar tree-path /data -p "0-1-2"

# Show all pages in path
java -jar tools.jar tree-path /data -p "0-1-2" --all

# JSON output (last page only)
java -jar tools.jar tree-path /data -p "0-1-2" --json

# JSON output (all pages)
java -jar tools.jar tree-path /data -p "0-1-2" --json --all
```

## JSON Output Format

```json
{
  "pages": [
    {
      "level": 1,
      "pageId": 123,
      "pageType": "PAGE_INDEX",
      "usedSize": 1024,
      "maxEntries": 100,
      "entryCount": 50,
      "location": {
        "chunkId": "uuid-string",
        "offset": 4096
      },
      "isLastPage": false,
      "entries": [
        {
          "index": 0,
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
  ]
}
```
