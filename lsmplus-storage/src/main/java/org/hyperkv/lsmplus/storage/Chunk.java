package org.hyperkv.lsmplus.storage;

import org.hyperkv.lsmplus.monitoring.ExtendedPerformanceCounter;
import org.hyperkv.lsmplus.monitoring.MetricsRegistry;
import org.hyperkv.lsmplus.monitoring.PerformanceCounter;
import org.hyperkv.lsmplus.proto.Common.ChunkStatus;
import org.hyperkv.lsmplus.proto.Common.ChunkType;
import org.hyperkv.lsmplus.storage.io.AbstractIO;
import org.hyperkv.lsmplus.storage.io.FileIOFactory;
import org.hyperkv.lsmplus.storage.io.IOFactory;
import org.hyperkv.lsmplus.storage.io.VirtualDataPath;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

import java.io.IOException;
import java.nio.ByteBuffer;
import java.nio.ByteOrder;
import java.util.ArrayList;
import java.util.List;
import java.util.UUID;

public class Chunk implements AutoCloseable {

    private static final Logger log = LoggerFactory.getLogger(Chunk.class);
    public static final long DEFAULT_KEEP_ALIVE_MILLIS = 30 * 60 * 1000L;

    private final VirtualDataPath path;
    private final ChunkHeader header;
    private ChunkStatus status;
    private AbstractIO io;
    private final IOFactory ioFactory;
    private long createdAt;
    private long keepAliveTime;

    static ExtendedPerformanceCounter writeJournalCounter;
    static ExtendedPerformanceCounter writePageCounter;

    public Chunk(VirtualDataPath path, ChunkType type, UUID ownerId, UUID namespaceId) throws IOException {
        this(path, type, ownerId, namespaceId, FileIOFactory.INSTANCE.createIO(), FileIOFactory.INSTANCE);
    }

    public Chunk(VirtualDataPath path, ChunkType type, UUID ownerId, UUID namespaceId, AbstractIO io) throws IOException {
        this(path, type, ownerId, namespaceId, io, FileIOFactory.INSTANCE);
    }

    public Chunk(VirtualDataPath path, ChunkType type, UUID ownerId, UUID namespaceId, AbstractIO io, IOFactory ioFactory) throws IOException {
        if (path == null) {
            throw new IllegalArgumentException("path must not be null");
        }
        if (type == null) {
            throw new IllegalArgumentException("type must not be null");
        }
        if (ownerId == null) {
            throw new IllegalArgumentException("ownerId must not be null");
        }
        if (namespaceId == null) {
            throw new IllegalArgumentException("namespaceId must not be null");
        }
        if (io == null) {
            throw new IllegalArgumentException("io must not be null");
        }
        if (ioFactory == null) {
            throw new IllegalArgumentException("ioFactory must not be null");
        }

        this.path = path;
        UUID chunkId = uuidFromPath(path);
        this.header = new ChunkHeader(chunkId, type, ownerId, namespaceId);
        this.status = ChunkStatus.OPEN;
        this.createdAt = System.currentTimeMillis();
        this.keepAliveTime = this.createdAt + DEFAULT_KEEP_ALIVE_MILLIS;
        this.io = io;
        this.ioFactory = ioFactory;

        io.open(path, AbstractIO.OpenMode.WRITE);
        io.write(0, header.toByteArray());
        io.sync();
        writeJournalCounter = MetricsRegistry.getExtendedCounter("chunkWriteJournal", "", "batchSize", "dataSize");
        writePageCounter = MetricsRegistry.getExtendedCounter("chunkWritePage", "", "batchSize", "dataSize");
        log.debug("Created new chunk: chunkId={}, type={}, path={}", chunkId, type, path);
    }

    private Chunk(VirtualDataPath path, ChunkHeader header, ChunkStatus status, AbstractIO io, IOFactory ioFactory) {
        this.path = path;
        this.header = header;
        this.status = status;
        this.createdAt = System.currentTimeMillis();
        this.keepAliveTime = this.createdAt + DEFAULT_KEEP_ALIVE_MILLIS;
        this.io = io;
        this.ioFactory = ioFactory;
    }

    public static Chunk openForRead(ChunkInfo info, AbstractIO io) throws IOException {
        return openForRead(info, io, FileIOFactory.INSTANCE);
    }

