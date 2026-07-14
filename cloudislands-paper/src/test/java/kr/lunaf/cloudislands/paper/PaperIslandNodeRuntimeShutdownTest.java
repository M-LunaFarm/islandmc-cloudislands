package kr.lunaf.cloudislands.paper;

import static org.junit.jupiter.api.Assertions.assertTrue;

import java.nio.file.Files;
import java.nio.file.Path;
import org.junit.jupiter.api.Test;

class PaperIslandNodeRuntimeShutdownTest {
    @Test
    void stopsJobIntakeBeforeFinalPeriodicSaveFlush() throws Exception {
        String runtime = Files.readString(Path.of("src/main/java/kr/lunaf/cloudislands/paper/PaperIslandNodeRuntime.java"));
        int shutdownRegistration = runtime.indexOf("plugin.lifecycle.started(\"island-worker-shutdown\"");
        int stopPeriodicIntake = runtime.indexOf("plugin.periodicSaveTask.stop()", shutdownRegistration);
        int drainJobs = runtime.indexOf("plugin.jobWorker.shutdown(timeout)", shutdownRegistration);
        int finalSave = runtime.indexOf("plugin.periodicSaveTask.shutdown(timeout)", shutdownRegistration);
        int noFinalSave = runtime.indexOf("plugin.periodicSaveTask.shutdownWithoutFinalSave(timeout)", shutdownRegistration);

        assertTrue(shutdownRegistration >= 0);
        assertTrue(stopPeriodicIntake > shutdownRegistration && drainJobs > stopPeriodicIntake, "shutdown must stop periodic intake before draining claimed jobs");
        assertTrue(finalSave > drainJobs, "the forced final save must start only after claimed jobs drain");
        assertTrue(noFinalSave > finalSave, "a worker drain failure must quiesce existing saves without starting a competing final snapshot");
        assertTrue(runtime.contains("activationHandler::prepareShutdown"), "the worker drain must service Paper global-thread work while onDisable waits");
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

    @Test
    void workerDrainFailureDoesNotStartACompetingFinalSnapshot() throws Exception {
        String saveTask = Files.readString(Path.of("src/main/java/kr/lunaf/cloudislands/paper/activation/PeriodicIslandSaveTask.java"));
        int noFinalStart = saveTask.indexOf("public boolean shutdownWithoutFinalSave");
        int noFinalEnd = saveTask.indexOf("private void saveAll()", noFinalStart);
        String noFinalPath = saveTask.substring(noFinalStart, noFinalEnd);

        assertTrue(noFinalPath.contains("saveService.prepareShutdown(activeIslands.snapshot())"));
        assertTrue(noFinalPath.contains("ShutdownSaveCoordinator.awaitIdleAndFlush"));
        assertTrue(noFinalPath.contains("() -> {}"));
        assertTrue(!noFinalPath.contains("saveAll(true)"), "the failure path must not begin a snapshot while claimed work may still mutate the island");
    }
}
