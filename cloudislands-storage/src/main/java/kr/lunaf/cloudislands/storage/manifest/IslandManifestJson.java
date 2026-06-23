package kr.lunaf.cloudislands.storage.manifest;

import java.time.Instant;
import java.util.List;
import java.util.Map;
import java.util.UUID;
import kr.lunaf.cloudislands.api.model.IslandLocation;
import kr.lunaf.cloudislands.common.json.JsonCodec;
import kr.lunaf.cloudislands.storage.IslandBundleManifest;
import kr.lunaf.cloudislands.storage.MinecraftKeyMigrations;

public final class IslandManifestJson {
    public static final int LEGACY_MANIFEST_SCHEMA_VERSION = 1;
    public static final int CURRENT_MANIFEST_SCHEMA_VERSION = 2;

    private IslandManifestJson() {}

    public static String write(IslandBundleManifest manifest) {
        IslandLocation spawn = manifest.spawn();
        return "{"
            + "\"manifestSchemaVersion\":" + CURRENT_MANIFEST_SCHEMA_VERSION + ","
            + "\"islandId\":\"" + manifest.islandId() + "\","
            + "\"ownerUuid\":\"" + manifest.ownerUuid() + "\","
            + "\"formatVersion\":" + manifest.formatVersion() + ","
            + "\"minecraftVersion\":\"" + escape(manifest.minecraftVersion()) + "\","
            + "\"pluginVersion\":\"" + escape(manifest.pluginVersion()) + "\","
            + "\"minecraftDataVersion\":" + manifest.minecraftDataVersion() + ","
            + "\"paperApiBaseline\":\"" + escape(manifest.paperApiBaseline()) + "\","
            + "\"templateVersion\":\"" + escape(manifest.templateVersion()) + "\","
            + "\"schemaVersion\":" + manifest.schemaVersion() + ","
            + "\"size\":" + manifest.size() + ","
            + "\"spawn\":{"
            + "\"world\":\"" + escape(spawn.worldName()) + "\","
            + "\"x\":" + spawn.localX() + ",\"y\":" + spawn.localY() + ",\"z\":" + spawn.localZ() + ","
            + "\"yaw\":" + spawn.yaw() + ",\"pitch\":" + spawn.pitch()
            + "},"
            + "\"homes\":" + stringArray(manifest.homes()) + ","
            + "\"warps\":" + stringArray(manifest.warps()) + ","
            + "\"biomes\":" + stringArray(manifest.biomes()) + ","
            + "\"createdAt\":\"" + manifest.createdAt() + "\","
            + "\"savedAt\":\"" + manifest.savedAt() + "\","
            + "\"checksum\":\"" + escape(manifest.checksum()) + "\","
            + "\"checksumAlgorithm\":\"" + escape(manifest.checksumAlgorithm()) + "\","
            + "\"compression\":\"" + escape(manifest.compression()) + "\","
            + "\"storagePath\":\"" + escape(manifest.storagePath()) + "\","
            + "\"sizeBytes\":" + manifest.sizeBytes() + ","
            + "\"snapshotReason\":\"" + escape(manifest.snapshotReason()) + "\","
            + "\"portable\":" + manifest.portable() + ","
            + "\"placementPolicy\":\"" + escape(manifest.placementPolicy()) + "\","
            + "\"restorePolicy\":\"" + escape(manifest.restorePolicy()) + "\","
            + "\"nodeBindingPolicy\":\"" + escape(kr.lunaf.cloudislands.storage.BundleRestorePolicy.NODE_BINDING_POLICY) + "\","
            + "\"manifestForbiddenRuntimeFields\":\"" + escape(kr.lunaf.cloudislands.storage.BundleRestorePolicy.MANIFEST_FORBIDDEN_RUNTIME_FIELDS) + "\","
            + "\"portableBundleLayout\":\"" + escape(kr.lunaf.cloudislands.storage.BundleRestorePolicy.PORTABLE_BUNDLE_LAYOUT) + "\","
            + "\"restorePreflightReady\":" + manifest.restorePreflightReady() + ","
            + "\"restorePreflightSummary\":\"" + escape(manifest.restorePreflightSummary()) + "\","
            + "\"restoreRequirements\":\"" + escape(kr.lunaf.cloudislands.storage.BundleRestorePolicy.RESTORE_REQUIREMENTS) + "\","
            + "\"restoreMissingRequirements\":" + stringArray(manifest.restoreMissingRequirements())
            + "}";
    }

