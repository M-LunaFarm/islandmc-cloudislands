package kr.lunaf.cloudislands.coreservice.warehouse;

import java.sql.Connection;
import java.sql.PreparedStatement;
import java.sql.ResultSet;
import java.sql.SQLException;
import java.sql.Timestamp;
import java.time.Instant;
import java.util.Locale;
import java.util.Optional;
import java.util.UUID;
import javax.sql.DataSource;

public final class JdbcWarehouseSettlementRepository implements WarehouseSettlementRepository {
    private final DataSource dataSource;

    public JdbcWarehouseSettlementRepository(DataSource dataSource) {
        if (dataSource == null) {
            throw new IllegalArgumentException("dataSource is required");
        }
        this.dataSource = dataSource;
    }

    @Override
    public PrepareResult prepare(WarehouseSettlementRecord settlement) {
        try (Connection connection = dataSource.getConnection()) {
            String sql = mysqlLike(connection)
                ? "INSERT IGNORE INTO warehouse_settlements(player_uuid, settlement_id, island_id, material_key, amount, direction, state, idempotency_key, owner_node_id) VALUES (?, ?, ?, ?, ?, ?, 'PREPARED', ?, ?)"
                : "INSERT INTO warehouse_settlements(player_uuid, settlement_id, island_id, material_key, amount, direction, state, idempotency_key, owner_node_id) VALUES (?, ?, ?, ?, ?, ?, 'PREPARED', ?, ?) ON CONFLICT (player_uuid) DO NOTHING";
            try (PreparedStatement statement = connection.prepareStatement(sql)) {
                setUuid(statement, 1, settlement.playerUuid(), connection);
                setUuid(statement, 2, settlement.settlementId(), connection);
                setUuid(statement, 3, settlement.islandId(), connection);
                statement.setString(4, settlement.materialKey());
                statement.setLong(5, settlement.amount());
                statement.setString(6, settlement.direction().name());
                statement.setString(7, settlement.idempotencyKey());
                statement.setString(8, settlement.ownerNodeId());
                if (statement.executeUpdate() == 1) {
                    return new PrepareResult(true, "WAREHOUSE_SETTLEMENT_PREPARED", find(connection, settlement.playerUuid()).orElse(settlement));
                }
            }
            WarehouseSettlementRecord current = find(connection, settlement.playerUuid()).orElse(null);
            if (current != null && current.sameOperation(settlement)) {
                return new PrepareResult(true, "WAREHOUSE_SETTLEMENT_EXISTS", current);
            }
            return new PrepareResult(false, "WAREHOUSE_SETTLEMENT_CONFLICT", current);
        } catch (SQLException exception) {
            throw new IllegalStateException("failed to prepare warehouse settlement", exception);
        }
    }

    @Override
    public TransitionResult markEscrowed(UUID playerUuid, UUID settlementId) {
        try (Connection connection = dataSource.getConnection();
             PreparedStatement statement = connection.prepareStatement(
                 "UPDATE warehouse_settlements SET state = 'ESCROWED', updated_at = now() WHERE player_uuid = ? AND settlement_id = ? AND state = 'PREPARED'"
             )) {
            setUuid(statement, 1, playerUuid, connection);
            setUuid(statement, 2, settlementId, connection);
            statement.executeUpdate();
            WarehouseSettlementRecord current = find(connection, playerUuid).orElse(null);
            boolean accepted = current != null
                && current.settlementId().equals(settlementId)
                && current.state() == WarehouseSettlementRecord.State.ESCROWED;
            return new TransitionResult(accepted, accepted ? "WAREHOUSE_SETTLEMENT_ESCROWED" : "WAREHOUSE_SETTLEMENT_NOT_FOUND", current);
        } catch (SQLException exception) {
            throw new IllegalStateException("failed to escrow warehouse settlement", exception);
        }
    }

    @Override
    public Optional<WarehouseSettlementRecord> find(UUID playerUuid) {
        try (Connection connection = dataSource.getConnection()) {
            return find(connection, playerUuid);
        } catch (SQLException exception) {
            throw new IllegalStateException("failed to find warehouse settlement", exception);
        }
    }

    @Override
    public boolean clear(UUID playerUuid, UUID settlementId) {
        try (Connection connection = dataSource.getConnection();
             PreparedStatement statement = connection.prepareStatement("DELETE FROM warehouse_settlements WHERE player_uuid = ? AND settlement_id = ?")) {
            setUuid(statement, 1, playerUuid, connection);
            setUuid(statement, 2, settlementId, connection);
            return statement.executeUpdate() == 1;
        } catch (SQLException exception) {
            throw new IllegalStateException("failed to clear warehouse settlement", exception);
        }
    }

    private Optional<WarehouseSettlementRecord> find(Connection connection, UUID playerUuid) throws SQLException {
        try (PreparedStatement statement = connection.prepareStatement(
            "SELECT settlement_id, player_uuid, island_id, material_key, amount, direction, state, idempotency_key, owner_node_id, created_at, updated_at FROM warehouse_settlements WHERE player_uuid = ?"
        )) {
            setUuid(statement, 1, playerUuid, connection);
            try (ResultSet result = statement.executeQuery()) {
                return result.next() ? Optional.of(record(result)) : Optional.empty();
            }
        }
    }

    private static WarehouseSettlementRecord record(ResultSet result) throws SQLException {
        return new WarehouseSettlementRecord(
            uuid(result.getObject("settlement_id")),
            uuid(result.getObject("player_uuid")),
            uuid(result.getObject("island_id")),
            result.getString("material_key"),
            result.getLong("amount"),
            WarehouseSettlementRecord.Direction.parse(result.getString("direction")),
            WarehouseSettlementRecord.State.valueOf(result.getString("state")),
            result.getString("idempotency_key"),
            result.getString("owner_node_id"),
            instant(result.getTimestamp("created_at")),
            instant(result.getTimestamp("updated_at"))
        );
    }

    private static void setUuid(PreparedStatement statement, int index, UUID value, Connection connection) throws SQLException {
        if (mysqlLike(connection)) {
            statement.setString(index, value.toString());
        } else {
            statement.setObject(index, value);
        }
    }

    private static UUID uuid(Object value) {
        return value instanceof UUID uuid ? uuid : UUID.fromString(String.valueOf(value));
    }

    private static Instant instant(Timestamp value) {
        return value == null ? Instant.EPOCH : value.toInstant();
    }

    private static boolean mysqlLike(Connection connection) throws SQLException {
        String product = connection.getMetaData().getDatabaseProductName();
        String normalized = product == null ? "" : product.toLowerCase(Locale.ROOT);
        return normalized.contains("mysql") || normalized.contains("mariadb");
    }
}
