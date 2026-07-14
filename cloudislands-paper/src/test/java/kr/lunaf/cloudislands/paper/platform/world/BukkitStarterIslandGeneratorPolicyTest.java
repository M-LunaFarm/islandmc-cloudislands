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
        assertTrue(source.contains("scheduler.runGlobal(() -> build"));
        assertTrue(source.contains("Material.GRASS_BLOCK"));
        assertTrue(source.contains("Material.DIRT"));
        assertTrue(source.contains("Material.BEDROCK"));
        assertTrue(source.contains("completion.get(timeout.toMillis()"));
    }
}
