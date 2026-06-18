package kr.lunaf.cloudislands.common.runtime;

import kr.lunaf.cloudislands.api.model.IslandState;
import org.junit.jupiter.api.Test;

import java.util.List;
import java.util.Set;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertTrue;

class IslandRuntimeStatePolicyTest {
    @Test
    void pinsGoalCreateActivateSaveLifecycleFlow() {
        assertEquals(
            List.of(
                IslandState.CREATE_REQUESTED,
                IslandState.CREATING,
                IslandState.INACTIVE_READY,
                IslandState.ACTIVATING,
                IslandState.ACTIVE,
                IslandState.SAVING,
                IslandState.INACTIVE_READY
            ),
            IslandRuntimeStatePolicy.createActivateSaveFlow()
        );
    }

    @Test
    void pinsGoalDeleteLifecycleFlow() {
        assertEquals(
            List.of(
                IslandState.DELETE_REQUESTED,
                IslandState.DEACTIVATING,
                IslandState.BACKUP_BEFORE_DELETE,
                IslandState.DELETING,
                IslandState.DELETED
            ),
            IslandRuntimeStatePolicy.deleteFlow()
        );
    }

    @Test
    void pinsFailureAndRecoveryStates() {
        assertEquals(
            Set.of(
                IslandState.ERROR_CREATING,
                IslandState.ERROR_ACTIVATING,
                IslandState.ERROR_SAVING,
                IslandState.QUARANTINED,
                IslandState.RECOVERY_REQUIRED
            ),
            IslandRuntimeStatePolicy.failureStates()
        );
        assertTrue(IslandRuntimeStatePolicy.failureState(IslandState.QUARANTINED));
        assertTrue(IslandRuntimeStatePolicy.failureState(IslandState.RECOVERY_REQUIRED));
        assertFalse(IslandRuntimeStatePolicy.failureState(IslandState.ACTIVE));
        assertFalse(IslandRuntimeStatePolicy.failureState(null));
    }

    @Test
    void keepsRuntimePlacementAndRecoveryPoliciesVisible() {
        assertEquals("create-activate-save-delete-and-recovery-states-follow-the-goal-lifecycle-contract", IslandRuntimeStatePolicy.STATE_MACHINE_POLICY);
        assertEquals("one-island-runtime-owner-active-or-transitioning-at-a-time", IslandRuntimeStatePolicy.SINGLE_ACTIVE_POLICY);
        assertEquals("active-transitioning-runtimes-occupy-shard-cell-placement", IslandRuntimeStatePolicy.PLACEMENT_POLICY);
        assertTrue(IslandRuntimeStatePolicy.runningOnNode(IslandState.ACTIVE));
        assertTrue(IslandRuntimeStatePolicy.runningOnNode(IslandState.RESTORING));
        assertTrue(IslandRuntimeStatePolicy.runningOnNode(IslandState.DEACTIVATING));
        assertFalse(IslandRuntimeStatePolicy.runningOnNode(IslandState.INACTIVE_READY));
        assertTrue(IslandRuntimeStatePolicy.lifecycleStateMachineSummary().contains("CREATE_REQUESTED>CREATING>INACTIVE_READY>ACTIVATING>ACTIVE>SAVING>INACTIVE_READY"));
        assertTrue(IslandRuntimeStatePolicy.lifecycleStateMachineSummary().contains("DELETE_REQUESTED>DEACTIVATING>BACKUP_BEFORE_DELETE>DELETING>DELETED"));
    }

