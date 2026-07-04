package kr.seungmin.satisskyfactory.database;

import com.zaxxer.hikari.HikariConfig;
import com.zaxxer.hikari.HikariDataSource;
import kr.lunaf.cloudislands.api.service.IslandAddonService;
import kr.seungmin.satisskyfactory.model.FactoryIsland;
import kr.seungmin.satisskyfactory.model.ItemNetwork;
import kr.seungmin.satisskyfactory.model.MachineInstance;
import kr.seungmin.satisskyfactory.model.PowerNetwork;
import kr.seungmin.satisskyfactory.model.ResourceNode;
import kr.seungmin.satisskyfactory.storage.SatisSchemaService;
import kr.seungmin.satisskyfactory.storage.VirtualInventory;
import org.bukkit.plugin.java.JavaPlugin;
import org.sqlite.SQLiteConfig;

import java.io.File;
import java.sql.Connection;
import java.sql.PreparedStatement;
import java.sql.ResultSet;
import java.sql.SQLException;
import java.sql.Statement;
import java.time.Instant;
import java.util.ArrayList;
import java.util.List;
import java.util.Locale;
import java.util.Optional;
import java.util.Set;
import java.util.UUID;
import java.util.function.Consumer;

public final class DatabaseService {
    public enum EconomyLedgerClaim {
        STARTED,
        PENDING,
        COMPLETED,
        FAILED,
        NEEDS_COMPENSATION
    }

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
    private final FactoryIslandRepository factoryIslandRepository;
    private final VirtualInventoryRepository virtualInventoryRepository;
    private final ResourceNodeRepository resourceNodeRepository;
    private final ResearchRepository researchRepository;
    private final ContractRepository contractRepository;
    private final LedgerRepository ledgerRepository;
    private final MarketRepository marketRepository;
    private final NetworkRepository networkRepository;
    private final MachineRepository machineRepository;
    private final CoreAddonStatePublisher coreStatePublisher;
    private HikariDataSource dataSource;
    private StorageBackend activeBackend = StorageBackend.SQLITE;
    private SqlDialect sqlDialect = SqlDialect.SQLITE;
    private String activeDescription = "";
    private String fallbackReason = "none";
    private List<StorageBackend> attemptedBackends = List.of();

    public record CoreRowWrite(UUID islandUuid, String key, String value) {
    }

    public record CoreTableWrite(UUID islandUuid, String table, java.util.Map<String, String> values) {
    }

    public record CoreBulkWrite(UUID islandUuid, java.util.Map<String, String> values, java.util.Map<String, java.util.Map<String, String>> tables) {
    }

    public record CoreGlobalRowWrite(String key, String value) {
    }

    public record CoreGlobalTableWrite(String table, java.util.Map<String, String> values) {
    }

    public record CoreGlobalBulkWrite(java.util.Map<String, String> values, java.util.Map<String, java.util.Map<String, String>> tables) {
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

    public DatabaseService(File dataFolder) {
        this(dataFolder, "data.db");
    }

    public DatabaseService(File dataFolder, String sqliteFileName) {
        this(dataFolder, Settings.sqlite(sqliteFileName));
    }

