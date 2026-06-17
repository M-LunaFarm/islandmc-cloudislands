package kr.seungmin.satisskyfactory.database;

import com.zaxxer.hikari.HikariConfig;
import com.zaxxer.hikari.HikariDataSource;
import kr.lunaf.cloudislands.api.service.IslandAddonService;
import kr.seungmin.satisskyfactory.model.BlockKey;
import kr.seungmin.satisskyfactory.model.FactoryIsland;
import kr.seungmin.satisskyfactory.model.ItemNetwork;
import kr.seungmin.satisskyfactory.model.MachineInstance;
import kr.seungmin.satisskyfactory.model.MachineStatus;
import kr.seungmin.satisskyfactory.model.MaintenanceStatus;
import kr.seungmin.satisskyfactory.model.PowerNetwork;
import kr.seungmin.satisskyfactory.model.ResourceNode;
import kr.seungmin.satisskyfactory.storage.MigrationService;
import kr.seungmin.satisskyfactory.storage.VirtualInventory;
import org.bukkit.block.BlockFace;
import org.bukkit.plugin.java.JavaPlugin;
import org.sqlite.SQLiteConfig;

import java.io.File;
import java.sql.Connection;
import java.sql.DriverManager;
import java.sql.PreparedStatement;
import java.sql.ResultSet;
import java.sql.SQLException;
import java.sql.Statement;
import java.time.Instant;
import java.util.ArrayList;
import java.util.LinkedHashSet;
import java.util.HashSet;
import java.util.List;
import java.util.Locale;
import java.util.Optional;
import java.util.Set;
import java.util.UUID;
import java.util.function.Consumer;

public final class DatabaseService {
    public enum StorageBackend {
        SQLITE,
        POSTGRESQL,
        MYSQL,
        MARIADB,
        CORE_API;

        public static StorageBackend parse(String value, StorageBackend fallback) {
            if (value == null || value.isBlank()) {
                return fallback;
            }
            String normalized = value.trim().replace('-', '_').toUpperCase(Locale.ROOT);
            normalized = switch (normalized) {
                case "POSTGRES", "PG" -> "POSTGRESQL";
                case "MARIA" -> "MARIADB";
                case "CORE", "COREAPI", "CLOUDISLANDS", "CLOUDISLANDS_API" -> "CORE_API";
                case "IN_MEMORY", "MEMORY", "LOCAL", "LOCAL_SQLITE" -> "SQLITE";
                default -> normalized;
            };
            try {
                return StorageBackend.valueOf(normalized);
            } catch (IllegalArgumentException ignored) {
                return fallback;
            }
        }
    }

    public record Settings(
            StorageBackend backend,
            String sqliteFileName,
            String jdbcUrl,
            String postgresqlJdbcUrl,
            String mysqlJdbcUrl,
            String mariadbJdbcUrl,
            String username,
            String password,
            int maxPoolSize,
            long connectionTimeoutMillis,
            BackendSettings postgresqlSettings,
            BackendSettings mysqlSettings,
            BackendSettings mariadbSettings,
            boolean fallbackEnabled,
            List<StorageBackend> fallbackOrder
    ) {
        public static Settings sqlite(String sqliteFileName) {
            return new Settings(StorageBackend.SQLITE, sqliteFileName, "", "", "", "", "", "", 4, 5000L, BackendSettings.empty(), BackendSettings.empty(), BackendSettings.empty(), false, List.of());
        }
    }

    public record BackendSettings(String username, String password, int maxPoolSize, long connectionTimeoutMillis) {
        public static BackendSettings empty() {
            return new BackendSettings("", "", 0, 0L);
        }
    }

    private enum SqlDialect {
        SQLITE,
        POSTGRESQL,
        MYSQL
    }

    private final File dataFolder;
    private final Settings settings;
    private HikariDataSource dataSource;
    private StorageBackend activeBackend = StorageBackend.SQLITE;
    private SqlDialect sqlDialect = SqlDialect.SQLITE;
    private String activeDescription = "";
    private String fallbackReason = "none";
    private List<StorageBackend> attemptedBackends = List.of();
    private Consumer<CoreRowWrite> coreStateWriter;
    private Consumer<CoreTableWrite> coreTableWriter;
    private Consumer<CoreGlobalRowWrite> coreGlobalStateWriter;
    private Consumer<CoreGlobalTableWrite> coreGlobalTableWriter;
    private boolean coreStatePublishingSuspended;

    public record CoreRowWrite(UUID islandUuid, String key, String value) {
    }

    public record CoreTableWrite(UUID islandUuid, String table, java.util.Map<String, String> values) {
    }

    public record CoreGlobalRowWrite(String key, String value) {
    }

    public record CoreGlobalTableWrite(String table, java.util.Map<String, String> values) {
    }

    public DatabaseService(JavaPlugin plugin) {
        this(plugin.getDataFolder());
    }

    public DatabaseService(JavaPlugin plugin, String sqliteFileName) {
        this(plugin.getDataFolder(), Settings.sqlite(sqliteFileName));
    }

    public DatabaseService(JavaPlugin plugin, Settings settings) {
        this(plugin.getDataFolder(), settings);
    }

    DatabaseService(File dataFolder) {
        this(dataFolder, "data.db");
    }

    public DatabaseService(File dataFolder, String sqliteFileName) {
        this(dataFolder, Settings.sqlite(sqliteFileName));
    }

    public DatabaseService(File dataFolder, Settings settings) {
        this.dataFolder = dataFolder;
        this.settings = settings == null ? Settings.sqlite("data.db") : settings;
    }

    public void open() {
        if (dataSource != null && !dataSource.isClosed()) {
            return;
        }
        List<StorageBackend> attempts = backendAttempts();
        attemptedBackends = List.copyOf(attempts);
        fallbackReason = "none";
        RuntimeException firstFailure = null;
        List<String> failures = new ArrayList<>();
        for (StorageBackend backend : attempts) {
            try {
                openBackend(backend);
                if (!failures.isEmpty()) {
                    fallbackReason = String.join(",", failures) + "->" + backend.name();
                }
                return;
            } catch (RuntimeException exception) {
                close();
                if (firstFailure == null) {
                    firstFailure = exception;
                }
                failures.add(backend.name() + "_FAILED:" + failureCode(exception));
            }
        }
        fallbackReason = failures.isEmpty() ? "none" : String.join(",", failures);
        throw new IllegalStateException("Failed to open Satis database with backend chain " + attempts, firstFailure);
    }

    private String failureCode(RuntimeException exception) {
        Throwable root = exception;
        while (root.getCause() != null) {
            root = root.getCause();
        }
        String code = root.getClass().getSimpleName();
        String message = root.getMessage();
        if (message == null || message.isBlank()) {
            return code;
        }
        String compact = message.replace('\n', ' ').replace('\r', ' ').trim();
        if (compact.length() > 80) {
            compact = compact.substring(0, 80);
        }
        return code + "(" + compact.replace(',', ';') + ")";
    }

    private List<StorageBackend> backendAttempts() {
        List<StorageBackend> attempts = new ArrayList<>();
        attempts.add(settings.backend() == null ? StorageBackend.SQLITE : settings.backend());
        if (settings.fallbackEnabled()) {
            List<StorageBackend> fallbackOrder = settings.fallbackOrder() == null ? List.of() : settings.fallbackOrder();
            for (StorageBackend backend : fallbackOrder) {
                if (backend != null && !attempts.contains(backend)) {
                    attempts.add(backend);
                }
            }
        }
        return attempts;
    }

    private void openBackend(StorageBackend backend) {
        if (backend == StorageBackend.CORE_API) {
            openSqlite();
            activeBackend = StorageBackend.CORE_API;
            sqlDialect = SqlDialect.SQLITE;
            activeDescription = "cloudislands-addon-state-with-local-sqlite-cache:" + databaseFile().getAbsolutePath();
            return;
        }
        if (backend == StorageBackend.SQLITE) {
            openSqlite();
            activeBackend = StorageBackend.SQLITE;
            sqlDialect = SqlDialect.SQLITE;
            activeDescription = databaseFile().getAbsolutePath();
            return;
        }
        openJdbc(backend);
        activeBackend = backend;
        sqlDialect = backend == StorageBackend.POSTGRESQL ? SqlDialect.POSTGRESQL : SqlDialect.MYSQL;
        activeDescription = safeJdbcDescription(jdbcUrl(backend));
    }

    private void openSqlite() {
        ensureDirectory(dataFolder, "Satis data folder");
        File database = databaseFile();
        File parent = database.getParentFile();
        if (parent != null) {
            ensureDirectory(parent, "Satis database folder");
        }
        if (database.isDirectory()) {
            throw new IllegalStateException("Satis database path points to a directory: " + database.getAbsolutePath());
        }
        SQLiteConfig sqliteConfig = new SQLiteConfig();
        sqliteConfig.enforceForeignKeys(true);
        sqliteConfig.setBusyTimeout(5000);
        sqliteConfig.setJournalMode(SQLiteConfig.JournalMode.WAL);
        sqliteConfig.setSynchronous(SQLiteConfig.SynchronousMode.NORMAL);

        HikariConfig poolConfig = new HikariConfig();
        poolConfig.setJdbcUrl("jdbc:sqlite:" + database.getAbsolutePath());
        poolConfig.setMaximumPoolSize(4);
        poolConfig.setPoolName("SatisSkyFactory");
        poolConfig.setDataSourceProperties(sqliteConfig.toProperties());
        dataSource = new HikariDataSource(poolConfig);
        try (Connection connection = connection()) {
            new MigrationService().migrate(connection, MigrationService.Dialect.SQLITE);
        } catch (SQLException exception) {
            throw new IllegalStateException("Failed to migrate SQLite database", exception);
        }
    }

    private void openJdbc(StorageBackend backend) {
        String jdbcUrl = jdbcUrl(backend);
        if (jdbcUrl.isBlank()) {
            throw new IllegalStateException("Satis " + backend + " backend needs a JDBC URL");
        }
        HikariConfig poolConfig = new HikariConfig();
        poolConfig.setJdbcUrl(jdbcUrl);
        BackendSettings backendSettings = backendSettings(backend);
        String username = firstNonBlank(backendSettings.username(), settings.username());
        String password = firstNonBlank(backendSettings.password(), settings.password());
        int maxPoolSize = backendSettings.maxPoolSize() > 0 ? backendSettings.maxPoolSize() : settings.maxPoolSize();
        long connectionTimeoutMillis = backendSettings.connectionTimeoutMillis() > 0L ? backendSettings.connectionTimeoutMillis() : settings.connectionTimeoutMillis();
        if (username != null && !username.isBlank()) {
            poolConfig.setUsername(username);
        }
        if (password != null && !password.isBlank()) {
            poolConfig.setPassword(password);
        }
        poolConfig.setMaximumPoolSize(Math.max(1, maxPoolSize));
        poolConfig.setConnectionTimeout(Math.max(1000L, connectionTimeoutMillis));
        poolConfig.setPoolName("SatisSkyFactory-" + backend.name());
        dataSource = new HikariDataSource(poolConfig);
        MigrationService.Dialect migrationDialect = switch (backend) {
            case POSTGRESQL -> MigrationService.Dialect.POSTGRESQL;
            case MARIADB -> MigrationService.Dialect.MARIADB;
            case MYSQL -> MigrationService.Dialect.MYSQL;
            default -> throw new IllegalArgumentException("Unsupported JDBC backend: " + backend);
        };
        try (Connection connection = connection()) {
            new MigrationService().migrate(connection, migrationDialect);
        } catch (SQLException exception) {
            throw new IllegalStateException("Failed to migrate " + backend + " database", exception);
        }
    }

    private String jdbcUrl(StorageBackend backend) {
        String configured = settings.jdbcUrl() == null ? "" : settings.jdbcUrl().trim();
        if (!configured.isBlank()) {
            return configured;
        }
        String backendUrl = switch (backend) {
            case POSTGRESQL -> settings.postgresqlJdbcUrl();
            case MYSQL -> settings.mysqlJdbcUrl();
            case MARIADB -> settings.mariadbJdbcUrl();
            default -> "";
        };
        return backendUrl == null ? "" : backendUrl.trim();
    }

    private BackendSettings backendSettings(StorageBackend backend) {
        BackendSettings backendSettings = switch (backend) {
            case POSTGRESQL -> settings.postgresqlSettings();
            case MYSQL -> settings.mysqlSettings();
            case MARIADB -> settings.mariadbSettings();
            default -> BackendSettings.empty();
        };
        return backendSettings == null ? BackendSettings.empty() : backendSettings;
    }

    private String firstNonBlank(String first, String second) {
        if (first != null && !first.isBlank()) {
            return first.trim();
        }
        return second == null ? "" : second.trim();
    }

    private String safeJdbcDescription(String jdbcUrl) {
        if (jdbcUrl == null || jdbcUrl.isBlank()) {
            return "";
        }
        int query = jdbcUrl.indexOf('?');
        String withoutQuery = query >= 0 ? jdbcUrl.substring(0, query) : jdbcUrl;
        return withoutQuery.replaceAll("(?i)(password=)[^;&]+", "$1***");
    }

    public File databasePath() {
        return databaseFile();
    }

    public StorageBackend activeBackend() {
        return activeBackend;
    }

    public String databaseDescription() {
        return activeDescription == null || activeDescription.isBlank() ? databaseFile().getAbsolutePath() : activeDescription;
    }

    public String fallbackReason() {
        return fallbackReason == null || fallbackReason.isBlank() ? "none" : fallbackReason;
    }

    public boolean coreApiAuthorityReady() {
        return activeBackend != StorageBackend.CORE_API || coreStateWritersAvailable();
    }

