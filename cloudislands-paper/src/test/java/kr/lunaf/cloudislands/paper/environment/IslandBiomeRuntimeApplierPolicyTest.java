package kr.lunaf.cloudislands.paper.environment;

import static org.junit.jupiter.api.Assertions.assertTrue;

import java.nio.file.Files;
import java.nio.file.Path;
import org.junit.jupiter.api.Test;

class IslandBiomeRuntimeApplierPolicyTest {
    @Test
    void islandNodesApplyBiomeEventsInCancellableChunkBatches() throws Exception {
        String bootstrap = Files.readString(Path.of("src/main/java/kr/lunaf/cloudislands/paper/PaperPluginBootstrap.java"));
        String applier = Files.readString(Path.of("src/main/java/kr/lunaf/cloudislands/paper/platform/world/IslandBiomeRuntimeApplier.java"));
        String plan = Files.readString(Path.of("src/main/java/kr/lunaf/cloudislands/paper/environment/IslandBiomePaintPlan.java"));

        assertTrue(bootstrap.contains("AgentRole.ISLAND_NODE"));
        assertTrue(bootstrap.contains("new kr.lunaf.cloudislands.paper.platform.world.IslandBiomeRuntimeApplier(plugin, plugin.agent.protection(), client.environment())"));
        assertTrue(applier.contains("onBiomeChange(IslandBiomeChangeEvent event)"));
        assertTrue(applier.contains("onIslandActivate(IslandActivateEvent event)"));
        assertTrue(applier.contains("environmentQueries.biome(islandId).whenComplete"));
        assertTrue(applier.contains("PaperSchedulers.run(plugin, () -> begin(islandId, biome.biomeKey(), generation))"));
        assertTrue(applier.contains("onIslandDeactivate(IslandDeactivateEvent event)"));
        assertTrue(applier.contains("generationSequence.incrementAndGet()"));
        assertTrue(applier.contains("current(islandId, generation)"));
        assertTrue(applier.contains("getChunkAtAsync"));
        assertTrue(applier.contains("PaperSchedulers.runLater"));
        assertTrue(applier.contains("}, 1L)"));
        assertTrue(applier.contains("world.setBiome"));
        assertTrue(applier.contains("world.refreshChunk"));
        assertTrue(plan.contains("Math.floorDiv(region.minX(), 16)"));
        assertTrue(plan.contains("SAMPLE_STEP = 4"));
    }
}
