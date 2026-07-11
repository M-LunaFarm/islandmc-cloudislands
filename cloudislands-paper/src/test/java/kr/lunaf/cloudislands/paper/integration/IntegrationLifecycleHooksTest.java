package kr.lunaf.cloudislands.paper.integration;

import static org.junit.jupiter.api.Assertions.assertDoesNotThrow;
import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertThrows;
import static org.junit.jupiter.api.Assertions.assertTrue;

import java.io.IOException;
import java.nio.file.Files;
import java.nio.file.Path;
import java.time.Instant;
import java.util.ArrayList;
import java.util.List;
import java.util.Map;
import java.util.Set;
import java.util.UUID;
import kr.lunaf.cloudislands.api.model.IslandLocation;
import kr.lunaf.cloudislands.paper.activation.ActiveIslandRegistry;
import kr.lunaf.cloudislands.paper.integration.analytics.PlanIntegration;
import kr.lunaf.cloudislands.paper.integration.coreprotect.CoreProtectIntegration;
import kr.lunaf.cloudislands.paper.integration.customitem.CustomItemIntegration;
import kr.lunaf.cloudislands.paper.integration.economy.VaultIntegration;
import kr.lunaf.cloudislands.paper.integration.permission.LuckPermsIntegration;
import kr.lunaf.cloudislands.paper.integration.placeholder.PlaceholderApiIntegration;
import kr.lunaf.cloudislands.paper.integration.spi.IntegrationCapability;
import kr.lunaf.cloudislands.paper.integration.spi.IntegrationContext;
import kr.lunaf.cloudislands.paper.integration.spi.IntegrationExternalRuntime;
import kr.lunaf.cloudislands.paper.integration.spi.IntegrationResult;
import kr.lunaf.cloudislands.paper.integration.spi.PolicyBackedCloudIntegration;
import kr.lunaf.cloudislands.paper.integration.stacker.StackerIntegration;
import kr.lunaf.cloudislands.paper.integration.worldedit.WorldEditIntegration;
import kr.lunaf.cloudislands.storage.IslandBundleManifest;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.io.TempDir;

class IntegrationLifecycleHooksTest {
    @TempDir
    Path tempDir;

    @Test
    void probeOnlyAdaptersNeverParticipateInIslandLifecycle() throws IOException {
        List<String> calls = new ArrayList<>();
        IntegrationExternalRuntime runtime = (plugin, _category, operation, _context, _plan) -> {
            calls.add(plugin + ":" + operation);
            return IntegrationResult.success("unexpected", Map.of("roundTripVerified", "true"));
        };
        IntegrationLifecycleHooks hooks = IntegrationLifecycleHooks.direct("node-01", List.of(
            new VaultIntegration(runtime),
            new PlaceholderApiIntegration(runtime),
            new LuckPermsIntegration(runtime),
            new PlanIntegration(runtime),
            new CustomItemIntegration("ItemsAdder", runtime),
            new StackerIntegration("RoseStacker", runtime),
            new CoreProtectIntegration(runtime),
            new WorldEditIntegration("WorldEdit", runtime)
        ));
        UUID islandId = UUID.randomUUID();
        ActiveIslandRegistry.ActiveIsland active = activeIsland(islandId);

        IntegrationLifecycleHooks.LifecycleBatch activated = hooks.onIslandActivated(islandId, active);
        IntegrationLifecycleHooks.LifecycleBatch exported = hooks.exportState(islandId, active, 44L, Path.of("44-bundle.tar.zst"));
        IntegrationLifecycleHooks.LifecycleBatch restored = hooks.restoreState(
            islandId, "ci_shard_001", 1, 2, 10, 20, 99L, 44L,
            "snapshots/island.tar.zst", Path.of("bundle.tar.zst"), tempDir.resolve("extracted"), manifest(islandId)
        );
        IntegrationLifecycleHooks.LifecycleBatch deactivated = hooks.onIslandDeactivated(islandId, active, Path.of("44-bundle.tar.zst"));

        assertTrue(calls.isEmpty());
        assertTrue(activated.results().isEmpty());
        assertTrue(exported.results().isEmpty());
        assertTrue(restored.results().isEmpty());
        assertTrue(deactivated.results().isEmpty());
        assertDoesNotThrow(activated::throwIfFailed);
        assertDoesNotThrow(exported::throwIfFailed);
        assertDoesNotThrow(restored::throwIfFailed);
        assertDoesNotThrow(deactivated::throwIfFailed);
    }

