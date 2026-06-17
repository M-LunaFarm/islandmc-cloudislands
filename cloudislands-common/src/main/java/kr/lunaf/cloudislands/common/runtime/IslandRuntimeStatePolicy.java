package kr.lunaf.cloudislands.common.runtime;

import kr.lunaf.cloudislands.api.model.IslandRuntimeSnapshot;
import kr.lunaf.cloudislands.api.model.IslandState;

public final class IslandRuntimeStatePolicy {
    public static final String FENCING_TOKEN_POLICY = "monotonic-token-rejects-stale-node-writes";
    public static final String SINGLE_ACTIVE_POLICY = "one-island-runtime-owner-active-or-transitioning-at-a-time";
    public static final String PLACEMENT_POLICY = "active-transitioning-runtimes-occupy-shard-cell-placement";
    public static final String RECOVERY_REQUIRED_POLICY = "node-failure-or-unsafe-job-result-marks-active-runtime-recovery-required-before-reroute";
    public static final String QUARANTINE_POLICY = "missing-or-unverified-last-good-snapshot-keeps-island-quarantined-until-admin-repair";

    private IslandRuntimeStatePolicy() {
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
}
