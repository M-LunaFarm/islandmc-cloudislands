package kr.lunaf.cloudislands.migration.rollback;

import org.junit.jupiter.api.Test;

import java.time.Instant;
import java.util.ArrayList;
import java.util.List;
import java.util.UUID;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertTrue;

class MigrationRollbackServiceTest {
    private static final UUID RUN_ID = UUID.fromString("00000000-0000-0000-0000-000000000501");
    private static final UUID ISLAND_A = UUID.fromString("00000000-0000-0000-0000-000000000601");
    private static final UUID ISLAND_B = UUID.fromString("00000000-0000-0000-0000-000000000602");

    @Test
    void rollbackRemovesOnlyImportedIslandsFromPlan() {
        MigrationRollbackService service = new MigrationRollbackService();
        List<UUID> removed = new ArrayList<>();
        MigrationRollbackPlan plan = new MigrationRollbackPlan(RUN_ID, List.of(ISLAND_A, ISLAND_B), Instant.parse("2026-06-17T00:00:00Z"));

        MigrationRollbackService.RollbackResult result = service.rollback(plan, removed::add);

        assertTrue(result.rolledBack());
        assertEquals(2, result.removedIslands());
        assertEquals(List.of(ISLAND_A, ISLAND_B), removed);
        assertTrue(result.issues().isEmpty());
    }

    @Test
    void rollbackKeepsGoingAndReportsFailedIsland() {
        MigrationRollbackService service = new MigrationRollbackService();
        List<UUID> removed = new ArrayList<>();
        MigrationRollbackPlan plan = new MigrationRollbackPlan(RUN_ID, List.of(ISLAND_A, ISLAND_B), Instant.parse("2026-06-17T00:00:00Z"));

        MigrationRollbackService.RollbackResult result = service.rollback(plan, islandId -> {
            if (islandId.equals(ISLAND_A)) {
                throw new IllegalStateException("db row is locked");
            }
            removed.add(islandId);
        });

        assertFalse(result.rolledBack());
        assertEquals(1, result.removedIslands());
        assertEquals(List.of(ISLAND_B), removed);
        assertEquals(1, result.issues().size());
        assertEquals("ROLLBACK_FAILED", result.issues().get(0).code());
    }

    @Test
    void rollbackRejectsMissingPlanOrTarget() {
        MigrationRollbackService service = new MigrationRollbackService();

        MigrationRollbackService.RollbackResult noPlan = service.rollback(null, ignored -> {});
        MigrationRollbackService.RollbackResult noTarget = service.rollback(new MigrationRollbackPlan(RUN_ID, List.of(ISLAND_A), null), null);

        assertFalse(noPlan.rolledBack());
        assertEquals("ROLLBACK_PLAN_REQUIRED", noPlan.issues().get(0).code());
        assertFalse(noTarget.rolledBack());
        assertEquals("ROLLBACK_TARGET_REQUIRED", noTarget.issues().get(0).code());
    }
}
