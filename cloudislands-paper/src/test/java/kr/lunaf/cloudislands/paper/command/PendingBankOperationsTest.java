package kr.lunaf.cloudislands.paper.command;

import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertTrue;

import java.util.UUID;
import org.junit.jupiter.api.Test;

class PendingBankOperationsTest {
    @Test
    void permitsOnlyOneOperationPerPlayerUntilReleased() {
        PendingBankOperations operations = new PendingBankOperations();
        UUID playerUuid = UUID.randomUUID();

        assertTrue(operations.acquire(playerUuid));
        assertFalse(operations.acquire(playerUuid));

        operations.release(playerUuid);
        assertTrue(operations.acquire(playerUuid));
    }

    @Test
    void permitsDifferentPlayersAtTheSameTime() {
        PendingBankOperations operations = new PendingBankOperations();

        assertTrue(operations.acquire(UUID.randomUUID()));
        assertTrue(operations.acquire(UUID.randomUUID()));
    }
}
