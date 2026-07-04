package kr.lunaf.cloudislands.coreservice.limit;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertTrue;

import java.util.UUID;
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

        var amount = repository.set(islandId, GameplayParityPolicy.blockAmountLimitKey("minecraft:diamond block"), 128L, actorUuid);
        var hidden = repository.set(islandId, GameplayParityPolicy.STACKED_BLOCKS_VISIBLE_LIMIT_KEY, 0L, actorUuid);

        assertEquals("BLOCK_AMOUNT:MINECRAFT:DIAMOND_BLOCK", amount.limitKey());
        assertEquals(128L, amount.value());
        assertEquals(0L, hidden.value());
        assertTrue(repository.list(islandId).stream()
            .anyMatch(limit -> limit.limitKey().equals("BLOCK_AMOUNT:MINECRAFT:DIAMOND_BLOCK") && limit.value() == 128L));
    }
}
