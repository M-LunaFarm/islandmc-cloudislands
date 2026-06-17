package kr.seungmin.satisskyfactory.config;

import org.junit.jupiter.api.Test;

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
    void policyListsAreImmutable() {
        assertThrows(UnsupportedOperationException.class, () -> SatisDatabaseConfigPolicy.typePriority().add("legacy"));
        assertThrows(UnsupportedOperationException.class, () -> SatisDatabaseConfigPolicy.pathPriority().add("legacy"));
        assertThrows(UnsupportedOperationException.class, () -> SatisDatabaseConfigPolicy.commonJdbcAliases().add("legacy"));
        assertThrows(UnsupportedOperationException.class, () -> SatisDatabaseConfigPolicy.credentialAliases().add("legacy"));
    }
}
