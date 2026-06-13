package kr.lunaf.cloudislands.storage.manifest;

import java.time.Instant;
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
            + "\"createdAt\":\"" + manifest.createdAt() + "\","
            + "\"savedAt\":\"" + manifest.savedAt() + "\","
            + "\"checksum\":\"" + escape(manifest.checksum()) + "\","
            + "\"checksumAlgorithm\":\"" + escape(manifest.checksumAlgorithm()) + "\","
            + "\"compression\":\"" + escape(manifest.compression()) + "\","
            + "\"storagePath\":\"" + escape(manifest.storagePath()) + "\","
            + "\"sizeBytes\":" + manifest.sizeBytes() + ","
            + "\"snapshotReason\":\"" + escape(manifest.snapshotReason()) + "\""
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
        Instant createdAt = instant(json, "createdAt", Instant.now());
        Instant savedAt = instant(json, "savedAt", createdAt);
        String checksum = text(json, "checksum", "");
        String checksumAlgorithm = text(json, "checksumAlgorithm", "SHA-256");
        String compression = text(json, "compression", "zstd");
        String storagePath = text(json, "storagePath", "");
        long sizeBytes = number(json, "sizeBytes", 0L);
        String snapshotReason = text(json, "snapshotReason", "");
        return new IslandBundleManifest(islandId, ownerUuid, formatVersion, minecraftVersion, schemaVersion, size, spawn, createdAt, savedAt, checksum, checksumAlgorithm, compression, storagePath, sizeBytes, snapshotReason);
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
        Matcher matcher = Pattern.compile("\"" + Pattern.quote(field) + "\"\\s*:\\s*([-0-9.]+)").matcher(json);
        if (!matcher.find()) {
            return fallback;
        }
        return matcher.group(1);
    }

    private static String unescape(String value) {
        return value.replace("\\\"", "\"").replace("\\\\", "\\");
    }

    private static String escape(String value) {
        return value == null ? "" : value.replace("\\", "\\\\").replace("\"", "\\\"");
    }
}
