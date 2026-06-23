package kr.lunaf.cloudislands.paper.integration;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertTrue;

import java.io.IOException;
import java.nio.file.Files;
import java.nio.file.Path;
import java.time.Instant;
import java.util.ArrayList;
import java.util.List;
import java.util.UUID;
import kr.lunaf.cloudislands.api.model.IslandLocation;
import kr.lunaf.cloudislands.common.json.SimpleJson;
import kr.lunaf.cloudislands.paper.activation.ActiveIslandRegistry;
import kr.lunaf.cloudislands.paper.integration.analytics.PlanIntegration;
import kr.lunaf.cloudislands.paper.integration.coreprotect.CoreProtectIntegration;
import kr.lunaf.cloudislands.paper.integration.customitem.CustomItemIntegration;
import kr.lunaf.cloudislands.paper.integration.permission.LuckPermsIntegration;
import kr.lunaf.cloudislands.paper.integration.spi.IntegrationResult;
import kr.lunaf.cloudislands.paper.integration.stacker.StackerIntegration;
import kr.lunaf.cloudislands.paper.integration.worldedit.WorldEditIntegration;
import kr.lunaf.cloudislands.storage.IslandBundleManifest;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.io.TempDir;

class IntegrationLifecycleHooksTest {
    @TempDir
    Path tempDir;

    @Test
    void lifecycleHooksSupplyBundleMetadataToPriorityAdapters() throws IOException {
        UUID islandId = UUID.randomUUID();
        IntegrationLifecycleHooks hooks = IntegrationLifecycleHooks.direct("island-node-01", List.of(
            new CoreProtectIntegration(),
            new WorldEditIntegration("WorldEdit"),
            new CustomItemIntegration("ItemsAdder"),
            new StackerIntegration("RoseStacker"),
            new LuckPermsIntegration(),
            new PlanIntegration()
        ));
        ActiveIslandRegistry.ActiveIsland activeIsland = new ActiveIslandRegistry.ActiveIsland(
            islandId,
            "ci_shard_001",
            12,
            -4,
            192,
            -64,
            300,
            12L,
            77L,
            Instant.now()
        );

        IntegrationLifecycleHooks.LifecycleBatch activationBatch = hooks.onIslandActivated(islandId, activeIsland);
        activationBatch.throwIfFailed();

        assertEquals(6, activationBatch.results().size());
        assertTrue(activationBatch.results().stream().allMatch(result -> result.status() == IntegrationResult.Status.SUCCESS));
        assertEquals("activate", activationBatch.operation());
        assertEquals("island-activate:" + islandId + ":0", activationBatch.context().idempotencyKey());
        assertEquals("12,-4", activationBatch.context().metadata().get("cell"));

        IntegrationLifecycleHooks.LifecycleBatch exportBatch = hooks.exportState(islandId, activeIsland, 1234L, Path.of("1234-bundle.tar.zst"));
        exportBatch.throwIfFailed();

        assertEquals(6, exportBatch.results().size());
        assertTrue(exportBatch.results().stream().allMatch(result -> result.status() == IntegrationResult.Status.SUCCESS));
        assertEquals("12,-4", exportBatch.context().metadata().get("cell"));
        assertEquals("192,0,-64..491,319,235", exportBatch.context().metadata().get("region"));
        assertEquals("1234-bundle.tar.zst", exportBatch.context().metadata().get("bundleKey"));
        assertEquals("cloudislands:island", exportBatch.context().metadata().get("contextKey"));
        assertEquals("true", exportBatch.context().metadata().get("activeOperationsDrained"));
        assertEquals("true", exportBatch.context().metadata().get("editSessionFlushed"));
        assertEquals("77", exportBatch.results().getFirst().details().get("fencingToken"));

        IslandBundleManifest manifest = manifest(islandId, 300);
        Path extractedRoot = tempDir.resolve("extracted");
        IntegrationLifecycleHooks.LifecycleBatch restoreBatch = hooks.restoreState(islandId, "ci_shard_001", 12, -4, 192, -64, 77L, 1234L, "snapshots/island.tar.zst", Path.of("bundle.tar.zst"), extractedRoot, manifest);
        restoreBatch.throwIfFailed();

        assertEquals(6, restoreBatch.results().size());
        assertTrue(restoreBatch.results().stream().allMatch(result -> result.status() == IntegrationResult.Status.SUCCESS));
        assertEquals("0", restoreBatch.context().metadata().get("rollbackSeconds"));
        assertEquals("snapshots/island.tar.zst", restoreBatch.context().metadata().get("bundleKey"));
        assertEquals("bundle.tar.zst", restoreBatch.context().metadata().get("bundlePath"));
        assertEquals("snapshots/island.tar.zst", restoreBatch.context().metadata().get("storagePath"));
        assertEquals(extractedRoot.resolve("integrations").toString(), restoreBatch.context().metadata().get("integrationStateRoot"));

        IntegrationLifecycleHooks.LifecycleBatch deactivationBatch = hooks.onIslandDeactivated(islandId, activeIsland, Path.of("1234-bundle.tar.zst"));
        deactivationBatch.throwIfFailed();

        assertEquals(6, deactivationBatch.results().size());
        assertTrue(deactivationBatch.results().stream().allMatch(result -> result.status() == IntegrationResult.Status.SUCCESS));
        assertEquals("deactivate", deactivationBatch.operation());
        assertEquals("island-deactivate:" + islandId + ":0", deactivationBatch.context().idempotencyKey());
        assertEquals("1234-bundle.tar.zst", deactivationBatch.context().metadata().get("bundleKey"));
    }

