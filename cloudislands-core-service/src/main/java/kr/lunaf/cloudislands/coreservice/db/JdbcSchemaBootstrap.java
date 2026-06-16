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
    private JdbcSchemaBootstrap() {
    }

    public static boolean apply(DataSource dataSource) {
        try (Connection connection = dataSource.getConnection()) {
            String productName = connection.getMetaData().getDatabaseProductName().toLowerCase(Locale.ROOT);
            if (productName.contains("mysql") || productName.contains("mariadb")) {
                return apply(connection, "mysql-v1", "/db/mysql/V1__cloudislands_mysql_schema.sql");
            }
            return false;
        } catch (SQLException | IOException exception) {
            throw new IllegalStateException("failed to bootstrap database schema", exception);
        }
    }

    private static boolean apply(Connection connection, String id, String resource) throws SQLException, IOException {
        ensureHistory(connection);
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
        markApplied(connection, id);
        return true;
    }

    private static void ensureHistory(Connection connection) throws SQLException {
        try (Statement statement = connection.createStatement()) {
            statement.execute("CREATE TABLE IF NOT EXISTS cloudislands_schema_bootstrap (id VARCHAR(128) PRIMARY KEY, applied_at DATETIME(6) NOT NULL DEFAULT CURRENT_TIMESTAMP(6))");
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

    private static void markApplied(Connection connection, String id) throws SQLException {
        try (PreparedStatement statement = connection.prepareStatement("INSERT IGNORE INTO cloudislands_schema_bootstrap(id) VALUES (?)")) {
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
