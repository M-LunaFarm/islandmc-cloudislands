package kr.lunaf.cloudislands.coreclient;

import java.util.Map;
import java.util.UUID;
import java.util.concurrent.CompletableFuture;

final class JdkWarehouseCommandClient implements WarehouseCommandClient {
    private final JdkCoreApiClient core;

    JdkWarehouseCommandClient(JdkCoreApiClient core) {
        if (core == null) {
            throw new IllegalArgumentException("core is required");
        }
        this.core = core;
    }

    @Override
    public CompletableFuture<WarehouseMutationView> deposit(UUID islandId, UUID actorUuid, String materialKey, long amount) {
        requireId(islandId, "islandId");
        requireId(actorUuid, "actorUuid");
        return core.postResultBody("/v1/islands/warehouse/deposit", CoreJsonPayload.object("islandId", islandId, "actorUuid", actorUuid, "materialKey", materialKey == null ? "" : materialKey, "amount", amount))
            .thenApply(CoreResponseBody::value)
            .thenApply(JdkWarehouseCommandClient::warehouseMutation);
    }

    @Override
    public CompletableFuture<WarehouseMutationView> withdraw(UUID islandId, UUID actorUuid, String materialKey, long amount) {
        requireId(islandId, "islandId");
        requireId(actorUuid, "actorUuid");
        return core.postResultBody("/v1/islands/warehouse/withdraw", CoreJsonPayload.object("islandId", islandId, "actorUuid", actorUuid, "materialKey", materialKey == null ? "" : materialKey, "amount", amount))
            .thenApply(CoreResponseBody::value)
            .thenApply(JdkWarehouseCommandClient::warehouseMutation);
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
