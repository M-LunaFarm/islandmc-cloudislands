package kr.lunaf.cloudislands.paper.platform.world;

import static org.junit.jupiter.api.Assertions.assertTrue;

import java.nio.file.Files;
import java.nio.file.Path;
import org.junit.jupiter.api.Test;

class BukkitShardWorldProvisionerPolicyTest {
    @Test
    void createsVoidShardWorldsOnTheGlobalThreadWithoutPinnedSpawnChunks() throws Exception {
        String source = Files.readString(Path.of("src/main/java/kr/lunaf/cloudislands/paper/platform/world/BukkitShardWorldProvisioner.java"));

        assertTrue(source.contains("scheduler.runGlobal"));
        assertTrue(source.contains(".keepSpawnLoaded(TriState.FALSE)"));
        assertTrue(source.contains(".generateStructures(false)"));
        assertTrue(source.contains("getFixedSpawnLocation(World world, Random random)"));
        assertTrue(source.contains(".generator(VOID_GENERATOR)"));
        assertTrue(source.contains("shouldGenerateNoise() { return false; }"));
    }
}
