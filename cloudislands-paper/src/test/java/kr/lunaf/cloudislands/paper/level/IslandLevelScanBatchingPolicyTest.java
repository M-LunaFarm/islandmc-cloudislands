package kr.lunaf.cloudislands.paper.level;

import static org.junit.jupiter.api.Assertions.assertTrue;

import java.nio.file.Files;
import java.nio.file.Path;
import org.junit.jupiter.api.Test;

class IslandLevelScanBatchingPolicyTest {
    @Test
    void fullScansAreTickBudgetedDeduplicatedAndLifecycleBound() throws Exception {
        String service = Files.readString(Path.of("src/main/java/kr/lunaf/cloudislands/paper/level/IslandLevelScanService.java"));
        String bootstrap = Files.readString(Path.of("src/main/java/kr/lunaf/cloudislands/paper/PaperPluginBootstrap.java"));
        String nodeRuntime = Files.readString(Path.of("src/main/java/kr/lunaf/cloudislands/paper/PaperIslandNodeRuntime.java"));
        String commands = Files.readString(Path.of("src/main/java/kr/lunaf/cloudislands/paper/command/PaperCommandRegistrar.java"));

        assertTrue(service.contains("MAX_BLOCKS_PER_TICK"));
        assertTrue(service.contains("MAX_TICK_NANOS"));
        assertTrue(service.contains("scheduler.repeatEveryTick(this)"));
        assertTrue(service.contains("inFlight.putIfAbsent"));
        assertTrue(service.contains("startingMutationVersion"));
        assertTrue(service.contains("enqueueWriteLocked"));
        assertTrue(service.contains("sameActivationStillActive"));
        assertTrue(service.contains("implements RuntimeComponent"));
        assertTrue(bootstrap.contains("lifecycle.started(\"level-scan-service\", plugin.levelScanService)"));
        assertTrue(nodeRuntime.contains("plugin.levelScanService"));
        assertTrue(commands.contains("plugin.levelScanService()"));
    }
}
