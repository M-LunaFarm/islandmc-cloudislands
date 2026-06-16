package kr.lunaf.cloudislands.common.routing;

public final class NodeDrainPolicy {
    public static final String CONTRACT = "drain-blocks-new-activation-route-candidates-keeps-active-islands-single-owner";
    public static final String NEW_ROUTE_POLICY = "DRAINING and SHUTTING_DOWN nodes are not eligible for new island activation or inactive-island routing";
    public static final String ACTIVE_ISLAND_POLICY = "active islands stay on the current node until save-migrate-sweep-or-admin-migrate completes";
    public static final String OWNER_MEMBER_POLICY = "owner and member access stays bound to the current active node while it remains healthy";
    public static final String VISITOR_POLICY = "visitors are denied or queued when a drained active node cannot accept safe route handoff";
    public static final String DRAIN_NEXT_STEP = "run sweep, migrate, kickall, or shutdown-safe depending on the maintenance window";
    public static final String UNDRAIN_NEXT_STEP = "heartbeat may report READY, SOFT_FULL, or HARD_FULL after undrain based on live caps";

    private NodeDrainPolicy() {
    }
}
