package org.hyperkv.lsmplus.tools;

import org.hyperkv.lsmplus.proto.Common;
import org.hyperkv.lsmplus.proto.Journal;
import org.hyperkv.lsmplus.proto.Keyvalue;
import org.hyperkv.lsmplus.proto.Metadata;
import org.hyperkv.lsmplus.proto.Page;
import org.hyperkv.lsmplus.storage.ChunkHeader;
import org.hyperkv.lsmplus.storage.WriteItem;
import picocli.CommandLine;
import picocli.CommandLine.Command;
import picocli.CommandLine.Mixin;
import picocli.CommandLine.Option;
import picocli.CommandLine.Parameters;

import java.io.File;
import java.io.FileOutputStream;
import java.io.IOException;
import java.io.RandomAccessFile;
import java.nio.ByteBuffer;
import java.nio.ByteOrder;
import java.nio.file.Files;
import java.nio.file.StandardCopyOption;
import java.text.SimpleDateFormat;
import java.util.ArrayList;
import java.util.Arrays;
import java.util.Date;
import java.util.List;
import java.util.Map;
import java.util.TreeMap;
import java.util.UUID;
import java.util.concurrent.Callable;

@Command(name = "diag-tool", mixinStandardHelpOptions = true,
        description = "HyperKVStore Diagnostic Tool",
        subcommands = {
                DiagnosticTool.TreeMetaCommand.class,
                DiagnosticTool.TreeRevertCommand.class,
                DiagnosticTool.ChunkMetaCommand.class,
                DiagnosticTool.JournalRegionCommand.class,
                DiagnosticTool.ChunkCommand.class,
                DiagnosticTool.AllCommand.class,
                DiagnosticTool.TreeTraverseCommand.class,
                DiagnosticTool.TreePathCommand.class,
                DiagnosticTool.OccupancyCommand.class
        })
public class DiagnosticTool implements Callable<Integer> {

    private static final SimpleDateFormat DATE_FORMAT = new SimpleDateFormat("yyyy-MM-dd HH:mm:ss.SSS");
    private static boolean jsonOutputStatic = false;

    public static class JsonOption {
        @Option(names = {"-j", "--json"}, description = "Output in JSON format")
        boolean jsonOutput;

        public boolean isJsonOutput() {
            return jsonOutput;
        }
    }

    public static void main(String[] args) {
        int exitCode = new CommandLine(new DiagnosticTool()).execute(args);
        System.exit(exitCode);
    }

    @Override
    public Integer call() {
        CommandLine.usage(this, System.out);
        return 0;
    }

    static boolean isJsonOutput() {
        return jsonOutputStatic;
    }

    static void setJsonOutput(boolean json) {
        jsonOutputStatic = json;
    }

    @Command(name = "tree-meta", description = "Read and display tree metadata")
    static class TreeMetaCommand implements Callable<Integer> {
        @Parameters(index = "0", description = "Data directory containing tree-metadata.pb")
        File dataDir;

        @Mixin
        JsonOption jsonOption = new JsonOption();

        @Override
        public Integer call() throws IOException {
            setJsonOutput(jsonOption.jsonOutput);
            return readTreeMetadata(dataDir) ? 0 : 1;
        }
    }

    @Command(name = "tree-revert", description = "Remove the latest tree metadata entry to revert to previous version")
    static class TreeRevertCommand implements Callable<Integer> {
        @Parameters(index = "0", description = "Data directory containing tree-metadata.pb")
        File dataDir;

        @Option(names = {"-n", "--dry-run"}, description = "Show what would be removed without actually removing")
        boolean dryRun;

        @Mixin
        JsonOption jsonOption = new JsonOption();

        @Override
        public Integer call() throws IOException {
            setJsonOutput(jsonOption.jsonOutput);
            return revertTreeMetadata(dataDir, dryRun) ? 0 : 1;
        }
    }

    @Command(name = "chunk-meta", description = "Read and display chunk metadata")
    static class ChunkMetaCommand implements Callable<Integer> {
        @Parameters(index = "0", description = "Data directory containing chunk-metadata.pb")
        File dataDir;

        @Mixin
        JsonOption jsonOption = new JsonOption();

        @Override
        public Integer call() throws IOException {
            setJsonOutput(jsonOption.jsonOutput);
            return readChunkMetadata(dataDir) ? 0 : 1;
        }
    }

    @Command(name = "journal-region", description = "Read and display journal region metadata")
    static class JournalRegionCommand implements Callable<Integer> {
        @Parameters(index = "0", description = "Data directory")
        File dataDir;

        @Mixin
        JsonOption jsonOption = new JsonOption();

        @Override
        public Integer call() throws IOException {
            setJsonOutput(jsonOption.jsonOutput);
            return readJournalRegionMetadata(dataDir) ? 0 : 1;
        }
    }

    @Command(name = "chunk", description = "Parse and display chunk contents")
    static class ChunkCommand implements Callable<Integer> {
        @Parameters(index = "0", description = "Chunk file path")
        File chunkFile;

        @Option(names = {"-d", "--detail"}, description = "Show full entry data")
        boolean detail;

        @Mixin
        JsonOption jsonOption = new JsonOption();

        @Override
        public Integer call() throws IOException {
            setJsonOutput(jsonOption.jsonOutput);
            if (!chunkFile.exists()) {
                if (isJsonOutput()) {
                    System.out.println("{\"error\":\"Chunk file not found: " + chunkFile + "\"}");
                } else {
                    System.out.println("Chunk file not found: " + chunkFile);
                }
                return 1;
            }
            parseChunkFile(chunkFile, detail);
            return 0;
        }
    }

    @Command(name = "all", description = "Read all metadata and parse all chunks")
    static class AllCommand implements Callable<Integer> {
        @Parameters(index = "0", description = "Data directory")
        File dataDir;

        @Option(names = {"-d", "--detail"}, description = "Show full entry data")
        boolean detail;

        @Mixin
        JsonOption jsonOption = new JsonOption();

        @Override
        public Integer call() throws IOException {
            setJsonOutput(jsonOption.jsonOutput);
            readAll(dataDir, detail);
            return 0;
        }
    }

    @Command(name = "tree-traverse", description = "Traverse tree level by level")
    static class TreeTraverseCommand implements Callable<Integer> {
        @Parameters(index = "0", description = "Data directory containing tree-metadata.pb")
        File dataDir;

        @Option(names = {"-v", "--version"}, description = "Tree version to traverse (default: latest)")
        int version = -1;

        @Option(names = {"-l", "--leaf"}, description = "Show leaf page content")
        boolean showLeaf;

        @Mixin
        JsonOption jsonOption = new JsonOption();

        @Override
        public Integer call() throws IOException {
            setJsonOutput(jsonOption.jsonOutput);
            return traverseTree(dataDir, version, showLeaf) ? 0 : 1;
        }
    }

    @Command(name = "tree-path", description = "Follow path through tree")
    static class TreePathCommand implements Callable<Integer> {
        @Parameters(index = "0", description = "Data directory containing tree-metadata.pb")
        File dataDir;

        @Option(names = {"-v", "--version"}, description = "Tree version (default: latest)")
        int version = -1;

        @Option(names = {"-p", "--path"}, description = "Path to follow (e.g., 0-1-2 for child 0 -> child 1 -> child 2)")
        String pathStr;

        @Mixin
        JsonOption jsonOption = new JsonOption();

        @Option(names = {"-a", "--all"}, description = "Show all pages in path (default: only last page)")
        boolean showAllPages;

        @Override
        public Integer call() throws IOException {
            setJsonOutput(jsonOption.jsonOutput);
            int[] path = null;
            if (pathStr != null && !pathStr.isEmpty()) {
                String[] parts = pathStr.split("-");
                path = new int[parts.length];
                try {
                    for (int i = 0; i < parts.length; i++) {
                        path[i] = Integer.parseInt(parts[i]);
                    }
                } catch (NumberFormatException e) {
                    System.err.println("Error: Invalid path format: " + pathStr);
                    return 1;
                }
            }
            return followTreePath(dataDir, version, path, showAllPages) ? 0 : 1;
        }
    }

    @Command(name = "occupancy", description = "Read and display occupancy metadata for tree versions")
    static class OccupancyCommand implements Callable<Integer> {
        @Parameters(index = "0", description = "Data directory containing occupancy files")
        File dataDir;

        @Option(names = {"-v", "--version"}, description = "Specific tree version to check (default: all versions)")
        long version = -1;

        @Mixin
        JsonOption jsonOption = new JsonOption();

        @Override
        public Integer call() throws IOException {
            setJsonOutput(jsonOption.jsonOutput);
            return readOccupancyMetadata(dataDir, version) ? 0 : 1;
        }
    }