    public boolean usesNodeLocalCache() {
        return activeBackend == StorageBackend.CORE_API || activeBackend == StorageBackend.SQLITE;
    }

    public List<StorageBackend> attemptedBackends() {
        return attemptedBackends == null ? List.of() : List.copyOf(attemptedBackends);
    }

    private File databaseFile() {
        String sqliteFileName = settings.sqliteFileName() == null || settings.sqliteFileName().isBlank() ? "data.db" : settings.sqliteFileName();
        File configured = new File(sqliteFileName);
        return configured.isAbsolute() ? configured : new File(dataFolder, sqliteFileName);
    }

    private void ensureDirectory(File directory, String label) {
        if (directory.exists()) {
            if (!directory.isDirectory()) {
                throw new IllegalStateException(label + " is not a directory: " + directory.getAbsolutePath());
            }
            return;
        }
        if (!directory.mkdirs() && !directory.isDirectory()) {
            throw new IllegalStateException("Failed to create " + label + ": " + directory.getAbsolutePath());
        }
    }

    public void close() {
        if (dataSource != null) {
            dataSource.close();
            dataSource = null;
        }
    }

    public Connection connection() throws SQLException {
        if (dataSource == null || dataSource.isClosed()) {
            throw new IllegalStateException("Satis database is not open");
        }
        return dataSource.getConnection();
    }

    public void coreStateWriter(Consumer<CoreRowWrite> coreStateWriter) {
        this.coreStateWriter = coreStateWriter;
    }

    public void coreTableWriter(Consumer<CoreTableWrite> coreTableWriter) {
        this.coreTableWriter = coreTableWriter;
    }

    public void coreGlobalStateWriter(Consumer<CoreGlobalRowWrite> coreGlobalStateWriter) {
        this.coreGlobalStateWriter = coreGlobalStateWriter;
    }

    public void coreGlobalTableWriter(Consumer<CoreGlobalTableWrite> coreGlobalTableWriter) {
        this.coreGlobalTableWriter = coreGlobalTableWriter;
    }

    public void withCoreStatePublishingSuspended(Runnable action) {
        if (action == null) {
            return;
        }
        boolean previous = coreStatePublishingSuspended;
        coreStatePublishingSuspended = true;
        try {
            action.run();
        } finally {
            coreStatePublishingSuspended = previous;
        }
    }

    public void purgeIsland(UUID islandUuid) {
        try (Connection connection = connection()) {
            boolean autoCommit = connection.getAutoCommit();
            connection.setAutoCommit(false);
            try {
                execute(connection, "DELETE FROM machine_network_links WHERE machine_id IN (SELECT machine_id FROM machines WHERE island_uuid = ?)", islandUuid);
                execute(connection, "DELETE FROM machine_network_links WHERE network_id IN (SELECT network_id FROM item_networks WHERE island_uuid = ?)", islandUuid);
                execute(connection, "DELETE FROM machine_network_links WHERE network_id IN (SELECT network_id FROM power_networks WHERE island_uuid = ?)", islandUuid);
                execute(connection, "DELETE FROM virtual_inventory_items WHERE inventory_id IN (SELECT inventory_id FROM virtual_inventories WHERE island_uuid = ?)", islandUuid);
                execute(connection, "DELETE FROM virtual_inventories WHERE island_uuid = ?", islandUuid);
                execute(connection, "DELETE FROM machines WHERE island_uuid = ?", islandUuid);
                execute(connection, "DELETE FROM resource_nodes WHERE island_uuid = ?", islandUuid);
                execute(connection, "DELETE FROM power_networks WHERE island_uuid = ?", islandUuid);
                execute(connection, "DELETE FROM item_networks WHERE island_uuid = ?", islandUuid);
                execute(connection, "DELETE FROM market_personal_daily WHERE island_uuid = ?", islandUuid);
                execute(connection, "DELETE FROM contracts WHERE island_uuid = ?", islandUuid);
                execute(connection, "DELETE FROM island_unlocks WHERE island_uuid = ?", islandUuid);
                execute(connection, "DELETE FROM ledger WHERE island_uuid = ?", islandUuid);
                execute(connection, "DELETE FROM factory_islands WHERE island_uuid = ?", islandUuid);
                connection.commit();
            } catch (SQLException exception) {
                connection.rollback();
                throw exception;
            } finally {
                connection.setAutoCommit(autoCommit);
            }
        } catch (SQLException exception) {
            throw new IllegalStateException("Failed to purge factory island", exception);
        }
    }

    private void execute(Connection connection, String sql, UUID islandUuid) throws SQLException {
        try (PreparedStatement statement = connection.prepareStatement(sql)) {
            statement.setString(1, islandUuid.toString());
            statement.executeUpdate();
        }
    }

    public LegacyImportResult importLegacyDatabase(File sourceDatabase) {
        File source = legacySourceDatabase(sourceDatabase);
        requireLegacyImportTargetAvailable();
        List<String> copiedTables = new ArrayList<>();
        List<String> skippedTables = new ArrayList<>();
        long copiedRows = 0L;
        String rollbackBackupPath = "unsupported-for-" + activeBackend.name();
        try (Connection target = connection();
             Connection sourceConnection = legacyConnection(source)) {
            rollbackBackupPath = createLegacyImportRollbackSnapshot(target);
            boolean autoCommit = target.getAutoCommit();
            target.setAutoCommit(false);
            try {
                for (String table : legacyImportTables()) {
                    long copied = copyLegacyTable(target, sourceConnection, table);
                    if (copied < 0L) {
                        skippedTables.add(table);
                        continue;
                    }
                    copiedRows += copied;
                    copiedTables.add(table);
                }
                target.commit();
            } catch (SQLException exception) {
                target.rollback();
                throw exception;
            } finally {
                target.setAutoCommit(autoCommit);
            }
            publishAllCoreState();
            return new LegacyImportResult(source.getAbsolutePath(), copiedRows, copiedTables, skippedTables, rollbackBackupPath);
        } catch (SQLException exception) {
            throw new IllegalStateException("Failed to import legacy Satis database", exception);
        }
    }

    public LegacyRollbackResult rollbackLastLegacyImport() {
        File backup = legacyRollbackBackupFile();
        if (!backup.isFile()) {
            return new LegacyRollbackResult(false, "backup-missing", backup.getAbsolutePath(), "run import once after this version creates a rollback snapshot");
        }
        if (activeBackend == StorageBackend.CORE_API && !coreStateWritersAvailable()) {
            return new LegacyRollbackResult(false, "core-api-addon-state-unavailable", backup.getAbsolutePath(), "enable CloudIslands addon-state writer, then rerun rollback");
        }
        if (sqlDialect == SqlDialect.SQLITE) {
            File target = databaseFile();
            try {
                close();
                java.nio.file.Files.copy(backup.toPath(), target.toPath(), java.nio.file.StandardCopyOption.REPLACE_EXISTING);
                open();
                publishAllCoreState();
                String status = activeBackend == StorageBackend.CORE_API ? "restored-core-api-local-cache-published" : "restored";
                return new LegacyRollbackResult(true, status, backup.getAbsolutePath(), "run verify against the legacy source before accepting rollback");
            } catch (java.io.IOException exception) {
                throw new IllegalStateException("Failed to restore legacy import rollback snapshot", exception);
            }
        }
        try (Connection target = connection();
             Connection source = legacyConnection(backup)) {
            boolean autoCommit = target.getAutoCommit();
            target.setAutoCommit(false);
            try {
                for (String table : reverseLegacyImportTables()) {
                    try (Statement statement = target.createStatement()) {
                        statement.executeUpdate("DELETE FROM " + table);
                    }
                }
                for (String table : legacyImportTables()) {
                    copyTableRows(target, source, table, sqlDialect, SqlDialect.SQLITE);
                }
                target.commit();
            } catch (SQLException exception) {
                target.rollback();
                throw exception;
            } finally {
                target.setAutoCommit(autoCommit);
            }
            publishAllCoreState();
            return new LegacyRollbackResult(true, "restored-shared-backend", backup.getAbsolutePath(), "run verify against the legacy source before accepting rollback");
        } catch (SQLException exception) {
            throw new IllegalStateException("Failed to restore shared backend legacy import rollback snapshot", exception);
        }
    }

    public LegacyImportPlan scanLegacyDatabase(File sourceDatabase) {
        File source = legacySourceDatabase(sourceDatabase);
        if (!legacyImportTargetAvailable()) {
            List<LegacyImportTablePlan> blockedTables = legacyImportTables().stream()
                    .map(table -> new LegacyImportTablePlan(table, false, 0L, "core-api-addon-state-unavailable"))
                    .toList();
            return new LegacyImportPlan(source.getAbsolutePath(), 0L, 0, blockedTables.size(), blockedTables);
        }
        List<LegacyImportTablePlan> tables = new ArrayList<>();
        long importableRows = 0L;
        int importableTables = 0;
        int skippedTables = 0;
        try (Connection target = connection();
             Connection sourceConnection = legacyConnection(source)) {
            for (String table : legacyImportTables()) {
                Set<String> targetColumns = tableColumns(target, table);
                Set<String> sourceColumns = sqliteTableColumns(sourceConnection, table);
                Set<String> requiredColumns = legacyRequiredColumns(table);
                if (targetColumns.isEmpty()) {
                    tables.add(new LegacyImportTablePlan(table, false, 0L, "target-table-missing"));
                    skippedTables++;
                    continue;
                }
                if (sourceColumns.isEmpty()) {
                    tables.add(new LegacyImportTablePlan(table, false, 0L, "source-table-missing"));
                    skippedTables++;
                    continue;
                }
                if (!sourceColumns.containsAll(requiredColumns)) {
                    tables.add(new LegacyImportTablePlan(table, false, 0L, "missing-required-columns"));
                    skippedTables++;
                    continue;
                }
                long rows = tableCount(sourceConnection, table);
                tables.add(new LegacyImportTablePlan(table, true, rows, "ready"));
                importableRows += rows;
                importableTables++;
            }
            return new LegacyImportPlan(source.getAbsolutePath(), importableRows, importableTables, skippedTables, tables);
        } catch (SQLException exception) {
            throw new IllegalStateException("Failed to scan legacy Satis database", exception);
        }
    }

    private void requireLegacyImportTargetAvailable() {
        if (!legacyImportTargetAvailable()) {
            throw new IllegalStateException("CORE_API legacy import requires an available CloudIslands addon-state writer");
        }
    }

    private boolean legacyImportTargetAvailable() {
        return activeBackend != StorageBackend.CORE_API || coreStateWritersAvailable();
    }

    private boolean coreStateWritersAvailable() {
        return coreStateWriter != null
                && coreTableWriter != null
                && coreGlobalStateWriter != null
                && coreGlobalTableWriter != null;
    }

    private File legacySourceDatabase(File sourceDatabase) {
        if (sourceDatabase == null) {
            throw new IllegalArgumentException("source database is required");
        }
        File source = sourceDatabase.isAbsolute() ? sourceDatabase : new File(dataFolder, sourceDatabase.getPath());
        if (!source.isFile()) {
            throw new IllegalArgumentException("legacy database does not exist: " + source.getAbsolutePath());
        }
        try {
            if (source.getCanonicalFile().equals(databaseFile().getCanonicalFile())) {
                throw new IllegalArgumentException("legacy database must be different from current database");
            }
        } catch (java.io.IOException exception) {
            throw new IllegalArgumentException("failed to compare database paths: " + exception.getMessage(), exception);
        }
        return source;
    }

    private Connection legacyConnection(File source) throws SQLException {
        return DriverManager.getConnection("jdbc:sqlite:" + source.getAbsolutePath());
    }

    private String createLegacyImportRollbackSnapshot(Connection target) throws SQLException {
        File backup = legacyRollbackBackupFile();
        File parent = backup.getParentFile();
        if (parent != null) {
            ensureDirectory(parent, "Satis migration backup folder");
        }
        try {
            java.nio.file.Files.deleteIfExists(backup.toPath());
        } catch (java.io.IOException exception) {
            throw new SQLException("failed to replace legacy rollback snapshot", exception);
        }
        if (sqlDialect == SqlDialect.SQLITE) {
            try (Statement statement = target.createStatement()) {
                statement.execute("VACUUM main INTO '" + sqliteLiteral(backup.getAbsolutePath()) + "'");
            }
            return backup.getAbsolutePath();
        }
        try (Connection backupConnection = legacyConnection(backup)) {
            new MigrationService().migrate(backupConnection, MigrationService.Dialect.SQLITE);
            for (String table : legacyImportTables()) {
                copyTableRows(backupConnection, target, table, SqlDialect.SQLITE, sqlDialect);
            }
        }
        return backup.getAbsolutePath();
    }

    private File legacyRollbackBackupFile() {
        return new File(dataFolder, "migration-backups/legacy-import-last.db");
    }

    private String sqliteLiteral(String value) {
        return value == null ? "" : value.replace("'", "''");
    }

    private List<String> legacyImportTables() {
        return List.of(
                "factory_islands",
                "machines",
                "virtual_inventories",
                "virtual_inventory_items",
                "resource_nodes",
                "power_networks",
                "item_networks",
                "machine_network_links",
                "market_daily",
                "market_personal_daily",
                "contracts",
                "island_unlocks",
                "ledger"
        );
    }

    private List<String> reverseLegacyImportTables() {
        List<String> tables = new ArrayList<>(legacyImportTables());
        java.util.Collections.reverse(tables);
        return tables;
    }

