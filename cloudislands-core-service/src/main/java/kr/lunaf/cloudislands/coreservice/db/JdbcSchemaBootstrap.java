package kr.lunaf.cloudislands.coreservice.db;

import java.io.IOException;
import java.io.InputStream;
import java.nio.charset.StandardCharsets;
import java.sql.Connection;
import java.sql.PreparedStatement;
import java.sql.ResultSet;
import java.sql.SQLException;
import java.sql.Statement;
import java.util.ArrayList;
import java.util.List;
import java.util.Locale;
import javax.sql.DataSource;

public final class JdbcSchemaBootstrap {
    public static final String CORE_JDBC_BOOTSTRAP_PRODUCTS = "POSTGRESQL,MYSQL,MARIADB";
    public static final String HA_MIGRATION_LOCK_POLICY = "database-session-advisory-lock-serializes-the-complete-schema-chain-with-bounded-acquisition";
    public static final String MYSQL_COMPATIBLE_SCHEMA_RESOURCE = "/db/mysql/V1__cloudislands_mysql_schema.sql";
    public static final String MYSQL_COMPATIBLE_SCHEMA_ID = "mysql-v1";
    public static final String MARIADB_SCHEMA_POLICY = "mariadb-uses-mysql-compatible-core-schema-bootstrap";
    static final long POSTGRESQL_MIGRATION_LOCK_KEY = 4_851_869_284_768_978_252L;
    static final String MYSQL_MIGRATION_LOCK_NAME = "cloudislands:core-schema-bootstrap:v1";
    static final int MIGRATION_LOCK_TIMEOUT_SECONDS = 60;
    private static final long POSTGRESQL_LOCK_RETRY_MILLIS = 100L;
    private static final String[] POSTGRESQL_MIGRATIONS = {
        "/db/migration/V1__cloudislands_schema.sql",
        "/db/migration/V2__island_bank.sql",
        "/db/migration/V3__island_homes.sql",
        "/db/migration/V4__island_biomes.sql",
        "/db/migration/V5__island_missions.sql",
        "/db/migration/V6__island_limits.sql",
        "/db/migration/V7__node_storage_health.sql",
        "/db/migration/V8__node_supported_templates.sql",
        "/db/migration/V9__island_templates.sql",
        "/db/migration/V10__node_version.sql",
        "/db/migration/V11__node_routing_pressure.sql",
        "/db/migration/V12__addon_state.sql",
        "/db/migration/V13__addon_state_limits.sql",
        "/db/migration/V14__addon_island_state.sql",
        "/db/migration/V15__addon_state_bulk_limits.sql",
        "/db/migration/V16__superior_template_input_only.sql",
        "/db/migration/V17__island_runtime_placement_guard.sql",
        "/db/migration/V18__route_job_operational_indexes.sql",
        "/db/migration/V19__server_node_identity_guard.sql",
        "/db/migration/V20__island_runtime_active_state_guard.sql",
        "/db/migration/V21__route_ticket_ready_target_guard.sql",
        "/db/migration/V22__route_ticket_consumed_guard.sql",
        "/db/migration/V23__route_ticket_player_active_guard.sql",
        "/db/migration/V24__route_ticket_nonce_guard.sql",
        "/db/migration/V25__public_island_lookup_indexes.sql",
        "/db/migration/V26__island_name_lookup_guard.sql",
        "/db/migration/V27__island_invite_pending_guard.sql",
        "/db/migration/V28__island_member_role_guard.sql",
        "/db/migration/V29__island_role_catalog_guard.sql",
        "/db/migration/V30__island_permission_flag_key_guard.sql",
        "/db/migration/V31__island_home_warp_name_guard.sql",
        "/db/migration/V32__island_snapshot_record_guard.sql",
        "/db/migration/V33__island_job_queue_guard.sql",
        "/db/migration/V34__server_node_capacity_guard.sql",
        "/db/migration/V35__server_node_routing_pressure_guard.sql",
        "/db/migration/V36__addon_state_table_key_guard.sql",
        "/db/migration/V37__island_snapshot_checksum_guard.sql",
        "/db/migration/V38__island_runtime_fencing_guard.sql",
        "/db/migration/V39__route_ticket_value_guard.sql",
        "/db/migration/V40__island_job_value_guard.sql",
        "/db/migration/V41__island_template_value_guard.sql",
        "/db/migration/V42__server_node_template_guard.sql",
        "/db/migration/V43__island_row_value_guard.sql",
        "/db/migration/V44__player_profile_value_guard.sql",
        "/db/migration/V45__island_member_invite_value_guard.sql",
        "/db/migration/V46__island_progression_value_guard.sql",
        "/db/migration/V47__island_location_value_guard.sql",
        "/db/migration/V48__island_ranking_log_value_guard.sql",
        "/db/migration/V49__migration_run_value_guard.sql",
        "/db/migration/V50__island_permission_value_guard.sql",
        "/db/migration/V51__island_ban_value_guard.sql",
        "/db/migration/V52__island_member_custom_role_guard.sql",
        "/db/migration/V53__island_role_catalog_member_guard.sql",
        "/db/migration/V54__route_ticket_node_failure_index.sql",
        "/db/migration/V55__player_profile_locale.sql",
        "/db/migration/V56__island_warp_category.sql",
        "/db/migration/V57__island_reviews.sql",
        "/db/migration/V58__island_warehouse.sql",
        "/db/migration/V59__temporary_trust_expiry.sql",
        "/db/migration/V60__island_permission_overrides.sql",
        "/db/migration/V61__mission_provider_definitions.sql",
        "/db/migration/V62__dynamic_role_keys.sql",
        "/db/migration/V63__dynamic_member_role_keys.sql",
        "/db/migration/V64__job_claim_leases.sql",
        "/db/migration/V65__job_completion_receipts.sql",
        "/db/migration/V66__job_completion_receipt_queue_mode.sql",
        "/db/migration/V67__completion_event_outbox.sql",
        "/db/migration/V68__mission_definition_metadata.sql",
        "/db/migration/V69__generator_profiles_and_rules.sql",
        "/db/migration/V70__review_moderation.sql",
        "/db/migration/V71__island_snapshot_node_id.sql",
        "/db/migration/V72__island_rank_ignored.sql",
        "/db/migration/V73__template_bundle_metadata.sql",
        "/db/migration/V74__island_warp_world_name.sql",
        "/db/migration/V75__player_disband_quota.sql",
        "/db/migration/V76__server_node_heartbeat_metadata.sql",
        "/db/migration/V77__island_permission_key_expansion.sql",
        "/db/migration/V78__extensible_permission_key_guards.sql",
        "/db/migration/V79__island_ranking_dirty_queue.sql",
        "/db/migration/V80__island_bank_ranking_index.sql",
        "/db/migration/V81__core_idempotency.sql",
        "/db/migration/V82__warehouse_settlement_recovery.sql",
        "/db/migration/V83__player_island_fly_preference.sql",
        "/db/migration/V84__player_visual_preferences.sql",
        "/db/migration/V85__player_border_color.sql"
    };
    private static final String[] MYSQL_MIGRATIONS = {
        MYSQL_COMPATIBLE_SCHEMA_RESOURCE,
        "/db/mysql/V2__island_ranking_dirty_queue.sql",
        "/db/mysql/V3__island_bank_ranking_index.sql",
        "/db/mysql/V4__core_idempotency.sql",
        "/db/mysql/V5__warehouse_settlement_recovery.sql",
        "/db/mysql/V6__player_island_fly_preference.sql",
        "/db/mysql/V7__player_visual_preferences.sql",
        "/db/mysql/V8__player_border_color.sql",
        "/db/mysql/V9__repair_player_island_fly_preference.sql"
    };

