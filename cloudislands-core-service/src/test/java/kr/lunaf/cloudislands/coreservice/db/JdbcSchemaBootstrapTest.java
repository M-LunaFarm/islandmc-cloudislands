package kr.lunaf.cloudislands.coreservice.db;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertTrue;

import java.io.IOException;
import java.nio.charset.StandardCharsets;
import java.sql.SQLException;
import org.junit.jupiter.api.Test;

class JdbcSchemaBootstrapTest {
    @Test
    void classifiesSupportedCoreJdbcBootstrapProducts() {
        assertEquals("POSTGRESQL,MYSQL,MARIADB", JdbcSchemaBootstrap.CORE_JDBC_BOOTSTRAP_PRODUCTS);
        assertEquals(
            "database-session-advisory-lock-serializes-the-complete-schema-chain-with-bounded-acquisition",
            JdbcSchemaBootstrap.HA_MIGRATION_LOCK_POLICY
        );
        assertEquals(
            "sha256-history-rejects-modified-applied-migrations-with-legacy-trust-on-first-verification",
            JdbcSchemaBootstrap.MIGRATION_CHECKSUM_POLICY
        );
        assertEquals("POSTGRESQL", JdbcSchemaBootstrap.databaseProductFamily("PostgreSQL"));
        assertEquals("MYSQL", JdbcSchemaBootstrap.databaseProductFamily("MySQL"));
        assertEquals("MARIADB", JdbcSchemaBootstrap.databaseProductFamily("MariaDB Server"));
        assertEquals("UNSUPPORTED", JdbcSchemaBootstrap.databaseProductFamily("Microsoft SQL Server"));
    }

    @Test
    void migrationChecksumsAreStableSha256Values() {
        assertEquals(
            "ba7816bf8f01cfea414140de5dae2223b00361a396177a9cb410ff61f20015ad",
            JdbcSchemaBootstrap.checksum("abc")
        );
        assertEquals(64, JdbcSchemaBootstrap.checksum("").length());
        assertEquals(JdbcSchemaBootstrap.checksum(""), JdbcSchemaBootstrap.checksum(null));
        assertFalse(JdbcSchemaBootstrap.checksum("V1").equals(JdbcSchemaBootstrap.checksum("V1 ")));
    }

    @Test
    void keepsMariaDbOnTheMysqlCompatibleBootstrapPath() {
        assertEquals("mariadb-uses-mysql-compatible-core-schema-bootstrap", JdbcSchemaBootstrap.MARIADB_SCHEMA_POLICY);
        assertEquals("/db/mysql/V1__cloudislands_mysql_schema.sql", JdbcSchemaBootstrap.MYSQL_COMPATIBLE_SCHEMA_RESOURCE);
        assertEquals("mysql-v1", JdbcSchemaBootstrap.MYSQL_COMPATIBLE_SCHEMA_ID);
        assertEquals("mysql-compatible-migration-chain:10", JdbcSchemaBootstrap.schemaResourceForProduct("MariaDB Server"));
        assertEquals("mysql-compatible-migration-chain:10", JdbcSchemaBootstrap.schemaResourceForProduct("MySQL"));
        assertEquals("cloudislands:core-schema-bootstrap:v1", JdbcSchemaBootstrap.MYSQL_MIGRATION_LOCK_NAME);
        assertEquals(60, JdbcSchemaBootstrap.MIGRATION_LOCK_TIMEOUT_SECONDS);
    }

    @Test
    void exposesPostgresqlChainAndRejectsUnsupportedProducts() {
        assertEquals("postgresql-migration-chain:86", JdbcSchemaBootstrap.schemaResourceForProduct("PostgreSQL"));
        assertEquals("", JdbcSchemaBootstrap.schemaResourceForProduct("SQLite"));
        assertTrue(JdbcSchemaBootstrap.POSTGRESQL_MIGRATION_LOCK_KEY > 0L);
    }

    @Test
    void idempotencyReceiptSchemaShipsForPostgresqlAndMysql() throws IOException {
        for (String resource : new String[]{"/db/migration/V81__core_idempotency.sql", "/db/mysql/V4__core_idempotency.sql"}) {
            try (var input = JdbcSchemaBootstrapTest.class.getResourceAsStream(resource)) {
                assertTrue(input != null, "missing migration " + resource);
                String migration = new String(input.readAllBytes(), StandardCharsets.UTF_8);
                assertTrue(migration.contains("CREATE TABLE IF NOT EXISTS core_idempotency"));
                assertTrue(migration.contains("idempotency_key VARCHAR(200) PRIMARY KEY"));
                assertTrue(migration.contains("request_fingerprint CHAR(64) NOT NULL"));
                assertTrue(migration.contains("response_body"));
            }
        }
    }

