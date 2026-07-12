package kr.lunaf.cloudislands.paper.command;

import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertTrue;

import java.util.UUID;
import org.junit.jupiter.api.Test;

class PendingIslandCreationOperationsTest {
    @Test
    void blocksDuplicateCreationUntilSettlementCompletes() {
        PendingIslandCreationOperations operations = new PendingIslandCreationOperations();
        UUID playerUuid = UUID.fromString("00000000-0000-0000-0000-000000000001");

        assertTrue(operations.acquire(playerUuid));
        assertFalse(operations.acquire(playerUuid));

        operations.release(playerUuid);
        assertTrue(operations.acquire(playerUuid));
    }
}
