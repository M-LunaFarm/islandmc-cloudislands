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
    boolean setupDatabaseAutoSchema,
    boolean setupDatabaseFallbackEnabled,
    String setupDatabaseFallbackOrder,
    boolean setupDatabaseFallbackRequireSharedBeforeLocal,
    boolean setupDatabaseFallbackLocalLast,
    String setupDatabaseFallbackProductionSafeOrder,
    String setupDatabaseCoreApiBaseUrl,
    boolean setupDatabaseCoreApiAuthTokenConfigured,
    boolean setupDatabaseCoreApiAdminTokenConfigured,
    int setupDatabaseCoreApiTimeoutMillis,
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
    String levelFormulaType,
    String levelFormulaExpression,
    String worthFormulaType,
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
            env("CI_DB_USERNAME", typedSetupDatabaseSetting(config, effectiveJdbcSettingsType(config), "username", setupDatabaseSetting(config, "username", setting(config, "database.username", "cloudislands")))),
            env("CI_DB_PASSWORD", typedSetupDatabaseSetting(config, effectiveJdbcSettingsType(config), "password", setupDatabaseSetting(config, "password", setting(config, "database.password", env("DB_PASSWORD", ""))))),
            integer("CI_DB_POOL_SIZE", typedSetupDatabaseInteger(config, effectiveJdbcSettingsType(config), "pool-size", setupDatabaseInteger(config, "pool-size", configInteger(config, "database.pool-size", 20)))),
            bool("CI_DB_AUTO_SCHEMA", configBoolean(config, "setup.database.auto-schema", configBoolean(config, "setup.database-auto-schema", false))),
            bool("CI_DB_FALLBACK_ENABLED", configBoolean(config, "setup.database.fallback.enabled", configBoolean(config, "setup.database-fallback-enabled", true))),
            env("CI_DB_FALLBACK_ORDER", setupDatabaseFallbackOrder(config)),
            bool("CI_DB_FALLBACK_REQUIRE_SHARED_BEFORE_LOCAL", configBoolean(config, "setup.database.fallback.require-shared-before-local", true)),
            bool("CI_DB_FALLBACK_LOCAL_LAST", configBoolean(config, "setup.database.fallback.local-fallback-last", true)),
            env("CI_DB_FALLBACK_PRODUCTION_SAFE_ORDER", setting(config, "setup.database.fallback.production-safe-order", setupDatabaseFallbackProductionSafeOrder(config))),
            env("CI_SETUP_CORE_API_BASE_URL", setupDatabaseCoreApiSetting(config, "base-url", setupDatabaseCoreApiSetting(config, "url", setting(config, "core-api.base-url", "")))),
            !env("CI_SETUP_CORE_API_AUTH_TOKEN", setupDatabaseCoreApiSetting(config, "auth-token", setting(config, "core-api.auth-token", env("CI_CORE_TOKEN", "")))).isBlank(),
            !env("CI_SETUP_CORE_API_ADMIN_TOKEN", setupDatabaseCoreApiSetting(config, "admin-token", setting(config, "core-api.admin-token", env("CI_ADMIN_TOKEN", "")))).isBlank(),
            integer("CI_SETUP_CORE_API_TIMEOUT_MS", setupDatabaseCoreApiInteger(config, "timeout-ms", configInteger(config, "core-api.timeout-ms", 3000))),
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
            env("CI_LEVEL_FORMULA_TYPE", setting(config, "block-values.level-formula.type", "EXPRESSION")),
            env("CI_LEVEL_FORMULA_EXPRESSION", setting(config, "block-values.level-formula.expression", "floor(total_level_points / 1000)")),
            env("CI_WORTH_FORMULA_TYPE", setting(config, "block-values.worth-formula.type", "SUM_BLOCK_VALUES")),
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

    public boolean setupDatabaseDurable() {
        return coreJdbcSupported(jdbcUrl);
    }

    public boolean setupDatabaseFallbackActive() {
        String requested = normalizeDatabaseType(configuredDatabaseType);
        String effective = jdbcUrlDatabaseType(jdbcUrl);
        if (coreJdbcSupported(jdbcUrl)) {
            return !requested.equals(effective) && !"UNKNOWN".equals(requested);
        }
        if ("CORE_API".equals(setupDatabaseFallbackTargetForUnsupported(requested))) {
            return true;
        }
        return !coreJdbcTypeSupported(requested);
    }

    public String setupDatabaseRequestedBackend() {
        return normalizeDatabaseType(configuredDatabaseType);
    }

    public String setupDatabaseEffectiveAuthority() {
        String requested = normalizeDatabaseType(configuredDatabaseType);
        String effective = jdbcUrlDatabaseType(jdbcUrl);
        if (coreJdbcSupported(jdbcUrl)) {
            if (!requested.equals(effective) && !"UNKNOWN".equals(requested)) {
                return effective + "_JDBC_FALLBACK_FOR_" + requested;
            }
            return effective + "_JDBC";
        }
        if ("CORE_API".equals(requested)) {
            return "CORE_API_CLIENT_MODE_NO_CORE_SELF_STORAGE";
        }
        String fallbackTarget = setupDatabaseFallbackTargetForUnsupported(requested);
        if ("CORE_API".equals(fallbackTarget)) {
            return "CORE_API_CLIENT_FALLBACK_FOR_" + requested;
        }
        return "SAFE_IN_MEMORY_CORE_FALLBACK";
    }

    public String setupDatabaseEffectiveBackend() {
        String requested = normalizeDatabaseType(configuredDatabaseType);
        if (coreJdbcSupported(jdbcUrl)) {
            return jdbcUrlDatabaseType(jdbcUrl) + "_JDBC";
        }
        if ("CORE_API".equals(requested) || "CORE_API".equals(setupDatabaseFallbackTargetForUnsupported(requested))) {
            return "CORE_API_CLIENT";
        }
        return "IN_MEMORY_FALLBACK";
    }

    public String setupDatabaseFallbackTarget() {
        String requested = normalizeDatabaseType(configuredDatabaseType);
        String effective = jdbcUrlDatabaseType(jdbcUrl);
        if (coreJdbcSupported(jdbcUrl) && !requested.equals(effective) && !"UNKNOWN".equals(requested)) {
            return effective;
        }
        if ("CORE_API".equals(requested)) {
            return "CORE_API";
        }
        if (!coreJdbcTypeSupported(requested) || "UNKNOWN".equals(effective)) {
            return setupDatabaseFallbackTargetForUnsupported(requested);
        }
        return "NONE";
    }

    public boolean setupDatabasePostgresqlFallbackConfigured() {
        return "POSTGRESQL".equals(setupDatabaseFallbackTarget()) || "POSTGRESQL".equals(jdbcUrlDatabaseType(jdbcUrl));
    }

    public boolean setupDatabaseCoreApiFallbackConfigured() {
        return setupDatabaseCoreApiFallbackConfigured(setupDatabaseRequestedBackend());
    }

    public String setupDatabaseFallbackReason() {
        String requested = normalizeDatabaseType(configuredDatabaseType);
        if (coreJdbcSupported(jdbcUrl)) {
            String effective = jdbcUrlDatabaseType(jdbcUrl);
            if (!requested.equals(effective) && !"UNKNOWN".equals(requested)) {
                return "requested-" + requested.toLowerCase(Locale.ROOT) + "-uses-" + effective.toLowerCase(Locale.ROOT) + "-core-jdbc-fallback";
            }
            return "native-" + effective.toLowerCase(Locale.ROOT) + "-core-jdbc";
        }
        if ("CORE_API".equals(requested)) {
            return "core-api-is-client-facing-selection-not-core-service-self-storage";
        }
        String fallbackTarget = setupDatabaseFallbackTargetForUnsupported(requested);
        if ("IN_MEMORY".equals(fallbackTarget) && !setupDatabaseFallbackEnabled) {
            return "database-fallback-disabled-for-" + requested.toLowerCase(Locale.ROOT) + "-setup";
        }
        if ("POSTGRESQL".equals(fallbackTarget) || "MYSQL".equals(fallbackTarget) || "MARIADB".equals(fallbackTarget)) {
            return "requested-" + requested.toLowerCase(Locale.ROOT) + "-uses-" + fallbackTarget.toLowerCase(Locale.ROOT) + "-setup-fallback";
        }
        if ("CORE_API".equals(fallbackTarget)) {
            return "requested-" + requested.toLowerCase(Locale.ROOT) + "-uses-core-api-client-fallback";
        }
        if ("UNKNOWN".equals(requested)) {
            return "missing-or-unknown-database-setup-using-in-memory-fallback";
        }
        return "unsupported-" + requested.toLowerCase(Locale.ROOT) + "-database-setup-using-in-memory-fallback";
    }

    public String setupDatabaseFallbackSummary() {
        return "requested=" + setupDatabaseRequestedBackend()
            + ",effective=" + setupDatabaseEffectiveBackend()
            + ",target=" + setupDatabaseFallbackTarget()
            + ",fallbackActive=" + setupDatabaseFallbackActive()
            + ",fallbackEnabled=" + setupDatabaseFallbackEnabled
            + ",safetyForced=" + setupDatabaseFallbackSafetyForced()
            + ",safetyMode=" + setupDatabaseFallbackSafetyMode()
            + ",order=" + setupDatabaseFallbackOrder
            + ",durable=" + setupDatabaseDurable()
            + ",coreApiReady=" + setupDatabaseCoreApiClientReady()
            + ",readiness=" + setupDatabaseFallbackReadiness()
            + ",reason=" + setupDatabaseFallbackReason();
    }

    public boolean setupDatabaseProductionDurable() {
        return jdbcRepositories() && setupDatabaseDurable();
    }

    public boolean setupDatabaseFallbackSafetyForced() {
        return setupDatabaseFallbackActive()
            && !setupDatabaseFallbackEnabled
            && ("IN_MEMORY".equals(setupDatabaseFallbackTarget()) || "IN_MEMORY_FALLBACK".equals(setupDatabaseEffectiveBackend()));
    }

    public String setupDatabaseFallbackSafetyMode() {
        if (!setupDatabaseFallbackActive()) {
            return "none";
        }
        if (setupDatabaseFallbackSafetyForced()) {
            return "fallback-disabled-but-safe-in-memory-forced";
        }
        if (setupDatabaseProductionDurable()) {
            return "durable-shared-backend";
        }
        if (setupDatabaseCoreApiClientReady()) {
            return "core-api-client-ready";
        }
        if ("IN_MEMORY".equals(setupDatabaseFallbackTarget()) || "IN_MEMORY_FALLBACK".equals(setupDatabaseEffectiveBackend())) {
            return "safe-in-memory-non-durable";
        }
        return "configured-fallback";
    }

    public boolean setupDatabaseCoreApiClientMode() {
        return "CORE_API".equals(setupDatabaseRequestedBackend()) || "CORE_API".equals(setupDatabaseFallbackTarget());
    }

    public boolean setupDatabaseCoreApiClientReady() {
        return setupDatabaseCoreApiClientMode()
            && setupDatabaseCoreApiBaseUrl != null
            && !setupDatabaseCoreApiBaseUrl.isBlank()
            && (setupDatabaseCoreApiAuthTokenConfigured || setupDatabaseCoreApiAdminTokenConfigured);
    }

    public String setupDatabaseFallbackReadiness() {
        if (setupDatabaseProductionDurable()) {
            return "production-durable";
        }
        if (setupDatabaseCoreApiClientReady()) {
            return "core-api-client-ready";
        }
        if (setupDatabaseCoreApiClientMode()) {
            return "core-api-client-missing-url-or-token";
        }
        if ("IN_MEMORY".equals(setupDatabaseFallbackTarget()) || "IN_MEMORY_FALLBACK".equals(setupDatabaseEffectiveBackend())) {
            return "safe-startup-non-durable";
        }
        return "unknown";
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
        return new CoreServiceConfig(bind, overridePort, repositoryMode, jobQueueMode, eventBusMode, jdbcUrl, configuredDatabaseType, databaseUsername, databasePassword, databasePoolSize, setupDatabaseAutoSchema, setupDatabaseFallbackEnabled, setupDatabaseFallbackOrder, setupDatabaseFallbackRequireSharedBeforeLocal, setupDatabaseFallbackLocalLast, setupDatabaseFallbackProductionSafeOrder, setupDatabaseCoreApiBaseUrl, setupDatabaseCoreApiAuthTokenConfigured, setupDatabaseCoreApiAdminTokenConfigured, setupDatabaseCoreApiTimeoutMillis, redisUri, storageType, storageEndpoint, storageBucket, storageLocalPath, storageRegion, storageAccessKey, storageSecretKey, storageBearerToken, coreToken, adminToken, ipAllowlist, upgradesFile, blockValuesFile, levelFormulaType, levelFormulaExpression, worthFormulaType, islandPool, softFullPolicy, hardFullPolicy, migrationPolicy, superiorSkyblock2MigrationEnabled, routeTicketTtl, routePreparingTicketTtl, heartbeatTimeout, leaseDuration, snapshotKeepLatest, snapshotRetentionPolicy, adminApiEnabled, requireMtls, mtlsVerifiedHeader, mtlsVerifiedValue, rateLimitRequests, rateLimitWindow);
    }

    public static String configuredDatabaseTypeSource() {
        Map<String, String> config = applicationConfig();
        if (presentEnv("CI_DATABASE_TYPE")) {
            return "CI_DATABASE_TYPE";
        }
        if (presentConfig(config, "setup.database.type")) {
            return "setup.database.type";
        }
        if (presentConfig(config, "setup.database-type")) {
            return "setup.database-type";
        }
        String coreApiSource = setupDatabaseCoreApiConfiguredSource(config);
        if (!coreApiSource.isBlank()) {
            return coreApiSource;
        }
        if (presentEnv("CI_JDBC_URL")) {
            return "CI_JDBC_URL";
        }
        if (presentConfig(config, "setup.database.jdbc-url")) {
            return "setup.database.jdbc-url";
        }
        if (presentConfig(config, "setup.jdbc-url")) {
            return "setup.jdbc-url";
        }
        String typedJdbc = typedSetupJdbcUrlSource(config);
        if (!typedJdbc.isBlank()) {
            return typedJdbc;
        }
        String typedHost = typedSetupHostDatabaseTypeSource(config);
        if (!typedHost.isBlank()) {
            return typedHost;
        }
        if (presentConfig(config, "database.jdbc-url")) {
            return "database.jdbc-url";
        }
        return "database.jdbc-url-default";
    }

    public static String configuredJdbcUrlSource() {
        Map<String, String> config = applicationConfig();
        if (presentEnv("CI_JDBC_URL")) {
            return "CI_JDBC_URL";
        }
        String type = configuredDatabaseType(config);
        String fallbackSource = setupDurableFallbackJdbcUrlSource(config);
        if ("CORE_API".equals(type)) {
            if (!fallbackSource.isBlank()) {
                return fallbackSource;
            }
            return "none:setup.database.core-api.enabled";
        }
        String typedSource = typedSetupDatabaseSettingSource(config, type, "jdbc-url");
        if (!typedSource.isBlank()) {
            if (!coreJdbcTypeSupported(type) && !fallbackSource.isBlank()) {
                return fallbackSource;
            }
            return typedSource;
        }
        if (presentConfig(config, "setup.database.jdbc-url")) {
            if (!coreJdbcTypeSupported(type) && !fallbackSource.isBlank()) {
                return fallbackSource;
            }
            return "setup.database.jdbc-url";
        }
        if (presentConfig(config, "setup.jdbc-url")) {
            if (!coreJdbcTypeSupported(type) && !fallbackSource.isBlank()) {
                return fallbackSource;
            }
            return "setup.jdbc-url";
        }
        typedSource = typedSetupDatabaseSettingSource(config, type, "url");
        if (!typedSource.isBlank()) {
            if (!coreJdbcTypeSupported(type) && !fallbackSource.isBlank()) {
                return fallbackSource;
            }
            return typedSource;
        }
        String hostSource = typedSetupHostDatabaseTypeSource(config);
        if (!hostSource.isBlank()) {
            if (!coreJdbcTypeSupported(type) && !fallbackSource.isBlank()) {
                return fallbackSource;
            }
            return hostSource;
        }
        if (!coreJdbcTypeSupported(type) && !fallbackSource.isBlank()) {
            return fallbackSource;
        }
        if (presentConfig(config, "database.jdbc-url")) {
            return "database.jdbc-url";
        }
        return "database.jdbc-url-default";
    }

    public static String configuredJdbcSettingsType() {
        return effectiveJdbcSettingsType(applicationConfig());
    }

    public static String configuredJdbcSettingsSource() {
        Map<String, String> config = applicationConfig();
        if (presentEnv("CI_DB_USERNAME") || presentEnv("CI_DB_PASSWORD") || presentEnv("CI_DB_POOL_SIZE")) {
            return "CI_DB_USERNAME/PASSWORD/POOL_SIZE";
        }
        String type = effectiveJdbcSettingsType(config);
        String scope = databaseSetupScope(type);
        if (!scope.isBlank()
            && (presentConfig(config, "setup.database." + scope + ".username")
            || presentConfig(config, "setup.database." + scope + ".password")
            || presentConfig(config, "setup.database." + scope + ".pool-size"))) {
            return "setup.database." + scope;
        }
        if (presentConfig(config, "setup.database.username")
            || presentConfig(config, "setup.database.password")
            || presentConfig(config, "setup.database.pool-size")) {
            return "setup.database";
        }
        if (presentConfig(config, "setup.database-username")
            || presentConfig(config, "setup.database-password")
            || presentConfig(config, "setup.database-pool-size")) {
            return "setup.database-*";
        }
        return "database";
    }

    private static String effectiveJdbcSettingsType(Map<String, String> config) {
        String configuredType = configuredDatabaseType(config);
        String jdbcUrl = env("CI_JDBC_URL", "");
        if (jdbcUrl.isBlank()) {
            jdbcUrl = setupJdbcUrl(config, setting(config, "database.jdbc-url", "jdbc:postgresql://postgres.internal:5432/cloudislands"));
        }
        String jdbcType = jdbcUrlDatabaseType(jdbcUrl);
        return coreJdbcTypeSupported(jdbcType) ? jdbcType : configuredType;
    }

    private static String env(String key, String fallback) {
        String value = System.getenv(key);
        return value == null || value.isBlank() ? fallback : value;
    }

    private static boolean presentEnv(String key) {
        String value = System.getenv(key);
        return value != null && !value.isBlank();
    }

    private static boolean presentConfig(Map<String, String> config, String key) {
        String value = config.get(key);
        return value != null && !value.isBlank();
    }

    private static Map<String, String> applicationConfig() {
        try (InputStream stream = CoreServiceConfig.class.getClassLoader().getResourceAsStream("application.yaml")) {
            if (stream == null) {
                return Map.of();
            }
            Map<String, String> values = new HashMap<>();
            try (BufferedReader reader = new BufferedReader(new InputStreamReader(stream, StandardCharsets.UTF_8))) {
                String[] path = new String[16];
                String rawLine;
                while ((rawLine = reader.readLine()) != null) {
                    String line = rawLine.strip();
                    if (line.isBlank() || line.startsWith("#")) {
                        continue;
                    }
                    if (line.startsWith("- ")) {
                        int level = Math.min(path.length - 1, leadingSpaces(rawLine) / 2);
                        String listPath = joinPath(path, Math.max(0, level - 1));
                        if (!listPath.isBlank()) {
                            String value = resolveEnv(unquote(line.substring(2).strip()));
                            if (!value.isBlank()) {
                                values.merge(listPath, value, (left, right) -> left + "," + right);
                            }
                        }
                        continue;
                    }
                    int colon = line.indexOf(':');
                    if (colon <= 0) {
                        continue;
                    }
                    int level = Math.min(path.length - 1, leadingSpaces(rawLine) / 2);
                    String key = line.substring(0, colon).strip();
                    String rawValue = line.substring(colon + 1).strip();
                    path[level] = key;
                    clearPath(path, level + 1);
                    if (rawValue.isBlank()) {
                        continue;
                    }
                    String value = resolveEnv(unquote(rawValue));
                    values.put(joinPath(path, level), value);
                }
            }
            return values;
        } catch (IOException exception) {
            return Map.of();
        }
    }

    private static int leadingSpaces(String value) {
        int count = 0;
        while (count < value.length() && value.charAt(count) == ' ') {
            count++;
        }
        return count;
    }

    private static void clearPath(String[] path, int from) {
        for (int index = from; index < path.length; index++) {
            path[index] = null;
        }
    }

    private static String joinPath(String[] path, int level) {
        StringBuilder builder = new StringBuilder();
        for (int index = 0; index <= level; index++) {
            if (path[index] == null || path[index].isBlank()) {
                continue;
            }
            if (builder.length() > 0) {
                builder.append('.');
            }
            builder.append(path[index]);
        }
        return builder.toString();
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

    private static String setupDatabaseCoreApiSetting(Map<String, String> config, String key, String fallback) {
        String nested = setting(config, "setup.database.core-api." + key, "");
        if (!nested.isBlank()) {
            return nested;
        }
        String setupCoreApi = setting(config, "setup.core-api." + key, "");
        if (!setupCoreApi.isBlank()) {
            return setupCoreApi;
        }
        String legacy = setting(config, "setup-core-api." + key, "");
        return legacy.isBlank() ? fallback : legacy;
    }

    private static boolean setupDatabaseCoreApiConfigured(Map<String, String> config) {
        return !setupDatabaseCoreApiConfiguredSource(config).isBlank();
    }

    private static String setupDatabaseCoreApiConfiguredSource(Map<String, String> config) {
        if (configBoolean(config, "setup.database.core-api.enabled", false)) {
            return "setup.database.core-api.enabled";
        }
        for (String prefix : java.util.List.of("setup.database.core-api", "setup.core-api", "setup-core-api", "core-api")) {
            for (String key : java.util.List.of("base-url", "url", "auth-token", "admin-token")) {
                String path = prefix + "." + key;
                if (presentConfig(config, path)) {
                    return path;
                }
            }
        }
        return "";
    }

    private static int setupDatabaseCoreApiInteger(Map<String, String> config, String key, int fallback) {
        String value = setupDatabaseCoreApiSetting(config, key, "");
        if (value.isBlank()) {
            return fallback;
        }
        try {
            int parsed = Integer.parseInt(value);
            return parsed <= 0 ? fallback : parsed;
        } catch (NumberFormatException exception) {
            return fallback;
        }
    }

    private static String setupDatabaseFallbackOrder(Map<String, String> config) {
        String nested = setting(config, "setup.database.fallback.order", "");
        if (!nested.isBlank()) {
            return normalizeSetupDatabaseFallbackOrder(config, nested);
        }
        String legacy = setting(config, "setup.database-fallback-order", "");
        if (!legacy.isBlank()) {
            return normalizeSetupDatabaseFallbackOrder(config, legacy);
        }
        return normalizeSetupDatabaseFallbackOrder(config, setupDatabaseFallbackProductionSafeOrder(config));
    }

    private static String setupDatabaseFallbackProductionSafeOrder(Map<String, String> config) {
        String configured = setting(config, "setup.database.fallback.production-safe-order", "");
        if (!configured.isBlank()) {
            return configured;
        }
        return "POSTGRESQL,MYSQL,MARIADB,CORE_API,UNSUPPORTED_JDBC";
    }

    private static String normalizeSetupDatabaseFallbackOrder(Map<String, String> config, String order) {
        boolean localLast = configBoolean(config, "setup.database.fallback.local-fallback-last", true);
        boolean sharedFirst = configBoolean(config, "setup.database.fallback.require-shared-before-local", true);
        if (!localLast && !sharedFirst) {
            return order;
        }
        java.util.List<String> shared = new java.util.ArrayList<>();
        java.util.List<String> local = new java.util.ArrayList<>();
        for (String entry : order.split(",")) {
            String normalized = normalizeDatabaseType(entry);
            if (normalized.isBlank()) {
                continue;
            }
            if ("IN_MEMORY".equals(normalized) || "UNSUPPORTED_JDBC".equals(normalized) || "SQLITE".equals(normalized)) {
                local.add(normalized);
            } else {
                shared.add(normalized);
            }
        }
        if (localLast || sharedFirst) {
            shared.addAll(local);
            return String.join(",", shared);
        }
        return order;
    }

    private static String setupJdbcUrl(Map<String, String> config, String fallback) {
        String type = configuredDatabaseType(config);
        String direct = setupJdbcUrlForType(config, type, fallback);
        if (coreJdbcSupported(direct) || coreJdbcTypeSupported(type)) {
            return direct;
        }
        String durableFallback = setupDurableFallbackJdbcUrl(config);
        return durableFallback.isBlank() ? direct : durableFallback;
    }

    private static String setupJdbcUrlForType(Map<String, String> config, String type, String fallback) {
        return setupJdbcUrlForType(config, type, fallback, true);
    }

    private static String setupJdbcUrlForType(Map<String, String> config, String type, String fallback, boolean allowGenericSetup) {
        String generic = allowGenericSetup ? setupDatabaseSetting(config, "jdbc-url", setting(config, "setup.jdbc-url", "")) : "";
        String explicit = typedSetupDatabaseSetting(config, type, "jdbc-url", generic);
        if (explicit.isBlank()) {
            explicit = typedSetupDatabaseSetting(config, type, "url", "");
        }
        if (!explicit.isBlank()) {
            return explicit;
        }
        if (!coreJdbcTypeSupported(type) && !type.equals("MYSQL") && !type.equals("MARIADB")) {
            return "";
        }
        String host = typedSetupDatabaseSetting(config, type, "host", allowGenericSetup ? setupDatabaseSetting(config, "host", "") : "");
        String database = typedSetupDatabaseSetting(config, type, "name", allowGenericSetup ? setupDatabaseSetting(config, "name", "") : "");
        if (database.isBlank()) {
            database = typedSetupDatabaseSetting(config, type, "database", allowGenericSetup ? setupDatabaseSetting(config, "database", "") : "");
        }
        if (type.isBlank() || host.isBlank() || database.isBlank()) {
            return coreJdbcTypeSupported(type) ? fallback : "";
        }
        String prefix = jdbcPrefix(type);
        if (prefix.isBlank()) {
            return "";
        }
        int port = typedSetupDatabaseInteger(config, type, "port", allowGenericSetup ? setupDatabaseInteger(config, "port", defaultDatabasePort(type)) : defaultDatabasePort(type));
        String url = prefix + "://" + host.trim() + ":" + port + "/" + database.trim();
        String options = typedSetupDatabaseSetting(config, type, "options", allowGenericSetup ? setupDatabaseSetting(config, "options", "") : "");
        if (!options.isBlank()) {
            url += "?" + options.trim();
        }
        return url;
    }

    private static String setupDurableFallbackJdbcUrl(Map<String, String> config) {
        if (!configBoolean(config, "setup.database.fallback.enabled", configBoolean(config, "setup.database-fallback-enabled", true))) {
            return "";
        }
        for (String entry : setupDatabaseFallbackOrder(config).split(",")) {
            String normalized = normalizeDatabaseType(entry);
            if (!coreJdbcTypeSupported(normalized) || !durableFallbackConfigured(config, normalized)) {
                continue;
            }
            String jdbcUrl = setupJdbcUrlForType(config, normalized, "", false);
            if (coreJdbcSupported(jdbcUrl)) {
                return jdbcUrl;
            }
        }
        return "";
    }

    private static String setupDurableFallbackJdbcUrlSource(Map<String, String> config) {
        if (!configBoolean(config, "setup.database.fallback.enabled", configBoolean(config, "setup.database-fallback-enabled", true))) {
            return "";
        }
        for (String entry : setupDatabaseFallbackOrder(config).split(",")) {
            String normalized = normalizeDatabaseType(entry);
            if (!coreJdbcTypeSupported(normalized) || !durableFallbackConfigured(config, normalized)) {
                continue;
            }
            String jdbcUrl = setupJdbcUrlForType(config, normalized, "", false);
            if (coreJdbcSupported(jdbcUrl)) {
                return "setup.database." + databaseSetupScope(normalized) + ".fallback";
            }
        }
        return "";
    }

    private String setupDatabaseFallbackTargetForUnsupported(String requested) {
        Map<String, String> config = applicationConfig();
        if (!setupDatabaseFallbackEnabled) {
            return "IN_MEMORY";
        }
        for (String entry : setupDatabaseFallbackOrder.split(",")) {
            String normalized = normalizeDatabaseType(entry);
            if (coreJdbcTypeSupported(normalized) && durableFallbackConfigured(config, normalized)) {
                String jdbcUrl = setupJdbcUrlForType(config, normalized, "", false);
                if (coreJdbcSupported(jdbcUrl)) {
                    return normalized;
                }
            }
            if ("CORE_API".equals(normalized) && setupDatabaseCoreApiFallbackConfigured(requested)) {
                return "CORE_API";
            }
            if ("IN_MEMORY".equals(normalized) || "UNSUPPORTED_JDBC".equals(normalized)) {
                return "IN_MEMORY";
            }
        }
        return "IN_MEMORY";
    }

    private boolean setupDatabaseCoreApiFallbackConfigured(String requested) {
        return "CORE_API".equals(requested)
            || !setupDatabaseCoreApiBaseUrl.isBlank()
            || setupDatabaseCoreApiAuthTokenConfigured
            || setupDatabaseCoreApiAdminTokenConfigured;
    }

    private static boolean durableFallbackConfigured(Map<String, String> config, String type) {
        String scope = databaseSetupScope(type);
        if (scope.isBlank()) {
            return false;
        }
        return presentConfig(config, "setup.database." + scope + ".jdbc-url")
            || presentConfig(config, "setup.database." + scope + ".url")
            || (presentConfig(config, "setup.database." + scope + ".host")
            && (presentConfig(config, "setup.database." + scope + ".name") || presentConfig(config, "setup.database." + scope + ".database")));
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
        if (coreJdbcTypeSupported(databaseType)) {
            return "JDBC";
        }
        return setupDurableFallbackJdbcUrl(config).isBlank() ? "IN_MEMORY" : "JDBC";
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
        if (setupDatabaseCoreApiConfigured(config)) {
            return "CORE_API";
        }
        String setupJdbcUrl = setupDatabaseSetting(config, "jdbc-url", setting(config, "setup.jdbc-url", ""));
        if (setupJdbcUrl.isBlank()) {
            setupJdbcUrl = typedSetupJdbcUrl(config);
        }
        String envJdbcUrl = env("CI_JDBC_URL", "");
        if (!envJdbcUrl.isBlank()) {
            return jdbcUrlDatabaseType(envJdbcUrl);
        }
        if (!setupJdbcUrl.isBlank()) {
            return jdbcUrlDatabaseType(setupJdbcUrl);
        }
        String typedHostType = typedSetupHostDatabaseType(config);
        if (!typedHostType.isBlank()) {
            return typedHostType;
        }
        return jdbcUrlDatabaseType(setting(config, "database.jdbc-url", ""));
    }

    private static String typedSetupJdbcUrl(Map<String, String> config) {
        for (String scope : java.util.List.of("postgresql", "mysql", "mariadb")) {
            String url = setting(config, "setup.database." + scope + ".jdbc-url", "");
            if (url.isBlank()) {
                url = setting(config, "setup.database." + scope + ".url", "");
            }
            if (!url.isBlank()) {
                return url;
            }
        }
        return "";
    }

    private static String typedSetupJdbcUrlSource(Map<String, String> config) {
        for (String scope : java.util.List.of("postgresql", "mysql", "mariadb")) {
            if (presentConfig(config, "setup.database." + scope + ".jdbc-url")) {
                return "setup.database." + scope + ".jdbc-url";
            }
            if (presentConfig(config, "setup.database." + scope + ".url")) {
                return "setup.database." + scope + ".url";
            }
        }
        return "";
    }

    private static String typedSetupDatabaseSettingSource(Map<String, String> config, String type, String key) {
        String scope = databaseSetupScope(type);
        if (scope.isBlank()) {
            return "";
        }
        String path = "setup.database." + scope + "." + key;
        return presentConfig(config, path) ? path : "";
    }

    private static String typedSetupHostDatabaseType(Map<String, String> config) {
        for (String scope : java.util.List.of("postgresql", "mysql", "mariadb")) {
            String host = setting(config, "setup.database." + scope + ".host", "");
            String name = setting(config, "setup.database." + scope + ".name", "");
            if (name.isBlank()) {
                name = setting(config, "setup.database." + scope + ".database", "");
            }
            if (!host.isBlank() && !name.isBlank()) {
                return switch (scope) {
                    case "postgresql" -> "POSTGRESQL";
                    case "mysql" -> "MYSQL";
                    case "mariadb" -> "MARIADB";
                    default -> "";
                };
            }
        }
        return "";
    }

    private static String typedSetupHostDatabaseTypeSource(Map<String, String> config) {
        for (String scope : java.util.List.of("postgresql", "mysql", "mariadb")) {
            boolean hasHost = presentConfig(config, "setup.database." + scope + ".host");
            boolean hasName = presentConfig(config, "setup.database." + scope + ".name") || presentConfig(config, "setup.database." + scope + ".database");
            if (hasHost && hasName) {
                return "setup.database." + scope + ".host/name";
            }
        }
        return "";
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
            case "POSTGRES", "POSTGRESQL", "PG" -> "POSTGRESQL";
            case "MYSQL" -> "MYSQL";
            case "MARIA", "MARIADB" -> "MARIADB";
            case "CORE", "CORE_API", "COREAPI", "CLOUDISLANDS", "CLOUDISLANDS_API" -> "CORE_API";
            case "IN_MEMORY", "MEMORY", "LOCAL", "LOCAL_SQLITE" -> "IN_MEMORY";
            case "UNSUPPORTED", "UNSUPPORTED_JDBC" -> "UNSUPPORTED_JDBC";
            default -> "UNKNOWN";
        };
    }

    private static boolean coreJdbcSupported(String jdbcUrl) {
        if (jdbcUrl == null) {
            return false;
        }
        String normalized = jdbcUrl.toLowerCase(Locale.ROOT);
        return normalized.startsWith("jdbc:postgresql:")
            || normalized.startsWith("jdbc:mysql:")
            || normalized.startsWith("jdbc:mariadb:");
    }

    private static boolean coreJdbcTypeSupported(String type) {
        return switch (type.trim().replace('-', '_').toUpperCase(Locale.ROOT)) {
            case "POSTGRES", "POSTGRESQL", "PG", "MYSQL", "MARIA", "MARIADB" -> true;
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
