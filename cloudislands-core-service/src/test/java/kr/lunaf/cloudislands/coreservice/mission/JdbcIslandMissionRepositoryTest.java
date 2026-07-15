package kr.lunaf.cloudislands.coreservice.mission;

import static org.junit.jupiter.api.Assertions.assertTrue;

import org.junit.jupiter.api.Test;

class JdbcIslandMissionRepositoryTest {
    @Test
    void completionIsCalculatedBeforeMysqlMutatesProgressLeftToRight() {
        String sql = JdbcIslandMissionRepository.progressUpdateSql();

        int completion = sql.indexOf("completed = LEAST(goal, progress + ?) >= goal");
        int progress = sql.indexOf("progress = LEAST(goal, progress + ?)");
        assertTrue(completion >= 0);
        assertTrue(progress > completion, "MySQL evaluates UPDATE assignments left to right, so completion must read the original progress first");
    }

    @Test
    void absoluteProgressUsesMonotonicAuthoritativeValue() {
        String sql = JdbcIslandMissionRepository.progressToUpdateSql();

        int completion = sql.indexOf("completed = GREATEST(progress, ?) >= goal");
        int progress = sql.indexOf("progress = LEAST(goal, GREATEST(progress, ?))");
        assertTrue(completion >= 0);
        assertTrue(progress > completion, "MySQL completion must read the original progress before the authoritative value is stored");
    }
}
