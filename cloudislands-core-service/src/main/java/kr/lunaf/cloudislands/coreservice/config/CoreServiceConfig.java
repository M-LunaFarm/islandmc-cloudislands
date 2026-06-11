package kr.lunaf.cloudislands.coreservice.config;

import java.net.URI;
import java.time.Duration;

public record CoreServiceConfig(
    String bind,
    int port,
    String jdbcUrl,
    String databaseUsername,
    String databasePassword,
    URI redisUri,
    URI storageEndpoint,
    String storageBucket,
    String coreToken,
    Duration heartbeatTimeout,
    Duration leaseDuration
) {
    public static CoreServiceConfig fromEnvironment() {
        return new CoreServiceConfig(
            env("CI_BIND", "0.0.0.0"),
            integer("CI_PORT", 8443),
            env("CI_JDBC_URL", "jdbc:postgresql://postgres.internal:5432/cloudislands"),
            env("CI_DB_USERNAME", "cloudislands"),
            env("CI_DB_PASSWORD", ""),
            URI.create(env("CI_REDIS_URI", "redis://redis.internal:6379")),
            URI.create(env("CI_STORAGE_ENDPOINT", "http://minio.internal:9000")),
            env("CI_STORAGE_BUCKET", "cloudislands"),
            env("CI_CORE_TOKEN", ""),
            Duration.ofSeconds(integer("CI_HEARTBEAT_TIMEOUT_SECONDS", 5)),
            Duration.ofSeconds(integer("CI_LEASE_SECONDS", 30))
        );
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
}