    private long copyLegacyTable(Connection target, Connection source, String table) throws SQLException {
        Set<String> targetColumns = tableColumns(target, table);
        Set<String> sourceColumns = sqliteTableColumns(source, table);
        if (targetColumns.isEmpty() || sourceColumns.isEmpty() || !sourceColumns.containsAll(legacyRequiredColumns(table))) {
            return -1L;
        }
        List<String> insertColumns = new ArrayList<>();
        List<String> selectExpressions = new ArrayList<>();
        for (String column : targetColumns) {
            if (sourceColumns.contains(column)) {
                insertColumns.add(column);
                selectExpressions.add(column);
                continue;
            }
            String defaultExpression = legacyImportDefaultExpression(table, column);
            if (!defaultExpression.isBlank()) {
                insertColumns.add(column);
                selectExpressions.add(defaultExpression);
            }
        }
        if (insertColumns.isEmpty()) {
            return -1L;
        }
        long before = tableCount(target, table);
        String columns = String.join(", ", insertColumns);
        String expressions = String.join(", ", selectExpressions);
        String placeholders = insertColumns.stream().map(_column -> "?").collect(java.util.stream.Collectors.joining(", "));
        try (Statement select = source.createStatement();
             ResultSet rs = select.executeQuery("SELECT " + expressions + " FROM " + table);
             PreparedStatement insert = target.prepareStatement(insertIgnoreSql(table, columns, placeholders))) {
            int columnIndex;
            while (rs.next()) {
                columnIndex = 1;
                for (int index = 0; index < insertColumns.size(); index++) {
                    insert.setObject(columnIndex, rs.getObject(index + 1));
                    columnIndex++;
                }
                insert.addBatch();
            }
            insert.executeBatch();
        }
        return Math.max(0L, tableCount(target, table) - before);
    }

    private long copyTableRows(Connection target, Connection source, String table, SqlDialect targetDialect, SqlDialect sourceDialect) throws SQLException {
        Set<String> targetColumns = tableColumns(target, table, targetDialect);
        Set<String> sourceColumns = tableColumns(source, table, sourceDialect);
        List<String> columns = targetColumns.stream()
                .filter(sourceColumns::contains)
                .toList();
        if (columns.isEmpty()) {
            return 0L;
        }
        String columnList = String.join(", ", columns);
        String placeholders = columns.stream().map(_column -> "?").collect(java.util.stream.Collectors.joining(", "));
        long before = tableCount(target, table, targetDialect);
        try (Statement select = source.createStatement();
             ResultSet rs = select.executeQuery("SELECT " + columnList + " FROM " + table);
             PreparedStatement insert = target.prepareStatement(insertIgnoreSql(table, columnList, placeholders, targetDialect))) {
            while (rs.next()) {
                for (int index = 0; index < columns.size(); index++) {
                    insert.setObject(index + 1, rs.getObject(index + 1));
                }
                insert.addBatch();
            }
            insert.executeBatch();
        }
        return Math.max(0L, tableCount(target, table, targetDialect) - before);
    }

    private String insertIgnoreSql(String table, String columns, String placeholders) {
        return insertIgnoreSql(table, columns, placeholders, sqlDialect);
    }

    private String insertIgnoreSql(String table, String columns, String placeholders, SqlDialect dialect) {
        if (dialect == SqlDialect.MYSQL) {
            return "INSERT IGNORE INTO " + table + "(" + columns + ") VALUES(" + placeholders + ")";
        }
        if (dialect == SqlDialect.POSTGRESQL) {
            return "INSERT INTO " + table + "(" + columns + ") VALUES(" + placeholders + ") ON CONFLICT DO NOTHING";
        }
        return "INSERT OR IGNORE INTO " + table + "(" + columns + ") VALUES(" + placeholders + ")";
    }

    private Set<String> legacyRequiredColumns(String table) {
        return switch (table) {
            case "factory_islands" -> Set.of("island_uuid", "owner_uuid");
            case "machines" -> Set.of("machine_id", "island_uuid", "owner_uuid", "type_id", "world", "x", "y", "z");
            case "virtual_inventories" -> Set.of("inventory_id", "island_uuid");
            case "virtual_inventory_items" -> Set.of("inventory_id", "item_id");
            case "resource_nodes" -> Set.of("node_id", "island_uuid", "node_type", "resource_id", "world", "x", "y", "z");
            case "power_networks", "item_networks" -> Set.of("network_id", "island_uuid");
            case "machine_network_links" -> Set.of("machine_id", "network_id");
            case "market_daily" -> Set.of("item_id", "date_key");
            case "market_personal_daily" -> Set.of("island_uuid", "item_id", "date_key");
            case "contracts" -> Set.of("contract_id", "island_uuid", "template_id", "contract_type");
            case "island_unlocks" -> Set.of("island_uuid", "unlock_id");
            case "ledger" -> Set.of("ledger_id", "island_uuid");
            default -> Set.of();
        };
    }

    private String legacyImportDefaultExpression(String table, String column) {
        if (column.equals("created_at") || column.equals("updated_at") || column.equals("last_tick_at")
                || column.equals("last_maintenance_at") || column.equals("last_process_at")
                || column.equals("expires_at") || column.equals("unlocked_at")) {
            return "CAST(strftime('%s','now') AS INTEGER) * 1000";
        }
        if (column.equals("maintenance_status")) {
            return "'NORMAL'";
        }
        if (column.equals("status") && table.equals("machines")) {
            return "'SLEEPING'";
        }
        if (column.equals("status") && table.equals("contracts")) {
            return "'ACTIVE'";
        }
        if (column.equals("direction")) {
            return "'NORTH'";
        }
        if (column.equals("config_json") || column.equals("required_json") || column.equals("progress_json") || column.equals("rewards_json")) {
            return "'{}'";
        }
        if (column.equals("tier")) {
            return "1";
        }
        if (column.equals("capacity")) {
            return Long.toString(defaultCapacity);
        }
        if (column.equals("holder_type")) {
            return "'ISLAND'";
        }
        if (column.equals("holder_id") && table.equals("virtual_inventories")) {
            return "island_uuid";
        }
        if (column.equals("network_type")) {
            return "'ITEM'";
        }
        if (column.equals("type")) {
            return "'LEGACY'";
        }
        if (column.equals("reason")) {
            return "'legacy-import'";
        }
        if (Set.of("research_points", "reputation", "maintenance_debt", "factory_score",
                "emergency_contracts_used_today", "active_center_x", "active_center_y", "active_center_z",
                "x", "y", "z", "remaining", "max_remaining", "regen_per_hour", "required_machine_tier",
                "throughput_per_minute", "dirty", "sold_amount", "amount").contains(column)) {
            return "0";
        }
        if (Set.of("wear", "purity", "generation_per_second", "consumption_per_second",
                "battery_stored", "battery_capacity", "power_ratio", "demand_factor").contains(column)) {
            return column.equals("power_ratio") || column.equals("demand_factor") ? "1.0" : "0.0";
        }
        if (Set.of("active_world", "input_inventory_id", "output_inventory_id", "power_network_id",
                "item_network_id", "linked_resource_node_id", "buffer_inventory_id").contains(column)) {
            return "''";
        }
        return "";
    }

    private Set<String> tableColumns(Connection connection, String schema, String table) throws SQLException {
        return tableColumns(connection, schema, table, sqlDialect);
    }

    private Set<String> tableColumns(Connection connection, String schema, String table, SqlDialect dialect) throws SQLException {
        if (dialect != SqlDialect.SQLITE) {
            Set<String> columns = new LinkedHashSet<>();
            try (ResultSet rs = connection.getMetaData().getColumns(null, null, table, null)) {
                while (rs.next()) {
                    columns.add(rs.getString("COLUMN_NAME").toLowerCase(Locale.ROOT));
                }
            }
            if (columns.isEmpty()) {
                try (ResultSet rs = connection.getMetaData().getColumns(null, null, table.toUpperCase(Locale.ROOT), null)) {
                    while (rs.next()) {
                        columns.add(rs.getString("COLUMN_NAME").toLowerCase(Locale.ROOT));
                    }
                }
            }
            return columns;
        }
        return sqliteTableColumns(connection, schema, table);
    }

    private Set<String> tableColumns(Connection connection, String table) throws SQLException {
        return tableColumns(connection, "main", table);
    }

    private Set<String> tableColumns(Connection connection, String table, SqlDialect dialect) throws SQLException {
        return tableColumns(connection, "main", table, dialect);
    }

    private Set<String> sqliteTableColumns(Connection connection, String table) throws SQLException {
        return sqliteTableColumns(connection, "", table);
    }

    private Set<String> sqliteTableColumns(Connection connection, String schema, String table) throws SQLException {
        Set<String> columns = new LinkedHashSet<>();
        String pragma = schema == null || schema.isBlank()
                ? "PRAGMA table_info(" + table + ")"
                : "PRAGMA " + schema + ".table_info(" + table + ")";
        try (Statement statement = connection.createStatement();
             ResultSet rs = statement.executeQuery(pragma)) {
            while (rs.next()) {
                columns.add(rs.getString("name"));
            }
        }
        return columns;
    }

    private long tableCount(Connection connection, String table) throws SQLException {
        return tableCount(connection, "main", table);
    }

    private long tableCount(Connection connection, String schema, String table) throws SQLException {
        return tableCount(connection, schema, table, sqlDialect);
    }

    private long tableCount(Connection connection, String table, SqlDialect dialect) throws SQLException {
        return tableCount(connection, "main", table, dialect);
    }

    private long tableCount(Connection connection, String schema, String table, SqlDialect dialect) throws SQLException {
        String source = dialect == SqlDialect.SQLITE ? schema + "." + table : table;
        try (Statement statement = connection.createStatement();
             ResultSet rs = statement.executeQuery("SELECT COUNT(*) AS count FROM " + source)) {
            return rs.next() ? rs.getLong("count") : 0L;
        }
    }

    public void publishAllCoreState() {
        if (coreStatePublishingSuspended || (coreStateWriter == null && coreTableWriter == null && coreGlobalStateWriter == null && coreGlobalTableWriter == null)) {
            return;
        }
        for (FactoryIsland island : loadIslands()) {
            UUID islandUuid = island.islandUuid();
            java.util.LinkedHashMap<String, String> rows = new java.util.LinkedHashMap<>();
            rows.put(islandUuid.toString(), factoryIslandJson(island));
            publishCoreTable(islandUuid, "factory_islands", rows);
            publishCoreTable(islandUuid, "machines", machineRows(islandUuid));
            publishCoreTable(islandUuid, "virtual_inventories", inventoryRows(islandUuid));
            publishCoreTable(islandUuid, "resource_nodes", nodeRows(islandUuid));
            publishItemNetworks(islandUuid, loadItemNetworks(islandUuid));
            publishPowerNetworks(islandUuid, loadPowerNetworks(islandUuid));
            publishCoreTable(islandUuid, "contracts", contractRows(islandUuid));
            publishCoreTable(islandUuid, "island_unlocks", unlockRows(islandUuid));
            publishCoreTable(islandUuid, "market_personal_daily", marketPersonalRows(islandUuid));
            publishCoreTable(islandUuid, "ledger", ledgerRows(islandUuid));
        }
        publishCoreGlobalTable("market_daily", marketDailyRows());
    }

    private java.util.Map<String, String> machineRows(UUID islandUuid) {
        java.util.LinkedHashMap<String, String> rows = new java.util.LinkedHashMap<>();
        for (MachineInstance machine : loadMachines()) {
            if (machine.islandUuid().equals(islandUuid)) {
                rows.put(machine.machineId().toString(), machineJson(machine));
            }
        }
        return rows;
    }

    private java.util.Map<String, String> inventoryRows(UUID islandUuid) {
        java.util.LinkedHashMap<String, String> rows = new java.util.LinkedHashMap<>();
        try (Connection connection = connection();
             PreparedStatement statement = connection.prepareStatement("SELECT inventory_id FROM virtual_inventories WHERE island_uuid = ?")) {
            statement.setString(1, islandUuid.toString());
            try (ResultSet rs = statement.executeQuery()) {
                while (rs.next()) {
                    UUID inventoryId = UUID.fromString(rs.getString("inventory_id"));
                    loadInventory(inventoryId).ifPresent(inventory -> rows.put(inventoryId.toString(), inventoryJson(inventory)));
                }
            }
            return rows;
        } catch (SQLException exception) {
            throw new IllegalStateException("Failed to publish inventory core state", exception);
        }
    }

    private java.util.Map<String, String> nodeRows(UUID islandUuid) {
        java.util.LinkedHashMap<String, String> rows = new java.util.LinkedHashMap<>();
        for (ResourceNode node : loadNodes(islandUuid)) {
            rows.put(node.nodeId().toString(), nodeJson(node));
        }
        return rows;
    }

    private java.util.Map<String, String> contractRows(UUID islandUuid) {
        java.util.LinkedHashMap<String, String> rows = new java.util.LinkedHashMap<>();
        try (Connection connection = connection();
             PreparedStatement statement = connection.prepareStatement("SELECT * FROM contracts WHERE island_uuid = ?")) {
            statement.setString(1, islandUuid.toString());
            try (ResultSet rs = statement.executeQuery()) {
                while (rs.next()) {
                    StoredContract contract = storedContract(rs);
                    rows.put(contract.contractId().toString(), contractJson(contract));
                }
            }
            return rows;
        } catch (SQLException exception) {
            throw new IllegalStateException("Failed to publish contract core state", exception);
        }
    }

    private java.util.Map<String, String> unlockRows(UUID islandUuid) {
        java.util.LinkedHashMap<String, String> rows = new java.util.LinkedHashMap<>();
        try (Connection connection = connection();
             PreparedStatement statement = connection.prepareStatement("SELECT unlock_id, unlocked_at FROM island_unlocks WHERE island_uuid = ?")) {
            statement.setString(1, islandUuid.toString());
            try (ResultSet rs = statement.executeQuery()) {
                while (rs.next()) {
                    String unlockId = rs.getString("unlock_id");
                    rows.put(unlockId, unlockJson(islandUuid, unlockId, rs.getLong("unlocked_at")));
                }
            }
            return rows;
        } catch (SQLException exception) {
            throw new IllegalStateException("Failed to publish unlock core state", exception);
        }
    }

