package kr.lunaf.cloudislands.coreservice.warehouse;

import java.util.Optional;
import java.util.UUID;

public interface WarehouseSettlementRepository {
    PrepareResult prepare(WarehouseSettlementRecord settlement);

    TransitionResult markEscrowed(UUID playerUuid, UUID settlementId);

    Optional<WarehouseSettlementRecord> find(UUID playerUuid);

    boolean clear(UUID playerUuid, UUID settlementId);

    record PrepareResult(boolean accepted, String code, WarehouseSettlementRecord settlement) {}

    record TransitionResult(boolean accepted, String code, WarehouseSettlementRecord settlement) {}
}
