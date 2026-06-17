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
                "core-api-create-home-route",
                "island-runtime-check",
                "active-runtime-uses-active-node",
                "inactive-runtime-activates-on-best-node",
                "route-ticket-ready",
                "velocity-connect",
                "paper-teleport"
            ),
            FinalRequestFlowPolicy.flow("island-home")
        );
        assertEquals(
            List.of(
                "player",
                "velocity-command-visit-player",
                "target-island-lookup",
                "public-ban-permission-check",
                "active-or-activate",
                "visitor-ticket-create",
                "connect",
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
    void rejectsUnknownFlowKeys() {
        assertTrue(FinalRequestFlowPolicy.knownFlow("island-create"));
        assertFalse(FinalRequestFlowPolicy.knownFlow("legacy-bungee-flow"));
        assertEquals(List.of(), FinalRequestFlowPolicy.flow(null));
    }
}
