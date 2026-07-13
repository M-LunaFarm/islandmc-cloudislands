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

        assertTrue(deltas.contains("customBlockKeys.blockKey(block)"));
        assertTrue(scans.contains("customBlockKeys.blockKey(block)"));
        assertTrue(scans.contains("customBlockKeys.entityKey(entity)"));
        assertTrue(runtime.contains("custom-block-key-registration"));
        assertTrue(runtime.contains("clearRuntimeService(pluginName)"));
    }
}
