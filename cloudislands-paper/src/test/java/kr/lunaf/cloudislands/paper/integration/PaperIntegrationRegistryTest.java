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
        assertTrue(missingResult.message().contains("region"));
        assertTrue(missingResult.message().contains("bundleKey"));
        assertEquals("CoreProtectAPI#performLookup", missingResult.details().get("external.api"));

        IntegrationContext exportContext = new IntegrationContext(UUID.randomUUID(), "island-node-01", 77L, true, "coreprotect:export:2", Map.of(
            "world", "islands",
            "cell", "12,-4",
            "region", "192,64,-64..255,319,-1",
            "bundleKey", "bundles/island.tar.zst"
        ));
        IntegrationResult exportResult = integration.exportState(exportContext);
        assertEquals(IntegrationResult.Status.SUCCESS, exportResult.status());
        assertEquals("CoreProtect", exportResult.details().get("plugin"));
        assertEquals("audit-export", exportResult.details().get("operation"));
        assertEquals(exportContext.islandId().toString(), exportResult.details().get("islandId"));
        assertEquals("island-node-01", exportResult.details().get("nodeId"));
        assertEquals("77", exportResult.details().get("fencingToken"));
        assertEquals("coreprotect:export:2", exportResult.details().get("idempotencyKey"));
        assertEquals("bundles/island.tar.zst", exportResult.details().get("metadata.bundleKey"));
        assertEquals("CoreProtect", exportResult.details().get("manifest.plugin"));
        assertEquals("audit-rollback", exportResult.details().get("manifest.category"));
        assertEquals("audit-export", exportResult.details().get("manifest.operation"));
        assertEquals("islands", exportResult.details().get("manifest.world"));
        assertEquals("12,-4", exportResult.details().get("manifest.cell"));
        assertEquals("islands:12,-4", exportResult.details().get("manifest.runtimeScope"));
        assertEquals("192,64,-64..255,319,-1", exportResult.details().get("manifest.metadata.region"));
        assertEquals("bundles/island.tar.zst", exportResult.details().get("manifest.bundleKey"));
        assertEquals("audit-rollback/CoreProtect/" + exportContext.islandId() + "/bundles/island.tar.zst/audit-export", exportResult.details().get("manifest.stateKey"));
        assertEquals("integrations/audit-rollback/CoreProtect/audit-export.json", exportResult.details().get("manifest.bundleRelativePath"));
        assertEquals("coreprotect:export:2", exportResult.details().get("manifest.idempotencyKey"));
        assertEquals("CoreProtectAPI#performLookup", exportResult.details().get("external.api"));
        assertEquals("region-audit-cursor,coreprotect-lookup-events", exportResult.details().get("external.artifacts"));
        assertEquals("runtime-authority,fencing-token,idempotency-key,region-boundary", exportResult.details().get("external.safetyBarriers"));
        assertEquals("CoreProtectAPI#performLookup", exportResult.details().get("plan.externalApi"));
        assertEquals("world,cell,region,bundleKey", exportResult.details().get("plan.requiredMetadata"));
        assertEquals("true", exportResult.details().get("plan.stateChanging"));

        IntegrationContext restoreContext = new IntegrationContext(UUID.randomUUID(), "island-node-01", 77L, true, "coreprotect:restore:1", Map.of(
            "world", "islands",
            "cell", "12,-4",
            "region", "192,64,-64..255,319,-1",
            "rollbackSeconds", "3600",
            "bundleKey", "bundles/island.tar.zst"
        ));
        IntegrationResult restoreResult = integration.restoreState(restoreContext);
        assertEquals(IntegrationResult.Status.SUCCESS, restoreResult.status());
        assertEquals("rollback-restore", restoreResult.details().get("operation"));
        assertEquals("3600", restoreResult.details().get("metadata.rollbackSeconds"));
        assertEquals("3600", restoreResult.details().get("manifest.metadata.rollbackSeconds"));
        assertEquals("true", restoreResult.details().get("nodeOwnsIsland"));
        assertEquals("CoreProtectAPI#performRollback", restoreResult.details().get("external.api"));
        assertEquals("rollback-plan,affected-region-audit", restoreResult.details().get("external.artifacts"));
        assertEquals("runtime-authority,fencing-token,idempotency-key,region-boundary", restoreResult.details().get("external.safetyBarriers"));
        assertEquals("world,cell,region,rollbackSeconds,bundleKey", restoreResult.details().get("plan.requiredMetadata"));
    }

    @Test
    void priorityStateAdaptersImplementFullIslandLifecycleContract() {
        IntegrationContext worldCell = new IntegrationContext(UUID.randomUUID(), "island-node-01", 77L, true, "lifecycle:1", Map.of(
            "world", "islands",
            "cell", "12,-4",
            "region", "192,64,-64..255,319,-1",
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
    void integrationContextMergesRuntimeMetadataWithoutOverwritingCallerValues() {
        IntegrationContext context = new IntegrationContext(UUID.randomUUID(), "island-node-01", 77L, true, "coreprotect:version:4", Map.of(
            "pluginVersion", "23.1",
            "minSupportedVersion", "23.0"
        ));

        IntegrationContext merged = context.withMetadata(Map.of(
            "pluginVersion", "24.0",
            "serverPluginVersion", "24.0"
        ));

        assertEquals("23.1", merged.metadata().get("pluginVersion"));
        assertEquals("24.0", merged.metadata().get("serverPluginVersion"));
    }

    @Test
    void worldEditAdapterRequiresRuntimeAuthorityBeforeWorldStateHooks() {
        WorldEditIntegration integration = new WorldEditIntegration("FastAsyncWorldEdit");
        IntegrationContext context = new IntegrationContext(UUID.randomUUID(), "island-node-01", 99L, true, "fawe:restore:1", Map.of(
            "world", "islands",
            "cell", "0,0",
            "region", "0,64,0..63,319,63",
            "bundleKey", "bundles/island.tar.zst"
        ));

        assertTrue(integration.capabilities().contains(IntegrationCapability.RUNTIME_AUTHORITY));
        assertTrue(integration.capabilities().contains(IntegrationCapability.ISLAND_DEACTIVATE));
        assertTrue(integration.validateRuntimeAuthority(context, true).allowed());
        IntegrationResult result = integration.restoreState(context);
        assertEquals(IntegrationResult.Status.SUCCESS, result.status());
        assertEquals("world-edit", result.details().get("manifest.category"));
        assertEquals("schematic-restore", result.details().get("manifest.operation"));
        assertEquals("0,64,0..63,319,63", result.details().get("manifest.metadata.region"));
        assertEquals("ClipboardReader#read+EditSession#paste", result.details().get("external.api"));
        assertEquals("clipboard-schematic,paste-operation-plan", result.details().get("external.artifacts"));
        assertEquals("runtime-authority,fencing-token,idempotency-key,region-boundary", result.details().get("external.safetyBarriers"));
        assertEquals("ClipboardReader#read+EditSession#paste", result.details().get("plan.externalApi"));
        assertEquals("world,cell,region,bundleKey", result.details().get("plan.requiredMetadata"));
    }

    @Test
    void worldEditAdapterRequiresOperationDrainBeforeDeactivation() {
        WorldEditIntegration integration = new WorldEditIntegration("WorldEdit");
        IntegrationContext context = new IntegrationContext(UUID.randomUUID(), "island-node-01", 99L, true, "worldedit:deactivate:1", Map.of(
            "world", "islands",
            "cell", "0,0",
            "region", "0,64,0..63,319,63",
            "activeOperationsDrained", "true",
            "editSessionFlushed", "true"
        ));

        IntegrationResult result = integration.onIslandDeactivate(context);
        assertEquals(IntegrationResult.Status.SUCCESS, result.status());
        assertEquals("edit-session-deactivate", result.details().get("manifest.operation"));
        assertEquals("EditSession#flushQueue+Operations#complete", result.details().get("external.api"));
        assertEquals("operation-drain-marker,edit-session-flush-marker", result.details().get("external.artifacts"));
        assertEquals(
            "runtime-authority,fencing-token,idempotency-key,active-operations-drained,edit-session-flushed,region-boundary",
            result.details().get("external.safetyBarriers")
        );
    }

    @Test
    void worldEditAdapterRejectsStateHooksWithoutCellAndBundleContext() {
        WorldEditIntegration integration = new WorldEditIntegration("WorldEdit");
        IntegrationContext context = new IntegrationContext(UUID.randomUUID(), "island-node-01", 99L, true, "worldedit:restore:1", Map.of("world", "islands"));

        IntegrationResult result = integration.restoreState(context);
        assertEquals(IntegrationResult.Status.FAILED, result.status());
        assertTrue(result.message().contains("cell"));
        assertTrue(result.message().contains("region"));
        assertTrue(result.message().contains("bundleKey"));
        assertEquals("ClipboardReader#read+EditSession#paste", result.details().get("external.api"));
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
        assertEquals("ItemsAdder registry#serializeCustomBlockState", denied.details().get("external.api"));

        IntegrationResult allowed = integration.restoreState(new IntegrationContext(UUID.randomUUID(), "island-node-01", 21L, true, "itemsadder:restore:1", Map.of(
            "world", "islands",
            "cell", "1,2",
            "externalBlockIds", "itemsadder:ruby_ore,itemsadder:ruby_block",
            "coreBlockValueKeys", "ruby_ore,ruby_block",
            "bundleKey", "bundles/island.tar.zst"
        )));
        assertEquals(IntegrationResult.Status.SUCCESS, allowed.status());
        assertEquals("custom-items", allowed.details().get("manifest.category"));
        assertEquals("itemsadder:ruby_ore,itemsadder:ruby_block", allowed.details().get("manifest.metadata.externalBlockIds"));
        assertEquals("ItemsAdder registry#restoreCustomBlockState", allowed.details().get("external.api"));
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
        assertEquals("RoseStacker API#serializeStackedEntitiesAndSpawners", denied.details().get("external.api"));

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
        assertEquals("stacker", restored.details().get("manifest.category"));
        assertEquals("limits.spawners.effective", restored.details().get("manifest.metadata.spawnerCountKey"));
        assertEquals("RoseStacker API#restoreStackedEntitiesAndSpawners", restored.details().get("external.api"));
    }

    @Test
    void luckPermsAndPlanAdaptersDeclareCorrectAuthorityBoundaries() {
        LuckPermsIntegration luckPerms = new LuckPermsIntegration();
        PlanIntegration plan = new PlanIntegration();
        IntegrationContext luckPermsContext = new IntegrationContext(UUID.randomUUID(), "island-node-01", 42L, true, "luckperms:activate:1", Map.of(
            "permissionNode", "cloudislands.admin.bypass",
            "bypassScope", "network"
        ));

        assertTrue(luckPerms.capabilities().contains(IntegrationCapability.RUNTIME_AUTHORITY));
        assertTrue(luckPerms.capabilities().contains(IntegrationCapability.STATE_EXPORT));
        assertTrue(luckPerms.capabilities().contains(IntegrationCapability.STATE_RESTORE));
        assertTrue(CloudIntegrationPolicy.requiresRuntimeAuthority("LuckPerms", false));
        IntegrationResult luckPermsResult = luckPerms.onIslandActivate(luckPermsContext);
        assertEquals(IntegrationResult.Status.SUCCESS, luckPermsResult.status());
        assertEquals("LuckPerms#contextManager+cachedData", luckPermsResult.details().get("external.api"));

        IntegrationResult deniedLuckPermsExport = luckPerms.exportState(new IntegrationContext(UUID.randomUUID(), "island-node-01", 42L, true, "luckperms:export:1", Map.of(
            "world", "islands",
            "cell", "4,5",
            "permissionNode", "cloudislands.island.member",
            "bypassScope", "island"
        )));
        assertEquals(IntegrationResult.Status.FAILED, deniedLuckPermsExport.status());
        assertTrue(deniedLuckPermsExport.message().contains("contextKey"));
        assertTrue(deniedLuckPermsExport.message().contains("bundleKey"));
        assertEquals("LuckPerms#userManager+trackManager#saveContextState", deniedLuckPermsExport.details().get("external.api"));

        IntegrationResult restoredLuckPerms = luckPerms.restoreState(new IntegrationContext(UUID.randomUUID(), "island-node-01", 42L, true, "luckperms:restore:1", Map.of(
            "world", "islands",
            "cell", "4,5",
            "permissionNode", "cloudislands.island.member",
            "bypassScope", "island",
            "contextKey", "cloudislands:island",
            "bundleKey", "bundles/luckperms-context.json"
        )));
        assertEquals(IntegrationResult.Status.SUCCESS, restoredLuckPerms.status());
        assertEquals("permission", restoredLuckPerms.details().get("manifest.category"));
        assertEquals("permission-context-restore", restoredLuckPerms.details().get("manifest.operation"));
        assertEquals("cloudislands:island", restoredLuckPerms.details().get("manifest.metadata.contextKey"));
        assertEquals("LuckPerms#userManager+trackManager#restoreContextState", restoredLuckPerms.details().get("external.api"));

        assertFalse(plan.capabilities().contains(IntegrationCapability.RUNTIME_AUTHORITY));
        assertTrue(plan.capabilities().contains(IntegrationCapability.STATE_EXPORT));
        assertTrue(plan.capabilities().contains(IntegrationCapability.STATE_RESTORE));
        IntegrationResult missingPlan = plan.exportState(new IntegrationContext(null, "", 0L, false, "", Map.of("analyticsScope", "island-presence")));
        assertEquals(IntegrationResult.Status.FAILED, missingPlan.status());
        assertTrue(missingPlan.message().contains("bundleKey"));

        IntegrationResult exportedPlan = plan.exportState(new IntegrationContext(null, "", 0L, false, "", Map.of(
            "analyticsScope", "island-presence",
            "bundleKey", "bundles/analytics.json"
        )));
        assertEquals(IntegrationResult.Status.SUCCESS, exportedPlan.status());
        assertEquals("PlanAPI#queryService", exportedPlan.details().get("external.api"));

        IntegrationResult restoredPlan = plan.restoreState(new IntegrationContext(null, "", 0L, false, "", Map.of(
            "analyticsScope", "island-presence",
            "bundleKey", "bundles/analytics.json"
        )));
        assertEquals(IntegrationResult.Status.SUCCESS, restoredPlan.status());
        assertEquals("PlanAPI#importService", restoredPlan.details().get("external.api"));
        assertEquals("analytics-restore", restoredPlan.details().get("operation"));
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
        assertTrue(registrySource.contains("integration.detect(pluginEnabled(integration.pluginName()))"));
        assertTrue(registrySource.contains("pluginEnabled(integration.pluginName())"));
        assertTrue(registrySource.contains("IntegrationResult.skipped(integration.pluginName() + \" is not enabled\")"));
        assertTrue(registrySource.contains("withPluginRuntimeMetadata(integration.pluginName(), context, plugin)"));
        assertTrue(registrySource.contains("runtimeMetadata.put(\"pluginClass\", plugin.getClass().getName())"));
        assertTrue(registrySource.contains("runtimeMetadata.put(\"pluginEnabled\", Boolean.toString(pluginEnabled(pluginName)))"));
        assertTrue(registrySource.contains("getPlugin(pluginName)"));
    }

    private String readRegistrySource() {
        try {
            return java.nio.file.Files.readString(java.nio.file.Path.of("src/main/java/kr/lunaf/cloudislands/paper/integration/PaperIntegrationRegistry.java"));
        } catch (java.io.IOException exception) {
            throw new java.io.UncheckedIOException(exception);
        }
    }
}
