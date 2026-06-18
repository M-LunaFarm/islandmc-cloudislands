package kr.lunaf.cloudislands.common.failure;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertTrue;

import java.util.List;
import java.util.Set;
import org.junit.jupiter.api.Test;

class SetupBackendFallbackPolicyTest {
    @Test
    void pinsConfigPathsAndProductionFallbackOrder() {
        assertEquals(
            "setup-selects-postgresql-mysql-mariadb-or-core-api-with-shared-safe-fallback-before-local",
            SetupBackendFallbackPolicy.CONTRACT
        );
        assertEquals("setup.database", SetupBackendFallbackPolicy.CONFIG_PATH);
        assertEquals("setup.database.fallback", SetupBackendFallbackPolicy.FALLBACK_CONFIG_PATH);
        assertEquals("setup.database.type", SetupBackendFallbackPolicy.SELECTED_BACKEND_FIELD);
        assertEquals("setup.database.fallback.order", SetupBackendFallbackPolicy.FALLBACK_ORDER_FIELD);
        assertEquals("setup.database.core-api.enabled", SetupBackendFallbackPolicy.CORE_API_ENABLED_FIELD);
        assertEquals("setup.database.core-api.local-cache-writes.enabled", SetupBackendFallbackPolicy.CORE_API_LOCAL_CACHE_WRITES_FIELD);
        assertEquals("setup.database.core-api.flattened-fallback.enabled", SetupBackendFallbackPolicy.CORE_API_FLATTENED_FALLBACK_FIELD);
        assertEquals("setup.database.jdbc.url", SetupBackendFallbackPolicy.JDBC_URL_FIELD);
        assertEquals("POSTGRESQL,MYSQL,MARIADB,CORE_API", SetupBackendFallbackPolicy.PRODUCTION_SAFE_ORDER);
        assertEquals("SQLITE,UNSUPPORTED_JDBC", SetupBackendFallbackPolicy.LAST_RESORT_ORDER);
        assertEquals(
            "env-type>setup.database.type>setup.database.core-api.enabled>setup.database.jdbc.url>single-configured-shared-backend>legacy-database.type",
            SetupBackendFallbackPolicy.SETUP_SOURCE_PRECEDENCE
        );
        assertEquals(
            List.of("POSTGRESQL", "MYSQL", "MARIADB", "CORE_API"),
            SetupBackendFallbackPolicy.PRODUCTION_FALLBACK_ORDER
        );
        assertEquals(
            List.of("SQLITE", "UNSUPPORTED_JDBC"),
            SetupBackendFallbackPolicy.LAST_RESORT_FALLBACK_ORDER
        );
    }

    @Test
    void exposesReadinessFieldsForSetupDatabaseBackends() {
        assertEquals(
            List.of(
                "env-type",
                "setup.database.type",
                "setup.database.core-api.enabled",
                "setup.database.jdbc.url",
                "single-configured-shared-backend",
                "legacy-database.type"
            ),
            SetupBackendFallbackPolicy.setupSourcePrecedence()
        );
        assertEquals(
            List.of(
                "setup.database.core-api.enabled",
                "cloudislands-api",
                "addon-state",
                "table-key-value-bulk-save-or-flattened-fallback"
            ),
            SetupBackendFallbackPolicy.backendReadinessFields("core-api")
        );
        assertEquals(
            List.of(
                "setup.database.postgresql.jdbc-url",
                "setup.database.postgresql.host",
                "setup.database.postgresql.database",
                "setup.database.postgresql.username",
                "setup.database.postgresql.password"
            ),
            SetupBackendFallbackPolicy.backendReadinessFields("postgres")
        );
        assertEquals(
            List.of(
                "setup.database.mysql.jdbc-url",
                "setup.database.mysql.host",
                "setup.database.mysql.database",
                "setup.database.mysql.username",
                "setup.database.mysql.password"
            ),
            SetupBackendFallbackPolicy.backendReadinessFields("mysql")
        );
        assertEquals(
            List.of(
                "setup.database.mariadb.jdbc-url",
                "setup.database.mariadb.host",
                "setup.database.mariadb.database",
                "setup.database.mariadb.username",
                "setup.database.mariadb.password"
            ),
            SetupBackendFallbackPolicy.backendReadinessFields("mariadb")
        );
        assertTrue(SetupBackendFallbackPolicy.backendHasReadinessContract("coreapi"));
        assertFalse(SetupBackendFallbackPolicy.backendHasReadinessContract("unknown"));
        assertEquals(
            "core-api-ready-requires-cloudislands-api-addon-state-and-bulk-or-flattened-state-writer",
            SetupBackendFallbackPolicy.CORE_API_READY_POLICY
        );
        assertEquals(
            "jdbc-backend-ready-requires-jdbc-url-or-host-database-credentials",
            SetupBackendFallbackPolicy.JDBC_READY_POLICY
        );
    }

    @Test
    void keepsCoreApiLocalCacheWritesSingleNodeOnly() {
        assertEquals(
            "core-api-local-cache-writes-disabled-by-default-and-single-node-rescue-only",
            SetupBackendFallbackPolicy.LOCAL_CACHE_WRITE_POLICY
        );
        assertTrue(SetupBackendFallbackPolicy.coreApiLocalCacheWritesProductionSafe(false, false));
        assertTrue(SetupBackendFallbackPolicy.coreApiLocalCacheWritesProductionSafe(true, true));
        assertFalse(SetupBackendFallbackPolicy.coreApiLocalCacheWritesProductionSafe(true, false));
    }