    @Test
    void warehouseSettlementSchemaShipsForPostgresqlAndMysql() throws IOException {
        for (String resource : new String[]{"/db/migration/V82__warehouse_settlement_recovery.sql", "/db/mysql/V5__warehouse_settlement_recovery.sql"}) {
            try (var input = JdbcSchemaBootstrapTest.class.getResourceAsStream(resource)) {
                assertTrue(input != null, "missing migration " + resource);
                String migration = new String(input.readAllBytes(), StandardCharsets.UTF_8);
                assertTrue(migration.contains("warehouse_settlements"));
                assertTrue(migration.contains("player_uuid"));
                assertTrue(migration.contains("settlement_id"));
                assertTrue(migration.contains("idempotency_key VARCHAR(200) NOT NULL"));
                assertTrue(migration.contains("'PREPARED', 'ESCROWED'"));
            }
        }
    }

    @Test
    void personalIslandFlightPreferenceShipsForPostgresqlAndMysql() throws IOException {
        for (String resource : new String[]{"/db/migration/V83__player_island_fly_preference.sql", "/db/mysql/V6__player_island_fly_preference.sql", "/db/mysql/V9__repair_player_island_fly_preference.sql"}) {
            try (var input = JdbcSchemaBootstrapTest.class.getResourceAsStream(resource)) {
                assertTrue(input != null, "missing migration " + resource);
                String migration = new String(input.readAllBytes(), StandardCharsets.UTF_8);
                assertTrue(migration.contains("ALTER TABLE player_profiles"));
                assertTrue(migration.contains("island_fly_enabled BOOLEAN NOT NULL DEFAULT FALSE"));
            }
        }
    }

    @Test
    void mysqlSyntaxErrorsMentioningExistsAreNeverTreatedAsDuplicateObjects() {
        SQLException syntax = new SQLException("syntax error near 'IF NOT EXISTS island_fly_enabled'", "42000", 1064);
        SQLException duplicateColumn = new SQLException("Duplicate column name 'island_fly_enabled'", "42S21", 1060);

        assertFalse(JdbcSchemaBootstrap.ignorableDuplicateSchemaObject(syntax));
        assertTrue(JdbcSchemaBootstrap.ignorableDuplicateSchemaObject(duplicateColumn));
    }

    @Test
    void personalVisualPreferencesShipForPostgresqlAndMysql() throws IOException {
        for (String resource : new String[]{"/db/migration/V84__player_visual_preferences.sql", "/db/mysql/V7__player_visual_preferences.sql"}) {
            try (var input = JdbcSchemaBootstrapTest.class.getResourceAsStream(resource)) {
                assertTrue(input != null, "missing migration " + resource);
                String migration = new String(input.readAllBytes(), StandardCharsets.UTF_8);
                assertTrue(migration.contains("world_border_enabled BOOLEAN NOT NULL DEFAULT TRUE"));
                assertTrue(migration.contains("blocks_stacker_enabled BOOLEAN NOT NULL DEFAULT TRUE"));
            }
        }
    }

    @Test
    void personalBorderColorShipsForPostgresqlAndMysql() throws IOException {
        for (String resource : new String[]{"/db/migration/V85__player_border_color.sql", "/db/mysql/V8__player_border_color.sql"}) {
            try (var input = JdbcSchemaBootstrapTest.class.getResourceAsStream(resource)) {
                assertTrue(input != null, "missing migration " + resource);
                String migration = new String(input.readAllBytes(), StandardCharsets.UTF_8);
                assertTrue(migration.contains("border_color VARCHAR(16) NOT NULL DEFAULT 'blue'"));
                assertTrue(migration.contains("border_color IN ('blue', 'green', 'red')"));
            }
        }
    }

    @Test
    void missionKindParticipatesInEveryPersistentIdentityAndUpsert() throws IOException {
        for (String resource : new String[]{
            "/db/migration/V86__mission_kind_identity.sql",
            "/db/mysql/V10__mission_kind_identity.sql",
            "/db/mysql/V1__cloudislands_mysql_schema.sql"
        }) {
            try (var input = JdbcSchemaBootstrapTest.class.getResourceAsStream(resource)) {
                assertTrue(input != null, "missing migration " + resource);
                String migration = new String(input.readAllBytes(), StandardCharsets.UTF_8);
                assertTrue(migration.contains("PRIMARY KEY (island_id, mission_key, kind)"));
                assertTrue(migration.contains("PRIMARY KEY (mission_key, kind)"));
                if (resource.contains("mysql")) {
                    assertTrue(migration.contains("category VARCHAR(64) NOT NULL DEFAULT 'general'"));
                    assertTrue(migration.contains("trigger_type VARCHAR(64) NOT NULL DEFAULT ''"));
                    assertTrue(migration.contains("daily_reset BOOLEAN NOT NULL DEFAULT false"));
                }
            }
        }
        String repository = java.nio.file.Files.readString(java.nio.file.Path.of(
            "src/main/java/kr/lunaf/cloudislands/coreservice/mission/JdbcIslandMissionRepository.java"
        ));
        assertTrue(repository.contains("ON CONFLICT (island_id, mission_key, kind)"));
        assertTrue(repository.contains("ON CONFLICT (mission_key, kind)"));
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
