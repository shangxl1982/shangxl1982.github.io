package org.hyperkv.lsmplus.storage.io;

import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

import java.io.File;
import java.io.IOException;
import java.io.RandomAccessFile;
import java.util.Objects;

public class FileIO implements AbstractIO {

    private static final Logger log = LoggerFactory.getLogger(FileIO.class);

    private VirtualDataPath path;
    private OpenMode mode;
    private RandomAccessFile raf;
    private boolean open;

    public FileIO() {
    }

    public FileIO(VirtualDataPath path, OpenMode mode) throws IOException {
        open(path, mode);
    }

    @Override
    public void open(VirtualDataPath path, OpenMode mode) throws IOException {
        if (path == null) {
            throw new IllegalArgumentException("path must not be null");
        }
        if (mode == null) {
            throw new IllegalArgumentException("mode must not be null");
        }
        if (!path.isFile()) {
            throw new IllegalArgumentException("FileIO only supports file:// scheme, got: " + path.getScheme());
        }

        if (open) {
            close();
        }

        this.path = path;
        this.mode = mode;

        File file = new File(path.getPath());
        String rafMode = switch (mode) {
            case READ -> "r";
            case WRITE -> "rw";
            case READ_WRITE -> "rw";
        };

        File parent = file.getParentFile();
        if (parent != null && !parent.exists()) {
            parent.mkdirs();
        }

        this.raf = new RandomAccessFile(file, rafMode);
        this.open = true;

        log.trace("Opened FileIO: path={}, mode={}", path, mode);
    }

    @Override
    public byte[] read(long offset, int length) throws IOException {
        ensureOpen();
        if (offset < 0) {
            throw new IllegalArgumentException("offset must be non-negative: " + offset);
        }
        if (length < 0) {
            throw new IllegalArgumentException("length must be non-negative: " + length);
        }

        byte[] data = new byte[length];
        raf.seek(offset);
        raf.readFully(data);
        try {
            Thread.sleep(1);
        } catch (InterruptedException e) {
            throw new RuntimeException(e);
        }
        return data;
    }

    @Override
    public void write(long offset, byte[] data) throws IOException {
        ensureOpen();
        ensureWritable();
        if (data == null) {
            throw new IllegalArgumentException("data must not be null");
        }
        if (offset < 0) {
            throw new IllegalArgumentException("offset must be non-negative: " + offset);
        }

        raf.seek(offset);
        raf.write(data);
    }

    @Override
    public void sync(long offset, long length) throws IOException {
        ensureOpen();
        ensureWritable();
        raf.getFD().sync();
        try {
            Thread.sleep(1);
        } catch (InterruptedException e) {
            throw new RuntimeException(e);
        }
    }

    @Override
    public void sync() throws IOException {
        ensureOpen();
        ensureWritable();
        raf.getFD().sync();
        try {
            Thread.sleep(1);
        } catch (InterruptedException e) {
            throw new RuntimeException(e);
        }
    }

    @Override
    public long length() throws IOException {
        ensureOpen();
        return raf.length();
    }

    @Override
    public void setLength(long newLength) throws IOException {
        ensureOpen();
        ensureWritable();
        if (newLength < 0) {
            throw new IllegalArgumentException("newLength must be non-negative: " + newLength);
        }
        raf.setLength(newLength);
    }

    @Override
    public void close() throws IOException {
        if (open && raf != null) {
            try {
                raf.close();
            } finally {
                raf = null;
                open = false;
            }
            log.trace("Closed FileIO: path={}", path);
        }
    }

    @Override
    public boolean isOpen() {
        return open && raf != null;
    }

    @Override
    public VirtualDataPath getPath() {
        return path;
    }

    @Override
    public OpenMode getMode() {
        return mode;
    }

    private void ensureOpen() throws IOException {
        if (!open || raf == null) {
            throw new IOException("FileIO is not open: path=" + path);
        }
    }

    private void ensureWritable() {
        if (mode == OpenMode.READ) {
            throw new IllegalStateException("FileIO is opened in read-only mode: path=" + path);
        }
    }

    @Override
    public String toString() {
        return "FileIO{path=" + path + ", mode=" + mode + ", open=" + open + '}';
    }
}
