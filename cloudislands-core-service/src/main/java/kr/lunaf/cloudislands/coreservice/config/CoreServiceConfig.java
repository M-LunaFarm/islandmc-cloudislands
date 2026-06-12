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
    Duration routeTicketTtl,
    Duration routePreparingTicketTtl,
    Duration heartbeatTimeout,
    Duration leaseDuration,
    int snapshotKeepLatest,
    boolean adminApiEnabled,
    boolean requireMtls,
    String mtlsVerifiedHeader,
    String mtlsVerifiedValue
) {
    public static CoreServiceConfig fromEnvironment() {
        Map<String, String> config = applicationConfig();
        return new CoreServiceConfig(
            env("CI_BIND", setting(config, "server.bind", "0.0.0.0")),
            integer("CI_PORT", configInteger(config, "server.port", 8443)),
            env("CI_REPOSITORY_MODE", "JDBC"),
            env("CI_JOB_QUEUE_MODE", "REDIS"),
            env("CI_EVENT_BUS_MODE", "REDIS"),
            env("CI_JDBC_URL", setting(config, "database.jdbc-url", "jdbc:postgresql://postgres.internal:5432/cloudislands")),
            env("CI_DB_USERNAME", setting(config, "database.username", "cloudislands")),
            env("CI_DB_PASSWORD", setting(config, "database.password", env("DB_PASSWORD", ""))),
            integer("CI_DB_POOL_SIZE", configInteger(config, "database.pool-size", 20)),
            URI.create(env("CI_REDIS_URI", setting(config, "redis.uri", "redis://redis.internal:6379"))),
            env("CI_STORAGE_TYPE", setting(config, "storage.type", "S3")),
            URI.create(env("CI_STORAGE_ENDPOINT", setting(config, "storage.endpoint", "http://minio.internal:9000"))),
            env("CI_STORAGE_BUCKET", setting(config, "storage.bucket", "cloudislands")),
            env("CI_STORAGE_LOCAL_PATH", setting(config, "storage.local-path", "cloudislands-storage")),
            env("CI_STORAGE_REGION", setting(config, "storage.region", "us-east-1")),
            env("CI_STORAGE_ACCESS_KEY", setting(config, "storage.access-key", env("S3_ACCESS_KEY", ""))),
            env("CI_STORAGE_SECRET_KEY", setting(config, "storage.secret-key", env("S3_SECRET_KEY", ""))),
            env("CI_STORAGE_BEARER_TOKEN", setting(config, "storage.auth-token", env("S3_BEARER_TOKEN", ""))),
            env("CI_CORE_TOKEN", setting(config, "security.core-token", "")),
            env("CI_ADMIN_TOKEN", setting(config, "security.admin-token", "")),
            env("CI_IP_ALLOWLIST", setting(config, "security.ip-allowlist", "")),
            env("CI_UPGRADES_FILE", setting(config, "upgrades.file", "")),
            env("CI_BLOCK_VALUES_FILE", setting(config, "block-values.file", "")),
            env("CI_ISLAND_POOL", "island"),
            env("CI_SOFT_FULL_POLICY", setting(config, "routing.soft-full-policy", "AVOID_NEW_ACTIVATIONS")),
            env("CI_HARD_FULL_POLICY", setting(config, "routing.hard-full-policy", "DENY_OR_QUEUE")),
            env("CI_MIGRATION_POLICY", setting(config, "routing.migration-policy", "INACTIVE_ONLY_AUTOMATIC")),
            Duration.ofSeconds(integer("CI_ROUTE_TICKET_TTL_SECONDS", configInteger(config, "routing.route-ticket-ttl-seconds", 30))),
            Duration.ofSeconds(integer("CI_ROUTE_PREPARING_TICKET_TTL_SECONDS", configInteger(config, "routing.route-preparing-ticket-ttl-seconds", 120))),
            Duration.ofSeconds(integer("CI_HEARTBEAT_TIMEOUT_SECONDS", configInteger(config, "routing.heartbeat-timeout-seconds", 5))),
            Duration.ofSeconds(integer("CI_LEASE_SECONDS", configInteger(config, "routing.lease-duration-seconds", 30))),
            integer("CI_SNAPSHOT_KEEP_LATEST", configInteger(config, "snapshots.keep-latest", 85)),
            bool("CI_ADMIN_API_ENABLED", configBoolean(config, "security.admin-api-enabled", true)),
            bool("CI_REQUIRE_MTLS", configBoolean(config, "security.require-mtls", true)),
            env("CI_MTLS_VERIFIED_HEADER", setting(config, "security.mtls-verified-header", "X-SSL-Client-Verify")),
            env("CI_MTLS_VERIFIED_VALUE", setting(config, "security.mtls-verified-value", "SUCCESS"))
        );
    }

    public boolean jdbcRepositories() {
        return "JDBC".equalsIgnoreCase(repositoryMode);
    }

    public boolean redisJobs() {
        return "REDIS".equalsIgnoreCase(jobQueueMode);
    }

    public boolean jdbcJobs() {
        return "JDBC".equalsIgnoreCase(jobQueueMode);
    }

    public boolean redisEvents() {
        return "REDIS".equalsIgnoreCase(eventBusMode);
    }

    public CoreServiceConfig withPort(int overridePort) {
        return new CoreServiceConfig(bind, overridePort, repositoryMode, jobQueueMode, eventBusMode, jdbcUrl, databaseUsername, databasePassword, databasePoolSize, redisUri, storageType, storageEndpoint, storageBucket, storageLocalPath, storageRegion, storageAccessKey, storageSecretKey, storageBearerToken, coreToken, adminToken, ipAllowlist, upgradesFile, blockValuesFile, islandPool, softFullPolicy, hardFullPolicy, migrationPolicy, routeTicketTtl, routePreparingTicketTtl, heartbeatTimeout, leaseDuration, snapshotKeepLatest, adminApiEnabled, requireMtls, mtlsVerifiedHeader, mtlsVerifiedValue);
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
