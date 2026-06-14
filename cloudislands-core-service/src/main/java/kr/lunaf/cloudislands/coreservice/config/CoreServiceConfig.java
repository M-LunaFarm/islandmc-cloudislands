package kr.lunaf.cloudislands.coreservice.config;

import java.io.BufferedReader;
import java.io.IOException;
import java.io.InputStream;
import java.io.InputStreamReader;
import java.net.URI;
import java.nio.charset.StandardCharsets;
import java.time.Duration;
import java.util.HashMap;
import java.util.Locale;
import java.util.Map;

public record CoreServiceConfig(
    String bind,
    int port,
    String repositoryMode,
    String jobQueueMode,
    String eventBusMode,
    String jdbcUrl,
    String configuredDatabaseType,
    String databaseUsername,
    String databasePassword,
    int databasePoolSize,
    URI redisUri,
    String storageType,
    URI storageEndpoint,
    String storageBucket,
    String storageLocalPath,
    String storageRegion,
    String storageAccessKey,
    String storageSecretKey,
    String storageBearerToken,
    String coreToken,
    String adminToken,
    String ipAllowlist,
    String upgradesFile,
    String blockValuesFile,
    String islandPool,
    String softFullPolicy,
    String hardFullPolicy,
    String migrationPolicy,
    boolean superiorSkyblock2MigrationEnabled,
    Duration routeTicketTtl,
    Duration routePreparingTicketTtl,
    Duration heartbeatTimeout,
    Duration leaseDuration,
    int snapshotKeepLatest,
    kr.lunaf.cloudislands.storage.snapshot.SnapshotRetentionPolicy snapshotRetentionPolicy,
    boolean adminApiEnabled,
    boolean requireMtls,
    String mtlsVerifiedHeader,
    String mtlsVerifiedValue,
    int rateLimitRequests,
    Duration rateLimitWindow
) {
    public static CoreServiceConfig fromEnvironment() {
        Map<String, String> config = applicationConfig();
        kr.lunaf.cloudislands.storage.snapshot.SnapshotRetentionPolicy snapshotRetentionPolicy = snapshotRetentionPolicy(config);
        int snapshotKeepLatest = integer("CI_SNAPSHOT_KEEP_LATEST", snapshotRetentionPolicy.retainedSnapshotCount());
        if (System.getenv("CI_SNAPSHOT_KEEP_LATEST") != null && !System.getenv("CI_SNAPSHOT_KEEP_LATEST").isBlank()) {
            snapshotRetentionPolicy = new kr.lunaf.cloudislands.storage.snapshot.SnapshotRetentionPolicy(snapshotKeepLatest, 0, 0, 0, snapshotRetentionPolicy.compress(), snapshotRetentionPolicy.checksumAlgorithm()).normalized();
        }
        return new CoreServiceConfig(
            env("CI_BIND", setting(config, "server.bind", "0.0.0.0")),
            integer("CI_PORT", configInteger(config, "server.port", 8443)),
            env("CI_REPOSITORY_MODE", setupRepositoryMode(config)),
            env("CI_JOB_QUEUE_MODE", setupSetting(config, "job-queue-mode", "REDIS")),
            env("CI_EVENT_BUS_MODE", setupSetting(config, "event-bus-mode", "REDIS")),
            env("CI_JDBC_URL", setupJdbcUrl(config, setting(config, "database.jdbc-url", "jdbc:postgresql://postgres.internal:5432/cloudislands"))),
            configuredDatabaseType(config),
            env("CI_DB_USERNAME", setupDatabaseSetting(config, "username", setting(config, "database.username", "cloudislands"))),
            env("CI_DB_PASSWORD", setupDatabaseSetting(config, "password", setting(config, "database.password", env("DB_PASSWORD", "")))),
            integer("CI_DB_POOL_SIZE", setupDatabaseInteger(config, "pool-size", configInteger(config, "database.pool-size", 20))),
            URI.create(env("CI_REDIS_URI", setupSetting(config, "redis-uri", setting(config, "redis.uri", "redis://redis.internal:6379")))),
            env("CI_STORAGE_TYPE", setupSetting(config, "storage-type", setting(config, "storage.type", "S3"))),
            URI.create(env("CI_STORAGE_ENDPOINT", setupSetting(config, "storage-endpoint", setting(config, "storage.endpoint", "http://minio.internal:9000")))),
            env("CI_STORAGE_BUCKET", setupSetting(config, "storage-bucket", setting(config, "storage.bucket", "cloudislands"))),
            env("CI_STORAGE_LOCAL_PATH", setupSetting(config, "storage-local-path", setting(config, "storage.local-path", "cloudislands-storage"))),
            env("CI_STORAGE_REGION", setting(config, "storage.region", "us-east-1")),
            env("CI_STORAGE_ACCESS_KEY", setting(config, "storage.access-key", env("S3_ACCESS_KEY", ""))),
            env("CI_STORAGE_SECRET_KEY", setting(config, "storage.secret-key", env("S3_SECRET_KEY", ""))),
            env("CI_STORAGE_BEARER_TOKEN", setting(config, "storage.auth-token", env("S3_BEARER_TOKEN", ""))),
            env("CI_CORE_TOKEN", setting(config, "security.core-token", "")),
            env("CI_ADMIN_TOKEN", setting(config, "security.admin-token", "")),
            env("CI_IP_ALLOWLIST", setting(config, "security.ip-allowlist", "")),
            env("CI_UPGRADES_FILE", setting(config, "upgrades.file", "")),
            env("CI_BLOCK_VALUES_FILE", setting(config, "block-values.file", "")),
            env("CI_ISLAND_POOL", setting(config, "routing.island-pool", "island")),
            env("CI_SOFT_FULL_POLICY", setting(config, "routing.soft-full-policy", "AVOID_NEW_ACTIVATIONS")),
            env("CI_HARD_FULL_POLICY", setting(config, "routing.hard-full-policy", "DENY_OR_QUEUE")),
            env("CI_MIGRATION_POLICY", setting(config, "routing.migration-policy", "INACTIVE_ONLY_AUTOMATIC")),
            bool("CI_SUPERIORSKYBLOCK2_MIGRATION_ENABLED", configBoolean(config, "migration.superiorskyblock2-enabled", configBoolean(config, "migration.enabled", true))),
            Duration.ofSeconds(integer("CI_ROUTE_TICKET_TTL_SECONDS", configInteger(config, "routing.route-ticket-ttl-seconds", 30))),
            Duration.ofSeconds(integer("CI_ROUTE_PREPARING_TICKET_TTL_SECONDS", configInteger(config, "routing.route-preparing-ticket-ttl-seconds", 120))),
            Duration.ofSeconds(integer("CI_HEARTBEAT_TIMEOUT_SECONDS", configInteger(config, "routing.heartbeat-timeout-seconds", 5))),
            Duration.ofSeconds(integer("CI_LEASE_SECONDS", configInteger(config, "routing.lease-duration-seconds", 30))),
            snapshotKeepLatest,
            snapshotRetentionPolicy,
            bool("CI_ADMIN_API_ENABLED", configBoolean(config, "security.admin-api-enabled", true)),
            bool("CI_REQUIRE_MTLS", configBoolean(config, "security.require-mtls", true)),
            env("CI_MTLS_VERIFIED_HEADER", setting(config, "security.mtls-verified-header", "X-SSL-Client-Verify")),
            env("CI_MTLS_VERIFIED_VALUE", setting(config, "security.mtls-verified-value", "SUCCESS")),
            integer("CI_RATE_LIMIT_REQUESTS", configInteger(config, "security.rate-limit-requests", 240)),
            Duration.ofSeconds(integer("CI_RATE_LIMIT_WINDOW_SECONDS", configInteger(config, "security.rate-limit-window-seconds", 60)))
        );
    }

    public boolean jdbcRepositories() {
        return "JDBC".equalsIgnoreCase(repositoryMode) && coreJdbcSupported(jdbcUrl);
    }

    public boolean redisJobs() {
        return "REDIS".equalsIgnoreCase(jobQueueMode);
    }

    public boolean jdbcJobs() {
        return "JDBC".equalsIgnoreCase(jobQueueMode) && coreJdbcSupported(jdbcUrl);
    }

    public boolean redisEvents() {
        return "REDIS".equalsIgnoreCase(eventBusMode);
    }

    public CoreServiceConfig withPort(int overridePort) {
        return new CoreServiceConfig(bind, overridePort, repositoryMode, jobQueueMode, eventBusMode, jdbcUrl, configuredDatabaseType, databaseUsername, databasePassword, databasePoolSize, redisUri, storageType, storageEndpoint, storageBucket, storageLocalPath, storageRegion, storageAccessKey, storageSecretKey, storageBearerToken, coreToken, adminToken, ipAllowlist, upgradesFile, blockValuesFile, islandPool, softFullPolicy, hardFullPolicy, migrationPolicy, superiorSkyblock2MigrationEnabled, routeTicketTtl, routePreparingTicketTtl, heartbeatTimeout, leaseDuration, snapshotKeepLatest, snapshotRetentionPolicy, adminApiEnabled, requireMtls, mtlsVerifiedHeader, mtlsVerifiedValue, rateLimitRequests, rateLimitWindow);
    }

    private static String env(String key, String fallback) {
        String value = System.getenv(key);
        return value == null || value.isBlank() ? fallback : value;
    }

    private static Map<String, String> applicationConfig() {
        try (InputStream stream = CoreServiceConfig.class.getClassLoader().getResourceAsStream("application.yaml")) {
            if (stream == null) {
                return Map.of();
            }
            Map<String, String> values = new HashMap<>();
            try (BufferedReader reader = new BufferedReader(new InputStreamReader(stream, StandardCharsets.UTF_8))) {
                String section = "";
                String rawLine;
                while ((rawLine = reader.readLine()) != null) {
                    String line = rawLine.strip();
                    if (line.isBlank() || line.startsWith("#")) {
                        continue;
                    }
                    if (!rawLine.startsWith(" ") && line.endsWith(":")) {
                        section = line.substring(0, line.length() - 1).strip();
                        continue;
                    }
                    int colon = line.indexOf(':');
                    if (colon <= 0 || section.isBlank()) {
                        continue;
                    }
                    String key = line.substring(0, colon).strip();
                    String value = resolveEnv(unquote(line.substring(colon + 1).strip()));
                    values.put(section + "." + key, value);
                }
            }
            return values;
        } catch (IOException exception) {
            return Map.of();
        }
    }

    private static String setting(Map<String, String> config, String key, String fallback) {
        String value = config.get(key);
        return value == null || value.isBlank() ? fallback : value;
    }

    private static String setupSetting(Map<String, String> config, String key, String fallback) {
        return setting(config, "setup." + key, fallback);
    }

    private static int setupInteger(Map<String, String> config, String key, int fallback) {
        String value = config.get("setup." + key);
        if (value == null || value.isBlank()) {
            return fallback;
        }
        try {
            int parsed = Integer.parseInt(value);
            return parsed <= 0 ? fallback : parsed;
        } catch (NumberFormatException exception) {
            return fallback;
        }
    }

    private static String setupDatabaseSetting(Map<String, String> config, String key, String fallback) {
        String nested = setting(config, "setup.database." + key, "");
        if (!nested.isBlank()) {
            return nested;
        }
        return setupSetting(config, "database-" + key, fallback);
    }

    private static int setupDatabaseInteger(Map<String, String> config, String key, int fallback) {
        String nested = config.get("setup.database." + key);
        if (nested != null && !nested.isBlank()) {
            try {
                int parsed = Integer.parseInt(nested);
                return parsed <= 0 ? fallback : parsed;
            } catch (NumberFormatException exception) {
                return fallback;
            }
        }
        return setupInteger(config, "database-" + key, fallback);
    }

    private static String setupJdbcUrl(Map<String, String> config, String fallback) {
        String type = configuredDatabaseType(config);
        String explicit = typedSetupDatabaseSetting(config, type, "jdbc-url", setupDatabaseSetting(config, "jdbc-url", setting(config, "setup.jdbc-url", "")));
        if (explicit.isBlank()) {
            explicit = typedSetupDatabaseSetting(config, type, "url", "");
        }
        if (!explicit.isBlank()) {
            return explicit;
        }
        if (!coreJdbcTypeSupported(type) && !type.equals("MYSQL") && !type.equals("MARIADB")) {
            return "";
        }
        String host = typedSetupDatabaseSetting(config, type, "host", setupDatabaseSetting(config, "host", ""));
        String database = typedSetupDatabaseSetting(config, type, "name", setupDatabaseSetting(config, "name", ""));
        if (database.isBlank()) {
            database = typedSetupDatabaseSetting(config, type, "database", setupDatabaseSetting(config, "database", ""));
        }
        if (type.isBlank() || host.isBlank() || database.isBlank()) {
            return coreJdbcTypeSupported(type) ? fallback : "";
        }
        String prefix = jdbcPrefix(type);
        if (prefix.isBlank()) {
            return "";
        }
        int port = typedSetupDatabaseInteger(config, type, "port", setupDatabaseInteger(config, "port", defaultDatabasePort(type)));
        String url = prefix + "://" + host.trim() + ":" + port + "/" + database.trim();
        String options = typedSetupDatabaseSetting(config, type, "options", setupDatabaseSetting(config, "options", ""));
        if (!options.isBlank()) {
            url += "?" + options.trim();
        }
        return url;
    }

    private static String typedSetupDatabaseSetting(Map<String, String> config, String type, String key, String fallback) {
        String scope = databaseSetupScope(type);
        if (scope.isBlank()) {
            return fallback;
        }
        String value = setting(config, "setup.database." + scope + "." + key, "");
        return value.isBlank() ? fallback : value;
    }

    private static int typedSetupDatabaseInteger(Map<String, String> config, String type, String key, int fallback) {
        String scope = databaseSetupScope(type);
        if (scope.isBlank()) {
            return fallback;
        }
        String value = config.get("setup.database." + scope + "." + key);
        if (value == null || value.isBlank()) {
            return fallback;
        }
        try {
            int parsed = Integer.parseInt(value);
            return parsed <= 0 ? fallback : parsed;
        } catch (NumberFormatException exception) {
            return fallback;
        }
    }

    private static String databaseSetupScope(String type) {
        return switch ((type == null ? "" : type).trim().replace('-', '_').toUpperCase(Locale.ROOT)) {
            case "POSTGRES", "POSTGRESQL" -> "postgresql";
            case "MYSQL" -> "mysql";
            case "MARIA", "MARIADB" -> "mariadb";
            default -> "";
        };
    }

    private static String jdbcPrefix(String type) {
        return switch (type.trim().replace('-', '_').toUpperCase(Locale.ROOT)) {
            case "POSTGRES", "POSTGRESQL" -> "jdbc:postgresql";
            case "MYSQL" -> "jdbc:mysql";
            case "MARIA", "MARIADB" -> "jdbc:mariadb";
            default -> "";
        };
    }

    private static int defaultDatabasePort(String type) {
        return switch (type.trim().replace('-', '_').toUpperCase(Locale.ROOT)) {
            case "MYSQL", "MARIA", "MARIADB" -> 3306;
            default -> 5432;
        };
    }

    private static String setupRepositoryMode(Map<String, String> config) {
        String explicit = setupSetting(config, "repository-mode", "");
        if (!explicit.isBlank()) {
            return explicit;
        }
        String databaseType = configuredDatabaseType(config);
        if (databaseType.isBlank()) {
            return "JDBC";
        }
        return coreJdbcTypeSupported(databaseType) ? "JDBC" : "IN_MEMORY";
    }

    private static String configuredDatabaseType(Map<String, String> config) {
        String envType = env("CI_DATABASE_TYPE", "");
        if (!envType.isBlank()) {
            return normalizeDatabaseType(envType);
        }
        String setupType = setupDatabaseSetting(config, "type", "");
        if (!setupType.isBlank()) {
            return normalizeDatabaseType(setupType);
        }
        String setupJdbcUrl = setupDatabaseSetting(config, "jdbc-url", setting(config, "setup.jdbc-url", ""));
        String jdbcUrl = env("CI_JDBC_URL", setupJdbcUrl.isBlank() ? setting(config, "database.jdbc-url", "") : setupJdbcUrl);
        return jdbcUrlDatabaseType(jdbcUrl);
    }

    private static String jdbcUrlDatabaseType(String jdbcUrl) {
        if (jdbcUrl == null || jdbcUrl.isBlank()) {
            return "UNKNOWN";
        }
        String value = jdbcUrl.toLowerCase(Locale.ROOT);
        if (value.startsWith("jdbc:mysql:")) {
            return "MYSQL";
        }
        if (value.startsWith("jdbc:mariadb:")) {
            return "MARIADB";
        }
        if (value.startsWith("jdbc:postgresql:")) {
            return "POSTGRESQL";
        }
        return "UNKNOWN";
    }

    private static String normalizeDatabaseType(String type) {
        return switch (type.trim().replace('-', '_').toUpperCase(Locale.ROOT)) {
            case "POSTGRES", "POSTGRESQL" -> "POSTGRESQL";
            case "MYSQL" -> "MYSQL";
            case "MARIA", "MARIADB" -> "MARIADB";
            case "CORE", "CORE_API" -> "CORE_API";
            default -> "UNKNOWN";
        };
    }

    private static boolean coreJdbcSupported(String jdbcUrl) {
        return jdbcUrl != null && jdbcUrl.toLowerCase(Locale.ROOT).startsWith("jdbc:postgresql:");
    }

    private static boolean coreJdbcTypeSupported(String type) {
        return switch (type.trim().replace('-', '_').toUpperCase(Locale.ROOT)) {
            case "POSTGRES", "POSTGRESQL" -> true;
            default -> false;
        };
    }

    private static kr.lunaf.cloudislands.storage.snapshot.SnapshotRetentionPolicy snapshotRetentionPolicy(Map<String, String> config) {
        boolean compress = configBoolean(config, "snapshots.compress", true);
        String checksum = setting(config, "snapshots.checksum", "SHA-256");
        if (config.containsKey("snapshots.keep-latest") && !config.getOrDefault("snapshots.keep-latest", "").isBlank()) {
            int keepLatest = configInteger(config, "snapshots.keep-latest", 85);
            return new kr.lunaf.cloudislands.storage.snapshot.SnapshotRetentionPolicy(keepLatest, 0, 0, 0, compress, checksum).normalized();
        }
        int hourly = configInteger(config, "snapshots.keep-hourly", 24);
        int daily = configInteger(config, "snapshots.keep-daily", 7);
        int weekly = configInteger(config, "snapshots.keep-weekly", 4);
        int manual = configInteger(config, "snapshots.keep-manual", 50);
        return new kr.lunaf.cloudislands.storage.snapshot.SnapshotRetentionPolicy(hourly, daily, weekly, manual, compress, checksum).normalized();
    }

    private static String unquote(String value) {
        if ((value.startsWith("\"") && value.endsWith("\"")) || (value.startsWith("'") && value.endsWith("'"))) {
            return value.substring(1, value.length() - 1);
        }
        return value;
    }

    private static String resolveEnv(String value) {
        if (value == null) {
            return "";
        }
        String trimmed = value.trim();
        if (trimmed.startsWith("${") && trimmed.endsWith("}")) {
            return System.getenv().getOrDefault(trimmed.substring(2, trimmed.length() - 1), "");
        }
        return trimmed;
    }

    private static int configInteger(Map<String, String> config, String key, int fallback) {
        try {
            String value = config.get(key);
            return value == null || value.isBlank() ? fallback : Integer.parseInt(value);
        } catch (NumberFormatException exception) {
            return fallback;
        }
    }

    private static boolean configBoolean(Map<String, String> config, String key, boolean fallback) {
        String value = config.get(key);
        return value == null || value.isBlank() ? fallback : parseBoolean(value, fallback);
    }

    private static int integer(String key, int fallback) {
        try {
            return Integer.parseInt(env(key, Integer.toString(fallback)));
        } catch (NumberFormatException ignored) {
            return fallback;
        }
    }

    private static boolean bool(String key, boolean fallback) {
        String value = env(key, Boolean.toString(fallback));
        return parseBoolean(value, fallback);
    }

    private static boolean parseBoolean(String value, boolean fallback) {
        String normalized = value.trim().toLowerCase(Locale.ROOT);
        if (normalized.equals("true") || normalized.equals("yes") || normalized.equals("on") || normalized.equals("1") || normalized.equals("enable") || normalized.equals("enabled") || normalized.equals("켜기") || normalized.equals("허용") || normalized.equals("활성")) {
            return true;
        }
        if (normalized.equals("false") || normalized.equals("no") || normalized.equals("off") || normalized.equals("0") || normalized.equals("disable") || normalized.equals("disabled") || normalized.equals("끄기") || normalized.equals("거부") || normalized.equals("비활성")) {
            return false;
        }
        return fallback;
    }
}
