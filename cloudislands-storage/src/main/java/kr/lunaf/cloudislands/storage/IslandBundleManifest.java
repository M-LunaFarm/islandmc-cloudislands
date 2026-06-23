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
    String restorePolicy,
    String pluginVersion,
    int minecraftDataVersion,
    String paperApiBaseline,
    String templateVersion
) {
    public static final int CURRENT_FORMAT_VERSION = 3;
    public static final int CURRENT_MINECRAFT_DATA_VERSION = 4435;
    public static final String DEFAULT_PLUGIN_VERSION = "unknown";
    public static final int DEFAULT_MINECRAFT_DATA_VERSION = 0;
    public static final String DEFAULT_PAPER_API_BASELINE = "1.21.11";
    public static final String DEFAULT_TEMPLATE_VERSION = "unknown";

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
        pluginVersion = pluginVersion == null || pluginVersion.isBlank() ? DEFAULT_PLUGIN_VERSION : pluginVersion;
        minecraftDataVersion = Math.max(0, minecraftDataVersion);
        paperApiBaseline = paperApiBaseline == null || paperApiBaseline.isBlank() ? DEFAULT_PAPER_API_BASELINE : paperApiBaseline;
        templateVersion = templateVersion == null || templateVersion.isBlank() ? DEFAULT_TEMPLATE_VERSION : templateVersion;
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
        String snapshotReason,
        boolean portable,
        String placementPolicy,
        String restorePolicy
    ) {
        this(islandId, ownerUuid, formatVersion, minecraftVersion, schemaVersion, size, spawn, homes, warps, biomes, createdAt, savedAt, checksum, checksumAlgorithm, compression, storagePath, sizeBytes, snapshotReason, portable, placementPolicy, restorePolicy, DEFAULT_PLUGIN_VERSION, DEFAULT_MINECRAFT_DATA_VERSION, DEFAULT_PAPER_API_BASELINE, DEFAULT_TEMPLATE_VERSION);
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
        return new IslandBundleManifest(islandId, ownerUuid, formatVersion, minecraftVersion, schemaVersion, size, spawn, homes, warps, biomes, createdAt, savedAt, checksum, checksumAlgorithm, compression, storagePath, sizeBytes, reason == null ? "" : reason, portable, placementPolicy, restorePolicy, pluginVersion, minecraftDataVersion, paperApiBaseline, templateVersion);
    }

    public IslandBundleManifest withStoredBundle(String checksum, String checksumAlgorithm, String compression, String storagePath, long sizeBytes) {
        return new IslandBundleManifest(islandId, ownerUuid, formatVersion, minecraftVersion, schemaVersion, size, spawn, homes, warps, biomes, createdAt, savedAt, checksum, checksumAlgorithm, compression, storagePath, sizeBytes, snapshotReason, portable, placementPolicy, restorePolicy, pluginVersion, minecraftDataVersion, paperApiBaseline, templateVersion);
    }

    public boolean restorePreflightReady() {
        return restoreMissingRequirements().isEmpty();
    }

    public String sourceMinecraftVersion() {
        return minecraftVersion;
    }

    public String sourceAdapterId() {
        return paperApiBaseline;
    }

    public int bundleSchemaVersion() {
        return formatVersion;
    }

    public int worldDataVersion() {
        return minecraftDataVersion;
    }

    public int minimumReaderVersion() {
        return formatVersion;
    }

    public List<String> featureCapabilities() {
        List<String> capabilities = new ArrayList<>();
        if (portable) {
            capabilities.add("portable");
        }
        if (!checksumAlgorithm.isBlank()) {
            capabilities.add("checksum:" + checksumAlgorithm);
        }
        if (!compression.isBlank()) {
            capabilities.add("compression:" + compression);
        }
        capabilities.add("node-agnostic");
        if (minecraftDataVersion < CURRENT_MINECRAFT_DATA_VERSION) {
            capabilities.add("datafixer-upgrade");
        }
        return List.copyOf(capabilities);
    }

    public String restoreCompatibilitySummary() {
        return BundleCompatibilityPolicy.evaluate(this).summary();
    }

    public String restoreMigrationAdapter() {
        return BundleCompatibilityPolicy.evaluate(this).migrationAdapterId();
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
        if (formatVersion > CURRENT_FORMAT_VERSION) {
            missing.add("formatVersion");
        }
        if (minecraftDataVersion > CURRENT_MINECRAFT_DATA_VERSION) {
            missing.add("minecraftDataVersion");
        }
        for (String requirement : BundleCompatibilityPolicy.evaluate(this).missingRequirements()) {
            if (!missing.contains(requirement)) {
                missing.add(requirement);
            }
        }
        return List.copyOf(missing);
    }

    public String restorePreflightSummary() {
        List<String> missing = restoreMissingRequirements();
        return missing.isEmpty() ? "ready" : "missing-" + String.join("+", missing);
    }
}
