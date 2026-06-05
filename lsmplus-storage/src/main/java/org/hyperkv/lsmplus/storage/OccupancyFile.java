package org.hyperkv.lsmplus.storage;

import org.hyperkv.lsmplus.proto.Metadata.DecommissionPage;
import org.hyperkv.lsmplus.proto.Metadata.OccupancyDelta;
import org.hyperkv.lsmplus.proto.Metadata.OccupancyRecord;
import org.hyperkv.lsmplus.storage.io.AbstractIO;
import org.hyperkv.lsmplus.storage.io.IOFactory;
import org.hyperkv.lsmplus.storage.io.VirtualDataPath;

import java.io.IOException;
import java.util.ArrayList;
import java.util.Collections;
import java.util.List;
import java.util.UUID;

public class OccupancyFile {

    private static final int MAGIC = 0x4F435059;
    private static final int FORMAT_VERSION = 1;

    private final VirtualDataPath path;
    private final long version;
    private final IOFactory ioFactory;
    private long mns;
    private long timestamp;
    private final List<OccupancyDeltaEntry> deltas;
    private final List<DecommissionPageEntry> decommissionPages;

    public OccupancyFile(VirtualDataPath path, long version, IOFactory ioFactory) {
        if (path == null) {
            throw new IllegalArgumentException("path must not be null");
        }
        if (version < 0) {
            throw new IllegalArgumentException("version must be non-negative");
        }
        if (ioFactory == null) {
            throw new IllegalArgumentException("ioFactory must not be null");
        }
        this.path = path;
        this.version = version;
        this.ioFactory = ioFactory;
        this.mns = 0;
        this.timestamp = System.currentTimeMillis();
        this.deltas = new ArrayList<>();
        this.decommissionPages = new ArrayList<>();
    }

    private OccupancyFile(VirtualDataPath path, long version, IOFactory ioFactory, long mns, long timestamp,
                          List<OccupancyDeltaEntry> deltas,
                          List<DecommissionPageEntry> decommissionPages) {
        this.path = path;
        this.version = version;
        this.ioFactory = ioFactory;
        this.mns = mns;
        this.timestamp = timestamp;
        this.deltas = new ArrayList<>(deltas);
        this.decommissionPages = new ArrayList<>(decommissionPages);
    }

    public void setMns(long mns) {
        this.mns = mns;
    }

    public void setTimestamp(long timestamp) {
        this.timestamp = timestamp;
    }

    public void addDelta(UUID chunkId, long deltaSize) {
        deltas.add(new OccupancyDeltaEntry(chunkId, deltaSize));
    }

    public void addDecommissionPage(UUID chunkId, int offset, int length) {
        decommissionPages.add(new DecommissionPageEntry(chunkId, offset, length));
    }

    public void clearDeltas() {
        deltas.clear();
    }

    public void clearDecommissionPages() {
        decommissionPages.clear();
    }

    public long getVersion() {
        return version;
    }

    public long getMns() {
        return mns;
    }

    public long getTimestamp() {
        return timestamp;
    }

    public List<OccupancyDeltaEntry> getDeltas() {
        return Collections.unmodifiableList(deltas);
    }

    public List<DecommissionPageEntry> getDecommissionPages() {
        return Collections.unmodifiableList(decommissionPages);
    }

    public void persist() throws IOException {
        OccupancyRecord.Builder builder = OccupancyRecord.newBuilder()
                .setVersion(version)
                .setMns(mns)
                .setTimestamp(timestamp);

        for (OccupancyDeltaEntry entry : deltas) {
            builder.addDeltas(entry.toProto());
        }

        for (DecommissionPageEntry entry : decommissionPages) {
            builder.addDecommissionPages(entry.toProto());
        }

        OccupancyRecord proto = builder.build();

        String pathStr = path.getPath();
        VirtualDataPath tmpPath = VirtualDataPath.file(pathStr + ".tmp");
        
        AbstractIO io = ioFactory.createIO();
        io.open(tmpPath, AbstractIO.OpenMode.WRITE);
        io.write(0, proto.toByteArray());
        io.sync();
        io.close();

        ioFactory.delete(path);
        ioFactory.createDirectories(path);
        
        java.io.File tmpFile = new java.io.File(tmpPath.getPath());
        java.io.File targetFile = new java.io.File(pathStr);
        if (!tmpFile.renameTo(targetFile)) {
            throw new IOException("Failed to rename " + tmpPath + " to " + path);
        }
    }

