package kr.lunaf.cloudislands.common.island;

import java.util.List;

public final class IslandPortabilityPolicy {
    public static final int MIN_READY_CANDIDATES_FOR_NEW_ACTIVATION = 1;
    public static final int RECOMMENDED_READY_CANDIDATES = 2;
    public static final String RESOURCE_MODEL = "global-resource-not-server-pinned";
    public static final String BUNDLE_MODEL = "portable-bundle-with-node-independent-manifest";
    public static final String NODE_MODEL = "island-nodes-run-islands-they-do-not-own-islands";
    public static final String PLAYER_VISIBILITY_POLICY = "players-see-islands-not-physical-node-names";
    public static final String SCALE_POLICY = "add-island-nodes-by-registering-more-route-candidate-nodes-no-fixed-node-count-limit";
    public static final String NO_FIXED_NODE_COUNT_LIMIT_POLICY = "island-node-count-has-no-hard-coded-maximum-route-eligibility-comes-from-live-heartbeats";
    public static final String FIVE_SIX_NODE_POLICY = "five-or-six-island-nodes-are-supported-when-each-node-has-unique-node-id-unique-velocity-server-name-shared-storage-and-route-candidate-readiness";
    public static final String EIGHT_PLUS_NODE_POLICY = "eight-or-more-island-nodes-use-the-same-live-route-candidate-rules-with-no-player-command-change";
    public static final String SCALE_READINESS_POLICY = "node-pool-readiness-is-live-heartbeat-driven-and-degraded-when-route-candidates-drop-below-recommended-minimum";
    public static final String ROUTE_CANDIDATE_MINIMUM_POLICY = "recommended-route-candidates-are-capped-for-alerting-not-for-node-count-limiting";
    public static final int MAX_RECOMMENDED_ROUTE_CANDIDATES = 6;
    public static final String SOFT_FULL_POLICY = "new-and-inactive-islands-avoid-soft-full-nodes-active-member-reserve-slots-visitors-queue-or-limit";
    public static final String ONE_LINE_DEFINITION = "CloudIslands manages islands as global portable resources on a Velocity network and dynamically activates them on an Island node pool.";
    public static final String SCALE_OUT_GUARD_POLICY = "new-nodes-must-use-unique-node-id-unique-velocity-server-name-shared-storage-and-ready-heartbeat-before-routing";

    private static final List<String> DESIGN_EFFECTS = List.of(
        "lobby-can-query-island-info",
        "any-island-node-can-query-island-info",
        "new-islands-can-use-ready-node-when-another-node-is-soft-full",
        "inactive-islands-can-open-on-a-different-ready-node",
        "players-do-not-need-to-know-the-physical-node",
        "admins-can-drain-and-migrate-by-node",
        "island-node-pool-can-scale-to-island-3-island-4-and-beyond",
        "island-node-pool-can-run-five-or-six-nodes-with-unique-identities-and-shared-storage",
        "island-node-pool-can-run-eight-or-more-nodes-with-the-same-live-heartbeat-routing",
        "route-candidate-shortfall-is-reported-before-new-activations-fail"
    );

    private static final List<Integer> SCALE_OUT_EXAMPLE_COUNTS = List.of(3, 4, 5, 6, 8);

    private IslandPortabilityPolicy() {}

    public static List<String> designEffects() {
        return DESIGN_EFFECTS;
    }

    public static String designEffectSummary() {
        return String.join(",", DESIGN_EFFECTS);
    }

    public static String scaleReadinessSummary() {
        return SCALE_POLICY + "," + FIVE_SIX_NODE_POLICY + "," + SCALE_READINESS_POLICY;
    }

    public static List<Integer> scaleOutExampleCounts() {
        return SCALE_OUT_EXAMPLE_COUNTS;
    }

    public static boolean supportsIslandNodeCount(int islandNodeCount) {
        return islandNodeCount >= 1;
    }

    public static boolean documentedScaleOutCount(int islandNodeCount) {
        return SCALE_OUT_EXAMPLE_COUNTS.contains(islandNodeCount);
    }

    public static boolean routeCandidateRecommendationCapsNodeCount() {
        return false;
    }

    public static boolean nodeCountExceedsRecommendedCandidateCap(long islandNodeCount) {
        return islandNodeCount > MAX_RECOMMENDED_ROUTE_CANDIDATES;
    }

    public static String routeCandidateCapMeaning(long islandNodeCount) {
        if (islandNodeCount <= 0L) {
            return "no-island-nodes-registered";
        }
        if (nodeCountExceedsRecommendedCandidateCap(islandNodeCount)) {
            return "alerting-cap-only-node-count-still-supported";
        }
        return "recommended-ready-candidate-count-tracks-node-count";
    }

    public static long recommendedRouteCandidateMinimum(long islandNodeCount) {
        if (islandNodeCount <= 0L) {
            return 0L;
        }
        if (islandNodeCount == 1L) {
            return 1L;
        }
        if (islandNodeCount < 5L) {
            return RECOMMENDED_READY_CANDIDATES;
        }
        if (islandNodeCount <= MAX_RECOMMENDED_ROUTE_CANDIDATES) {
            return islandNodeCount;
        }
        return MAX_RECOMMENDED_ROUTE_CANDIDATES;
    }

    public static boolean readyCandidateCountAllowsNewActivation(long readyCandidates) {
        return readyCandidates >= MIN_READY_CANDIDATES_FOR_NEW_ACTIVATION;
    }

    public static boolean readyCandidateCountDegraded(long readyCandidates) {
        return readyCandidates < RECOMMENDED_READY_CANDIDATES;
    }

    public static String readinessState(long readyCandidates) {
        if (!readyCandidateCountAllowsNewActivation(readyCandidates)) {
            return "blocked-no-ready-route-candidates";
        }
        if (readyCandidateCountDegraded(readyCandidates)) {
            return "degraded-below-recommended-ready-route-candidates";
        }
        return "healthy-ready-route-candidates";
    }
}
