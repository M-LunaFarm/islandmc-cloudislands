package kr.seungmin.satisskyfactory.storage;

import java.sql.Connection;
import java.sql.DatabaseMetaData;
import java.sql.PreparedStatement;
import java.sql.ResultSet;
import java.sql.SQLException;
import java.sql.Statement;

public final class MigrationService {
    public enum Dialect {
        SQLITE,
        POSTGRESQL,
        MYSQL
    }

    public void migrate(Connection connection, Dialect dialect) throws SQLException {
        try (Statement statement = connection.createStatement()) {
            statement.executeUpdate(ddl("CREATE TABLE IF NOT EXISTS schema_version (version INTEGER NOT NULL)", dialect));
            try (ResultSet rs = statement.executeQuery("SELECT COUNT(*) AS count FROM schema_version")) {
                if (rs.next() && rs.getInt("count") == 0) {
                    statement.executeUpdate("INSERT INTO schema_version(version) VALUES (1)");
                }
            }
            statement.executeUpdate(ddl("""
                    CREATE TABLE IF NOT EXISTS factory_islands (
                      island_uuid TEXT PRIMARY KEY,
                      owner_uuid TEXT NOT NULL,
                      tier INTEGER NOT NULL DEFAULT 1,
                      research_points INTEGER NOT NULL DEFAULT 0,
                      reputation INTEGER NOT NULL DEFAULT 0,
                      maintenance_debt INTEGER NOT NULL DEFAULT 0,
                      maintenance_status TEXT NOT NULL DEFAULT 'NORMAL',
                      factory_score INTEGER NOT NULL DEFAULT 0,
                      last_maintenance_at INTEGER NOT NULL DEFAULT 0,
                      last_tick_at INTEGER NOT NULL DEFAULT 0,
                      emergency_contracts_used_today INTEGER NOT NULL DEFAULT 0,
                      active_world TEXT NOT NULL DEFAULT '',
                      active_center_x INTEGER NOT NULL DEFAULT 0,
                      active_center_y INTEGER NOT NULL DEFAULT 0,
                      active_center_z INTEGER NOT NULL DEFAULT 0,
                      created_at INTEGER NOT NULL,
                      updated_at INTEGER NOT NULL
                    )
                    """, dialect));
            statement.executeUpdate(ddl("""
                    CREATE TABLE IF NOT EXISTS machines (
                      machine_id TEXT PRIMARY KEY,
                      island_uuid TEXT NOT NULL,
                      owner_uuid TEXT NOT NULL,
                      type_id TEXT NOT NULL,
                      tier INTEGER NOT NULL,
                      world TEXT NOT NULL,
                      x INTEGER NOT NULL,
                      y INTEGER NOT NULL,
                      z INTEGER NOT NULL,
                      direction TEXT NOT NULL,
                      status TEXT NOT NULL,
                      input_inventory_id TEXT,
                      output_inventory_id TEXT,
                      power_network_id TEXT,
                      item_network_id TEXT,
                      linked_resource_node_id TEXT,
                      last_process_at INTEGER NOT NULL,
                      wear REAL NOT NULL DEFAULT 0,
                      config_json TEXT NOT NULL DEFAULT '{}',
                      created_at INTEGER NOT NULL,
                      updated_at INTEGER NOT NULL
                    )
                    """, dialect));
            createIndexIfMissing(connection, statement, "idx_machines_location", "machines",
                    "CREATE UNIQUE INDEX idx_machines_location ON machines(world, x, y, z)");
            createIndexIfMissing(connection, statement, "idx_machines_island", "machines",
                    "CREATE INDEX idx_machines_island ON machines(island_uuid)");
            statement.executeUpdate(ddl("""
                    CREATE TABLE IF NOT EXISTS virtual_inventories (
                      inventory_id TEXT PRIMARY KEY,
                      island_uuid TEXT NOT NULL,
                      holder_type TEXT NOT NULL,
                      holder_id TEXT NOT NULL,
                      capacity INTEGER NOT NULL,
                      created_at INTEGER NOT NULL,
                      updated_at INTEGER NOT NULL
                    )
                    """, dialect));
            statement.executeUpdate(ddl("""
                    CREATE TABLE IF NOT EXISTS virtual_inventory_items (
                      inventory_id TEXT NOT NULL,
                      item_id TEXT NOT NULL,
                      amount INTEGER NOT NULL,
                      PRIMARY KEY(inventory_id, item_id)
                    )
                    """, dialect));
            statement.executeUpdate(ddl("""
                    CREATE TABLE IF NOT EXISTS resource_nodes (
                      node_id TEXT PRIMARY KEY,
                      island_uuid TEXT NOT NULL,
                      node_type TEXT NOT NULL,
                      resource_id TEXT NOT NULL,
                      purity REAL NOT NULL,
                      remaining INTEGER NOT NULL,
                      max_remaining INTEGER NOT NULL,
                      regen_per_hour INTEGER NOT NULL,
                      required_machine_tier INTEGER NOT NULL,
                      world TEXT NOT NULL,
                      x INTEGER NOT NULL,
                      y INTEGER NOT NULL,
                      z INTEGER NOT NULL,
                      created_at INTEGER NOT NULL,
                      updated_at INTEGER NOT NULL
                    )
                    """, dialect));
            statement.executeUpdate(ddl("""
                    CREATE TABLE IF NOT EXISTS power_networks (
                      network_id TEXT PRIMARY KEY,
                      island_uuid TEXT NOT NULL,
                      generation_per_second REAL NOT NULL DEFAULT 0,
                      consumption_per_second REAL NOT NULL DEFAULT 0,
                      battery_stored REAL NOT NULL DEFAULT 0,
                      battery_capacity REAL NOT NULL DEFAULT 0,
                      power_ratio REAL NOT NULL DEFAULT 1,
                      updated_at INTEGER NOT NULL
                    )
                    """, dialect));
            statement.executeUpdate(ddl("""
                    CREATE TABLE IF NOT EXISTS item_networks (
                      network_id TEXT PRIMARY KEY,
                      island_uuid TEXT NOT NULL,
                      throughput_per_minute INTEGER NOT NULL,
                      buffer_inventory_id TEXT,
                      dirty INTEGER NOT NULL DEFAULT 0,
                      updated_at INTEGER NOT NULL
                    )
                    """, dialect));
            statement.executeUpdate(ddl("""
                    CREATE TABLE IF NOT EXISTS machine_network_links (
                      machine_id TEXT NOT NULL,
                      network_id TEXT NOT NULL,
                      network_type TEXT NOT NULL,
                      PRIMARY KEY(machine_id, network_id, network_type)
                    )
                    """, dialect));
            statement.executeUpdate(ddl("""
                    CREATE TABLE IF NOT EXISTS market_daily (
                      item_id TEXT NOT NULL,
                      date_key TEXT NOT NULL,
                      sold_amount INTEGER NOT NULL DEFAULT 0,
                      demand_factor REAL NOT NULL DEFAULT 1,
                      PRIMARY KEY(item_id, date_key)
                    )
                    """, dialect));
            statement.executeUpdate(ddl("""
                    CREATE TABLE IF NOT EXISTS market_personal_daily (
                      island_uuid TEXT NOT NULL,
                      item_id TEXT NOT NULL,
                      date_key TEXT NOT NULL,
                      sold_amount INTEGER NOT NULL DEFAULT 0,
                      PRIMARY KEY(island_uuid, item_id, date_key)
                    )
                    """, dialect));
            statement.executeUpdate(ddl("""
                    CREATE TABLE IF NOT EXISTS contracts (
                      contract_id TEXT PRIMARY KEY,
                      island_uuid TEXT NOT NULL,
                      template_id TEXT NOT NULL,
                      contract_type TEXT NOT NULL,
                      tier INTEGER NOT NULL,
                      required_json TEXT NOT NULL,
                      progress_json TEXT NOT NULL,
                      rewards_json TEXT NOT NULL,
                      status TEXT NOT NULL,
                      expires_at INTEGER NOT NULL,
                      created_at INTEGER NOT NULL,
                      updated_at INTEGER NOT NULL
                    )
                    """, dialect));
            statement.executeUpdate(ddl("""
                    CREATE TABLE IF NOT EXISTS island_unlocks (
                      island_uuid TEXT NOT NULL,
                      unlock_id TEXT NOT NULL,
                      unlocked_at INTEGER NOT NULL,
                      PRIMARY KEY(island_uuid, unlock_id)
                    )
                    """, dialect));
            statement.executeUpdate(ddl("""
                    CREATE TABLE IF NOT EXISTS ledger (
                      ledger_id TEXT PRIMARY KEY,
                      island_uuid TEXT NOT NULL,
                      type TEXT NOT NULL,
                      amount INTEGER NOT NULL,
                      reason TEXT NOT NULL,
                      created_at INTEGER NOT NULL
                    )
                    """, dialect));
            applyIncrementalMigrations(connection, statement, dialect);
        }
    }

