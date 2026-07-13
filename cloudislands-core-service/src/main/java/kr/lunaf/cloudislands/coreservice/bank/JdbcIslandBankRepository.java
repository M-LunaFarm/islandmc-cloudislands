package kr.lunaf.cloudislands.coreservice.bank;

import java.math.BigDecimal;
import java.sql.Connection;
import java.sql.PreparedStatement;
import java.sql.ResultSet;
import java.sql.SQLException;
import java.time.Instant;
import java.util.UUID;
import java.util.ArrayList;
import java.util.List;
import javax.sql.DataSource;
import kr.lunaf.cloudislands.api.model.IslandBankSnapshot;

public final class JdbcIslandBankRepository implements IslandBankRepository {
    private final DataSource dataSource;

    public JdbcIslandBankRepository(DataSource dataSource) {
        this.dataSource = dataSource;
    }

    @Override
    public IslandBankSnapshot balance(UUID islandId) {
        try (Connection connection = dataSource.getConnection()) {
            ensureRow(connection, islandId);
            return snapshot(connection, islandId);
        } catch (SQLException exception) {
            throw new IllegalStateException("failed to read island bank", exception);
        }
    }

    @Override
    public List<IslandBankSnapshot> topBalances(int limit) {
        int boundedLimit = Math.max(1, Math.min(100, limit));
        String sql = "SELECT b.island_id, b.balance, b.updated_at FROM island_bank b "
            + "LEFT JOIN island_rank_snapshots r ON r.island_id = b.island_id "
            + "WHERE COALESCE(r.ignored, false) = false "
            + "ORDER BY b.balance DESC, b.updated_at ASC, b.island_id ASC LIMIT ?";
        try (Connection connection = dataSource.getConnection();
             PreparedStatement statement = connection.prepareStatement(sql)) {
            statement.setInt(1, boundedLimit);
            try (ResultSet rs = statement.executeQuery()) {
                List<IslandBankSnapshot> rankings = new ArrayList<>();
                while (rs.next()) {
                    rankings.add(new IslandBankSnapshot(
                        UUID.fromString(rs.getString("island_id")),
                        rs.getBigDecimal("balance").toPlainString(),
                        rs.getTimestamp("updated_at").toInstant()
                    ));
                }
                return List.copyOf(rankings);
            }
        } catch (SQLException exception) {
            throw new IllegalStateException("failed to read island bank rankings", exception);
        }
    }

    @Override
    public IslandBankSnapshot deposit(UUID islandId, BigDecimal amount) {
        return deposit(islandId, amount, IslandBankRepository.MAX_STORABLE_BALANCE).snapshot();
    }

    @Override
    public BankChangeResult deposit(UUID islandId, BigDecimal amount, BigDecimal maxBalance) {
        BigDecimal normalized = IslandBankRepository.normalizeAmount(amount);
        if (normalized == null) {
            return new BankChangeResult(false, "INVALID_AMOUNT", balance(islandId));
        }
        try (Connection connection = dataSource.getConnection()) {
            connection.setAutoCommit(false);
            ensureRow(connection, islandId);
            BigDecimal current = lockedBalance(connection, islandId);
            BigDecimal limit = IslandBankRepository.effectiveMaxBalance(maxBalance);
            if (current.add(normalized).compareTo(limit) > 0) {
                connection.rollback();
                return new BankChangeResult(false, "BANK_LIMIT", new IslandBankSnapshot(islandId, current.toPlainString(), Instant.now()));
            }
            try (PreparedStatement statement = connection.prepareStatement("UPDATE island_bank SET balance = balance + ?, updated_at = now() WHERE island_id = ?")) {
                statement.setBigDecimal(1, normalized);
                statement.setObject(2, islandId);
                statement.executeUpdate();
            }
            IslandBankSnapshot snapshot = snapshot(connection, islandId);
            connection.commit();
            return new BankChangeResult(true, "DEPOSITED", snapshot);
        } catch (SQLException exception) {
            throw new IllegalStateException("failed to deposit island bank", exception);
        }
    }

    @Override
    public BankChangeResult withdraw(UUID islandId, BigDecimal amount) {
        BigDecimal normalized = IslandBankRepository.normalizeAmount(amount);
        if (normalized == null) {
            return new BankChangeResult(false, "INVALID_AMOUNT", balance(islandId));
        }
        try (Connection connection = dataSource.getConnection()) {
            connection.setAutoCommit(false);
            ensureRow(connection, islandId);
            BigDecimal current = lockedBalance(connection, islandId);
            if (current.compareTo(normalized) < 0) {
                connection.rollback();
                return new BankChangeResult(false, "INSUFFICIENT_FUNDS", new IslandBankSnapshot(islandId, current.toPlainString(), Instant.now()));
            }
            try (PreparedStatement statement = connection.prepareStatement("UPDATE island_bank SET balance = balance - ?, updated_at = now() WHERE island_id = ?")) {
                statement.setBigDecimal(1, normalized);
                statement.setObject(2, islandId);
                statement.executeUpdate();
            }
            IslandBankSnapshot snapshot = snapshot(connection, islandId);
            connection.commit();
            return new BankChangeResult(true, "WITHDRAWN", snapshot);
        } catch (SQLException exception) {
            throw new IllegalStateException("failed to withdraw island bank", exception);
        }
    }

    private void ensureRow(Connection connection, UUID islandId) throws SQLException {
        try (PreparedStatement statement = connection.prepareStatement(ensureRowSql(connection))) {
            statement.setObject(1, islandId);
            statement.executeUpdate();
        }
    }

    private String ensureRowSql(Connection connection) throws SQLException {
        if (mysqlLike(connection)) {
            return "INSERT IGNORE INTO island_bank(island_id) VALUES (?)";
        }
        return "INSERT INTO island_bank(island_id) VALUES (?) ON CONFLICT (island_id) DO NOTHING";
    }

    private boolean mysqlLike(Connection connection) throws SQLException {
        String product = connection.getMetaData().getDatabaseProductName();
        String normalized = product == null ? "" : product.toLowerCase(java.util.Locale.ROOT);
        return normalized.contains("mysql") || normalized.contains("mariadb");
    }

    private BigDecimal lockedBalance(Connection connection, UUID islandId) throws SQLException {
        try (PreparedStatement statement = connection.prepareStatement("SELECT balance FROM island_bank WHERE island_id = ? FOR UPDATE")) {
            statement.setObject(1, islandId);
            try (ResultSet rs = statement.executeQuery()) {
                return rs.next() ? rs.getBigDecimal("balance") : BigDecimal.ZERO;
            }
        }
    }

    private IslandBankSnapshot snapshot(Connection connection, UUID islandId) throws SQLException {
        try (PreparedStatement statement = connection.prepareStatement("SELECT balance, updated_at FROM island_bank WHERE island_id = ?")) {
            statement.setObject(1, islandId);
            try (ResultSet rs = statement.executeQuery()) {
                if (!rs.next()) {
                    return new IslandBankSnapshot(islandId, "0", Instant.EPOCH);
                }
                return new IslandBankSnapshot(islandId, rs.getBigDecimal("balance").toPlainString(), rs.getTimestamp("updated_at").toInstant());
            }
        }
    }
}
