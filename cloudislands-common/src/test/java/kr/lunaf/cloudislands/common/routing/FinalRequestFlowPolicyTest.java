package kr.lunaf.cloudislands.common.routing;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertTrue;

import java.util.List;
import org.junit.jupiter.api.Test;

class FinalRequestFlowPolicyTest {
    @Test
    void recordsIslandCreateRequestFlow() {
        assertEquals(
            List.of(
                "player",
                "velocity-command-create",
                "logical-island-command-no-node-name",
                "core-api-create-island",
                "db-transaction-and-lock",
                "node-allocator",
                "create-island-job",
                "island-agent-claim",
                "template-restore",
                "cell-allocate",
                "runtime-active",
                "route-ticket-ready",
                "velocity-connect-target-node",
                "player-message-hides-physical-node",
                "paper-consume-ticket",
                "teleport-island-spawn"
            ),
            FinalRequestFlowPolicy.flow("island-create")
        );
    }

    @Test
    void recordsHomeAndVisitRouteFlows() {
        assertEquals(
            List.of(
                "player",
                "velocity-command-home",
                "logical-island-home-no-node-name",
                "core-api-create-home-route",
                "island-runtime-check",
                "active-runtime-uses-active-node",
                "inactive-runtime-activates-on-best-node",
                "route-ticket-ready",
                "velocity-connect",
                "player-message-hides-physical-node",
                "paper-teleport"
            ),
            FinalRequestFlowPolicy.flow("island-home")
        );
        assertEquals(
            List.of(
                "player",
                "velocity-command-visit-player",
                "logical-target-island-no-node-name",
                "target-island-lookup",
                "public-ban-permission-check",
                "active-or-activate",
                "visitor-ticket-create",
                "connect",
                "player-message-hides-physical-node",
                "visitor-spawn-teleport"
            ),
            FinalRequestFlowPolicy.flow("island-visit")
        );
    }

    @Test
    void recordsSoftFullRoutingBehavior() {
        assertEquals(
            List.of(
                "soft-full-node-avoided-for-new-islands",
                "allocator-block-reason-state-soft-full",
                "ready-node-selected-for-new-islands",
                "ready-node-selected-for-inactive-existing-islands",
                "active-island-owner-member-use-reserved-slots-on-current-node",
                "active-island-visitor-queued-or-limited",
                "empty-active-island-may-migrate-after-save"
            ),
            FinalRequestFlowPolicy.flow("soft-full-routing")
        );
    }

    @Test
    void recordsIslandOneSoftFullCreateOnIslandTwoScenario() {
        assertEquals(
            List.of(
                "player-runs-island-create",
                "island-1-heartbeat-reports-soft-full",
                "allocator-marks-island-1-state-soft-full-for-new-activation",
                "allocator-keeps-searching-ready-candidates",
                "island-2-ready-candidate-selected",
                "create-island-job-targets-island-2",
                "route-ticket-targets-island-2",
                "player-sees-logical-island-not-island-2",
                "player-command-does-not-change"
            ),
            FinalRequestFlowPolicy.flow("island-1-soft-full-create-on-island-2")
        );
    }

    @Test
    void recordsScaleOutToFiveOrSixNodesWithoutPlayerFacingNodeNames() {
        assertEquals("player-facing-route-flow-uses-logical-island-names-and-hides-physical-node-names", FinalRequestFlowPolicy.PLAYER_TOPOLOGY_POLICY);
        assertEquals("island-node-pool-can-grow-without-player-command-or-ui-change", FinalRequestFlowPolicy.NODE_SCALE_OUT_POLICY);
        assertEquals(
            List.of(
                "island-5-and-island-6-register-heartbeats",
                "node-pool-adds-ready-candidates",
                "allocator-considers-all-ready-nodes",
                "existing-islands-remain-global-resources",
                "new-and-inactive-islands-can-activate-on-new-nodes",
                "players-keep-using-create-home-visit-commands",
                "player-facing-output-hides-island-5-and-island-6"
            ),
            FinalRequestFlowPolicy.flow("scale-to-five-or-six-island-nodes")
        );
    }

    @Test
    void rejectsUnknownFlowKeys() {
        assertTrue(FinalRequestFlowPolicy.knownFlow("island-create"));
        assertTrue(FinalRequestFlowPolicy.knownFlow("island-1-soft-full-create-on-island-2"));
        assertTrue(FinalRequestFlowPolicy.knownFlow("scale-to-five-or-six-island-nodes"));
        assertFalse(FinalRequestFlowPolicy.knownFlow("legacy-bungee-flow"));
        assertEquals(List.of(), FinalRequestFlowPolicy.flow(null));
    }
}
