package kr.seungmin.satisskyfactory.database;

import java.sql.Connection;
import java.sql.PreparedStatement;
import java.sql.ResultSet;
import java.sql.SQLException;
import java.time.Instant;
import java.util.ArrayList;
import java.util.List;
import java.util.UUID;

final class LedgerRepository {
    private final DatabaseService database;

    LedgerRepository(DatabaseService database) {
        this.database = database;
    }

    List<LedgerEntry> load(UUID islandUuid) {
        List<LedgerEntry> entries = new ArrayList<>();
        try (Connection connection = database.connection();
             PreparedStatement statement = connection.prepareStatement("SELECT ledger_id, type, amount, reason, created_at FROM ledger WHERE island_uuid = ?")) {
            statement.setString(1, islandUuid.toString());
            try (ResultSet rs = statement.executeQuery()) {
                while (rs.next()) {
                    entries.add(new LedgerEntry(
                            UUID.fromString(rs.getString("ledger_id")),
                            islandUuid,
                            rs.getString("type"),
                            rs.getLong("amount"),
                            rs.getString("reason"),
                            rs.getLong("created_at")
                    ));
                }
            }
            return entries;
        } catch (SQLException exception) {
            throw new IllegalStateException("Failed to publish ledger core state", exception);
        }
    }

    LedgerEntry add(UUID islandUuid, String type, long amount, String reason) {
        UUID ledgerId = UUID.randomUUID();
        long now = Instant.now().toEpochMilli();
        try (Connection connection = database.connection();
             PreparedStatement statement = connection.prepareStatement("INSERT INTO ledger(ledger_id, island_uuid, type, amount, reason, created_at) VALUES(?, ?, ?, ?, ?, ?)")) {
            statement.setString(1, ledgerId.toString());
            statement.setString(2, islandUuid.toString());
            statement.setString(3, type);
            statement.setLong(4, amount);
            statement.setString(5, reason);
            statement.setLong(6, now);
            statement.executeUpdate();
            return new LedgerEntry(ledgerId, islandUuid, type, amount, reason, now);
        } catch (SQLException exception) {
            throw new IllegalStateException("Failed to write ledger", exception);
        }
    }

    DatabaseService.EconomyLedgerClaim beginEconomyLedger(UUID islandUuid, UUID playerUuid, String operation, long amount,
                                                         String reason, String idempotencyKey) {
        if (islandUuid == null || idempotencyKey == null || idempotencyKey.isBlank()) {
            throw new IllegalArgumentException("Economy ledger requires island UUID and idempotency key");
        }
        long now = Instant.now().toEpochMilli();
        try (Connection connection = database.connection();
             PreparedStatement statement = connection.prepareStatement(insertEconomyLedgerSql())) {
            statement.setString(1, idempotencyKey);
            statement.setString(2, islandUuid.toString());
            statement.setString(3, playerUuid == null ? "" : playerUuid.toString());
            statement.setString(4, operation == null ? "" : operation);
            statement.setLong(5, amount);
            statement.setString(6, "PENDING");
            statement.setString(7, reason == null ? "" : reason);
            statement.setLong(8, now);
            statement.setLong(9, 0L);
            if (statement.executeUpdate() > 0) {
                return DatabaseService.EconomyLedgerClaim.STARTED;
            }
            DatabaseService.EconomyLedgerClaim claim = economyLedgerClaim(connection, idempotencyKey);
            if (claim == DatabaseService.EconomyLedgerClaim.FAILED && retryFailedEconomyLedger(connection, idempotencyKey)) {
                return DatabaseService.EconomyLedgerClaim.STARTED;
            }
            return claim;
        } catch (SQLException exception) {
            throw new IllegalStateException("Failed to begin economy ledger", exception);
        }
    }

