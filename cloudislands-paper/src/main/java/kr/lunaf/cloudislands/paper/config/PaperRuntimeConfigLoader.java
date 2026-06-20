package kr.lunaf.cloudislands.paper.config;

import java.time.Duration;
import java.util.EnumMap;
import java.util.HashMap;
import java.util.List;
import java.util.Locale;
import java.util.Map;
import java.util.function.Function;
import kr.lunaf.cloudislands.api.model.IslandPermission;
import kr.lunaf.cloudislands.paper.AgentRole;
import kr.lunaf.cloudislands.storage.StorageBackendPolicy;
import kr.lunaf.cloudislands.storage.snapshot.SnapshotRetentionPolicy;
import org.bukkit.configuration.ConfigurationSection;
import org.bukkit.configuration.file.FileConfiguration;

public final class PaperRuntimeConfigLoader {
    private static final String PRIMARY_STORAGE_TYPE_PATH = "setup.storage.type";
    private static final String FALLBACK_STORAGE_TYPE_PATH = "setup.storage.fallback.type";

    private PaperRuntimeConfigLoader() {
    }

    public static PaperRuntimeConfig load(FileConfiguration config, Function<String, String> envResolver) {
        Function<String, String> resolver = envResolver == null ? value -> value == null ? "" : value.trim() : envResolver;
        PaperRuntimeConfig.Node node = node(config);
        return new PaperRuntimeConfig(
            string(config, "plugin.service-name", "CloudIslands"),
            node,
            coreApi(config, resolver),
            redis(config, resolver),
            security(config, resolver),
            routing(config),
            protection(config),
            new PaperRuntimeConfig.Generator(string(config, "generators.default-key", "default")),
            messages(config),
            storage(config, resolver),
            worker(config),
            snapshots(config),
            new PaperRuntimeConfig.Health(config.getBoolean("health.enabled", false), string(config, "health.bind-host", "127.0.0.1"), config.getInt("health.port", 8787)),
            new PaperRuntimeConfig.Heartbeat(config.getLong("heartbeat.interval-ticks", 20L)),
            new PaperRuntimeConfig.Gui(booleanValue(config, "paper-gui.enabled", true), booleanValue(config, "paper-gui.island-node-enabled", true), booleanValue(config, "paper-gui.lobby-enabled", true))
        );
    }

    private static PaperRuntimeConfig.Messages messages(FileConfiguration config) {
        Map<String, String> translations = new HashMap<>();
        ConfigurationSection section = config.getConfigurationSection("messages.translations");
        if (section != null) {
            for (String key : section.getKeys(false)) {
                String value = section.getString(key);
                if (value != null) {
                    translations.put(key, value);
                }
            }
        }
        List<String> scoreboardLines = config.getStringList("messages.scoreboard-lines");
        return new PaperRuntimeConfig.Messages(
            string(config, "plugin.language", "ko_kr"),
            translations,
            scoreboardLines
        );
    }

    private static PaperRuntimeConfig.Storage storage(FileConfiguration config, Function<String, String> resolver) {
        PaperRuntimeConfig.StorageTarget primary = storageTarget(config, resolver, false);
        PaperRuntimeConfig.StorageTarget fallback = storageTarget(config, resolver, true);
        return new PaperRuntimeConfig.Storage(
            primary,
            fallbackEnabled(config, primary.backend()),
            fallback
        );
    }

    private static PaperRuntimeConfig.StorageTarget storageTarget(FileConfiguration config, Function<String, String> resolver, boolean fallback) {
        String prefix = fallback ? "storage.fallback." : "storage.";
        String setupPrefix = fallback ? "setup.storage.fallback." : "setup.storage.";
        String typePath = fallback ? FALLBACK_STORAGE_TYPE_PATH : PRIMARY_STORAGE_TYPE_PATH;
        return new PaperRuntimeConfig.StorageTarget(
            normalizeBackend(setupString(config, resolver, typePath, prefix + "type", fallback ? "LOCAL_FILESYSTEM" : "S3")),
            setupString(config, resolver, setupPrefix + "endpoint", prefix + "endpoint", "http://minio.internal:9000"),
            setupString(config, resolver, setupPrefix + "bucket", prefix + "bucket", "cloudislands"),
            setupString(config, resolver, setupPrefix + "region", prefix + "region", "us-east-1"),
            envOrConfig("S3_ACCESS_KEY", setupString(config, resolver, setupPrefix + "access-key", prefix + "access-key", "")),
            envOrConfig("S3_SECRET_KEY", setupString(config, resolver, setupPrefix + "secret-key", prefix + "secret-key", "")),
            envOrConfig("S3_BEARER_TOKEN", setupString(config, resolver, setupPrefix + "auth-token", prefix + "auth-token", "")),
            setupString(config, resolver, setupPrefix + "local-path", prefix + "local-path", fallback ? "islands-storage-fallback" : "islands-storage")
        );
    }

