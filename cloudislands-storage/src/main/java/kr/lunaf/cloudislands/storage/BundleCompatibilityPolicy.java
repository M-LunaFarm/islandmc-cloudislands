package kr.lunaf.cloudislands.storage;

import java.io.IOException;
import java.util.ArrayList;
import java.util.List;

public final class BundleCompatibilityPolicy {
    public static final int TARGET_BUNDLE_SCHEMA_VERSION = IslandBundleManifest.CURRENT_FORMAT_VERSION;
    public static final int TARGET_WORLD_DATA_VERSION = IslandBundleManifest.CURRENT_MINECRAFT_DATA_VERSION;
    public static final String CURRENT_READER_VERSION = "cloudislands-bundle-reader-" + TARGET_BUNDLE_SCHEMA_VERSION;
    public static final String TARGET_ADAPTER_ID = "paper-" + IslandBundleManifest.DEFAULT_PAPER_API_BASELINE;
    public static final String UPGRADE_ADAPTER_ID = "minecraft-datafixer-upgrade";
    public static final String LEGACY_ADAPTER_ID = "legacy-manifest-defaults";

    private BundleCompatibilityPolicy() {}

    public static CompatibilityResult evaluate(IslandBundleManifest manifest) {
        List<String> missing = new ArrayList<>();
        int sourceBundleSchemaVersion = Math.max(0, manifest.bundleSchemaVersion());
        int sourceWorldDataVersion = Math.max(0, manifest.worldDataVersion());
        int minimumReaderVersion = Math.max(0, manifest.minimumReaderVersion());

        if (sourceBundleSchemaVersion > TARGET_BUNDLE_SCHEMA_VERSION || minimumReaderVersion > TARGET_BUNDLE_SCHEMA_VERSION) {
            addMissing(missing, "formatVersion");
        }
        if (sourceWorldDataVersion > TARGET_WORLD_DATA_VERSION) {
            addMissing(missing, "minecraftDataVersion");
        }
        if (!missing.isEmpty()) {
            return new CompatibilityResult(
                false,
                false,
                "",
                sourceBundleSchemaVersion,
                TARGET_BUNDLE_SCHEMA_VERSION,
                sourceWorldDataVersion,
                TARGET_WORLD_DATA_VERSION,
                List.copyOf(missing),
                "incompatible-" + String.join("+", missing)
            );
        }

        if (sourceWorldDataVersion == 0) {
            return compatibleUpgrade(sourceBundleSchemaVersion, sourceWorldDataVersion, LEGACY_ADAPTER_ID);
        }
        if (sourceWorldDataVersion < TARGET_WORLD_DATA_VERSION) {
            return compatibleUpgrade(sourceBundleSchemaVersion, sourceWorldDataVersion, UPGRADE_ADAPTER_ID);
        }
        return new CompatibilityResult(
            true,
            false,
            TARGET_ADAPTER_ID,
            sourceBundleSchemaVersion,
            TARGET_BUNDLE_SCHEMA_VERSION,
            sourceWorldDataVersion,
            TARGET_WORLD_DATA_VERSION,
            List.of(),
            "compatible-current"
        );
    }

    public static void requireCompatible(IslandBundleManifest manifest) throws IOException {
        CompatibilityResult result = evaluate(manifest);
        if (!result.compatible()) {
            throw new IOException("incompatible island bundle restore: " + result.summary());
        }
    }

    private static CompatibilityResult compatibleUpgrade(int sourceBundleSchemaVersion, int sourceWorldDataVersion, String adapterId) {
        return new CompatibilityResult(
            true,
            true,
            adapterId,
            sourceBundleSchemaVersion,
            TARGET_BUNDLE_SCHEMA_VERSION,
            sourceWorldDataVersion,
            TARGET_WORLD_DATA_VERSION,
            List.of(),
            "compatible-upgrade:" + adapterId
        );
    }

    private static void addMissing(List<String> missing, String requirement) {
        if (!missing.contains(requirement)) {
            missing.add(requirement);
        }
    }

    public record CompatibilityResult(
        boolean compatible,
        boolean migrationRequired,
        String migrationAdapterId,
        int sourceBundleSchemaVersion,
        int targetBundleSchemaVersion,
        int sourceWorldDataVersion,
        int targetWorldDataVersion,
        List<String> missingRequirements,
        String summary
    ) {
        public CompatibilityResult {
            migrationAdapterId = migrationAdapterId == null ? "" : migrationAdapterId;
            missingRequirements = missingRequirements == null ? List.of() : List.copyOf(missingRequirements);
            summary = summary == null || summary.isBlank() ? "unknown" : summary;
        }
    }
}
