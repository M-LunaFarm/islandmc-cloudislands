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
    public static final String MYSQL_COMPATIBLE_SCHEMA_RESOURCE = "/db/mysql/V1__cloudislands_mysql_schema.sql";
    public static final String MYSQL_COMPATIBLE_SCHEMA_ID = "mysql-v1";
    public static final String MARIADB_SCHEMA_POLICY = "mariadb-uses-mysql-compatible-core-schema-bootstrap";
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
        "/db/migration/V55__player_profile_locale.sql"
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
                return apply(connection, Dialect.MYSQL, MYSQL_COMPATIBLE_SCHEMA_ID, MYSQL_COMPATIBLE_SCHEMA_RESOURCE);
            }
            if ("POSTGRESQL".equals(productFamily)) {
                return applyAll(connection, Dialect.POSTGRESQL, POSTGRESQL_MIGRATIONS);
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
            return MYSQL_COMPATIBLE_SCHEMA_RESOURCE;
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

    private static boolean ignorableDuplicateSchemaObject(SQLException exception) {
        int code = exception.getErrorCode();
        if (code == 1050 || code == 1060 || code == 1061 || code == 1826 || code == 3822) {
            return true;
        }
        String state = exception.getSQLState();
        if ("42P07".equals(state) || "42710".equals(state) || "42701".equals(state) || "23505".equals(state)) {
            return true;
        }
        String message = exception.getMessage() == null ? "" : exception.getMessage().toLowerCase();
        return ("42S01".equals(state) || "42000".equals(state))
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
}