    public static Chunk openForRead(ChunkInfo info, AbstractIO io, IOFactory ioFactory) throws IOException {
        if (info == null) {
            throw new IllegalArgumentException("info must not be null");
        }
        if (io == null) {
            throw new IllegalArgumentException("io must not be null");
        }
        if (ioFactory == null) {
            throw new IllegalArgumentException("ioFactory must not be null");
        }

        VirtualDataPath path = info.getPath();
        io.open(path, AbstractIO.OpenMode.READ);

        byte[] headerBytes = io.read(0, ChunkHeader.HEADER_SIZE);
        ChunkHeader header = ChunkHeader.fromByteArray(headerBytes);

        Chunk chunk = new Chunk(path, header, info.getStatus(), io, ioFactory);
        chunk.createdAt = info.getCreatedAt();
        chunk.keepAliveTime = info.getKeepAliveTime();
        return chunk;
    }

    public static Chunk openForRecovery(ChunkInfo info, AbstractIO io, IOFactory ioFactory) throws IOException {
        if (info == null) {
            throw new IllegalArgumentException("info must not be null");
        }
        if (io == null) {
            throw new IllegalArgumentException("io must not be null");
        }
        if (ioFactory == null) {
            throw new IllegalArgumentException("ioFactory must not be null");
        }

        VirtualDataPath path = info.getPath();
        io.open(path, AbstractIO.OpenMode.READ_WRITE);

        byte[] headerBytes = io.read(0, ChunkHeader.HEADER_SIZE);
        ChunkHeader header = ChunkHeader.fromByteArray(headerBytes);

        Chunk chunk = new Chunk(path, header, info.getStatus(), io, ioFactory);
        chunk.createdAt = info.getCreatedAt();
        chunk.keepAliveTime = info.getKeepAliveTime();
        return chunk;
    }

    public static Chunk openExisting(VirtualDataPath path) throws IOException {
        return openExisting(path, FileIOFactory.INSTANCE.createIO(), FileIOFactory.INSTANCE);
    }

    public static Chunk openExisting(VirtualDataPath path, AbstractIO io, IOFactory ioFactory) throws IOException {
        if (path == null) {
            throw new IllegalArgumentException("path must not be null");
        }
        if (io == null) {
            throw new IllegalArgumentException("io must not be null");
        }
        if (ioFactory == null) {
            throw new IllegalArgumentException("ioFactory must not be null");
        }

        io.open(path, AbstractIO.OpenMode.READ);

        byte[] headerBytes = io.read(0, ChunkHeader.HEADER_SIZE);
        ChunkHeader header = ChunkHeader.fromByteArray(headerBytes);

        Chunk chunk = new Chunk(path, header, ChunkStatus.SEALED, io, ioFactory);
        return chunk;
    }

    public synchronized SegmentLocation write(byte[] data, short writeItemType) throws IOException {
        if (status != ChunkStatus.OPEN) {
            throw new IllegalStateException("Cannot write to chunk in status: " + status);
        }
        if (data == null) {
            throw new IllegalArgumentException("data must not be null");
        }
        ensureIOOpen();

        WriteItem item = new WriteItem(writeItemType, data);
        byte[] itemBytes = item.toByteArray();

        long offset = io.length();
        io.write(offset, itemBytes);
        io.sync();

        log.trace("Wrote {} bytes to chunk {} at offset {}", itemBytes.length, header.getChunkId(), offset);
        return new SegmentLocation(header.getChunkId(), (int) offset, itemBytes.length);
    }

    public synchronized List<SegmentLocation> writeBatch(List<byte[]> dataList, short writeItemType) throws IOException {
        if (status != ChunkStatus.OPEN) {
            throw new IllegalStateException("Cannot write to chunk in status: " + status);
        }
        if (dataList == null || dataList.isEmpty()) {
            throw new IllegalArgumentException("dataList must not be null or empty");
        }
        ensureIOOpen();

        long start = PerformanceCounter.startTime();

        List<WriteItem> items = new ArrayList<>(dataList.size());
        List<Integer> offsets = new ArrayList<>(dataList.size());
        int totalBytes = 0;
        
        for (byte[] data : dataList) {
            WriteItem item = new WriteItem(writeItemType, data);
            items.add(item);
            totalBytes += item.getTotalSize();
        }

        ByteBuffer batchBuffer = ByteBuffer.allocate(totalBytes)
                .order(ByteOrder.BIG_ENDIAN);
        
        long baseOffset = io.length();
        int currentOffset = 0;
        
        for (WriteItem item : items) {
            offsets.add(currentOffset);
            batchBuffer.put(item.toByteArray());
            currentOffset += item.getTotalSize();
        }

        io.write(baseOffset, batchBuffer.array());
        io.sync();

        switch (writeItemType) {
            case WriteItem.TYPE_PAGE_DATA:
                writePageCounter.recordSuccess(PerformanceCounter.calcLatency(start), items.size(), totalBytes);
                break;
            case WriteItem.TYPE_JOURNAL_ENTRY:
                writeJournalCounter.recordSuccess(PerformanceCounter.calcLatency(start), items.size(), totalBytes);
                break;
        }

        List<SegmentLocation> locations = new ArrayList<>(items.size());
        for (int i = 0; i < items.size(); i++) {
            int itemOffset = (int) baseOffset + offsets.get(i);
            int itemSize = items.get(i).getTotalSize();
            locations.add(new SegmentLocation(header.getChunkId(), itemOffset, itemSize));
        }

        log.trace("Wrote batch of {} items ({} bytes total) to chunk {} at offset {}", 
            items.size(), totalBytes, header.getChunkId(), baseOffset);
        
        return locations;
    }

