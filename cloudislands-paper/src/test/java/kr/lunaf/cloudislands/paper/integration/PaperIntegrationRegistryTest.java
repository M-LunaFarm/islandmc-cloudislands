package kr.lunaf.cloudislands.paper.integration;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertTrue;

import java.nio.file.Files;
import java.nio.file.Path;
import java.util.List;
import java.util.Map;
import java.util.Set;
import java.util.UUID;
import kr.lunaf.cloudislands.common.integration.CloudIntegrationPolicy;
import kr.lunaf.cloudislands.paper.integration.analytics.PlanIntegration;
import kr.lunaf.cloudislands.paper.integration.economy.VaultIntegration;
import kr.lunaf.cloudislands.paper.integration.placeholder.PlaceholderApiIntegration;
import kr.lunaf.cloudislands.paper.integration.coreprotect.CoreProtectIntegration;
import kr.lunaf.cloudislands.paper.integration.customitem.CustomItemIntegration;
import kr.lunaf.cloudislands.paper.integration.permission.LuckPermsIntegration;
import kr.lunaf.cloudislands.paper.integration.spi.IntegrationCapability;
import kr.lunaf.cloudislands.paper.integration.spi.IntegrationContext;
import kr.lunaf.cloudislands.paper.integration.spi.IntegrationExternalRuntime;
import kr.lunaf.cloudislands.paper.integration.spi.IntegrationResult;
import kr.lunaf.cloudislands.paper.integration.spi.IntegrationSupportState;
import kr.lunaf.cloudislands.paper.integration.stacker.StackerIntegration;
import kr.lunaf.cloudislands.paper.integration.vanish.VanishIntegration;
import kr.lunaf.cloudislands.paper.integration.worldedit.WorldEditIntegration;
import org.junit.jupiter.api.Test;

class PaperIntegrationRegistryTest {
    @Test
    void explicitStateMutationsStillRequireDistributedRuntimeClaims() {
        CloudIntegrationPolicy.HookDecision denied = CloudIntegrationPolicy.validateHookContext(
            new CloudIntegrationPolicy.HookContext("CoreProtect", null, "", 0L, false, "", true)
        );

        assertFalse(denied.allowed());
        assertTrue(denied.violations().contains("island-uuid-missing"));
        assertTrue(denied.violations().contains("runtime-fencing-token-missing"));
        assertTrue(denied.violations().contains("node-ownership-missing"));
        assertTrue(denied.violations().contains("core-idempotency-key-missing"));
    }

    @Test
    void coreProtectIsAppendOnlyDiagnosticAndNeverABundleRestoreProvider() {
        CoreProtectIntegration integration = new CoreProtectIntegration(acceptingRuntime());

        assertEquals(Set.of(IntegrationCapability.DETECT, IntegrationCapability.VALIDATE_VERSION), integration.capabilities());
        assertFalse(integration.capabilities().contains(IntegrationCapability.RUNTIME_AUTHORITY));
        assertFalse(integration.capabilities().contains(IntegrationCapability.ISLAND_ACTIVATE));
        assertFalse(integration.capabilities().contains(IntegrationCapability.ISLAND_DEACTIVATE));
        assertFalse(integration.capabilities().contains(IntegrationCapability.STATE_EXPORT));
        assertFalse(integration.capabilities().contains(IntegrationCapability.STATE_RESTORE));
        assertEquals(IntegrationResult.Status.SKIPPED, integration.exportState(context()).status());
        assertEquals(IntegrationResult.Status.SKIPPED, integration.restoreState(context()).status());
    }

    @Test
    void worldEditAndFaweAreCompatibilityOnlyAndChunkBundleOwnsWorldState() {
        for (String pluginName : List.of("WorldEdit", "FastAsyncWorldEdit")) {
            WorldEditIntegration integration = new WorldEditIntegration(pluginName, acceptingRuntime());
            assertEquals(Set.of(IntegrationCapability.DETECT, IntegrationCapability.VALIDATE_VERSION), integration.capabilities());
            assertFalse(integration.capabilities().contains(IntegrationCapability.RUNTIME_AUTHORITY));
            assertFalse(integration.capabilities().contains(IntegrationCapability.STATE_EXPORT));
            assertFalse(integration.capabilities().contains(IntegrationCapability.STATE_RESTORE));
            assertEquals(IntegrationResult.Status.SKIPPED, integration.exportState(context()).status());
            assertEquals(IntegrationResult.Status.SKIPPED, integration.restoreState(context()).status());
        }
    }