    public static OccupancyFile load(VirtualDataPath path, IOFactory ioFactory) throws IOException {
        if (!ioFactory.exists(path)) {
            long version = extractVersionFromPath(path);
            return new OccupancyFile(path, version, ioFactory);
        }

        AbstractIO io = ioFactory.createIO();
        io.open(path, AbstractIO.OpenMode.READ);
        byte[] data = io.read(0, (int) io.length());
        io.close();

        OccupancyRecord proto = OccupancyRecord.parseFrom(data);

        List<OccupancyDeltaEntry> deltas = new ArrayList<>();
        for (OccupancyDelta delta : proto.getDeltasList()) {
            deltas.add(OccupancyDeltaEntry.fromProto(delta));
        }

        List<DecommissionPageEntry> decommissionPages = new ArrayList<>();
        for (DecommissionPage page : proto.getDecommissionPagesList()) {
            decommissionPages.add(DecommissionPageEntry.fromProto(page));
        }

        return new OccupancyFile(path, proto.getVersion(), ioFactory, proto.getMns(),
                proto.getTimestamp(), deltas, decommissionPages);
    }

    private static long extractVersionFromPath(VirtualDataPath path) {
        String pathStr = path.getPath();
        int lastSep = pathStr.lastIndexOf('/');
        String fileName = lastSep >= 0 ? pathStr.substring(lastSep + 1) : pathStr;
        
        String name = fileName;
        int dotIdx = name.lastIndexOf('.');
        if (dotIdx > 0) {
            name = name.substring(0, dotIdx);
        }
        try {
            return Long.parseLong(name);
        } catch (NumberFormatException e) {
            return 0;
        }
    }

    public static final class OccupancyDeltaEntry {
        private final UUID chunkId;
        private final long deltaSize;

        public OccupancyDeltaEntry(UUID chunkId, long deltaSize) {
            this.chunkId = chunkId;
            this.deltaSize = deltaSize;
        }

        public UUID getChunkId() {
            return chunkId;
        }

        public long getDeltaSize() {
            return deltaSize;
        }

        public OccupancyDelta toProto() {
            return OccupancyDelta.newBuilder()
                    .setChunkIdMostSig(chunkId.getMostSignificantBits())
                    .setChunkIdLeastSig(chunkId.getLeastSignificantBits())
                    .setDeltaSize(deltaSize)
                    .build();
        }

        public static OccupancyDeltaEntry fromProto(OccupancyDelta proto) {
            return new OccupancyDeltaEntry(
                    new UUID(proto.getChunkIdMostSig(), proto.getChunkIdLeastSig()),
                    proto.getDeltaSize()
            );
        }
    }

    public static final class DecommissionPageEntry {
        private final UUID chunkId;
        private final int offset;
        private final int length;

        public DecommissionPageEntry(UUID chunkId, int offset, int length) {
            this.chunkId = chunkId;
            this.offset = offset;
            this.length = length;
        }

        public UUID getChunkId() {
            return chunkId;
        }

        public int getOffset() {
            return offset;
        }

        public int getLength() {
            return length;
        }

        public DecommissionPage toProto() {
            return DecommissionPage.newBuilder()
                    .setChunkIdMostSig(chunkId.getMostSignificantBits())
                    .setChunkIdLeastSig(chunkId.getLeastSignificantBits())
                    .setOffset(offset)
                    .setLength(length)
                    .build();
        }

        public static DecommissionPageEntry fromProto(DecommissionPage proto) {
            return new DecommissionPageEntry(
                    new UUID(proto.getChunkIdMostSig(), proto.getChunkIdLeastSig()),
                    proto.getOffset(),
                    proto.getLength()
            );
        }
    }
}
