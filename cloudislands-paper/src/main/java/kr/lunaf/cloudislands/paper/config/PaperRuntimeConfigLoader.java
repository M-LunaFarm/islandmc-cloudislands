package kr.lunaf.cloudislands.paper.config;

import java.io.IOException;
import java.io.InputStream;
import java.io.UncheckedIOException;
import java.nio.charset.StandardCharsets;
import java.nio.file.Files;
import java.nio.file.Path;
import java.time.Duration;
import java.util.ArrayList;
import java.util.EnumMap;
import java.util.HashMap;
import java.util.List;
import java.util.Locale;
import java.util.Map;
import java.util.function.Function;
import kr.lunaf.cloudislands.api.model.IslandPermission;
import kr.lunaf.cloudislands.common.config.ConfigIssue;
import kr.lunaf.cloudislands.common.config.ConfigSnapshot;
import kr.lunaf.cloudislands.common.config.ConfigSource;
import kr.lunaf.cloudislands.common.config.ConfigValidationResult;
import kr.lunaf.cloudislands.common.config.ConfigV2Loader;
import kr.lunaf.cloudislands.common.config.ConfigV2Validator;
import kr.lunaf.cloudislands.paper.AgentRole;
import kr.lunaf.cloudislands.paper.gui.GuiActionSchema;
import kr.lunaf.cloudislands.storage.StorageBackendPolicy;
import kr.lunaf.cloudislands.storage.snapshot.SnapshotRetentionPolicy;
import org.bukkit.configuration.ConfigurationSection;
import org.bukkit.configuration.InvalidConfigurationException;
import org.bukkit.configuration.file.FileConfiguration;
import org.bukkit.configuration.file.YamlConfiguration;
import org.bukkit.plugin.java.JavaPlugin;

public final class PaperRuntimeConfigLoader {
    private static final String PRIMARY_STORAGE_TYPE_PATH = "setup.storage.type";
    private static final String FALLBACK_STORAGE_TYPE_PATH = "setup.storage.fallback.type";
    private static final List<String> PAPER_CONFIG_V2_FILES = List.of(
        "config.yml",
        "runtime.yml",
        "integrations.yml",
        "security.yml",
        "features.yml",
        "gameplay.yml",
        "migration.yml",
        "ui/scoreboard.yml",
        "ui/menus/admin-node.yml",
        "ui/menus/bank.yml",
        "ui/menus/bans.yml",
        "ui/menus/biome.yml",
        "ui/menus/chat.yml",
        "ui/menus/create.yml",
        "ui/menus/main.yml",
        "ui/menus/members.yml",
        "ui/menus/permissions.yml",
        "ui/menus/danger.yml",
        "ui/menus/flags.yml",
        "ui/menus/homes.yml",
        "ui/menus/info.yml",
        "ui/menus/invites.yml",
        "ui/menus/limits.yml",
        "ui/menus/logs.yml",
        "ui/menus/missions.yml",
        "ui/menus/my-islands.yml",
        "ui/menus/ranking.yml",
        "ui/menus/roles.yml",
        "ui/menus/settings.yml",
        "ui/menus/snapshots.yml",
        "ui/menus/upgrades.yml",
        "ui/menus/visit.yml",
        "ui/menus/warps.yml",
        "ui/messages/ko_kr.yml"
    );

    private PaperRuntimeConfigLoader() {
    }

    public static PaperRuntimeConfig load(JavaPlugin plugin, Function<String, String> envResolver) {
        if (plugin == null) {
            return PaperRuntimeConfig.defaults();
        }
        List<ConfigSource> sources = paperConfigV2Sources(plugin);
        if (sources.isEmpty()) {
            return loadV2(List.of(new ConfigSource("paper/config-v2/empty", 10, "")), envResolver);
        }
        return loadV2(sources, envResolver);
    }

    public static PaperRuntimeConfig load(FileConfiguration config, Function<String, String> envResolver) {
        return load(config, envResolver, null);
    }

