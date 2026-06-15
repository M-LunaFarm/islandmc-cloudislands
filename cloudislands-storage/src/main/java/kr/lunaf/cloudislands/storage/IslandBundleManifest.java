package kr.lunaf.cloudislands.storage;

import java.time.Instant;
import java.util.List;
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
    List<String> homes,
    List<String> warps,
    List<String> biomes,
    Instant createdAt,
    Instant savedAt,
    String checksum,
    String checksumAlgorithm,
    String compression,
    String storagePath,
    long sizeBytes,
    String snapshotReason
) {
    public IslandBundleManifest {
        homes = homes == null ? List.of() : List.copyOf(homes);
        warps = warps == null ? List.of() : List.copyOf(warps);
        biomes = biomes == null ? List.of() : List.copyOf(biomes);
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
        String checksum,
        String checksumAlgorithm,
        String compression,
        String storagePath,
        long sizeBytes
    ) {
        this(islandId, ownerUuid, formatVersion, minecraftVersion, schemaVersion, size, spawn, List.of(), List.of(), List.of(), createdAt, savedAt, checksum, checksumAlgorithm, compression, storagePath, sizeBytes, "");
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
        return new IslandBundleManifest(islandId, ownerUuid, formatVersion, minecraftVersion, schemaVersion, size, spawn, homes, warps, biomes, createdAt, savedAt, checksum, checksumAlgorithm, compression, storagePath, sizeBytes, reason == null ? "" : reason);
    }
}
