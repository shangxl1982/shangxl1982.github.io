package org.hyperkv.lsmplus.journal;

public final class JournalReplayPoint implements Comparable<JournalReplayPoint> {

    private final long regionMajor;
    private final long regionMinor;
    private final int offset;

    public JournalReplayPoint(long regionMajor, long regionMinor, int offset) {
        this.regionMajor = regionMajor;
        this.regionMinor = regionMinor;
        this.offset = offset;
    }

    public long getRegionMajor() {
        return regionMajor;
    }

    public long getRegionMinor() {
        return regionMinor;
    }

    public int getOffset() {
        return offset;
    }

    public org.hyperkv.lsmplus.proto.Journal.JournalReplayPointProto toProto() {
        return org.hyperkv.lsmplus.proto.Journal.JournalReplayPointProto.newBuilder()
                .setRegionMajor(regionMajor)
                .setRegionMinor(regionMinor)
                .setOffset(offset)
                .build();
    }

    public static JournalReplayPoint fromProto(org.hyperkv.lsmplus.proto.Journal.JournalReplayPointProto proto) {
        return new JournalReplayPoint(
                proto.getRegionMajor(),
                proto.getRegionMinor(),
                proto.getOffset()
        );
    }

    @Override
    public boolean equals(Object o) {
        if (this == o) return true;
        if (!(o instanceof JournalReplayPoint other)) return false;
        return regionMajor == other.regionMajor &&
               regionMinor == other.regionMinor &&
               offset == other.offset;
    }

    @Override
    public int hashCode() {
        int result = Long.hashCode(regionMajor);
        result = 31 * result + Long.hashCode(regionMinor);
        result = 31 * result + offset;
        return result;
    }

    @Override
    public String toString() {
        return "JournalReplayPoint{major=" + regionMajor + ", minor=" + regionMinor + ", offset=" + offset + '}';
    }

    @Override
    public int compareTo(JournalReplayPoint other) {
        if (this.regionMajor != other.regionMajor) {
            return Long.compare(this.regionMajor, other.regionMajor);
        }
        if (this.regionMinor != other.regionMinor) {
            return Long.compare(this.regionMinor, other.regionMinor);
        }
        return Integer.compare(this.offset, other.offset);
    }
}