    public DatabaseService(File dataFolder, Settings settings) {
        this.dataFolder = dataFolder;
        this.settings = settings == null ? Settings.sqlite("data.db") : settings;
        this.factoryIslandRepository = new FactoryIslandRepository(this);
        this.virtualInventoryRepository = new VirtualInventoryRepository(this);
        this.resourceNodeRepository = new ResourceNodeRepository(this);
        this.researchRepository = new ResearchRepository(this);
        this.contractRepository = new ContractRepository(this);
        this.ledgerRepository = new LedgerRepository(this);
        this.marketRepository = new MarketRepository(this);
        this.networkRepository = new NetworkRepository(this);
        this.machineRepository = new MachineRepository(this);
        this.coreStatePublisher = new CoreAddonStatePublisher();
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
            new SatisSchemaService().initializeOrUpgradeSchema(connection, SatisSchemaService.Dialect.SQLITE);
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
        SatisSchemaService.Dialect migrationDialect = switch (backend) {
            case POSTGRESQL -> SatisSchemaService.Dialect.POSTGRESQL;
            case MARIADB -> SatisSchemaService.Dialect.MARIADB;
            case MYSQL -> SatisSchemaService.Dialect.MYSQL;
            default -> throw new IllegalArgumentException("Unsupported JDBC backend: " + backend);
        };
        try (Connection connection = connection()) {
            new SatisSchemaService().initializeOrUpgradeSchema(connection, migrationDialect);
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

    public String coreApiAuthorityStatus() {
        if (activeBackend != StorageBackend.CORE_API) {
            return "not-core-api:" + activeBackend.name();
        }
        if (coreStateWritersAvailable()) {
            return coreStatePublisher.publishingSuspended() ? "ready-publishing-suspended" : "ready";
        }
        return "unavailable-local-cache-only";
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
        coreStatePublisher.coreStateWriter(coreStateWriter);
    }

    public void coreTableWriter(Consumer<CoreTableWrite> coreTableWriter) {
        coreStatePublisher.coreTableWriter(coreTableWriter);
    }

    public void coreBulkWriter(Consumer<CoreBulkWrite> coreBulkWriter) {
        coreStatePublisher.coreBulkWriter(coreBulkWriter);
    }

    public void coreGlobalStateWriter(Consumer<CoreGlobalRowWrite> coreGlobalStateWriter) {
        coreStatePublisher.coreGlobalStateWriter(coreGlobalStateWriter);
    }

    public void coreGlobalTableWriter(Consumer<CoreGlobalTableWrite> coreGlobalTableWriter) {
        coreStatePublisher.coreGlobalTableWriter(coreGlobalTableWriter);
    }

    public void coreGlobalBulkWriter(Consumer<CoreGlobalBulkWrite> coreGlobalBulkWriter) {
        coreStatePublisher.coreGlobalBulkWriter(coreGlobalBulkWriter);
    }

    public void withCoreStatePublishingSuspended(Runnable action) {
        coreStatePublisher.withPublishingSuspended(action);
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

    private boolean coreStateWritersAvailable() {
        return coreStatePublisher.writersAvailable();
    }

    public void publishAllCoreState() {
        if (coreStatePublisher.publishingSuspended() || !coreStatePublisher.hasPrimaryWriter()) {
            return;
        }
        for (FactoryIsland island : loadIslands()) {
            UUID islandUuid = island.islandUuid();
            java.util.LinkedHashMap<String, String> rows = new java.util.LinkedHashMap<>();
            rows.put(islandUuid.toString(), factoryIslandJson(island));
            coreStatePublisher.publishTable(islandUuid, "factory_islands", rows);
            coreStatePublisher.publishTable(islandUuid, "machines", machineRows(islandUuid));
            coreStatePublisher.publishTable(islandUuid, "virtual_inventories", inventoryRows(islandUuid));
            coreStatePublisher.publishTable(islandUuid, "resource_nodes", nodeRows(islandUuid));
            publishItemNetworks(islandUuid, loadItemNetworks(islandUuid));
            publishPowerNetworks(islandUuid, loadPowerNetworks(islandUuid));
            coreStatePublisher.publishTable(islandUuid, "contracts", contractRows(islandUuid));
            coreStatePublisher.publishTable(islandUuid, "island_unlocks", unlockRows(islandUuid));
            coreStatePublisher.publishTable(islandUuid, "market_personal_daily", marketPersonalRows(islandUuid));
            coreStatePublisher.publishTable(islandUuid, "ledger", ledgerRows(islandUuid));
        }
        coreStatePublisher.publishGlobalTable("market_daily", marketDailyRows());
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
        try {
            for (UUID inventoryId : virtualInventoryRepository.inventoryIds(islandUuid)) {
                loadInventory(inventoryId).ifPresent(inventory -> rows.put(inventoryId.toString(), inventoryJson(inventory)));
            }
            return rows;
        } catch (IllegalStateException exception) {
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
        for (StoredContract contract : contractRepository.load(islandUuid)) {
            rows.put(contract.contractId().toString(), contractJson(contract));
        }
        return rows;
    }

    private java.util.Map<String, String> unlockRows(UUID islandUuid) {
        java.util.LinkedHashMap<String, String> rows = new java.util.LinkedHashMap<>();
        for (ResearchRepository.UnlockEntry entry : researchRepository.unlockEntries(islandUuid)) {
            rows.put(entry.unlockId(), unlockJson(islandUuid, entry.unlockId(), entry.unlockedAt()));
        }
        return rows;
    }

    private java.util.Map<String, String> marketPersonalRows(UUID islandUuid) {
        java.util.LinkedHashMap<String, String> rows = new java.util.LinkedHashMap<>();
        for (MarketRepository.PersonalMarketRow row : marketRepository.personalRows(islandUuid)) {
            rows.put(row.itemId() + "/" + row.dateKey(), marketPersonalJson(row.islandUuid(), row.itemId(), row.dateKey(), row.soldAmount()));
        }
        return rows;
    }

    private java.util.Map<String, String> ledgerRows(UUID islandUuid) {
        java.util.LinkedHashMap<String, String> rows = new java.util.LinkedHashMap<>();
        for (LedgerRepository.LedgerEntry entry : ledgerRepository.load(islandUuid)) {
            rows.put(entry.ledgerId().toString(), ledgerJson(entry.ledgerId(), entry.islandUuid(), entry.type(), entry.amount(), entry.reason(), entry.createdAt()));
        }
        return rows;
    }

    private java.util.Map<String, String> marketDailyRows() {
        java.util.LinkedHashMap<String, String> rows = new java.util.LinkedHashMap<>();
        for (MarketRepository.DailyMarketRow row : marketRepository.dailyRows()) {
            rows.put(row.itemId() + "/" + row.dateKey(), marketDailyJson(row.itemId(), row.dateKey(), row.soldAmount(), row.demandFactor()));
        }
        return rows;
    }


    public Optional<FactoryIsland> findIsland(UUID islandUuid) {
        return factoryIslandRepository.find(islandUuid);
    }

    public List<FactoryIsland> loadIslands() {
        return factoryIslandRepository.loadAll();
    }

    public void saveIsland(FactoryIsland island) {
        factoryIslandRepository.save(island);
        coreStatePublisher.publishRow(island.islandUuid(), IslandAddonService.tableStateKey("factory_islands", island.islandUuid().toString()), factoryIslandJson(island));
    }

    boolean usesMysqlDialect() {
        return sqlDialect == SqlDialect.MYSQL;
    }

    boolean usesPostgresqlDialect() {
        return sqlDialect == SqlDialect.POSTGRESQL;
    }

    public List<MachineInstance> loadMachines() {
        return machineRepository.loadAll();
    }

    public void saveMachine(MachineInstance machine) {
        machineRepository.save(machine);
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
        machineRepository.delete(machineId);
    }

    public void replaceItemNetworks(UUID islandUuid, List<ItemNetwork> networks) {
        networkRepository.replaceItemNetworks(islandUuid, networks);
        publishItemNetworks(islandUuid, networks);
    }

    public List<ItemNetwork> loadItemNetworks(UUID islandUuid) {
        return networkRepository.loadItemNetworks(islandUuid);
    }

    public void replacePowerNetworks(UUID islandUuid, List<PowerNetwork> networks) {
        networkRepository.replacePowerNetworks(islandUuid, networks);
        publishPowerNetworks(islandUuid, networks);
    }

    public List<PowerNetwork> loadPowerNetworks(UUID islandUuid) {
        return networkRepository.loadPowerNetworks(islandUuid);
    }

    public void saveInventory(VirtualInventory inventory) {
        virtualInventoryRepository.save(inventory);
    }

    public Optional<VirtualInventory> loadInventory(UUID inventoryId) {
        return virtualInventoryRepository.load(inventoryId);
    }

    public Optional<VirtualInventory> findInventoryByHolder(UUID islandUuid, String holderType, String holderId) {
        return virtualInventoryRepository.findByHolder(islandUuid, holderType, holderId);
    }

    public void deleteInventory(UUID inventoryId) {
        virtualInventoryRepository.delete(inventoryId);
    }

    public List<ResourceNode> loadNodes(UUID islandUuid) {
        return resourceNodeRepository.load(islandUuid);
    }

    public void saveNode(ResourceNode node) {
        resourceNodeRepository.save(node);
    }

    public void addLedger(UUID islandUuid, String type, long amount, String reason) {
        LedgerRepository.LedgerEntry entry = ledgerRepository.add(islandUuid, type, amount, reason);
        coreStatePublisher.publishRow(islandUuid, IslandAddonService.tableStateKey("ledger", entry.ledgerId().toString()),
                ledgerJson(entry.ledgerId(), entry.islandUuid(), entry.type(), entry.amount(), entry.reason(), entry.createdAt()));
    }

    public EconomyLedgerClaim beginEconomyLedger(UUID islandUuid, UUID playerUuid, String operation, long amount,
                                                 String reason, String idempotencyKey) {
        return ledgerRepository.beginEconomyLedger(islandUuid, playerUuid, operation, amount, reason, idempotencyKey);
    }

    public EconomyLedgerClaim economyLedgerClaim(String idempotencyKey) {
        return ledgerRepository.economyLedgerClaim(idempotencyKey);
    }

    public void completeEconomyLedger(String idempotencyKey) {
        ledgerRepository.completeEconomyLedger(idempotencyKey);
    }

    public void failEconomyLedger(String idempotencyKey) {
        ledgerRepository.failEconomyLedger(idempotencyKey);
    }

    public void compensateEconomyLedger(String idempotencyKey) {
        ledgerRepository.compensateEconomyLedger(idempotencyKey);
    }

    public long marketDailySold(String itemId, String dateKey) {
        return marketRepository.dailySold(itemId, dateKey);
    }

    public long marketPersonalSold(UUID islandUuid, String itemId, String dateKey) {
        return marketRepository.personalSold(islandUuid, itemId, dateKey);
    }

    public void recordMarketSale(UUID islandUuid, String itemId, String dateKey, long amount, double demandFactor) {
        MarketRepository.MarketSaleTotals totals = marketRepository.recordSale(islandUuid, itemId, dateKey, amount, demandFactor);
        coreStatePublisher.publishGlobalRow(IslandAddonService.tableStateKey("market_daily", itemId + "/" + dateKey),
                marketDailyJson(itemId, dateKey, totals.dailySold(), demandFactor));
        coreStatePublisher.publishRow(islandUuid, IslandAddonService.tableStateKey("market_personal_daily", itemId + "/" + dateKey),
                marketPersonalJson(islandUuid, itemId, dateKey, totals.personalSold()));
    }

    public void saveMarketDailySnapshot(String itemId, String dateKey, long soldAmount, double demandFactor) {
        marketRepository.saveDailySnapshot(itemId, dateKey, soldAmount, demandFactor);
    }

    public void saveMarketPersonalSnapshot(UUID islandUuid, String itemId, String dateKey, long soldAmount) {
        marketRepository.savePersonalSnapshot(islandUuid, itemId, dateKey, soldAmount);
    }

    public void saveLedgerSnapshot(UUID ledgerId, UUID islandUuid, String type, long amount, String reason, long createdAt) {
        ledgerRepository.saveSnapshot(ledgerId, islandUuid, type, amount, reason, createdAt);
    }

    public List<StoredContract> loadContracts(UUID islandUuid, String status) {
        return contractRepository.load(islandUuid, status);
    }

    public boolean hasContractForTemplate(UUID islandUuid, String templateId, String status) {
        return contractRepository.existsForTemplate(islandUuid, templateId, status);
    }

    public int countContracts(UUID islandUuid, String contractType, String status, long updatedSince) {
        return contractRepository.count(islandUuid, contractType, status, updatedSince);
    }

    public void saveContract(StoredContract contract) {
        contractRepository.save(contract);
        coreStatePublisher.publishRow(contract.islandUuid(), IslandAddonService.tableStateKey("contracts", contract.contractId().toString()), contractJson(contract));
    }

    public void updateContractStatus(UUID contractId, String status, String progressJson) {
        StoredContract updated = contractRepository.updateStatus(contractId, status, progressJson).orElse(null);
        if (updated != null) {
            coreStatePublisher.publishRow(updated.islandUuid(), IslandAddonService.tableStateKey("contracts", updated.contractId().toString()), contractJson(updated));
        }
    }

    public Set<String> loadUnlocks(UUID islandUuid) {
        return researchRepository.loadUnlocks(islandUuid);
    }

    public void saveUnlock(UUID islandUuid, String unlockId) {
        long unlockedAt = researchRepository.saveUnlock(islandUuid, unlockId);
        coreStatePublisher.publishRow(islandUuid, IslandAddonService.tableStateKey("island_unlocks", unlockId), unlockJson(islandUuid, unlockId, unlockedAt));
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
        coreStatePublisher.publishTable(islandUuid, "item_networks", values);
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
        coreStatePublisher.publishTable(islandUuid, "power_networks", values);
    }

    private String itemNetworkJson(ItemNetwork network) {
        return "{"
                + field("networkId", network.networkId().toString()) + ","
                + field("islandUuid", network.islandUuid().toString()) + ","
                + number("throughputPerMinute", network.throughputPerMinute()) + ","
                + field("bufferInventoryId", network.bufferInventoryId() == null ? "" : network.bufferInventoryId().toString()) + ","
                + field("dirty", Boolean.toString(network.dirty())) + ","
                + number("updatedAt", network.updatedAt()) + ","
                + field("connectedMachineIds", uuidSetCsv(network.connectedMachineIds())) + ","
                + field("routes", routeCsv(network.routes()))
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

    private String routeCsv(List<ItemNetwork.Route> routes) {
        if (routes == null || routes.isEmpty()) {
            return "";
        }
        return routes.stream()
                .map(route -> route.fromMachineId() + "->" + route.toMachineId())
                .reduce((left, right) -> left + "," + right)
                .orElse("");
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
                + field("pendingMachineRemapWorld", island.pendingMachineRemapWorld()) + ","
                + number("pendingMachineRemapCenterX", island.pendingMachineRemapCenterX()) + ","
                + number("pendingMachineRemapCenterY", island.pendingMachineRemapCenterY()) + ","
                + number("pendingMachineRemapCenterZ", island.pendingMachineRemapCenterZ()) + ","
                + field("pendingResourceNodeRemapWorld", island.pendingResourceNodeRemapWorld()) + ","
                + number("pendingResourceNodeRemapCenterX", island.pendingResourceNodeRemapCenterX()) + ","
                + number("pendingResourceNodeRemapCenterY", island.pendingResourceNodeRemapCenterY()) + ","
                + number("pendingResourceNodeRemapCenterZ", island.pendingResourceNodeRemapCenterZ()) + ","
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