    private static boolean readTreeMetadata(File dataDir) throws IOException {
        File treeMetaFile = new File(dataDir, "tree-metadata.pb");

        if (!treeMetaFile.exists()) {
            System.out.println("Tree metadata file not found: " + treeMetaFile);
            return false;
        }

        byte[] data = Files.readAllBytes(treeMetaFile.toPath());
        Metadata.TreeMetadataFile treeMeta = Metadata.TreeMetadataFile.parseFrom(data);

        if (jsonOutputStatic) {
            StringBuilder json = new StringBuilder();
            json.append("{");
            json.append("\"file\":\"").append(treeMetaFile).append("\",");
            json.append("\"magic\":\"0x").append(Integer.toHexString(treeMeta.getMagic())).append("\",");
            json.append("\"formatVersion\":").append(treeMeta.getFormatVersion()).append(",");
            json.append("\"entryCount\":").append(treeMeta.getEntriesCount()).append(",");
            json.append("\"entries\":[");
            
            for (int i = 0; i < treeMeta.getEntriesCount(); i++) {
                Metadata.TreeMetadataEntry entry = treeMeta.getEntries(i);
                if (i > 0) json.append(",");
                json.append("{");
                json.append("\"version\":").append(entry.getVersion()).append(",");
                
                if (entry.hasRootLocation()) {
                    Keyvalue.SegmentLocationProto loc = entry.getRootLocation();
                    json.append("\"rootLocation\":{");
                    json.append("\"chunkId\":\"").append(formatUUID(loc.getChunkIdMostSig(), loc.getChunkIdLeastSig())).append("\",");
                    json.append("\"offset\":").append(loc.getOffset()).append(",");
                    json.append("\"length\":").append(loc.getLength());
                    json.append("},");
                }
                
                if (entry.hasReplayPoint()) {
                    Journal.JournalReplayPointProto rp = entry.getReplayPoint();
                    json.append("\"replayPoint\":{");
                    json.append("\"regionMajor\":").append(rp.getRegionMajor()).append(",");
                    json.append("\"regionMinor\":").append(rp.getRegionMinor()).append(",");
                    json.append("\"offset\":").append(rp.getOffset());
                    json.append("},");
                }
                
                json.append("\"mns\":").append(entry.getMns()).append(",");
                json.append("\"createdAt\":\"").append(formatTimestamp(entry.getCreatedAt())).append("\",");
                
                if (entry.hasStats()) {
                    Metadata.TreeStats stats = entry.getStats();
                    json.append("\"stats\":{");
                    json.append("\"leafPageCount\":").append(stats.getLeafPageCount()).append(",");
                    json.append("\"indexPageCount\":").append(stats.getIndexPageCount()).append(",");
                    json.append("\"totalEntries\":").append(stats.getTotalEntries()).append(",");
                    json.append("\"height\":").append(stats.getHeight()).append(",");
                    json.append("\"totalSize\":").append(stats.getTotalSize());
                    json.append("}");
                }
                json.append("}");
            }
            json.append("]}");
            System.out.println(json);
            return true;
        }

        System.out.println("=== Tree Metadata ===");
        System.out.println("File: " + treeMetaFile);
        System.out.println();

        System.out.println("Magic: 0x" + Integer.toHexString(treeMeta.getMagic()));
        System.out.println("Format Version: " + treeMeta.getFormatVersion());
        System.out.println("Entry Count: " + treeMeta.getEntriesCount());
        System.out.println();

        for (int i = 0; i < treeMeta.getEntriesCount(); i++) {
            Metadata.TreeMetadataEntry entry = treeMeta.getEntries(i);
            System.out.println("--- Tree Version #" + (i + 1) + " ---");
            System.out.println("Version: " + entry.getVersion());
            
            if (entry.hasRootLocation()) {
                Keyvalue.SegmentLocationProto loc = entry.getRootLocation();
                System.out.println("Root Location:");
                System.out.println("  Chunk ID: " + formatUUID(loc.getChunkIdMostSig(), loc.getChunkIdLeastSig()));
                System.out.println("  Offset: " + loc.getOffset());
                System.out.println("  Length: " + loc.getLength());
            }

            if (entry.hasReplayPoint()) {
                Journal.JournalReplayPointProto rp = entry.getReplayPoint();
                System.out.println("Replay Point: region=" + rp.getRegionMajor() + "." + rp.getRegionMinor() + ", offset=" + rp.getOffset());
            }

            System.out.println("MNS: " + entry.getMns());
            System.out.println("Created At: " + formatTimestamp(entry.getCreatedAt()));

            if (entry.hasStats()) {
                Metadata.TreeStats stats = entry.getStats();
                System.out.println("Stats:");
                System.out.println("  Leaf Pages: " + stats.getLeafPageCount());
                System.out.println("  Index Pages: " + stats.getIndexPageCount());
                System.out.println("  Total Entries: " + stats.getTotalEntries());
                System.out.println("  Height: " + stats.getHeight());
                System.out.println("  Total Size: " + formatSize(stats.getTotalSize()));
            }
            System.out.println();
        }
        return true;
    }

    private static boolean revertTreeMetadata(File dataDir, boolean dryRun) throws IOException {
        File treeMetaFile = new File(dataDir, "tree-metadata.pb");

        if (!treeMetaFile.exists()) {
            if (jsonOutputStatic) {
                System.out.println("{\"error\":\"Tree metadata file not found: " + treeMetaFile + "\"}");
            } else {
                System.out.println("Tree metadata file not found: " + treeMetaFile);
            }
            return false;
        }

        byte[] data = Files.readAllBytes(treeMetaFile.toPath());
        Metadata.TreeMetadataFile treeMeta = Metadata.TreeMetadataFile.parseFrom(data);

        if (treeMeta.getEntriesCount() == 0) {
            if (jsonOutputStatic) {
                System.out.println("{\"error\":\"No tree metadata entries to remove\"}");
            } else {
                System.out.println("No tree metadata entries to remove");
            }
            return false;
        }

        if (treeMeta.getEntriesCount() == 1) {
            if (jsonOutputStatic) {
                System.out.println("{\"error\":\"Only one entry exists, cannot remove\"}");
            } else {
                System.out.println("Only one entry exists, cannot remove");
            }
            return false;
        }

        Metadata.TreeMetadataEntry currentEntry = treeMeta.getEntries(0);
        Metadata.TreeMetadataEntry previousEntry = treeMeta.getEntries(1);

        if (jsonOutputStatic) {
            System.out.println("{\"action\":\"revert\",\"dryRun\":" + dryRun + ",");
            System.out.println("\"currentVersion\":" + currentEntry.getVersion() + ",");
            System.out.println("\"previousVersion\":" + previousEntry.getVersion() + ",");
            System.out.println("\"remainingEntries\":" + (treeMeta.getEntriesCount() - 1));
            System.out.println("}");
        } else {
            System.out.println("=== Tree Metadata Revert ===");
            System.out.println("File: " + treeMetaFile);
            System.out.println();
            System.out.println("Current version to remove: " + currentEntry.getVersion());
            if (currentEntry.hasStats()) {
                System.out.println("  Total Entries: " + currentEntry.getStats().getTotalEntries());
            }
            System.out.println();
            System.out.println("Will revert to version: " + previousEntry.getVersion());
            if (previousEntry.hasStats()) {
                System.out.println("  Total Entries: " + previousEntry.getStats().getTotalEntries());
            }
            System.out.println();
            System.out.println("Remaining entries after revert: " + (treeMeta.getEntriesCount() - 1));
            System.out.println();
        }

        if (dryRun) {
            if (!jsonOutputStatic) {
                System.out.println("Dry run - no changes made");
            }
            return true;
        }

        List<Metadata.TreeMetadataEntry> remainingEntries = new ArrayList<>(
            treeMeta.getEntriesList().subList(1, treeMeta.getEntriesCount()));

        Metadata.TreeMetadataFile newFile = Metadata.TreeMetadataFile.newBuilder()
                .setMagic(treeMeta.getMagic())
                .setFormatVersion(treeMeta.getFormatVersion())
                .addAllEntries(remainingEntries)
                .build();

        File tempFile = new File(treeMetaFile.getParentFile(), treeMetaFile.getName() + ".tmp");
        try (FileOutputStream fos = new FileOutputStream(tempFile)) {
            newFile.writeTo(fos);
            fos.getFD().sync();
        }
        Files.move(tempFile.toPath(), treeMetaFile.toPath(), StandardCopyOption.REPLACE_EXISTING);

        if (jsonOutputStatic) {
            System.out.println("{\"success\":true,\"message\":\"Tree metadata reverted successfully\"}");
        } else {
            System.out.println("Tree metadata reverted successfully");
            System.out.println("Removed version: " + currentEntry.getVersion());
            System.out.println("Current version: " + previousEntry.getVersion());
        }
        return true;
    }

