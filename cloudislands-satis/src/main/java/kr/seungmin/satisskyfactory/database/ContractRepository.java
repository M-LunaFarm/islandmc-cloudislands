package kr.seungmin.satisskyfactory.database;

import java.sql.Connection;
import java.sql.PreparedStatement;
import java.sql.ResultSet;
import java.sql.SQLException;
import java.time.Instant;
import java.util.ArrayList;
import java.util.List;
import java.util.Optional;
import java.util.UUID;

final class ContractRepository {
    private final DatabaseService database;

    ContractRepository(DatabaseService database) {
        this.database = database;
    }

    List<DatabaseService.StoredContract> load(UUID islandUuid) {
        List<DatabaseService.StoredContract> contracts = new ArrayList<>();
        try (Connection connection = database.connection();
             PreparedStatement statement = connection.prepareStatement("SELECT * FROM contracts WHERE island_uuid = ?")) {
            statement.setString(1, islandUuid.toString());
            try (ResultSet rs = statement.executeQuery()) {
                while (rs.next()) {
                    contracts.add(storedContract(rs));
                }
            }
            return contracts;
        } catch (SQLException exception) {
            throw new IllegalStateException("Failed to publish contract core state", exception);
        }
    }

    List<DatabaseService.StoredContract> load(UUID islandUuid, String status) {
        List<DatabaseService.StoredContract> contracts = new ArrayList<>();
        try (Connection connection = database.connection();
             PreparedStatement statement = connection.prepareStatement("""
                     SELECT * FROM contracts WHERE island_uuid = ? AND status = ? ORDER BY created_at ASC
                     """)) {
            statement.setString(1, islandUuid.toString());
            statement.setString(2, status);
            try (ResultSet rs = statement.executeQuery()) {
                while (rs.next()) {
                    contracts.add(storedContract(rs));
                }
            }
            return contracts;
        } catch (SQLException exception) {
            throw new IllegalStateException("Failed to load contracts", exception);
        }
    }

    boolean existsForTemplate(UUID islandUuid, String templateId, String status) {
        try (Connection connection = database.connection();
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

    int count(UUID islandUuid, String contractType, String status, long updatedSince) {
        try (Connection connection = database.connection();
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

    void save(DatabaseService.StoredContract contract) {
        long now = Instant.now().toEpochMilli();
        try (Connection connection = database.connection();
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
    }

    Optional<DatabaseService.StoredContract> updateStatus(UUID contractId, String status, String progressJson) {
        try (Connection connection = database.connection();
             PreparedStatement statement = connection.prepareStatement("""
                     UPDATE contracts SET status = ?, progress_json = ?, updated_at = ? WHERE contract_id = ?
                     """)) {
            statement.setString(1, status);
            statement.setString(2, progressJson);
            statement.setLong(3, Instant.now().toEpochMilli());
            statement.setString(4, contractId.toString());
            statement.executeUpdate();
            return find(connection, contractId);
        } catch (SQLException exception) {
            throw new IllegalStateException("Failed to update contract", exception);
        }
    }

    private Optional<DatabaseService.StoredContract> find(Connection connection, UUID contractId) throws SQLException {
        try (PreparedStatement statement = connection.prepareStatement("SELECT * FROM contracts WHERE contract_id = ?")) {
            statement.setString(1, contractId.toString());
            try (ResultSet rs = statement.executeQuery()) {
                if (!rs.next()) {
                    return Optional.empty();
                }
                return Optional.of(storedContract(rs));
            }
        }
    }

    private DatabaseService.StoredContract storedContract(ResultSet rs) throws SQLException {
        return new DatabaseService.StoredContract(
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

    private String saveContractSql() {
        if (database.usesMysqlDialect()) {
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
}
