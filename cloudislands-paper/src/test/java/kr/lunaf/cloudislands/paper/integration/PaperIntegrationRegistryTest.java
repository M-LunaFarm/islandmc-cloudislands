package kr.lunaf.cloudislands.paper.integration;

import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertTrue;

import java.util.UUID;
import kr.lunaf.cloudislands.common.integration.CloudIntegrationPolicy;
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
}
