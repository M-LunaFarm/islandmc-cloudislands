package kr.lunaf.cloudislands.coreservice.config;

import kr.lunaf.cloudislands.storage.snapshot.SnapshotRetentionPolicy;
import org.junit.jupiter.api.Test;

import java.net.URI;
import java.time.Duration;
import java.util.List;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertThrows;
import static org.junit.jupiter.api.Assertions.assertTrue;

class CoreServiceConfigTest {
    @Test
    void mysqlSetupUsesNativeCoreJdbcAuthority() {
        CoreServiceConfig config = config("JDBC", "jdbc:mysql://mysql.internal:3306/cloudislands", "MYSQL", true);

        assertTrue(config.jdbcRepositories());
        assertTrue(config.setupDatabaseDurable());
        assertTrue(config.setupDatabaseProductionDurable());
        assertFalse(config.setupDatabaseFallbackActive());
        assertEquals("MYSQL", config.setupDatabaseRequestedBackend());
        assertEquals("MYSQL_JDBC", config.setupDatabaseEffectiveBackend());
        assertEquals("MYSQL_JDBC", config.setupDatabaseEffectiveAuthority());
        assertEquals("NONE", config.setupDatabaseFallbackTarget());
        assertEquals("native-mysql-core-jdbc", config.setupDatabaseFallbackReason());
        assertFalse(config.setupDatabaseCoreApiClientMode());
        assertFalse(config.setupDatabaseCoreApiClientReady());
        assertEquals("production-durable", config.setupDatabaseFallbackReadiness());
        assertFalse(config.setupDatabaseFallbackSafetyForced());
        assertEquals("none", config.setupDatabaseFallbackSafetyMode());
        assertTrue(config.setupDatabaseFallbackSummary().contains("readiness=production-durable"));
        assertTrue(config.setupDatabaseFallbackSummary().contains("safetyForced=false"));
    }

    @Test
    void postgresqlSetupUsesNativeCoreJdbcAuthority() {
        CoreServiceConfig config = config("JDBC", "jdbc:postgresql://postgres.internal:5432/cloudislands", "POSTGRESQL", true);

        assertTrue(config.jdbcRepositories());
        assertTrue(config.setupDatabaseDurable());
        assertTrue(config.setupDatabaseProductionDurable());
        assertFalse(config.setupDatabaseFallbackActive());
        assertEquals("POSTGRESQL", config.setupDatabaseRequestedBackend());
        assertEquals("POSTGRESQL_JDBC", config.setupDatabaseEffectiveBackend());
        assertEquals("POSTGRESQL_JDBC", config.setupDatabaseEffectiveAuthority());
        assertEquals("NONE", config.setupDatabaseFallbackTarget());
        assertEquals("native-postgresql-core-jdbc", config.setupDatabaseFallbackReason());
        assertFalse(config.setupDatabaseCoreApiClientMode());
        assertFalse(config.setupDatabaseCoreApiClientReady());
        assertEquals("production-durable", config.setupDatabaseFallbackReadiness());
        assertFalse(config.setupDatabaseFallbackSafetyForced());
        assertEquals("none", config.setupDatabaseFallbackSafetyMode());
    }

    @Test
    void coreApiSetupIsClientModeNotCoreSelfStorage() {
        CoreServiceConfig config = config("IN_MEMORY", "", "CORE_API", true);

        assertFalse(config.jdbcRepositories());
        assertFalse(config.setupDatabaseDurable());
        assertFalse(config.setupDatabaseProductionDurable());
        assertTrue(config.setupDatabaseFallbackActive());
        assertEquals("CORE_API", config.setupDatabaseRequestedBackend());
        assertEquals("CORE_API_CLIENT", config.setupDatabaseEffectiveBackend());
        assertEquals("CORE_API_CLIENT_MODE_NO_CORE_SELF_STORAGE", config.setupDatabaseEffectiveAuthority());
        assertEquals("CORE_API", config.setupDatabaseFallbackTarget());
        assertEquals("core-api-is-client-facing-selection-not-core-service-self-storage", config.setupDatabaseFallbackReason());
        assertTrue(config.setupDatabaseCoreApiClientMode());
        assertTrue(config.setupDatabaseCoreApiClientReady());
        assertFalse(config.setupDatabaseReady());
        assertEquals("blocked-non-durable-fallback", config.setupDatabaseFallbackReadiness());
        assertFalse(config.setupDatabaseFallbackSafetyForced());
        assertEquals("blocked-non-durable-fallback", config.setupDatabaseFallbackSafetyMode());
        assertTrue(config.setupDatabaseFallbackSummary().contains("coreApiReady=true"));
        assertThrows(IllegalStateException.class, config::validateStartupStorage);
    }