    public synchronized byte[] read(SegmentLocation location) throws IOException {
        if (location == null) {
            throw new IllegalArgumentException("location must not be null");
        }
        if (!location.getChunkId().equals(header.getChunkId())) {
            throw new IllegalArgumentException(
                    "Chunk ID mismatch: expected " + header.getChunkId() +
                    ", got " + location.getChunkId());
        }
        ensureIOOpen();

        byte[] itemBytes = io.read(location.getOffset(), location.getLength());
        WriteItem item = WriteItem.fromByteArray(itemBytes, 0);
        return item.getBody();
    }

    public synchronized byte[] readRaw(int offset, int length) throws IOException {
        ensureIOOpen();
        long fileLength = io.length();
        if (offset < 0 || length < 0 || offset + length > fileLength) {
            throw new IllegalArgumentException(
                    "Invalid offset/length: offset=" + offset +
                    ", length=" + length + ", fileSize=" + fileLength);
        }
        return io.read(offset, length);
    }

    public synchronized long getFileSize() throws IOException {
        ensureIOOpen();
        return io.length();
    }

    public synchronized void seal() throws IOException {
        validateTransition(ChunkStatus.SEALED);
        
        ensureIOOpen();
        long fileLength = io.length();
        header.setValidDataSize((int) (fileLength - ChunkHeader.HEADER_SIZE));
        writeHeader();
        
        status = ChunkStatus.SEALED;
        log.info("Sealed chunk: chunkId={}, validDataSize={}", header.getChunkId(), header.getValidDataSize());
    }

    public synchronized void recover() throws IOException {
        if (status != ChunkStatus.OPEN) {
            throw new IllegalStateException("Can only recover OPEN chunks, current status: " + status);
        }
        
        ensureIOOpen();
        long fileLength = io.length();
        
        if (fileLength <= ChunkHeader.HEADER_SIZE) {
            log.debug("Chunk {} is empty, nothing to recover", header.getChunkId());
            return;
        }
        
        header.setValidDataSize((int) (fileLength - ChunkHeader.HEADER_SIZE));
        log.info("Recovered chunk {}: validDataSize={}", header.getChunkId(), header.getValidDataSize());
    }
    
    public synchronized void recoverWithValidation() throws IOException {
        if (status != ChunkStatus.OPEN) {
            throw new IllegalStateException("Can only recover OPEN chunks, current status: " + status);
        }
        
        ensureIOOpen();
        long fileLength = io.length();
        
        if (fileLength <= ChunkHeader.HEADER_SIZE) {
            log.debug("Chunk {} is empty, nothing to recover", header.getChunkId());
            return;
        }
        
        long lastValidOffset = ChunkHeader.HEADER_SIZE;
        long currentOffset = ChunkHeader.HEADER_SIZE;
        
        while (currentOffset < fileLength) {
            try {
                int remainingBytes = (int) (fileLength - currentOffset);
                if (remainingBytes < WriteItem.HEADER_SIZE) {
                    log.debug("Insufficient bytes for header at offset {}, stopping scan", currentOffset);
                    break;
                }
                
                byte[] headerBytes = io.read(currentOffset, WriteItem.HEADER_SIZE);
                java.nio.ByteBuffer buffer = java.nio.ByteBuffer.wrap(headerBytes)
                    .order(java.nio.ByteOrder.BIG_ENDIAN);
                
                short magic = buffer.getShort();
                if (magic != WriteItem.MAGIC) {
                    log.debug("Invalid magic at offset {}, stopping scan", currentOffset);
                    break;
                }
                
                short type = buffer.getShort();
                int bodyLength = buffer.getInt();
                buffer.getInt();
                
                int totalItemSize = WriteItem.alignUp(
                    WriteItem.HEADER_SIZE + bodyLength + WriteItem.CRC32_SIZE, 
                    WriteItem.ALIGNMENT
                );
                
                if (currentOffset + totalItemSize > fileLength) {
                    log.debug("Incomplete item at offset {}, stopping scan", currentOffset);
                    break;
                }
                
                byte[] itemBytes = io.read(currentOffset, totalItemSize);
                WriteItem item = WriteItem.fromByteArray(itemBytes, 0);
                
                if (!item.validate()) {
                    log.debug("CRC validation failed at offset {}, stopping scan", currentOffset);
                    break;
                }
                
                lastValidOffset = currentOffset + totalItemSize;
                currentOffset += totalItemSize;
                
            } catch (Exception e) {
                log.debug("Error scanning item at offset {}: {}, stopping scan", currentOffset, e.getMessage());
                break;
            }
        }
        
        header.setValidDataSize((int) (lastValidOffset - ChunkHeader.HEADER_SIZE));
        writeHeader();
        
        log.info("Recovered chunk {}: validDataSize={}, lastValidOffset={}", 
            header.getChunkId(), header.getValidDataSize(), lastValidOffset);
    }

