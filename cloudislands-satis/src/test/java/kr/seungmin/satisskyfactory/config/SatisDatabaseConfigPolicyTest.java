package kr.seungmin.satisskyfactory.config;

import org.junit.jupiter.api.Test;

import java.util.List;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertThrows;
import static org.junit.jupiter.api.Assertions.assertTrue;

class SatisDatabaseConfigPolicyTest {
    @Test
    void keepsAddonDatabaseConfigBetweenSetupAndLegacyDatabaseFallback() {
        assertEquals("setup.database", SatisDatabaseConfigPolicy.SETUP_ROOT);
        assertEquals("addons.cloudislands-satis.database", SatisDatabaseConfigPolicy.ADDON_ROOT);
        assertEquals("database", SatisDatabaseConfigPolicy.LEGACY_ROOT);
        assertEquals(
                "env,setup.database,addons.cloudislands-satis.database,database",
                SatisDatabaseConfigPolicy.FALLBACK_PRECEDENCE
        );
        assertTrue(SatisDatabaseConfigPolicy.typePriority().indexOf("setup.database.type")
                < SatisDatabaseConfigPolicy.typePriority().indexOf("addons.cloudislands-satis.database.type"));
        assertTrue(SatisDatabaseConfigPolicy.typePriority().indexOf("addons.cloudislands-satis.database.type")
                < SatisDatabaseConfigPolicy.typePriority().indexOf("database.type"));
    }

    @Test
    void exposesHostLevelAddonDatabaseAliasesForPathJdbcAndCredentials() {
        assertTrue(SatisDatabaseConfigPolicy.pathPriority().contains("addons.cloudislands-satis.database.path"));
        assertTrue(SatisDatabaseConfigPolicy.pathPriority().contains("addons.cloudislands-satis.database.shared-directory"));
        assertTrue(SatisDatabaseConfigPolicy.pathPriority().contains("addons.cloudislands-satis.database.sqlite-file"));
        assertTrue(SatisDatabaseConfigPolicy.commonJdbcAliases().contains("addons.cloudislands-satis.database.jdbc.url"));
        assertTrue(SatisDatabaseConfigPolicy.credentialAliases().contains("addons.cloudislands-satis.database.jdbc.username"));
        assertTrue(SatisDatabaseConfigPolicy.credentialAliases().contains("addons.cloudislands-satis.database.jdbc.password"));
        assertEquals(
                "setup.database.jdbc.url,setup.database.<backend>.jdbc-url,setup.database.<backend>.url,addons.cloudislands-satis.database.jdbc.url,database.jdbc.url,database.<backend>.url",
                SatisDatabaseConfigPolicy.commonJdbcAliasMetadata()
        );
    }

    @Test
    void classifiesSharedAndLocalFallbackBackends() {
        assertEquals(List.of("POSTGRESQL", "MYSQL", "MARIADB", "CORE_API"), SatisDatabaseConfigPolicy.sharedBackends());
        assertEquals(List.of("SQLITE"), SatisDatabaseConfigPolicy.localBackends());
        assertEquals("POSTGRESQL", SatisDatabaseConfigPolicy.normalizeBackend("pg"));
        assertEquals("MARIADB", SatisDatabaseConfigPolicy.normalizeBackend("maria"));
        assertEquals("CORE_API", SatisDatabaseConfigPolicy.normalizeBackend("cloudislands-api"));
        assertEquals("SQLITE", SatisDatabaseConfigPolicy.normalizeBackend("local-sqlite"));
        assertTrue(SatisDatabaseConfigPolicy.sharedBackend("postgres"));
        assertTrue(SatisDatabaseConfigPolicy.sharedBackend("mysql"));
        assertTrue(SatisDatabaseConfigPolicy.sharedBackend("coreapi"));
        assertTrue(SatisDatabaseConfigPolicy.localBackend("memory"));
    }

    @Test
    void reportsFallbackOrderRiskForMultiNodeSetups() {
        assertEquals(
                SatisDatabaseConfigPolicy.FALLBACK_RISK_SHARED_BEFORE_LOCAL,
                SatisDatabaseConfigPolicy.fallbackRisk(List.of("postgresql", "mysql", "sqlite"))
        );
        assertEquals(
                SatisDatabaseConfigPolicy.FALLBACK_RISK_LOCAL_BEFORE_SHARED,
                SatisDatabaseConfigPolicy.fallbackRisk(List.of("sqlite", "core-api"))
        );
        assertEquals(
                SatisDatabaseConfigPolicy.FALLBACK_RISK_LOCAL_ONLY,
                SatisDatabaseConfigPolicy.fallbackRisk(List.of("sqlite"))
        );
        assertEquals(
                SatisDatabaseConfigPolicy.FALLBACK_RISK_SHARED_ONLY,
                SatisDatabaseConfigPolicy.fallbackRisk(List.of("coreapi", "mariadb"))
        );
        assertEquals(
                SatisDatabaseConfigPolicy.FALLBACK_RISK_NO_READY_BACKEND,
                SatisDatabaseConfigPolicy.fallbackRisk(List.of("unknown"))
        );
        assertEquals(
                SatisDatabaseConfigPolicy.FALLBACK_RISK_NO_ORDER,
                SatisDatabaseConfigPolicy.fallbackRisk(List.of())
        );
        assertEquals("POSTGRESQL", SatisDatabaseConfigPolicy.firstSharedFallback(List.of("sqlite", "postgres")));
        assertEquals(0, SatisDatabaseConfigPolicy.localFallbackPosition(List.of("sqlite", "postgres")));
    }

    @Test
    void policyListsAreImmutable() {
        assertThrows(UnsupportedOperationException.class, () -> SatisDatabaseConfigPolicy.typePriority().add("legacy"));
        assertThrows(UnsupportedOperationException.class, () -> SatisDatabaseConfigPolicy.pathPriority().add("legacy"));
        assertThrows(UnsupportedOperationException.class, () -> SatisDatabaseConfigPolicy.commonJdbcAliases().add("legacy"));
        assertThrows(UnsupportedOperationException.class, () -> SatisDatabaseConfigPolicy.credentialAliases().add("legacy"));
        assertThrows(UnsupportedOperationException.class, () -> SatisDatabaseConfigPolicy.sharedBackends().add("legacy"));
        assertThrows(UnsupportedOperationException.class, () -> SatisDatabaseConfigPolicy.localBackends().add("legacy"));
    }
}
