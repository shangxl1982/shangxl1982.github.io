package org.hyperkv.lsmplus.storage.io;

import java.io.IOException;

public interface IOFactory {
    
    AbstractIO createIO();
    
    VirtualDataPath createChunkPath(String basePath, String chunkType, String chunkId);
    
    boolean exists(VirtualDataPath path);
    
    void delete(VirtualDataPath path) throws IOException;
    
    void createDirectories(VirtualDataPath path) throws IOException;
}
