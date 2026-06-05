package org.hyperkv.lsmplus.api.model;

import com.google.protobuf.ByteString;
import com.google.protobuf.InvalidProtocolBufferException;
import org.hyperkv.lsmplus.proto.Common.BackupType;
import org.hyperkv.lsmplus.proto.Journal.JournalReplayPointProto;
import org.hyperkv.lsmplus.proto.Metadata.BackupMetadata;
import org.hyperkv.lsmplus.proto.Metadata.TreeStats;
import org.junit.jupiter.api.Test;

import static org.junit.jupiter.api.Assertions.*;

class BackupMetadataProtoTest {

    @Test
    void testFullBackupMetadata() throws InvalidProtocolBufferException {
        JournalReplayPointProto replayPoint = JournalReplayPointProto.newBuilder()
                .setRegionMajor(1L)
                .setRegionMinor(2L)
                .setOffset(1024)
                .build();

        TreeStats stats = TreeStats.newBuilder()
                .setLeafPageCount(100L)
                .setIndexPageCount(10L)
                .setTotalEntries(1000L)
                .setHeight(3)
                .setTotalSize(1024 * 1024)
                .build();

        BackupMetadata original = BackupMetadata.newBuilder()
                .setBackupId("backup-001")
                .setBackupType(BackupType.FULL)
                .setCreatedAt(System.currentTimeMillis())
                .setTreeVersion(5L)
                .setTreeMns(100L)
                .setReplayPoint(replayPoint)
                .setCutoffPoint(replayPoint)
                .setTreeStats(stats)
                .setChecksum(ByteString.copyFrom(new byte[]{1, 2, 3, 4, 5}))
                .build();

        byte[] serialized = original.toByteArray();
        BackupMetadata restored = BackupMetadata.parseFrom(serialized);

        assertEquals("backup-001", restored.getBackupId());
        assertEquals(BackupType.FULL, restored.getBackupType());
        assertEquals(5L, restored.getTreeVersion());
        assertEquals(100L, restored.getTreeMns());
        assertEquals(replayPoint, restored.getReplayPoint());
        assertEquals(stats, restored.getTreeStats());
        assertArrayEquals(new byte[]{1, 2, 3, 4, 5}, restored.getChecksum().toByteArray());
    }

    @Test
    void testIncrementalBackupMetadata() throws InvalidProtocolBufferException {
        BackupMetadata incremental = BackupMetadata.newBuilder()
                .setBackupId("backup-002")
                .setBackupType(BackupType.INCREMENTAL)
                .setParentBackupId("backup-001")
                .setCreatedAt(System.currentTimeMillis())
                .setTreeVersion(6L)
                .build();

        byte[] serialized = incremental.toByteArray();
        BackupMetadata restored = BackupMetadata.parseFrom(serialized);

        assertEquals("backup-002", restored.getBackupId());
        assertEquals(BackupType.INCREMENTAL, restored.getBackupType());
        assertEquals("backup-001", restored.getParentBackupId());
        assertEquals(6L, restored.getTreeVersion());
    }

    @Test
    void testBackupTypeValues() throws InvalidProtocolBufferException {
        for (BackupType type : BackupType.values()) {
            if (type == BackupType.UNRECOGNIZED) {
                continue;
            }

            BackupMetadata backup = BackupMetadata.newBuilder()
                    .setBackupId("test-backup")
                    .setBackupType(type)
                    .build();

            byte[] serialized = backup.toByteArray();
            BackupMetadata restored = BackupMetadata.parseFrom(serialized);
            assertEquals(type, restored.getBackupType());
        }
    }

    @Test
    void testTreeStatsPreservation() throws InvalidProtocolBufferException {
        TreeStats original = TreeStats.newBuilder()
                .setLeafPageCount(500L)
                .setIndexPageCount(50L)
                .setTotalEntries(5000L)
                .setHeight(4)
                .setTotalSize(10 * 1024 * 1024)
                .build();

        BackupMetadata backup = BackupMetadata.newBuilder()
                .setBackupId("test")
                .setTreeStats(original)
                .build();

        byte[] serialized = backup.toByteArray();
        BackupMetadata restored = BackupMetadata.parseFrom(serialized);

        TreeStats restoredStats = restored.getTreeStats();
        assertEquals(500L, restoredStats.getLeafPageCount());
        assertEquals(50L, restoredStats.getIndexPageCount());
        assertEquals(5000L, restoredStats.getTotalEntries());
        assertEquals(4, restoredStats.getHeight());
        assertEquals(10 * 1024 * 1024, restoredStats.getTotalSize());
    }

    @Test
    void testJournalReplayPoints() throws InvalidProtocolBufferException {
        JournalReplayPointProto replayPoint = JournalReplayPointProto.newBuilder()
                .setRegionMajor(10L)
                .setRegionMinor(20L)
                .setOffset(2048)
                .build();

        JournalReplayPointProto cutoffPoint = JournalReplayPointProto.newBuilder()
                .setRegionMajor(15L)
                .setRegionMinor(25L)
                .setOffset(4096)
                .build();

        BackupMetadata backup = BackupMetadata.newBuilder()
                .setBackupId("test")
                .setReplayPoint(replayPoint)
                .setCutoffPoint(cutoffPoint)
                .build();

        byte[] serialized = backup.toByteArray();
        BackupMetadata restored = BackupMetadata.parseFrom(serialized);

        assertEquals(replayPoint, restored.getReplayPoint());
        assertEquals(cutoffPoint, restored.getCutoffPoint());
    }

    @Test
    void testChecksumPreservation() throws InvalidProtocolBufferException {
        byte[] checksum = new byte[32];
        for (int i = 0; i < checksum.length; i++) {
            checksum[i] = (byte) (i % 256);
        }

        BackupMetadata backup = BackupMetadata.newBuilder()
                .setBackupId("test")
                .setChecksum(ByteString.copyFrom(checksum))
                .build();

        byte[] serialized = backup.toByteArray();
        BackupMetadata restored = BackupMetadata.parseFrom(serialized);

        assertArrayEquals(checksum, restored.getChecksum().toByteArray());
    }

    @Test
    void testTimestampPreservation() throws InvalidProtocolBufferException {
        long timestamp = System.currentTimeMillis();

        BackupMetadata backup = BackupMetadata.newBuilder()
                .setBackupId("test")
                .setCreatedAt(timestamp)
                .build();

        byte[] serialized = backup.toByteArray();
        BackupMetadata restored = BackupMetadata.parseFrom(serialized);

        assertEquals(timestamp, restored.getCreatedAt());
    }
}
