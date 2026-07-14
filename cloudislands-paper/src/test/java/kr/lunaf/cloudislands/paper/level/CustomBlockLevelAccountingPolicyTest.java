package kr.lunaf.cloudislands.paper.level;

import static org.junit.jupiter.api.Assertions.assertTrue;

import java.nio.file.Files;
import java.nio.file.Path;
import org.junit.jupiter.api.Test;

class CustomBlockLevelAccountingPolicyTest {
    @Test
    void bothIncrementalAndReconciliationPathsUseCustomBlockKeys() throws Exception {
        String deltas = Files.readString(Path.of("src/main/java/kr/lunaf/cloudislands/paper/level/BlockDeltaReporter.java"));
        String scans = Files.readString(Path.of("src/main/java/kr/lunaf/cloudislands/paper/level/IslandLevelScanService.java"));
        String runtime = Files.readString(Path.of("src/main/java/kr/lunaf/cloudislands/paper/bootstrap/PaperRuntimeServices.java"));
        String listener = Files.readString(Path.of("src/main/java/kr/lunaf/cloudislands/paper/IslandProtectionListener.java"));
        String furniture = Files.readString(Path.of("src/main/java/kr/lunaf/cloudislands/paper/integration/customitem/CraftEngineFurnitureBridge.java"));

        assertTrue(deltas.contains("customBlockKeys.blockKey(block)"));
        assertTrue(deltas.contains("customBlockKeys.entityKey(entity)"));
        assertTrue(deltas.contains("levelScanService.recordBlockDelta"));
        assertTrue(scans.contains("customBlockKeys.blockKey(block)"));
        assertTrue(scans.contains("customBlockKeys.entityKey(entity)"));
        assertTrue(runtime.contains("custom-block-key-registration"));
        assertTrue(runtime.contains("clearRuntimeService(pluginName)"));
        assertTrue(listener.contains("blockDeltas.entityPlaced(islandId, event.getEntity())"));
        assertTrue(listener.contains("blockDeltas.entityRemoved(islandId, event.getEntity())"));
        assertTrue(furniture.contains("FurniturePlaceEvent"));
        assertTrue(furniture.contains("FurnitureBreakEvent"));
        assertTrue(furniture.contains("IslandPermission.BUILD"));
        assertTrue(furniture.contains("IslandPermission.BREAK"));
        assertTrue(furniture.contains("blockDeltas.customEntityPlaced"));
        assertTrue(furniture.contains("blockDeltas.customEntityRemoved"));
    }
}