    private static boolean readChunkMetadata(File dataDir) throws IOException {
        File chunkMetaFile = new File(dataDir, "chunk-metadata.pb");

        if (!chunkMetaFile.exists()) {
            System.out.println("Chunk metadata file not found: " + chunkMetaFile);
            return false;
        }

        System.out.println("=== Chunk Metadata ===");
        System.out.println("File: " + chunkMetaFile);
        System.out.println();

        byte[] data = Files.readAllBytes(chunkMetaFile.toPath());
        Metadata.ChunkMetadataFile chunkMeta = Metadata.ChunkMetadataFile.parseFrom(data);

        System.out.println("Magic: 0x" + Integer.toHexString(chunkMeta.getMagic()));
        System.out.println("Format Version: " + chunkMeta.getFormatVersion());
        System.out.println("Chunk Count: " + chunkMeta.getChunksCount());
        System.out.println();

        Map<Common.ChunkType, List<Metadata.ChunkMetadata>> chunksByType = new TreeMap<>();
        for (Metadata.ChunkMetadata chunk : chunkMeta.getChunksList()) {
            chunksByType.computeIfAbsent(chunk.getChunkType(), k -> new ArrayList<>()).add(chunk);
        }

        for (Map.Entry<Common.ChunkType, List<Metadata.ChunkMetadata>> entry : chunksByType.entrySet()) {
            System.out.println("--- " + entry.getKey() + " Chunks (" + entry.getValue().size() + ") ---");
            for (Metadata.ChunkMetadata chunk : entry.getValue()) {
                System.out.println("  Chunk #" + chunk.getChunkNumber());
                System.out.println("    ID: " + formatUUID(chunk.getChunkIdMostSig(), chunk.getChunkIdLeastSig()));
                System.out.println("    Owner: " + formatUUID(chunk.getOwnerIdMostSig(), chunk.getOwnerIdLeastSig()));
                System.out.println("    Namespace: " + formatUUID(chunk.getNamespaceIdMostSig(), chunk.getNamespaceIdLeastSig()));
                System.out.println("    Status: " + chunk.getStatus());
                System.out.println("    Total Size: " + formatSize(chunk.getTotalSize()));
                System.out.println("    Used Size: " + formatSize(chunk.getUsedSize()));
                System.out.println("    Occupancy: " + formatSize(chunk.getOccupancySize()));
                System.out.println("    Created: " + formatTimestamp(chunk.getCreatedAt()));
                System.out.println("    Keep Alive: " + formatTimestamp(chunk.getKeepAliveTime()));
            }
            System.out.println();
        }
        return true;
    }

    private static boolean readJournalRegionMetadata(File dataDir) throws IOException {
        File journalRegionFile = new File(dataDir, "journal-region.pb");

        if (!journalRegionFile.exists()) {
            System.out.println("Journal region metadata file not found: " + journalRegionFile);
            return false;
        }

        System.out.println("=== Journal Region Metadata ===");
        System.out.println("File: " + journalRegionFile);
        System.out.println();

        byte[] data = Files.readAllBytes(journalRegionFile.toPath());
        Metadata.JournalRegionIndex regionIndex = Metadata.JournalRegionIndex.parseFrom(data);

        System.out.println("Magic: 0x" + Integer.toHexString(regionIndex.getMagic()));
        System.out.println("Format Version: " + regionIndex.getFormatVersion());
        System.out.println("Instance ID: " + formatUUID(regionIndex.getInstanceIdMostSig(), regionIndex.getInstanceIdLeastSig()));
        System.out.println("Region Count: " + regionIndex.getEntriesCount());
        System.out.println();

        for (int i = 0; i < regionIndex.getEntriesCount(); i++) {
            Metadata.JournalRegionEntry entry = regionIndex.getEntries(i);
            System.out.println("--- Region #" + (i + 1) + " ---");
            System.out.println("Region: " + entry.getRegionMajor() + "." + entry.getRegionMinor());
            System.out.println("Chunk ID: " + formatUUID(entry.getChunkIdMostSig(), entry.getChunkIdLeastSig()));
            System.out.println("Offset: " + entry.getOffset());
            System.out.println("Length: " + (entry.getLength() == -1 ? "entire chunk" : entry.getLength()));
            System.out.println("Created: " + formatTimestamp(entry.getCreatedAt()));
            System.out.println();
        }
        return true;
    }

    private static void parseChunkFile(File chunkFile, boolean detail) throws IOException {
        ChunkInfo chunkInfo = new ChunkInfo();
        chunkInfo.file = chunkFile.getPath();
        chunkInfo.size = chunkFile.length();

        try (RandomAccessFile raf = new RandomAccessFile(chunkFile, "r")) {
            byte[] headerBytes = new byte[ChunkHeader.HEADER_SIZE];
            raf.readFully(headerBytes);
            ChunkHeader header = ChunkHeader.fromByteArray(headerBytes);

            chunkInfo.chunkId = header.getChunkId().toString();
            chunkInfo.chunkType = header.getChunkType().name();
            chunkInfo.ownerId = header.getOwnerId().toString();
            chunkInfo.namespaceId = header.getNamespaceId().toString();
            chunkInfo.validDataSize = header.getValidDataSize();

            List<WriteItemInfo> items = new ArrayList<>();
            int itemIndex = 0;
            long offset = ChunkHeader.HEADER_SIZE;
            long fileSize = raf.length();

            while (offset < fileSize) {
                raf.seek(offset);
                byte[] headerBuf = new byte[WriteItem.HEADER_SIZE];
                raf.readFully(headerBuf);

                ByteBuffer bb = ByteBuffer.wrap(headerBuf).order(ByteOrder.BIG_ENDIAN);
                short magic = bb.getShort();
                if (magic != WriteItem.MAGIC) {
                    WriteItemInfo item = new WriteItemInfo();
                    item.index = itemIndex;
                    item.offset = offset;
                    item.error = "Invalid magic: 0x" + Integer.toHexString(magic & 0xFFFF);
                    items.add(item);
                    break;
                }

                short type = bb.getShort();
                int bodyLength = bb.getInt();

                int rawSize = WriteItem.HEADER_SIZE + bodyLength + WriteItem.CRC32_SIZE;
                int totalSize = alignUp(rawSize, WriteItem.ALIGNMENT);

                if (offset + totalSize > fileSize) {
                    WriteItemInfo item = new WriteItemInfo();
                    item.index = itemIndex;
                    item.offset = offset;
                    item.error = "Incomplete item";
                    items.add(item);
                    break;
                }

                byte[] body = new byte[bodyLength];
                raf.seek(offset + WriteItem.HEADER_SIZE);
                raf.readFully(body);

                byte[] crcBytes = new byte[WriteItem.CRC32_SIZE];
                raf.readFully(crcBytes);
                int storedCrc = ByteBuffer.wrap(crcBytes).order(ByteOrder.BIG_ENDIAN).getInt();

                WriteItemInfo item = new WriteItemInfo();
                item.index = itemIndex;
                item.offset = offset;
                item.type = formatWriteItemType(type);
                item.typeCode = type;
                item.bodyLength = bodyLength;
                item.totalSize = totalSize;
                item.crc32 = "0x" + Integer.toHexString(storedCrc);

                if (detail) {
                    parseWriteItemBodyToInfo(type, body, header.getChunkType(), item);
                }

                items.add(item);
                itemIndex++;
                offset += totalSize;
            }

            chunkInfo.items = items;
            chunkInfo.totalItems = itemIndex;
        }

        if (isJsonOutput()) {
            printChunkInfoJson(chunkInfo);
        } else {
            printChunkInfoText(chunkInfo);
        }
    }

    private static class ChunkInfo {
        String file;
        long size;
        String chunkId;
        String chunkType;
        String ownerId;
        String namespaceId;
        long validDataSize;
        List<WriteItemInfo> items;
        int totalItems;
    }

    private static class WriteItemInfo {
        int index;
        long offset;
        String type;
        int typeCode;
        int bodyLength;
        int totalSize;
        String crc32;
        String error;
        JournalEntryInfo journalEntry;
        PageInfo pageData;
    }

    private static class JournalEntryInfo {
        String operationType;
        long timestamp;
        long sequenceNumber;
        int entryCount;
        List<EntryDetailInfo> entries;
    }

    private static class PageInfo {
        long pageId;
        String pageType;
        int usedSize;
        int entryCount;
        List<EntryDetailInfo> entries;
    }

    private static class EntryDetailInfo {
        String keyType;
        String keyData;
        boolean hasValue;
        String valueType;
        String valueData;
        boolean hasLocation;
        String locationChunkId;
        long locationOffset;
        long locationLength;
    }

