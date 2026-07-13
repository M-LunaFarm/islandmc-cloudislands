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
    }
}
