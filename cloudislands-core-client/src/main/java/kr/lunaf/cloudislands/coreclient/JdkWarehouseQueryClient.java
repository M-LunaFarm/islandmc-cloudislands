package kr.lunaf.cloudislands.coreclient;

import java.util.List;
import java.util.Map;
import java.util.UUID;
import java.util.concurrent.CompletableFuture;
import kr.lunaf.cloudislands.common.json.SimpleJson;

final class JdkWarehouseQueryClient implements WarehouseQueryClient {
    private final JdkCoreApiClient core;

    JdkWarehouseQueryClient(JdkCoreApiClient core) {
        if (core == null) {
            throw new IllegalArgumentException("core is required");
        }
        this.core = core;
    }

    @Override
    public CompletableFuture<List<WarehouseItemView>> listItems(UUID islandId, int limit) {
        requireId(islandId, "islandId");
        int safeLimit = Math.max(1, Math.min(limit, 100));
        return core.post("/v1/islands/warehouse", JdkCoreApiClient.jsonObject("islandId", islandId, "limit", safeLimit))
            .thenApply(body -> warehouseItems(islandId, body));
    }

    private static List<WarehouseItemView> warehouseItems(UUID islandId, String body) {
        return CoreJson.entries(body).stream()
            .map(object -> new WarehouseItemView(warehouseItemIslandId(islandId, object), SimpleJson.text(object.get("materialKey")), SimpleJson.number(object.get("amount")), SimpleJson.text(object.get("updatedAt"))))
            .filter(item -> !item.materialKey().isBlank() && item.amount() > 0L)
            .toList();
    }

    private static String warehouseItemIslandId(UUID fallbackIslandId, Map<?, ?> object) {
        String itemIslandId = SimpleJson.text(object.get("islandId"));
        return itemIslandId.isBlank() ? fallbackIslandId.toString() : itemIslandId;
    }

    private static void requireId(UUID id, String name) {
        if (id == null) {
            throw new IllegalArgumentException(name + " is required");
        }
    }
}
