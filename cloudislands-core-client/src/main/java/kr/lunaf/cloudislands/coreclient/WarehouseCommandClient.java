package kr.lunaf.cloudislands.coreclient;

import java.util.UUID;
import java.util.concurrent.CompletableFuture;

public interface WarehouseCommandClient {
    CompletableFuture<WarehouseMutationView> deposit(UUID islandId, UUID actorUuid, String materialKey, long amount);

    CompletableFuture<WarehouseMutationView> withdraw(UUID islandId, UUID actorUuid, String materialKey, long amount);

    CompletableFuture<WarehouseSettlementResult> prepareSettlement(WarehouseSettlementView settlement);

    CompletableFuture<WarehouseSettlementResult> escrowSettlement(UUID playerUuid, UUID settlementId);

    CompletableFuture<WarehouseSettlementResult> clearSettlement(UUID playerUuid, UUID settlementId);
}