    @Test
    void coreApiSetupCanUsePostgresqlForCoreSelfStorageFallback() {
        CoreServiceConfig config = config("JDBC", "jdbc:postgresql://postgres.internal:5432/cloudislands", "CORE_API", true);

        assertTrue(config.jdbcRepositories());
        assertTrue(config.setupDatabaseDurable());
        assertTrue(config.setupDatabaseProductionDurable());
        assertTrue(config.setupDatabaseFallbackActive());
        assertEquals("CORE_API", config.setupDatabaseRequestedBackend());
        assertEquals("POSTGRESQL_JDBC", config.setupDatabaseEffectiveBackend());
        assertEquals("POSTGRESQL_JDBC_FALLBACK_FOR_CORE_API", config.setupDatabaseEffectiveAuthority());
        assertEquals("POSTGRESQL", config.setupDatabaseFallbackTarget());
        assertEquals("requested-core_api-uses-postgresql-core-jdbc-fallback", config.setupDatabaseFallbackReason());
        assertTrue(config.setupDatabaseCoreApiClientMode());
        assertTrue(config.setupDatabaseCoreApiClientReady());
        assertEquals("production-durable", config.setupDatabaseFallbackReadiness());
        assertFalse(config.setupDatabaseFallbackSafetyForced());
        assertEquals("durable-shared-backend", config.setupDatabaseFallbackSafetyMode());
        assertTrue(config.setupDatabaseFallbackSummary().contains("target=POSTGRESQL"));
        assertTrue(config.setupDatabaseFallbackSummary().contains("coreApiReady=true"));
    }

    @Test
    void disabledFallbackBlocksUnsupportedSetupInProduction() {
        CoreServiceConfig config = config("JDBC", "jdbc:sqlserver://mssql.internal:1433/cloudislands", "UNSUPPORTED_JDBC", false);

        assertFalse(config.jdbcRepositories());
        assertFalse(config.setupDatabaseDurable());
        assertFalse(config.setupDatabaseProductionDurable());
        assertTrue(config.setupDatabaseFallbackActive());
        assertEquals("UNSUPPORTED_JDBC", config.setupDatabaseRequestedBackend());
        assertEquals("UNAVAILABLE_NON_DURABLE", config.setupDatabaseEffectiveBackend());
        assertEquals("BLOCKED_NON_DURABLE_CORE_FALLBACK", config.setupDatabaseEffectiveAuthority());
        assertEquals("IN_MEMORY", config.setupDatabaseFallbackTarget());
        assertEquals("database-fallback-disabled-for-unsupported_jdbc-setup", config.setupDatabaseFallbackReason());
        assertFalse(config.setupDatabaseCoreApiClientMode());
        assertFalse(config.setupDatabaseCoreApiClientReady());
        assertFalse(config.setupDatabaseReady());
        assertEquals("blocked-non-durable-fallback", config.setupDatabaseFallbackReadiness());
        assertFalse(config.setupDatabaseFallbackSafetyForced());
        assertEquals("blocked-non-durable-fallback", config.setupDatabaseFallbackSafetyMode());
        assertTrue(config.setupDatabaseFallbackSummary().contains("safetyForced=false"));
        assertTrue(config.setupDatabaseFallbackSummary().contains("safetyMode=blocked-non-durable-fallback"));
        assertThrows(IllegalStateException.class, config::validateStartupStorage);
    }

    @Test
    void productionModeBlocksExplicitInMemoryFallbackOptIn() {
        CoreServiceConfig config = config("JDBC", "jdbc:sqlserver://mssql.internal:1433/cloudislands", "UNSUPPORTED_JDBC", true, "production", true, false);

        assertFalse(config.jdbcRepositories());
        assertFalse(config.setupDatabaseProductionDurable());
        assertTrue(config.setupDatabaseFallbackActive());
        assertEquals("UNAVAILABLE_NON_DURABLE", config.setupDatabaseEffectiveBackend());
        assertEquals("BLOCKED_NON_DURABLE_CORE_FALLBACK", config.setupDatabaseEffectiveAuthority());
        assertEquals("IN_MEMORY", config.setupDatabaseFallbackTarget());
        assertTrue(config.setupDatabaseAllowInMemoryFallback());
        assertEquals("blocked-non-durable-fallback", config.setupDatabaseFallbackReadiness());
        assertEquals("blocked-non-durable-fallback", config.setupDatabaseFallbackSafetyMode());
        assertThrows(IllegalStateException.class, config::validateStartupStorage);
    }