    @Test
    void diagnosticAdaptersRetainVersionValidation() {
        CoreProtectIntegration coreProtect = new CoreProtectIntegration();
        IntegrationResult missing = coreProtect.validateVersion(new IntegrationContext(UUID.randomUUID(), "node", 1L, true, "version:1", Map.of()));
        assertEquals(IntegrationResult.Status.FAILED, missing.status());

        IntegrationResult old = coreProtect.validateVersion(new IntegrationContext(UUID.randomUUID(), "node", 1L, true, "version:2", Map.of(
            "pluginVersion", "22.1",
            "minSupportedVersion", "23.0"
        )));
        assertEquals(IntegrationResult.Status.FAILED, old.status());

        IntegrationResult current = new WorldEditIntegration("WorldEdit").validateVersion(new IntegrationContext(UUID.randomUUID(), "node", 1L, true, "version:3", Map.of(
            "pluginVersion", "7.4.4",
            "minSupportedVersion", "7.3.0"
        )));
        assertEquals(IntegrationResult.Status.SUCCESS, current.status());
    }

    @Test
    void craftEngineSlimefunAndStackersExposeRealRuntimeServices() {
        CustomItemIntegration craftEngine = new CustomItemIntegration("CraftEngine", acceptingRuntime());
        CustomItemIntegration slimefun = new CustomItemIntegration("Slimefun", acceptingRuntime());
        StackerIntegration roseStacker = new StackerIntegration("RoseStacker", acceptingRuntime());

        assertEquals(Set.of(IntegrationCapability.DETECT, IntegrationCapability.VALIDATE_VERSION, IntegrationCapability.RUNTIME_SERVICE), craftEngine.capabilities());
        assertEquals(Set.of(IntegrationCapability.DETECT, IntegrationCapability.VALIDATE_VERSION, IntegrationCapability.RUNTIME_SERVICE), slimefun.capabilities());
        assertEquals(Set.of(IntegrationCapability.DETECT, IntegrationCapability.VALIDATE_VERSION, IntegrationCapability.RUNTIME_SERVICE), roseStacker.capabilities());
        assertEquals(IntegrationSupportState.ACTIVE, PaperIntegrationRegistry.adapterState(craftEngine, true, IntegrationSupportState.API_COMPATIBLE));
        assertEquals(IntegrationSupportState.ACTIVE, PaperIntegrationRegistry.adapterState(slimefun, true, IntegrationSupportState.API_COMPATIBLE));
        assertEquals(IntegrationResult.Status.SKIPPED, roseStacker.exportState(context()).status());
        assertEquals(IntegrationResult.Status.SKIPPED, roseStacker.restoreState(context()).status());
    }

    @Test
    void permissionAdapterRemainsDiagnosticWhilePlanIsAnExecutableRuntimeService() {
        LuckPermsIntegration luckPerms = new LuckPermsIntegration(acceptingRuntime());
        PlanIntegration plan = new PlanIntegration(acceptingRuntime());

        assertEquals(Set.of(IntegrationCapability.DETECT, IntegrationCapability.VALIDATE_VERSION), luckPerms.capabilities());
        assertEquals(Set.of(IntegrationCapability.DETECT, IntegrationCapability.VALIDATE_VERSION, IntegrationCapability.RUNTIME_SERVICE), plan.capabilities());
    }

    @Test
    void supportStatesSeparateDiagnosticAdaptersFromExecutableAdapters() {
        assertEquals(
            IntegrationSupportState.DIAGNOSTIC_ONLY,
            PaperIntegrationRegistry.adapterState(new CoreProtectIntegration(), true, IntegrationSupportState.API_COMPATIBLE)
        );
        assertEquals(
            IntegrationSupportState.DIAGNOSTIC_ONLY,
            PaperIntegrationRegistry.adapterState(new WorldEditIntegration("WorldEdit"), true, IntegrationSupportState.API_COMPATIBLE)
        );
        assertEquals(
            IntegrationSupportState.ACTIVE,
            PaperIntegrationRegistry.adapterState(new StackerIntegration("RoseStacker"), true, IntegrationSupportState.API_COMPATIBLE)
        );
        assertEquals(
            IntegrationSupportState.ADAPTER_INACTIVE,
            PaperIntegrationRegistry.adapterState(new CoreProtectIntegration(), false, IntegrationSupportState.NOT_INSTALLED)
        );
        assertEquals(IntegrationSupportState.OPERATION_SUCCEEDED, PaperIntegrationRegistry.operationState(IntegrationResult.success("ok")));
        assertEquals(IntegrationSupportState.OPERATION_FAILED, PaperIntegrationRegistry.operationState(IntegrationResult.failed("bad")));
        assertEquals(IntegrationSupportState.ADAPTER_INACTIVE, PaperIntegrationRegistry.operationState(IntegrationResult.skipped("not run")));
    }

