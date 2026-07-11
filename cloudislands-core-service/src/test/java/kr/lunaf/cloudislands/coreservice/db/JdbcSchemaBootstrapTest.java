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
        assertEquals(
            JdbcSchemaBootstrap.MYSQL_COMPATIBLE_SCHEMA_RESOURCE,
            JdbcSchemaBootstrap.schemaResourceForProduct("MariaDB Server")
        );
        assertEquals(
            JdbcSchemaBootstrap.MYSQL_COMPATIBLE_SCHEMA_RESOURCE,
            JdbcSchemaBootstrap.schemaResourceForProduct("MySQL")
        );
    }

    @Test
    void exposesPostgresqlChainAndRejectsUnsupportedProducts() {
        assertEquals("postgresql-migration-chain:76", JdbcSchemaBootstrap.schemaResourceForProduct("PostgreSQL"));
        assertEquals("", JdbcSchemaBootstrap.schemaResourceForProduct("SQLite"));
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