    private void applyIncrementalMigrations(Connection connection, Statement statement, Dialect dialect) throws SQLException {
        addColumnIfMissing(connection, statement, "factory_islands", "emergency_contracts_used_today",
                "INTEGER NOT NULL DEFAULT 0", dialect);
        addColumnIfMissing(connection, statement, "factory_islands", "active_world", "TEXT NOT NULL DEFAULT ''", dialect);
        addColumnIfMissing(connection, statement, "factory_islands", "active_center_x", "INTEGER NOT NULL DEFAULT 0", dialect);
        addColumnIfMissing(connection, statement, "factory_islands", "active_center_y", "INTEGER NOT NULL DEFAULT 0", dialect);
        addColumnIfMissing(connection, statement, "factory_islands", "active_center_z", "INTEGER NOT NULL DEFAULT 0", dialect);
        addColumnIfMissing(connection, statement, "machines", "power_network_id", "TEXT", dialect);
        addColumnIfMissing(connection, statement, "machines", "item_network_id", "TEXT", dialect);
        addColumnIfMissing(connection, statement, "machines", "linked_resource_node_id", "TEXT", dialect);
        addColumnIfMissing(connection, statement, "machines", "config_json", "TEXT NOT NULL DEFAULT '{}'", dialect);
        addColumnIfMissing(connection, statement, "machines", "wear", "REAL NOT NULL DEFAULT 0", dialect);
        statement.executeUpdate("UPDATE schema_version SET version = 3");
    }