    private static void parseWriteItemBodyToInfo(short type, byte[] body, Common.ChunkType chunkType, WriteItemInfo item) {
        try {
            switch (type) {
                case WriteItem.TYPE_JOURNAL_ENTRY -> {
                    Journal.JournalEntryProto entry = Journal.JournalEntryProto.parseFrom(body);
                    JournalEntryInfo jeInfo = new JournalEntryInfo();
                    jeInfo.operationType = entry.getOperationType().name();
                    jeInfo.timestamp = entry.getTimestamp();
                    jeInfo.sequenceNumber = entry.getSequenceNumber();
                    jeInfo.entryCount = entry.getEntriesCount();
                    
                    List<EntryDetailInfo> entries = new ArrayList<>();
                    for (int i = 0; i < entry.getEntriesCount(); i++) {
                        Keyvalue.KeyValuePairProto kvp = entry.getEntries(i);
                        entries.add(buildEntryDetailInfo(kvp));
                    }
                    jeInfo.entries = entries;
                    item.journalEntry = jeInfo;
                }
                case WriteItem.TYPE_PAGE_DATA, WriteItem.TYPE_INDEX_DATA -> {
                    Page.PageProto page = Page.PageProto.parseFrom(body);
                    PageInfo pageInfo = new PageInfo();
                    pageInfo.pageId = page.getPageId();
                    pageInfo.pageType = page.getPageType().name();
                    pageInfo.usedSize = page.getUsedSize();
                    
                    int entryCount = page.getEntryOffsets().size() / 4;
                    pageInfo.entryCount = entryCount;
                    
                    if (entryCount > 0) {
                        byte[] offsetsBytes = page.getEntryOffsets().toByteArray();
                        byte[] entriesBytes = page.getEntries().toByteArray();

                        ByteBuffer offsetsBuf = ByteBuffer.wrap(offsetsBytes).order(ByteOrder.LITTLE_ENDIAN);
                        int[] offsets = new int[entryCount];
                        for (int i = 0; i < entryCount; i++) {
                            offsets[i] = offsetsBuf.getInt();
                        }

                        List<EntryDetailInfo> entries = new ArrayList<>();
                        for (int i = 0; i < entryCount; i++) {
                            int start = offsets[i];
                            int end = (i + 1 < entryCount) ? offsets[i + 1] : entriesBytes.length;
                            byte[] entryBytes = new byte[end - start];
                            System.arraycopy(entriesBytes, start, entryBytes, 0, entryBytes.length);

                            Keyvalue.KeyValuePairProto kvp = Keyvalue.KeyValuePairProto.parseFrom(entryBytes);
                            entries.add(buildEntryDetailInfo(kvp));
                        }
                        pageInfo.entries = entries;
                    }
                    item.pageData = pageInfo;
                }
                default -> item.error = "Unknown type: " + type;
            }
        } catch (Exception e) {
            item.error = "Error parsing body: " + e.getMessage();
        }
    }

    private static EntryDetailInfo buildEntryDetailInfo(Keyvalue.KeyValuePairProto kvp) {
        EntryDetailInfo info = new EntryDetailInfo();
        
        if (kvp.hasKey()) {
            Keyvalue.KeyProto key = kvp.getKey();
            info.keyType = key.getKeyType().name();
            info.keyData = bytesToBase64(key.getKeyData().toByteArray());
        }
        
        if (kvp.hasValue()) {
            info.hasValue = true;
            Keyvalue.ValueProto value = kvp.getValue();
            info.valueType = value.getValueType().name();
            info.valueData = bytesToBase64(value.getValueData().toByteArray());
        }
        
        if (kvp.hasLocation()) {
            info.hasLocation = true;
            Keyvalue.SegmentLocationProto loc = kvp.getLocation();
            info.locationChunkId = formatUUID(loc.getChunkIdMostSig(), loc.getChunkIdLeastSig());
            info.locationOffset = loc.getOffset();
            info.locationLength = loc.getLength();
        }
        
        return info;
    }

    private static void printChunkInfoJson(ChunkInfo info) {
        StringBuilder json = new StringBuilder();
        json.append("{");
        json.append("\"file\":\"").append(escapeJson(info.file)).append("\",");
        json.append("\"size\":").append(info.size).append(",");
        json.append("\"header\":{");
        json.append("\"chunkId\":\"").append(info.chunkId).append("\",");
        json.append("\"chunkType\":\"").append(info.chunkType).append("\",");
        json.append("\"ownerId\":\"").append(info.ownerId).append("\",");
        json.append("\"namespaceId\":\"").append(info.namespaceId).append("\",");
        json.append("\"validDataSize\":").append(info.validDataSize);
        json.append("},");
        json.append("\"totalItems\":").append(info.totalItems).append(",");
        json.append("\"items\":[");
        
        for (int i = 0; i < info.items.size(); i++) {
            WriteItemInfo item = info.items.get(i);
            if (i > 0) json.append(",");
            json.append("{");
            json.append("\"index\":").append(item.index).append(",");
            json.append("\"offset\":").append(item.offset).append(",");
            
            if (item.error != null) {
                json.append("\"error\":\"").append(escapeJson(item.error)).append("\"");
            } else {
                json.append("\"type\":\"").append(item.type).append("\",");
                json.append("\"typeCode\":").append(item.typeCode).append(",");
                json.append("\"bodyLength\":").append(item.bodyLength).append(",");
                json.append("\"totalSize\":").append(item.totalSize).append(",");
                json.append("\"crc32\":\"").append(item.crc32).append("\"");
                
                if (item.journalEntry != null) {
                    json.append(",\"journalEntry\":");
                    appendJournalEntryJson(json, item.journalEntry);
                }
                
                if (item.pageData != null) {
                    json.append(",\"pageData\":");
                    appendPageInfoJson(json, item.pageData);
                }
            }
            json.append("}");
        }
        
        json.append("]}");
        System.out.println(json);
    }

    private static void appendJournalEntryJson(StringBuilder json, JournalEntryInfo je) {
        json.append("{");
        json.append("\"operationType\":\"").append(je.operationType).append("\",");
        json.append("\"timestamp\":").append(je.timestamp).append(",");
        json.append("\"sequenceNumber\":").append(je.sequenceNumber).append(",");
        json.append("\"entryCount\":").append(je.entryCount).append(",");
        json.append("\"entries\":[");
        
        for (int i = 0; i < je.entries.size(); i++) {
            if (i > 0) json.append(",");
            appendEntryDetailJson(json, je.entries.get(i));
        }
        
        json.append("]}");
    }

    private static void appendPageInfoJson(StringBuilder json, PageInfo page) {
        json.append("{");
        json.append("\"pageId\":").append(page.pageId).append(",");
        json.append("\"pageType\":\"").append(page.pageType).append("\",");
        json.append("\"usedSize\":").append(page.usedSize).append(",");
        json.append("\"entryCount\":").append(page.entryCount).append(",");
        json.append("\"entries\":[");
        
        if (page.entries != null) {
            for (int i = 0; i < page.entries.size(); i++) {
                if (i > 0) json.append(",");
                appendEntryDetailJson(json, page.entries.get(i));
            }
        }
        
        json.append("]}");
    }

    private static void appendEntryDetailJson(StringBuilder json, EntryDetailInfo entry) {
        json.append("{");
        json.append("\"keyType\":\"").append(entry.keyType).append("\",");
        json.append("\"keyData\":\"").append(entry.keyData).append("\"");
        
        if (entry.hasValue) {
            json.append(",\"value\":{");
            json.append("\"type\":\"").append(entry.valueType).append("\",");
            json.append("\"data\":\"").append(entry.valueData).append("\"");
            json.append("}");
        }
        
        if (entry.hasLocation) {
            json.append(",\"location\":{");
            json.append("\"chunkId\":\"").append(entry.locationChunkId).append("\",");
            json.append("\"offset\":").append(entry.locationOffset).append(",");
            json.append("\"length\":").append(entry.locationLength);
            json.append("}");
        }
        
        json.append("}");
    }

    private static String escapeJson(String s) {
        if (s == null) return "";
        return s.replace("\\", "\\\\")
                .replace("\"", "\\\"")
                .replace("\n", "\\n")
                .replace("\r", "\\r")
                .replace("\t", "\\t");
    }

    private static void printChunkInfoText(ChunkInfo info) {
        System.out.println("=== Chunk Content ===");
        System.out.println("File: " + info.file);
        System.out.println("Size: " + formatSize(info.size));
        System.out.println();

        System.out.println("--- Chunk Header ---");
        System.out.println("Chunk ID: " + info.chunkId);
        System.out.println("Type: " + info.chunkType);
        System.out.println("Owner ID: " + info.ownerId);
        System.out.println("Namespace ID: " + info.namespaceId);
        System.out.println("Valid Data Size: " + formatSize(info.validDataSize));
        System.out.println();

        System.out.println("--- Write Items ---");
        for (WriteItemInfo item : info.items) {
            if (item.error != null) {
                System.out.println("Item #" + item.index + " at offset " + item.offset);
                System.out.println("  Error: " + item.error);
                break;
            }
            
            System.out.println("Item #" + item.index + " at offset " + item.offset);
            System.out.println("  Type: " + item.type);
            System.out.println("  Body Length: " + item.bodyLength);
            System.out.println("  Total Size: " + item.totalSize);
            System.out.println("  CRC32: " + item.crc32);

            if (item.journalEntry != null) {
                printJournalEntryText(item.journalEntry);
            }
            
            if (item.pageData != null) {
                printPageInfoText(item.pageData);
            }
        }

        System.out.println();
        System.out.println("Total items: " + info.totalItems);
    }