    public static IslandBundleManifest read(String json) {
        Map<String, Object> root = JsonCodec.readObject(json);
        int manifestSchemaVersion = integer(root, "manifestSchemaVersion", LEGACY_MANIFEST_SCHEMA_VERSION);
        if (manifestSchemaVersion > CURRENT_MANIFEST_SCHEMA_VERSION) {
            throw new IllegalArgumentException("Unsupported island bundle manifest schema version " + manifestSchemaVersion);
        }
        UUID islandId = uuid(root, "islandId", new UUID(0L, 0L));
        UUID ownerUuid = uuid(root, "ownerUuid", new UUID(0L, 0L));
        int formatVersion = integer(root, "formatVersion", 3);
        String minecraftVersion = text(root, "minecraftVersion", "unknown");
        String pluginVersion = text(root, "pluginVersion", IslandBundleManifest.DEFAULT_PLUGIN_VERSION);
        int minecraftDataVersion = integer(root, "minecraftDataVersion", IslandBundleManifest.DEFAULT_MINECRAFT_DATA_VERSION);
        String paperApiBaseline = text(root, "paperApiBaseline", IslandBundleManifest.DEFAULT_PAPER_API_BASELINE);
        String templateVersion = text(root, "templateVersion", IslandBundleManifest.DEFAULT_TEMPLATE_VERSION);
        int schemaVersion = integer(root, "schemaVersion", 12);
        int size = integer(root, "size", 300);
        Map<?, ?> spawnJson = object(root, "spawn");
        IslandLocation spawn = new IslandLocation(
            text(spawnJson, "world", "ci_shard_001"),
            decimal(spawnJson, "x", 0.5D),
            decimal(spawnJson, "y", 100.0D),
            decimal(spawnJson, "z", 0.5D),
            (float) decimal(spawnJson, "yaw", 180.0D),
            (float) decimal(spawnJson, "pitch", 0.0D)
        );
        List<String> homes = stringArray(root.get("homes"));
        List<String> warps = stringArray(root.get("warps"));
        List<String> biomes = stringArray(root.get("biomes")).stream()
            .map(key -> MinecraftKeyMigrations.defaults().migrateBiome(key, minecraftDataVersion).orElse(key))
            .toList();
        Instant createdAt = instant(root, "createdAt", Instant.now());
        Instant savedAt = instant(root, "savedAt", createdAt);
        String checksum = text(root, "checksum", "");
        String checksumAlgorithm = text(root, "checksumAlgorithm", "SHA-256");
        String compression = text(root, "compression", "zstd");
        String storagePath = text(root, "storagePath", "");
        long sizeBytes = number(root, "sizeBytes", 0L);
        String snapshotReason = text(root, "snapshotReason", "");
        boolean portable = bool(root, "portable", true);
        String placementPolicy = text(root, "placementPolicy", "node-agnostic-shard-cell-remap");
        String restorePolicy = text(root, "restorePolicy", "verify-checksum-then-restore-to-current-active-node");
        return new IslandBundleManifest(islandId, ownerUuid, formatVersion, minecraftVersion, schemaVersion, size, spawn, homes, warps, biomes, createdAt, savedAt, checksum, checksumAlgorithm, compression, storagePath, sizeBytes, snapshotReason, portable, placementPolicy, restorePolicy, pluginVersion, minecraftDataVersion, paperApiBaseline, templateVersion);
    }

    public static IslandBundleManifest minimal(UUID islandId, UUID ownerUuid, String checksum) {
        Instant now = Instant.now();
        return new IslandBundleManifest(islandId, ownerUuid, 3, "1.21.11", 12, 300, new IslandLocation("ci_shard_001", 0.5D, 100.0D, 0.5D, 180.0F, 0.0F), now, now, checksum);
    }

    private static String text(Map<?, ?> object, String field, String fallback) {
        Object value = object.get(field);
        if (value == null) {
            return fallback;
        }
        if (value instanceof String text && !text.isBlank()) {
            return text;
        }
        return fallback;
    }

    private static UUID uuid(Map<?, ?> object, String field, UUID fallback) {
        try {
            return UUID.fromString(text(object, field, fallback.toString()));
        } catch (IllegalArgumentException ignored) {
            return fallback;
        }
    }

    private static int integer(Map<?, ?> object, String field, int fallback) {
        Object value = object.get(field);
        if (value instanceof Number number) {
            return number.intValue();
        }
        if (value instanceof String text && !text.isBlank()) {
            try {
                return Integer.parseInt(text);
            } catch (NumberFormatException ignored) {
                return fallback;
            }
        }
        return fallback;
    }

    private static long number(Map<?, ?> object, String field, long fallback) {
        Object value = object.get(field);
        if (value instanceof Number number) {
            return number.longValue();
        }
        if (value instanceof String text && !text.isBlank()) {
            try {
                return Long.parseLong(text);
            } catch (NumberFormatException ignored) {
                return fallback;
            }
        }
        return fallback;
    }

    private static boolean bool(Map<?, ?> object, String field, boolean fallback) {
        Object value = object.get(field);
        if (value instanceof Boolean booleanValue) {
            return booleanValue;
        }
        return value instanceof String text && !text.isBlank() ? Boolean.parseBoolean(text) : fallback;
    }

    private static double decimal(Map<?, ?> object, String field, double fallback) {
        Object value = object.get(field);
        if (value instanceof Number number) {
            return number.doubleValue();
        }
        try {
            return value instanceof String text && !text.isBlank() ? Double.parseDouble(text) : fallback;
        } catch (NumberFormatException ignored) {
            return fallback;
        }
    }

    private static Instant instant(Map<?, ?> object, String field, Instant fallback) {
        try {
            return Instant.parse(text(object, field, fallback.toString()));
        } catch (RuntimeException ignored) {
            return fallback;
        }
    }

    private static List<String> stringArray(Object value) {
        if (!(value instanceof List<?> list)) {
            return List.of();
        }
        return list.stream()
            .filter(String.class::isInstance)
            .map(String.class::cast)
            .toList();
    }

    private static Map<?, ?> object(Map<?, ?> object, String field) {
        Object value = object.get(field);
        return value instanceof Map<?, ?> map ? map : Map.of();
    }

    private static String stringArray(List<String> values) {
        if (values == null || values.isEmpty()) {
            return "[]";
        }
        StringBuilder builder = new StringBuilder("[");
        boolean first = true;
        for (String value : values) {
            if (value == null) {
                continue;
            }
            if (!first) {
                builder.append(',');
            }
            builder.append('"').append(escape(value)).append('"');
            first = false;
        }
        return builder.append(']').toString();
    }

    private static String escape(String value) {
        return value == null ? "" : value.replace("\\", "\\\\").replace("\"", "\\\"");
    }
}