    private enum Dialect {
        POSTGRESQL,
        MYSQL
    }

    private JdbcSchemaBootstrap() {
    }

    public static boolean apply(DataSource dataSource) {
        try (Connection connection = dataSource.getConnection()) {
            String productFamily = databaseProductFamily(connection.getMetaData().getDatabaseProductName());
            if ("MYSQL".equals(productFamily) || "MARIADB".equals(productFamily)) {
                SchemaLock schemaLock = acquireSchemaLock(connection, Dialect.MYSQL);
                try (schemaLock) {
                    boolean applied = apply(connection, Dialect.MYSQL, MYSQL_COMPATIBLE_SCHEMA_ID, MYSQL_COMPATIBLE_SCHEMA_RESOURCE);
                    for (int index = 1; index < MYSQL_MIGRATIONS.length; index++) {
                        applied |= apply(connection, Dialect.MYSQL, migrationId(MYSQL_MIGRATIONS[index]), MYSQL_MIGRATIONS[index]);
                    }
                    return applied;
                }
            }
            if ("POSTGRESQL".equals(productFamily)) {
                SchemaLock schemaLock = acquireSchemaLock(connection, Dialect.POSTGRESQL);
                try (schemaLock) {
                    return applyAll(connection, Dialect.POSTGRESQL, POSTGRESQL_MIGRATIONS);
                }
            }
            return false;
        } catch (SQLException | IOException exception) {
            throw new IllegalStateException("failed to bootstrap database schema", exception);
        }
    }