    private static void printJournalEntryText(JournalEntryInfo je) {
        System.out.println("  Journal Entry:");
        System.out.println("    Operation Type: " + je.operationType);
        System.out.println("    Timestamp: " + je.timestamp);
        System.out.println("    Sequence Number: " + je.sequenceNumber);
        System.out.println("    Entry Count: " + je.entryCount);
        
        if (je.entries != null) {
            for (int i = 0; i < je.entries.size(); i++) {
                EntryDetailInfo entry = je.entries.get(i);
                StringBuilder sb = new StringBuilder();
                sb.append("    Entry [").append(i).append("]: ");
                appendEntryDetailText(sb, entry);
                System.out.println(sb);
            }
        }
    }

    private static void printPageInfoText(PageInfo page) {
        System.out.println("  Page Data:");
        System.out.println("    Page ID: " + page.pageId);
        System.out.println("    Page Type: " + page.pageType);
        System.out.println("    Used Size: " + page.usedSize);
        System.out.println("    Entry Count: " + page.entryCount);
        
        if (page.entries != null && !page.entries.isEmpty()) {
            System.out.println("    Entries:");
            for (int i = 0; i < page.entries.size(); i++) {
                EntryDetailInfo entry = page.entries.get(i);
                StringBuilder sb = new StringBuilder();
                sb.append("      [").append(i).append("] ");
                appendEntryDetailText(sb, entry);
                System.out.println(sb);
            }
        }
    }

    private static void appendEntryDetailText(StringBuilder sb, EntryDetailInfo entry) {
        sb.append("keyType=").append(entry.keyType).append(" ");
        sb.append("keyData=\"").append(formatKeyDataFromBase64(entry.keyData)).append("\"");
        
        if (entry.hasValue) {
            sb.append(" valueType=").append(entry.valueType);
            sb.append(" valueData=0x[").append(bytesToHex(java.util.Base64.getDecoder().decode(entry.valueData), 16)).append("]");
        }
        
        if (entry.hasLocation) {
            sb.append(" -> chunk=").append(entry.locationChunkId);
            sb.append(" offset=").append(entry.locationOffset);
        }
    }

    private static String formatKeyDataFromBase64(String base64) {
        byte[] keyData = java.util.Base64.getDecoder().decode(base64);
        return formatKeyData(keyData);
    }

    private static void readAll(File dataDir, boolean detail) throws IOException {
        System.out.println("================================================================================");
        System.out.println("HyperKVStore Diagnostic Report");
        System.out.println("================================================================================");
        System.out.println("Data Directory: " + dataDir);
        System.out.println("Generated: " + DATE_FORMAT.format(new Date()));
        System.out.println();

        readTreeMetadata(dataDir);
        System.out.println();

        readChunkMetadata(dataDir);
        System.out.println();

        readJournalRegionMetadata(dataDir);
        System.out.println();

        File dataSubDir = new File(dataDir, "data");
        if (dataSubDir.exists() && dataSubDir.isDirectory()) {
            File[] chunkFiles = dataSubDir.listFiles((dir, name) -> name.startsWith("chunk_") && name.endsWith(".dat"));
            if (chunkFiles != null && chunkFiles.length > 0) {
                System.out.println("================================================================================");
                System.out.println("Chunk Files (" + chunkFiles.length + ")");
                System.out.println("================================================================================");
                for (File chunkFile : chunkFiles) {
                    System.out.println();
                    parseChunkFile(chunkFile, detail);
                }
            }
        }

        System.out.println();
        System.out.println("================================================================================");
        System.out.println("End of Report");
        System.out.println("================================================================================");
    }

    private static boolean traverseTree(File dataDir, int version, boolean showLeaf) throws IOException {
        File treeMetaFile = new File(dataDir, "tree-metadata.pb");
        File dataSubDir = new File(dataDir, "data");

        if (!treeMetaFile.exists()) {
            System.out.println("Tree metadata file not found: " + treeMetaFile);
            return false;
        }

        byte[] metaData = Files.readAllBytes(treeMetaFile.toPath());
        Metadata.TreeMetadataFile treeMeta = Metadata.TreeMetadataFile.parseFrom(metaData);

        if (treeMeta.getEntriesCount() == 0) {
            System.out.println("No tree versions found");
            return false;
        }

        Metadata.TreeMetadataEntry targetEntry = null;
        int actualVersion = version;
        if (version < 0) {
            targetEntry = treeMeta.getEntries(0);
            actualVersion = (int) targetEntry.getVersion();
        } else {
            for (int i = 0; i < treeMeta.getEntriesCount(); i++) {
                if (treeMeta.getEntries(i).getVersion() == version) {
                    targetEntry = treeMeta.getEntries(i);
                    break;
                }
            }
        }

        if (targetEntry == null) {
            System.out.println("Tree version " + version + " not found");
            return false;
        }

        if (!targetEntry.hasRootLocation()) {
            System.out.println("Tree version " + version + " has no root location");
            return false;
        }

        Keyvalue.SegmentLocationProto rootLoc = targetEntry.getRootLocation();
        UUID chunkId = new UUID(rootLoc.getChunkIdMostSig(), rootLoc.getChunkIdLeastSig());
        long offset = rootLoc.getOffset();
        int length = (int) rootLoc.getLength();

        System.out.println("=== Tree Traverse ===");
        System.out.println("Data Directory: " + dataDir);
        System.out.println("Tree Version: " + actualVersion);
        System.out.println("Show Leaf Content: " + showLeaf);
        System.out.println("Root Location: chunk=" + chunkId + ", offset=" + offset + ", length=" + length);
        System.out.println();

        File chunkFile = new File(dataSubDir, "chunk_" + chunkId.toString() + ".dat");
        if (!chunkFile.exists()) {
            chunkFile = new File(dataDir, "journal/chunk_" + chunkId.toString() + ".dat");
        }

        if (!chunkFile.exists()) {
            System.out.println("Chunk file not found: " + chunkFile);
            return false;
        }

        int level = targetEntry.getStats().getHeight();
        traversePage(dataDir, dataSubDir, chunkFile, offset, length, level, showLeaf);
        return true;
    }

    private static void traversePage(File dataDir, File dataSubDir, File chunkFile, long offset, int length, int level, boolean showLeaf) throws IOException {
        try (RandomAccessFile raf = new RandomAccessFile(chunkFile, "r")) {
            raf.seek(0);
            byte[] chunkHeaderBuf = new byte[ChunkHeader.HEADER_SIZE];
            raf.readFully(chunkHeaderBuf);
            ChunkHeader chunkHeader = ChunkHeader.fromByteArray(chunkHeaderBuf);

            System.out.println("Chunk Type: " + chunkHeader.getChunkType());
            System.out.println("Chunk ID: " + chunkHeader.getChunkId());

            raf.seek(offset);
            byte[] writeItemHeaderBuf = new byte[WriteItem.HEADER_SIZE];
            raf.readFully(writeItemHeaderBuf);

            ByteBuffer bb = ByteBuffer.wrap(writeItemHeaderBuf).order(ByteOrder.BIG_ENDIAN);
            short magic = bb.getShort();
            if (magic != WriteItem.MAGIC) {
                System.out.println("Invalid write item magic: " + magic);
                return;
            }

            short type = bb.getShort();
            int bodyLength = bb.getInt();

            System.out.println("Write Item Type: " + type + ", Body Length: " + bodyLength);

            byte[] body = new byte[bodyLength];
            raf.readFully(body);

            Page.PageProto pageProto = Page.PageProto.parseFrom(body);
            printPageContent(pageProto, level, showLeaf);

            if (pageProto.getPageType() == Common.PageType.PAGE_BRANCH || 
                pageProto.getPageType() == Common.PageType.PAGE_ROOT) {
                int entryCount = pageProto.getEntryOffsets().size() / 4;
                byte[] offsetsBytes = pageProto.getEntryOffsets().toByteArray();
                byte[] entriesBytes = pageProto.getEntries().toByteArray();

                ByteBuffer offsetsBuf = ByteBuffer.wrap(offsetsBytes).order(ByteOrder.LITTLE_ENDIAN);
                int[] offsets = new int[entryCount];
                for (int i = 0; i < entryCount; i++) {
                    offsets[i] = offsetsBuf.getInt();
                }

                for (int i = 0; i < entryCount; i++) {
                    int start = offsets[i];
                    int end = (i + 1 < entryCount) ? offsets[i + 1] : entriesBytes.length;
                    byte[] entryBytes = new byte[end - start];
                    System.arraycopy(entriesBytes, start, entryBytes, 0, entryBytes.length);

                    Keyvalue.KeyValuePairProto kvp = Keyvalue.KeyValuePairProto.parseFrom(entryBytes);
                    if (kvp.hasLocation()) {
                        Keyvalue.SegmentLocationProto loc = kvp.getLocation();
                        UUID childChunkId = new UUID(loc.getChunkIdMostSig(), loc.getChunkIdLeastSig());
                        long childOffset = loc.getOffset();
                        int childLength = (int) loc.getLength();

                        File childChunkFile = new File(dataSubDir, "chunk_" + childChunkId.toString() + ".dat");
                        if (!childChunkFile.exists()) {
                            childChunkFile = new File(dataDir, "journal/chunk_" + childChunkId.toString() + ".dat");
                        }

                        if (childChunkFile.exists()) {
                            System.out.println();
                            System.out.println("=== Child " + i + " ===");
                            traversePage(dataDir, dataSubDir, childChunkFile, childOffset, childLength, level - 1, showLeaf);
                        }
                    }
                }
            }
        }
    }