    private java.util.Map<String, String> marketPersonalRows(UUID islandUuid) {
        java.util.LinkedHashMap<String, String> rows = new java.util.LinkedHashMap<>();
        try (Connection connection = connection();
             PreparedStatement statement = connection.prepareStatement("SELECT item_id, date_key, sold_amount FROM market_personal_daily WHERE island_uuid = ?")) {
            statement.setString(1, islandUuid.toString());
            try (ResultSet rs = statement.executeQuery()) {
                while (rs.next()) {
                    String itemId = rs.getString("item_id");
                    String dateKey = rs.getString("date_key");
                    rows.put(itemId + "/" + dateKey, marketPersonalJson(islandUuid, itemId, dateKey, rs.getLong("sold_amount")));
                }
            }
            return rows;
        } catch (SQLException exception) {
            throw new IllegalStateException("Failed to publish personal market core state", exception);
        }
    }

    private java.util.Map<String, String> ledgerRows(UUID islandUuid) {
        java.util.LinkedHashMap<String, String> rows = new java.util.LinkedHashMap<>();
        try (Connection connection = connection();
             PreparedStatement statement = connection.prepareStatement("SELECT ledger_id, type, amount, reason, created_at FROM ledger WHERE island_uuid = ?")) {
            statement.setString(1, islandUuid.toString());
            try (ResultSet rs = statement.executeQuery()) {
                while (rs.next()) {
                    UUID ledgerId = UUID.fromString(rs.getString("ledger_id"));
                    rows.put(ledgerId.toString(), ledgerJson(ledgerId, islandUuid, rs.getString("type"), rs.getLong("amount"), rs.getString("reason"), rs.getLong("created_at")));
                }
            }
            return rows;
        } catch (SQLException exception) {
            throw new IllegalStateException("Failed to publish ledger core state", exception);
        }
    }

    private java.util.Map<String, String> marketDailyRows() {
        java.util.LinkedHashMap<String, String> rows = new java.util.LinkedHashMap<>();
        try (Connection connection = connection();
             PreparedStatement statement = connection.prepareStatement("SELECT item_id, date_key, sold_amount, demand_factor FROM market_daily");
             ResultSet rs = statement.executeQuery()) {
            while (rs.next()) {
                String itemId = rs.getString("item_id");
                String dateKey = rs.getString("date_key");
                rows.put(itemId + "/" + dateKey, marketDailyJson(itemId, dateKey, rs.getLong("sold_amount"), rs.getDouble("demand_factor")));
            }
            return rows;
        } catch (SQLException exception) {
            throw new IllegalStateException("Failed to publish daily market core state", exception);
        }
    }

    public record LegacyImportResult(String sourcePath, long copiedRows, List<String> copiedTables, List<String> skippedTables, String rollbackBackupPath) {}
    public record LegacyImportTablePlan(String table, boolean importable, long sourceRows, String reason) {}
    public record LegacyImportPlan(String sourcePath, long importableRows, int importableTables, int skippedTables, List<LegacyImportTablePlan> tables) {}
    public record LegacyRollbackResult(boolean restored, String status, String backupPath, String nextStep) {}

    public Optional<FactoryIsland> findIsland(UUID islandUuid) {
        try (Connection connection = connection();
             PreparedStatement statement = connection.prepareStatement("SELECT * FROM factory_islands WHERE island_uuid = ?")) {
            statement.setString(1, islandUuid.toString());
            try (ResultSet rs = statement.executeQuery()) {
                if (!rs.next()) {
                    return Optional.empty();
                }
                return Optional.of(mapIsland(rs));
            }
        } catch (SQLException exception) {
            throw new IllegalStateException("Failed to load factory island", exception);
        }
    }

    public List<FactoryIsland> loadIslands() {
        List<FactoryIsland> islands = new ArrayList<>();
        try (Connection connection = connection();
             PreparedStatement statement = connection.prepareStatement("SELECT * FROM factory_islands");
             ResultSet rs = statement.executeQuery()) {
            while (rs.next()) {
                islands.add(mapIsland(rs));
            }
            return islands;
        } catch (SQLException exception) {
            throw new IllegalStateException("Failed to load factory islands", exception);
        }
    }

    private FactoryIsland mapIsland(ResultSet rs) throws SQLException {
        FactoryIsland island = new FactoryIsland(UUID.fromString(rs.getString("island_uuid")), UUID.fromString(rs.getString("owner_uuid")));
        island.tier(rs.getInt("tier"));
        island.researchPoints(rs.getLong("research_points"));
        island.reputation(rs.getLong("reputation"));
        island.maintenanceDebt(rs.getLong("maintenance_debt"));
        island.maintenanceStatus(MaintenanceStatus.valueOf(rs.getString("maintenance_status")));
        island.factoryScore(rs.getLong("factory_score"));
        island.lastMaintenanceAt(rs.getLong("last_maintenance_at"));
        island.lastTickAt(rs.getLong("last_tick_at"));
        island.createdAt(rs.getLong("created_at"));
        island.updatedAt(rs.getLong("updated_at"));
        island.emergencyContractsUsedToday(rs.getInt("emergency_contracts_used_today"));
        island.activeWorld(rs.getString("active_world"));
        island.activeCenterX(rs.getInt("active_center_x"));
        island.activeCenterY(rs.getInt("active_center_y"));
        island.activeCenterZ(rs.getInt("active_center_z"));
        return island;
    }

    public void saveIsland(FactoryIsland island) {
        long now = Instant.now().toEpochMilli();
        if (island.createdAt() <= 0) {
            island.createdAt(now);
        }
        island.updatedAt(now);
        try (Connection connection = connection();
             PreparedStatement statement = connection.prepareStatement(saveIslandSql())) {
            statement.setString(1, island.islandUuid().toString());
            statement.setString(2, island.ownerUuid().toString());
            statement.setInt(3, island.tier());
            statement.setLong(4, island.researchPoints());
            statement.setLong(5, island.reputation());
            statement.setLong(6, island.maintenanceDebt());
            statement.setString(7, island.maintenanceStatus().name());
            statement.setLong(8, island.factoryScore());
            statement.setLong(9, island.lastMaintenanceAt());
            statement.setLong(10, island.lastTickAt());
            statement.setInt(11, island.emergencyContractsUsedToday());
            statement.setString(12, island.activeWorld());
            statement.setInt(13, island.activeCenterX());
            statement.setInt(14, island.activeCenterY());
            statement.setInt(15, island.activeCenterZ());
            statement.setLong(16, island.createdAt());
            statement.setLong(17, now);
            statement.executeUpdate();
        } catch (SQLException exception) {
            throw new IllegalStateException("Failed to save factory island", exception);
        }
        publishCoreRow(island.islandUuid(), IslandAddonService.tableStateKey("factory_islands", island.islandUuid().toString()), factoryIslandJson(island));
    }

    private String saveIslandSql() {
        if (sqlDialect == SqlDialect.MYSQL) {
            return """
                     INSERT INTO factory_islands(island_uuid, owner_uuid, tier, research_points, reputation, maintenance_debt,
                       maintenance_status, factory_score, last_maintenance_at, last_tick_at, emergency_contracts_used_today,
                       active_world, active_center_x, active_center_y, active_center_z, created_at, updated_at)
                     VALUES(?, ?, ?, ?, ?, ?, ?, ?, ?, ?, ?, ?, ?, ?, ?, ?, ?)
                     ON DUPLICATE KEY UPDATE owner_uuid=VALUES(owner_uuid), tier=VALUES(tier),
                       research_points=VALUES(research_points), reputation=VALUES(reputation),
                       maintenance_debt=VALUES(maintenance_debt), maintenance_status=VALUES(maintenance_status),
                       factory_score=VALUES(factory_score), last_maintenance_at=VALUES(last_maintenance_at),
                       last_tick_at=VALUES(last_tick_at), emergency_contracts_used_today=VALUES(emergency_contracts_used_today),
                       active_world=VALUES(active_world), active_center_x=VALUES(active_center_x),
                       active_center_y=VALUES(active_center_y), active_center_z=VALUES(active_center_z),
                       updated_at=VALUES(updated_at)
                    """;
        }
        return """
                     INSERT INTO factory_islands(island_uuid, owner_uuid, tier, research_points, reputation, maintenance_debt,
                       maintenance_status, factory_score, last_maintenance_at, last_tick_at, emergency_contracts_used_today,
                       active_world, active_center_x, active_center_y, active_center_z, created_at, updated_at)
                     VALUES(?, ?, ?, ?, ?, ?, ?, ?, ?, ?, ?, ?, ?, ?, ?, ?, ?)
                     ON CONFLICT(island_uuid) DO UPDATE SET owner_uuid=excluded.owner_uuid, tier=excluded.tier,
                       research_points=excluded.research_points, reputation=excluded.reputation,
                       maintenance_debt=excluded.maintenance_debt, maintenance_status=excluded.maintenance_status,
                       factory_score=excluded.factory_score, last_maintenance_at=excluded.last_maintenance_at,
                       last_tick_at=excluded.last_tick_at, emergency_contracts_used_today=excluded.emergency_contracts_used_today,
                       active_world=excluded.active_world, active_center_x=excluded.active_center_x,
                       active_center_y=excluded.active_center_y, active_center_z=excluded.active_center_z,
                       updated_at=excluded.updated_at
                    """;
    }

    public List<MachineInstance> loadMachines() {
        List<MachineInstance> machines = new ArrayList<>();
        try (Connection connection = connection();
             PreparedStatement statement = connection.prepareStatement("SELECT * FROM machines");
             ResultSet rs = statement.executeQuery()) {
            while (rs.next()) {
                MachineInstance machine = new MachineInstance(
                        UUID.fromString(rs.getString("machine_id")),
                        UUID.fromString(rs.getString("island_uuid")),
                        UUID.fromString(rs.getString("owner_uuid")),
                        rs.getString("type_id"),
                        rs.getInt("tier"),
                        new BlockKey(rs.getString("world"), rs.getInt("x"), rs.getInt("y"), rs.getInt("z"))
                );
                machine.direction(BlockFace.valueOf(rs.getString("direction")));
                machine.status(MachineStatus.fromStoredValue(rs.getString("status")));
                machine.inputInventoryId(uuidOrNull(rs.getString("input_inventory_id")));
                machine.outputInventoryId(uuidOrNull(rs.getString("output_inventory_id")));
                machine.powerNetworkId(uuidOrNull(rs.getString("power_network_id")));
                machine.itemNetworkId(uuidOrNull(rs.getString("item_network_id")));
                machine.linkedResourceNodeId(uuidOrNull(rs.getString("linked_resource_node_id")));
                machine.configJson(rs.getString("config_json"));
                machine.selectedRecipeId(selectedRecipeId(machine.configJson()));
                machine.lastProcessAt(rs.getLong("last_process_at"));
                machine.wear(rs.getDouble("wear"));
                machine.createdAt(rs.getLong("created_at"));
                machine.updatedAt(rs.getLong("updated_at"));
                machines.add(machine);
            }
            return machines;
        } catch (SQLException exception) {
            throw new IllegalStateException("Failed to load machines", exception);
        }
    }

    public void saveMachine(MachineInstance machine) {
        long now = Instant.now().toEpochMilli();
        if (machine.createdAt() <= 0) {
            machine.createdAt(now);
        }
        machine.updatedAt(now);
        try (Connection connection = connection();
             PreparedStatement statement = connection.prepareStatement(saveMachineSql())) {
            statement.setString(1, machine.machineId().toString());
            statement.setString(2, machine.islandUuid().toString());
            statement.setString(3, machine.ownerUuid().toString());
            statement.setString(4, machine.typeId());
            statement.setInt(5, machine.tier());
            statement.setString(6, machine.location().world());
            statement.setInt(7, machine.location().x());
            statement.setInt(8, machine.location().y());
            statement.setInt(9, machine.location().z());
            statement.setString(10, machine.direction().name());
            statement.setString(11, machine.status().name());
            statement.setString(12, stringOrNull(machine.inputInventoryId()));
            statement.setString(13, stringOrNull(machine.outputInventoryId()));
            statement.setString(14, stringOrNull(machine.powerNetworkId()));
            statement.setString(15, stringOrNull(machine.itemNetworkId()));
            statement.setString(16, stringOrNull(machine.linkedResourceNodeId()));
            statement.setLong(17, machine.lastProcessAt());
            statement.setDouble(18, machine.wear());
            statement.setString(19, machineConfigJson(machine));
            statement.setLong(20, machine.createdAt());
            statement.setLong(21, machine.updatedAt());
            statement.executeUpdate();
        } catch (SQLException exception) {
            throw new IllegalStateException("Failed to save machine", exception);
        }
    }

