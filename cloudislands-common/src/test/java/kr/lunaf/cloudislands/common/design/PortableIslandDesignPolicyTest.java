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
        assertTrue(PortableIslandDesignPolicy.requiredOutcome("admins-can-drain-or-migrate-by-node"));
        assertTrue(PortableIslandDesignPolicy.requiredOutcome("island-3-and-island-4-can-be-added-later"));
    }
}