    private static void printPageContent(Page.PageProto pageProto, int level, boolean showLeaf) throws IOException {
        System.out.println("--- Level " + level + " ---");
        System.out.println("Page ID: " + pageProto.getPageId());
        System.out.println("Page Type: " + pageProto.getPageType());
        System.out.println("Used Size: " + pageProto.getUsedSize());

        int entryCount = pageProto.getEntryOffsets().size() / 4;
        System.out.println("Entry Count: " + entryCount);

        boolean isLeaf = pageProto.getPageType() == Common.PageType.PAGE_LEAF;
        if (!showLeaf && isLeaf) {
            System.out.println("(Leaf content hidden - use --leaf to show)");
            System.out.println();
            return;
        }

        if (entryCount > 0) {
            byte[] offsetsBytes = pageProto.getEntryOffsets().toByteArray();
            byte[] entriesBytes = pageProto.getEntries().toByteArray();

            ByteBuffer offsetsBuf = ByteBuffer.wrap(offsetsBytes).order(ByteOrder.LITTLE_ENDIAN);
            int[] offsets = new int[entryCount];
            for (int i = 0; i < entryCount; i++) {
                offsets[i] = offsetsBuf.getInt();
            }

            System.out.println("Entries:");
            for (int i = 0; i < entryCount; i++) {
                int start = offsets[i];
                int end = (i + 1 < entryCount) ? offsets[i + 1] : entriesBytes.length;
                byte[] entryBytes = new byte[end - start];
                System.arraycopy(entriesBytes, start, entryBytes, 0, entryBytes.length);

                Keyvalue.KeyValuePairProto kvp = Keyvalue.KeyValuePairProto.parseFrom(entryBytes);
                System.out.println("  [" + i + "] " + formatKeyValue(kvp));
            }
        }
        System.out.println();
    }

    private static String formatKeyValue(Keyvalue.KeyValuePairProto kvp) {
        StringBuilder sb = new StringBuilder();
        
        Keyvalue.KeyProto key = kvp.getKey();
        sb.append("key=");
        
        if (key.getKeyType() == Common.KeyType.ORDERED_BYTES) {
            sb.append("\"");
            byte[] keyData = key.getKeyData().toByteArray();
            for (int i = 0; i < Math.min(keyData.length, 32); i++) {
                if (keyData[i] >= 32 && keyData[i] < 127) {
                    sb.append((char) keyData[i]);
                } else {
                    sb.append(String.format("\\x%02X", keyData[i]));
                }
            }
            if (keyData.length > 32) sb.append("...");
            sb.append("\"");
        } else {
            sb.append(bytesToHex(key.getKeyData().toByteArray(), 32));
        }
        
        if (kvp.hasValue()) {
            sb.append(" value=");
            Keyvalue.ValueProto value = kvp.getValue();
            byte[] valueData = value.getValueData().toByteArray();
            sb.append("0x[").append(bytesToHex(valueData, 16)).append("]");
            if (valueData.length > 16) {
                sb.append("...");
            }
        } else if (kvp.hasLocation()) {
            Keyvalue.SegmentLocationProto loc = kvp.getLocation();
            sb.append(" -> chunk=").append(formatUUID(loc.getChunkIdMostSig(), loc.getChunkIdLeastSig()));
            sb.append(" offset=").append(loc.getOffset());
        }
        
        return sb.toString();
    }

