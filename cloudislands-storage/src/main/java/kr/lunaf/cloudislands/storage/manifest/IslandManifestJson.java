package kr.lunaf.cloudislands.storage.manifest;

import java.time.Instant;
import java.util.ArrayList;
import java.util.List;
import java.util.UUID;
import java.util.regex.Matcher;
import java.util.regex.Pattern;
import kr.lunaf.cloudislands.api.model.IslandLocation;
import kr.lunaf.cloudislands.storage.IslandBundleManifest;

public final class IslandManifestJson {
    private IslandManifestJson() {}

    public static String write(IslandBundleManifest manifest) {
        IslandLocation spawn = manifest.spawn();
        return "{"
            + "\"islandId\":\"" + manifest.islandId() + "\","
            + "\"ownerUuid\":\"" + manifest.ownerUuid() + "\","
            + "\"formatVersion\":" + manifest.formatVersion() + ","
            + "\"minecraftVersion\":\"" + escape(manifest.minecraftVersion()) + "\","
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
        UUID islandId = uuid(json, "islandId", new UUID(0L, 0L));
        UUID ownerUuid = uuid(json, "ownerUuid", new UUID(0L, 0L));
        int formatVersion = integer(json, "formatVersion", 3);
        String minecraftVersion = text(json, "minecraftVersion", "unknown");
        int schemaVersion = integer(json, "schemaVersion", 12);
        int size = integer(json, "size", 300);
        IslandLocation spawn = new IslandLocation(
            text(json, "world", "ci_shard_001"),
            decimal(json, "x", 0.5D),
            decimal(json, "y", 100.0D),
            decimal(json, "z", 0.5D),
            (float) decimal(json, "yaw", 180.0D),
            (float) decimal(json, "pitch", 0.0D)
        );
        List<String> homes = stringArray(json, "homes");
        List<String> warps = stringArray(json, "warps");
        List<String> biomes = stringArray(json, "biomes");
        Instant createdAt = instant(json, "createdAt", Instant.now());
        Instant savedAt = instant(json, "savedAt", createdAt);
        String checksum = text(json, "checksum", "");
        String checksumAlgorithm = text(json, "checksumAlgorithm", "SHA-256");
        String compression = text(json, "compression", "zstd");
        String storagePath = text(json, "storagePath", "");
        long sizeBytes = number(json, "sizeBytes", 0L);
        String snapshotReason = text(json, "snapshotReason", "");
        boolean portable = bool(json, "portable", true);
        String placementPolicy = text(json, "placementPolicy", "node-agnostic-shard-cell-remap");
        String restorePolicy = text(json, "restorePolicy", "verify-checksum-then-restore-to-current-active-node");
        return new IslandBundleManifest(islandId, ownerUuid, formatVersion, minecraftVersion, schemaVersion, size, spawn, homes, warps, biomes, createdAt, savedAt, checksum, checksumAlgorithm, compression, storagePath, sizeBytes, snapshotReason, portable, placementPolicy, restorePolicy);
    }

    public static IslandBundleManifest minimal(UUID islandId, UUID ownerUuid, String checksum) {
        Instant now = Instant.now();
        return new IslandBundleManifest(islandId, ownerUuid, 3, "1.21.11", 12, 300, new IslandLocation("ci_shard_001", 0.5D, 100.0D, 0.5D, 180.0F, 0.0F), now, now, checksum);
    }

    private static String text(String json, String field, String fallback) {
        Matcher matcher = Pattern.compile("\"" + Pattern.quote(field) + "\"\\s*:\\s*\"((?:\\\\.|[^\"])*)\"").matcher(json);
        if (!matcher.find()) {
            return fallback;
        }
        return unescape(matcher.group(1));
    }

    private static UUID uuid(String json, String field, UUID fallback) {
        try {
            return UUID.fromString(text(json, field, fallback.toString()));
        } catch (IllegalArgumentException ignored) {
            return fallback;
        }
    }

    private static int integer(String json, String field, int fallback) {
        try {
            return Integer.parseInt(scalar(json, field, Integer.toString(fallback)));
        } catch (NumberFormatException ignored) {
            return fallback;
        }
    }

    private static long number(String json, String field, long fallback) {
        try {
            return Long.parseLong(scalar(json, field, Long.toString(fallback)));
        } catch (NumberFormatException ignored) {
            return fallback;
        }
    }

    private static boolean bool(String json, String field, boolean fallback) {
        String value = scalar(json, field, Boolean.toString(fallback));
        return value.equalsIgnoreCase("true") || (!value.equalsIgnoreCase("false") && fallback);
    }

    private static double decimal(String json, String field, double fallback) {
        try {
            return Double.parseDouble(scalar(json, field, Double.toString(fallback)));
        } catch (NumberFormatException ignored) {
            return fallback;
        }
    }

    private static Instant instant(String json, String field, Instant fallback) {
        try {
            return Instant.parse(text(json, field, fallback.toString()));
        } catch (RuntimeException ignored) {
            return fallback;
        }
    }

    private static String scalar(String json, String field, String fallback) {
        Matcher matcher = Pattern.compile("\"" + Pattern.quote(field) + "\"\\s*:\\s*([-0-9.]+|true|false)").matcher(json);
        if (!matcher.find()) {
            return fallback;
        }
        return matcher.group(1);
    }

    private static List<String> stringArray(String json, String field) {
        Matcher matcher = Pattern.compile("\"" + Pattern.quote(field) + "\"\\s*:\\s*\\[(.*?)]").matcher(json);
        if (!matcher.find()) {
            return List.of();
        }
        String body = matcher.group(1).trim();
        if (body.isEmpty()) {
            return List.of();
        }
        List<String> values = new ArrayList<>();
        Matcher valueMatcher = Pattern.compile("\"((?:\\\\.|[^\"])*)\"").matcher(body);
        while (valueMatcher.find()) {
            values.add(unescape(valueMatcher.group(1)));
        }
        return List.copyOf(values);
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

    private static String unescape(String value) {
        return value.replace("\\\"", "\"").replace("\\\\", "\\");
    }

    private static String escape(String value) {
        return value == null ? "" : value.replace("\\", "\\\\").replace("\"", "\\\"");
    }
}