    private static boolean fallbackEnabled(FileConfiguration config, String primaryBackend) {
        if (config.contains("setup.storage.fallback.enabled")) {
            return config.getBoolean("setup.storage.fallback.enabled");
        }
        if (config.contains("storage.fallback.enabled")) {
            return config.getBoolean("storage.fallback.enabled");
        }
        return StorageBackendPolicy.sharedBackend(primaryBackend);
    }

    private static PaperRuntimeConfig.Node node(FileConfiguration config) {
        String nodeId = string(config, "node.id", "island-1");
        int maxActivationQueue = Math.max(1, config.getInt("node.max-activation-queue", config.getInt("island-node.activation.max-concurrent", 4)));
        Integer softPlayerCap = config.contains("node.soft-player-cap") ? config.getInt("node.soft-player-cap") : null;
        return new PaperRuntimeConfig.Node(
            nodeId,
            string(config, "node.pool", "island"),
            string(config, "node.velocity-server-name", nodeId),
            role(string(config, "node.role", "ISLAND_NODE")),
            booleanValue(config, "node.reject-default-identity", true),
            config.getStringList("node.supported-templates"),
            string(config, "node.supported-template", "*"),
            templateVersions(config),
            maxActivationQueue,
            config.getInt("node.hard-player-cap", 110),
            config.getInt("node.reserved-slots", 15),
            softPlayerCap,
            config.getInt("node.max-active-islands", 600)
        );
    }

    private static PaperRuntimeConfig.CoreApi coreApi(FileConfiguration config, Function<String, String> resolver) {
        String token = System.getenv("CI_CORE_TOKEN");
        if (token == null || token.isBlank()) {
            token = setupString(config, resolver, "setup.core-api.auth-token", "core-api.auth-token", "");
        }
        String adminToken = System.getenv("CI_ADMIN_TOKEN");
        if (adminToken == null || adminToken.isBlank()) {
            adminToken = setupString(config, resolver, "setup.core-api.admin-token", "core-api.admin-token", "");
        }
        long setupTimeout = config.getLong("setup.core-api.timeout-ms", 0L);
        long timeout = setupTimeout > 0L ? setupTimeout : config.getLong("core-api.timeout-ms", 3000L);
        return new PaperRuntimeConfig.CoreApi(
            setupString(config, resolver, "setup.core-api.base-url", "core-api.base-url", "https://core-api.internal:8443"),
            token,
            adminToken,
            Duration.ofMillis(Math.max(1L, timeout))
        );
    }

    private static PaperRuntimeConfig.Redis redis(FileConfiguration config, Function<String, String> resolver) {
        return new PaperRuntimeConfig.Redis(
            resolver.apply(string(config, "redis.uri", "redis://redis.internal:6379")),
            Duration.ofMillis(Math.max(1L, config.getLong("redis.timeout-ms", 1000L)))
        );
    }

    private static PaperRuntimeConfig.Security security(FileConfiguration config, Function<String, String> resolver) {
        return new PaperRuntimeConfig.Security(
            booleanValue(config, "security.allow-bungee-connect-plugin-messaging", false),
            booleanValue(config, "security.enforce-route-session", true),
            booleanValue(config, "routing.require-route-session", true),
            booleanValue(config, "security.require-velocity-forwarding", true),
            resolver.apply(string(config, "security.forwarding-secret", "")),
            config.getStringList("security.proxy-source-allowlist"),
            booleanValue(config, "security.require-proxy-source-allowlist", true)
        );
    }

    private static PaperRuntimeConfig.Routing routing(FileConfiguration config) {
        return new PaperRuntimeConfig.Routing(
            string(config, "routing.fallback-on-failure", "Lobby"),
            config.getInt("routing.wait-for-activation-timeout-seconds", 20)
        );
    }

    private static PaperRuntimeConfig.Protection protection(FileConfiguration config) {
        return new PaperRuntimeConfig.Protection(
            config.getLong("protection.deny-message-cooldown-ms", 1000L),
            config.getLong("protection.cache-event-poll-ticks", 100L),
            denyMessages(config)
        );
    }

