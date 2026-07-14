package kr.lunaf.cloudislands.paper.activation;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertTrue;

import java.nio.file.Files;
import java.nio.file.Path;
import java.time.Instant;
import java.util.UUID;
import org.junit.jupiter.api.Test;

class IslandDeactivationCellUnloadPolicyTest {
    @Test
    void liveCellIsSavedThenUnloadedBeforeOwnershipIsReleased() throws Exception {
        String source = Files.readString(Path.of("src/main/java/kr/lunaf/cloudislands/paper/activation/IslandDeactivationHandler.java"));

        int save = source.indexOf("saveService.save(islandId, active");
        int unload = source.indexOf("cellUnloader.unload(IslandCellRange.from(active))");
        int unregister = source.indexOf("protectionController.unregisterIsland(islandId)");
        int release = source.indexOf("shardWorldManager.release(islandId)");
        assertTrue(save >= 0 && save < unload);
        assertTrue(unload < unregister && unregister < release);
        assertTrue(source.contains("activeIslands.beginTransition(islandId)"));
        assertTrue(source.contains("activeIslands.endTransition(islandId)"));
    }

    @Test
    void activeIslandRangeUsesTheSameInclusiveChunkGeometryAsBundleTransfer() {
        UUID islandId = UUID.randomUUID();
        ActiveIslandRegistry.ActiveIsland active = new ActiveIslandRegistry.ActiveIsland(
            islandId, "ci_shard_001", 2, 3, 2048, 3072, 300, 5L, 7L, Instant.EPOCH
        );

        IslandCellRange range = IslandCellRange.from(active);

        assertEquals(islandId, range.islandId());
        assertEquals(118, range.minChunkX());
        assertEquals(137, range.maxChunkX());
        assertEquals(182, range.minChunkZ());
        assertEquals(201, range.maxChunkZ());
    }
}
