package kr.lunaf.cloudislands.common.design;

import org.junit.jupiter.api.Test;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertTrue;

class PortableIslandDesignPolicyTest {
    @Test
    void namesGlobalResourceAndPortableBundleDecision() {
        assertEquals(
                "CloudIslands manages islands as global resources on a Velocity network and dynamically activates them in an Island node pool",
                PortableIslandDesignPolicy.ONE_LINE_DEFINITION
        );
        assertTrue(PortableIslandDesignPolicy.coreDecisions().contains("islands-are-global-resources-not-fixed-to-servers"));
        assertTrue(PortableIslandDesignPolicy.coreDecisions().contains("islands-are-saved-as-portable-bundles"));
        assertTrue(PortableIslandDesignPolicy.coreDecisions().contains("island-nodes-are-execution-hosts-not-island-owners"));
        assertTrue(PortableIslandDesignPolicy.coreDecisions().contains("players-see-logical-islands-never-island-node-names"));
        assertTrue(PortableIslandDesignPolicy.coreDecisions().contains("adding-island-5-or-island-6-requires-registration-only-not-player-command-changes"));
    }

    @Test
    void pinsNodeAgnosticRestorePolicies() {
        assertEquals("node-agnostic-shard-cell-remap", PortableIslandDesignPolicy.PLACEMENT_POLICY);
        assertEquals("verify-checksum-then-restore-to-current-active-node", PortableIslandDesignPolicy.RESTORE_POLICY);
    }

    @Test
    void coversRequiredMultiNodeOutcomes() {
        assertTrue(PortableIslandDesignPolicy.requiredOutcome("lobby-can-query-island-info"));
        assertTrue(PortableIslandDesignPolicy.requiredOutcome("island-1-can-query-island-info"));
        assertTrue(PortableIslandDesignPolicy.requiredOutcome("island-2-can-query-island-info"));
        assertTrue(PortableIslandDesignPolicy.requiredOutcome("full-island-1-does-not-block-create-on-island-2"));
        assertTrue(PortableIslandDesignPolicy.requiredOutcome("inactive-island-can-open-on-island-2"));
        assertTrue(PortableIslandDesignPolicy.requiredOutcome("players-do-not-need-channel-or-node-knowledge"));
        assertTrue(PortableIslandDesignPolicy.requiredOutcome("player-facing-output-hides-island-node-names"));
        assertTrue(PortableIslandDesignPolicy.requiredOutcome("admins-can-drain-or-migrate-by-node"));
        assertTrue(PortableIslandDesignPolicy.requiredOutcome("island-3-and-island-4-can-be-added-later"));
        assertTrue(PortableIslandDesignPolicy.requiredOutcome("island-5-and-island-6-can-be-added-later"));
    }

    @Test
    void recordsExpectedAbServerMoveScenario() {
        assertTrue(PortableIslandDesignPolicy.knownScenario("a-server-to-b-server-move"));
        assertEquals(
                "island-active-on-a-server>save-portable-bundle-with-manifest-and-checksum>b-server-claims-runtime",
                PortableIslandDesignPolicy.expectedScenario("a-server-to-b-server-move").get(0)
                        + ">"
                        + PortableIslandDesignPolicy.expectedScenario("a-server-to-b-server-move").get(2)
                        + ">"
                        + PortableIslandDesignPolicy.expectedScenario("a-server-to-b-server-move").get(3)
        );
        assertTrue(PortableIslandDesignPolicy.scenarioSummary("a-server-to-b-server-move").contains("player-sees-same-island-without-node-knowledge"));
    }

    @Test
    void recordsSoftFullScaleOutAndAddonRemovalScenarios() {
        assertTrue(PortableIslandDesignPolicy.expectedScenario("island-1-soft-full-create-on-island-2").contains("new-island-create-skips-island-1"));
        assertTrue(PortableIslandDesignPolicy.expectedScenario("island-1-soft-full-create-on-island-2").contains("allocator-selects-island-2"));
        assertTrue(PortableIslandDesignPolicy.expectedScenario("island-1-soft-full-create-on-island-2").contains("player-output-still-says-logical-island"));
        assertTrue(PortableIslandDesignPolicy.expectedScenario("add-island-5-and-6").contains("players-do-not-change-commands"));
        assertTrue(PortableIslandDesignPolicy.expectedScenario("add-island-5-and-6").contains("players-never-see-island-5-or-island-6-as-destination"));
        assertTrue(PortableIslandDesignPolicy.expectedScenario("addon-disabled-or-removed").contains("core-island-create-home-visit-still-work"));
        assertTrue(PortableIslandDesignPolicy.expectedScenario("addon-disabled-or-removed").contains("reenable-restores-addon-state-by-island-uuid"));
        assertTrue(PortableIslandDesignPolicy.knownScenario("a-server-to-b-server-move-with-addon-disabled"));
        assertTrue(PortableIslandDesignPolicy.expectedScenario("a-server-to-b-server-move-with-addon-disabled").contains("b-server-claims-runtime-without-satis-runtime-components"));
        assertTrue(PortableIslandDesignPolicy.expectedScenario("a-server-to-b-server-move-with-addon-disabled").contains("satis-reenable-reconnects-state-by-island-uuid"));
    }
}