    DatabaseService.EconomyLedgerClaim economyLedgerClaim(String idempotencyKey) {
        try (Connection connection = database.connection()) {
            return economyLedgerClaim(connection, idempotencyKey);
        } catch (SQLException exception) {
            throw new IllegalStateException("Failed to read economy ledger", exception);
        }
    }

    void completeEconomyLedger(String idempotencyKey) {
        updateEconomyLedgerStatus(idempotencyKey, "COMPLETED", Instant.now().toEpochMilli());
    }

    void failEconomyLedger(String idempotencyKey) {
        updateEconomyLedgerStatus(idempotencyKey, "FAILED", 0L);
    }

    void compensateEconomyLedger(String idempotencyKey) {
        updateEconomyLedgerStatus(idempotencyKey, "NEEDS_COMPENSATION", 0L);
    }

    void saveSnapshot(UUID ledgerId, UUID islandUuid, String type, long amount, String reason, long createdAt) {
        try (Connection connection = database.connection();
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

    private String insertEconomyLedgerSql() {
        if (database.usesMysqlDialect()) {
            return """
                    INSERT IGNORE INTO satis_economy_ledger(idempotency_key, island_uuid, player_uuid, operation, amount, status, reason, created_at, completed_at)
                    VALUES(?, ?, ?, ?, ?, ?, ?, ?, ?)
                    """;
        }
        return """
                INSERT INTO satis_economy_ledger(idempotency_key, island_uuid, player_uuid, operation, amount, status, reason, created_at, completed_at)
                VALUES(?, ?, ?, ?, ?, ?, ?, ?, ?)
                ON CONFLICT(idempotency_key) DO NOTHING
                """;
    }

    private DatabaseService.EconomyLedgerClaim economyLedgerClaim(Connection connection, String idempotencyKey) throws SQLException {
        try (PreparedStatement statement = connection.prepareStatement("SELECT status FROM satis_economy_ledger WHERE idempotency_key = ?")) {
            statement.setString(1, idempotencyKey);
            try (ResultSet rs = statement.executeQuery()) {
                if (!rs.next()) {
                    return DatabaseService.EconomyLedgerClaim.FAILED;
                }
                return switch (rs.getString("status")) {
                    case "COMPLETED" -> DatabaseService.EconomyLedgerClaim.COMPLETED;
                    case "FAILED" -> DatabaseService.EconomyLedgerClaim.FAILED;
                    case "NEEDS_COMPENSATION" -> DatabaseService.EconomyLedgerClaim.NEEDS_COMPENSATION;
                    default -> DatabaseService.EconomyLedgerClaim.PENDING;
                };
            }
        }
    }

    private boolean retryFailedEconomyLedger(Connection connection, String idempotencyKey) throws SQLException {
        try (PreparedStatement statement = connection.prepareStatement("""
                UPDATE satis_economy_ledger
                SET status = 'PENDING', completed_at = 0
                WHERE idempotency_key = ? AND status = 'FAILED'
                """)) {
            statement.setString(1, idempotencyKey);
            return statement.executeUpdate() > 0;
        }
    }

    private void updateEconomyLedgerStatus(String idempotencyKey, String status, long completedAt) {
        if (idempotencyKey == null || idempotencyKey.isBlank()) {
            return;
        }
        try (Connection connection = database.connection();
             PreparedStatement statement = connection.prepareStatement("""
                     UPDATE satis_economy_ledger
                     SET status = ?, completed_at = ?
                     WHERE idempotency_key = ? AND status <> 'COMPLETED'
                     """)) {
            statement.setString(1, status);
            statement.setLong(2, completedAt);
            statement.setString(3, idempotencyKey);
            statement.executeUpdate();
        } catch (SQLException exception) {
            throw new IllegalStateException("Failed to update economy ledger", exception);
        }
    }

    private String saveLedgerSnapshotSql() {
        if (database.usesMysqlDialect()) {
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

    record LedgerEntry(UUID ledgerId, UUID islandUuid, String type, long amount, String reason, long createdAt) {
    }
}