    @Test
    void productionModeAllowsOnlyDurableSharedRuntimeBackends() {
        CoreServiceConfig valid = config("JDBC", "jdbc:postgresql://postgres.internal:5432/cloudislands", "POSTGRESQL", true);
        CoreServiceConfig invalid = withRuntimeBackends(valid, "IN_MEMORY", "IN_MEMORY", "IN_MEMORY", "LOCAL", true, true);

        assertTrue(valid.productionRuntimeModeViolations().isEmpty());
        valid.validateStartupProductionRuntimeModes();
        assertEquals(
            List.of(
                "repository-mode-must-be-JDBC",
                "job-queue-mode-must-be-JDBC-or-REDIS",
                "event-bus-mode-must-be-REDIS",
                "storage-type-must-be-S3",
                "in-memory-fallback-must-be-disabled",
                "redis-lock-local-fallback-must-be-disabled"
            ),
            invalid.productionRuntimeModeViolations()
        );
        IllegalStateException exception = assertThrows(IllegalStateException.class, invalid::validateStartupProductionRuntimeModes);
        assertTrue(exception.getMessage().contains("Production runtime modes are restricted"));
        assertTrue(exception.getMessage().contains("repository-mode-must-be-JDBC"));
    }

    @Test
    void developmentModeDoesNotApplyProductionRuntimeBackendRestrictions() {
        CoreServiceConfig development = withRuntimeBackends(
            config("JDBC", "jdbc:postgresql://postgres.internal:5432/cloudislands", "POSTGRESQL", true, "development", true),
            "IN_MEMORY",
            "IN_MEMORY",
            "IN_MEMORY",
            "LOCAL",
            true,
            true
        );

        assertTrue(development.productionRuntimeModeViolations().isEmpty());
        development.validateStartupProductionRuntimeModes();
    }

    @Test
    void developmentModeCanExplicitlyUseNonDurableInMemoryFallbackButIsNotReady() {
        CoreServiceConfig config = config("JDBC", "jdbc:sqlserver://mssql.internal:1433/cloudislands", "UNSUPPORTED_JDBC", true, "development", true, false);

        assertFalse(config.jdbcRepositories());
        assertFalse(config.setupDatabaseProductionDurable());
        assertTrue(config.setupDatabaseFallbackActive());
        assertEquals("IN_MEMORY_FALLBACK", config.setupDatabaseEffectiveBackend());
        assertEquals("SAFE_IN_MEMORY_CORE_FALLBACK", config.setupDatabaseEffectiveAuthority());
        assertEquals("IN_MEMORY", config.setupDatabaseFallbackTarget());
        assertFalse(config.setupDatabaseReady());
        assertEquals("non-durable-readiness-failed", config.setupDatabaseFallbackReadiness());
        assertEquals("safe-in-memory-non-durable", config.setupDatabaseFallbackSafetyMode());
        config.validateStartupStorage();
    }

    @Test
    void developmentModeBlocksInMemoryFallbackWithoutExplicitOptIn() {
        CoreServiceConfig config = config("JDBC", "jdbc:sqlserver://mssql.internal:1433/cloudislands", "UNSUPPORTED_JDBC", true, "development", false, false);

        assertFalse(config.jdbcRepositories());
        assertFalse(config.setupDatabaseProductionDurable());
        assertTrue(config.setupDatabaseFallbackActive());
        assertEquals("UNAVAILABLE_NON_DURABLE", config.setupDatabaseEffectiveBackend());
        assertEquals("BLOCKED_NON_DURABLE_CORE_FALLBACK", config.setupDatabaseEffectiveAuthority());
        assertEquals("IN_MEMORY", config.setupDatabaseFallbackTarget());
        assertFalse(config.setupDatabaseAllowInMemoryFallback());
        assertEquals("in-memory-fallback-not-explicitly-enabled-for-unsupported_jdbc-setup", config.setupDatabaseFallbackReason());
        assertEquals("blocked-non-durable-fallback", config.setupDatabaseFallbackReadiness());
        assertEquals("blocked-non-durable-fallback", config.setupDatabaseFallbackSafetyMode());
        assertThrows(IllegalStateException.class, config::validateStartupStorage);
    }

    @Test
    void productionPublicPlainHttpBindRequiresExplicitInsecureOptIn() {
        CoreServiceConfig blocked = config("0.0.0.0", "production", false);
        CoreServiceConfig loopback = config("127.0.0.1", "production", false);
        CoreServiceConfig explicitOptIn = config("0.0.0.0", "production", true);
        CoreServiceConfig developmentPublic = config("0.0.0.0", "development", false);

        assertTrue(blocked.productionPublicPlainHttpBlocked());
        assertThrows(IllegalStateException.class, blocked::validateStartupNetworkExposure);
        assertFalse(loopback.productionPublicPlainHttpBlocked());
        loopback.validateStartupNetworkExposure();
        assertFalse(explicitOptIn.productionPublicPlainHttpBlocked());
        explicitOptIn.validateStartupNetworkExposure();
        assertFalse(developmentPublic.productionPublicPlainHttpBlocked());
        developmentPublic.validateStartupNetworkExposure();
    }