    private String saveMachineSql() {
        if (sqlDialect == SqlDialect.MYSQL) {
            return """
                     INSERT INTO machines(machine_id, island_uuid, owner_uuid, type_id, tier, world, x, y, z, direction, status,
                       input_inventory_id, output_inventory_id, power_network_id, item_network_id, linked_resource_node_id,
                       last_process_at, wear, config_json, created_at, updated_at)
                     VALUES(?, ?, ?, ?, ?, ?, ?, ?, ?, ?, ?, ?, ?, ?, ?, ?, ?, ?, ?, ?, ?)
                     ON DUPLICATE KEY UPDATE world=VALUES(world), x=VALUES(x), y=VALUES(y), z=VALUES(z),
                       direction=VALUES(direction), status=VALUES(status), input_inventory_id=VALUES(input_inventory_id),
                       output_inventory_id=VALUES(output_inventory_id), power_network_id=VALUES(power_network_id),
                       item_network_id=VALUES(item_network_id), linked_resource_node_id=VALUES(linked_resource_node_id),
                       last_process_at=VALUES(last_process_at), wear=VALUES(wear), config_json=VALUES(config_json),
                       updated_at=VALUES(updated_at)
                    """;
        }
        return """
                     INSERT INTO machines(machine_id, island_uuid, owner_uuid, type_id, tier, world, x, y, z, direction, status,
                       input_inventory_id, output_inventory_id, power_network_id, item_network_id, linked_resource_node_id,
                       last_process_at, wear, config_json, created_at, updated_at)
                     VALUES(?, ?, ?, ?, ?, ?, ?, ?, ?, ?, ?, ?, ?, ?, ?, ?, ?, ?, ?, ?, ?)
                     ON CONFLICT(machine_id) DO UPDATE SET world=excluded.world, x=excluded.x, y=excluded.y, z=excluded.z,
                       direction=excluded.direction, status=excluded.status, input_inventory_id=excluded.input_inventory_id,
                       output_inventory_id=excluded.output_inventory_id, power_network_id=excluded.power_network_id,
                       item_network_id=excluded.item_network_id, linked_resource_node_id=excluded.linked_resource_node_id,
                       last_process_at=excluded.last_process_at, wear=excluded.wear, config_json=excluded.config_json, updated_at=excluded.updated_at
                    """;
    }

    private String selectedRecipeId(String json) {
        if (json == null || json.isBlank()) {
            return null;
        }
        String key = "\"selectedRecipe\"";
        int keyIndex = json.indexOf(key);
        if (keyIndex < 0) {
            return null;
        }
        int colon = json.indexOf(':', keyIndex + key.length());
        if (colon < 0) {
            return null;
        }
        int start = json.indexOf('"', colon + 1);
        if (start < 0) {
            return null;
        }
        StringBuilder value = new StringBuilder();
        boolean escaped = false;
        for (int index = start + 1; index < json.length(); index++) {
            char current = json.charAt(index);
            if (escaped) {
                value.append(current);
                escaped = false;
            } else if (current == '\\') {
                escaped = true;
            } else if (current == '"') {
                String parsed = value.toString();
                return parsed.isBlank() ? null : parsed;
            } else {
                value.append(current);
            }
        }
        return null;
    }

    private String machineConfigJson(MachineInstance machine) {
        String base = validJsonObject(machine.configJson()) ? machine.configJson().trim() : "{}";
        String selectedRecipe = machine.selectedRecipeId();
        String withoutSelectedRecipe = removeTopLevelStringField(base, "selectedRecipe");
        if (selectedRecipe == null || selectedRecipe.isBlank()) {
            return withoutSelectedRecipe;
        }
        String field = "\"selectedRecipe\":\"" + selectedRecipe.replace("\\", "\\\\").replace("\"", "\\\"") + "\"";
        if (withoutSelectedRecipe.equals("{}")) {
            return "{" + field + "}";
        }
        return withoutSelectedRecipe.substring(0, withoutSelectedRecipe.length() - 1) + "," + field + "}";
    }

    private boolean validJsonObject(String json) {
        if (json == null) {
            return false;
        }
        String trimmed = json.trim();
        return trimmed.startsWith("{") && trimmed.endsWith("}");
    }

    private String removeTopLevelStringField(String json, String fieldName) {
        String trimmed = json.trim();
        if (trimmed.equals("{}")) {
            return "{}";
        }
        List<String> fields = splitTopLevelFields(trimmed.substring(1, trimmed.length() - 1));
        String prefix = "\"" + fieldName + "\"";
        List<String> kept = fields.stream()
                .filter(field -> !field.trim().startsWith(prefix))
                .toList();
        return kept.isEmpty() ? "{}" : kept.stream().collect(java.util.stream.Collectors.joining(",", "{", "}"));
    }

    private List<String> splitTopLevelFields(String body) {
        List<String> fields = new ArrayList<>();
        StringBuilder current = new StringBuilder();
        boolean quoted = false;
        boolean escaped = false;
        int depth = 0;
        for (int index = 0; index < body.length(); index++) {
            char value = body.charAt(index);
            if (escaped) {
                current.append(value);
                escaped = false;
                continue;
            }
            if (value == '\\') {
                current.append(value);
                escaped = true;
                continue;
            }
            if (value == '"') {
                quoted = !quoted;
            } else if (!quoted && (value == '{' || value == '[')) {
                depth++;
            } else if (!quoted && (value == '}' || value == ']')) {
                depth = Math.max(0, depth - 1);
            } else if (!quoted && depth == 0 && value == ',') {
                String field = current.toString().trim();
                if (!field.isEmpty()) {
                    fields.add(field);
                }
                current.setLength(0);
                continue;
            }
            current.append(value);
        }
        String field = current.toString().trim();
        if (!field.isEmpty()) {
            fields.add(field);
        }
        return fields;
    }

    public void deleteMachine(UUID machineId) {
        try (Connection connection = connection()) {
            connection.setAutoCommit(false);
            try (PreparedStatement links = connection.prepareStatement("DELETE FROM machine_network_links WHERE machine_id = ?")) {
                links.setString(1, machineId.toString());
                links.executeUpdate();
            }
            try (PreparedStatement statement = connection.prepareStatement("DELETE FROM machines WHERE machine_id = ?")) {
                statement.setString(1, machineId.toString());
                statement.executeUpdate();
            }
            connection.commit();
        } catch (SQLException exception) {
            throw new IllegalStateException("Failed to delete machine", exception);
        }
    }

    public void replaceItemNetworks(UUID islandUuid, List<ItemNetwork> networks) {
        long now = Instant.now().toEpochMilli();
        try (Connection connection = connection()) {
            connection.setAutoCommit(false);
            try (PreparedStatement deleteLinks = connection.prepareStatement("""
                    DELETE FROM machine_network_links
                    WHERE network_type = 'ITEM'
                      AND network_id IN (SELECT network_id FROM item_networks WHERE island_uuid = ?)
                    """)) {
                deleteLinks.setString(1, islandUuid.toString());
                deleteLinks.executeUpdate();
            }
            try (PreparedStatement deleteNetworks = connection.prepareStatement("DELETE FROM item_networks WHERE island_uuid = ?")) {
                deleteNetworks.setString(1, islandUuid.toString());
                deleteNetworks.executeUpdate();
            }
            try (PreparedStatement clearMachines = connection.prepareStatement("UPDATE machines SET item_network_id = NULL WHERE island_uuid = ?")) {
                clearMachines.setString(1, islandUuid.toString());
                clearMachines.executeUpdate();
            }
            try (PreparedStatement networkStatement = connection.prepareStatement("""
                    INSERT INTO item_networks(network_id, island_uuid, throughput_per_minute, buffer_inventory_id, dirty, updated_at)
                    VALUES(?, ?, ?, ?, ?, ?)
                    """);
                 PreparedStatement linkStatement = connection.prepareStatement("""
                    INSERT INTO machine_network_links(machine_id, network_id, network_type)
                    VALUES(?, ?, 'ITEM')
                    """);
                 PreparedStatement machineStatement = connection.prepareStatement("""
                    UPDATE machines SET item_network_id = ?, updated_at = ? WHERE machine_id = ?
                    """)) {
                for (ItemNetwork network : networks) {
                    networkStatement.setString(1, network.networkId().toString());
                    networkStatement.setString(2, network.islandUuid().toString());
                    networkStatement.setInt(3, network.throughputPerMinute());
                    networkStatement.setString(4, stringOrNull(network.bufferInventoryId()));
                    networkStatement.setInt(5, network.dirty() ? 1 : 0);
                    networkStatement.setLong(6, now);
                    networkStatement.addBatch();
                    for (UUID machineId : network.connectedMachineIds()) {
                        linkStatement.setString(1, machineId.toString());
                        linkStatement.setString(2, network.networkId().toString());
                        linkStatement.addBatch();
                        machineStatement.setString(1, network.networkId().toString());
                        machineStatement.setLong(2, now);
                        machineStatement.setString(3, machineId.toString());
                        machineStatement.addBatch();
                    }
                }
                networkStatement.executeBatch();
                linkStatement.executeBatch();
                machineStatement.executeBatch();
            }
            connection.commit();
            publishItemNetworks(islandUuid, networks);
        } catch (SQLException exception) {
            throw new IllegalStateException("Failed to replace item networks", exception);
        }
    }

    public List<ItemNetwork> loadItemNetworks(UUID islandUuid) {
        List<ItemNetwork> networks = new ArrayList<>();
        try (Connection connection = connection();
             PreparedStatement statement = connection.prepareStatement("""
                     SELECT * FROM item_networks WHERE island_uuid = ? ORDER BY network_id
                     """)) {
            statement.setString(1, islandUuid.toString());
            try (ResultSet rs = statement.executeQuery()) {
                while (rs.next()) {
                    UUID networkId = UUID.fromString(rs.getString("network_id"));
                    networks.add(new ItemNetwork(
                            networkId,
                            islandUuid,
                            rs.getInt("throughput_per_minute"),
                            uuidOrNull(rs.getString("buffer_inventory_id")),
                            rs.getInt("dirty") != 0,
                            rs.getLong("updated_at"),
                            loadNetworkMachineIds(connection, networkId, "ITEM"),
                            itemRoutes(connection, networkId)
                    ));
                }
            }
            return networks;
        } catch (SQLException exception) {
            throw new IllegalStateException("Failed to load item networks", exception);
        }
    }

    private List<ItemNetwork.Route> itemRoutes(Connection connection, UUID networkId) throws SQLException {
        UUID bufferOwnerMachineId = null;
        try (PreparedStatement statement = connection.prepareStatement("""
                SELECT machine_id FROM machines
                WHERE item_network_id = ? AND input_inventory_id = (
                  SELECT buffer_inventory_id FROM item_networks WHERE network_id = ?
                )
                LIMIT 1
                """)) {
            statement.setString(1, networkId.toString());
            statement.setString(2, networkId.toString());
            try (ResultSet rs = statement.executeQuery()) {
                if (rs.next()) {
                    bufferOwnerMachineId = UUID.fromString(rs.getString("machine_id"));
                }
            }
        }
        if (bufferOwnerMachineId == null) {
            return List.of();
        }
        UUID root = bufferOwnerMachineId;
        return loadNetworkMachineIds(connection, networkId, "ITEM").stream()
                .filter(machineId -> !machineId.equals(root))
                .map(machineId -> new ItemNetwork.Route(root, machineId))
                .toList();
    }

    public void replacePowerNetworks(UUID islandUuid, List<PowerNetwork> networks) {
        long now = Instant.now().toEpochMilli();
        try (Connection connection = connection()) {
            connection.setAutoCommit(false);
            try (PreparedStatement deleteLinks = connection.prepareStatement("""
                    DELETE FROM machine_network_links
                    WHERE network_type = 'POWER'
                      AND network_id IN (SELECT network_id FROM power_networks WHERE island_uuid = ?)
                    """)) {
                deleteLinks.setString(1, islandUuid.toString());
                deleteLinks.executeUpdate();
            }
            try (PreparedStatement deleteNetworks = connection.prepareStatement("DELETE FROM power_networks WHERE island_uuid = ?")) {
                deleteNetworks.setString(1, islandUuid.toString());
                deleteNetworks.executeUpdate();
            }
            try (PreparedStatement clearMachines = connection.prepareStatement("UPDATE machines SET power_network_id = NULL WHERE island_uuid = ?")) {
                clearMachines.setString(1, islandUuid.toString());
                clearMachines.executeUpdate();
            }
            try (PreparedStatement networkStatement = connection.prepareStatement("""
                    INSERT INTO power_networks(network_id, island_uuid, generation_per_second, consumption_per_second,
                      battery_stored, battery_capacity, power_ratio, updated_at)
                    VALUES(?, ?, ?, ?, ?, ?, ?, ?)
                    """);
                 PreparedStatement linkStatement = connection.prepareStatement("""
                    INSERT INTO machine_network_links(machine_id, network_id, network_type)
                    VALUES(?, ?, 'POWER')
                    """);
                 PreparedStatement machineStatement = connection.prepareStatement("""
                    UPDATE machines SET power_network_id = ?, updated_at = ? WHERE machine_id = ?
                    """)) {
                for (PowerNetwork network : networks) {
                    networkStatement.setString(1, network.networkId().toString());
                    networkStatement.setString(2, network.islandUuid().toString());
                    networkStatement.setDouble(3, network.generationPerSecond());
                    networkStatement.setDouble(4, network.consumptionPerSecond());
                    networkStatement.setDouble(5, network.batteryStored());
                    networkStatement.setDouble(6, network.batteryCapacity());
                    networkStatement.setDouble(7, network.powerRatio());
                    networkStatement.setLong(8, now);
                    networkStatement.addBatch();
                    for (UUID machineId : network.connectedMachineIds()) {
                        linkStatement.setString(1, machineId.toString());
                        linkStatement.setString(2, network.networkId().toString());
                        linkStatement.addBatch();
                        machineStatement.setString(1, network.networkId().toString());
                        machineStatement.setLong(2, now);
                        machineStatement.setString(3, machineId.toString());
                        machineStatement.addBatch();
                    }
                }
                networkStatement.executeBatch();
                linkStatement.executeBatch();
                machineStatement.executeBatch();
            }
            connection.commit();
            publishPowerNetworks(islandUuid, networks);
        } catch (SQLException exception) {
            throw new IllegalStateException("Failed to replace power networks", exception);
        }
    }

