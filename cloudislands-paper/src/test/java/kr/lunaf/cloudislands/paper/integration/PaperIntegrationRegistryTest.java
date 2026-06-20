package kr.lunaf.cloudislands.paper.integration;

import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertTrue;

import java.util.UUID;
import kr.lunaf.cloudislands.common.integration.CloudIntegrationPolicy;
import kr.lunaf.cloudislands.paper.integration.coreprotect.CoreProtectIntegration;
import kr.lunaf.cloudislands.paper.integration.spi.IntegrationCapability;
import kr.lunaf.cloudislands.paper.integration.spi.IntegrationContext;
import kr.lunaf.cloudislands.paper.integration.spi.IntegrationResult;
import kr.lunaf.cloudislands.paper.integration.worldedit.WorldEditIntegration;
import org.junit.jupiter.api.Test;

class PaperIntegrationRegistryTest {
    @Test
    void coreStateChangingHooksRequireDistributedRuntimeClaims() {
        CloudIntegrationPolicy.HookDecision denied = CloudIntegrationPolicy.validateHookContext(
            new CloudIntegrationPolicy.HookContext("CoreProtect", null, "", 0L, false, "", true)
        );

        assertFalse(denied.allowed());
        assertTrue(denied.violations().contains("island-uuid-missing"));
        assertTrue(denied.violations().contains("runtime-fencing-token-missing"));
        assertTrue(denied.violations().contains("node-ownership-missing"));
        assertTrue(denied.violations().contains("core-idempotency-key-missing"));

        CloudIntegrationPolicy.HookDecision allowed = CloudIntegrationPolicy.validateHookContext(
            new CloudIntegrationPolicy.HookContext("CoreProtect", UUID.randomUUID(), "island-node-01", 42L, true, "coreprotect:rollback:1", true)
        );

        assertTrue(allowed.allowed());
    }

    @Test
    void presenceOnlyHooksDoNotBecomeCoreAuthority() {
        CloudIntegrationPolicy.HookDecision decision = CloudIntegrationPolicy.validateHookContext(
            new CloudIntegrationPolicy.HookContext("SuperVanish", null, "", 0L, false, "", false)
        );

        assertTrue(decision.allowed());
    }

    @Test
    void coreProtectAdapterPublishesRuntimeAuthorityAndStateCapabilities() {
        CoreProtectIntegration integration = new CoreProtectIntegration();
        assertTrue(integration.capabilities().contains(IntegrationCapability.RUNTIME_AUTHORITY));
        assertTrue(integration.capabilities().contains(IntegrationCapability.STATE_EXPORT));
        assertTrue(integration.capabilities().contains(IntegrationCapability.STATE_RESTORE));

        CloudIntegrationPolicy.HookDecision denied = integration.validateRuntimeAuthority(
            new IntegrationContext(null, "", 0L, false, "", java.util.Map.of()),
            true
        );

        assertFalse(denied.allowed());
        assertTrue(denied.violations().contains("island-uuid-missing"));
    }

    @Test
    void worldEditAdapterRequiresRuntimeAuthorityBeforeWorldStateHooks() {
        WorldEditIntegration integration = new WorldEditIntegration("FastAsyncWorldEdit");
        IntegrationContext context = new IntegrationContext(UUID.randomUUID(), "island-node-01", 99L, true, "fawe:restore:1", java.util.Map.of());

        assertTrue(integration.capabilities().contains(IntegrationCapability.RUNTIME_AUTHORITY));
        assertTrue(integration.validateRuntimeAuthority(context, true).allowed());
        IntegrationResult result = integration.restoreState(context);
        assertTrue(result.status() == IntegrationResult.Status.SKIPPED);
        assertTrue(result.message().contains("restore hook is not implemented"));
    }
}