    @Test
    void tokenAuthModeRequiresGlobalTokenOrNodeCredentialBindings() {
        CoreServiceConfig tokenModeWithoutCredentials = withAuth(config("127.0.0.1", "production", false), "", "", false);
        CoreServiceConfig tokenModeWithNodeCredentials = withAuth(config("127.0.0.1", "production", false), "", "node-a:token-a", false);
        CoreServiceConfig mtlsModeWithoutToken = withAuth(config("127.0.0.1", "production", false), "", "", true);

        IllegalStateException exception = assertThrows(IllegalStateException.class, tokenModeWithoutCredentials::validateStartupAuthentication);
        assertTrue(exception.getMessage().contains("requires CI_CORE_TOKEN or CI_NODE_CREDENTIALS"));
        assertFalse(CoreNetworkExposure.coreApiAuthConfigured(tokenModeWithoutCredentials));
        assertTrue(CoreNetworkExposure.coreApiAuthConfigured(tokenModeWithNodeCredentials));
        tokenModeWithNodeCredentials.validateStartupAuthentication();
        mtlsModeWithoutToken.validateStartupAuthentication();
    }

    @Test
    void productionMtlsTrustBoundaryRequiresExplicitTrustedProxySettings() {
        CoreServiceConfig safe = withMtlsTrustBoundary(
            withAuth(config("127.0.0.1", "production", false), "", "", true),
            "X-SSL-Client-Verify",
            "SUCCESS",
            "127.0.0.1,10.0.0.0/8"
        );
        CoreServiceConfig unsafe = withMtlsTrustBoundary(safe, "", "", "0.0.0.0/0");
        CoreServiceConfig developmentUnsafe = withMtlsTrustBoundary(
            withAuth(config("127.0.0.1", "development", false), "", "", true),
            "",
            "",
            "0.0.0.0/0"
        );

        assertTrue(safe.mtlsTrustBoundaryViolations().isEmpty());
        safe.validateStartupMtlsTrustBoundary();
        assertEquals(
            List.of(
                "mtls-verified-header-required",
                "mtls-verified-value-required",
                "mtls-trusted-proxies-must-not-allow-everywhere"
            ),
            unsafe.mtlsTrustBoundaryViolations()
        );
        IllegalStateException exception = assertThrows(IllegalStateException.class, unsafe::validateStartupMtlsTrustBoundary);
        assertTrue(exception.getMessage().contains("Production mTLS trusted proxy boundary is unsafe"));
        assertTrue(developmentUnsafe.mtlsTrustBoundaryViolations().isEmpty());
        developmentUnsafe.validateStartupMtlsTrustBoundary();
    }

    @Test
    void mariadbSetupUsesNativeCoreJdbcAuthority() {
        CoreServiceConfig config = config("JDBC", "jdbc:mariadb://mariadb.internal:3306/cloudislands", "MARIADB", true);

        assertTrue(config.jdbcRepositories());
        assertTrue(config.setupDatabaseDurable());
        assertTrue(config.setupDatabaseProductionDurable());
        assertFalse(config.setupDatabaseFallbackActive());
        assertEquals("MARIADB", config.setupDatabaseRequestedBackend());
        assertEquals("MARIADB_JDBC", config.setupDatabaseEffectiveBackend());
        assertEquals("MARIADB_JDBC", config.setupDatabaseEffectiveAuthority());
        assertEquals("NONE", config.setupDatabaseFallbackTarget());
        assertEquals("native-mariadb-core-jdbc", config.setupDatabaseFallbackReason());
        assertFalse(config.setupDatabaseCoreApiClientMode());
        assertFalse(config.setupDatabaseCoreApiClientReady());
        assertEquals("production-durable", config.setupDatabaseFallbackReadiness());
        assertFalse(config.setupDatabaseFallbackSafetyForced());
        assertEquals("none", config.setupDatabaseFallbackSafetyMode());
    }

