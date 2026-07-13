package kr.lunaf.cloudislands.coreclient;

import java.util.List;
import java.util.Optional;
import java.util.UUID;
import java.util.concurrent.CompletableFuture;

public interface WarehouseQueryClient {
    CompletableFuture<List<WarehouseItemView>> listItems(UUID islandId, int limit);

    CompletableFuture<Optional<WarehouseSettlementView>> pendingSettlement(UUID playerUuid);
}
