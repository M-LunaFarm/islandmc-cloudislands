package kr.lunaf.cloudislands.paper.level;

import static org.junit.jupiter.api.Assertions.assertTrue;

import java.nio.file.Files;
import java.nio.file.Path;
import org.junit.jupiter.api.Test;

class StackAmountLevelAccountingPolicyTest {
    @Test
    void logicalStackAmountsFeedReconciliationAndRuntimeCertification() throws Exception {
        String scans = Files.readString(Path.of("src/main/java/kr/lunaf/cloudislands/paper/level/IslandLevelScanService.java"));
        String runtime = Files.readString(Path.of("src/main/java/kr/lunaf/cloudislands/paper/bootstrap/PaperRuntimeServices.java"));
        String stacker = Files.readString(Path.of("src/main/java/kr/lunaf/cloudislands/paper/integration/stacker/StackAmountService.java"));
        String deltas = Files.readString(Path.of("src/main/java/kr/lunaf/cloudislands/paper/limit/LogicalStackDeltaBridge.java"));
        String entitySpawns = Files.readString(Path.of("src/main/java/kr/lunaf/cloudislands/paper/limit/LogicalEntitySpawnBridge.java"));
        String bootstrap = Files.readString(Path.of("src/main/java/kr/lunaf/cloudislands/paper/PaperPluginBootstrap.java"));

        assertTrue(scans.contains("stackAmounts.snapshot"));
        assertTrue(scans.contains("stackSnapshot.blockAmount(block)"));
        assertTrue(scans.contains("stackSnapshot.entityAmount(entity)"));
        assertTrue(scans.contains("counts.merge(IslandEntityLimitKeys.COUNT_KEY, amount, Long::sum)"));
        assertTrue(scans.contains("stackSnapshot.blockKeyOverride(block)"));
        assertTrue(runtime.contains("stack-amount-registration"));
        assertTrue(runtime.contains("unregisterStackAmounts"));
        assertTrue(stacker.contains("getStackedBlocks"));
        assertTrue(stacker.contains("getStackedBarrels"));
        assertTrue(stacker.contains("getSpawnerAmount"));
        assertTrue(stacker.contains("getSpawnersAmount"));
        assertTrue(stacker.contains("getEntityAmount"));
        assertTrue(deltas.contains("BlockStackEvent"));
        assertTrue(deltas.contains("BlockUnstackEvent"));
        assertTrue(deltas.contains("SpawnerUnstackEvent"));
        assertTrue(deltas.contains("BarrelPlaceInventoryEvent"));
        assertTrue(deltas.contains("BarrelUnstackEvent"));
        assertTrue(bootstrap.contains("LogicalStackDeltaBridge.register"));
        assertTrue(entitySpawns.contains("PreStackedSpawnerSpawnEvent"));
        assertTrue(entitySpawns.contains("PostStackedSpawnerSpawnEvent"));
        assertTrue(entitySpawns.contains("directLogicalDelta"));
        assertTrue(bootstrap.contains("LogicalEntitySpawnBridge.register"));
    }
}
