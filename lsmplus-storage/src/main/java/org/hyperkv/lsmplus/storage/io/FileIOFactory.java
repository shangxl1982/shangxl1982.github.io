package org.hyperkv.lsmplus.storage.io;

import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

import java.io.File;
import java.io.IOException;
import java.nio.file.Files;
import java.nio.file.Path;

public class FileIOFactory implements IOFactory {

    private static final Logger log = LoggerFactory.getLogger(FileIOFactory.class);

    public static final FileIOFactory INSTANCE = new FileIOFactory();

    @Override
    public AbstractIO createIO() {
        return new FileIO();
    }

    @Override
    public VirtualDataPath createChunkPath(String basePath, String chunkType, String chunkId) {
        String dir = "journal".equals(chunkType) ? "journal" : "data";
        String filePath = basePath + File.separator + dir + File.separator + "chunk_" + chunkId + ".dat";
        return VirtualDataPath.file(filePath);
    }

    @Override
    public boolean exists(VirtualDataPath path) {
        if (!path.isFile()) {
            return false;
        }
        return new File(path.getPath()).exists();
    }

    @Override
    public void delete(VirtualDataPath path) throws IOException {
        if (!path.isFile()) {
            throw new IOException("Cannot delete non-file path: " + path);
        }
        File file = new File(path.getPath());
        if (file.exists()) {
            if (!file.delete()) {
                throw new IOException("Failed to delete file: " + path);
            }
            log.trace("Deleted file: {}", path);
        }
    }

    @Override
    public void createDirectories(VirtualDataPath path) throws IOException {
        if (!path.isFile()) {
            throw new IOException("Cannot create directories for non-file path: " + path);
        }
        File file = new File(path.getPath());
        File parent = file.getParentFile();
        if (parent != null && !parent.exists()) {
            if (!parent.mkdirs()) {
                throw new IOException("Failed to create directories: " + parent);
            }
            log.trace("Created directories: {}", parent);
        }
    }
}