    @Test
    void treatsSharedBackendsAsSafeForMultipleIslandNodes() {
        assertEquals(Set.of("POSTGRESQL", "MYSQL", "MARIADB", "CORE_API"), SetupBackendFallbackPolicy.SHARED_STATE_BACKENDS);
        assertTrue(SetupBackendFallbackPolicy.sharedStateBackend("postgres"));
        assertTrue(SetupBackendFallbackPolicy.sharedStateBackend("mysql-jdbc"));
        assertTrue(SetupBackendFallbackPolicy.sharedStateBackend("mariadb_jdbc"));
        assertTrue(SetupBackendFallbackPolicy.sharedStateBackend("coreapi"));
        assertFalse(SetupBackendFallbackPolicy.sharedStateBackend("unsupported-jdbc"));
        assertFalse(SetupBackendFallbackPolicy.sharedStateBackend(null));
        assertTrue(SetupBackendFallbackPolicy.localStateBackend("sqlite"));
        assertTrue(SetupBackendFallbackPolicy.localStateBackend("local-sqlite"));
    }

    @Test
    void fallsBackUnknownOrEmptySetupToCoreApiInsteadOfLocalStorage() {
        assertEquals("CORE_API", SetupBackendFallbackPolicy.fallbackTarget(""));
        assertEquals("CORE_API", SetupBackendFallbackPolicy.fallbackTarget("sqlite"));
        assertEquals("POSTGRESQL", SetupBackendFallbackPolicy.fallbackTarget("pgsql"));
        assertTrue(SetupBackendFallbackPolicy.fallbackKeepsSharedState("sqlite"));
        assertEquals("setup-backend-empty-use-core-api", SetupBackendFallbackPolicy.fallbackReason(""));
        assertEquals("local-fallback-last-resort-not-shared-safe", SetupBackendFallbackPolicy.fallbackReason("sqlite"));
        assertEquals("setup-backend-supported", SetupBackendFallbackPolicy.fallbackReason("postgresql"));
    }

    @Test
    void keepsUnsupportedJdbcExplicitlyLastResort() {
        assertTrue(SetupBackendFallbackPolicy.unsafeLocalFallback("unsupported-jdbc"));
        assertTrue(SetupBackendFallbackPolicy.unsafeLocalFallback("sqlite"));
        assertFalse(SetupBackendFallbackPolicy.unsafeLocalFallback("core-api"));
        assertEquals(
            "unsupported-jdbc-is-last-resort-and-not-valid-for-multi-island-node-production",
            SetupBackendFallbackPolicy.UNSAFE_LOCAL_POLICY
        );
        assertEquals(
            "local-fallback-last-resort-not-shared-safe",
            SetupBackendFallbackPolicy.fallbackReason("unsupported_jdbc")
        );
    }

    @Test
    void resolvesReadyFallbackChainWithoutPromotingLocalStorageFirst() {
        assertEquals(
            List.of("POSTGRESQL", "MYSQL", "MARIADB", "CORE_API", "SQLITE", "UNSUPPORTED_JDBC"),
            SetupBackendFallbackPolicy.fallbackOrder("")
        );
        assertEquals(
            "MYSQL,CORE_API,SQLITE",
            SetupBackendFallbackPolicy.fallbackReadyChain("POSTGRESQL,MYSQL,MARIADB,CORE_API,SQLITE", "mysql,coreapi,local-sqlite")
        );
        assertEquals(
            "POSTGRESQL,MARIADB",
            SetupBackendFallbackPolicy.fallbackNotReadyBackends("POSTGRESQL,MYSQL,MARIADB,CORE_API,SQLITE", "mysql,coreapi,local-sqlite")
        );
        assertEquals(
            "ready=MYSQL,CORE_API,SQLITE;not-ready=POSTGRESQL,MARIADB",
            SetupBackendFallbackPolicy.fallbackReadinessSummary("POSTGRESQL,MYSQL,MARIADB,CORE_API,SQLITE", "mysql,coreapi,local-sqlite")
        );
        assertEquals(
            SetupBackendFallbackPolicy.FALLBACK_RISK_SHARED_BEFORE_LOCAL,
            SetupBackendFallbackPolicy.fallbackReadyChainRisk("POSTGRESQL,MYSQL,MARIADB,CORE_API,SQLITE", "mysql,coreapi,local-sqlite")
        );
        assertTrue(SetupBackendFallbackPolicy.fallbackReadyChainProductionSafe("POSTGRESQL,MYSQL,MARIADB,CORE_API,SQLITE", "mysql,coreapi,local-sqlite"));
    }

    @Test
    void flagsLocalOnlyOrLocalFirstFallbackAsUnsafeForIslandNodePools() {
        assertEquals(
            SetupBackendFallbackPolicy.FALLBACK_RISK_LOCAL_ONLY,
            SetupBackendFallbackPolicy.fallbackReadyChainRisk("POSTGRESQL,MYSQL,MARIADB,CORE_API,SQLITE", "sqlite")
        );
        assertFalse(SetupBackendFallbackPolicy.fallbackReadyChainProductionSafe("POSTGRESQL,MYSQL,MARIADB,CORE_API,SQLITE", "sqlite"));
        assertEquals(
            SetupBackendFallbackPolicy.FALLBACK_RISK_LOCAL_BEFORE_SHARED,
            SetupBackendFallbackPolicy.fallbackRisk(List.of("sqlite", "core-api"))
        );
    }
}