    public static String databaseProductFamily(String productName) {
        String value = productName == null ? "" : productName.toLowerCase(Locale.ROOT);
        if (value.contains("mariadb")) {
            return "MARIADB";
        }
        if (value.contains("mysql")) {
            return "MYSQL";
        }
        if (value.contains("postgresql")) {
            return "POSTGRESQL";
        }
        return "UNSUPPORTED";
    }

    public static String schemaResourceForProduct(String productName) {
        String productFamily = databaseProductFamily(productName);
        if ("MYSQL".equals(productFamily) || "MARIADB".equals(productFamily)) {
            return "mysql-compatible-migration-chain:" + MYSQL_MIGRATIONS.length;
        }
        if ("POSTGRESQL".equals(productFamily)) {
            return "postgresql-migration-chain:" + POSTGRESQL_MIGRATIONS.length;
        }
        return "";
    }

    private static boolean applyAll(Connection connection, Dialect dialect, String[] resources) throws SQLException, IOException {
        boolean applied = false;
        for (String resource : resources) {
            applied |= apply(connection, dialect, migrationId(resource), resource);
        }
        return applied;
    }

    private static SchemaLock acquireSchemaLock(Connection connection, Dialect dialect) throws SQLException {
        if (dialect == Dialect.MYSQL) {
            try (PreparedStatement statement = connection.prepareStatement("SELECT GET_LOCK(?, ?)")) {
                statement.setString(1, MYSQL_MIGRATION_LOCK_NAME);
                statement.setInt(2, MIGRATION_LOCK_TIMEOUT_SECONDS);
                try (ResultSet result = statement.executeQuery()) {
                    if (!result.next() || result.getInt(1) != 1 || result.wasNull()) {
                        throw new SQLException("timed out acquiring MySQL/MariaDB schema migration lock");
                    }
                }
            }
            return () -> releaseMysqlLock(connection);
        }

        long deadline = System.nanoTime() + MIGRATION_LOCK_TIMEOUT_SECONDS * 1_000_000_000L;
        try (PreparedStatement statement = connection.prepareStatement("SELECT pg_try_advisory_lock(?)")) {
            statement.setLong(1, POSTGRESQL_MIGRATION_LOCK_KEY);
            while (true) {
                try (ResultSet result = statement.executeQuery()) {
                    if (result.next() && result.getBoolean(1)) {
                        return () -> releasePostgresqlLock(connection);
                    }
                }
                if (System.nanoTime() >= deadline) {
                    throw new SQLException("timed out acquiring PostgreSQL schema migration lock");
                }
                try {
                    Thread.sleep(POSTGRESQL_LOCK_RETRY_MILLIS);
                } catch (InterruptedException exception) {
                    Thread.currentThread().interrupt();
                    throw new SQLException("interrupted while acquiring PostgreSQL schema migration lock", exception);
                }
            }
        }
    }

    private static void releasePostgresqlLock(Connection connection) throws SQLException {
        try (PreparedStatement statement = connection.prepareStatement("SELECT pg_advisory_unlock(?)")) {
            statement.setLong(1, POSTGRESQL_MIGRATION_LOCK_KEY);
            try (ResultSet result = statement.executeQuery()) {
                if (!result.next() || !result.getBoolean(1)) {
                    throw new SQLException("PostgreSQL schema migration lock was not owned by this session");
                }
            }
        }
    }

    private static void releaseMysqlLock(Connection connection) throws SQLException {
        try (PreparedStatement statement = connection.prepareStatement("SELECT RELEASE_LOCK(?)")) {
            statement.setString(1, MYSQL_MIGRATION_LOCK_NAME);
            try (ResultSet result = statement.executeQuery()) {
                if (!result.next() || result.getInt(1) != 1 || result.wasNull()) {
                    throw new SQLException("MySQL/MariaDB schema migration lock was not owned by this session");
                }
            }
        }
    }

