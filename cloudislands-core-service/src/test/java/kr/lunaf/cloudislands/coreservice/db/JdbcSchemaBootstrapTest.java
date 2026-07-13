package kr.lunaf.cloudislands.coreservice.db;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertTrue;

import java.io.IOException;
import java.nio.charset.StandardCharsets;
import org.junit.jupiter.api.Test;

class JdbcSchemaBootstrapTest {
    @Test
    void classifiesSupportedCoreJdbcBootstrapProducts() {
        assertEquals("POSTGRESQL,MYSQL,MARIADB", JdbcSchemaBootstrap.CORE_JDBC_BOOTSTRAP_PRODUCTS);
        assertEquals("POSTGRESQL", JdbcSchemaBootstrap.databaseProductFamily("PostgreSQL"));
        assertEquals("MYSQL", JdbcSchemaBootstrap.databaseProductFamily("MySQL"));
        assertEquals("MARIADB", JdbcSchemaBootstrap.databaseProductFamily("MariaDB Server"));
        assertEquals("UNSUPPORTED", JdbcSchemaBootstrap.databaseProductFamily("Microsoft SQL Server"));
    }

    @Test
    void keepsMariaDbOnTheMysqlCompatibleBootstrapPath() {
        assertEquals("mariadb-uses-mysql-compatible-core-schema-bootstrap", JdbcSchemaBootstrap.MARIADB_SCHEMA_POLICY);
        assertEquals("/db/mysql/V1__cloudislands_mysql_schema.sql", JdbcSchemaBootstrap.MYSQL_COMPATIBLE_SCHEMA_RESOURCE);
        assertEquals("mysql-v1", JdbcSchemaBootstrap.MYSQL_COMPATIBLE_SCHEMA_ID);
        assertEquals("mysql-compatible-migration-chain:3", JdbcSchemaBootstrap.schemaResourceForProduct("MariaDB Server"));
        assertEquals("mysql-compatible-migration-chain:3", JdbcSchemaBootstrap.schemaResourceForProduct("MySQL"));
    }

    @Test
    void exposesPostgresqlChainAndRejectsUnsupportedProducts() {
        assertEquals("postgresql-migration-chain:80", JdbcSchemaBootstrap.schemaResourceForProduct("PostgreSQL"));
        assertEquals("", JdbcSchemaBootstrap.schemaResourceForProduct("SQLite"));
    }

    @Test
    void permissionKeyExpansionMatchesEveryRuntimePermission() throws IOException {
        try (var input = JdbcSchemaBootstrapTest.class.getResourceAsStream("/db/migration/V77__island_permission_key_expansion.sql")) {
            String migration = new String(input.readAllBytes(), StandardCharsets.UTF_8);
            assertTrue(migration.contains("DROP CONSTRAINT IF EXISTS chk_island_permissions_key_known"));
            assertTrue(migration.contains("DROP CONSTRAINT IF EXISTS chk_island_permission_override_key_known"));
            assertEquals(2, occurrences(migration, "'BUILD'"));
            assertEquals(2, occurrences(migration, "'DEPOSIT_BANK'"));
        }
    }

    @Test
    void extensiblePermissionGuardsAcceptFutureEnumKeysWithoutAnotherAllowlistMigration() throws IOException {
        try (var input = JdbcSchemaBootstrapTest.class.getResourceAsStream("/db/migration/V78__extensible_permission_key_guards.sql")) {
            String migration = new String(input.readAllBytes(), StandardCharsets.UTF_8);
            assertTrue(migration.contains("DROP CONSTRAINT IF EXISTS chk_island_permissions_key_known"));
            assertTrue(migration.contains("DROP CONSTRAINT IF EXISTS chk_island_permission_override_key_known"));
            assertEquals(2, occurrences(migration, "permission_key ~ '^[A-Z][A-Z0-9_]{0,63}$'"));
            assertTrue(migration.contains("chk_island_permissions_key_format"));
            assertTrue(migration.contains("chk_island_permission_override_key_format"));
        }
    }

    private int occurrences(String text, String expected) {
        return (text.length() - text.replace(expected, "").length()) / expected.length();
    }

    @Test
    void heartbeatMetadataMigrationValidatesOnlyTheTemplatePrefixAsCsv() throws IOException {
        try (var input = JdbcSchemaBootstrapTest.class.getResourceAsStream("/db/migration/V76__server_node_heartbeat_metadata.sql")) {
            String migration = new String(input.readAllBytes(), StandardCharsets.UTF_8);
            assertTrue(migration.contains("split_part(supported_templates, ';', 1)"));
            assertTrue(migration.contains("chk_server_nodes_heartbeat_metadata_shape"));
        }
    }
}
