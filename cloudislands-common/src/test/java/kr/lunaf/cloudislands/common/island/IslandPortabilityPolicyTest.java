package kr.lunaf.cloudislands.common.island;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertTrue;

import java.util.List;
import org.junit.jupiter.api.Test;

class IslandPortabilityPolicyTest {
    @Test
    void keepsFiveAndSixIslandNodesInSupportedScaleOutExamples() {
        assertEquals(List.of(3, 4, 5, 6, 7, 8), IslandPortabilityPolicy.scaleOutExampleCounts());
        assertTrue(IslandPortabilityPolicy.supportsIslandNodeCount(5));
        assertTrue(IslandPortabilityPolicy.supportsIslandNodeCount(6));
        assertTrue(IslandPortabilityPolicy.supportsIslandNodeCount(7));
        assertTrue(IslandPortabilityPolicy.supportsIslandNodeCount(8));
        assertTrue(IslandPortabilityPolicy.supportsIslandNodeCount(12));
        assertTrue(IslandPortabilityPolicy.documentedScaleOutCount(5));
        assertTrue(IslandPortabilityPolicy.documentedScaleOutCount(6));
        assertTrue(IslandPortabilityPolicy.documentedScaleOutCount(7));
        assertTrue(IslandPortabilityPolicy.documentedScaleOutCount(8));
        assertFalse(IslandPortabilityPolicy.supportsIslandNodeCount(0));
    }

    @Test
    void requiresUniqueNodeIdentitySharedStorageAndReadyHeartbeatForScaleOut() {
        assertEquals(
            "five-or-six-island-nodes-are-supported-when-each-node-has-unique-node-id-unique-velocity-server-name-shared-storage-and-route-candidate-readiness",
            IslandPortabilityPolicy.FIVE_SIX_NODE_POLICY
        );
        assertEquals(
            "new-nodes-must-use-unique-node-id-unique-velocity-server-name-shared-storage-and-ready-heartbeat-before-routing",
            IslandPortabilityPolicy.SCALE_OUT_GUARD_POLICY
        );
        assertTrue(IslandPortabilityPolicy.designEffects().contains("island-node-pool-can-run-five-or-six-nodes-with-unique-identities-and-shared-storage"));
        assertTrue(IslandPortabilityPolicy.designEffects().contains("island-node-pool-can-run-seven-or-more-nodes-with-the-same-live-heartbeat-routing"));
        assertTrue(IslandPortabilityPolicy.designEffects().contains("island-node-pool-can-run-eight-or-more-nodes-with-the-same-live-heartbeat-routing"));
        assertEquals(
            "island-node-count-has-no-hard-coded-maximum-route-eligibility-comes-from-live-heartbeats",
            IslandPortabilityPolicy.NO_FIXED_NODE_COUNT_LIMIT_POLICY
        );
        assertEquals(
            "seven-or-more-island-nodes-use-the-same-live-route-candidate-rules-with-no-player-command-change",
            IslandPortabilityPolicy.ABOVE_SIX_NODE_POLICY
        );
        assertEquals(
            "eight-or-more-island-nodes-use-the-same-live-route-candidate-rules-with-no-player-command-change",
            IslandPortabilityPolicy.EIGHT_PLUS_NODE_POLICY
        );
    }

    @Test
    void classifiesReadyCandidateShortfallBeforeActivationFails() {
        assertEquals(1, IslandPortabilityPolicy.MIN_READY_CANDIDATES_FOR_NEW_ACTIVATION);
        assertEquals(2, IslandPortabilityPolicy.RECOMMENDED_READY_CANDIDATES);
        assertFalse(IslandPortabilityPolicy.readyCandidateCountAllowsNewActivation(0));
        assertTrue(IslandPortabilityPolicy.readyCandidateCountAllowsNewActivation(1));
        assertTrue(IslandPortabilityPolicy.readyCandidateCountDegraded(1));
        assertFalse(IslandPortabilityPolicy.readyCandidateCountDegraded(2));
        assertEquals("blocked-no-ready-route-candidates", IslandPortabilityPolicy.readinessState(0));
        assertEquals("degraded-below-recommended-ready-route-candidates", IslandPortabilityPolicy.readinessState(1));
        assertEquals("healthy-ready-route-candidates", IslandPortabilityPolicy.readinessState(2));
    }

    @Test
    void capsRecommendedRouteCandidatesWithoutCappingNodeCount() {
        assertEquals(0L, IslandPortabilityPolicy.recommendedRouteCandidateMinimum(0));
        assertEquals(1L, IslandPortabilityPolicy.recommendedRouteCandidateMinimum(1));
        assertEquals(2L, IslandPortabilityPolicy.recommendedRouteCandidateMinimum(4));
        assertEquals(5L, IslandPortabilityPolicy.recommendedRouteCandidateMinimum(5));
        assertEquals(6L, IslandPortabilityPolicy.recommendedRouteCandidateMinimum(6));
        assertEquals(6L, IslandPortabilityPolicy.recommendedRouteCandidateMinimum(7));
        assertEquals(6L, IslandPortabilityPolicy.recommendedRouteCandidateMinimum(8));
        assertEquals(6L, IslandPortabilityPolicy.recommendedRouteCandidateMinimum(12));
        assertEquals(6, IslandPortabilityPolicy.MAX_RECOMMENDED_ROUTE_CANDIDATES);
        assertFalse(IslandPortabilityPolicy.routeCandidateRecommendationCapsNodeCount());
        assertFalse(IslandPortabilityPolicy.nodeCountExceedsRecommendedCandidateCap(6));
        assertTrue(IslandPortabilityPolicy.nodeCountExceedsRecommendedCandidateCap(12));
        assertTrue(IslandPortabilityPolicy.supportsIslandNodeCount(64));
        assertEquals("no-island-nodes-registered", IslandPortabilityPolicy.routeCandidateCapMeaning(0));
        assertEquals("recommended-ready-candidate-count-tracks-node-count", IslandPortabilityPolicy.routeCandidateCapMeaning(6));
        assertEquals("alerting-cap-only-node-count-still-supported", IslandPortabilityPolicy.routeCandidateCapMeaning(12));
        assertEquals(
            "recommended-route-candidates-are-capped-for-alerting-not-for-node-count-limiting",
            IslandPortabilityPolicy.ROUTE_CANDIDATE_MINIMUM_POLICY
        );
    }
}
