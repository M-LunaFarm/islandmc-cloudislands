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
    Instant createdAt
) {}