    public List<PowerNetwork> loadPowerNetworks(UUID islandUuid) {
        List<PowerNetwork> networks = new ArrayList<>();
        try (Connection connection = connection();
             PreparedStatement statement = connection.prepareStatement("""
                     SELECT * FROM power_networks WHERE island_uuid = ? ORDER BY network_id
                     """)) {
            statement.setString(1, islandUuid.toString());
            try (ResultSet rs = statement.executeQuery()) {
                while (rs.next()) {
                    UUID networkId = UUID.fromString(rs.getString("network_id"));
                    networks.add(new PowerNetwork(
                            networkId,
                            islandUuid,
                            rs.getDouble("generation_per_second"),
                            rs.getDouble("consumption_per_second"),
                            rs.getDouble("battery_stored"),
                            rs.getDouble("battery_capacity"),
                            rs.getDouble("power_ratio"),
                            rs.getLong("updated_at"),
                            loadNetworkMachineIds(connection, networkId, "POWER")
                    ));
                }
            }
            return networks;
        } catch (SQLException exception) {
            throw new IllegalStateException("Failed to load power networks", exception);
        }
    }

    private Set<UUID> loadNetworkMachineIds(Connection connection, UUID networkId, String networkType) throws SQLException {
        Set<UUID> machineIds = new HashSet<>();
        try (PreparedStatement statement = connection.prepareStatement("""
                SELECT machine_id FROM machine_network_links WHERE network_id = ? AND network_type = ?
                """)) {
            statement.setString(1, networkId.toString());
            statement.setString(2, networkType);
            try (ResultSet rs = statement.executeQuery()) {
                while (rs.next()) {
                    machineIds.add(UUID.fromString(rs.getString("machine_id")));
                }
            }
        }
        return machineIds;
    }

    public void saveInventory(VirtualInventory inventory) {
        long now = Instant.now().toEpochMilli();
        try (Connection connection = connection()) {
            connection.setAutoCommit(false);
            try (PreparedStatement inv = connection.prepareStatement(saveInventorySql())) {
                inv.setString(1, inventory.inventoryId().toString());
                inv.setString(2, inventory.islandUuid().toString());
                inv.setString(3, inventory.holderType());
                inv.setString(4, inventory.holderId());
                inv.setLong(5, inventory.capacity());
                inv.setLong(6, now);
                inv.setLong(7, now);
                inv.executeUpdate();
            }
            try (PreparedStatement delete = connection.prepareStatement("DELETE FROM virtual_inventory_items WHERE inventory_id = ?")) {
                delete.setString(1, inventory.inventoryId().toString());
                delete.executeUpdate();
            }
            try (PreparedStatement item = connection.prepareStatement("INSERT INTO virtual_inventory_items(inventory_id, item_id, amount) VALUES(?, ?, ?)")) {
                for (var entry : inventory.items().entrySet()) {
                    item.setString(1, inventory.inventoryId().toString());
                    item.setString(2, entry.getKey());
                    item.setLong(3, entry.getValue());
                    item.addBatch();
                }
                item.executeBatch();
            }
            connection.commit();
        } catch (SQLException exception) {
            throw new IllegalStateException("Failed to save inventory", exception);
        }
    }

    private String saveInventorySql() {
        if (sqlDialect == SqlDialect.MYSQL) {
            return """
                    INSERT INTO virtual_inventories(inventory_id, island_uuid, holder_type, holder_id, capacity, created_at, updated_at)
                    VALUES(?, ?, ?, ?, ?, ?, ?)
                    ON DUPLICATE KEY UPDATE capacity=VALUES(capacity), updated_at=VALUES(updated_at)
                    """;
        }
        return """
                    INSERT INTO virtual_inventories(inventory_id, island_uuid, holder_type, holder_id, capacity, created_at, updated_at)
                    VALUES(?, ?, ?, ?, ?, ?, ?)
                    ON CONFLICT(inventory_id) DO UPDATE SET capacity=excluded.capacity, updated_at=excluded.updated_at
                    """;
    }

    public Optional<VirtualInventory> loadInventory(UUID inventoryId) {
        try (Connection connection = connection();
             PreparedStatement statement = connection.prepareStatement("SELECT * FROM virtual_inventories WHERE inventory_id = ?")) {
            statement.setString(1, inventoryId.toString());
            try (ResultSet rs = statement.executeQuery()) {
                if (!rs.next()) {
                    return Optional.empty();
                }
                VirtualInventory inventory = new VirtualInventory(
                        inventoryId,
                        UUID.fromString(rs.getString("island_uuid")),
                        rs.getString("holder_type"),
                        rs.getString("holder_id"),
                        rs.getLong("capacity")
                );
                loadInventoryItems(connection, inventory);
                return Optional.of(inventory);
            }
        } catch (SQLException exception) {
            throw new IllegalStateException("Failed to load inventory", exception);
        }
    }

    public Optional<VirtualInventory> findInventoryByHolder(UUID islandUuid, String holderType, String holderId) {
        try (Connection connection = connection();
             PreparedStatement statement = connection.prepareStatement("""
                     SELECT inventory_id FROM virtual_inventories
                     WHERE island_uuid = ? AND holder_type = ? AND holder_id = ?
                     LIMIT 1
                     """)) {
            statement.setString(1, islandUuid.toString());
            statement.setString(2, holderType);
            statement.setString(3, holderId);
            try (ResultSet rs = statement.executeQuery()) {
                if (!rs.next()) {
                    return Optional.empty();
                }
                return loadInventory(UUID.fromString(rs.getString("inventory_id")));
            }
        } catch (SQLException exception) {
            throw new IllegalStateException("Failed to find inventory", exception);
        }
    }

    public void deleteInventory(UUID inventoryId) {
        try (Connection connection = connection()) {
            connection.setAutoCommit(false);
            try (PreparedStatement items = connection.prepareStatement("DELETE FROM virtual_inventory_items WHERE inventory_id = ?")) {
                items.setString(1, inventoryId.toString());
                items.executeUpdate();
            }
            try (PreparedStatement inventory = connection.prepareStatement("DELETE FROM virtual_inventories WHERE inventory_id = ?")) {
                inventory.setString(1, inventoryId.toString());
                inventory.executeUpdate();
            }
            connection.commit();
        } catch (SQLException exception) {
            throw new IllegalStateException("Failed to delete inventory", exception);
        }
    }

    public List<ResourceNode> loadNodes(UUID islandUuid) {
        List<ResourceNode> nodes = new ArrayList<>();
        try (Connection connection = connection();
             PreparedStatement statement = connection.prepareStatement("SELECT * FROM resource_nodes WHERE island_uuid = ?")) {
            statement.setString(1, islandUuid.toString());
            try (ResultSet rs = statement.executeQuery()) {
                while (rs.next()) {
                    nodes.add(new ResourceNode(
                            UUID.fromString(rs.getString("node_id")),
                            islandUuid,
                            rs.getString("node_type"),
                            rs.getString("resource_id"),
                            rs.getDouble("purity"),
                            rs.getLong("remaining"),
                            rs.getLong("max_remaining"),
                            rs.getLong("regen_per_hour"),
                            rs.getInt("required_machine_tier"),
                            new BlockKey(rs.getString("world"), rs.getInt("x"), rs.getInt("y"), rs.getInt("z")),
                            rs.getLong("created_at"),
                            rs.getLong("updated_at")
                    ));
                }
            }
            return nodes;
        } catch (SQLException exception) {
            throw new IllegalStateException("Failed to load resource nodes", exception);
        }
    }

    public void saveNode(ResourceNode node) {
        long now = Instant.now().toEpochMilli();
        if (node.createdAt() <= 0) {
            node.createdAt(now);
        }
        node.updatedAt(now);
        try (Connection connection = connection();
             PreparedStatement statement = connection.prepareStatement(saveNodeSql())) {
            statement.setString(1, node.nodeId().toString());
            statement.setString(2, node.islandUuid().toString());
            statement.setString(3, node.nodeType());
            statement.setString(4, node.resourceId());
            statement.setDouble(5, node.purity());
            statement.setLong(6, node.remaining());
            statement.setLong(7, node.maxRemaining());
            statement.setLong(8, node.regenPerHour());
            statement.setInt(9, node.requiredMachineTier());
            statement.setString(10, node.location().world());
            statement.setInt(11, node.location().x());
            statement.setInt(12, node.location().y());
            statement.setInt(13, node.location().z());
            statement.setLong(14, node.createdAt());
            statement.setLong(15, node.updatedAt());
            statement.executeUpdate();
        } catch (SQLException exception) {
            throw new IllegalStateException("Failed to save resource node", exception);
        }
    }

    private String saveNodeSql() {
        if (sqlDialect == SqlDialect.MYSQL) {
            return """
                     INSERT INTO resource_nodes(node_id, island_uuid, node_type, resource_id, purity, remaining, max_remaining,
                       regen_per_hour, required_machine_tier, world, x, y, z, created_at, updated_at)
                     VALUES(?, ?, ?, ?, ?, ?, ?, ?, ?, ?, ?, ?, ?, ?, ?)
                     ON DUPLICATE KEY UPDATE remaining=VALUES(remaining), world=VALUES(world),
                       x=VALUES(x), y=VALUES(y), z=VALUES(z), updated_at=VALUES(updated_at)
                    """;
        }
        return """
                     INSERT INTO resource_nodes(node_id, island_uuid, node_type, resource_id, purity, remaining, max_remaining,
                       regen_per_hour, required_machine_tier, world, x, y, z, created_at, updated_at)
                     VALUES(?, ?, ?, ?, ?, ?, ?, ?, ?, ?, ?, ?, ?, ?, ?)
                     ON CONFLICT(node_id) DO UPDATE SET remaining=excluded.remaining, world=excluded.world,
                       x=excluded.x, y=excluded.y, z=excluded.z, updated_at=excluded.updated_at
                    """;
    }

    public void addLedger(UUID islandUuid, String type, long amount, String reason) {
        UUID ledgerId = UUID.randomUUID();
        long now = Instant.now().toEpochMilli();
        try (Connection connection = connection();
             PreparedStatement statement = connection.prepareStatement("INSERT INTO ledger(ledger_id, island_uuid, type, amount, reason, created_at) VALUES(?, ?, ?, ?, ?, ?)")) {
            statement.setString(1, ledgerId.toString());
            statement.setString(2, islandUuid.toString());
            statement.setString(3, type);
            statement.setLong(4, amount);
            statement.setString(5, reason);
            statement.setLong(6, now);
            statement.executeUpdate();
        } catch (SQLException exception) {
            throw new IllegalStateException("Failed to write ledger", exception);
        }
        publishCoreRow(islandUuid, IslandAddonService.tableStateKey("ledger", ledgerId.toString()), ledgerJson(ledgerId, islandUuid, type, amount, reason, now));
    }

    public long marketDailySold(String itemId, String dateKey) {
        try (Connection connection = connection();
             PreparedStatement statement = connection.prepareStatement("SELECT sold_amount FROM market_daily WHERE item_id = ? AND date_key = ?")) {
            statement.setString(1, itemId);
            statement.setString(2, dateKey);
            try (ResultSet rs = statement.executeQuery()) {
                return rs.next() ? rs.getLong("sold_amount") : 0L;
            }
        } catch (SQLException exception) {
            throw new IllegalStateException("Failed to read market daily sold amount", exception);
        }
    }

    public long marketPersonalSold(UUID islandUuid, String itemId, String dateKey) {
        try (Connection connection = connection();
             PreparedStatement statement = connection.prepareStatement("""
                     SELECT sold_amount FROM market_personal_daily
                     WHERE island_uuid = ? AND item_id = ? AND date_key = ?
                     """)) {
            statement.setString(1, islandUuid.toString());
            statement.setString(2, itemId);
            statement.setString(3, dateKey);
            try (ResultSet rs = statement.executeQuery()) {
                return rs.next() ? rs.getLong("sold_amount") : 0L;
            }
        } catch (SQLException exception) {
            throw new IllegalStateException("Failed to read personal market sold amount", exception);
        }
    }

    public void recordMarketSale(UUID islandUuid, String itemId, String dateKey, long amount, double demandFactor) {
        long dailySold = 0L;
        long personalSold = 0L;
        try (Connection connection = connection()) {
            connection.setAutoCommit(false);
            try (PreparedStatement daily = connection.prepareStatement(recordMarketDailySql())) {
                daily.setString(1, itemId);
                daily.setString(2, dateKey);
                daily.setLong(3, amount);
                daily.setDouble(4, demandFactor);
                daily.executeUpdate();
            }
            try (PreparedStatement personal = connection.prepareStatement(recordMarketPersonalSql())) {
                personal.setString(1, islandUuid.toString());
                personal.setString(2, itemId);
                personal.setString(3, dateKey);
                personal.setLong(4, amount);
                personal.executeUpdate();
            }
            dailySold = marketDailySold(connection, itemId, dateKey);
            personalSold = marketPersonalSold(connection, islandUuid, itemId, dateKey);
            connection.commit();
        } catch (SQLException exception) {
            throw new IllegalStateException("Failed to record market sale", exception);
        }
        publishCoreGlobalRow(IslandAddonService.tableStateKey("market_daily", itemId + "/" + dateKey),
                marketDailyJson(itemId, dateKey, dailySold, demandFactor));
        publishCoreRow(islandUuid, IslandAddonService.tableStateKey("market_personal_daily", itemId + "/" + dateKey),
                marketPersonalJson(islandUuid, itemId, dateKey, personalSold));
    }

