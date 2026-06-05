package org.hyperkv.lsmplus.storage.io;

import java.io.IOException;

public interface AbstractIO extends AutoCloseable {

    enum OpenMode {
        READ,
        WRITE,
        READ_WRITE
    }

    void open(VirtualDataPath path, OpenMode mode) throws IOException;

    byte[] read(long offset, int length) throws IOException;

    void write(long offset, byte[] data) throws IOException;

    void sync(long offset, long length) throws IOException;

    void sync() throws IOException;

    long length() throws IOException;

    void setLength(long newLength) throws IOException;

    void close() throws IOException;

    boolean isOpen();

    VirtualDataPath getPath();

    OpenMode getMode();
}
