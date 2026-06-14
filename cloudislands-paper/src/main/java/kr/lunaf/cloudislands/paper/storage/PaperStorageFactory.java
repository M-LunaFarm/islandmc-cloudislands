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
        String backend = backendName(config);
        if ("S3".equals(backend)) {
            return new S3IslandStorage(
                URI.create(config.getString("storage.endpoint", "http://minio.internal:9000")),
                config.getString("storage.bucket", "cloudislands"),
                config.getString("storage.region", "us-east-1"),
                envOrConfig("S3_ACCESS_KEY", config.getString("storage.access-key", "")),
                envOrConfig("S3_SECRET_KEY", config.getString("storage.secret-key", "")),
                envOrConfig("S3_BEARER_TOKEN", config.getString("storage.auth-token", ""))
            );
        }
        return new LocalIslandStorage(plugin.getDataFolder().toPath().resolve(config.getString("storage.local-path", "islands-storage")));
    }

    public static String backendName(FileConfiguration config) {
        String type = config.getString("storage.type", "LOCAL");
        return "S3".equalsIgnoreCase(type) ? "S3" : "LOCAL";
    }

    private static String envOrConfig(String envName, String configured) {
        String envValue = System.getenv(envName);
        if (envValue != null && !envValue.isBlank()) {
            return envValue;
        }
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