    private long marketDailySold(Connection connection, String itemId, String dateKey) throws SQLException {
        try (PreparedStatement statement = connection.prepareStatement("SELECT sold_amount FROM market_daily WHERE item_id = ? AND date_key = ?")) {
            statement.setString(1, itemId);
            statement.setString(2, dateKey);
            try (ResultSet rs = statement.executeQuery()) {
                return rs.next() ? rs.getLong("sold_amount") : 0L;
            }
        }
    }

    private long marketPersonalSold(Connection connection, UUID islandUuid, String itemId, String dateKey) throws SQLException {
        try (PreparedStatement statement = connection.prepareStatement("""
                SELECT sold_amount FROM market_personal_daily
                WHERE island_uuid = ? AND item_id = ? AND date_key = ?
                """)) {
            statement.setString(1, islandUuid.toString());
            statement.setString(2, itemId);
            statement.setString(3, dateKey);
            try (ResultSet rs = statement.executeQuery()) {
                return rs.next() ? rs.getLong("sold_amount") : 0L;
            }
        }
    }

    public void saveMarketDailySnapshot(String itemId, String dateKey, long soldAmount, double demandFactor) {
        try (Connection connection = connection();
             PreparedStatement statement = connection.prepareStatement(saveMarketDailySnapshotSql())) {
            statement.setString(1, itemId);
            statement.setString(2, dateKey);
            statement.setLong(3, soldAmount);
            statement.setDouble(4, demandFactor);
            statement.executeUpdate();
        } catch (SQLException exception) {
            throw new IllegalStateException("Failed to save market daily snapshot", exception);
        }
    }

    private String saveMarketDailySnapshotSql() {
        if (sqlDialect == SqlDialect.MYSQL) {
            return """
                    INSERT INTO market_daily(item_id, date_key, sold_amount, demand_factor)
                    VALUES(?, ?, ?, ?)
                    ON DUPLICATE KEY UPDATE
                      sold_amount = VALUES(sold_amount),
                      demand_factor = VALUES(demand_factor)
                    """;
        }
        return """
                    INSERT INTO market_daily(item_id, date_key, sold_amount, demand_factor)
                    VALUES(?, ?, ?, ?)
                    ON CONFLICT(item_id, date_key) DO UPDATE SET
                      sold_amount = excluded.sold_amount,
                      demand_factor = excluded.demand_factor
                    """;
    }

    public void saveMarketPersonalSnapshot(UUID islandUuid, String itemId, String dateKey, long soldAmount) {
        try (Connection connection = connection();
             PreparedStatement statement = connection.prepareStatement(saveMarketPersonalSnapshotSql())) {
            statement.setString(1, islandUuid.toString());
            statement.setString(2, itemId);
            statement.setString(3, dateKey);
            statement.setLong(4, soldAmount);
            statement.executeUpdate();
        } catch (SQLException exception) {
            throw new IllegalStateException("Failed to save market personal snapshot", exception);
        }
    }

    private String saveMarketPersonalSnapshotSql() {
        if (sqlDialect == SqlDialect.MYSQL) {
            return """
                    INSERT INTO market_personal_daily(island_uuid, item_id, date_key, sold_amount)
                    VALUES(?, ?, ?, ?)
                    ON DUPLICATE KEY UPDATE
                      sold_amount = VALUES(sold_amount)
                    """;
        }
        return """
                    INSERT INTO market_personal_daily(island_uuid, item_id, date_key, sold_amount)
                    VALUES(?, ?, ?, ?)
                    ON CONFLICT(island_uuid, item_id, date_key) DO UPDATE SET
                      sold_amount = excluded.sold_amount
                    """;
    }

    public void saveLedgerSnapshot(UUID ledgerId, UUID islandUuid, String type, long amount, String reason, long createdAt) {
        try (Connection connection = connection();
             PreparedStatement statement = connection.prepareStatement(saveLedgerSnapshotSql())) {
            statement.setString(1, ledgerId.toString());
            statement.setString(2, islandUuid.toString());
            statement.setString(3, type);
            statement.setLong(4, amount);
            statement.setString(5, reason);
            statement.setLong(6, createdAt);
            statement.executeUpdate();
        } catch (SQLException exception) {
            throw new IllegalStateException("Failed to save ledger snapshot", exception);
        }
    }

    private String saveLedgerSnapshotSql() {
        if (sqlDialect == SqlDialect.MYSQL) {
            return """
                    INSERT INTO ledger(ledger_id, island_uuid, type, amount, reason, created_at)
                    VALUES(?, ?, ?, ?, ?, ?)
                    ON DUPLICATE KEY UPDATE reason = VALUES(reason)
                    """;
        }
        return """
                    INSERT INTO ledger(ledger_id, island_uuid, type, amount, reason, created_at)
                    VALUES(?, ?, ?, ?, ?, ?)
                    ON CONFLICT(ledger_id) DO NOTHING
                    """;
    }

    private String recordMarketDailySql() {
        if (sqlDialect == SqlDialect.MYSQL) {
            return """
                    INSERT INTO market_daily(item_id, date_key, sold_amount, demand_factor)
                    VALUES(?, ?, ?, ?)
                    ON DUPLICATE KEY UPDATE
                      sold_amount = sold_amount + VALUES(sold_amount),
                      demand_factor = VALUES(demand_factor)
                    """;
        }
        return """
                    INSERT INTO market_daily(item_id, date_key, sold_amount, demand_factor)
                    VALUES(?, ?, ?, ?)
                    ON CONFLICT(item_id, date_key) DO UPDATE SET
                      sold_amount = sold_amount + excluded.sold_amount,
                      demand_factor = excluded.demand_factor
                    """;
    }

    private String recordMarketPersonalSql() {
        if (sqlDialect == SqlDialect.MYSQL) {
            return """
                    INSERT INTO market_personal_daily(island_uuid, item_id, date_key, sold_amount)
                    VALUES(?, ?, ?, ?)
                    ON DUPLICATE KEY UPDATE
                      sold_amount = sold_amount + VALUES(sold_amount)
                    """;
        }
        return """
                    INSERT INTO market_personal_daily(island_uuid, item_id, date_key, sold_amount)
                    VALUES(?, ?, ?, ?)
                    ON CONFLICT(island_uuid, item_id, date_key) DO UPDATE SET
                      sold_amount = sold_amount + excluded.sold_amount
                    """;
    }

    public List<StoredContract> loadContracts(UUID islandUuid, String status) {
        List<StoredContract> contracts = new ArrayList<>();
        try (Connection connection = connection();
             PreparedStatement statement = connection.prepareStatement("""
                     SELECT * FROM contracts WHERE island_uuid = ? AND status = ? ORDER BY created_at ASC
                     """)) {
            statement.setString(1, islandUuid.toString());
            statement.setString(2, status);
            try (ResultSet rs = statement.executeQuery()) {
                while (rs.next()) {
                    contracts.add(new StoredContract(
                            UUID.fromString(rs.getString("contract_id")),
                            islandUuid,
                            rs.getString("template_id"),
                            rs.getString("contract_type"),
                            rs.getInt("tier"),
                            rs.getString("required_json"),
                            rs.getString("progress_json"),
                            rs.getString("rewards_json"),
                            rs.getString("status"),
                            rs.getLong("expires_at")
                    ));
                }
            }
            return contracts;
        } catch (SQLException exception) {
            throw new IllegalStateException("Failed to load contracts", exception);
        }
    }

    public boolean hasContractForTemplate(UUID islandUuid, String templateId, String status) {
        try (Connection connection = connection();
             PreparedStatement statement = connection.prepareStatement("""
                     SELECT 1 FROM contracts WHERE island_uuid = ? AND template_id = ? AND status = ? LIMIT 1
                     """)) {
            statement.setString(1, islandUuid.toString());
            statement.setString(2, templateId);
            statement.setString(3, status);
            try (ResultSet rs = statement.executeQuery()) {
                return rs.next();
            }
        } catch (SQLException exception) {
            throw new IllegalStateException("Failed to check contract", exception);
        }
    }

    public int countContracts(UUID islandUuid, String contractType, String status, long updatedSince) {
        try (Connection connection = connection();
             PreparedStatement statement = connection.prepareStatement("""
                     SELECT COUNT(*) AS count FROM contracts
                     WHERE island_uuid = ? AND contract_type = ? AND status = ? AND updated_at >= ?
                     """)) {
            statement.setString(1, islandUuid.toString());
            statement.setString(2, contractType);
            statement.setString(3, status);
            statement.setLong(4, updatedSince);
            try (ResultSet rs = statement.executeQuery()) {
                return rs.next() ? rs.getInt("count") : 0;
            }
        } catch (SQLException exception) {
            throw new IllegalStateException("Failed to count contracts", exception);
        }
    }

    public void saveContract(StoredContract contract) {
        long now = Instant.now().toEpochMilli();
        try (Connection connection = connection();
             PreparedStatement statement = connection.prepareStatement(saveContractSql())) {
            statement.setString(1, contract.contractId().toString());
            statement.setString(2, contract.islandUuid().toString());
            statement.setString(3, contract.templateId());
            statement.setString(4, contract.contractType());
            statement.setInt(5, contract.tier());
            statement.setString(6, contract.requiredJson());
            statement.setString(7, contract.progressJson());
            statement.setString(8, contract.rewardsJson());
            statement.setString(9, contract.status());
            statement.setLong(10, contract.expiresAt());
            statement.setLong(11, now);
            statement.setLong(12, now);
            statement.executeUpdate();
        } catch (SQLException exception) {
            throw new IllegalStateException("Failed to save contract", exception);
        }
        publishCoreRow(contract.islandUuid(), IslandAddonService.tableStateKey("contracts", contract.contractId().toString()), contractJson(contract));
    }

    private String saveContractSql() {
        if (sqlDialect == SqlDialect.MYSQL) {
            return """
                     INSERT INTO contracts(contract_id, island_uuid, template_id, contract_type, tier, required_json,
                       progress_json, rewards_json, status, expires_at, created_at, updated_at)
                     VALUES(?, ?, ?, ?, ?, ?, ?, ?, ?, ?, ?, ?)
                     ON DUPLICATE KEY UPDATE progress_json=VALUES(progress_json),
                       status=VALUES(status), updated_at=VALUES(updated_at)
                    """;
        }
        return """
                     INSERT INTO contracts(contract_id, island_uuid, template_id, contract_type, tier, required_json,
                       progress_json, rewards_json, status, expires_at, created_at, updated_at)
                     VALUES(?, ?, ?, ?, ?, ?, ?, ?, ?, ?, ?, ?)
                     ON CONFLICT(contract_id) DO UPDATE SET progress_json=excluded.progress_json,
                       status=excluded.status, updated_at=excluded.updated_at
                    """;
    }

    public void updateContractStatus(UUID contractId, String status, String progressJson) {
        StoredContract updated;
        try (Connection connection = connection();
             PreparedStatement statement = connection.prepareStatement("""
                     UPDATE contracts SET status = ?, progress_json = ?, updated_at = ? WHERE contract_id = ?
                     """)) {
            statement.setString(1, status);
            statement.setString(2, progressJson);
            statement.setLong(3, Instant.now().toEpochMilli());
            statement.setString(4, contractId.toString());
            statement.executeUpdate();
            updated = findContract(connection, contractId).orElse(null);
        } catch (SQLException exception) {
            throw new IllegalStateException("Failed to update contract", exception);
        }
        if (updated != null) {
            publishCoreRow(updated.islandUuid(), IslandAddonService.tableStateKey("contracts", updated.contractId().toString()), contractJson(updated));
        }
    }

    private Optional<StoredContract> findContract(Connection connection, UUID contractId) throws SQLException {
        try (PreparedStatement statement = connection.prepareStatement("SELECT * FROM contracts WHERE contract_id = ?")) {
            statement.setString(1, contractId.toString());
            try (ResultSet rs = statement.executeQuery()) {
                if (!rs.next()) {
                    return Optional.empty();
                }
                return Optional.of(new StoredContract(
                        contractId,
                        UUID.fromString(rs.getString("island_uuid")),
                        rs.getString("template_id"),
                        rs.getString("contract_type"),
                        rs.getInt("tier"),
                        rs.getString("required_json"),
                        rs.getString("progress_json"),
                        rs.getString("rewards_json"),
                        rs.getString("status"),
                        rs.getLong("expires_at")
                ));
            }
        }
    }

    public Set<String> loadUnlocks(UUID islandUuid) {
        Set<String> unlocks = new HashSet<>();
        try (Connection connection = connection();
             PreparedStatement statement = connection.prepareStatement("SELECT unlock_id FROM island_unlocks WHERE island_uuid = ?")) {
            statement.setString(1, islandUuid.toString());
            try (ResultSet rs = statement.executeQuery()) {
                while (rs.next()) {
                    unlocks.add(rs.getString("unlock_id"));
                }
            }
            return unlocks;
        } catch (SQLException exception) {
            throw new IllegalStateException("Failed to load unlocks", exception);
        }
    }

    public void saveUnlock(UUID islandUuid, String unlockId) {
        try (Connection connection = connection();
             PreparedStatement statement = connection.prepareStatement(saveUnlockSql())) {
            statement.setString(1, islandUuid.toString());
            statement.setString(2, unlockId);
            statement.setLong(3, Instant.now().toEpochMilli());
            statement.executeUpdate();
        } catch (SQLException exception) {
            throw new IllegalStateException("Failed to save unlock", exception);
        }
        publishCoreRow(islandUuid, IslandAddonService.tableStateKey("island_unlocks", unlockId), unlockJson(islandUuid, unlockId));
    }

    private void publishCoreRow(UUID islandUuid, String key, String value) {
        if (coreStatePublishingSuspended || coreStateWriter == null || islandUuid == null || key == null || key.isBlank() || value == null) {
            return;
        }
        coreStateWriter.accept(new CoreRowWrite(islandUuid, key, value));
    }

