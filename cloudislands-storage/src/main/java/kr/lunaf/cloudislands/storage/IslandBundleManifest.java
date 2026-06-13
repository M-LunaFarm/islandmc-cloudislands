package kr.lunaf.cloudislands.storage;

import java.time.Instant;
import java.util.UUID;
import kr.lunaf.cloudislands.api.model.IslandLocation;

public record IslandBundleManifest(
    UUID islandId,
    UUID ownerUuid,
    int formatVersion,
    String minecraftVersion,
    int schemaVersion,
    int size,
    IslandLocation spawn,
    Instant createdAt,
    Instant savedAt,
    String checksum,
    String checksumAlgorithm,
    String compression,
    String storagePath,
    long sizeBytes,
    String snapshotReason
) {
    public IslandBundleManifest(
        UUID islandId,
        UUID ownerUuid,
        int formatVersion,
        String minecraftVersion,
        int schemaVersion,
        int size,
        IslandLocation spawn,
        Instant createdAt,
        Instant savedAt,
        String checksum,
        String checksumAlgorithm,
        String compression,
        String storagePath,
        long sizeBytes
    ) {
        this(islandId, ownerUuid, formatVersion, minecraftVersion, schemaVersion, size, spawn, createdAt, savedAt, checksum, checksumAlgorithm, compression, storagePath, sizeBytes, "");
    }

    public IslandBundleManifest(
        UUID islandId,
        UUID ownerUuid,
        int formatVersion,
        String minecraftVersion,
        int schemaVersion,
        int size,
        IslandLocation spawn,
        Instant createdAt,
        Instant savedAt,
        String checksum
    ) {
        this(islandId, ownerUuid, formatVersion, minecraftVersion, schemaVersion, size, spawn, createdAt, savedAt, checksum, "SHA-256", "zstd", "", 0L, "");
    }

    public IslandBundleManifest withSnapshotReason(String reason) {
        return new IslandBundleManifest(islandId, ownerUuid, formatVersion, minecraftVersion, schemaVersion, size, spawn, createdAt, savedAt, checksum, checksumAlgorithm, compression, storagePath, sizeBytes, reason == null ? "" : reason);
    }
}
