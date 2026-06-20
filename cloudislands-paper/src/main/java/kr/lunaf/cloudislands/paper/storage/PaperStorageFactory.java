package kr.lunaf.cloudislands.paper.storage;

import java.net.URI;
import kr.lunaf.cloudislands.paper.config.PaperRuntimeConfig;
import kr.lunaf.cloudislands.storage.IslandStorage;
import kr.lunaf.cloudislands.storage.LocalIslandStorage;
import kr.lunaf.cloudislands.storage.StorageBackendPolicy;
import kr.lunaf.cloudislands.storage.s3.S3IslandStorage;
import org.bukkit.plugin.Plugin;

public final class PaperStorageFactory {
    private PaperStorageFactory() {}

    public static IslandStorage create(Plugin plugin, PaperRuntimeConfig.Storage config) {
        IslandStorage primary = createConfiguredStorage(plugin, config.primary());
        if (!config.fallbackEnabled()) {
            return primary;
        }
        String primaryBackend = backendName(config);
        String fallbackBackend = backendName(config.fallback());
        if (primaryBackend.equals(fallbackBackend)) {
            return primary;
        }
        IslandStorage fallback = createConfiguredStorage(plugin, config.fallback());
        return new FallbackIslandStorage(primary, fallback, plugin.getLogger());
    }

    private static IslandStorage createConfiguredStorage(Plugin plugin, PaperRuntimeConfig.StorageTarget config) {
        String backend = backendName(config);
        if (StorageBackendPolicy.sharedBackend(backend)) {
            return new S3IslandStorage(
                URI.create(config.endpoint()),
                config.bucket(),
                config.region(),
                config.accessKey(),
                config.secretKey(),
                config.bearerToken()
            );
        }
        return new LocalIslandStorage(plugin.getDataFolder().toPath().resolve(config.localPath()));
    }

    public static String backendName(PaperRuntimeConfig.Storage config) {
        return backendName(config.primary());
    }

    private static String backendName(PaperRuntimeConfig.StorageTarget config) {
        return normalizeBackend(config.backend());
    }

    private static String normalizeBackend(String type) {
        String normalized = StorageBackendPolicy.normalizeBackend(type);
        return StorageBackendPolicy.supportedBackend(normalized)
            ? normalized
            : StorageBackendPolicy.fallbackTarget(normalized);
    }
}
