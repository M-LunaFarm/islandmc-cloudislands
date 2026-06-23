package kr.lunaf.cloudislands.coreservice.config;

import kr.lunaf.cloudislands.storage.snapshot.SnapshotRetentionPolicy;

public record SnapshotConfig(
    int keepLatest,
    SnapshotRetentionPolicy retentionPolicy
) {}
