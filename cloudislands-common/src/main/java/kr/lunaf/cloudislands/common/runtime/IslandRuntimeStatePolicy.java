package kr.lunaf.cloudislands.common.runtime;

import kr.lunaf.cloudislands.api.model.IslandRuntimeSnapshot;
import kr.lunaf.cloudislands.api.model.IslandState;
import java.util.List;
import java.util.Set;

public final class IslandRuntimeStatePolicy {
    public static final String FENCING_TOKEN_POLICY = "monotonic-token-rejects-stale-node-writes";
    public static final String SINGLE_ACTIVE_POLICY = "one-island-runtime-owner-active-or-transitioning-at-a-time";
    public static final String PLACEMENT_POLICY = "active-transitioning-runtimes-occupy-shard-cell-placement";
    public static final String RECOVERY_REQUIRED_POLICY = "node-failure-or-unsafe-job-result-marks-active-runtime-recovery-required-before-reroute";
    public static final String QUARANTINE_POLICY = "missing-or-unverified-last-good-snapshot-keeps-island-quarantined-until-admin-repair";
    public static final String STATE_MACHINE_POLICY = "create-activate-save-delete-and-recovery-states-follow-the-goal-lifecycle-contract";
    private static final List<IslandState> CREATE_ACTIVATE_SAVE_FLOW = List.of(
        IslandState.CREATE_REQUESTED,
        IslandState.CREATING,
        IslandState.INACTIVE_READY,
        IslandState.ACTIVATING,
        IslandState.ACTIVE,
        IslandState.SAVING,
        IslandState.INACTIVE_READY
    );
    private static final List<IslandState> DELETE_FLOW = List.of(
        IslandState.DELETE_REQUESTED,
        IslandState.DEACTIVATING,
        IslandState.BACKUP_BEFORE_DELETE,
        IslandState.DELETING,
        IslandState.DELETED
    );
    private static final Set<IslandState> FAILURE_STATES = Set.of(
        IslandState.ERROR_CREATING,
        IslandState.ERROR_ACTIVATING,
        IslandState.ERROR_SAVING,
        IslandState.QUARANTINED,
        IslandState.RECOVERY_REQUIRED
    );

    private IslandRuntimeStatePolicy() {
    }

    public static List<IslandState> createActivateSaveFlow() {
        return CREATE_ACTIVATE_SAVE_FLOW;
    }

    public static List<IslandState> deleteFlow() {
        return DELETE_FLOW;
    }

    public static Set<IslandState> failureStates() {
        return FAILURE_STATES;
    }

    public static boolean failureState(IslandState state) {
        return state != null && FAILURE_STATES.contains(state);
    }

    public static boolean transitionAllowed(IslandState from, IslandState to) {
        if (from == null || to == null) {
            return false;
        }
        if (from == to) {
            return true;
        }
        if (failureState(to)) {
            return true;
        }
        return adjacentTransition(CREATE_ACTIVATE_SAVE_FLOW, from, to)
            || adjacentTransition(DELETE_FLOW, from, to)
            || from == IslandState.INACTIVE_READY && to == IslandState.RESTORING
            || from == IslandState.RESTORING && to == IslandState.ACTIVE
            || from == IslandState.ACTIVE && to == IslandState.DELETE_REQUESTED
            || from == IslandState.SAVING && to == IslandState.DELETE_REQUESTED
            || from == IslandState.QUARANTINED && to == IslandState.RESTORING
            || from == IslandState.RECOVERY_REQUIRED && to == IslandState.RESTORING
            || from == IslandState.RECOVERY_REQUIRED && to == IslandState.QUARANTINED;
    }

    public static String lifecycleStateMachineSummary() {
        return STATE_MACHINE_POLICY
            + ";create=" + stateSummary(CREATE_ACTIVATE_SAVE_FLOW)
            + ";delete=" + stateSummary(DELETE_FLOW)
            + ";failure=" + stateSummary(FAILURE_STATES.stream().sorted().toList());
    }

    public static boolean runningOnNode(IslandState state) {
        return state == IslandState.ACTIVE
            || state == IslandState.ACTIVATING
            || state == IslandState.RESTORING
            || state == IslandState.SAVING
            || state == IslandState.DEACTIVATING;
    }

    public static boolean runningOnNode(IslandRuntimeSnapshot runtime) {
        return runtime != null && runningOnNode(runtime.state());
    }

    public static boolean listedByNode(IslandRuntimeSnapshot runtime) {
        return runningOnNode(runtime) || (runtime != null && runtime.state() == IslandState.RECOVERY_REQUIRED);
    }

    public static boolean occupiesPlacement(IslandRuntimeSnapshot runtime) {
        return runningOnNode(runtime);
    }

    public static String recoveryStateSummary() {
        return RECOVERY_REQUIRED_POLICY + "," + QUARANTINE_POLICY;
    }

    public static boolean staleFencingToken(IslandRuntimeSnapshot current, long proposedToken) {
        return current != null && current.fencingToken() > proposedToken;
    }

    public static long nextFencingToken(IslandRuntimeSnapshot current) {
        return (current == null ? 0L : current.fencingToken()) + 1L;
    }

    private static String stateSummary(List<IslandState> states) {
        return states.stream().map(IslandState::name).reduce((left, right) -> left + ">" + right).orElse("");
    }

    private static boolean adjacentTransition(List<IslandState> states, IslandState from, IslandState to) {
        for (int index = 0; index < states.size() - 1; index++) {
            if (states.get(index) == from && states.get(index + 1) == to) {
                return true;
            }
        }
        return false;
    }
}
