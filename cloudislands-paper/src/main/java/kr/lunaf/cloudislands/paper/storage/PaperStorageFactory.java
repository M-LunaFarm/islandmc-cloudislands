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
        String type = config.getString("storage.type", "LOCAL");
        if ("S3".equalsIgnoreCase(type)) {
            return new S3IslandStorage(
                URI.create(config.getString("storage.endpoint", "http://minio.internal:9000")),
                config.getString("storage.bucket", "cloudislands"),
                System.getenv().getOrDefault("S3_BEARER_TOKEN", config.getString("storage.auth-token", ""))
            );
        }
        return new LocalIslandStorage(plugin.getDataFolder().toPath().resolve(config.getString("storage.local-path", "islands-storage")));
    }
}