    @Test
    void httpExecutorSettingsAreTypedAndNormalized() {
        CoreServiceConfig config = config("JDBC", "jdbc:postgresql://postgres.internal:5432/cloudislands", "POSTGRESQL", true);
        CoreServiceConfig clamped = new CoreServiceConfig(
                config.bind(),
                config.port(),
                config.adminBind(),
                config.adminPort(),
                config.repositoryMode(),
                config.jobQueueMode(),
                config.eventBusMode(),
                config.jdbcUrl(),
                config.configuredDatabaseType(),
                config.databaseUsername(),
                config.databasePassword(),
                config.databasePoolSize(),
                config.setupDatabaseAutoSchema(),
                config.setupDatabaseFallbackEnabled(),
                config.setupDatabaseFallbackOrder(),
                config.setupDatabaseFallbackRequireSharedBeforeLocal(),
                config.setupDatabaseFallbackLocalLast(),
                config.setupDatabaseFallbackProductionSafeOrder(),
                config.runtimeMode(),
                config.setupDatabaseAllowInMemoryFallback(),
                config.allowInsecurePublicHttp(),
                config.setupDatabaseCoreApiBaseUrl(),
                config.setupDatabaseCoreApiAuthTokenConfigured(),
                config.setupDatabaseCoreApiAdminTokenConfigured(),
                config.setupDatabaseCoreApiTimeoutMillis(),
                config.redisUri(),
                config.redisLockLocalFallbackEnabled(),
                config.storageType(),
                config.storageEndpoint(),
                config.storageBucket(),
                config.storageLocalPath(),
                config.storageRegion(),
                config.storageAccessKey(),
                config.storageSecretKey(),
                config.storageBearerToken(),
                config.coreToken(),
                config.nodeCredentials(),
                config.adminToken(),
                config.adminPermissions(),
                config.ipAllowlist(),
                config.upgradesFile(),
                config.blockValuesFile(),
                config.levelFormulaType(),
                config.levelFormulaExpression(),
                config.worthFormulaType(),
                config.islandPool(),
                config.softFullPolicy(),
                config.hardFullPolicy(),
                config.migrationPolicy(),
                config.superiorSkyblock2MigrationEnabled(),
                config.routeTicketTtl(),
                config.routePreparingTicketTtl(),
                config.heartbeatTimeout(),
                config.leaseDuration(),
                config.snapshotKeepLatest(),
                config.snapshotRetentionPolicy(),
                config.adminApiEnabled(),
                config.adminListenerEnabled(),
                config.publicAdminApiEnabled(),
                config.requireMtls(),
                config.mtlsVerifiedHeader(),
                config.mtlsVerifiedValue(),
                config.mtlsTrustedProxies(),
                config.rateLimitRequests(),
                config.rateLimitWindow(),
                0,
                -1,
                Duration.ZERO,
                Duration.ofSeconds(-1)
        );

        assertEquals(8, config.httpWorkerThreads());
        assertEquals(128, config.httpQueueCapacity());
        assertEquals(Duration.ofSeconds(30), config.httpKeepAlive());
        assertEquals(Duration.ofSeconds(10), config.httpShutdownGrace());
        assertEquals(1, clamped.httpWorkerThreads());
        assertEquals(0, clamped.httpQueueCapacity());
        assertEquals(Duration.ofSeconds(30), clamped.httpKeepAlive());
        assertEquals(Duration.ofSeconds(10), clamped.httpShutdownGrace());
    }

