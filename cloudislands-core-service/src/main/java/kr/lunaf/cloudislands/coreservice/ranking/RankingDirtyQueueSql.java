package kr.lunaf.cloudislands.coreservice.ranking;

final class RankingDirtyQueueSql {
    static final String SELECT_FOR_DRAIN =
        "SELECT island_id FROM island_ranking_dirty ORDER BY marked_at, island_id LIMIT ? FOR UPDATE";
    static final String DELETE = "DELETE FROM island_ranking_dirty WHERE island_id = ?";
    static final String COUNT = "SELECT COUNT(*) FROM island_ranking_dirty";

    private RankingDirtyQueueSql() {
    }

    static String mark(boolean mysqlLike) {
        if (mysqlLike) {
            return "INSERT INTO island_ranking_dirty(island_id, marked_at) VALUES (?, now(6)) ON DUPLICATE KEY UPDATE marked_at = VALUES(marked_at)";
        }
        return "INSERT INTO island_ranking_dirty(island_id, marked_at) VALUES (?, now()) ON CONFLICT (island_id) DO UPDATE SET marked_at = EXCLUDED.marked_at";
    }
}
