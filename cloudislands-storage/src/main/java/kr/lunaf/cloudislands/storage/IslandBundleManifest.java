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
    long sizeBytes
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
        String checksum
    ) {
        this(islandId, ownerUuid, formatVersion, minecraftVersion, schemaVersion, size, spawn, createdAt, savedAt, checksum, "SHA-256", "zstd", "", 0L);
    }
}