    private static boolean followTreePath(File dataDir, int version, int[] path, boolean showAllPages) throws IOException {
        File treeMetaFile = new File(dataDir, "tree-metadata.pb");
        File dataSubDir = new File(dataDir, "data");

        if (!treeMetaFile.exists()) {
            if (jsonOutputStatic) {
                System.out.println("{\"error\":\"Tree metadata file not found: " + treeMetaFile + "\"}");
            } else {
                System.out.println("Tree metadata file not found: " + treeMetaFile);
            }
            return false;
        }

        byte[] metaData = Files.readAllBytes(treeMetaFile.toPath());
        Metadata.TreeMetadataFile treeMeta = Metadata.TreeMetadataFile.parseFrom(metaData);

        if (treeMeta.getEntriesCount() == 0) {
            if (jsonOutputStatic) {
                System.out.println("{\"error\":\"No tree versions found\"}");
            } else {
                System.out.println("No tree versions found");
            }
            return false;
        }

        Metadata.TreeMetadataEntry targetEntry = treeMeta.getEntries(0);
        if (version >= 0) {
            boolean found = false;
            for (int i = 0; i < treeMeta.getEntriesCount(); i++) {
                if (treeMeta.getEntries(i).getVersion() == version) {
                    targetEntry = treeMeta.getEntries(i);
                    found = true;
                    break;
                }
            }
            if (!found) {
                if (jsonOutputStatic) {
                    System.out.println("{\"error\":\"Tree version " + version + " not found\"}");
                } else {
                    System.out.println("Tree version " + version + " not found");
                }
                return false;
            }
        }

        if (!targetEntry.hasRootLocation()) {
            if (jsonOutputStatic) {
                System.out.println("{\"error\":\"Tree has no root location\"}");
            } else {
                System.out.println("Tree has no root location");
            }
            return false;
        }

        Keyvalue.SegmentLocationProto rootLoc = targetEntry.getRootLocation();
        UUID chunkId = new UUID(rootLoc.getChunkIdMostSig(), rootLoc.getChunkIdLeastSig());
        long offset = rootLoc.getOffset();
        int length = (int) rootLoc.getLength();
        int height = targetEntry.getStats().getHeight();

        List<TreePathPageInfo> pageInfos = new ArrayList<>();

        if (!jsonOutputStatic) {
            System.out.println("=== Tree Path Follow ===");
            System.out.println("Data Directory: " + dataDir);
            System.out.println("Tree Version: " + targetEntry.getVersion());
            System.out.println("Tree Height: " + height);
            if (path != null) {
                StringBuilder pathStr = new StringBuilder();
                for (int i = 0; i < path.length; i++) {
                    if (i > 0) pathStr.append("-");
                    pathStr.append(path[i]);
                }
                System.out.println("Path: " + pathStr);
            } else {
                System.out.println("Path: (root only)");
            }
            System.out.println();
        }

        File chunkFile = new File(dataSubDir, "chunk_" + chunkId.toString() + ".dat");
        if (!chunkFile.exists()) {
            chunkFile = new File(dataDir, "journal/chunk_" + chunkId.toString() + ".dat");
        }

        if (!chunkFile.exists()) {
            if (jsonOutputStatic) {
                System.out.println("{\"error\":\"Chunk file not found: " + chunkFile + "\"}");
            } else {
                System.out.println("Chunk file not found: " + chunkFile);
            }
            return false;
        }

        int currentLevel = height;
        long currentOffset = offset;
        int currentLength = length;
        UUID currentChunkId = chunkId;
        int pathIndex = 0;
        
        while (true) {
            try (RandomAccessFile raf = new RandomAccessFile(chunkFile, "r")) {
                raf.seek(0);
                byte[] chunkHeaderBuf = new byte[ChunkHeader.HEADER_SIZE];
                raf.readFully(chunkHeaderBuf);
                ChunkHeader chunkHeader = ChunkHeader.fromByteArray(chunkHeaderBuf);

                raf.seek(currentOffset);
                byte[] writeItemHeaderBuf = new byte[WriteItem.HEADER_SIZE];
                raf.readFully(writeItemHeaderBuf);

                ByteBuffer bb = ByteBuffer.wrap(writeItemHeaderBuf).order(ByteOrder.BIG_ENDIAN);
                short magic = bb.getShort();
                if (magic != WriteItem.MAGIC) {
                    if (jsonOutputStatic) {
                        System.out.println("{\"error\":\"Invalid write item magic: " + magic + "\"}");
                    } else {
                        System.out.println("Invalid write item magic: " + magic);
                    }
                    return false;
                }

                short type = bb.getShort();
                int bodyLength = bb.getInt();

                byte[] body = new byte[bodyLength];
                raf.readFully(body);

                Page.PageProto pageProto = Page.PageProto.parseFrom(body);
                
                int entryCount = pageProto.getEntryOffsets().size() / 4;
                byte[] offsetsBytes = pageProto.getEntryOffsets().toByteArray();
                byte[] entriesBytes = pageProto.getEntries().toByteArray();
                ByteBuffer offsetsBuf = ByteBuffer.wrap(offsetsBytes).order(ByteOrder.LITTLE_ENDIAN);
                int[] offsets = new int[entryCount];
                for (int i = 0; i < entryCount; i++) {
                    offsets[i] = offsetsBuf.getInt();
                }

                TreePathPageInfo pageInfo = new TreePathPageInfo();
                pageInfo.level = currentLevel;
                pageInfo.pageId = pageProto.getPageId();
                pageInfo.pageType = pageProto.getPageType().name();
                pageInfo.usedSize = pageProto.getUsedSize();
                pageInfo.entryCount = entryCount;
                pageInfo.chunkId = currentChunkId.toString();
                pageInfo.offset = currentOffset;
                pageInfo.isLastPage = (path == null || pathIndex >= path.length);

                List<TreePathEntryInfo> entryInfos = new ArrayList<>();
                for (int i = 0; i < entryCount; i++) {
                    int start = offsets[i];
                    int end = (i + 1 < entryCount) ? offsets[i + 1] : entriesBytes.length;
                    byte[] entryBytes = new byte[end - start];
                    System.arraycopy(entriesBytes, start, entryBytes, 0, entryBytes.length);

                    Keyvalue.KeyValuePairProto kvp = Keyvalue.KeyValuePairProto.parseFrom(entryBytes);
                    TreePathEntryInfo entryInfo = new TreePathEntryInfo();
                    entryInfo.index = i;
                    entryInfo.keyType = kvp.getKey().getKeyType().name();
                    entryInfo.keyData = kvp.getKey().getKeyData().toByteArray();
                    if (kvp.hasValue()) {
                        entryInfo.hasValue = true;
                        entryInfo.valueData = kvp.getValue().getValueData().toByteArray();
                    }
                    if (kvp.hasLocation()) {
                        entryInfo.hasLocation = true;
                        Keyvalue.SegmentLocationProto loc = kvp.getLocation();
                        entryInfo.locationChunkId = formatUUID(loc.getChunkIdMostSig(), loc.getChunkIdLeastSig());
                        entryInfo.locationOffset = loc.getOffset();
                        entryInfo.locationLength = loc.getLength();
                    }
                    entryInfos.add(entryInfo);
                }
                pageInfo.entries = entryInfos;
                pageInfos.add(pageInfo);

                if (path == null || pathIndex >= path.length) {
                    break;
                }

                int childIdx = path[pathIndex];
                currentLevel--;

                if (childIdx < 0 || childIdx >= entryCount) {
                    if (jsonOutputStatic) {
                        System.out.println("{\"error\":\"Child index " + childIdx + " out of bounds (0-" + (entryCount - 1) + ")\"}");
                    } else {
                        System.out.println("Child index " + childIdx + " out of bounds (0-" + (entryCount - 1) + ")");
                    }
                    return false;
                }

                TreePathEntryInfo targetEntryInfo = entryInfos.get(childIdx);
                if (!targetEntryInfo.hasLocation) {
                    if (jsonOutputStatic) {
                        System.out.println("{\"error\":\"Entry at index " + childIdx + " has no location (leaf node)\"}");
                    } else {
                        System.out.println("Entry at index " + childIdx + " has no location (leaf node)");
                    }
                    return false;
                }

                currentChunkId = UUID.fromString(targetEntryInfo.locationChunkId);
                currentOffset = targetEntryInfo.locationOffset;
                currentLength = (int) targetEntryInfo.locationLength;

                chunkFile = new File(dataSubDir, "chunk_" + currentChunkId.toString() + ".dat");
                if (!chunkFile.exists()) {
                    chunkFile = new File(dataDir, "journal/chunk_" + currentChunkId.toString() + ".dat");
                }

                if (!chunkFile.exists()) {
                    if (jsonOutputStatic) {
                        System.out.println("{\"error\":\"Chunk file not found: " + chunkFile + "\"}");
                    } else {
                        System.out.println("Chunk file not found: " + chunkFile);
                    }
                    return false;
                }

                pathIndex++;
            }
        }

        if (jsonOutputStatic) {
            printTreePathJson(pageInfos, showAllPages);
        } else {
            printTreePathText(pageInfos, showAllPages);
        }
        return true;
    }

    private static class TreePathPageInfo {
        int level;
        long pageId;
        String pageType;
        int usedSize;
        int entryCount;
        String chunkId;
        long offset;
        boolean isLastPage;
        List<TreePathEntryInfo> entries;
    }

    private static class TreePathEntryInfo {
        int index;
        String keyType;
        byte[] keyData;
        boolean hasValue;
        byte[] valueData;
        boolean hasLocation;
        String locationChunkId;
        long locationOffset;
        long locationLength;
    }

    private static void printTreePathJson(List<TreePathPageInfo> pageInfos, boolean showAllPages) {
        StringBuilder json = new StringBuilder();
        json.append("{");
        json.append("\"pages\":[");

        List<TreePathPageInfo> pagesToPrint = showAllPages ? pageInfos : 
            pageInfos.stream().filter(p -> p.isLastPage).toList();

        for (int i = 0; i < pagesToPrint.size(); i++) {
            TreePathPageInfo page = pagesToPrint.get(i);
            if (i > 0) json.append(",");
            json.append("{");
            json.append("\"level\":").append(page.level).append(",");
            json.append("\"pageId\":").append(page.pageId).append(",");
            json.append("\"pageType\":\"").append(page.pageType).append("\",");
            json.append("\"usedSize\":").append(page.usedSize).append(",");
            json.append("\"entryCount\":").append(page.entryCount).append(",");
            json.append("\"location\":{");
            json.append("\"chunkId\":\"").append(page.chunkId).append("\",");
            json.append("\"offset\":").append(page.offset);
            json.append("},");
            json.append("\"isLastPage\":").append(page.isLastPage).append(",");
            json.append("\"entries\":[");
            
            for (int j = 0; j < page.entries.size(); j++) {
                TreePathEntryInfo entry = page.entries.get(j);
                if (j > 0) json.append(",");
                json.append("{");
                json.append("\"index\":").append(entry.index).append(",");
                json.append("\"keyType\":\"").append(entry.keyType).append("\",");
                json.append("\"keyData\":\"").append(bytesToBase64(entry.keyData)).append("\"");
                
                if (entry.hasValue) {
                    json.append(",\"value\":{");
                    json.append("\"data\":\"").append(bytesToBase64(entry.valueData)).append("\"");
                    json.append("}");
                }
                
                if (entry.hasLocation) {
                    json.append(",\"location\":{");
                    json.append("\"chunkId\":\"").append(entry.locationChunkId).append("\",");
                    json.append("\"offset\":").append(entry.locationOffset).append(",");
                    json.append("\"length\":").append(entry.locationLength);
                    json.append("}");
                }
                json.append("}");
            }
            json.append("]");
            json.append("}");
        }
        
        json.append("]}");
        System.out.println(json);
    }

    private static void printTreePathText(List<TreePathPageInfo> pageInfos, boolean showAllPages) {
        List<TreePathPageInfo> pagesToPrint = showAllPages ? pageInfos : 
            pageInfos.stream().filter(p -> p.isLastPage).toList();

        for (TreePathPageInfo page : pagesToPrint) {
            System.out.println("=== Level " + page.level + " ===");
            System.out.println("Page ID: " + page.pageId);
            System.out.println("Page Type: " + page.pageType);
            System.out.println("Used Size: " + page.usedSize);
            System.out.println("Entry Count: " + page.entryCount);
            System.out.println("Location: chunk=" + page.chunkId + ", offset=" + page.offset);
            System.out.println("Is Last Page: " + page.isLastPage);

            if (page.entryCount > 0) {
                System.out.println("Entries:");
                for (TreePathEntryInfo entry : page.entries) {
                    StringBuilder sb = new StringBuilder();
                    sb.append("  [").append(entry.index).append("] ");
                    sb.append("keyType=").append(entry.keyType).append(" ");
                    sb.append("keyData=\"").append(formatKeyData(entry.keyData)).append("\"");
                    
                    if (entry.hasValue) {
                        sb.append(" value=0x[").append(bytesToHex(entry.valueData, 16)).append("]");
                        if (entry.valueData.length > 16) sb.append("...");
                    }
                    
                    if (entry.hasLocation) {
                        sb.append(" -> chunk=").append(entry.locationChunkId);
                        sb.append(" offset=").append(entry.locationOffset);
                    }
                    System.out.println(sb);
                }
            }
            System.out.println();
        }
    }