    public static PaperRuntimeConfig loadV2(List<ConfigSource> sources, Function<String, String> envResolver) {
        validateV2Sources(sources);
        YamlConfiguration mapped = mapV2Sources(sources);
        if (mapped.getKeys(true).isEmpty()) {
            return load(new YamlConfiguration(), envResolver);
        }
        ConfigSnapshot snapshot = ConfigV2Loader.load(List.of(new ConfigSource("paper-config-v2-runtime", 10, mapped.saveToString())));
        requireValidSnapshot(snapshot);
        return load(mapped, envResolver, snapshot);
    }

    private static void validateV2Sources(List<ConfigSource> sources) {
        if (sources == null || sources.isEmpty()) {
            return;
        }
        List<ConfigIssue> issues = new ArrayList<>();
        for (ConfigSource source : sources) {
            if (source == null || source.yaml() == null || source.yaml().isBlank()) {
                continue;
            }
            issues.addAll(validateV2Source(source).issues());
        }
        ConfigValidationResult validation = new ConfigValidationResult(issues);
        if (!validation.valid()) {
            throw new IllegalArgumentException("Invalid Paper config-v2 sources: " + validation.summary());
        }
    }

    private static ConfigValidationResult validateV2Source(ConfigSource source) {
        if (source.name().contains("/ui/menus/")) {
            return ConfigV2Validator.validateMenuYaml(source.name(), source.yaml(), GuiActionSchema.registeredActionIds());
        }
        return ConfigV2Validator.validateYaml(source.name(), source.yaml());
    }

    private static void requireValidSnapshot(ConfigSnapshot snapshot) {
        if (snapshot != null && !snapshot.validation().valid()) {
            throw new IllegalArgumentException("Invalid Paper config-v2 runtime snapshot: " + snapshot.validation().summary());
        }
    }

    private static PaperRuntimeConfig load(FileConfiguration config, Function<String, String> envResolver, ConfigSnapshot sourceConfig) {
        Function<String, String> resolver = envResolver == null ? value -> value == null ? "" : value.trim() : envResolver;
        FileConfiguration safeConfig = config == null ? new YamlConfiguration() : config;
        PaperRuntimeConfig.Node node = node(safeConfig);
        return new PaperRuntimeConfig(
            string(safeConfig, "plugin.service-name", "CloudIslands"),
            node,
            coreApi(safeConfig, resolver),
            redis(safeConfig, resolver),
            security(safeConfig, resolver),
            routing(safeConfig),
            protection(safeConfig),
            new PaperRuntimeConfig.Generator(string(safeConfig, "generators.default-key", "default")),
            messages(safeConfig),
            storage(safeConfig, resolver),
            migration(safeConfig),
            worker(safeConfig),
            snapshots(safeConfig),
            new PaperRuntimeConfig.Health(safeConfig.getBoolean("health.enabled", false), string(safeConfig, "health.bind-host", "127.0.0.1"), safeConfig.getInt("health.port", 8787)),
            new PaperRuntimeConfig.Heartbeat(safeConfig.getLong("heartbeat.interval-ticks", 20L)),
            new PaperRuntimeConfig.Gui(booleanValue(safeConfig, "paper-gui.enabled", true), booleanValue(safeConfig, "paper-gui.island-node-enabled", true), booleanValue(safeConfig, "paper-gui.lobby-enabled", true)),
            sourceConfig
        );
    }

    private static List<ConfigSource> paperConfigV2Sources(JavaPlugin plugin) {
        List<ConfigSource> sources = new ArrayList<>();
        Path dataRoot = plugin.getDataFolder().toPath().resolve("config-v2");
        for (String file : PAPER_CONFIG_V2_FILES) {
            String yaml = configV2Yaml(plugin, dataRoot, file);
            if (!yaml.isBlank()) {
                sources.add(new ConfigSource("paper/config-v2/" + file, 10 + sources.size(), yaml));
            }
        }
        return List.copyOf(sources);
    }

