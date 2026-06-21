package kr.lunaf.cloudislands.coreclient;

import java.util.List;
import java.util.Map;
import java.util.UUID;
import java.util.concurrent.CompletableFuture;
import kr.lunaf.cloudislands.common.json.SimpleJson;

public final class CoreWarehouseQueryClient implements WarehouseQueryClient {
    private final CoreApiClient delegate;

    public CoreWarehouseQueryClient(CoreApiClient delegate) {
        if (delegate == null) {
            throw new IllegalArgumentException("delegate is required");
        }
        this.delegate = delegate;
    }

    @Override
    public CompletableFuture<List<WarehouseItemView>> listItems(UUID islandId, int limit) {
        requireIsland(islandId);
        int safeLimit = Math.max(1, Math.min(limit, 100));
        return delegate.islandWarehouse(islandId, safeLimit).thenApply(body -> itemViews(islandId, body));
    }

    private static List<WarehouseItemView> itemViews(UUID islandId, String body) {
        return entries(body).stream()
            .map(object -> new WarehouseItemView(itemIslandId(islandId, object), text(object, "materialKey"), SimpleJson.number(object.get("amount")), text(object, "updatedAt")))
            .filter(item -> !item.materialKey().isBlank() && item.amount() > 0L)
            .toList();
    }

    private static String itemIslandId(UUID fallbackIslandId, Map<?, ?> object) {
        String itemIslandId = text(object, "islandId");
        return itemIslandId.isBlank() ? fallbackIslandId.toString() : itemIslandId;
    }

    private static List<Map<?, ?>> entries(String body) {
        return CoreJson.entries(body);
    }

    private static String text(Map<?, ?> object, String key) {
        return SimpleJson.text(object.get(key));
    }

    private static void requireIsland(UUID islandId) {
        if (islandId == null) {
            throw new IllegalArgumentException("islandId is required");
        }
    }
}