    private static String formatKeyData(byte[] keyData) {
        StringBuilder sb = new StringBuilder();
        for (int i = 0; i < Math.min(keyData.length, 32); i++) {
            if (keyData[i] >= 32 && keyData[i] < 127) {
                sb.append((char) keyData[i]);
            } else {
                sb.append(String.format("\\x%02X", keyData[i]));
            }
        }
        if (keyData.length > 32) sb.append("...");
        return sb.toString();
    }

    private static String bytesToBase64(byte[] bytes) {
        return java.util.Base64.getEncoder().encodeToString(bytes);
    }

    private static String formatUUID(long mostSig, long leastSig) {
        return new UUID(mostSig, leastSig).toString();
    }

    private static String formatTimestamp(long timestamp) {
        if (timestamp <= 0) {
            return "N/A";
        }
        return DATE_FORMAT.format(new Date(timestamp));
    }

    private static String formatSize(long size) {
        if (size < 1024) {
            return size + " B";
        } else if (size < 1024 * 1024) {
            return String.format("%.2f KB", size / 1024.0);
        } else if (size < 1024 * 1024 * 1024) {
            return String.format("%.2f MB", size / (1024.0 * 1024));
        } else {
            return String.format("%.2f GB", size / (1024.0 * 1024 * 1024));
        }
    }

    private static String formatWriteItemType(short type) {
        return switch (type) {
            case WriteItem.TYPE_JOURNAL_ENTRY -> "JOURNAL_ENTRY";
            case WriteItem.TYPE_PAGE_DATA -> "PAGE_DATA";
            case WriteItem.TYPE_METADATA -> "METADATA";
            case WriteItem.TYPE_INDEX_DATA -> "INDEX_DATA";
            default -> "UNKNOWN(" + type + ")";
        };
    }

    private static String bytesToHex(byte[] bytes, int maxLen) {
        StringBuilder sb = new StringBuilder();
        int len = Math.min(bytes.length, maxLen);
        for (int i = 0; i < len; i++) {
            sb.append(String.format("%02X ", bytes[i]));
        }
        if (bytes.length > maxLen) {
            sb.append("...");
        }
        return sb.toString().trim();
    }

    private static int alignUp(int value, int alignment) {
        return (value + alignment - 1) & ~(alignment - 1);
    }

    private static boolean readOccupancyMetadata(File dataDir, long targetVersion) throws IOException {
        File occupancyDir = new File(dataDir, "occupancy");
        
        if (!occupancyDir.exists() || !occupancyDir.isDirectory()) {
            if (jsonOutputStatic) {
                System.out.println("{\"error\":\"Occupancy directory not found: " + occupancyDir + "\"}");
            } else {
                System.out.println("Occupancy directory not found: " + occupancyDir);
            }
            return false;
        }

        File[] occupancyFiles = occupancyDir.listFiles((dir, name) -> name.endsWith(".pb"));
        
        if (occupancyFiles == null || occupancyFiles.length == 0) {
            if (jsonOutputStatic) {
                System.out.println("{\"error\":\"No occupancy files found in directory\"}");
            } else {
                System.out.println("No occupancy files found in directory: " + occupancyDir);
            }
            return false;
        }

        Arrays.sort(occupancyFiles, (a, b) -> {
            String nameA = a.getName().replace(".pb", "");
            String nameB = b.getName().replace(".pb", "");
            try {
                long verA = Long.parseLong(nameA);
                long verB = Long.parseLong(nameB);
                return Long.compare(verB, verA);
            } catch (NumberFormatException e) {
                return a.getName().compareTo(b.getName());
            }
        });

        if (jsonOutputStatic) {
            StringBuilder json = new StringBuilder();
            json.append("{");
            json.append("\"directory\":\"").append(occupancyDir).append("\",");
            json.append("\"fileCount\":").append(occupancyFiles.length).append(",");
            json.append("\"records\":[");
            
            boolean first = true;
            for (File file : occupancyFiles) {
                String fileName = file.getName().replace(".pb", "");
                long version;
                try {
                    version = Long.parseLong(fileName);
                } catch (NumberFormatException e) {
                    continue;
                }

                if (targetVersion >= 0 && version != targetVersion) {
                    continue;
                }

                if (!first) json.append(",");
                first = false;

                try {
                    byte[] data = Files.readAllBytes(file.toPath());
                    Metadata.OccupancyRecord record = Metadata.OccupancyRecord.parseFrom(data);
                    
                    json.append("{");
                    json.append("\"version\":").append(record.getVersion()).append(",");
                    json.append("\"mns\":").append(record.getMns()).append(",");
                    json.append("\"timestamp\":\"").append(formatTimestamp(record.getTimestamp())).append("\",");
                    json.append("\"deltaCount\":").append(record.getDeltasCount()).append(",");
                    json.append("\"deltas\":[");
                    
                    for (int i = 0; i < record.getDeltasCount(); i++) {
                        Metadata.OccupancyDelta delta = record.getDeltas(i);
                        if (i > 0) json.append(",");
                        json.append("{");
                        json.append("\"chunkId\":\"").append(formatUUID(delta.getChunkIdMostSig(), delta.getChunkIdLeastSig())).append("\",");
                        json.append("\"deltaSize\":").append(delta.getDeltaSize());
                        json.append("}");
                    }
                    
                    json.append("],");
                    json.append("\"decommissionPageCount\":").append(record.getDecommissionPagesCount()).append(",");
                    json.append("\"decommissionPages\":[");
                    
                    for (int i = 0; i < record.getDecommissionPagesCount(); i++) {
                        Metadata.DecommissionPage page = record.getDecommissionPages(i);
                        if (i > 0) json.append(",");
                        json.append("{");
                        json.append("\"chunkId\":\"").append(formatUUID(page.getChunkIdMostSig(), page.getChunkIdLeastSig())).append("\",");
                        json.append("\"offset\":").append(page.getOffset()).append(",");
                        json.append("\"length\":").append(page.getLength());
                        json.append("}");
                    }
                    
                    json.append("]");
                    json.append("}");
                } catch (Exception e) {
                    json.append("{\"error\":\"Failed to parse file: ").append(file.getName()).append("\"}");
                }
            }
            
            json.append("]}");
            System.out.println(json);
        } else {
            System.out.println("=== Occupancy Metadata ===");
            System.out.println("Directory: " + occupancyDir);
            System.out.println("File Count: " + occupancyFiles.length);
            System.out.println();

            for (File file : occupancyFiles) {
                String fileName = file.getName().replace(".pb", "");
                long version;
                try {
                    version = Long.parseLong(fileName);
                } catch (NumberFormatException e) {
                    System.out.println("Skipping file with invalid name: " + file.getName());
                    continue;
                }

                if (targetVersion >= 0 && version != targetVersion) {
                    continue;
                }

                try {
                    byte[] data = Files.readAllBytes(file.toPath());
                    Metadata.OccupancyRecord record = Metadata.OccupancyRecord.parseFrom(data);
                    
                    System.out.println("--- Tree Version #" + record.getVersion() + " ---");
                    System.out.println("MNS: " + record.getMns());
                    System.out.println("Timestamp: " + formatTimestamp(record.getTimestamp()));
                    System.out.println("Delta Count: " + record.getDeltasCount());
                    
                    if (record.getDeltasCount() > 0) {
                        System.out.println("Occupancy Deltas:");
                        for (int i = 0; i < record.getDeltasCount(); i++) {
                            Metadata.OccupancyDelta delta = record.getDeltas(i);
                            System.out.println("  Delta #" + (i + 1) + ":");
                            System.out.println("    Chunk ID: " + formatUUID(delta.getChunkIdMostSig(), delta.getChunkIdLeastSig()));
                            System.out.println("    Delta Size: " + formatSize(Math.abs(delta.getDeltaSize())) + 
                                (delta.getDeltaSize() >= 0 ? " (write)" : " (decommission)"));
                        }
                    }
                    
                    if (record.getDecommissionPagesCount() > 0) {
                        System.out.println("Decommission Pages: " + record.getDecommissionPagesCount());
                        for (int i = 0; i < record.getDecommissionPagesCount(); i++) {
                            Metadata.DecommissionPage page = record.getDecommissionPages(i);
                            System.out.println("  Page #" + (i + 1) + ":");
                            System.out.println("    Chunk ID: " + formatUUID(page.getChunkIdMostSig(), page.getChunkIdLeastSig()));
                            System.out.println("    Offset: " + page.getOffset());
                            System.out.println("    Length: " + formatSize(page.getLength()));
                        }
                    }
                    System.out.println();
                } catch (Exception e) {
                    System.out.println("Error reading file: " + file.getName());
                    e.printStackTrace();
                }
            }
        }
        
        return true;
    }
}