    @Test
    void lifecycleBatchWritesAuditableJsonWhenPluginsRan() throws IOException {
        UUID islandId = UUID.randomUUID();
        IntegrationLifecycleHooks hooks = IntegrationLifecycleHooks.direct("island-node-01", List.of(new CoreProtectIntegration()));
        ActiveIslandRegistry.ActiveIsland activeIsland = new ActiveIslandRegistry.ActiveIsland(
            islandId,
            "ci_shard_001",
            1,
            2,
            10,
            20,
            100,
            12L,
            99L,
            Instant.now()
        );

        IntegrationLifecycleHooks.LifecycleBatch batch = hooks.exportState(islandId, activeIsland, 777L, Path.of("777-bundle.tar.zst"));
        Path output = tempDir.resolve("integrations/export.json");
        batch.writeIfPresent(output);

        assertTrue(Files.isRegularFile(output));
        java.util.Map<?, ?> root = SimpleJson.object(SimpleJson.parse(Files.readString(output)));
        assertEquals("export", SimpleJson.text(root.get("operation")));
        assertEquals(islandId.toString(), SimpleJson.text(root.get("islandId")));
        assertFalse(SimpleJson.list(root.get("results")).isEmpty());
        java.util.List<?> manifests = SimpleJson.list(root.get("stateManifests"));
        assertFalse(manifests.isEmpty());
        java.util.Map<?, ?> manifest = SimpleJson.object(manifests.getFirst());
        assertEquals("CoreProtect", SimpleJson.text(manifest.get("plugin")));
        assertEquals("audit-export", SimpleJson.text(manifest.get("operation")));
        assertEquals("integrations/audit-rollback/CoreProtect/audit-export.json", SimpleJson.text(manifest.get("bundleRelativePath")));
        assertEquals("ci_shard_001:1,2", SimpleJson.text(manifest.get("runtimeScope")));
    }

