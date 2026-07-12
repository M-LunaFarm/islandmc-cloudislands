package kr.lunaf.cloudislands.coreservice.limit;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertTrue;

import java.util.UUID;
import java.util.concurrent.Executors;
import java.util.concurrent.TimeUnit;
import kr.lunaf.cloudislands.common.feature.GameplayParityPolicy;
import org.junit.jupiter.api.Test;

class InMemoryIslandLimitRepositoryTest {
    @Test
    void persistsStackedBlockAmountsAndVisibilityWithSharedKeys() {
        UUID islandId = UUID.fromString("00000000-0000-0000-0000-000000000071");
        UUID actorUuid = UUID.fromString("00000000-0000-0000-0000-000000000072");
        InMemoryIslandLimitRepository repository = new InMemoryIslandLimitRepository();

        assertTrue(repository.list(islandId).stream()
            .anyMatch(limit -> limit.limitKey().equals(GameplayParityPolicy.STACKED_BLOCKS_VISIBLE_LIMIT_KEY) && limit.value() == 1L));
        assertTrue(repository.list(islandId).stream()
            .anyMatch(limit -> limit.limitKey().equals(GameplayParityPolicy.WAREHOUSE_ROWS_LIMIT_KEY) && limit.value() == 6L));

        var amount = repository.set(islandId, GameplayParityPolicy.blockAmountLimitKey("minecraft:diamond block"), 128L, actorUuid);
        var hidden = repository.set(islandId, GameplayParityPolicy.STACKED_BLOCKS_VISIBLE_LIMIT_KEY, 0L, actorUuid);

        assertEquals("BLOCK_AMOUNT:MINECRAFT:DIAMOND_BLOCK", amount.limitKey());
        assertEquals(128L, amount.value());
        assertEquals(0L, hidden.value());
        assertTrue(repository.list(islandId).stream()
            .anyMatch(limit -> limit.limitKey().equals("BLOCK_AMOUNT:MINECRAFT:DIAMOND_BLOCK") && limit.value() == 128L));
    }

    @Test
    void addIsAtomicAndSaturatesAtNumericBoundaries() throws Exception {
        UUID islandId = UUID.fromString("00000000-0000-0000-0000-000000000073");
        UUID actorUuid = UUID.fromString("00000000-0000-0000-0000-000000000074");
        InMemoryIslandLimitRepository repository = new InMemoryIslandLimitRepository();
        assertEquals(51L, repository.add(UUID.randomUUID(), "HOPPER", 1L, actorUuid).value());
        repository.set(islandId, "HOPPER", 0L, actorUuid);

        try (var workers = Executors.newFixedThreadPool(8)) {
            for (int index = 0; index < 1_000; index++) {
                workers.submit(() -> repository.add(islandId, "HOPPER", 1L, actorUuid));
            }
            workers.shutdown();
            assertTrue(workers.awaitTermination(10, TimeUnit.SECONDS));
        }

        assertEquals(1_000L, repository.list(islandId).stream()
            .filter(limit -> limit.limitKey().equals("HOPPER"))
            .findFirst().orElseThrow().value());
        assertEquals(Long.MAX_VALUE, repository.add(islandId, "HOPPER", Long.MAX_VALUE, actorUuid).value());
        assertEquals(Long.MAX_VALUE, repository.add(islandId, "HOPPER", 1L, actorUuid).value());
        assertEquals(0L, repository.add(islandId, "HOPPER", Long.MIN_VALUE, actorUuid).value());
        assertEquals(0L, repository.add(islandId, "HOPPER", -1L, actorUuid).value());
    }
}
