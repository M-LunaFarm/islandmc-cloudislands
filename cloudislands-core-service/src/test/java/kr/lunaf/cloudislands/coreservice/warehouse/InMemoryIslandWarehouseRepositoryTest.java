package kr.lunaf.cloudislands.coreservice.warehouse;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertTrue;

import java.util.UUID;
import org.junit.jupiter.api.Test;

class InMemoryIslandWarehouseRepositoryTest {
    @Test
    void preventsNegativeWithdrawAndKeepsCoreBalance() {
        InMemoryIslandWarehouseRepository repository = new InMemoryIslandWarehouseRepository();
        UUID islandId = UUID.fromString("00000000-0000-0000-0000-000000000001");

        assertTrue(repository.deposit(islandId, "Stone", 64L).accepted());
        assertFalse(repository.withdraw(islandId, "minecraft:stone", 65L).accepted());
        var withdrawn = repository.withdraw(islandId, "minecraft:stone", 16L);

        assertTrue(withdrawn.accepted());
        assertEquals(48L, withdrawn.item().amount());
        assertEquals(1, repository.list(islandId, 10).size());
    }
}