    @Test
    void acceptsOnlyExpectedLifecycleTransitions() {
        assertTrue(IslandRuntimeStatePolicy.transitionAllowed(IslandState.CREATE_REQUESTED, IslandState.CREATING));
        assertTrue(IslandRuntimeStatePolicy.transitionAllowed(IslandState.CREATING, IslandState.INACTIVE_READY));
        assertTrue(IslandRuntimeStatePolicy.transitionAllowed(IslandState.INACTIVE_READY, IslandState.ACTIVATING));
        assertTrue(IslandRuntimeStatePolicy.transitionAllowed(IslandState.ACTIVATING, IslandState.ACTIVE));
        assertTrue(IslandRuntimeStatePolicy.transitionAllowed(IslandState.ACTIVE, IslandState.SAVING));
        assertTrue(IslandRuntimeStatePolicy.transitionAllowed(IslandState.SAVING, IslandState.INACTIVE_READY));
        assertTrue(IslandRuntimeStatePolicy.transitionAllowed(IslandState.ACTIVE, IslandState.DELETE_REQUESTED));
        assertTrue(IslandRuntimeStatePolicy.transitionAllowed(IslandState.DELETE_REQUESTED, IslandState.DEACTIVATING));
        assertTrue(IslandRuntimeStatePolicy.transitionAllowed(IslandState.DEACTIVATING, IslandState.BACKUP_BEFORE_DELETE));
        assertTrue(IslandRuntimeStatePolicy.transitionAllowed(IslandState.BACKUP_BEFORE_DELETE, IslandState.DELETING));
        assertTrue(IslandRuntimeStatePolicy.transitionAllowed(IslandState.DELETING, IslandState.DELETED));
        assertTrue(IslandRuntimeStatePolicy.transitionAllowed(IslandState.INACTIVE_READY, IslandState.DELETE_REQUESTED));
        assertTrue(IslandRuntimeStatePolicy.transitionAllowed(IslandState.INACTIVE_READY, IslandState.DEACTIVATING));
        assertTrue(IslandRuntimeStatePolicy.transitionAllowed(IslandState.ACTIVE, IslandState.RESTORING));
        assertTrue(IslandRuntimeStatePolicy.transitionAllowed(IslandState.RESTORING, IslandState.ACTIVATING));
        assertTrue(IslandRuntimeStatePolicy.transitionAllowed(IslandState.RECOVERY_REQUIRED, IslandState.RESTORING));
        assertTrue(IslandRuntimeStatePolicy.transitionAllowed(IslandState.RECOVERY_REQUIRED, IslandState.INACTIVE_READY));
        assertTrue(IslandRuntimeStatePolicy.transitionAllowed(IslandState.RECOVERY_REQUIRED, IslandState.QUARANTINED));
        assertTrue(IslandRuntimeStatePolicy.transitionAllowed(IslandState.QUARANTINED, IslandState.RESTORING));
        assertTrue(IslandRuntimeStatePolicy.transitionAllowed(IslandState.QUARANTINED, IslandState.INACTIVE_READY));
        assertTrue(IslandRuntimeStatePolicy.transitionAllowed(IslandState.RESTORING, IslandState.ACTIVE));
        assertTrue(IslandRuntimeStatePolicy.transitionAllowed(IslandState.ACTIVATING, IslandState.ERROR_ACTIVATING));

        assertFalse(IslandRuntimeStatePolicy.transitionAllowed(IslandState.CREATE_REQUESTED, IslandState.ACTIVE));
        assertFalse(IslandRuntimeStatePolicy.transitionAllowed(IslandState.INACTIVE_READY, IslandState.DELETED));
        assertFalse(IslandRuntimeStatePolicy.transitionAllowed(IslandState.DELETED, IslandState.ACTIVE));
        assertFalse(IslandRuntimeStatePolicy.transitionAllowed(IslandState.DELETED, IslandState.ERROR_SAVING));
        assertFalse(IslandRuntimeStatePolicy.transitionAllowed(IslandState.ACTIVE, IslandState.CREATE_REQUESTED));
        assertFalse(IslandRuntimeStatePolicy.transitionAllowed(null, IslandState.ACTIVE));
        assertFalse(IslandRuntimeStatePolicy.transitionAllowed(IslandState.ACTIVE, null));
    }
}