    @Test
    void typedSectionsExposeExistingConfigWithoutChangingDefaults() {
        CoreServiceConfig config = config("JDBC", "jdbc:postgresql://postgres.internal:5432/cloudislands", "POSTGRESQL", true);
        CoreConfigSections sections = config.sections();

        assertEquals("127.0.0.1", sections.server().bind());
        assertEquals(8443, sections.server().port());
        assertEquals("127.0.0.1", sections.server().adminBind());
        assertEquals(9443, sections.server().adminPort());
        assertTrue(sections.server().adminListenerEnabled());
        assertFalse(sections.server().allowInsecurePublicHttp());
        assertEquals(8, sections.server().httpWorkerThreads());
        assertEquals(128, sections.server().httpQueueCapacity());
        assertEquals(Duration.ofSeconds(30), sections.server().httpKeepAlive());
        assertEquals(Duration.ofSeconds(10), sections.server().httpShutdownGrace());

        assertEquals(config.coreToken(), sections.auth().coreToken());
        assertEquals(config.nodeCredentials(), sections.auth().nodeCredentials());
        assertEquals(config.adminToken(), sections.auth().adminToken());
        assertTrue(sections.auth().adminApiEnabled());
        assertFalse(sections.auth().publicAdminApiEnabled());
        assertEquals("127.0.0.1,localhost,::1", sections.auth().mtlsTrustedProxies());

        assertEquals("JDBC", sections.database().repositoryMode());
        assertEquals("jdbc:postgresql://postgres.internal:5432/cloudislands", sections.database().jdbcUrl());
        assertEquals("POSTGRESQL", sections.database().configuredDatabaseType());
        assertEquals(20, sections.database().poolSize());
        assertEquals("POSTGRESQL,MYSQL,MARIADB,CORE_API,UNSUPPORTED_JDBC", sections.database().fallbackOrder());
        assertTrue(sections.database().coreApiAuthTokenConfigured());

        assertEquals(URI.create("redis://127.0.0.1:6379"), sections.redis().uri());
        assertEquals("REDIS", sections.redis().jobQueueMode());
        assertEquals("REDIS", sections.redis().eventBusMode());
        assertFalse(sections.redis().lockLocalFallbackEnabled());

        assertEquals("S3", sections.storage().type());
        assertEquals(URI.create("http://127.0.0.1:9000"), sections.storage().endpoint());
        assertEquals("cloudislands", sections.storage().bucket());
        assertEquals("us-east-1", sections.storage().region());

        assertEquals("island", sections.routing().islandPool());
        assertEquals("AVOID_NEW_ACTIVATIONS", sections.routing().softFullPolicy());
        assertEquals("DENY_OR_QUEUE", sections.routing().hardFullPolicy());
        assertEquals(Duration.ofSeconds(120), sections.routing().routePreparingTicketTtl());

        assertEquals("REDIS", sections.job().queueMode());
        assertEquals(Duration.ofSeconds(30), sections.job().leaseDuration());
        assertEquals(config.snapshotKeepLatest(), sections.snapshot().keepLatest());
        assertEquals(config.snapshotRetentionPolicy(), sections.snapshot().retentionPolicy());
        assertEquals(240, sections.observability().rateLimitRequests());
        assertEquals(Duration.ofSeconds(60), sections.observability().rateLimitWindow());
        assertEquals("INACTIVE_ONLY_AUTOMATIC", sections.migration().policy());
        assertTrue(sections.migration().superiorSkyblock2Enabled());
    }

    private CoreServiceConfig config(String repositoryMode, String jdbcUrl, String databaseType, boolean fallbackEnabled) {
        return config(repositoryMode, jdbcUrl, databaseType, fallbackEnabled, "production", false);
    }

    private CoreServiceConfig config(String repositoryMode, String jdbcUrl, String databaseType, boolean fallbackEnabled, String runtimeMode, boolean allowInMemoryFallback) {
        return config(repositoryMode, jdbcUrl, databaseType, fallbackEnabled, runtimeMode, allowInMemoryFallback, true);
    }

    private CoreServiceConfig config(String repositoryMode, String jdbcUrl, String databaseType, boolean fallbackEnabled, String runtimeMode, boolean allowInMemoryFallback, boolean coreApiReady) {
        return config(repositoryMode, jdbcUrl, databaseType, fallbackEnabled, runtimeMode, allowInMemoryFallback, coreApiReady, "127.0.0.1", false);
    }

    private CoreServiceConfig config(String bind, String runtimeMode, boolean allowInsecurePublicHttp) {
        return config("JDBC", "jdbc:postgresql://postgres.internal:5432/cloudislands", "POSTGRESQL", true, runtimeMode, false, true, bind, allowInsecurePublicHttp);
    }

    private CoreServiceConfig config(String repositoryMode, String jdbcUrl, String databaseType, boolean fallbackEnabled, String runtimeMode, boolean allowInMemoryFallback, boolean coreApiReady, String bind, boolean allowInsecurePublicHttp) {
        return new CoreServiceConfig(
                bind,
                8443,
                "127.0.0.1",
                9443,
                repositoryMode,
                "REDIS",
                "REDIS",
                jdbcUrl,
                databaseType,
                "cloudislands",
                "",
                20,
                false,
                fallbackEnabled,
                "POSTGRESQL,MYSQL,MARIADB,CORE_API,UNSUPPORTED_JDBC",
                true,
                true,
                "POSTGRESQL,MYSQL,MARIADB,CORE_API",
                runtimeMode,
                allowInMemoryFallback,
                allowInsecurePublicHttp,
                coreApiReady ? "http://127.0.0.1:8443" : "",
                coreApiReady,
                coreApiReady,
                3000,
                URI.create("redis://127.0.0.1:6379"),
                false,
                "S3",
                URI.create("http://127.0.0.1:9000"),
                "cloudislands",
                "cloudislands-storage",
                "us-east-1",
                "",
                "",
                "*",
                "",
                "",
                "",
                "",
                "",
                "",
                "",
                "EXPRESSION",
                "floor(total_level_points / 1000)",
                "SUM_BLOCK_VALUES",
                "island",
                "AVOID_NEW_ACTIVATIONS",
                "DENY_OR_QUEUE",
                "INACTIVE_ONLY_AUTOMATIC",
                true,
                Duration.ofSeconds(30),
                Duration.ofSeconds(120),
                Duration.ofSeconds(5),
                Duration.ofSeconds(30),
                SnapshotRetentionPolicy.defaultPolicy().retainedSnapshotCount(),
                SnapshotRetentionPolicy.defaultPolicy(),
                true,
                true,
                false,
                true,
                "X-SSL-Client-Verify",
                "SUCCESS",
                "127.0.0.1,localhost,::1",
                240,
                Duration.ofSeconds(60),
                8,
                128,
                Duration.ofSeconds(30),
                Duration.ofSeconds(10)
        );
    }

