package org.hyperkv.lsmplus.storage.io;

import java.util.Objects;

public final class VirtualDataPath {

    private final String scheme;
    private final String path;
    private final int hashCode;

    public VirtualDataPath(String scheme, String path) {
        if (scheme == null || scheme.isEmpty()) {
            throw new IllegalArgumentException("scheme must not be null or empty");
        }
        if (path == null || path.isEmpty()) {
            throw new IllegalArgumentException("path must not be null or empty");
        }
        this.scheme = scheme.toLowerCase();
        this.path = path;
        this.hashCode = Objects.hash(this.scheme, this.path);
    }

    public static VirtualDataPath file(String filePath) {
        return new VirtualDataPath("file", filePath);
    }

    public static VirtualDataPath memory(String path) {
        return new VirtualDataPath("memory", path);
    }

    public static VirtualDataPath s3(String bucket, String key) {
        return new VirtualDataPath("s3", bucket + "/" + key);
    }

    public String getScheme() {
        return scheme;
    }

    public String getPath() {
        return path;
    }

    public boolean isFile() {
        return "file".equals(scheme);
    }

    public boolean isMemory() {
        return "memory".equals(scheme);
    }

    public boolean isS3() {
        return "s3".equals(scheme);
    }

    @Override
    public boolean equals(Object o) {
        if (this == o) return true;
        if (o == null || getClass() != o.getClass()) return false;
        VirtualDataPath that = (VirtualDataPath) o;
        return scheme.equals(that.scheme) && path.equals(that.path);
    }

    @Override
    public int hashCode() {
        return hashCode;
    }

    @Override
    public String toString() {
        return scheme + "://" + path;
    }
}
