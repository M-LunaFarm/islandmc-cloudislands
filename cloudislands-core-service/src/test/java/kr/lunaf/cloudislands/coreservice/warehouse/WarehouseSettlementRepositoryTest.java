package kr.lunaf.cloudislands.coreservice.warehouse;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertTrue;

import java.time.Instant;
import java.util.UUID;
import org.junit.jupiter.api.Test;

class WarehouseSettlementRepositoryTest {
    @Test
    void preparesEscrowsAndClearsOneSharedSettlementPerPlayer() {
        InMemoryWarehouseSettlementRepository repository = new InMemoryWarehouseSettlementRepository();
        WarehouseSettlementRecord settlement = settlement(UUID.randomUUID(), UUID.randomUUID());

        assertTrue(repository.prepare(settlement).accepted());
        assertEquals(WarehouseSettlementRecord.State.PREPARED, repository.find(settlement.playerUuid()).orElseThrow().state());
        assertTrue(repository.markEscrowed(settlement.playerUuid(), settlement.settlementId()).accepted());
        assertEquals(WarehouseSettlementRecord.State.ESCROWED, repository.find(settlement.playerUuid()).orElseThrow().state());
        assertTrue(repository.clear(settlement.playerUuid(), settlement.settlementId()));
        assertTrue(repository.find(settlement.playerUuid()).isEmpty());
    }

    @Test
    void sameOperationIsIdempotentButAnotherSettlementConflicts() {
        InMemoryWarehouseSettlementRepository repository = new InMemoryWarehouseSettlementRepository();
        UUID playerUuid = UUID.randomUUID();
        WarehouseSettlementRecord first = settlement(playerUuid, UUID.randomUUID());
        WarehouseSettlementRecord conflicting = settlement(playerUuid, UUID.randomUUID());

        assertTrue(repository.prepare(first).accepted());
        assertEquals("WAREHOUSE_SETTLEMENT_EXISTS", repository.prepare(first).code());
        assertFalse(repository.prepare(conflicting).accepted());
        assertEquals("WAREHOUSE_SETTLEMENT_CONFLICT", repository.prepare(conflicting).code());
        assertFalse(repository.clear(playerUuid, conflicting.settlementId()));
        assertEquals(first.settlementId(), repository.find(playerUuid).orElseThrow().settlementId());
    }

    private static WarehouseSettlementRecord settlement(UUID playerUuid, UUID settlementId) {
        return new WarehouseSettlementRecord(
            settlementId,
            playerUuid,
            UUID.randomUUID(),
            "STONE",
            64L,
            WarehouseSettlementRecord.Direction.DEPOSIT,
            WarehouseSettlementRecord.State.PREPARED,
            settlementId.toString(),
            "paper-a",
            Instant.parse("2026-07-14T00:00:00Z"),
            Instant.parse("2026-07-14T00:00:00Z")
        );
    }
}