    private CoreServiceConfig withAuth(CoreServiceConfig config, String coreToken, String nodeCredentials, boolean requireMtls) {
        return new CoreServiceConfig(
                config.bind(),
                config.port(),
                config.adminBind(),
                config.adminPort(),
                config.repositoryMode(),
                config.jobQueueMode(),
                config.eventBusMode(),
                config.jdbcUrl(),
                config.configuredDatabaseType(),
                config.databaseUsername(),
                config.databasePassword(),
                config.databasePoolSize(),
                config.setupDatabaseAutoSchema(),
                config.setupDatabaseFallbackEnabled(),
                config.setupDatabaseFallbackOrder(),
                config.setupDatabaseFallbackRequireSharedBeforeLocal(),
                config.setupDatabaseFallbackLocalLast(),
                config.setupDatabaseFallbackProductionSafeOrder(),
                config.runtimeMode(),
                config.setupDatabaseAllowInMemoryFallback(),
                config.allowInsecurePublicHttp(),
                config.setupDatabaseCoreApiBaseUrl(),
                config.setupDatabaseCoreApiAuthTokenConfigured(),
                config.setupDatabaseCoreApiAdminTokenConfigured(),
                config.setupDatabaseCoreApiTimeoutMillis(),
                config.redisUri(),
                config.redisLockLocalFallbackEnabled(),
                config.storageType(),
                config.storageEndpoint(),
                config.storageBucket(),
                config.storageLocalPath(),
                config.storageRegion(),
                config.storageAccessKey(),
                config.storageSecretKey(),
                config.storageBearerToken(),
                coreToken,
                nodeCredentials,
                config.adminToken(),
                config.adminPermissions(),
                config.ipAllowlist(),
                config.upgradesFile(),
                config.blockValuesFile(),
                config.levelFormulaType(),
                config.levelFormulaExpression(),
                config.worthFormulaType(),
                config.islandPool(),
                config.softFullPolicy(),
                config.hardFullPolicy(),
                config.migrationPolicy(),
                config.superiorSkyblock2MigrationEnabled(),
                config.routeTicketTtl(),
                config.routePreparingTicketTtl(),
                config.heartbeatTimeout(),
                config.leaseDuration(),
                config.snapshotKeepLatest(),
                config.snapshotRetentionPolicy(),
                config.adminApiEnabled(),
                config.adminListenerEnabled(),
                config.publicAdminApiEnabled(),
                requireMtls,
                config.mtlsVerifiedHeader(),
                config.mtlsVerifiedValue(),
                config.mtlsTrustedProxies(),
                config.rateLimitRequests(),
                config.rateLimitWindow(),
                config.httpWorkerThreads(),
                config.httpQueueCapacity(),
                config.httpKeepAlive(),
                config.httpShutdownGrace()
        );
    }

