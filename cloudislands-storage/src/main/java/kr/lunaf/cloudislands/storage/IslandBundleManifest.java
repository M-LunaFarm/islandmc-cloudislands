package kr.lunaf.cloudislands.storage;

import java.time.Instant;
import java.util.ArrayList;
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
    String snapshotReason,
    boolean portable,
    String placementPolicy,
    String restorePolicy
) {
    public IslandBundleManifest {
        homes = homes == null ? List.of() : List.copyOf(homes);
        warps = warps == null ? List.of() : List.copyOf(warps);
        biomes = biomes == null ? List.of() : List.copyOf(biomes);
        checksum = checksum == null ? "" : checksum;
        checksumAlgorithm = checksumAlgorithm == null || checksumAlgorithm.isBlank() ? BundleRestorePolicy.CHECKSUM_ALGORITHM : checksumAlgorithm;
        compression = compression == null || compression.isBlank() ? BundleRestorePolicy.COMPRESSION : compression;
        storagePath = storagePath == null ? "" : storagePath;
        snapshotReason = snapshotReason == null ? "" : snapshotReason;
        placementPolicy = placementPolicy == null || placementPolicy.isBlank() ? BundleRestorePolicy.PLACEMENT_POLICY : placementPolicy;
        restorePolicy = restorePolicy == null || restorePolicy.isBlank() ? BundleRestorePolicy.RESTORE_POLICY : restorePolicy;
    }

    public IslandBundleManifest(
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
        this(islandId, ownerUuid, formatVersion, minecraftVersion, schemaVersion, size, spawn, homes, warps, biomes, createdAt, savedAt, checksum, checksumAlgorithm, compression, storagePath, sizeBytes, snapshotReason, true, BundleRestorePolicy.PLACEMENT_POLICY, BundleRestorePolicy.RESTORE_POLICY);
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
        this(islandId, ownerUuid, formatVersion, minecraftVersion, schemaVersion, size, spawn, List.of(), List.of(), List.of(), createdAt, savedAt, checksum, BundleRestorePolicy.CHECKSUM_ALGORITHM, BundleRestorePolicy.COMPRESSION, "", 0L, "");
    }

    public IslandBundleManifest withSnapshotReason(String reason) {
        return new IslandBundleManifest(islandId, ownerUuid, formatVersion, minecraftVersion, schemaVersion, size, spawn, homes, warps, biomes, createdAt, savedAt, checksum, checksumAlgorithm, compression, storagePath, sizeBytes, reason == null ? "" : reason, portable, placementPolicy, restorePolicy);
    }

    public IslandBundleManifest withStoredBundle(String checksum, String checksumAlgorithm, String compression, String storagePath, long sizeBytes) {
        return new IslandBundleManifest(islandId, ownerUuid, formatVersion, minecraftVersion, schemaVersion, size, spawn, homes, warps, biomes, createdAt, savedAt, checksum, checksumAlgorithm, compression, storagePath, sizeBytes, snapshotReason, portable, placementPolicy, restorePolicy);
    }

    public boolean restorePreflightReady() {
        return restoreMissingRequirements().isEmpty();
    }

    public List<String> restoreMissingRequirements() {
        List<String> missing = new ArrayList<>();
        if (!portable) {
            missing.add("portable");
        }
        if (checksum.isBlank()) {
            missing.add("checksum");
        }
        if (!BundleRestorePolicy.CHECKSUM_ALGORITHM.equalsIgnoreCase(checksumAlgorithm)) {
            missing.add("checksumAlgorithm");
        }
        if (!BundleRestorePolicy.COMPRESSION.equalsIgnoreCase(compression)) {
            missing.add("compression");
        }
        if (storagePath.isBlank()) {
            missing.add("storagePath");
        }
        if (sizeBytes <= 0L) {
            missing.add("sizeBytes");
        }
        return List.copyOf(missing);
    }

    public String restorePreflightSummary() {
        List<String> missing = restoreMissingRequirements();
        return missing.isEmpty() ? "ready" : "missing-" + String.join("+", missing);
    }
}
