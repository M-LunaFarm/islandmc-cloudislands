package kr.lunaf.cloudislands.paper.platform.world;

import static org.junit.jupiter.api.Assertions.assertTrue;

import java.nio.file.Files;
import java.nio.file.Path;
import org.junit.jupiter.api.Test;

class BukkitStarterIslandGeneratorPolicyTest {
    @Test
    void starterGenerationUsesAsyncChunkPreparationAndSchedulerBoundBlockWrites() throws Exception {
        String source = Files.readString(Path.of("src/main/java/kr/lunaf/cloudislands/paper/platform/world/BukkitStarterIslandGenerator.java"));

        assertTrue(source.contains("world.getChunkAtAsync(chunkX, chunkZ, true)"));
        assertTrue(source.contains("scheduler.runGlobal(() -> completeBuild"));
        assertTrue(source.contains("public void prepareShutdown()"));
        assertTrue(source.contains("world.getChunkAt(chunkX, chunkZ)"), "shutdown must finish a pending chunk preparation without waiting on the blocked global scheduler");
        assertTrue(source.contains("pendingGenerations.remove(pending.id(), pending)"), "normal and shutdown paths must claim each generation exactly once");
        assertTrue(source.contains("Material.GRASS_BLOCK"));
        assertTrue(source.contains("Material.DIRT"));
        assertTrue(source.contains("Material.BEDROCK"));
        assertTrue(source.contains("Material.CHEST"));
        assertTrue(source.contains("Material.LAVA_BUCKET"));
        assertTrue(source.contains("Material.ICE, 2"));
        assertTrue(source.contains("Material.OAK_SAPLING, 2"));
        assertTrue(source.contains("Material.WHEAT_SEEDS, 4"));
        assertTrue(source.contains("chest.update(true, false)"));
        assertTrue(source.contains("completion.get(timeout.toMillis()"));
    }
}
