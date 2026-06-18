package kr.lunaf.cloudislands.storage.snapshot;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertTrue;

import java.util.List;
import org.junit.jupiter.api.Test;

class SnapshotOperationPolicyTest {
    @Test
    void pinsAutomaticSnapshotTriggersFromGoal() {
        assertEquals("create,deactivate,periodic,before-delete,before-reset,before-migration,manual-admin", SnapshotOperationPolicy.AUTOMATIC_TRIGGER_POLICY);
        assertEquals(List.of("CREATED", "DEACTIVATION", "PERIODIC", "BEFORE_DELETE", "BEFORE_RESET", "BEFORE_MIGRATION", "MANUAL"), SnapshotOperationPolicy.automaticTriggerReasons());
    }

    @Test
    void pinsRollbackPipelineAndRestoringState() {
        assertEquals("RESTORING", SnapshotOperationPolicy.RESTORING_LOCK_STATE);
        assertEquals("lock-restoring,evacuate-active-players,pre-restore-snapshot,restore-bundle,clear-runtime,reactivate,unlock", SnapshotOperationPolicy.RESTORE_PIPELINE_POLICY);
        assertEquals("clear-island-runtime-after-promote-before-reactivate", SnapshotOperationPolicy.RUNTIME_RESET_POLICY);
        assertTrue(SnapshotOperationPolicy.rollbackSteps().contains("WRITE_PRE_RESTORE_SNAPSHOT"));
        assertTrue(SnapshotOperationPolicy.rollbackSteps().contains("CLEAR_ISLAND_RUNTIME"));
        assertTrue(SnapshotOperationPolicy.rollbackSteps().contains("REACTIVATE_ISLAND"));
    }
}
