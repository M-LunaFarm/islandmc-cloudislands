package kr.lunaf.cloudislands.paper.config;

import java.time.Duration;
import java.util.EnumMap;
import java.util.Locale;
import java.util.Map;
import java.util.function.Function;
import kr.lunaf.cloudislands.api.model.IslandPermission;
import kr.lunaf.cloudislands.paper.AgentRole;
import kr.lunaf.cloudislands.storage.snapshot.SnapshotRetentionPolicy;
import org.bukkit.configuration.ConfigurationSection;
import org.bukkit.configuration.file.FileConfiguration;

public final class PaperRuntimeConfigLoader {
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
            worker(config),
            snapshots(config),
            new PaperRuntimeConfig.Health(config.getBoolean("health.enabled", false), string(config, "health.bind-host", "127.0.0.1"), config.getInt("health.port", 8787)),
            new PaperRuntimeConfig.Heartbeat(config.getLong("heartbeat.interval-ticks", 20L)),
            new PaperRuntimeConfig.Gui(booleanValue(config, "paper-gui.enabled", true), booleanValue(config, "paper-gui.island-node-enabled", true), booleanValue(config, "paper-gui.lobby-enabled", true))
        );
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
