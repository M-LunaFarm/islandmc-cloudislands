package kr.lunaf.cloudislands.coreservice.config;

import java.net.URI;
import java.time.Duration;
import java.util.Locale;

public record CoreServiceConfig(
    String bind,
    int port,
    String repositoryMode,
    String jobQueueMode,
    String eventBusMode,
    String jdbcUrl,
    String databaseUsername,
    String databasePassword,
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
        return new CoreServiceConfig(
            env("CI_BIND", "0.0.0.0"),
            integer("CI_PORT", 8443),
            env("CI_REPOSITORY_MODE", "MEMORY"),
            env("CI_JOB_QUEUE_MODE", "MEMORY"),
            env("CI_EVENT_BUS_MODE", "MEMORY"),
            env("CI_JDBC_URL", "jdbc:postgresql://postgres.internal:5432/cloudislands"),
            env("CI_DB_USERNAME", "cloudislands"),
            env("CI_DB_PASSWORD", ""),
            URI.create(env("CI_REDIS_URI", "redis://redis.internal:6379")),
            env("CI_STORAGE_TYPE", "NONE"),
            URI.create(env("CI_STORAGE_ENDPOINT", "http://minio.internal:9000")),
            env("CI_STORAGE_BUCKET", "cloudislands"),
            env("CI_STORAGE_LOCAL_PATH", "cloudislands-storage"),
            env("CI_STORAGE_REGION", "us-east-1"),
            env("CI_STORAGE_ACCESS_KEY", env("S3_ACCESS_KEY", "")),
            env("CI_STORAGE_SECRET_KEY", env("S3_SECRET_KEY", "")),
            env("CI_STORAGE_BEARER_TOKEN", ""),
            env("CI_CORE_TOKEN", ""),
            env("CI_ADMIN_TOKEN", ""),
            env("CI_IP_ALLOWLIST", ""),
            env("CI_UPGRADES_FILE", ""),
            env("CI_BLOCK_VALUES_FILE", ""),
            env("CI_ISLAND_POOL", "island"),
            Duration.ofSeconds(integer("CI_ROUTE_TICKET_TTL_SECONDS", 30)),
            Duration.ofSeconds(integer("CI_ROUTE_PREPARING_TICKET_TTL_SECONDS", 120)),
            Duration.ofSeconds(integer("CI_HEARTBEAT_TIMEOUT_SECONDS", 5)),
            Duration.ofSeconds(integer("CI_LEASE_SECONDS", 30)),
            integer("CI_SNAPSHOT_KEEP_LATEST", 85),
            bool("CI_ADMIN_API_ENABLED", true),
            bool("CI_REQUIRE_MTLS", true),
            env("CI_MTLS_VERIFIED_HEADER", "X-SSL-Client-Verify"),
            env("CI_MTLS_VERIFIED_VALUE", "SUCCESS")
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
        return new CoreServiceConfig(bind, overridePort, repositoryMode, jobQueueMode, eventBusMode, jdbcUrl, databaseUsername, databasePassword, redisUri, storageType, storageEndpoint, storageBucket, storageLocalPath, storageRegion, storageAccessKey, storageSecretKey, storageBearerToken, coreToken, adminToken, ipAllowlist, upgradesFile, blockValuesFile, islandPool, routeTicketTtl, routePreparingTicketTtl, heartbeatTimeout, leaseDuration, snapshotKeepLatest, adminApiEnabled, requireMtls, mtlsVerifiedHeader, mtlsVerifiedValue);
    }

    private static String env(String key, String fallback) {
        String value = System.getenv(key);
        return value == null || value.isBlank() ? fallback : value;
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
