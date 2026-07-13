package kr.lunaf.cloudislands.paper.api;

import org.junit.jupiter.api.Test;

import java.nio.file.Files;
import java.nio.file.Path;

import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertTrue;

class AddonStateClearFailurePolicyTest {
    @Test
    void fullStateClearsWaitForCoreAndPropagateFailures() throws Exception {
        String source = Files.readString(Path.of("src/main/java/kr/lunaf/cloudislands/paper/api/PaperCloudIslandsApi.java"));

        assertTrue(source.contains("mutateIdempotent(\"addon.state.clear\", () -> addonStateClient.clearState(safeId))\n                .thenRun(() -> clearLocalAddonState(safeId));"));
        assertTrue(source.contains("mutateIdempotent(\"addon.island-state.clear\", () -> addonStateClient.clearIslandState(safeId, islandId))\n                .thenRun(() -> clearLocalAddonIslandState(safeId, islandId));"));
        assertFalse(source.contains("addonStateClient.clearState(safeId)).exceptionally(_error -> null)"));
        assertFalse(source.contains("addonStateClient.clearIslandState(safeId, islandId)).exceptionally(_error -> null)"));
    }

    @Test
    void localCacheIsEvictedOnlyAfterPersistentFallbackDeletionSucceeds() throws Exception {
        String source = Files.readString(Path.of("src/main/java/kr/lunaf/cloudislands/paper/api/PaperCloudIslandsApi.java"));
        int globalDelete = source.indexOf("Files.deleteIfExists(addonStatePath(safeId));");
        int globalEvict = source.indexOf("addonStates.remove(safeId);", globalDelete);
        int islandDelete = source.indexOf("Files.deleteIfExists(addonIslandStatePath(safeId, islandId));");
        int islandEvict = source.indexOf("islandStates.remove(islandId);", islandDelete);

        assertTrue(globalDelete >= 0 && globalEvict > globalDelete);
        assertTrue(islandDelete >= 0 && islandEvict > islandDelete);
        assertTrue(source.contains("throw new IllegalStateException(\"Failed to clear local addon state"));
        assertTrue(source.contains("throw new IllegalStateException(\"Failed to clear local addon island state"));
    }
}
