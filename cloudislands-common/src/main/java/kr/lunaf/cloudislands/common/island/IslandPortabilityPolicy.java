package kr.lunaf.cloudislands.common.island;

import java.util.List;

public final class IslandPortabilityPolicy {
    public static final String RESOURCE_MODEL = "global-resource-not-server-pinned";
    public static final String BUNDLE_MODEL = "portable-bundle-with-node-independent-manifest";
    public static final String NODE_MODEL = "island-nodes-run-islands-they-do-not-own-islands";
    public static final String PLAYER_VISIBILITY_POLICY = "players-see-islands-not-physical-node-names";
    public static final String SCALE_POLICY = "add-island-3-island-4-by-registering-more-route-candidate-nodes";
    public static final String SOFT_FULL_POLICY = "new-and-inactive-islands-avoid-soft-full-nodes-active-member-reserve-slots-visitors-queue-or-limit";
    public static final String ONE_LINE_DEFINITION = "CloudIslands manages islands as global portable resources on a Velocity network and dynamically activates them on an Island node pool.";

    private static final List<String> DESIGN_EFFECTS = List.of(
        "lobby-can-query-island-info",
        "any-island-node-can-query-island-info",
        "new-islands-can-use-ready-node-when-another-node-is-soft-full",
        "inactive-islands-can-open-on-a-different-ready-node",
        "players-do-not-need-to-know-the-physical-node",
        "admins-can-drain-and-migrate-by-node",
        "island-node-pool-can-scale-to-island-3-island-4-and-beyond"
    );

    private IslandPortabilityPolicy() {}

    public static List<String> designEffects() {
        return DESIGN_EFFECTS;
    }

    public static String designEffectSummary() {
        return String.join(",", DESIGN_EFFECTS);
    }
}
