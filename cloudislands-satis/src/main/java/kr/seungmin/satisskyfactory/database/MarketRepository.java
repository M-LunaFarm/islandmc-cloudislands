package kr.seungmin.satisskyfactory.database;

import java.sql.Connection;
import java.sql.PreparedStatement;
import java.sql.ResultSet;
import java.sql.SQLException;
import java.util.ArrayList;
import java.util.List;
import java.util.UUID;

final class MarketRepository {
    private final DatabaseService database;

    MarketRepository(DatabaseService database) {
        this.database = database;
    }

    List<PersonalMarketRow> personalRows(UUID islandUuid) {
        List<PersonalMarketRow> rows = new ArrayList<>();
        try (Connection connection = database.connection();
             PreparedStatement statement = connection.prepareStatement("SELECT item_id, date_key, sold_amount FROM market_personal_daily WHERE island_uuid = ?")) {
            statement.setString(1, islandUuid.toString());
            try (ResultSet rs = statement.executeQuery()) {
                while (rs.next()) {
                    rows.add(new PersonalMarketRow(islandUuid, rs.getString("item_id"), rs.getString("date_key"), rs.getLong("sold_amount")));
                }
            }
            return rows;
        } catch (SQLException exception) {
            throw new IllegalStateException("Failed to publish personal market core state", exception);
        }
    }

    List<DailyMarketRow> dailyRows() {
        List<DailyMarketRow> rows = new ArrayList<>();
        try (Connection connection = database.connection();
             PreparedStatement statement = connection.prepareStatement("SELECT item_id, date_key, sold_amount, demand_factor FROM market_daily");
             ResultSet rs = statement.executeQuery()) {
            while (rs.next()) {
                rows.add(new DailyMarketRow(rs.getString("item_id"), rs.getString("date_key"), rs.getLong("sold_amount"), rs.getDouble("demand_factor")));
            }
            return rows;
        } catch (SQLException exception) {
            throw new IllegalStateException("Failed to publish daily market core state", exception);
        }
    }

    long dailySold(String itemId, String dateKey) {
        try (Connection connection = database.connection()) {
            return dailySold(connection, itemId, dateKey);
        } catch (SQLException exception) {
            throw new IllegalStateException("Failed to read market daily sold amount", exception);
        }
    }

    long personalSold(UUID islandUuid, String itemId, String dateKey) {
        try (Connection connection = database.connection()) {
            return personalSold(connection, islandUuid, itemId, dateKey);
        } catch (SQLException exception) {
            throw new IllegalStateException("Failed to read personal market sold amount", exception);
        }
    }

    MarketSaleTotals recordSale(UUID islandUuid, String itemId, String dateKey, long amount, double demandFactor) {
        try (Connection connection = database.connection()) {
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
            long dailySold = dailySold(connection, itemId, dateKey);
            long personalSold = personalSold(connection, islandUuid, itemId, dateKey);
            connection.commit();
            return new MarketSaleTotals(dailySold, personalSold);
        } catch (SQLException exception) {
            throw new IllegalStateException("Failed to record market sale", exception);
        }
    }

    void saveDailySnapshot(String itemId, String dateKey, long soldAmount, double demandFactor) {
        try (Connection connection = database.connection();
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

    void savePersonalSnapshot(UUID islandUuid, String itemId, String dateKey, long soldAmount) {
        try (Connection connection = database.connection();
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

    private long dailySold(Connection connection, String itemId, String dateKey) throws SQLException {
        try (PreparedStatement statement = connection.prepareStatement("SELECT sold_amount FROM market_daily WHERE item_id = ? AND date_key = ?")) {
            statement.setString(1, itemId);
            statement.setString(2, dateKey);
            try (ResultSet rs = statement.executeQuery()) {
                return rs.next() ? rs.getLong("sold_amount") : 0L;
            }
        }
    }

    private long personalSold(Connection connection, UUID islandUuid, String itemId, String dateKey) throws SQLException {
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

    private String saveMarketDailySnapshotSql() {
        if (database.usesMysqlDialect()) {
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

    private String saveMarketPersonalSnapshotSql() {
        if (database.usesMysqlDialect()) {
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

    private String recordMarketDailySql() {
        if (database.usesMysqlDialect()) {
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
        if (database.usesMysqlDialect()) {
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

    record DailyMarketRow(String itemId, String dateKey, long soldAmount, double demandFactor) {
    }

    record PersonalMarketRow(UUID islandUuid, String itemId, String dateKey, long soldAmount) {
    }

    record MarketSaleTotals(long dailySold, long personalSold) {
    }
}
