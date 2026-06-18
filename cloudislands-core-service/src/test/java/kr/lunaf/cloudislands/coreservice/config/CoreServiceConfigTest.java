package kr.lunaf.cloudislands.coreservice.config;

import kr.lunaf.cloudislands.storage.snapshot.SnapshotRetentionPolicy;
import org.junit.jupiter.api.Test;

import java.net.URI;
import java.time.Duration;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
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
        assertEquals("core-api-client-ready", config.setupDatabaseFallbackReadiness());
        assertFalse(config.setupDatabaseFallbackSafetyForced());
        assertEquals("core-api-client-ready", config.setupDatabaseFallbackSafetyMode());
        assertTrue(config.setupDatabaseFallbackSummary().contains("coreApiReady=true"));
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
    void disabledFallbackStillUsesSafeInMemoryForUnsupportedSetup() {
        CoreServiceConfig config = config("JDBC", "jdbc:sqlserver://mssql.internal:1433/cloudislands", "UNSUPPORTED_JDBC", false);

        assertFalse(config.jdbcRepositories());
        assertFalse(config.setupDatabaseDurable());
        assertFalse(config.setupDatabaseProductionDurable());
        assertTrue(config.setupDatabaseFallbackActive());
        assertEquals("UNSUPPORTED_JDBC", config.setupDatabaseRequestedBackend());
        assertEquals("IN_MEMORY_FALLBACK", config.setupDatabaseEffectiveBackend());
        assertEquals("SAFE_IN_MEMORY_CORE_FALLBACK", config.setupDatabaseEffectiveAuthority());
        assertEquals("IN_MEMORY", config.setupDatabaseFallbackTarget());
        assertEquals("database-fallback-disabled-for-unsupported_jdbc-setup", config.setupDatabaseFallbackReason());
        assertFalse(config.setupDatabaseCoreApiClientMode());
        assertFalse(config.setupDatabaseCoreApiClientReady());
        assertEquals("safe-startup-non-durable", config.setupDatabaseFallbackReadiness());
        assertTrue(config.setupDatabaseFallbackSafetyForced());
        assertEquals("fallback-disabled-but-safe-in-memory-forced", config.setupDatabaseFallbackSafetyMode());
        assertTrue(config.setupDatabaseFallbackSummary().contains("safetyForced=true"));
        assertTrue(config.setupDatabaseFallbackSummary().contains("safetyMode=fallback-disabled-but-safe-in-memory-forced"));
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

    private CoreServiceConfig config(String repositoryMode, String jdbcUrl, String databaseType, boolean fallbackEnabled) {
        return new CoreServiceConfig(
                "127.0.0.1",
                8443,
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
                "http://127.0.0.1:8443",
                true,
                true,
                3000,
                URI.create("redis://127.0.0.1:6379"),
                "S3",
                URI.create("http://127.0.0.1:9000"),
                "cloudislands",
                "cloudislands-storage",
                "us-east-1",
                "",
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
                "X-SSL-Client-Verify",
                "SUCCESS",
                240,
                Duration.ofSeconds(60)
        );
    }
}
