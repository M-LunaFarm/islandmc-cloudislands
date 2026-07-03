package kr.lunaf.cloudislands.api.model;

import java.time.Instant;
import java.util.UUID;

public record IslandSnapshotRecord(
    UUID snapshotId,
    UUID islandId,
    long snapshotNo,
    String storagePath,
    String reason,
    UUID createdBy,
    String checksum,
    long sizeBytes,
    Instant createdAt,
    String nodeId
) {
    public IslandSnapshotRecord(
        UUID snapshotId,
        UUID islandId,
        long snapshotNo,
        String storagePath,
        String reason,
        UUID createdBy,
        String checksum,
        long sizeBytes,
        Instant createdAt
    ) {
        this(snapshotId, islandId, snapshotNo, storagePath, reason, createdBy, checksum, sizeBytes, createdAt, "");
    }

    public IslandSnapshotRecord {
        nodeId = nodeId == null ? "" : nodeId.trim();
    }
}
