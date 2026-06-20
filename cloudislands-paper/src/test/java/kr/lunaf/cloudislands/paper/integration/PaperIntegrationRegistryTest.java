package kr.lunaf.cloudislands.paper.integration;

import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertTrue;

import java.util.Map;
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
    void coreProtectAdapterRequiresDistributedExportAndRollbackContext() {
        CoreProtectIntegration integration = new CoreProtectIntegration();
        IntegrationContext missing = new IntegrationContext(UUID.randomUUID(), "island-node-01", 77L, true, "coreprotect:export:1", Map.of("world", "islands"));

        IntegrationResult missingResult = integration.exportState(missing);
        assertEquals(IntegrationResult.Status.FAILED, missingResult.status());
        assertTrue(missingResult.message().contains("missing metadata"));
        assertTrue(missingResult.message().contains("cell"));
        assertTrue(missingResult.message().contains("bundleKey"));

        IntegrationContext exportContext = new IntegrationContext(UUID.randomUUID(), "island-node-01", 77L, true, "coreprotect:export:2", Map.of(
            "world", "islands",
            "cell", "12,-4",
            "bundleKey", "bundles/island.tar.zst"
        ));
        assertEquals(IntegrationResult.Status.SUCCESS, integration.exportState(exportContext).status());

        IntegrationContext restoreContext = new IntegrationContext(UUID.randomUUID(), "island-node-01", 77L, true, "coreprotect:restore:1", Map.of(
            "world", "islands",
            "cell", "12,-4",
            "rollbackSeconds", "3600",
            "bundleKey", "bundles/island.tar.zst"
        ));
        assertEquals(IntegrationResult.Status.SUCCESS, integration.restoreState(restoreContext).status());
    }

    @Test
    void worldEditAdapterRequiresRuntimeAuthorityBeforeWorldStateHooks() {
        WorldEditIntegration integration = new WorldEditIntegration("FastAsyncWorldEdit");
        IntegrationContext context = new IntegrationContext(UUID.randomUUID(), "island-node-01", 99L, true, "fawe:restore:1", Map.of(
            "world", "islands",
            "cell", "0,0",
            "bundleKey", "bundles/island.tar.zst"
        ));

        assertTrue(integration.capabilities().contains(IntegrationCapability.RUNTIME_AUTHORITY));
        assertTrue(integration.validateRuntimeAuthority(context, true).allowed());
        IntegrationResult result = integration.restoreState(context);
        assertEquals(IntegrationResult.Status.SUCCESS, result.status());
    }

    @Test
    void worldEditAdapterRejectsStateHooksWithoutCellAndBundleContext() {
        WorldEditIntegration integration = new WorldEditIntegration("WorldEdit");
        IntegrationContext context = new IntegrationContext(UUID.randomUUID(), "island-node-01", 99L, true, "worldedit:restore:1", Map.of("world", "islands"));

        IntegrationResult result = integration.restoreState(context);
        assertEquals(IntegrationResult.Status.FAILED, result.status());
        assertTrue(result.message().contains("cell"));
        assertTrue(result.message().contains("bundleKey"));
    }
}
