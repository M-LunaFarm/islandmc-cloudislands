package kr.lunaf.cloudislands.common.island;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertTrue;

import java.util.List;
import org.junit.jupiter.api.Test;

class IslandPortabilityPolicyTest {
    @Test
    void keepsFiveAndSixIslandNodesInSupportedScaleOutExamples() {
        assertEquals(List.of(3, 4, 5, 6, 8), IslandPortabilityPolicy.scaleOutExampleCounts());
        assertTrue(IslandPortabilityPolicy.supportsIslandNodeCount(5));
        assertTrue(IslandPortabilityPolicy.supportsIslandNodeCount(6));
        assertTrue(IslandPortabilityPolicy.documentedScaleOutCount(5));
        assertTrue(IslandPortabilityPolicy.documentedScaleOutCount(6));
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
}
