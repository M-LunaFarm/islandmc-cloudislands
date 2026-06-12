package kr.lunaf.cloudislands.coreservice.bank;

import java.math.BigDecimal;
import java.sql.Connection;
import java.sql.PreparedStatement;
import java.sql.ResultSet;
import java.sql.SQLException;
import java.time.Instant;
import java.util.UUID;
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
    public IslandBankSnapshot deposit(UUID islandId, BigDecimal amount) {
        try (Connection connection = dataSource.getConnection()) {
            ensureRow(connection, islandId);
            if (amount.signum() > 0) {
                try (PreparedStatement statement = connection.prepareStatement("UPDATE island_bank SET balance = balance + ?, updated_at = now() WHERE island_id = ?")) {
                    statement.setBigDecimal(1, amount);
                    statement.setObject(2, islandId);
                    statement.executeUpdate();
                }
            }
            return snapshot(connection, islandId);
        } catch (SQLException exception) {
            throw new IllegalStateException("failed to deposit island bank", exception);
        }
    }

    @Override
    public BankChangeResult deposit(UUID islandId, BigDecimal amount, BigDecimal maxBalance) {
        if (amount.signum() <= 0) {
            return new BankChangeResult(false, "INVALID_AMOUNT", balance(islandId));
        }
        try (Connection connection = dataSource.getConnection()) {
            connection.setAutoCommit(false);
            ensureRow(connection, islandId);
            BigDecimal current = lockedBalance(connection, islandId);
            if (maxBalance != null && current.add(amount).compareTo(maxBalance) > 0) {
                connection.rollback();
                return new BankChangeResult(false, "BANK_LIMIT", new IslandBankSnapshot(islandId, current.toPlainString(), Instant.now()));
            }
            try (PreparedStatement statement = connection.prepareStatement("UPDATE island_bank SET balance = balance + ?, updated_at = now() WHERE island_id = ?")) {
                statement.setBigDecimal(1, amount);
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
        if (amount.signum() <= 0) {
            return new BankChangeResult(false, "INVALID_AMOUNT", balance(islandId));
        }
        try (Connection connection = dataSource.getConnection()) {
            connection.setAutoCommit(false);
            ensureRow(connection, islandId);
            BigDecimal current = lockedBalance(connection, islandId);
            if (current.compareTo(amount) < 0) {
                connection.rollback();
                return new BankChangeResult(false, "INSUFFICIENT_FUNDS", new IslandBankSnapshot(islandId, current.toPlainString(), Instant.now()));
            }
            try (PreparedStatement statement = connection.prepareStatement("UPDATE island_bank SET balance = balance - ?, updated_at = now() WHERE island_id = ?")) {
                statement.setBigDecimal(1, amount);
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
        try (PreparedStatement statement = connection.prepareStatement("INSERT INTO island_bank(island_id) VALUES (?) ON CONFLICT (island_id) DO NOTHING")) {
            statement.setObject(1, islandId);
            statement.executeUpdate();
        }
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
