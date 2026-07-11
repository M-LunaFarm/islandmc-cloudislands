package kr.lunaf.cloudislands.paper.command;

import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertTrue;

import java.util.UUID;
import org.junit.jupiter.api.Test;

class PendingWarehouseOperationsTest {
    @Test
    void serializesOperationsForTheSamePlayer() {
        PendingWarehouseOperations operations = new PendingWarehouseOperations();
        UUID playerUuid = UUID.randomUUID();

        assertTrue(operations.acquire(playerUuid));
        assertFalse(operations.acquire(playerUuid));

        operations.release(playerUuid);
        assertTrue(operations.acquire(playerUuid));
    }

    @Test
    void allowsDifferentPlayersToOperateConcurrently() {
        PendingWarehouseOperations operations = new PendingWarehouseOperations();

        assertTrue(operations.acquire(UUID.randomUUID()));
        assertTrue(operations.acquire(UUID.randomUUID()));
    }
}