    private static String configV2Yaml(JavaPlugin plugin, Path dataRoot, String file) {
        Path dataFile = dataRoot.resolve(file);
        if (Files.isRegularFile(dataFile)) {
            try {
                return Files.readString(dataFile, StandardCharsets.UTF_8);
            } catch (IOException exception) {
                throw new UncheckedIOException("Failed to read Paper config-v2 file " + dataFile, exception);
            }
        }
        try (InputStream input = plugin.getResource("config-v2/" + file)) {
            if (input == null) {
                return "";
            }
            return new String(input.readAllBytes(), StandardCharsets.UTF_8);
        } catch (IOException exception) {
            throw new UncheckedIOException("Failed to read bundled Paper config-v2 file " + file, exception);
        }
    }

    private static YamlConfiguration mapV2Sources(List<ConfigSource> sources) {
        YamlConfiguration mapped = new YamlConfiguration();
        if (sources == null || sources.isEmpty()) {
            return mapped;
        }
        for (ConfigSource source : sources) {
            YamlConfiguration yaml = yaml(source.yaml(), source.name());
            String name = source.name();
            if (name.endsWith("config.yml")) {
                mapRootV2(yaml, mapped);
            } else if (name.endsWith("runtime.yml")) {
                mapRuntimeV2(yaml, mapped);
            } else if (name.endsWith("integrations.yml")) {
                mapIntegrationsV2(yaml, mapped);
            } else if (name.endsWith("security.yml")) {
                mapSecurityV2(yaml, mapped);
            } else if (name.endsWith("features.yml")) {
                mapFeaturesV2(yaml, mapped);
            } else if (name.endsWith("gameplay.yml")) {
                mapGameplayV2(yaml, mapped);
            } else if (name.endsWith("migration.yml")) {
                setIfPresent(yaml, mapped, "superiorskyblock2.enabled", "migration.superiorskyblock2.enabled");
            } else if (name.endsWith("ui/scoreboard.yml")) {
                setIfPresent(yaml, mapped, "lines", "messages.scoreboard-lines");
            }
        }
        return mapped;
    }

    private static void mapRootV2(FileConfiguration source, FileConfiguration target) {
        setIfPresent(source, target, "language", "plugin.language");
    }

    private static void mapRuntimeV2(FileConfiguration source, FileConfiguration target) {
        setIfPresent(source, target, "node.id", "node.id");
        setIfPresent(source, target, "node.role", "node.role");
        setIfPresent(source, target, "node.pool", "node.pool");
        setIfPresent(source, target, "node.velocity-server-name", "node.velocity-server-name");
        setIfPresent(source, target, "node.reject-default-identity", "node.reject-default-identity");
        setIfPresent(source, target, "node.supported-templates", "node.supported-templates");
        setIfPresent(source, target, "capacity.max-active-islands", "node.max-active-islands");
        setIfPresent(source, target, "capacity.max-activation-queue", "node.max-activation-queue");
        setIfPresent(source, target, "capacity.soft-player-limit", "node.soft-player-cap");
        setIfPresent(source, target, "capacity.hard-player-limit", "node.hard-player-cap");
        setIfPresent(source, target, "health.enabled", "health.enabled");
        setIfPresent(source, target, "health.bind-host", "health.bind-host");
        setIfPresent(source, target, "health.port", "health.port");
        if (source.contains("heartbeat.interval")) {
            target.set("heartbeat.interval-ticks", durationTicks(source.getString("heartbeat.interval", "1s")));
        }
    }

    private static void mapIntegrationsV2(FileConfiguration source, FileConfiguration target) {
        setIfPresent(source, target, "core-api.base-url", "core-api.base-url");
        if (source.contains("core-api.timeout.request")) {
            target.set("core-api.timeout-ms", durationMillis(source.getString("core-api.timeout.request", "3s")));
        }
        setIfPresent(source, target, "redis.uri", "redis.uri");
        setIfPresent(source, target, "storage.type", "storage.type");
        setIfPresent(source, target, "storage.endpoint", "storage.endpoint");
        setIfPresent(source, target, "storage.bucket", "storage.bucket");
        setIfPresent(source, target, "storage.region", "storage.region");
    }

