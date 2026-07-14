package kr.lunaf.cloudislands.coreservice.db;

import java.sql.Connection;
import java.sql.DatabaseMetaData;
import java.sql.ResultSet;
import java.sql.SQLException;
import java.sql.Types;
import java.util.ArrayList;
import java.util.List;
import java.util.Locale;
import javax.sql.DataSource;

/** Fail-fast contract for columns that participate in island creation and activation. */
public final class JdbcSchemaContract {
    public static final String POLICY = "startup-validates-critical-island-column-types-even-when-auto-schema-is-disabled";

    private static final List<RequiredColumn> REQUIRED_COLUMNS = List.of(
        new RequiredColumn("island_templates", "id", ColumnKind.TEXT),
        new RequiredColumn("island_templates", "enabled", ColumnKind.BOOLEAN),
        new RequiredColumn("island_templates", "schema_version", ColumnKind.INTEGER),
        new RequiredColumn("island_templates", "default_island_size", ColumnKind.INTEGER),
        new RequiredColumn("island_templates", "environment_preset", ColumnKind.TEXT),
        new RequiredColumn("islands", "template_id", ColumnKind.TEXT),
        new RequiredColumn("islands", "state", ColumnKind.TEXT),
        new RequiredColumn("islands", "size", ColumnKind.INTEGER),
        new RequiredColumn("islands", "worth", ColumnKind.DECIMAL),
        new RequiredColumn("islands", "public_access", ColumnKind.BOOLEAN),
        new RequiredColumn("island_runtime", "state", ColumnKind.TEXT),
        new RequiredColumn("island_runtime", "cell_x", ColumnKind.INTEGER),
        new RequiredColumn("island_runtime", "fencing_token", ColumnKind.BIGINT)
    );

    private JdbcSchemaContract() {
    }

    public static int validate(DataSource dataSource) {
        try (Connection connection = dataSource.getConnection()) {
            String product = JdbcSchemaBootstrap.databaseProductFamily(connection.getMetaData().getDatabaseProductName());
            if ("UNSUPPORTED".equals(product)) {
                throw new IllegalStateException("unsupported database product for schema validation");
            }
            List<String> violations = violations(connection);
            if (!violations.isEmpty()) {
                throw new IllegalStateException(
                    "database schema contract mismatch; repair the listed columns before startup: " + String.join("; ", violations)
                );
            }
            return REQUIRED_COLUMNS.size();
        } catch (SQLException exception) {
            throw new IllegalStateException("failed to validate database schema contract", exception);
        }
    }

    static List<String> violations(Connection connection) throws SQLException {
        DatabaseMetaData metadata = connection.getMetaData();
        String catalog = connection.getCatalog();
        String schema = schema(connection);
        List<String> violations = new ArrayList<>();
        for (RequiredColumn required : REQUIRED_COLUMNS) {
            ColumnMetadata actual = find(metadata, catalog, schema, required.table(), required.column());
            if (actual == null && (catalog != null || schema != null)) {
                actual = find(metadata, null, null, required.table(), required.column());
            }
            String qualified = required.table() + "." + required.column();
            if (actual == null) {
                violations.add(qualified + " missing (expected " + required.kind().label + ")");
            } else if (!required.kind().accepts(actual.jdbcType())) {
                violations.add(qualified + " is " + actual.typeName() + " (expected " + required.kind().label + ")");
            }
        }
        return List.copyOf(violations);
    }

    static boolean compatible(String kind, int jdbcType) {
        return ColumnKind.valueOf(kind.toUpperCase(Locale.ROOT)).accepts(jdbcType);
    }

    static int requiredColumnCount() {
        return REQUIRED_COLUMNS.size();
    }

    private static String schema(Connection connection) {
        try {
            return connection.getSchema();
        } catch (SQLException | AbstractMethodError | UnsupportedOperationException ignored) {
            return null;
        }
    }

    private static ColumnMetadata find(
        DatabaseMetaData metadata,
        String catalog,
        String schema,
        String table,
        String column
    ) throws SQLException {
        ColumnMetadata exact = findCase(metadata, catalog, schema, table, column);
        return exact == null ? findCase(metadata, catalog, schema, table.toUpperCase(Locale.ROOT), column.toUpperCase(Locale.ROOT)) : exact;
    }

    private static ColumnMetadata findCase(
        DatabaseMetaData metadata,
        String catalog,
        String schema,
        String table,
        String column
    ) throws SQLException {
        try (ResultSet columns = metadata.getColumns(catalog, schema, table, column)) {
            while (columns.next()) {
                if (table.equalsIgnoreCase(columns.getString("TABLE_NAME"))
                    && column.equalsIgnoreCase(columns.getString("COLUMN_NAME"))) {
                    return new ColumnMetadata(columns.getInt("DATA_TYPE"), columns.getString("TYPE_NAME"));
                }
            }
        }
        return null;
    }

    private enum ColumnKind {
        TEXT("text") {
            @Override boolean accepts(int type) {
                return type == Types.CHAR || type == Types.VARCHAR || type == Types.LONGVARCHAR
                    || type == Types.NCHAR || type == Types.NVARCHAR || type == Types.LONGNVARCHAR;
            }
        },
        BOOLEAN("boolean") {
            @Override boolean accepts(int type) {
                return type == Types.BOOLEAN || type == Types.BIT || type == Types.TINYINT;
            }
        },
        INTEGER("integer") {
            @Override boolean accepts(int type) {
                return type == Types.TINYINT || type == Types.SMALLINT || type == Types.INTEGER;
            }
        },
        BIGINT("bigint") {
            @Override boolean accepts(int type) {
                return type == Types.BIGINT;
            }
        },
        DECIMAL("decimal") {
            @Override boolean accepts(int type) {
                return type == Types.DECIMAL || type == Types.NUMERIC;
            }
        };

        private final String label;

        ColumnKind(String label) {
            this.label = label;
        }

        abstract boolean accepts(int type);
    }

    private record RequiredColumn(String table, String column, ColumnKind kind) {
    }

    private record ColumnMetadata(int jdbcType, String typeName) {
        private ColumnMetadata {
            typeName = typeName == null || typeName.isBlank() ? "JDBC type " + jdbcType : typeName;
        }
    }
}