    @Test
    void lifecycleContextCarriesPortableBundleCoordinatesToExecutableAdapters() throws IOException {
        IntegrationLifecycleHooks hooks = IntegrationLifecycleHooks.direct("node-01", List.of(
            new SyntheticStateIntegration((_plugin, _category, _operation, _context, _plan) ->
                IntegrationResult.success("executed", Map.of("stateArtifact", "synthetic-state")))
        ));
        UUID islandId = UUID.randomUUID();

        IntegrationLifecycleHooks.LifecycleBatch batch = hooks.exportState(
            islandId,
            activeIsland(islandId),
            55L,
            Path.of("55-bundle.tar.zst")
        );

        batch.throwIfFailed();
        assertEquals(1, batch.results().size());
        assertEquals("1,2", batch.context().metadata().get("cell"));
        assertEquals("10,0,20..109,319,119", batch.context().metadata().get("region"));
        assertEquals("55-bundle.tar.zst", batch.context().metadata().get("bundleKey"));
        Path summary = tempDir.resolve("integrations/export.json");
        batch.writeIfPresent(summary);
        assertTrue(Files.isRegularFile(summary));
        assertTrue(Files.readString(summary).contains("synthetic-state"));
    }

    @Test
    void skippedStateChangingHookFailsClosed() {
        UUID islandId = UUID.randomUUID();
        IntegrationLifecycleHooks hooks = IntegrationLifecycleHooks.direct("node-01", List.of(
            new SyntheticStateIntegration((_plugin, _category, _operation, _context, _plan) ->
                IntegrationResult.skipped("executor unavailable"))
        ));

        IntegrationLifecycleHooks.LifecycleBatch batch = hooks.exportState(
            islandId, activeIsland(islandId), 66L, Path.of("66-bundle.tar.zst")
        );

        IOException exception = assertThrows(IOException.class, batch::throwIfFailed);
        assertTrue(exception.getMessage().contains("ItemsAdder [SKIPPED]"));
        assertEquals("true", batch.results().getFirst().details().get("plan.stateChanging"));
    }

    @Test
    void skippedObservationHookRemainsBestEffort() {
        UUID islandId = UUID.randomUUID();
        IntegrationLifecycleHooks hooks = IntegrationLifecycleHooks.direct("node-01", List.of(
            new SyntheticObservationIntegration((_plugin, _category, _operation, _context, _plan) ->
                IntegrationResult.skipped("analytics unavailable"))
        ));

        IntegrationLifecycleHooks.LifecycleBatch batch = hooks.exportState(
            islandId, activeIsland(islandId), 77L, Path.of("77-bundle.tar.zst")
        );

        assertDoesNotThrow(batch::throwIfFailed);
        assertEquals("false", batch.results().getFirst().details().get("plan.stateChanging"));
    }

    @Test
    void emptyBatchesDoNotWriteMisleadingIntegrationArtifacts() throws IOException {
        Path summary = tempDir.resolve("integrations/export.json");
        IntegrationLifecycleHooks.LifecycleBatch.empty("export").writeIfPresent(summary);
        assertFalse(Files.exists(summary));
    }

    private ActiveIslandRegistry.ActiveIsland activeIsland(UUID islandId) {
        return new ActiveIslandRegistry.ActiveIsland(
            islandId, "ci_shard_001", 1, 2, 10, 20, 100, 12L, 99L, Instant.now()
        );
    }

    private IslandBundleManifest manifest(UUID islandId) {
        Instant now = Instant.now();
        return new IslandBundleManifest(
            islandId,
            UUID.randomUUID(),
            3,
            "1.21.11",
            12,
            100,
            new IslandLocation("ci_shard_001", 0.5D, 100.0D, 0.5D, 180.0F, 0.0F),
            now,
            now,
            ""
        );
    }

    private static final class SyntheticStateIntegration extends PolicyBackedCloudIntegration {
        private SyntheticStateIntegration(IntegrationExternalRuntime runtime) {
            super("ItemsAdder", Set.of(IntegrationCapability.STATE_EXPORT), runtime);
        }

        @Override
        public IntegrationResult exportState(IntegrationContext context) {
            return guardedStateHook("synthetic-export", context, "world", "cell", "bundleKey");
        }

        @Override
        protected String externalApiCall(String operation) {
            return "SyntheticApi#export";
        }

        @Override
        protected String externalStateArtifacts(String operation) {
            return "synthetic-state";
        }
    }

    private static final class SyntheticObservationIntegration extends PolicyBackedCloudIntegration {
        private SyntheticObservationIntegration(IntegrationExternalRuntime runtime) {
            super("Plan", Set.of(IntegrationCapability.STATE_EXPORT), runtime);
        }

        @Override
        public IntegrationResult exportState(IntegrationContext context) {
            return guardedObservationHook("synthetic-observation", context, "bundleKey");
        }
    }
}
