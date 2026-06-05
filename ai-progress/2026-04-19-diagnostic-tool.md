# Diagnostic Tool Implementation

Date: 2026-04-19

## Summary

Created a diagnostic tools module for HyperKVStore that can read and parse all metadata files and chunk contents.

## New Module Structure

```
tools/
├── build.gradle.kts
└── src/main/java/org/hyperkv/lsmplus/tools/
    └── DiagnosticTool.java
```

## Features

### Commands

| Command | Description |
|---------|-------------|
| `tree-meta <data-dir>` | Read and display tree metadata |
| `chunk-meta <data-dir>` | Read and display chunk metadata |
| `journal-region <data-dir>` | Read and display journal region metadata |
| `chunk <chunk-file> [detail]` | Parse and display chunk contents |
| `all <data-dir> [detail]` | Read all metadata and parse all chunks |
| `help` | Show help message |

### Metadata Parsing

1. **Tree Metadata** (`tree-metadata.pb`)
   - Magic and format version
   - Tree versions with root location, replay point, MNS
   - Statistics (leaf pages, index pages, entries, height, size)

2. **Chunk Metadata** (`chunk-metadata.pb`)
   - Magic and format version
   - Chunks grouped by type (INDEX, LEAF, JOURNAL)
   - Chunk ID, owner, namespace, status, sizes, timestamps

3. **Journal Region Metadata** (`journal-region.pb`)
   - Magic and format version
   - Instance ID
   - Region entries with chunk ID, offset, length

### Chunk Parsing

Parses all three chunk types:
- **INDEX chunks**: Contains index pages with key-location pairs
- **LEAF chunks**: Contains leaf pages with key-value pairs
- **JOURNAL chunks**: Contains journal entries with operations

For each write item:
- Header: magic, type, body length, CRC32
- Body: parsed based on type (journal entry or page data)
- Detail mode: shows full entry data including keys and values

## Usage Examples

```bash
# Build the tool
./gradlew :tools:jar

# Show help
java -jar tools/build/libs/tools.jar help

# Read tree metadata
java -jar tools/build/libs/tools.jar tree-meta /path/to/kvstore

# Parse a chunk with detail
java -jar tools/build/libs/tools.jar chunk /path/to/chunk_uuid.dat detail

# Full diagnostic report
java -jar tools/build/libs/tools.jar all /path/to/kvstore detail
```

## Files Changed

1. `settings.gradle.kts` - Added tools module
2. `tools/build.gradle.kts` - New build configuration
3. `tools/src/main/java/org/hyperkv/lsmplus/tools/DiagnosticTool.java` - Main tool class

## Test Results

```
BUILD SUCCESSFUL in 7s
34 actionable tasks: 34 up-to-date
```