    private static PaperRuntimeConfig.Worker worker(FileConfiguration config) {
        return new PaperRuntimeConfig.Worker(
            string(config, "island-node.shard-world-prefix", "ci_shard_"),
            config.getInt("island-node.shard-count", 16),
            config.getInt("island-node.cell-size", 1024),
            config.getInt("island-node.activation.preload-radius", 4),
            config.getInt("island-node.default-island-size", 300),
            config.getLong("island-node.activation.worker-interval-ticks", 20L),
            config.getLong("island-node.activation.periodic-save-seconds", 600L),
            config.getLong("island-node.activation.save-on-empty-after-seconds", 300L),
            config.getLong("island-node.level-scan-interval-seconds", 900L)
        );
    }

    private static SnapshotRetentionPolicy snapshots(FileConfiguration config) {
        return new SnapshotRetentionPolicy(
            config.getInt("snapshots.keep-hourly", 24),
            config.getInt("snapshots.keep-daily", 7),
            config.getInt("snapshots.keep-weekly", 4),
            config.getInt("snapshots.keep-manual", 50),
            booleanValue(config, "snapshots.compress", true),
            string(config, "snapshots.checksum", "SHA-256")
        ).normalized();
    }

    private static String setupString(FileConfiguration config, Function<String, String> resolver, String setupPath, String legacyPath, String fallback) {
        String value = string(config, setupPath, "");
        if (value.isBlank()) {
            value = string(config, legacyPath, fallback);
        }
        return resolver.apply(value);
    }

    private static String templateVersions(FileConfiguration config) {
        ConfigurationSection section = config.getConfigurationSection("node.template-versions");
        if (section == null) {
            return "";
        }
        java.util.List<String> values = new java.util.ArrayList<>();
        for (String key : section.getKeys(false)) {
            String version = section.getString(key, "");
            if (key == null || key.isBlank() || version == null || version.isBlank()) {
                continue;
            }
            values.add(safeMetadata(key) + ":" + safeMetadata(version));
        }
        return String.join(",", values);
    }

    private static String normalizeBackend(String type) {
        String normalized = StorageBackendPolicy.normalizeBackend(type);
        return StorageBackendPolicy.supportedBackend(normalized)
            ? normalized
            : StorageBackendPolicy.fallbackTarget(normalized);
    }

    private static String envOrConfig(String envName, String configured) {
        String envValue = System.getenv(envName);
        if (envValue != null && !envValue.isBlank()) {
            return envValue.trim();
        }
        return configured == null ? "" : configured.trim();
    }

    private static String safeMetadata(String value) {
        return value.trim().replace(',', '_').replace(';', '_').replace(':', '_').replace('=', '_');
    }

    private static Map<IslandPermission, String> denyMessages(FileConfiguration config) {
        Map<IslandPermission, String> messages = new EnumMap<>(IslandPermission.class);
        ConfigurationSection section = config.getConfigurationSection("protection.deny-messages");
        if (section == null) {
            return messages;
        }
        for (String key : section.getKeys(false)) {
            try {
                IslandPermission permission = IslandPermission.valueOf(key.toUpperCase(Locale.ROOT).replace('-', '_'));
                String message = section.getString(key, "");
                if (message != null && !message.isBlank()) {
                    messages.put(permission, message);
                }
            } catch (IllegalArgumentException ignored) {
                // Unknown keys remain a validation concern; runtime ignores them here.
            }
        }
        return messages;
    }

    private static AgentRole role(String configuredRole) {
        String normalized = configuredRole == null ? "" : configuredRole.trim().toUpperCase(Locale.ROOT).replace('-', '_');
        if (normalized.isBlank()) {
            return AgentRole.ISLAND_NODE;
        }
        try {
            return AgentRole.valueOf(normalized);
        } catch (IllegalArgumentException exception) {
            return AgentRole.ISLAND_NODE;
        }
    }

    private static String string(FileConfiguration config, String path, String fallback) {
        String value = config.getString(path, fallback);
        return value == null ? "" : value.trim();
    }

    private static boolean booleanValue(FileConfiguration config, String path, boolean fallback) {
        if (!config.contains(path)) {
            return fallback;
        }
        Object raw = config.get(path);
        if (raw instanceof Boolean value) {
            return value;
        }
        String normalized = String.valueOf(raw).trim().toLowerCase(Locale.ROOT);
        if (normalized.equals("true") || normalized.equals("yes") || normalized.equals("on") || normalized.equals("1") || normalized.equals("enable") || normalized.equals("enabled") || normalized.equals("켜기") || normalized.equals("허용") || normalized.equals("활성")) {
            return true;
        }
        if (normalized.equals("false") || normalized.equals("no") || normalized.equals("off") || normalized.equals("0") || normalized.equals("disable") || normalized.equals("disabled") || normalized.equals("끄기") || normalized.equals("거부") || normalized.equals("비활성")) {
            return false;
        }
        return fallback;
    }
}
