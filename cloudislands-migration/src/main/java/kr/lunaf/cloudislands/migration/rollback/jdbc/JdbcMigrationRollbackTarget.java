package kr.lunaf.cloudislands.migration.rollback.jdbc;

import java.sql.Connection;
import java.sql.PreparedStatement;
import java.sql.SQLException;
import java.util.UUID;
import javax.sql.DataSource;
import kr.lunaf.cloudislands.migration.rollback.MigrationRollbackService;

public final class JdbcMigrationRollbackTarget implements MigrationRollbackService.RollbackTarget {
    private final DataSource dataSource;

    public JdbcMigrationRollbackTarget(DataSource dataSource) {
        this.dataSource = dataSource;
    }

    @Override
    public void removeImportedIsland(UUID islandId) {
        try (Connection connection = dataSource.getConnection()) {
            connection.setAutoCommit(false);
            delete(connection, "route_tickets", islandId);
            delete(connection, "island_jobs", islandId);
            delete(connection, "island_snapshots", islandId);
            delete(connection, "island_runtime", islandId);
            delete(connection, "island_warps", islandId);
            delete(connection, "island_invites", islandId);
            delete(connection, "island_bans", islandId);
            delete(connection, "island_flags", islandId);
            delete(connection, "island_permissions", islandId);
            delete(connection, "island_roles", islandId);
            delete(connection, "island_members", islandId);
            try (PreparedStatement island = connection.prepareStatement("DELETE FROM islands WHERE id = ?")) {
                island.setObject(1, islandId);
                island.executeUpdate();
            }
            connection.commit();
        } catch (SQLException exception) {
            throw new IllegalStateException("failed to rollback imported island " + islandId, exception);
        }
    }

    private void delete(Connection connection, String table, UUID islandId) throws SQLException {
        try (PreparedStatement statement = connection.prepareStatement("DELETE FROM " + table + " WHERE island_id = ?")) {
            statement.setObject(1, islandId);
            statement.executeUpdate();
        }
    }
}
