package kr.lunaf.cloudislands.coreservice.warehouse;

import java.time.Instant;
import java.util.HashMap;
import java.util.Map;
import java.util.Optional;
import java.util.UUID;

public final class InMemoryWarehouseSettlementRepository implements WarehouseSettlementRepository {
    private final Map<UUID, WarehouseSettlementRecord> settlements = new HashMap<>();

    @Override
    public synchronized PrepareResult prepare(WarehouseSettlementRecord settlement) {
        WarehouseSettlementRecord current = settlements.get(settlement.playerUuid());
        if (current == null) {
            settlements.put(settlement.playerUuid(), settlement);
            return new PrepareResult(true, "WAREHOUSE_SETTLEMENT_PREPARED", settlement);
        }
        if (current.sameOperation(settlement)) {
            return new PrepareResult(true, "WAREHOUSE_SETTLEMENT_EXISTS", current);
        }
        return new PrepareResult(false, "WAREHOUSE_SETTLEMENT_CONFLICT", current);
    }

    @Override
    public synchronized TransitionResult markEscrowed(UUID playerUuid, UUID settlementId) {
        WarehouseSettlementRecord current = settlements.get(playerUuid);
        if (current == null || !current.settlementId().equals(settlementId)) {
            return new TransitionResult(false, "WAREHOUSE_SETTLEMENT_NOT_FOUND", current);
        }
        if (current.state() == WarehouseSettlementRecord.State.ESCROWED) {
            return new TransitionResult(true, "WAREHOUSE_SETTLEMENT_ESCROWED", current);
        }
        WarehouseSettlementRecord escrowed = current.escrowed(Instant.now());
        settlements.put(playerUuid, escrowed);
        return new TransitionResult(true, "WAREHOUSE_SETTLEMENT_ESCROWED", escrowed);
    }

    @Override
    public synchronized Optional<WarehouseSettlementRecord> find(UUID playerUuid) {
        return Optional.ofNullable(settlements.get(playerUuid));
    }

    @Override
    public synchronized boolean clear(UUID playerUuid, UUID settlementId) {
        WarehouseSettlementRecord current = settlements.get(playerUuid);
        return current != null && current.settlementId().equals(settlementId) && settlements.remove(playerUuid, current);
    }
}
