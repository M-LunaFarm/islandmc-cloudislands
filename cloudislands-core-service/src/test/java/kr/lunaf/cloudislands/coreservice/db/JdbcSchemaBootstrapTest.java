package kr.lunaf.cloudislands.coreservice.db;

import static org.junit.jupiter.api.Assertions.assertEquals;

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
        assertEquals("postgresql-migration-chain:62", JdbcSchemaBootstrap.schemaResourceForProduct("PostgreSQL"));
        assertEquals("", JdbcSchemaBootstrap.schemaResourceForProduct("SQLite"));
    }
}