    public synchronized void extend(long durationMillis) {
        if (status != ChunkStatus.OPEN) {
            throw new IllegalStateException("Cannot extend chunk in status: " + status);
        }
        if (durationMillis <= 0) {
            throw new IllegalArgumentException("durationMillis must be positive: " + durationMillis);
        }
        this.keepAliveTime = System.currentTimeMillis() + durationMillis;
        log.debug("Extended chunk keep-alive: chunkId={}, newKeepAliveTime={}", 
            header.getChunkId(), this.keepAliveTime);
    }

    public synchronized boolean isKeepAliveExpired() {
        return System.currentTimeMillis() > keepAliveTime;
    }

    public long getCreatedAt() {
        return createdAt;
    }

    public long getKeepAliveTime() {
        return keepAliveTime;
    }

    public synchronized void markForDeletion() {
        validateTransition(ChunkStatus.DELETING);
        status = ChunkStatus.DELETING;
        log.debug("Marked chunk for deletion: chunkId={}", header.getChunkId());
    }

    public synchronized void delete() {
        markForDeletion();
    }

    public synchronized void cleanup() throws IOException {
        validateTransition(ChunkStatus.DELETED);
        if (io != null) {
            io.close();
            io = null;
        }
        ioFactory.delete(path);
        status = ChunkStatus.DELETED;
        log.info("Deleted chunk: chunkId={}, path={}", header.getChunkId(), path);
    }

    private void validateTransition(ChunkStatus targetStatus) {
        if (!isValidTransition(status, targetStatus)) {
            throw new IllegalStateException(
                "Invalid status transition: " + status + " -> " + targetStatus + 
                " for chunk " + header.getChunkId());
        }
    }

    private static boolean isValidTransition(ChunkStatus from, ChunkStatus to) {
        if (from == to) {
            return false;
        }
        
        return switch (from) {
            case OPEN -> to == ChunkStatus.SEALED;
            case SEALED -> to == ChunkStatus.DELETING;
            case DELETING -> to == ChunkStatus.DELETED;
            case DELETED -> false;
            case UNRECOGNIZED -> false;
        };
    }

    public ChunkStatus getStatus() {
        return status;
    }

    public ChunkHeader getHeader() {
        return header;
    }

    public UUID getChunkId() {
        return header.getChunkId();
    }

    public ChunkType getChunkType() {
        return header.getChunkType();
    }

    public int getValidDataSize() {
        return header.getValidDataSize();
    }

    public long getRemainingSpace() throws IOException {
        ensureIOOpen();
        long fileSize = io.length();
        return getMaxChunkSize() - fileSize;
    }

    public long getMaxChunkSize() {
        return 64 * 1024 * 1024;
    }

    public VirtualDataPath getPath() {
        return path;
    }

    public java.io.File getFile() {
        return new java.io.File(path.getPath());
    }

    private void writeHeader() throws IOException {
        ensureIOOpen();
        io.write(0, header.toByteArray());
        io.sync();
    }

    private void ensureIOOpen() throws IOException {
        if (io == null || !io.isOpen()) {
            throw new IOException("Chunk IO is not open: chunkId=" + header.getChunkId());
        }
    }

    private static UUID uuidFromPath(VirtualDataPath path) {
        String pathStr = path.getPath();
        int lastSep = pathStr.lastIndexOf('/');
        String fileName = lastSep >= 0 ? pathStr.substring(lastSep + 1) : pathStr;
        
        String name = fileName;
        int dotIdx = name.lastIndexOf('.');
        if (dotIdx > 0) {
            name = name.substring(0, dotIdx);
        }
        if (name.startsWith("chunk_")) {
            name = name.substring(6);
        }
        return UUID.fromString(name);
    }

    @Override
    public synchronized void close() throws IOException {
        if (io != null) {
            io.close();
            io = null;
        }
    }

    @Override
    public String toString() {
        return "Chunk{chunkId=" + header.getChunkId() +
               ", type=" + header.getChunkType() +
               ", status=" + status +
               ", validDataSize=" + header.getValidDataSize() + '}';
    }
}
