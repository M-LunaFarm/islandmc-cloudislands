package kr.lunaf.cloudislands.common.integration;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertTrue;

import java.util.UUID;
import org.junit.jupiter.api.Test;

class CloudIntegrationPolicyTest {
    @Test
    void pinsKnownHookEcosystemAndDistributedSafetyContract() {
        assertTrue(CloudIntegrationPolicy.knownPlugin("CoreProtect"));
        assertTrue(CloudIntegrationPolicy.knownPlugin("FastAsyncWorldEdit"));
        assertTrue(CloudIntegrationPolicy.knownPlugin("LuckPerms"));
        assertTrue(CloudIntegrationPolicy.knownPlugin("Plan"));
        assertTrue(CloudIntegrationPolicy.knownPlugin("SuperVanish"));
        assertTrue(CloudIntegrationPolicy.knownPlugin("SlimeWorldManager"));
        assertFalse(CloudIntegrationPolicy.knownPlugin("UnknownSkyblockHook"));
        assertTrue(CloudIntegrationPolicy.DISTRIBUTED_HOOK_POLICY.contains("island-uuid"));
        assertTrue(CloudIntegrationPolicy.DISTRIBUTED_HOOK_POLICY.contains("runtime-fencing-token"));
        assertTrue(CloudIntegrationPolicy.DISTRIBUTED_HOOK_POLICY.contains("node-ownership"));
        assertTrue(CloudIntegrationPolicy.requiredRuntimeClaims().contains("core-idempotency-key"));
    }

    @Test
    void classifiesIntegrationCategories() {
        assertEquals("audit-rollback", CloudIntegrationPolicy.category("CoreProtect"));
        assertEquals("world-edit", CloudIntegrationPolicy.category("WorldEdit"));
        assertEquals("custom-items", CloudIntegrationPolicy.category("ItemsAdder"));
        assertEquals("stacker", CloudIntegrationPolicy.category("RoseStacker"));
        assertEquals("economy", CloudIntegrationPolicy.category("Vault"));
        assertEquals("presence", CloudIntegrationPolicy.category("SuperVanish"));
        assertEquals("world-storage", CloudIntegrationPolicy.category("SlimeWorldManager"));
    }

    @Test
    void rejectsStateChangingHooksWithoutDistributedRuntimeContext() {
        CloudIntegrationPolicy.HookDecision decision = CloudIntegrationPolicy.validateHookContext(
            new CloudIntegrationPolicy.HookContext("CoreProtect", null, "", 0L, false, "", true)
        );

        assertFalse(decision.allowed());
        assertTrue(decision.violations().contains("island-uuid-missing"));
        assertTrue(decision.violations().contains("node-id-missing"));
        assertTrue(decision.violations().contains("runtime-fencing-token-missing"));
        assertTrue(decision.violations().contains("node-ownership-missing"));
        assertTrue(decision.violations().contains("core-idempotency-key-missing"));
    }

    @Test
    void allowsStateChangingHooksWithFencedNodeOwnershipContext() {
        CloudIntegrationPolicy.HookDecision decision = CloudIntegrationPolicy.validateHookContext(
            new CloudIntegrationPolicy.HookContext(
                "FastAsyncWorldEdit",
                UUID.fromString("00000000-0000-0000-0000-000000000001"),
                "island-node-1",
                42L,
                true,
                "repair-42",
                true
            )
        );

        assertTrue(decision.allowed());
    }

    @Test
    void observationOnlyPresenceHooksDoNotBecomeMembershipAuthority() {
        CloudIntegrationPolicy.HookDecision decision = CloudIntegrationPolicy.validateHookContext(
            new CloudIntegrationPolicy.HookContext("SuperVanish", null, "", 0L, false, "", false)
        );

        assertTrue(decision.allowed());
        assertEquals("presence", CloudIntegrationPolicy.category("SuperVanish"));
    }
}
