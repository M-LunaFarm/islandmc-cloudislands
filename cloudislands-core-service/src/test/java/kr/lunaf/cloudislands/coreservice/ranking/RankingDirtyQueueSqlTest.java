package kr.lunaf.cloudislands.coreservice.ranking;

import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertTrue;

import org.junit.jupiter.api.Test;

class RankingDirtyQueueSqlTest {
    @Test
    void dirtyMarksAreIslandScopedUpsertsEvenWithoutBlockCountRows() {
        String postgres = RankingDirtyQueueSql.mark(false);
        String mysql = RankingDirtyQueueSql.mark(true);

        assertTrue(postgres.startsWith("INSERT INTO island_ranking_dirty"));
        assertTrue(postgres.contains("ON CONFLICT (island_id) DO UPDATE"));
        assertTrue(mysql.startsWith("INSERT INTO island_ranking_dirty"));
        assertTrue(mysql.contains("ON DUPLICATE KEY UPDATE"));
        assertFalse(postgres.contains("island_block_counts"));
        assertFalse(mysql.contains("island_block_counts"));
    }

    @Test
    void drainLocksQueueRowsBeforeDeletingThem() {
        assertTrue(RankingDirtyQueueSql.SELECT_FOR_DRAIN.contains("FOR UPDATE"));
        assertTrue(RankingDirtyQueueSql.SELECT_FOR_DRAIN.contains("ORDER BY marked_at, island_id"));
        assertTrue(RankingDirtyQueueSql.DELETE.startsWith("DELETE FROM island_ranking_dirty"));
        assertTrue(RankingDirtyQueueSql.COUNT.endsWith("island_ranking_dirty"));
    }
}
