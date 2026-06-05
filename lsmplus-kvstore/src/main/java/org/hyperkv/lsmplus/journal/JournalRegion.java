package org.hyperkv.lsmplus.journal;

import java.util.UUID;

public final class JournalRegion {

    private final long major;
    private final long minor;
    private final UUID chunkId;

    public JournalRegion(long major, long minor, UUID chunkId) {
        if (chunkId == null) {
            throw new IllegalArgumentException("chunkId must not be null");
        }
        this.major = major;
        this.minor = minor;
        this.chunkId = chunkId;
    }

    public long getMajor() {
        return major;
    }

    public long getMinor() {
        return minor;
    }

    public UUID getChunkId() {
        return chunkId;
    }

    @Override
    public boolean equals(Object o) {
        if (this == o) return true;
        if (!(o instanceof JournalRegion other)) return false;
        return major == other.major && minor == other.minor && chunkId.equals(other.chunkId);
    }

    @Override
    public int hashCode() {
        int result = Long.hashCode(major);
        result = 31 * result + Long.hashCode(minor);
        result = 31 * result + chunkId.hashCode();
        return result;
    }

    @Override
    public String toString() {
        return "JournalRegion{major=" + major + ", minor=" + minor + ", chunkId=" + chunkId + '}';
    }
}