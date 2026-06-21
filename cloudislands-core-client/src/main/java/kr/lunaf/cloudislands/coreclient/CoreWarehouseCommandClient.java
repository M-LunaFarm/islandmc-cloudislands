package kr.lunaf.cloudislands.coreclient;

import java.util.Map;
import java.util.UUID;
import java.util.concurrent.CompletableFuture;
import kr.lunaf.cloudislands.common.json.SimpleJson;

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
        Map<?, ?> root = SimpleJson.object(SimpleJson.parse(body));
        boolean accepted = !root.containsKey("error")
            && !Boolean.FALSE.equals(root.get("accepted"))
            && !Boolean.FALSE.equals(root.get("applied"));
        return new WarehouseMutationView(accepted, SimpleJson.text(root.get("code")), SimpleJson.text(root.get("materialKey")), SimpleJson.number(root.get("amount")));
    }

    private static void requireId(UUID id, String name) {
        if (id == null) {
            throw new IllegalArgumentException(name + " is required");
        }
    }
}
