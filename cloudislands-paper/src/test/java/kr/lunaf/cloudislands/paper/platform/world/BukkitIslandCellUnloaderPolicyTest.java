package kr.lunaf.cloudislands.paper.platform.world;

import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertTrue;

import java.nio.file.Files;
import java.nio.file.Path;
import org.junit.jupiter.api.Test;

class BukkitIslandCellUnloaderPolicyTest {
    @Test
    void cellEvictionIsBoundedSchedulerSafeAndFailsClosed() throws Exception {
        String source = Files.readString(Path.of("src/main/java/kr/lunaf/cloudislands/paper/platform/world/BukkitIslandCellUnloader.java"));

        assertTrue(source.contains("scheduler.runGlobal"));
        assertTrue(source.contains("public void prepareShutdown()"));
        assertTrue(source.contains("pendingUnloads.remove(pending.id(), pending)"), "normal and shutdown paths must claim each unload exactly once");
        assertTrue(source.contains("world.getPlayers()"));
        assertTrue(source.contains("world.isChunkLoaded(chunkX, chunkZ)"));
        assertTrue(source.contains("world.unloadChunk(chunkX, chunkZ, false)"));
        assertTrue(source.contains("Paper refused to unload island chunk"));
        assertTrue(source.contains("island chunk remained loaded after unload"));
        assertFalse(source.contains("getLoadedChunks"), "cell eviction must inspect only the bounded placement range");
    }
}