    private CoreServiceConfig withMtlsTrustBoundary(CoreServiceConfig config, String mtlsVerifiedHeader, String mtlsVerifiedValue, String mtlsTrustedProxies) {
        return new CoreServiceConfig(
                config.bind(),
                config.port(),
                config.adminBind(),
                config.adminPort(),
                config.repositoryMode(),
                config.jobQueueMode(),
                config.eventBusMode(),
                config.jdbcUrl(),
                config.configuredDatabaseType(),
                config.databaseUsername(),
                config.databasePassword(),
                config.databasePoolSize(),
                config.setupDatabaseAutoSchema(),
                config.setupDatabaseFallbackEnabled(),
                config.setupDatabaseFallbackOrder(),
                config.setupDatabaseFallbackRequireSharedBeforeLocal(),
                config.setupDatabaseFallbackLocalLast(),
                config.setupDatabaseFallbackProductionSafeOrder(),
                config.runtimeMode(),
                config.setupDatabaseAllowInMemoryFallback(),
                config.allowInsecurePublicHttp(),
                config.setupDatabaseCoreApiBaseUrl(),
                config.setupDatabaseCoreApiAuthTokenConfigured(),
                config.setupDatabaseCoreApiAdminTokenConfigured(),
                config.setupDatabaseCoreApiTimeoutMillis(),
                config.redisUri(),
                config.redisLockLocalFallbackEnabled(),
                config.storageType(),
                config.storageEndpoint(),
                config.storageBucket(),
                config.storageLocalPath(),
                config.storageRegion(),
                config.storageAccessKey(),
                config.storageSecretKey(),
                config.storageBearerToken(),
                config.coreToken(),
                config.nodeCredentials(),
                config.adminToken(),
                config.adminPermissions(),
                config.ipAllowlist(),
                config.upgradesFile(),
                config.blockValuesFile(),
                config.levelFormulaType(),
                config.levelFormulaExpression(),
                config.worthFormulaType(),
                config.islandPool(),
                config.softFullPolicy(),
                config.hardFullPolicy(),
                config.migrationPolicy(),
                config.superiorSkyblock2MigrationEnabled(),
                config.routeTicketTtl(),
                config.routePreparingTicketTtl(),
                config.heartbeatTimeout(),
                config.leaseDuration(),
                config.snapshotKeepLatest(),
                config.snapshotRetentionPolicy(),
                config.adminApiEnabled(),
                config.adminListenerEnabled(),
                config.publicAdminApiEnabled(),
                config.requireMtls(),
                mtlsVerifiedHeader,
                mtlsVerifiedValue,
                mtlsTrustedProxies,
                config.rateLimitRequests(),
                config.rateLimitWindow(),
                config.httpWorkerThreads(),
                config.httpQueueCapacity(),
                config.httpKeepAlive(),
                config.httpShutdownGrace()
        );
    }

    private CoreServiceConfig withRuntimeBackends(
            CoreServiceConfig config,
            String repositoryMode,
            String jobQueueMode,
            String eventBusMode,
            String storageType,
            boolean allowInMemoryFallback,
            boolean redisLockLocalFallbackEnabled
    ) {
        return new CoreServiceConfig(
                config.bind(),
                config.port(),
                config.adminBind(),
                config.adminPort(),
                repositoryMode,
                jobQueueMode,
                eventBusMode,
                config.jdbcUrl(),
                config.configuredDatabaseType(),
                config.databaseUsername(),
                config.databasePassword(),
                config.databasePoolSize(),
                config.setupDatabaseAutoSchema(),
                config.setupDatabaseFallbackEnabled(),
                config.setupDatabaseFallbackOrder(),
                config.setupDatabaseFallbackRequireSharedBeforeLocal(),
                config.setupDatabaseFallbackLocalLast(),
                config.setupDatabaseFallbackProductionSafeOrder(),
                config.runtimeMode(),
                allowInMemoryFallback,
                config.allowInsecurePublicHttp(),
                config.setupDatabaseCoreApiBaseUrl(),
                config.setupDatabaseCoreApiAuthTokenConfigured(),
                config.setupDatabaseCoreApiAdminTokenConfigured(),
                config.setupDatabaseCoreApiTimeoutMillis(),
                config.redisUri(),
                redisLockLocalFallbackEnabled,
                storageType,
                config.storageEndpoint(),
                config.storageBucket(),
                config.storageLocalPath(),
                config.storageRegion(),
                config.storageAccessKey(),
                config.storageSecretKey(),
                config.storageBearerToken(),
                config.coreToken(),
                config.nodeCredentials(),
                config.adminToken(),
                config.adminPermissions(),
                config.ipAllowlist(),
                config.upgradesFile(),
                config.blockValuesFile(),
                config.levelFormulaType(),
                config.levelFormulaExpression(),
                config.worthFormulaType(),
                config.islandPool(),
                config.softFullPolicy(),
                config.hardFullPolicy(),
                config.migrationPolicy(),
                config.superiorSkyblock2MigrationEnabled(),
                config.routeTicketTtl(),
                config.routePreparingTicketTtl(),
                config.heartbeatTimeout(),
                config.leaseDuration(),
                config.snapshotKeepLatest(),
                config.snapshotRetentionPolicy(),
                config.adminApiEnabled(),
                config.adminListenerEnabled(),
                config.publicAdminApiEnabled(),
                config.requireMtls(),
                config.mtlsVerifiedHeader(),
                config.mtlsVerifiedValue(),
                config.mtlsTrustedProxies(),
                config.rateLimitRequests(),
                config.rateLimitWindow(),
                config.httpWorkerThreads(),
                config.httpQueueCapacity(),
                config.httpKeepAlive(),
                config.httpShutdownGrace()
        );
    }
}