    @Test
    void realVaultPlaceholderPlanVanishCustomBlockAndStackServicesAreExecutableIntegrations() {
        VaultIntegration vault = new VaultIntegration();
        PlaceholderApiIntegration placeholder = new PlaceholderApiIntegration();
        PlanIntegration plan = new PlanIntegration();
        VanishIntegration vanish = new VanishIntegration("SuperVanish");
        CustomItemIntegration customBlocks = new CustomItemIntegration("ItemsAdder");
        StackerIntegration stackAmounts = new StackerIntegration("RoseStacker");

        assertTrue(vault.capabilities().contains(IntegrationCapability.RUNTIME_SERVICE));
        assertTrue(placeholder.capabilities().contains(IntegrationCapability.RUNTIME_SERVICE));
        assertTrue(plan.capabilities().contains(IntegrationCapability.RUNTIME_SERVICE));
        assertTrue(vanish.capabilities().contains(IntegrationCapability.RUNTIME_SERVICE));
        assertTrue(customBlocks.capabilities().contains(IntegrationCapability.RUNTIME_SERVICE));
        assertTrue(stackAmounts.capabilities().contains(IntegrationCapability.RUNTIME_SERVICE));
        assertEquals(
            IntegrationSupportState.ACTIVE,
            PaperIntegrationRegistry.adapterState(vault, true, IntegrationSupportState.API_COMPATIBLE)
        );
        assertEquals(
            IntegrationSupportState.ACTIVE,
            PaperIntegrationRegistry.adapterState(placeholder, true, IntegrationSupportState.API_COMPATIBLE)
        );
        assertEquals(
            IntegrationSupportState.ACTIVE,
            PaperIntegrationRegistry.adapterState(plan, true, IntegrationSupportState.API_COMPATIBLE)
        );
        assertEquals(
            IntegrationSupportState.ACTIVE,
            PaperIntegrationRegistry.adapterState(vanish, true, IntegrationSupportState.API_COMPATIBLE)
        );
        assertEquals(
            IntegrationSupportState.ACTIVE,
            PaperIntegrationRegistry.adapterState(customBlocks, true, IntegrationSupportState.API_COMPATIBLE)
        );
        assertEquals(
            IntegrationSupportState.ACTIVE,
            PaperIntegrationRegistry.adapterState(stackAmounts, true, IntegrationSupportState.API_COMPATIBLE)
        );
    }

    @Test
    void registryWiresSpecificAdaptersAndRejectsUndeclaredOperations() {
        String registry = source("src/main/java/kr/lunaf/cloudislands/paper/integration/PaperIntegrationRegistry.java");

        assertTrue(registry.contains("new CoreProtectIntegration(externalRuntime)"));
        assertTrue(registry.contains("new WorldEditIntegration(pluginName, externalRuntime)"));
        assertTrue(registry.contains("new CustomItemIntegration(pluginName, externalRuntime)"));
        assertTrue(registry.contains("new StackerIntegration(pluginName, externalRuntime)"));
        assertTrue(registry.contains("new VanishIntegration(pluginName, externalRuntime)"));
        assertTrue(registry.contains("!integration.capabilities().contains(capability)"));
        assertTrue(registry.contains("does not declare \" + capability + \" support"));
        assertTrue(registry.contains("withPluginRuntimeMetadata"));
        assertTrue(registry.contains("runtimeCertificationResults"));
        assertTrue(registry.contains("reportRuntimeService"));
    }

    @Test
    void bukkitRuntimeNeverPromotesApiProbeToCompletedOperation() {
        String runtime = source("src/main/java/kr/lunaf/cloudislands/paper/integration/BukkitIntegrationExternalRuntime.java");

        assertTrue(runtime.contains("apiProbe.method.getAPI"));
        assertTrue(runtime.contains("apiProbe.class.WorldEdit"));
        assertTrue(runtime.contains("apiProbe.class.BlockStorage"));
        assertTrue(runtime.contains("apiProbe.method.BlockStorage.checkID"));
        assertTrue(runtime.contains("apiProbe.class.CraftEngineBlocks"));
        assertTrue(runtime.contains("apiProbe.class.CraftEngineFurniture"));
        assertTrue(runtime.contains("apiProbe.method.CraftEngineBlocks.getCustomBlockState"));
        assertTrue(runtime.contains("apiProbe.method.CraftEngineFurniture.getLoadedFurnitureByMetaEntity"));
        assertTrue(runtime.contains("bukkitService(\"net.luckperms.api.LuckPerms\")"));
        assertTrue(runtime.contains("IntegrationResult.skipped(pluginName + \" Bukkit adapter verified API"));
        assertFalse(runtime.contains("IntegrationResult.success(pluginName + \" Bukkit adapter accepted"));
    }

    private static IntegrationContext context() {
        return new IntegrationContext(UUID.randomUUID(), "island-node-01", 77L, true, "diagnostic:1", Map.of(
            "world", "islands",
            "cell", "1,2",
            "region", "0,-64,0..63,319,63",
            "bundleKey", "bundle.tar.zst"
        ));
    }

    private static String source(String path) {
        try {
            return Files.readString(Path.of(path));
        } catch (java.io.IOException exception) {
            throw new java.io.UncheckedIOException(exception);
        }
    }

    private static IntegrationExternalRuntime acceptingRuntime() {
        return (_pluginName, _category, _operation, _context, _plan) -> IntegrationResult.success("external called", Map.of(
            "roundTripVerified", "true",
            "stateArtifact", "integration-runtime-proof"
        ));
    }
}
