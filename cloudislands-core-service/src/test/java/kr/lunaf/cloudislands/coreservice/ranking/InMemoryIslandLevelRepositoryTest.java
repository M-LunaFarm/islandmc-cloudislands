package kr.lunaf.cloudislands.coreservice.ranking;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertTrue;

import java.util.UUID;
import java.util.concurrent.Executors;
import java.util.concurrent.TimeUnit;
import org.junit.jupiter.api.Test;

class InMemoryIslandLevelRepositoryTest {
    @Test
    void blockDeltasAreAtomicAndSaturateWithoutWrapping() throws Exception {
        UUID islandId = UUID.fromString("00000000-0000-0000-0000-000000000081");
        String material = "minecraft:diamond_block";
        InMemoryIslandLevelRepository repository = new InMemoryIslandLevelRepository();

        try (var workers = Executors.newFixedThreadPool(8)) {
            for (int index = 0; index < 1_000; index++) {
                workers.submit(() -> repository.addBlockDelta(islandId, material, 1L));
            }
            workers.shutdown();
            assertTrue(workers.awaitTermination(10, TimeUnit.SECONDS));
        }

        assertEquals(1_000L, repository.blockCounts(islandId).get(material));
        repository.addBlockDelta(islandId, material, Long.MAX_VALUE);
        assertEquals(Long.MAX_VALUE, repository.blockCounts(islandId).get(material));
        repository.addBlockDelta(islandId, material, 1L);
        assertEquals(Long.MAX_VALUE, repository.blockCounts(islandId).get(material));
        repository.addBlockDelta(islandId, material, Long.MIN_VALUE);
        assertEquals(0L, repository.blockCounts(islandId).get(material));
        repository.addBlockDelta(islandId, material, -1L);
        assertEquals(0L, repository.blockCounts(islandId).get(material));
    }
}
