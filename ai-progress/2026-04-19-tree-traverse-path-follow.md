# Tree Traversal and Path Following Features Added to Diagnostic Tool

## Changes

### DiagnosticTool.java

1. **Tree Traverse Function** (`tree-traverse`)
   - Loads any version of the tree from tree-metadata.pb
   - Traverses the tree level by level, starting from root
   - Shows detailed page content including:
     - Chunk type and ID
     - Write item type and body length
     - Page ID, type, used size, max entries
     - All entries with keys and values/locations
   - Only loads tree data without loading any journal content
   - **Flag-based parameters:**
     - `-d, --data-dir <dir>` - Data directory (required)
     - `-v, --version <num>` - Tree version (default: latest)
     - `-l, --leaf` - Show leaf page content
     - `-h, --help` - Show help message

2. **Tree Path Follow Function** (`tree-path`)
   - Accepts a path like "2-12-22" to navigate through the tree
   - Displays page content at each step along the path
   - If no path provided, displays only the root page content
   - Path format: `childIdx-childIdx-childIdx...`
   - Shows chunk header from offset 0, then reads write item from given offset
   - **Flag-based parameters:**
     - `-d, --data-dir <dir>` - Data directory (required)
     - `-v, --version <num>` - Tree version (default: latest)
     - `-p, --path <path>` - Path to follow (e.g., 0-1-2)
     - `-h, --help` - Show help message

3. **Fixed Page Reading Logic**
   - Chunk header is read from offset 0 of the chunk file
   - The offset parameter points to a WriteItem within the chunk
   - WriteItem header is parsed to get body length
   - Page data is parsed from WriteItem body

4. **printPageContent() Enhancement**
   - Added `showLeaf` parameter
   - When `showLeaf` is false and page is a leaf, shows summary only
   - Displays message "(Leaf content hidden - use --leaf to show)"

## Technical Details

- Both functions use the same approach for reading pages:
  1. Read chunk header from offset 0
  2. Seek to the write item offset
  3. Parse write item header (magic, type, body length)
  4. Read and parse page data from write item body

- Tree traversal recursively visits all child pages for index pages
- Path following iteratively navigates through specified child indices

## Usage Examples

```
# Tree traverse
java -jar tools.jar tree-traverse -d demo-kvstore
java -jar tools.jar tree-traverse -d demo-kvstore -l
java -jar tools.jar tree-traverse -d demo-kvstore -v 3 -l
java -jar tools.jar tree-traverse demo-kvstore -l              # positional data-dir also works

# Tree path follow
java -jar tools.jar tree-path -d demo-kvstore                  # Root page only (latest version)
java -jar tools.jar tree-path -d demo-kvstore -p 0             # Path 0 (latest version)
java -jar tools.jar tree-path -d demo-kvstore -v 5 -p 0        # Version 5, path 0
java -jar tools.jar tree-path -d demo-kvstore -p 100-50        # Path 100-50 (latest version)
java -jar tools.jar tree-path demo-kvstore -p 0                # positional data-dir also works

# Help
java -jar tools.jar tree-traverse --help
java -jar tools.jar tree-path --help
```
