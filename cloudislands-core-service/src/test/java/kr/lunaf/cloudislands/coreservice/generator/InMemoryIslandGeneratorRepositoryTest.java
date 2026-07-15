package kr.lunaf.cloudislands.coreservice.generator;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertTrue;

import java.util.UUID;
import java.util.concurrent.Executors;
import java.util.concurrent.TimeUnit;
import org.junit.jupiter.api.Test;

class InMemoryIslandGeneratorRepositoryTest {
    @Test
    void generatorLevelAddsAreAtomicAndSaturating() throws Exception {
        UUID islandId = UUID.fromString("00000000-0000-0000-0000-000000000091");
        InMemoryIslandGeneratorRepository repository = new InMemoryIslandGeneratorRepository();

        assertEquals(2, repository.addProfile(islandId, "ore", 1).level());
        try (var workers = Executors.newFixedThreadPool(8)) {
            for (int index = 0; index < 1_000; index++) {
                workers.submit(() -> repository.addProfile(islandId, "ore", 1));
            }
            workers.shutdown();
            assertTrue(workers.awaitTermination(10, TimeUnit.SECONDS));
        }

        assertEquals(1_002, repository.profile(islandId).level());
        assertEquals(Integer.MAX_VALUE, repository.addProfile(islandId, "elite", Integer.MAX_VALUE).level());
        assertEquals(Integer.MAX_VALUE, repository.addProfile(islandId, "elite", 1).level());
        assertEquals("elite", repository.profile(islandId).generatorKey());
        assertEquals(1, repository.addProfile(islandId, "default", Integer.MIN_VALUE).level());
    }

    @Test
    void monotonicProfileWritesRejectLateLowerUpgradeEffects() throws Exception {
        UUID islandId = UUID.fromString("00000000-0000-0000-0000-000000000092");
        InMemoryIslandGeneratorRepository repository = new InMemoryIslandGeneratorRepository();

        try (var workers = Executors.newFixedThreadPool(8)) {
            for (int level = 1; level <= 1_000; level++) {
                int requestedLevel = level;
                workers.submit(() -> repository.setProfileAtLeast(islandId, "level-" + requestedLevel, requestedLevel));
            }
            workers.shutdown();
            assertTrue(workers.awaitTermination(10, TimeUnit.SECONDS));
        }

        assertEquals(1_000, repository.profile(islandId).level());
        assertEquals("level-1000", repository.profile(islandId).generatorKey());
        assertEquals(1_000, repository.setProfileAtLeast(islandId, "late-level-5", 5).level());
        assertEquals("level-1000", repository.profile(islandId).generatorKey());
        assertEquals("recalculated-level-1000", repository.setProfileAtLeast(islandId, "recalculated-level-1000", 1_000).generatorKey());
    }
}
