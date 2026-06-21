package kr.lunaf.cloudislands.paper.application;

import java.util.List;
import java.util.Map;
import java.util.UUID;
import java.util.concurrent.CompletableFuture;
import java.util.function.Supplier;
import kr.lunaf.cloudislands.common.json.SimpleJson;
import kr.lunaf.cloudislands.coreclient.CoreApiClient;

public final class IslandWarehouseUseCase {
    private final CoreApiClient coreApiClient;

    public IslandWarehouseUseCase(CoreApiClient coreApiClient) {
        if (coreApiClient == null) {
            throw new IllegalArgumentException("coreApiClient is required");
        }
        this.coreApiClient = coreApiClient;
    }

    private CompletableFuture<String> listBody(UUID islandId, int limit) {
        requireIsland(islandId);
        return coreApiClient.islandWarehouse(islandId, Math.max(1, Math.min(limit, 100)));
    }

    public CompletableFuture<List<WarehouseItemView>> listItems(UUID islandId, int limit) {
        return listBody(islandId, limit).thenApply(IslandWarehouseUseCase::itemViews);
    }

    public CompletableFuture<WarehouseOperationResult> deposit(UUID islandId, UUID actorUuid, String materialKey, long amount, MutationRunner runner) {
        requireIsland(islandId);
        requireActor(actorUuid);
        requireMaterial(materialKey);
        requireAmount(amount);
        requireRunner(runner);
        return runner.mutateIdempotent("island.warehouse.deposit", () -> coreApiClient.depositIslandWarehouse(islandId, actorUuid, materialKey, amount))
            .thenApply(WarehouseOperationResult::fromBody);
    }

    public CompletableFuture<WarehouseOperationResult> withdraw(UUID islandId, UUID actorUuid, String materialKey, long amount, MutationRunner runner) {
        requireIsland(islandId);
        requireActor(actorUuid);
        requireMaterial(materialKey);
        requireAmount(amount);
        requireRunner(runner);
        return runner.mutateIdempotent("island.warehouse.withdraw", () -> coreApiClient.withdrawIslandWarehouse(islandId, actorUuid, materialKey, amount))
            .thenApply(WarehouseOperationResult::fromBody);
    }

    private static void requireIsland(UUID islandId) {
        if (islandId == null) {
            throw new IllegalArgumentException("islandId is required");
        }
    }

    private static void requireActor(UUID actorUuid) {
        if (actorUuid == null) {
            throw new IllegalArgumentException("actorUuid is required");
        }
    }

    private static void requireMaterial(String materialKey) {
        if (materialKey == null || materialKey.isBlank()) {
            throw new IllegalArgumentException("materialKey is required");
        }
    }

    private static void requireAmount(long amount) {
        if (amount <= 0L) {
            throw new IllegalArgumentException("positive amount is required");
        }
    }

    private static void requireRunner(MutationRunner runner) {
        if (runner == null) {
            throw new IllegalArgumentException("runner is required");
        }
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

    private static Map<?, ?> root(String body) {
        return SimpleJson.object(SimpleJson.parse(body));
    }

    private static String text(Map<?, ?> object, String key) {
        return SimpleJson.text(object.get(key));
    }

    @FunctionalInterface
    public interface MutationRunner {
        CompletableFuture<String> mutateIdempotent(String auditAction, Supplier<CompletableFuture<String>> operation);
    }

    public record WarehouseOperationResult(boolean accepted, String code, String materialKey, long amount) {
        private static WarehouseOperationResult fromBody(String body) {
            Map<?, ?> root = root(body);
            boolean accepted = !root.containsKey("error")
                && !Boolean.FALSE.equals(root.get("accepted"))
                && !Boolean.FALSE.equals(root.get("applied"));
            return new WarehouseOperationResult(accepted, text(root, "code"), text(root, "materialKey"), SimpleJson.number(root.get("amount")));
        }
    }

    public record WarehouseItemView(String materialKey, long amount) {
        public WarehouseItemView {
            materialKey = materialKey == null ? "" : materialKey;
        }
    }
}