    private static void mapSecurityV2(FileConfiguration source, FileConfiguration target) {
        setIfPresent(source, target, "core-api.auth-token", "core-api.auth-token");
        setIfPresent(source, target, "core-api.admin-token", "core-api.admin-token");
        setIfPresent(source, target, "storage.access-key", "storage.access-key");
        setIfPresent(source, target, "storage.secret-key", "storage.secret-key");
        setIfPresent(source, target, "storage.bearer-token", "storage.auth-token");
        setIfPresent(source, target, "forwarding.secret", "security.forwarding-secret");
        setIfPresent(source, target, "forwarding.required", "security.require-velocity-forwarding");
        setIfPresent(source, target, "route-session.enforce", "security.enforce-route-session");
        setIfPresent(source, target, "route-session.required", "routing.require-route-session");
        setIfPresent(source, target, "trusted-proxies", "security.proxy-source-allowlist");
        setIfPresent(source, target, "proxy-source-allowlist.required", "security.require-proxy-source-allowlist");
    }

    private static void mapFeaturesV2(FileConfiguration source, FileConfiguration target) {
        setIfPresent(source, target, "cloudislands.gui", "paper-gui.enabled");
        setIfPresent(source, target, "cloudislands.migration", "migration.superiorskyblock2.enabled");
    }

    private static void mapGameplayV2(FileConfiguration source, FileConfiguration target) {
        setIfPresent(source, target, "generator.default-profile", "generators.default-key");
        if (source.contains("snapshots.retention-count")) {
            target.set("snapshots.keep-manual", source.getInt("snapshots.retention-count", 50));
        }
    }

    private static YamlConfiguration yaml(String value, String sourceName) {
        YamlConfiguration yaml = new YamlConfiguration();
        try {
            yaml.loadFromString(value == null ? "" : value);
        } catch (InvalidConfigurationException exception) {
            throw new IllegalArgumentException("Invalid Paper config-v2 yaml " + sourceName, exception);
        }
        return yaml;
    }

    private static void setIfPresent(FileConfiguration source, FileConfiguration target, String sourcePath, String targetPath) {
        if (source.contains(sourcePath)) {
            target.set(targetPath, source.get(sourcePath));
        }
    }

    private static PaperRuntimeConfig.Migration migration(FileConfiguration config) {
        boolean enabled = booleanValue(config, "migration.superiorskyblock2.enabled", true);
        if (config.contains("migration.superiorskyblock2-enabled")) {
            enabled = enabled && booleanValue(config, "migration.superiorskyblock2-enabled", true);
        }
        return new PaperRuntimeConfig.Migration(enabled);
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
            config.getInt("routing.wait-for-activation-timeout-seconds", 20),
            booleanValue(config, "routing.hide-node-names", true)
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

    private static long durationTicks(String configured) {
        long millis = durationMillis(configured);
        return Math.max(1L, Math.round(millis / 50.0d));
    }

    private static long durationMillis(String configured) {
        String value = configured == null ? "" : configured.trim().toLowerCase(Locale.ROOT);
        if (value.isBlank()) {
            return 1L;
        }
        try {
            if (value.endsWith("ms")) {
                return Math.max(1L, Long.parseLong(value.substring(0, value.length() - 2).trim()));
            }
            if (value.endsWith("s")) {
                return Math.max(1L, Long.parseLong(value.substring(0, value.length() - 1).trim()) * 1000L);
            }
            if (value.endsWith("m")) {
                return Math.max(1L, Long.parseLong(value.substring(0, value.length() - 1).trim()) * 60_000L);
            }
            return Math.max(1L, Long.parseLong(value));
        } catch (NumberFormatException exception) {
            return 1L;
        }
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
