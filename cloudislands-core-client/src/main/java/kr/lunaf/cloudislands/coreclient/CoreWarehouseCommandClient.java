package kr.lunaf.cloudislands.coreclient;

import java.util.Map;
import java.util.UUID;
import java.util.concurrent.CompletableFuture;

public final class CoreWarehouseCommandClient implements WarehouseCommandClient {
    private final CoreApiClient delegate;

    public CoreWarehouseCommandClient(CoreApiClient delegate) {
        if (delegate == null) {
            throw new IllegalArgumentException("delegate is required");
        }
        this.delegate = delegate;
    }

    @Override
    public CompletableFuture<WarehouseMutationView> deposit(UUID islandId, UUID actorUuid, String materialKey, long amount) {
        requireId(islandId, "islandId");
        requireId(actorUuid, "actorUuid");
        return delegate.depositIslandWarehouse(islandId, actorUuid, materialKey == null ? "" : materialKey, amount)
            .thenApply(CoreWarehouseCommandClient::warehouseMutation);
    }

    @Override
    public CompletableFuture<WarehouseMutationView> withdraw(UUID islandId, UUID actorUuid, String materialKey, long amount) {
        requireId(islandId, "islandId");
        requireId(actorUuid, "actorUuid");
        return delegate.withdrawIslandWarehouse(islandId, actorUuid, materialKey == null ? "" : materialKey, amount)
            .thenApply(CoreWarehouseCommandClient::warehouseMutation);
    }

    private static WarehouseMutationView warehouseMutation(String body) {
        Map<?, ?> root = CoreJson.object(body);
        return new WarehouseMutationView(CoreJson.accepted(root), CoreJson.text(root, "code"), CoreJson.text(root, "materialKey"), CoreJson.number(root, "amount"));
    }

    private static void requireId(UUID id, String name) {
        if (id == null) {
            throw new IllegalArgumentException(name + " is required");
        }
    }
}
