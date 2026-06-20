package kr.lunaf.cloudislands.common.integration;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertTrue;

import org.junit.jupiter.api.Test;

class CloudIntegrationPolicyTest {
    @Test
    void pinsKnownHookEcosystemAndDistributedSafetyContract() {
        assertTrue(CloudIntegrationPolicy.knownPlugin("CoreProtect"));
        assertTrue(CloudIntegrationPolicy.knownPlugin("FastAsyncWorldEdit"));
        assertTrue(CloudIntegrationPolicy.knownPlugin("LuckPerms"));
        assertTrue(CloudIntegrationPolicy.knownPlugin("Plan"));
        assertFalse(CloudIntegrationPolicy.knownPlugin("UnknownSkyblockHook"));
        assertTrue(CloudIntegrationPolicy.DISTRIBUTED_HOOK_POLICY.contains("island-uuid"));
        assertTrue(CloudIntegrationPolicy.DISTRIBUTED_HOOK_POLICY.contains("runtime-fencing-token"));
        assertTrue(CloudIntegrationPolicy.DISTRIBUTED_HOOK_POLICY.contains("node-ownership"));
    }

    @Test
    void classifiesIntegrationCategories() {
        assertEquals("audit-rollback", CloudIntegrationPolicy.category("CoreProtect"));
        assertEquals("world-edit", CloudIntegrationPolicy.category("WorldEdit"));
        assertEquals("custom-items", CloudIntegrationPolicy.category("ItemsAdder"));
        assertEquals("stacker", CloudIntegrationPolicy.category("RoseStacker"));
        assertEquals("economy", CloudIntegrationPolicy.category("Vault"));
    }
}