    private void publishItemNetworks(UUID islandUuid, List<ItemNetwork> networks) {
        if (islandUuid == null || networks == null) {
            return;
        }
        java.util.LinkedHashMap<String, String> values = new java.util.LinkedHashMap<>();
        values.put("index", "{\"networkIds\":\"" + escape(networkIdsCsv(networks)) + "\"}");
        for (ItemNetwork network : networks) {
            values.put(network.networkId().toString(), itemNetworkJson(network));
        }
        publishCoreTable(islandUuid, "item_networks", values);
    }

    private void publishPowerNetworks(UUID islandUuid, List<PowerNetwork> networks) {
        if (islandUuid == null || networks == null) {
            return;
        }
        java.util.LinkedHashMap<String, String> values = new java.util.LinkedHashMap<>();
        values.put("index", "{\"networkIds\":\"" + escape(networkIdsCsv(networks)) + "\"}");
        for (PowerNetwork network : networks) {
            values.put(network.networkId().toString(), powerNetworkJson(network));
        }
        publishCoreTable(islandUuid, "power_networks", values);
    }

    private void publishCoreTable(UUID islandUuid, String table, java.util.Map<String, String> values) {
        if (coreStatePublishingSuspended || islandUuid == null || table == null || table.isBlank() || values == null) {
            return;
        }
        if (coreTableWriter != null) {
            coreTableWriter.accept(new CoreTableWrite(islandUuid, table, java.util.Map.copyOf(values)));
            return;
        }
        if (values.isEmpty()) {
            return;
        }
        String safeTable = table.startsWith(IslandAddonService.TABLE_STATE_KEY_PREFIX) ? table.substring(IslandAddonService.TABLE_STATE_KEY_PREFIX.length()) : table;
        values.forEach((key, value) -> publishCoreRow(islandUuid, IslandAddonService.tableStateKey(safeTable, key), value));
    }

    private String itemNetworkJson(ItemNetwork network) {
        return "{"
                + field("networkId", network.networkId().toString()) + ","
                + field("islandUuid", network.islandUuid().toString()) + ","
                + number("throughputPerMinute", network.throughputPerMinute()) + ","
                + field("bufferInventoryId", network.bufferInventoryId() == null ? "" : network.bufferInventoryId().toString()) + ","
                + field("dirty", Boolean.toString(network.dirty())) + ","
                + number("updatedAt", network.updatedAt()) + ","
                + field("connectedMachineIds", uuidSetCsv(network.connectedMachineIds()))
                + "}";
    }

    private String powerNetworkJson(PowerNetwork network) {
        return "{"
                + field("networkId", network.networkId().toString()) + ","
                + field("islandUuid", network.islandUuid().toString()) + ","
                + number("generationPerSecond", network.generationPerSecond()) + ","
                + number("consumptionPerSecond", network.consumptionPerSecond()) + ","
                + number("batteryStored", network.batteryStored()) + ","
                + number("batteryCapacity", network.batteryCapacity()) + ","
                + number("powerRatio", network.powerRatio()) + ","
                + number("updatedAt", network.updatedAt()) + ","
                + field("connectedMachineIds", uuidSetCsv(network.connectedMachineIds()))
                + "}";
    }

    private String networkIdsCsv(List<?> networks) {
        return networks.stream()
                .map(network -> {
                    if (network instanceof ItemNetwork itemNetwork) {
                        return itemNetwork.networkId().toString();
                    }
                    if (network instanceof PowerNetwork powerNetwork) {
                        return powerNetwork.networkId().toString();
                    }
                    return "";
                })
                .filter(value -> !value.isBlank())
                .sorted()
                .reduce((left, right) -> left + "," + right)
                .orElse("");
    }

    private String uuidSetCsv(Set<UUID> values) {
        if (values == null || values.isEmpty()) {
            return "";
        }
        return values.stream()
                .map(UUID::toString)
                .sorted()
                .reduce((left, right) -> left + "," + right)
                .orElse("");
    }

    private void publishCoreGlobalRow(String key, String value) {
        if (coreStatePublishingSuspended || coreGlobalStateWriter == null || key == null || key.isBlank() || value == null) {
            return;
        }
        coreGlobalStateWriter.accept(new CoreGlobalRowWrite(key, value));
    }

    private void publishCoreGlobalTable(String table, java.util.Map<String, String> values) {
        if (coreStatePublishingSuspended || table == null || table.isBlank() || values == null) {
            return;
        }
        if (coreGlobalTableWriter != null) {
            coreGlobalTableWriter.accept(new CoreGlobalTableWrite(table, java.util.Map.copyOf(values)));
            return;
        }
        if (values.isEmpty()) {
            return;
        }
        values.forEach((key, value) -> publishCoreGlobalRow(IslandAddonService.TABLE_STATE_KEY_PREFIX + table + "/" + key, value));
    }

    private String marketDailyJson(String itemId, String dateKey, long soldAmount, double demandFactor) {
        return "{"
                + field("itemId", itemId) + ","
                + field("dateKey", dateKey) + ","
                + number("soldAmount", soldAmount) + ","
                + number("demandFactor", demandFactor)
                + "}";
    }

    private String marketPersonalJson(UUID islandUuid, String itemId, String dateKey, long soldAmount) {
        return "{"
                + field("islandUuid", islandUuid.toString()) + ","
                + field("itemId", itemId) + ","
                + field("dateKey", dateKey) + ","
                + number("soldAmount", soldAmount)
                + "}";
    }

    private String ledgerJson(UUID ledgerId, UUID islandUuid, String type, long amount, String reason, long createdAt) {
        return "{"
                + field("ledgerId", ledgerId.toString()) + ","
                + field("islandUuid", islandUuid.toString()) + ","
                + field("type", type) + ","
                + number("amount", amount) + ","
                + field("reason", reason) + ","
                + number("createdAt", createdAt)
                + "}";
    }

    private String contractJson(StoredContract contract) {
        return "{"
                + field("contractId", contract.contractId().toString()) + ","
                + field("islandUuid", contract.islandUuid().toString()) + ","
                + field("templateId", contract.templateId()) + ","
                + field("contractType", contract.contractType()) + ","
                + number("tier", contract.tier()) + ","
                + field("requiredJson", contract.requiredJson()) + ","
                + field("progressJson", contract.progressJson()) + ","
                + field("rewardsJson", contract.rewardsJson()) + ","
                + field("status", contract.status()) + ","
                + number("expiresAt", contract.expiresAt())
                + "}";
    }

    private StoredContract storedContract(ResultSet rs) throws SQLException {
        return new StoredContract(
                UUID.fromString(rs.getString("contract_id")),
                UUID.fromString(rs.getString("island_uuid")),
                rs.getString("template_id"),
                rs.getString("contract_type"),
                rs.getInt("tier"),
                rs.getString("required_json"),
                rs.getString("progress_json"),
                rs.getString("rewards_json"),
                rs.getString("status"),
                rs.getLong("expires_at")
        );
    }

    private String machineJson(MachineInstance machine) {
        return "{"
                + field("machineId", machine.machineId().toString()) + ","
                + field("islandUuid", machine.islandUuid().toString()) + ","
                + field("ownerUuid", machine.ownerUuid().toString()) + ","
                + field("typeId", machine.typeId()) + ","
                + number("tier", machine.tier()) + ","
                + field("world", machine.world()) + ","
                + number("x", machine.x()) + ","
                + number("y", machine.y()) + ","
                + number("z", machine.z()) + ","
                + field("direction", machine.direction().name()) + ","
                + field("status", machine.status().name()) + ","
                + field("inputInventoryId", stringOrEmpty(machine.inputInventoryId())) + ","
                + field("outputInventoryId", stringOrEmpty(machine.outputInventoryId())) + ","
                + field("powerNetworkId", stringOrEmpty(machine.powerNetworkId())) + ","
                + field("itemNetworkId", stringOrEmpty(machine.itemNetworkId())) + ","
                + field("linkedResourceNodeId", stringOrEmpty(machine.linkedResourceNodeId())) + ","
                + field("selectedRecipeId", machine.selectedRecipeId()) + ","
                + field("configJson", machineConfigJson(machine)) + ","
                + number("lastProcessAt", machine.lastProcessAt()) + ","
                + number("wear", machine.wear()) + ","
                + number("createdAt", machine.createdAt()) + ","
                + number("updatedAt", machine.updatedAt())
                + "}";
    }

    private String inventoryJson(VirtualInventory inventory) {
        StringBuilder items = new StringBuilder("{");
        boolean first = true;
        for (var entry : inventory.items().entrySet()) {
            if (!first) {
                items.append(',');
            }
            first = false;
            items.append('"').append(escape(entry.getKey())).append("\":").append(entry.getValue());
        }
        items.append('}');
        return "{"
                + field("inventoryId", inventory.inventoryId().toString()) + ","
                + field("islandUuid", inventory.islandUuid().toString()) + ","
                + field("holderType", inventory.holderType()) + ","
                + field("holderId", inventory.holderId()) + ","
                + number("capacity", inventory.capacity()) + ","
                + "\"items\":" + items
                + "}";
    }

    private String nodeJson(ResourceNode node) {
        return "{"
                + field("nodeId", node.nodeId().toString()) + ","
                + field("islandUuid", node.islandUuid().toString()) + ","
                + field("nodeType", node.nodeType()) + ","
                + field("resourceId", node.resourceId()) + ","
                + number("purity", node.purity()) + ","
                + number("remaining", node.remaining()) + ","
                + number("maxRemaining", node.maxRemaining()) + ","
                + number("regenPerHour", node.regenPerHour()) + ","
                + number("requiredMachineTier", node.requiredMachineTier()) + ","
                + field("world", node.world()) + ","
                + number("x", node.x()) + ","
                + number("y", node.y()) + ","
                + number("z", node.z()) + ","
                + number("createdAt", node.createdAt()) + ","
                + number("updatedAt", node.updatedAt())
                + "}";
    }

    private String factoryIslandJson(FactoryIsland island) {
        return "{"
                + field("islandUuid", island.islandUuid().toString()) + ","
                + field("ownerUuid", island.ownerUuid().toString()) + ","
                + number("tier", island.tier()) + ","
                + number("researchPoints", island.researchPoints()) + ","
                + number("reputation", island.reputation()) + ","
                + number("maintenanceDebt", island.maintenanceDebt()) + ","
                + field("maintenanceStatus", island.maintenanceStatus().name()) + ","
                + number("factoryScore", island.factoryScore()) + ","
                + number("lastMaintenanceAt", island.lastMaintenanceAt()) + ","
                + number("lastTickAt", island.lastTickAt()) + ","
                + number("emergencyContractsUsedToday", island.emergencyContractsUsedToday()) + ","
                + field("activeWorld", island.activeWorld()) + ","
                + number("activeCenterX", island.activeCenterX()) + ","
                + number("activeCenterY", island.activeCenterY()) + ","
                + number("activeCenterZ", island.activeCenterZ()) + ","
                + number("createdAt", island.createdAt()) + ","
                + number("updatedAt", island.updatedAt())
                + "}";
    }

    private String unlockJson(UUID islandUuid, String unlockId) {
        return unlockJson(islandUuid, unlockId, Instant.now().toEpochMilli());
    }

    private String unlockJson(UUID islandUuid, String unlockId, long unlockedAt) {
        return "{"
                + field("islandUuid", islandUuid.toString()) + ","
                + field("unlockId", unlockId) + ","
                + number("unlockedAt", unlockedAt)
                + "}";
    }

    private String stringOrEmpty(UUID value) {
        return value == null ? "" : value.toString();
    }

    private String field(String key, String value) {
        return "\"" + key + "\":\"" + escape(value == null ? "" : value) + "\"";
    }

    private String number(String key, long value) {
        return "\"" + key + "\":" + value;
    }

    private String number(String key, double value) {
        return "\"" + key + "\":" + value;
    }

    private String escape(String value) {
        return value == null ? "" : value.replace("\\", "\\\\").replace("\"", "\\\"");
    }

    private String saveUnlockSql() {
        if (sqlDialect == SqlDialect.MYSQL) {
            return """
                     INSERT IGNORE INTO island_unlocks(island_uuid, unlock_id, unlocked_at)
                     VALUES(?, ?, ?)
                    """;
        }
        if (sqlDialect == SqlDialect.POSTGRESQL) {
            return """
                     INSERT INTO island_unlocks(island_uuid, unlock_id, unlocked_at)
                     VALUES(?, ?, ?)
                     ON CONFLICT(island_uuid, unlock_id) DO NOTHING
                    """;
        }
        return """
                     INSERT OR IGNORE INTO island_unlocks(island_uuid, unlock_id, unlocked_at)
                     VALUES(?, ?, ?)
                    """;
    }

    private void loadInventoryItems(Connection connection, VirtualInventory inventory) throws SQLException {
        try (PreparedStatement statement = connection.prepareStatement("SELECT item_id, amount FROM virtual_inventory_items WHERE inventory_id = ?")) {
            statement.setString(1, inventory.inventoryId().toString());
            try (ResultSet rs = statement.executeQuery()) {
                while (rs.next()) {
                    inventory.set(rs.getString("item_id"), rs.getLong("amount"));
                }
            }
        }
    }

    private UUID uuidOrNull(String value) {
        return value == null ? null : UUID.fromString(value);
    }

    private String stringOrNull(UUID value) {
        return value == null ? null : value.toString();
    }

    public record StoredContract(
            UUID contractId,
            UUID islandUuid,
            String templateId,
            String contractType,
            int tier,
            String requiredJson,
            String progressJson,
            String rewardsJson,
            String status,
            long expiresAt
    ) {
    }
}