    @Test
    void lifecycleBatchWritesCustomItemAndStackerStateArtifactsIntoBundleRoot() throws IOException {
        UUID islandId = UUID.randomUUID();
        IntegrationLifecycleHooks hooks = IntegrationLifecycleHooks.direct("island-node-01", List.of(
            new CustomItemIntegration("ItemsAdder"),
            new StackerIntegration("RoseStacker")
        ));
        ActiveIslandRegistry.ActiveIsland activeIsland = new ActiveIslandRegistry.ActiveIsland(
            islandId,
            "ci_shard_001",
            1,
            2,
            10,
            20,
            100,
            12L,
            99L,
            Instant.now()
        );

        IntegrationLifecycleHooks.LifecycleBatch batch = hooks.exportState(islandId, activeIsland, 777L, Path.of("777-bundle.tar.zst"));
        batch.writeIfPresent(tempDir.resolve("integrations/export.json"));

        Path customItemState = tempDir.resolve("integrations/custom-items/ItemsAdder/custom-item-export.json");
        Path stackerState = tempDir.resolve("integrations/stacker/RoseStacker/effective-stack-export.json");
        assertTrue(Files.isRegularFile(customItemState));
        assertTrue(Files.isRegularFile(stackerState));

        java.util.Map<?, ?> customRoot = SimpleJson.object(SimpleJson.parse(Files.readString(customItemState)));
        java.util.Map<?, ?> customManifest = SimpleJson.object(customRoot.get("stateManifest"));
        java.util.Map<?, ?> customDetails = SimpleJson.object(customRoot.get("details"));
        assertEquals("ItemsAdder", SimpleJson.text(customManifest.get("plugin")));
        assertEquals("custom-item-export", SimpleJson.text(customManifest.get("operation")));
        assertEquals("custom-block-id-index,custom-block-state,core-block-value-mapping", SimpleJson.text(customDetails.get("external.artifacts")));

        java.util.Map<?, ?> stackerRoot = SimpleJson.object(SimpleJson.parse(Files.readString(stackerState)));
        java.util.Map<?, ?> stackerManifest = SimpleJson.object(stackerRoot.get("stateManifest"));
        java.util.Map<?, ?> stackerDetails = SimpleJson.object(stackerRoot.get("details"));
        assertEquals("RoseStacker", SimpleJson.text(stackerManifest.get("plugin")));
        assertEquals("effective-stack-export", SimpleJson.text(stackerManifest.get("operation")));
        assertEquals("stacked-entity-state,stacked-spawner-state,effective-limit-keys", SimpleJson.text(stackerDetails.get("external.artifacts")));
    }

    @Test
    void worldEditLifecycleHooksRunForIslandActivationExportRestoreAndDeactivation() throws IOException {
        UUID islandId = UUID.randomUUID();
        List<String> operations = new ArrayList<>();
        IntegrationLifecycleHooks hooks = IntegrationLifecycleHooks.direct("island-node-01", List.of(
            new WorldEditIntegration("WorldEdit", (pluginName, category, operation, context, plan) -> {
                operations.add(pluginName + ":" + operation);
                return IntegrationResult.success("worldedit called");
            })
        ));
        ActiveIslandRegistry.ActiveIsland activeIsland = new ActiveIslandRegistry.ActiveIsland(
            islandId,
            "ci_shard_001",
            1,
            2,
            10,
            20,
            100,
            12L,
            99L,
            Instant.now()
        );
        IslandBundleManifest manifest = manifest(islandId, 100);

        hooks.onIslandActivated(islandId, activeIsland).throwIfFailed();
        IntegrationLifecycleHooks.LifecycleBatch exportBatch = hooks.exportState(islandId, activeIsland, 777L, Path.of("777-bundle.tar.zst"));
        exportBatch.throwIfFailed();
        exportBatch.writeIfPresent(tempDir.resolve("integrations/export.json"));
        hooks.restoreState(islandId, "ci_shard_001", 1, 2, 10, 20, 99L, 777L, "snapshots/island.tar.zst", Path.of("bundle.tar.zst"), tempDir.resolve("extracted"), manifest).throwIfFailed();
        hooks.onIslandDeactivated(islandId, activeIsland, Path.of("777-bundle.tar.zst")).throwIfFailed();

        assertEquals(List.of(
            "WorldEdit:clipboard-activate",
            "WorldEdit:schematic-export",
            "WorldEdit:schematic-restore",
            "WorldEdit:edit-session-deactivate"
        ), operations);
        assertTrue(Files.isRegularFile(tempDir.resolve("integrations/world-edit/WorldEdit/schematic-export.json")));
    }

    @Test
    void registryBackedHooksOnlyInvokeActiveAdapters() throws IOException {
        String source = Files.readString(Path.of("src/main/java/kr/lunaf/cloudislands/paper/integration/IntegrationLifecycleHooks.java"));

        assertTrue(source.contains("status.adapterState() == IntegrationSupportState.ACTIVE"));
        assertFalse(source.contains("filter(PaperIntegrationRegistry.IntegrationStatus::enabled)"));
    }

    private IslandBundleManifest manifest(UUID islandId, int size) {
        Instant now = Instant.now();
        return new IslandBundleManifest(
            islandId,
            UUID.randomUUID(),
            3,
            "1.21.11",
            12,
            size,
            new IslandLocation("ci_shard_001", 0.5D, 100.0D, 0.5D, 180.0F, 0.0F),
            now,
            now,
            ""
        );
    }
}
