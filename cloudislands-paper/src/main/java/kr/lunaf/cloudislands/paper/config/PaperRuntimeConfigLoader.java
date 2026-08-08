package kr.lunaf.cloudislands.paper.config;

import java.io.IOException;
import java.io.InputStream;
import java.io.UncheckedIOException;
import java.net.JarURLConnection;
import java.net.URISyntaxException;
import java.net.URL;
import java.nio.charset.StandardCharsets;
import java.nio.file.Files;
import java.nio.file.Path;
import java.nio.file.StandardCopyOption;
import java.time.Duration;
import java.util.ArrayList;
import java.util.EnumMap;
import java.util.HashMap;
import java.util.HashSet;
import java.util.List;
import java.util.Locale;
import java.util.Map;
import java.util.Set;
import java.util.TreeSet;
import java.util.function.Function;
import java.util.jar.JarFile;
import kr.lunaf.cloudislands.api.model.IslandPermission;
import kr.lunaf.cloudislands.common.config.ConfigIssue;
import kr.lunaf.cloudislands.common.config.ConfigSnapshot;
import kr.lunaf.cloudislands.common.config.ConfigSource;
import kr.lunaf.cloudislands.common.config.ConfigValidationResult;
import kr.lunaf.cloudislands.common.config.ConfigV2Loader;
import kr.lunaf.cloudislands.common.config.ConfigV2Validator;
import kr.lunaf.cloudislands.common.security.SecureSecretFile;
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

    private PaperRuntimeConfigLoader() {
    }

    public static PaperRuntimeConfig load(JavaPlugin plugin, Function<String, String> envResolver) {
        if (plugin == null) {
            return PaperRuntimeConfig.defaults();
        }
        saveBundledConfigV2Defaults(plugin);
        migrateSecurityDefaults(plugin);
        List<ConfigSource> sources = paperConfigV2Sources(plugin);
        if (sources.isEmpty()) {
            return loadV2(List.of(new ConfigSource("paper/config-v2/empty", 10, "")), envResolver, plugin.getDataFolder().toPath());
        }
        return loadV2(sources, envResolver, plugin.getDataFolder().toPath());
    }

    public static PaperRuntimeConfig loadV2(List<ConfigSource> sources, Function<String, String> envResolver) {
        return loadV2(sources, envResolver, null);
    }

    static PaperRuntimeConfig loadV2(List<ConfigSource> sources, Function<String, String> envResolver, Path dataFolder) {
        validateV2Sources(sources);
        YamlConfiguration mapped = mapV2Sources(sources, dataFolder);
        if (mapped.getKeys(true).isEmpty()) {
            return loadMappedConfig(new YamlConfiguration(), envResolver, null, dataFolder);
        }
        ConfigSnapshot snapshot = ConfigV2Loader.load(List.of(new ConfigSource("paper-config-v2-runtime", 10, mapped.saveToString())));
        requireValidSnapshot(snapshot);
        return loadMappedConfig(mapped, envResolver, snapshot, dataFolder);
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

    private static String configV2RelativeName(String sourceName) {
        String name = normalizeConfigV2ResourceName(sourceName);
        int marker = name.lastIndexOf("config-v2/");
        return marker < 0 ? name : name.substring(marker + "config-v2/".length());
    }

    private static void requireValidSnapshot(ConfigSnapshot snapshot) {
        if (snapshot != null && !snapshot.validation().valid()) {
            throw new IllegalArgumentException("Invalid Paper config-v2 runtime snapshot: " + snapshot.validation().summary());
        }
    }

    private static PaperRuntimeConfig loadMappedConfig(FileConfiguration config, Function<String, String> envResolver, ConfigSnapshot sourceConfig, Path dataFolder) {
        Function<String, String> resolver = envResolver == null ? value -> value == null ? "" : value.trim() : envResolver;
        FileConfiguration safeConfig = config == null ? new YamlConfiguration() : config;
        PaperRuntimeConfig.Node node = node(safeConfig);
        return new PaperRuntimeConfig(
            string(safeConfig, "plugin.service-name", "CloudIslands"),
            node,
            coreApi(safeConfig, resolver),
            redis(safeConfig, resolver),
            security(safeConfig, resolver, dataFolder),
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
        for (String file : configV2ResourceNames(plugin, dataRoot)) {
            String yaml = configV2Yaml(plugin, dataRoot, file);
            if (!yaml.isBlank()) {
                sources.add(new ConfigSource("paper/config-v2/" + file, 10 + sources.size(), yaml));
            }
        }
        return List.copyOf(sources);
    }

    private static void saveBundledConfigV2Defaults(JavaPlugin plugin) {
        Path dataRoot = plugin.getDataFolder().toPath().resolve("config-v2");
        for (String file : bundledConfigV2ResourceNames(plugin)) {
            Path target = dataRoot.resolve(file);
            if (Files.exists(target)) {
                continue;
            }
            try (InputStream input = plugin.getResource("config-v2/" + file)) {
                if (input == null) {
                    continue;
                }
                Files.createDirectories(target.getParent());
                Files.copy(input, target);
            } catch (IOException exception) {
                throw new UncheckedIOException("Failed to save bundled Paper config-v2 default " + file, exception);
            }
        }
    }

    private static void migrateSecurityDefaults(JavaPlugin plugin) {
        Path dataFolder = plugin.getDataFolder().toPath().toAbsolutePath().normalize();
        SecurityMigration migration = migrateSecurityConfig(dataFolder);
        if (!migration.changed()) {
            return;
        }
        if (migration.imported().isEmpty()) {
            plugin.getLogger().info("Migrated Docker-only Paper secret paths to cross-platform paths in " + migration.config());
        } else {
            plugin.getLogger().info("Moved " + migration.imported().size() + " plaintext Paper secret(s) into protected files: "
                + String.join(", ", migration.imported()));
        }
    }

    static SecurityMigration migrateSecurityConfig(Path dataFolder) {
        Path securityConfig = dataFolder.resolve("config-v2").resolve("security.yml");
        if (!Files.isRegularFile(securityConfig)) {
            return new SecurityMigration(securityConfig, false, List.of());
        }
        try {
            String original = Files.readString(securityConfig, StandardCharsets.UTF_8);
            String migrated = migrateLegacyDockerSecretReferences(original);
            YamlConfiguration yaml = yaml(migrated, securityConfig.toString());
            Map<String, String> secretFiles = Map.of(
                "core-api.auth-token", "core-token",
                "core-api.admin-token", "admin-token",
                "redis.password", "redis-password",
                "storage.access-key", "s3-access-key",
                "storage.secret-key", "s3-secret-key",
                "storage.bearer-token", "s3-bearer-token",
                "forwarding.secret", "forwarding-secret"
            );
            List<String> imported = new ArrayList<>();
            for (Map.Entry<String, String> entry : secretFiles.entrySet()) {
                String value = yaml.getString(entry.getKey(), "").trim();
                if (!plaintextSecret(value)) {
                    continue;
                }
                Path secretPath = SecureSecretFile.store(dataFolder.resolve("secrets").resolve(entry.getValue()), value);
                migrated = replaceYamlScalar(migrated, entry.getKey(), "${file:secrets/" + entry.getValue() + "}");
                imported.add(entry.getKey() + " -> " + secretPath);
            }
            if (!migrated.equals(original)) {
                Path temporary = Files.createTempFile(securityConfig.getParent(), "security.", ".yml.tmp");
                try {
                    Files.writeString(temporary, migrated, StandardCharsets.UTF_8);
                    try {
                        Files.move(temporary, securityConfig, StandardCopyOption.ATOMIC_MOVE, StandardCopyOption.REPLACE_EXISTING);
                    } catch (java.nio.file.AtomicMoveNotSupportedException ignored) {
                        Files.move(temporary, securityConfig, StandardCopyOption.REPLACE_EXISTING);
                    }
                } finally {
                    Files.deleteIfExists(temporary);
                }
            }
            return new SecurityMigration(securityConfig, !migrated.equals(original), List.copyOf(imported));
        } catch (IOException exception) {
            throw new UncheckedIOException("Failed to migrate Paper security config " + securityConfig, exception);
        }
    }

    private static String migrateLegacyDockerSecretReferences(String source) {
        String migrated = source;
        if (Files.notExists(Path.of("/run/secrets/cloudislands_core_token"))) {
            migrated = migrated.replace("${file:/run/secrets/cloudislands_core_token}", "${file:secrets/core-token}");
        }
        if (Files.notExists(Path.of("/run/secrets/cloudislands_admin_token"))) {
            migrated = migrated.replace("${file:/run/secrets/cloudislands_admin_token}", "${file:secrets/admin-token}");
        }
        if (Files.notExists(Path.of("/run/secrets/redis_password"))) {
            migrated = migrated.replace("${file:/run/secrets/redis_password}", "");
        }
        if (Files.notExists(Path.of("/run/secrets/s3_access_key"))) {
            migrated = migrated.replace("${file:/run/secrets/s3_access_key}", "");
        }
        if (Files.notExists(Path.of("/run/secrets/s3_secret_key"))) {
            migrated = migrated.replace("${file:/run/secrets/s3_secret_key}", "");
        }
        if (Files.notExists(Path.of("/run/secrets/s3_bearer_token"))) {
            migrated = migrated.replace("${file:/run/secrets/s3_bearer_token}", "");
        }
        return migrated;
    }

    private static boolean plaintextSecret(String value) {
        return value != null && !value.isBlank()
            && !value.startsWith("${env:")
            && !value.startsWith("${file:")
            && !value.startsWith("<");
    }

    private static String replaceYamlScalar(String source, String path, String replacement) {
        int separator = path.indexOf('.');
        String section = separator < 0 ? "" : path.substring(0, separator);
        String key = separator < 0 ? path : path.substring(separator + 1);
        String currentSection = "";
        List<String> output = new ArrayList<>();
        for (String line : source.split("\\R", -1)) {
            String trimmed = line.trim();
            int indent = leadingSpaces(line);
            if (indent == 0 && trimmed.endsWith(":")) {
                currentSection = trimmed.substring(0, trimmed.length() - 1).trim();
            }
            if (currentSection.equals(section) && indent > 0 && trimmed.startsWith(key + ":")) {
                output.add(line.substring(0, indent) + key + ": \"" + replacement + "\"");
            } else {
                output.add(line);
            }
        }
        return String.join(System.lineSeparator(), output);
    }

    private static int leadingSpaces(String value) {
        int spaces = 0;
        while (spaces < value.length() && value.charAt(spaces) == ' ') {
            spaces++;
        }
        return spaces;
    }

    record SecurityMigration(Path config, boolean changed, List<String> imported) {
    }

    private static Set<String> configV2ResourceNames(JavaPlugin plugin, Path dataRoot) {
        TreeSet<String> files = new TreeSet<>();
        files.addAll(dataConfigV2ResourceNames(dataRoot));
        files.addAll(bundledConfigV2ResourceNames(plugin));
        return files;
    }

    private static Set<String> dataConfigV2ResourceNames(Path dataRoot) {
        if (dataRoot == null || Files.notExists(dataRoot)) {
            return Set.of();
        }
        try (var paths = Files.walk(dataRoot)) {
            Set<String> files = new HashSet<>();
            paths
                .filter(Files::isRegularFile)
                .map(dataRoot::relativize)
                .map(Path::toString)
                .map(PaperRuntimeConfigLoader::normalizeConfigV2ResourceName)
                .filter(PaperRuntimeConfigLoader::configV2YamlResource)
                .forEach(files::add);
            return files;
        } catch (IOException exception) {
            throw new UncheckedIOException("Failed to scan Paper data config-v2 directory " + dataRoot, exception);
        }
    }

    private static Set<String> bundledConfigV2ResourceNames(JavaPlugin plugin) {
        if (plugin == null) {
            return Set.of();
        }
        Set<String> files = new HashSet<>();
        URL root = plugin.getClass().getClassLoader().getResource("config-v2");
        if (root != null) {
            files.addAll(bundledConfigV2ResourceNames(root));
        }
        if (files.isEmpty()) {
            files.addAll(codeSourceConfigV2ResourceNames(plugin));
        }
        return files;
    }

    private static Set<String> bundledConfigV2ResourceNames(URL root) {
        String protocol = root.getProtocol();
        if ("file".equals(protocol)) {
            try {
                return dataConfigV2ResourceNames(Path.of(root.toURI()));
            } catch (URISyntaxException exception) {
                throw new IllegalArgumentException("Invalid bundled config-v2 resource URL " + root, exception);
            }
        }
        if ("jar".equals(protocol)) {
            try {
                JarURLConnection connection = (JarURLConnection) root.openConnection();
                return jarConfigV2ResourceNames(connection.getJarFile());
            } catch (IOException exception) {
                throw new UncheckedIOException("Failed to scan bundled Paper config-v2 resources " + root, exception);
            }
        }
        return Set.of();
    }

    private static Set<String> codeSourceConfigV2ResourceNames(JavaPlugin plugin) {
        try {
            URL location = plugin.getClass().getProtectionDomain().getCodeSource().getLocation();
            Path path = Path.of(location.toURI());
            if (Files.isDirectory(path)) {
                return dataConfigV2ResourceNames(path.resolve("config-v2"));
            }
            if (Files.isRegularFile(path) && path.toString().endsWith(".jar")) {
                try (JarFile jar = new JarFile(path.toFile())) {
                    return jarConfigV2ResourceNames(jar);
                }
            }
            return Set.of();
        } catch (IOException exception) {
            throw new UncheckedIOException("Failed to scan Paper plugin jar config-v2 resources", exception);
        } catch (URISyntaxException exception) {
            throw new IllegalArgumentException("Invalid Paper plugin code source", exception);
        }
    }

    private static Set<String> jarConfigV2ResourceNames(JarFile jar) {
        Set<String> files = new HashSet<>();
        jar.stream()
            .filter(entry -> !entry.isDirectory())
            .map(entry -> entry.getName())
            .filter(name -> name.startsWith("config-v2/"))
            .map(name -> name.substring("config-v2/".length()))
            .map(PaperRuntimeConfigLoader::normalizeConfigV2ResourceName)
            .filter(PaperRuntimeConfigLoader::configV2YamlResource)
            .forEach(files::add);
        return files;
    }

    private static String normalizeConfigV2ResourceName(String value) {
        return value == null ? "" : value.replace('\\', '/');
    }

    private static boolean configV2YamlResource(String name) {
        return name != null && !name.isBlank() && (name.endsWith(".yml") || name.endsWith(".yaml"));
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

    private static YamlConfiguration mapV2Sources(List<ConfigSource> sources, Path dataFolder) {
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
                setIfPresent(yaml, mapped, "legacy-aliases.superiorskyblock2.enabled", "migration.legacy-aliases.superiorskyblock2.enabled");
            } else if (name.endsWith("ui/scoreboard.yml")) {
                setIfPresent(yaml, mapped, "lines", "messages.scoreboard-lines");
            } else if (name.contains("/ui/messages/")) {
                mapMessagesV2(yaml, mapped, name);
            }
        }
        applyAccessMode(mapped, dataFolder);
        return mapped;
    }

    private static void mapRootV2(FileConfiguration source, FileConfiguration target) {
        setIfPresent(source, target, "language", "plugin.language");
        setIfPresent(source, target, "configuration-mode", "access.mode");
        setIfPresent(source, target, "basic.topology", "access.topology");
        setIfPresent(source, target, "basic.node-id", "access.node-id");
        setIfPresent(source, target, "basic.velocity-server-name", "access.velocity-server-name");
        setIfPresent(source, target, "basic.core-url", "access.core-url");
        setIfPresent(source, target, "basic.redis-url", "access.redis-url");
        setIfPresent(source, target, "basic.local-storage-path", "access.local-storage-path");
        setIfPresent(source, target, "basic.trusted-proxies", "security.proxy-source-allowlist");
    }

    private static void applyAccessMode(FileConfiguration target, Path dataFolder) {
        if (!"BASIC".equalsIgnoreCase(string(target, "access.mode", "ADVANCED"))) {
            return;
        }
        String topology = effectiveBasicTopology(string(target, "access.topology", "AUTO"), target, dataFolder);
        String inferredIdentity = inferredNetworkIdentity(topology, target, dataFolder);
        String nodeId = inferredIdentity.isBlank()
            ? string(target, "access.node-id", topology.equals("LOBBY") ? "lobby-1" : "island-1")
            : inferredIdentity;
        target.set("node.id", nodeId);
        target.set("node.velocity-server-name", inferredIdentity.isBlank()
            ? string(target, "access.velocity-server-name", nodeId)
            : inferredIdentity);
        target.set("node.reject-default-identity", false);
        target.set("health.enabled", false);
        target.set("setup.core-api.base-url", string(target, "access.core-url", "http://127.0.0.1:8443"));
        String redisUrl = string(target, "access.redis-url", "");
        target.set("redis.enabled", !redisUrl.isBlank());
        if (!redisUrl.isBlank()) {
            target.set("redis.uri", redisUrl);
        }
        if (topology.equals("SINGLE_PAPER")) {
            target.set("node.role", "ISLAND_NODE");
            target.set("routing.direct-local-teleport", true);
            target.set("security.require-velocity-forwarding", false);
            target.set("security.enforce-route-session", false);
            target.set("routing.require-route-session", false);
            target.set("security.require-proxy-source-allowlist", false);
            target.set("setup.storage.type", "LOCAL_FILESYSTEM");
            target.set("setup.storage.local-path", string(target, "access.local-storage-path", "islands-storage"));
            return;
        }
        target.set("node.role", topology.equals("LOBBY") ? "LOBBY" : "ISLAND_NODE");
        if (topology.equals("LOBBY")) {
            target.set("setup.storage.type", "LOCAL_FILESYSTEM");
            target.set("setup.storage.local-path", string(target, "access.local-storage-path", "islands-storage"));
        }
        target.set("routing.direct-local-teleport", false);
        target.set("security.require-velocity-forwarding", true);
        target.set("security.enforce-route-session", true);
        target.set("routing.require-route-session", true);
        if (target.getStringList("security.proxy-source-allowlist").isEmpty()) {
            target.set("security.proxy-source-allowlist", List.of("127.0.0.1", "::1"));
        }
    }

    private static String effectiveBasicTopology(String configured, FileConfiguration target, Path dataFolder) {
        String normalized = configured == null ? "AUTO" : configured.trim().toUpperCase(Locale.ROOT).replace('-', '_');
        boolean legacyDefault = normalized.equals("SINGLE_PAPER")
            && string(target, "access.node-id", "island-1").equalsIgnoreCase("island-1")
            && string(target, "access.velocity-server-name", "island-1").equalsIgnoreCase("island-1");
        if (!normalized.equals("AUTO") && !legacyDefault) {
            return normalized;
        }
        String identity = string(target, "access.node-id", "") + " "
            + string(target, "access.velocity-server-name", "") + " " + serverDirectoryName(dataFolder);
        String lower = identity.toLowerCase(Locale.ROOT);
        if (lower.matches(".*\\b(lobby|hub|spawn)\\b.*")) {
            return "LOBBY";
        }
        return lower.matches(".*\\bislands?[-_ ]?\\d+\\b.*") ? "NETWORK_ISLAND" : "SINGLE_PAPER";
    }

    private static String inferredNetworkIdentity(String topology, FileConfiguration target, Path dataFolder) {
        if (topology.equals("SINGLE_PAPER")) {
            return "";
        }
        String nodeId = string(target, "access.node-id", "island-1");
        String velocityName = string(target, "access.velocity-server-name", "island-1");
        if (!nodeId.equalsIgnoreCase("island-1") || !velocityName.equalsIgnoreCase("island-1")) {
            return "";
        }
        String directory = serverDirectoryName(dataFolder);
        return directory.matches("[A-Za-z0-9._-]{1,64}") ? directory : "";
    }

    private static String serverDirectoryName(Path dataFolder) {
        if (dataFolder == null) {
            return "";
        }
        Path plugins = dataFolder.toAbsolutePath().normalize().getParent();
        Path server = plugins == null ? null : plugins.getParent();
        return server == null || server.getFileName() == null ? "" : server.getFileName().toString();
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
        setIfPresent(source, target, "core-api.base-url", "setup.core-api.base-url");
        if (source.contains("core-api.timeout.request")) {
            target.set("core-api.timeout-ms", durationMillis(source.getString("core-api.timeout.request", "3s")));
        }
        setIfPresent(source, target, "redis.enabled", "redis.enabled");
        setIfPresent(source, target, "redis.uri", "redis.uri");
        setIfPresent(source, target, "storage.type", "setup.storage.type");
        setIfPresent(source, target, "storage.endpoint", "setup.storage.endpoint");
        setIfPresent(source, target, "storage.bucket", "setup.storage.bucket");
        setIfPresent(source, target, "storage.region", "setup.storage.region");
        setIfPresent(source, target, "storage.local-path", "setup.storage.local-path");
        setIfPresent(source, target, "routing.direct-local-teleport", "routing.direct-local-teleport");
        setIfPresent(source, target, "routing.local-fallback-world", "routing.local-fallback-world");
    }

    private static void mapSecurityV2(FileConfiguration source, FileConfiguration target) {
        setIfPresent(source, target, "core-api.auth-token", "setup.core-api.auth-token");
        setIfPresent(source, target, "core-api.admin-token", "setup.core-api.admin-token");
        setIfPresent(source, target, "redis.password", "setup.redis.password");
        setIfPresent(source, target, "storage.access-key", "setup.storage.access-key");
        setIfPresent(source, target, "storage.secret-key", "setup.storage.secret-key");
        setIfPresent(source, target, "storage.bearer-token", "setup.storage.auth-token");
        setIfPresent(source, target, "forwarding.secret", "security.forwarding-secret");
        setIfPresent(source, target, "forwarding.required", "security.require-velocity-forwarding");
        setIfPresent(source, target, "route-session.enforce", "security.enforce-route-session");
        setIfPresent(source, target, "route-session.required", "routing.require-route-session");
        setIfPresent(source, target, "trusted-proxies", "security.proxy-source-allowlist");
        setIfPresent(source, target, "proxy-source-allowlist.required", "security.require-proxy-source-allowlist");
        setIfPresent(source, target, "admin-command-dispatch.enabled", "security.admin-command-dispatch.enabled");
    }

    private static void mapFeaturesV2(FileConfiguration source, FileConfiguration target) {
        setIfPresent(source, target, "cloudislands.gui", "paper-gui.enabled");
        setIfPresent(source, target, "cloudislands.migration", "migration.superiorskyblock2.enabled");
    }

    private static void mapGameplayV2(FileConfiguration source, FileConfiguration target) {
        setIfPresent(source, target, "island-node.shard-world-prefix", "island-node.shard-world-prefix");
        setIfPresent(source, target, "island-node.shard-count", "island-node.shard-count");
        setIfPresent(source, target, "island-node.cell-size", "island-node.cell-size");
        setIfPresent(source, target, "island-node.default-island-size", "island-node.default-island-size");
        setIfPresent(source, target, "island-node.activation.preload-radius", "island-node.activation.preload-radius");
        if (source.contains("island-node.activation.worker-interval")) {
            target.set("island-node.activation.worker-interval-ticks", durationTicks(source.getString("island-node.activation.worker-interval", "1s")));
        }
        if (source.contains("island-node.activation.periodic-save")) {
            target.set("island-node.activation.periodic-save-seconds", durationSeconds(source.getString("island-node.activation.periodic-save", "10m")));
        }
        if (source.contains("island-node.activation.save-on-empty-after")) {
            target.set("island-node.activation.save-on-empty-after-seconds", durationSeconds(source.getString("island-node.activation.save-on-empty-after", "5m")));
        }
        if (source.contains("island-node.activation.shutdown-save-timeout")) {
            target.set("island-node.activation.shutdown-save-timeout-seconds", durationSeconds(source.getString("island-node.activation.shutdown-save-timeout", "30s")));
        }
        if (source.contains("island-node.level-scan-interval")) {
            target.set("island-node.level-scan-interval-seconds", durationSeconds(source.getString("island-node.level-scan-interval", "15m")));
        }
        setIfPresent(source, target, "generator.default-profile", "generators.default-key");
        if (source.contains("snapshots.retention-count")) {
            target.set("snapshots.keep-manual", source.getInt("snapshots.retention-count", 50));
        }
    }

    private static void mapMessagesV2(FileConfiguration source, FileConfiguration target, String sourceName) {
        String locale = localeFromMessageSource(sourceName);
        String activeLocale = normalizeLocale(target.getString("plugin.language", "ko_kr"));
        if (!locale.equals(activeLocale)) {
            return;
        }
        for (String key : source.getKeys(true)) {
            if (source.isString(key)) {
                target.set("messages.translations." + key, source.getString(key));
            }
        }
    }

    private static String localeFromMessageSource(String sourceName) {
        String normalized = sourceName == null ? "" : sourceName.replace('\\', '/');
        int slash = normalized.lastIndexOf('/');
        String file = slash < 0 ? normalized : normalized.substring(slash + 1);
        int dot = file.lastIndexOf('.');
        return normalizeLocale(dot < 0 ? file : file.substring(0, dot));
    }

    private static String normalizeLocale(String value) {
        return value == null || value.isBlank() ? "ko_kr" : value.trim().toLowerCase(Locale.ROOT).replace('-', '_');
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
        boolean enabled = booleanValue(config, "migration.superiorskyblock2.enabled", false);
        if (config.contains("migration.superiorskyblock2-enabled")) {
            enabled = enabled && booleanValue(config, "migration.superiorskyblock2-enabled", false);
        }
        boolean legacyAliases = booleanValue(config, "migration.legacy-aliases.superiorskyblock2.enabled", false);
        return new PaperRuntimeConfig.Migration(enabled, legacyAliases);
    }

    private static PaperRuntimeConfig.Messages messages(FileConfiguration config) {
        Map<String, String> translations = new HashMap<>();
        ConfigurationSection section = config.getConfigurationSection("messages.translations");
        if (section != null) {
            for (String key : section.getKeys(true)) {
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
        String setupPrefix = fallback ? "setup.storage.fallback." : "setup.storage.";
        String typePath = fallback ? FALLBACK_STORAGE_TYPE_PATH : PRIMARY_STORAGE_TYPE_PATH;
        String backend = normalizeBackend(setupString(config, resolver, typePath, "LOCAL_FILESYSTEM"));
        boolean shared = StorageBackendPolicy.sharedBackend(backend);
        return new PaperRuntimeConfig.StorageTarget(
            backend,
            setupString(config, resolver, setupPrefix + "endpoint", "http://minio.internal:9000"),
            setupString(config, resolver, setupPrefix + "bucket", "cloudislands"),
            setupString(config, resolver, setupPrefix + "region", "us-east-1"),
            shared ? envOrConfig("S3_ACCESS_KEY", setupString(config, resolver, setupPrefix + "access-key", "")) : "",
            shared ? envOrConfig("S3_SECRET_KEY", setupString(config, resolver, setupPrefix + "secret-key", "")) : "",
            shared ? envOrConfig("S3_BEARER_TOKEN", setupString(config, resolver, setupPrefix + "auth-token", "")) : "",
            setupString(config, resolver, setupPrefix + "local-path", fallback ? "islands-storage-fallback" : "islands-storage")
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
            token = setupString(config, resolver, "setup.core-api.auth-token", "");
        }
        String adminToken = System.getenv("CI_ADMIN_TOKEN");
        if (adminToken == null || adminToken.isBlank()) {
            adminToken = setupString(config, resolver, "setup.core-api.admin-token", "");
        }
        long setupTimeout = config.getLong("setup.core-api.timeout-ms", 0L);
        long timeout = setupTimeout > 0L ? setupTimeout : config.getLong("core-api.timeout-ms", 3000L);
        return new PaperRuntimeConfig.CoreApi(
            setupString(config, resolver, "setup.core-api.base-url", "https://core-api.internal:8443"),
            token,
            adminToken,
            Duration.ofMillis(Math.max(1L, timeout))
        );
    }

    private static PaperRuntimeConfig.Redis redis(FileConfiguration config, Function<String, String> resolver) {
        boolean enabled = booleanValue(config, "redis.enabled", true);
        if (!enabled) {
            return new PaperRuntimeConfig.Redis("", "", Duration.ofMillis(Math.max(1L, config.getLong("redis.timeout-ms", 1000L))));
        }
        return new PaperRuntimeConfig.Redis(
            enabled ? resolver.apply(string(config, "redis.uri", "redis://redis.internal:6379")) : "",
            setupString(config, resolver, "setup.redis.password", ""),
            Duration.ofMillis(Math.max(1L, config.getLong("redis.timeout-ms", 1000L)))
        );
    }

    private static PaperRuntimeConfig.Security security(FileConfiguration config, Function<String, String> resolver, Path dataFolder) {
        String forwardingSecret = resolver.apply(string(config, "security.forwarding-secret", ""));
        if (forwardingSecret.isBlank()) {
            forwardingSecret = System.getenv().getOrDefault("VELOCITY_FORWARDING_SECRET", "").trim();
        }
        if (forwardingSecret.isBlank()) {
            forwardingSecret = nativePaperForwardingSecret(dataFolder);
        }
        return new PaperRuntimeConfig.Security(
            booleanValue(config, "security.allow-bungee-connect-plugin-messaging", false),
            booleanValue(config, "security.enforce-route-session", true),
            booleanValue(config, "routing.require-route-session", true),
            booleanValue(config, "security.require-velocity-forwarding", true),
            forwardingSecret,
            config.getStringList("security.proxy-source-allowlist"),
            booleanValue(config, "security.require-proxy-source-allowlist", true),
            booleanValue(config, "security.admin-command-dispatch.enabled", false)
        );
    }

    private static String nativePaperForwardingSecret(Path dataFolder) {
        if (dataFolder == null) {
            return "";
        }
        Path plugins = dataFolder.toAbsolutePath().normalize().getParent();
        Path serverRoot = plugins == null ? null : plugins.getParent();
        if (serverRoot == null) {
            return "";
        }
        Path paperGlobal = serverRoot.resolve("config").resolve("paper-global.yml");
        if (!Files.isRegularFile(paperGlobal)) {
            return "";
        }
        YamlConfiguration nativeConfig = YamlConfiguration.loadConfiguration(paperGlobal.toFile());
        return nativeConfig.getString("proxies.velocity.secret", "").trim();
    }

    private static PaperRuntimeConfig.Routing routing(FileConfiguration config) {
        return new PaperRuntimeConfig.Routing(
            string(config, "routing.fallback-on-failure", "Lobby"),
            config.getInt("routing.wait-for-activation-timeout-seconds", 20),
            booleanValue(config, "routing.hide-node-names", true),
            booleanValue(config, "routing.direct-local-teleport", false),
            string(config, "routing.local-fallback-world", "world")
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
            config.getLong("island-node.level-scan-interval-seconds", 900L),
            config.getLong("island-node.activation.shutdown-save-timeout-seconds", 30L)
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

    private static String setupString(FileConfiguration config, Function<String, String> resolver, String setupPath, String fallback) {
        return resolver.apply(string(config, setupPath, fallback));
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

    private static long durationSeconds(String configured) {
        long millis = durationMillis(configured);
        return Math.max(1L, Math.round(millis / 1000.0d));
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