    private static boolean apply(Connection connection, Dialect dialect, String id, String resource) throws SQLException, IOException {
        ensureHistory(connection, dialect);
        if (alreadyApplied(connection, id)) {
            return false;
        }
        for (String statementSql : statements(readResource(resource))) {
            try (Statement statement = connection.createStatement()) {
                statement.execute(statementSql);
            } catch (SQLException exception) {
                if (!ignorableDuplicateSchemaObject(exception)) {
                    throw exception;
                }
            }
        }
        markApplied(connection, dialect, id);
        return true;
    }

    private static void ensureHistory(Connection connection, Dialect dialect) throws SQLException {
        String ddl = dialect == Dialect.POSTGRESQL
            ? "CREATE TABLE IF NOT EXISTS cloudislands_schema_bootstrap (id VARCHAR(128) PRIMARY KEY, applied_at TIMESTAMPTZ NOT NULL DEFAULT now())"
            : "CREATE TABLE IF NOT EXISTS cloudislands_schema_bootstrap (id VARCHAR(128) PRIMARY KEY, applied_at DATETIME(6) NOT NULL DEFAULT CURRENT_TIMESTAMP(6))";
        try (Statement statement = connection.createStatement()) {
            statement.execute(ddl);
        }
    }

    private static boolean alreadyApplied(Connection connection, String id) throws SQLException {
        try (PreparedStatement statement = connection.prepareStatement("SELECT id FROM cloudislands_schema_bootstrap WHERE id = ?")) {
            statement.setString(1, id);
            try (ResultSet rs = statement.executeQuery()) {
                return rs.next();
            }
        }
    }

    private static void markApplied(Connection connection, Dialect dialect, String id) throws SQLException {
        String sql = dialect == Dialect.POSTGRESQL
            ? "INSERT INTO cloudislands_schema_bootstrap(id) VALUES (?) ON CONFLICT (id) DO NOTHING"
            : "INSERT IGNORE INTO cloudislands_schema_bootstrap(id) VALUES (?)";
        try (PreparedStatement statement = connection.prepareStatement(sql)) {
            statement.setString(1, id);
            statement.executeUpdate();
        }
    }

    static boolean ignorableDuplicateSchemaObject(SQLException exception) {
        int code = exception.getErrorCode();
        if (code == 1050 || code == 1060 || code == 1061 || code == 1826 || code == 3822) {
            return true;
        }
        String state = exception.getSQLState();
        if ("42P07".equals(state) || "42710".equals(state) || "42701".equals(state) || "23505".equals(state)) {
            return true;
        }
        String message = exception.getMessage() == null ? "" : exception.getMessage().toLowerCase();
        return "42S01".equals(state)
            && (message.contains("already exists")
            || message.contains("duplicate")
            || message.contains("exists"));
    }

    private static String readResource(String resource) throws IOException {
        try (InputStream input = JdbcSchemaBootstrap.class.getResourceAsStream(resource)) {
            if (input == null) {
                throw new IOException("schema resource not found: " + resource);
            }
            return new String(input.readAllBytes(), StandardCharsets.UTF_8);
        }
    }

    private static String migrationId(String resource) {
        int slash = resource.lastIndexOf('/');
        String fileName = slash < 0 ? resource : resource.substring(slash + 1);
        return fileName.endsWith(".sql") ? fileName.substring(0, fileName.length() - 4) : fileName;
    }

    private static List<String> statements(String sql) {
        List<String> result = new ArrayList<>();
        StringBuilder current = new StringBuilder();
        boolean quoted = false;
        for (int i = 0; i < sql.length(); i++) {
            char c = sql.charAt(i);
            if (c == '\'') {
                quoted = !quoted;
            }
            if (c == ';' && !quoted) {
                add(result, current);
            } else {
                current.append(c);
            }
        }
        add(result, current);
        return result;
    }

    private static void add(List<String> result, StringBuilder current) {
        String statement = current.toString().trim();
        current.setLength(0);
        if (!statement.isBlank()) {
            result.add(statement);
        }
    }

    @FunctionalInterface
    private interface SchemaLock extends AutoCloseable {
        @Override
        void close() throws SQLException;
    }
}
