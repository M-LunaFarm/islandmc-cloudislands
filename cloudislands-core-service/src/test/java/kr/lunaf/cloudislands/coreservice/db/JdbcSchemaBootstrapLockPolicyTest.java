package kr.lunaf.cloudislands.coreservice.db;

import static org.junit.jupiter.api.Assertions.assertTrue;

import java.nio.file.Files;
import java.nio.file.Path;
import org.junit.jupiter.api.Test;

class JdbcSchemaBootstrapLockPolicyTest {
    @Test
    void supportedDatabasesHoldOneSessionLockAcrossTheCompleteMigrationChain() throws Exception {
        String source = Files.readString(Path.of("src/main/java/kr/lunaf/cloudislands/coreservice/db/JdbcSchemaBootstrap.java"));
        String build = Files.readString(Path.of("build.gradle.kts"));

        assertTrue(source.contains("SchemaLock schemaLock = acquireSchemaLock(connection, Dialect.MYSQL)"));
        assertTrue(source.contains("SchemaLock schemaLock = acquireSchemaLock(connection, Dialect.POSTGRESQL)"));
        assertTrue(source.contains("try (schemaLock)"));
        assertTrue(source.contains("SELECT GET_LOCK(?, ?)"));
        assertTrue(source.contains("SELECT RELEASE_LOCK(?)"));
        assertTrue(source.contains("SELECT pg_try_advisory_lock(?)"));
        assertTrue(source.contains("SELECT pg_advisory_unlock(?)"));
        assertTrue(source.contains("MIGRATION_LOCK_TIMEOUT_SECONDS * 1_000_000_000L"));
        assertTrue(source.contains("Thread.currentThread().interrupt()"));
        assertTrue(source.contains("throw new SQLException(\"timed out acquiring MySQL/MariaDB schema migration lock\")"));
        assertTrue(source.contains("throw new SQLException(\"timed out acquiring PostgreSQL schema migration lock\")"));
        assertTrue(build.contains("CloudIslands-Core-JDBC-Auto-Schema-HA-Lock-Policy"));
        assertTrue(build.contains("postgresql=/db/migration/V1..V85,mysql-mariadb=/db/mysql/V1..V8"));
    }
}
