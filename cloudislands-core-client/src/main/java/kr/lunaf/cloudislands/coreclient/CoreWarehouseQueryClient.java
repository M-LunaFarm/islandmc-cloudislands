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
        return delegate.islandWarehouse(islandId, safeLimit).thenApply(CoreWarehouseQueryClient::itemViews);
    }

    private static List<WarehouseItemView> itemViews(String body) {
        return entries(body).stream()
            .map(object -> new WarehouseItemView(text(object, "materialKey"), SimpleJson.number(object.get("amount"))))
            .filter(item -> !item.materialKey().isBlank() && item.amount() > 0L)
            .toList();
    }

    private static List<Map<?, ?>> entries(String body) {
        Object parsed = SimpleJson.parse(body);
        if (parsed instanceof List<?>) {
            return SimpleJson.list(parsed).stream()
                .map(SimpleJson::object)
                .filter(map -> !map.isEmpty())
                .toList();
        }
        Map<?, ?> root = SimpleJson.object(parsed);
        for (Object value : root.values()) {
            if (value instanceof List<?>) {
                return SimpleJson.list(value).stream()
                    .map(SimpleJson::object)
                    .filter(map -> !map.isEmpty())
                    .toList();
            }
        }
        return root.isEmpty() ? List.of() : List.of(root);
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