    private void addColumnIfMissing(Connection connection, Statement statement, String table, String column, String definition,
                                    Dialect dialect)
            throws SQLException {
        if (!hasColumn(connection, table, column)) {
            statement.executeUpdate(ddl("ALTER TABLE " + table + " ADD COLUMN " + column + " " + definition, dialect));
        }
    }

    private boolean hasColumn(Connection connection, String table, String column) throws SQLException {
        DatabaseMetaData metaData = connection.getMetaData();
        try (ResultSet rs = metaData.getColumns(connection.getCatalog(), null, table, column)) {
            while (rs.next()) {
                if (column.equalsIgnoreCase(rs.getString("COLUMN_NAME"))) {
                    return true;
                }
            }
        }
        try (ResultSet rs = metaData.getColumns(connection.getCatalog(), null, table.toUpperCase(), column.toUpperCase())) {
            return rs.next();
        }
    }

    private void createIndexIfMissing(Connection connection, Statement statement, String index, String table, String sql)
            throws SQLException {
        DatabaseMetaData metaData = connection.getMetaData();
        try (ResultSet rs = metaData.getIndexInfo(connection.getCatalog(), null, table, false, false)) {
            while (rs.next()) {
                if (index.equalsIgnoreCase(rs.getString("INDEX_NAME"))) {
                    return;
                }
            }
        }
        statement.executeUpdate(sql);
    }

    private String ddl(String sql, Dialect dialect) {
        if (dialect != Dialect.MYSQL) {
            return sql;
        }
        return sql
                .replace("island_uuid TEXT", "island_uuid VARCHAR(36)")
                .replace("owner_uuid TEXT", "owner_uuid VARCHAR(36)")
                .replace("machine_id TEXT", "machine_id VARCHAR(36)")
                .replace("inventory_id TEXT", "inventory_id VARCHAR(36)")
                .replace("node_id TEXT", "node_id VARCHAR(36)")
                .replace("network_id TEXT", "network_id VARCHAR(36)")
                .replace("contract_id TEXT", "contract_id VARCHAR(36)")
                .replace("ledger_id TEXT", "ledger_id VARCHAR(36)")
                .replace("input_inventory_id TEXT", "input_inventory_id VARCHAR(36)")
                .replace("output_inventory_id TEXT", "output_inventory_id VARCHAR(36)")
                .replace("power_network_id TEXT", "power_network_id VARCHAR(36)")
                .replace("item_network_id TEXT", "item_network_id VARCHAR(36)")
                .replace("linked_resource_node_id TEXT", "linked_resource_node_id VARCHAR(36)")
                .replace("buffer_inventory_id TEXT", "buffer_inventory_id VARCHAR(36)")
                .replace("world TEXT", "world VARCHAR(255)")
                .replace("active_world TEXT", "active_world VARCHAR(255)")
                .replace("holder_type TEXT", "holder_type VARCHAR(64)")
                .replace("holder_id TEXT", "holder_id VARCHAR(128)")
                .replace("type_id TEXT", "type_id VARCHAR(128)")
                .replace("direction TEXT", "direction VARCHAR(32)")
                .replace("status TEXT", "status VARCHAR(64)")
                .replace("node_type TEXT", "node_type VARCHAR(64)")
                .replace("resource_id TEXT", "resource_id VARCHAR(128)")
                .replace("network_type TEXT", "network_type VARCHAR(32)")
                .replace("item_id TEXT", "item_id VARCHAR(128)")
                .replace("date_key TEXT", "date_key VARCHAR(32)")
                .replace("template_id TEXT", "template_id VARCHAR(128)")
                .replace("contract_type TEXT", "contract_type VARCHAR(64)")
                .replace("unlock_id TEXT", "unlock_id VARCHAR(128)")
                .replace("maintenance_status TEXT", "maintenance_status VARCHAR(64)")
                .replace("config_json TEXT NOT NULL DEFAULT '{}'", "config_json VARCHAR(2048) NOT NULL DEFAULT '{}'");
    }
}
