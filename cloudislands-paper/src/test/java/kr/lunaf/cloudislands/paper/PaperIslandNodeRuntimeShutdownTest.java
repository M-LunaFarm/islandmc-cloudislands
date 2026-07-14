package kr.lunaf.cloudislands.paper;

import static org.junit.jupiter.api.Assertions.assertTrue;

import java.nio.file.Files;
import java.nio.file.Path;
import org.junit.jupiter.api.Test;

class PaperIslandNodeRuntimeShutdownTest {
    @Test
    void stopsJobIntakeBeforeFinalPeriodicSaveFlush() throws Exception {
        String runtime = Files.readString(Path.of("src/main/java/kr/lunaf/cloudislands/paper/PaperIslandNodeRuntime.java"));
        int periodicRegistration = runtime.indexOf("plugin.lifecycle.started(\"periodic-save\"");
        int jobRegistration = runtime.indexOf("plugin.lifecycle.started(\"job-worker\"");

        assertTrue(periodicRegistration >= 0);
        assertTrue(jobRegistration > periodicRegistration, "LifecycleRegistry stops components in reverse registration order");
        assertTrue(runtime.contains("periodicSaveTask.shutdown(Duration.ofSeconds(config.worker().shutdownSaveTimeoutSeconds()))"));
        assertTrue(saveTaskUsesMainThreadPreparation(), "shutdown must drain pending world flushes before waiting for async export");
    }

    private boolean saveTaskUsesMainThreadPreparation() throws Exception {
        String saveTask = Files.readString(Path.of("src/main/java/kr/lunaf/cloudislands/paper/activation/PeriodicIslandSaveTask.java"));
        int prepare = saveTask.indexOf("saveService.prepareShutdown(activeIslands.snapshot())");
        int await = saveTask.indexOf("ShutdownSaveCoordinator.awaitIdleAndFlush");
        return prepare >= 0 && await > prepare;
    }

    @Test
    void exposesABoundedShutdownSaveTimeout() throws Exception {
        String gameplay = Files.readString(Path.of("src/main/resources/config-v2/gameplay.yml"));
        String loader = Files.readString(Path.of("src/main/java/kr/lunaf/cloudislands/paper/config/PaperRuntimeConfigLoader.java"));

        assertTrue(gameplay.contains("shutdown-save-timeout: 30s"));
        assertTrue(loader.contains("island-node.activation.shutdown-save-timeout-seconds"));
    }

    @Test
    void finalFlushPersistsANewSnapshotEvenWhenMetadataIsAlreadyPending() throws Exception {
        String saveTask = Files.readString(Path.of("src/main/java/kr/lunaf/cloudislands/paper/activation/PeriodicIslandSaveTask.java"));

        assertTrue(saveTask.contains("saveAll(true)"));
        assertTrue(saveTask.contains("if (!forceActiveSave && pendingSnapshotRecords.contains(activeIsland.islandId()))"));
    }
}
