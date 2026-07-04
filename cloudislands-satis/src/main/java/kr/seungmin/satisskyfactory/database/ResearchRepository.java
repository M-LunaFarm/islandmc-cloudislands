package kr.seungmin.satisskyfactory.database;

import java.sql.Connection;
import java.sql.PreparedStatement;
import java.sql.ResultSet;
import java.sql.SQLException;
import java.time.Instant;
import java.util.ArrayList;
import java.util.LinkedHashSet;
import java.util.List;
import java.util.Set;
import java.util.UUID;

final class ResearchRepository {
    private final DatabaseService database;

    ResearchRepository(DatabaseService database) {
        this.database = database;
    }

    Set<String> loadUnlocks(UUID islandUuid) {
        Set<String> unlocks = new LinkedHashSet<>();
        try (Connection connection = database.connection();
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

    List<UnlockEntry> unlockEntries(UUID islandUuid) {
        List<UnlockEntry> unlocks = new ArrayList<>();
        try (Connection connection = database.connection();
             PreparedStatement statement = connection.prepareStatement("SELECT unlock_id, unlocked_at FROM island_unlocks WHERE island_uuid = ?")) {
            statement.setString(1, islandUuid.toString());
            try (ResultSet rs = statement.executeQuery()) {
                while (rs.next()) {
                    unlocks.add(new UnlockEntry(rs.getString("unlock_id"), rs.getLong("unlocked_at")));
                }
            }
            return unlocks;
        } catch (SQLException exception) {
            throw new IllegalStateException("Failed to publish unlock core state", exception);
        }
    }

    long saveUnlock(UUID islandUuid, String unlockId) {
        long unlockedAt = Instant.now().toEpochMilli();
        try (Connection connection = database.connection();
             PreparedStatement statement = connection.prepareStatement(saveUnlockSql())) {
            statement.setString(1, islandUuid.toString());
            statement.setString(2, unlockId);
            statement.setLong(3, unlockedAt);
            statement.executeUpdate();
            return unlockedAt;
        } catch (SQLException exception) {
            throw new IllegalStateException("Failed to save unlock", exception);
        }
    }

    private String saveUnlockSql() {
        if (database.usesMysqlDialect()) {
            return """
                     INSERT IGNORE INTO island_unlocks(island_uuid, unlock_id, unlocked_at)
                     VALUES(?, ?, ?)
                    """;
        }
        if (database.usesPostgresqlDialect()) {
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

    record UnlockEntry(String unlockId, long unlockedAt) {
    }
}
