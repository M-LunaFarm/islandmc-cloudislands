package kr.lunaf.cloudislands.paper.integration;

import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertTrue;

import java.util.Map;
import java.util.UUID;
import kr.lunaf.cloudislands.common.integration.CloudIntegrationPolicy;
import kr.lunaf.cloudislands.paper.integration.analytics.PlanIntegration;
import kr.lunaf.cloudislands.paper.integration.coreprotect.CoreProtectIntegration;
import kr.lunaf.cloudislands.paper.integration.customitem.CustomItemIntegration;
import kr.lunaf.cloudislands.paper.integration.permission.LuckPermsIntegration;
import kr.lunaf.cloudislands.paper.integration.stacker.StackerIntegration;
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
    void priorityStateAdaptersImplementFullIslandLifecycleContract() {
        IntegrationContext worldCell = new IntegrationContext(UUID.randomUUID(), "island-node-01", 77L, true, "lifecycle:1", Map.of(
            "world", "islands",
            "cell", "12,-4",
            "namespace", "itemsadder",
            "entityCountKey", "limits.entities.effective",
            "spawnerCountKey", "limits.spawners.effective",
            "bundleKey", "bundles/island.tar.zst"
        ));

        CoreProtectIntegration coreProtect = new CoreProtectIntegration();
        assertTrue(coreProtect.capabilities().contains(IntegrationCapability.ISLAND_ACTIVATE));
        assertTrue(coreProtect.capabilities().contains(IntegrationCapability.ISLAND_DEACTIVATE));
        assertEquals(IntegrationResult.Status.SUCCESS, coreProtect.onIslandActivate(worldCell).status());
        assertEquals(IntegrationResult.Status.SUCCESS, coreProtect.onIslandDeactivate(worldCell).status());

        CustomItemIntegration customItems = new CustomItemIntegration("ItemsAdder");
        assertTrue(customItems.capabilities().contains(IntegrationCapability.ISLAND_DEACTIVATE));
        assertEquals(IntegrationResult.Status.SUCCESS, customItems.onIslandActivate(worldCell).status());
        assertEquals(IntegrationResult.Status.SUCCESS, customItems.onIslandDeactivate(worldCell).status());

        StackerIntegration stacker = new StackerIntegration("RoseStacker");
        assertTrue(stacker.capabilities().contains(IntegrationCapability.ISLAND_ACTIVATE));
        assertTrue(stacker.capabilities().contains(IntegrationCapability.ISLAND_DEACTIVATE));
        assertEquals(IntegrationResult.Status.SUCCESS, stacker.onIslandActivate(worldCell).status());
        assertEquals(IntegrationResult.Status.SUCCESS, stacker.onIslandDeactivate(worldCell).status());
    }

    @Test
    void priorityAdaptersValidatePluginVersionMetadata() {
        CoreProtectIntegration integration = new CoreProtectIntegration();

        IntegrationResult missing = integration.validateVersion(new IntegrationContext(UUID.randomUUID(), "island-node-01", 77L, true, "coreprotect:version:1", Map.of()));
        assertEquals(IntegrationResult.Status.FAILED, missing.status());
        assertTrue(missing.message().contains("pluginVersion"));

        IntegrationResult tooOld = integration.validateVersion(new IntegrationContext(UUID.randomUUID(), "island-node-01", 77L, true, "coreprotect:version:2", Map.of(
            "pluginVersion", "22.1",
            "minSupportedVersion", "23.0"
        )));
        assertEquals(IntegrationResult.Status.FAILED, tooOld.status());
        assertTrue(tooOld.message().contains("older than supported"));

        IntegrationResult accepted = integration.validateVersion(new IntegrationContext(UUID.randomUUID(), "island-node-01", 77L, true, "coreprotect:version:3", Map.of(
            "pluginVersion", "23.2.1",
            "minSupportedVersion", "23.0"
        )));
        assertEquals(IntegrationResult.Status.SUCCESS, accepted.status());
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

    @Test
    void customItemAdaptersRequireExternalIdMappingsBeforeStateExport() {
        CustomItemIntegration integration = new CustomItemIntegration("ItemsAdder");

        assertTrue(integration.capabilities().contains(IntegrationCapability.STATE_EXPORT));
        assertTrue(integration.capabilities().contains(IntegrationCapability.STATE_RESTORE));
        assertTrue(integration.capabilities().contains(IntegrationCapability.RUNTIME_AUTHORITY));

        IntegrationResult denied = integration.exportState(new IntegrationContext(UUID.randomUUID(), "island-node-01", 21L, true, "itemsadder:export:1", Map.of(
            "world", "islands",
            "cell", "1,2",
            "bundleKey", "bundles/island.tar.zst"
        )));
        assertEquals(IntegrationResult.Status.FAILED, denied.status());
        assertTrue(denied.message().contains("externalBlockIds"));
        assertTrue(denied.message().contains("coreBlockValueKeys"));

        IntegrationResult allowed = integration.restoreState(new IntegrationContext(UUID.randomUUID(), "island-node-01", 21L, true, "itemsadder:restore:1", Map.of(
            "world", "islands",
            "cell", "1,2",
            "externalBlockIds", "itemsadder:ruby_ore,itemsadder:ruby_block",
            "coreBlockValueKeys", "ruby_ore,ruby_block",
            "bundleKey", "bundles/island.tar.zst"
        )));
        assertEquals(IntegrationResult.Status.SUCCESS, allowed.status());
    }

    @Test
    void stackerAdaptersRequireEffectiveEntityAndSpawnerCountKeys() {
        StackerIntegration integration = new StackerIntegration("RoseStacker");

        assertTrue(integration.capabilities().contains(IntegrationCapability.STATE_EXPORT));
        assertTrue(integration.capabilities().contains(IntegrationCapability.STATE_RESTORE));
        assertTrue(integration.capabilities().contains(IntegrationCapability.RUNTIME_AUTHORITY));

        IntegrationResult denied = integration.exportState(new IntegrationContext(UUID.randomUUID(), "island-node-01", 31L, true, "stacker:export:1", Map.of(
            "world", "islands",
            "cell", "3,4",
            "entityCountKey", "limits.entities.effective"
        )));
        assertEquals(IntegrationResult.Status.FAILED, denied.status());
        assertTrue(denied.message().contains("spawnerCountKey"));
        assertTrue(denied.message().contains("bundleKey"));

        IntegrationResult allowed = integration.exportState(new IntegrationContext(UUID.randomUUID(), "island-node-01", 31L, true, "stacker:export:2", Map.of(
            "world", "islands",
            "cell", "3,4",
            "entityCountKey", "limits.entities.effective",
            "spawnerCountKey", "limits.spawners.effective",
            "bundleKey", "bundles/island.tar.zst"
        )));
        assertEquals(IntegrationResult.Status.SUCCESS, allowed.status());

        IntegrationResult restored = integration.restoreState(new IntegrationContext(UUID.randomUUID(), "island-node-01", 31L, true, "stacker:restore:1", Map.of(
            "world", "islands",
            "cell", "3,4",
            "entityCountKey", "limits.entities.effective",
            "spawnerCountKey", "limits.spawners.effective",
            "bundleKey", "bundles/island.tar.zst"
        )));
        assertEquals(IntegrationResult.Status.SUCCESS, restored.status());
    }

    @Test
    void luckPermsAndPlanAdaptersStayObservationOnlyForCoreAuthority() {
        LuckPermsIntegration luckPerms = new LuckPermsIntegration();
        PlanIntegration plan = new PlanIntegration();
        IntegrationContext noIslandContext = new IntegrationContext(null, "", 0L, false, "", Map.of(
            "permissionNode", "cloudislands.admin.bypass",
            "bypassScope", "network"
        ));

        assertFalse(luckPerms.capabilities().contains(IntegrationCapability.RUNTIME_AUTHORITY));
        assertEquals(IntegrationResult.Status.SUCCESS, luckPerms.onIslandActivate(noIslandContext).status());

        IntegrationResult missingPlan = plan.exportState(new IntegrationContext(null, "", 0L, false, "", Map.of("analyticsScope", "island-presence")));
        assertEquals(IntegrationResult.Status.FAILED, missingPlan.status());
        assertTrue(missingPlan.message().contains("bundleKey"));

        IntegrationResult exportedPlan = plan.exportState(new IntegrationContext(null, "", 0L, false, "", Map.of(
            "analyticsScope", "island-presence",
            "bundleKey", "bundles/analytics.json"
        )));
        assertEquals(IntegrationResult.Status.SUCCESS, exportedPlan.status());
    }

    @Test
    void registryPromotesPriorityHookPluginsToSpecificAdapters() {
        String registry = readRegistrySource();

        assertTrue(registry.contains("new CustomItemIntegration(pluginName)"));
        assertTrue(registry.contains("new StackerIntegration(pluginName)"));
        assertTrue(registry.contains("new LuckPermsIntegration()"));
        assertTrue(registry.contains("new PlanIntegration()"));
        assertTrue(registry.contains("ItemsAdder\", \"Oraxen\", \"Nexo\", \"Slimefun"));
        assertTrue(registry.contains("RoseStacker\", \"WildStacker\", \"AdvancedSpawners"));
    }

    @Test
    void registryExposesGuardedAdapterLifecycleSurface() {
        String registrySource = readRegistrySource();

        assertTrue(registrySource.contains("public IntegrationResult onIslandActivate"));
        assertTrue(registrySource.contains("public IntegrationResult onIslandDeactivate"));
        assertTrue(registrySource.contains("public IntegrationResult exportState"));
        assertTrue(registrySource.contains("public IntegrationResult restoreState"));
        assertTrue(registrySource.contains("private IntegrationResult execute"));
        assertTrue(registrySource.contains("pluginEnabled(integration.pluginName())"));
        assertTrue(registrySource.contains("IntegrationResult.skipped(integration.pluginName() + \" is not enabled\")"));
    }

    private String readRegistrySource() {
        try {
            return java.nio.file.Files.readString(java.nio.file.Path.of("src/main/java/kr/lunaf/cloudislands/paper/integration/PaperIntegrationRegistry.java"));
        } catch (java.io.IOException exception) {
            throw new java.io.UncheckedIOException(exception);
        }
    }
}
