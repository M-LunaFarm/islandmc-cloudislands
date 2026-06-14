package kr.lunaf.cloudislands.paper.storage;

import java.net.URI;
import kr.lunaf.cloudislands.storage.IslandStorage;
import kr.lunaf.cloudislands.storage.LocalIslandStorage;
import kr.lunaf.cloudislands.storage.s3.S3IslandStorage;
import org.bukkit.configuration.file.FileConfiguration;
import org.bukkit.plugin.Plugin;

public final class PaperStorageFactory {
    private PaperStorageFactory() {}

    public static IslandStorage create(Plugin plugin, FileConfiguration config) {
        IslandStorage primary = createConfiguredStorage(plugin, config, false);
        if (!fallbackEnabled(config)) {
            return primary;
        }
        String primaryBackend = backendName(config);
        String fallbackBackend = fallbackBackendName(config);
        if (primaryBackend.equals(fallbackBackend)) {
            return primary;
        }
        IslandStorage fallback = createConfiguredStorage(plugin, config, true);
        return new FallbackIslandStorage(primary, fallback, plugin.getLogger());
    }

    private static IslandStorage createConfiguredStorage(Plugin plugin, FileConfiguration config, boolean fallback) {
        String backend = fallback ? fallbackBackendName(config) : backendName(config);
        String prefix = fallback ? "storage.fallback." : "storage.";
        String setupPrefix = fallback ? "setup.storage.fallback." : "setup.storage.";
        if ("S3".equals(backend)) {
            return new S3IslandStorage(
                URI.create(configString(config, setupPrefix + "endpoint", prefix + "endpoint", "http://minio.internal:9000")),
                configString(config, setupPrefix + "bucket", prefix + "bucket", "cloudislands"),
                configString(config, setupPrefix + "region", prefix + "region", "us-east-1"),
                envOrConfig("S3_ACCESS_KEY", configString(config, setupPrefix + "access-key", prefix + "access-key", "")),
                envOrConfig("S3_SECRET_KEY", configString(config, setupPrefix + "secret-key", prefix + "secret-key", "")),
                envOrConfig("S3_BEARER_TOKEN", configString(config, setupPrefix + "auth-token", prefix + "auth-token", ""))
            );
        }
        return new LocalIslandStorage(plugin.getDataFolder().toPath().resolve(configString(config, setupPrefix + "local-path", prefix + "local-path", fallback ? "islands-storage-fallback" : "islands-storage")));
    }

    public static String backendName(FileConfiguration config) {
        return normalizeBackend(configString(config, "setup.storage.type", "storage.type", "LOCAL"));
    }

    private static String fallbackBackendName(FileConfiguration config) {
        return normalizeBackend(configString(config, "setup.storage.fallback.type", "storage.fallback.type", "LOCAL"));
    }

    private static boolean fallbackEnabled(FileConfiguration config) {
        if (config.contains("setup.storage.fallback.enabled")) {
            return config.getBoolean("setup.storage.fallback.enabled");
        }
        if (config.contains("storage.fallback.enabled")) {
            return config.getBoolean("storage.fallback.enabled");
        }
        return "S3".equals(backendName(config));
    }

    private static String normalizeBackend(String type) {
        return "S3".equalsIgnoreCase(type == null ? "" : type.trim()) ? "S3" : "LOCAL";
    }

    private static String configString(FileConfiguration config, String setupPath, String legacyPath, String fallback) {
        String value = config.getString(setupPath, "");
        if (value == null || value.isBlank()) {
            value = config.getString(legacyPath, fallback);
        }
        return resolveConfigValue(value);
    }

    private static String envOrConfig(String envName, String configured) {
        String envValue = System.getenv(envName);
        if (envValue != null && !envValue.isBlank()) {
            return envValue;
        }
        return resolveConfigValue(configured);
    }

    private static String resolveConfigValue(String configured) {
        if (configured == null) {
            return "";
        }
        String trimmed = configured.trim();
        if (trimmed.startsWith("${") && trimmed.endsWith("}")) {
            return System.getenv().getOrDefault(trimmed.substring(2, trimmed.length() - 1), "");
        }
        return trimmed;
    }
}
