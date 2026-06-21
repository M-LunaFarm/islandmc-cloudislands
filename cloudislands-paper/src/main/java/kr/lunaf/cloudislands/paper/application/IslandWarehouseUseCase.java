package kr.lunaf.cloudislands.paper.application;

import java.util.List;
import java.util.Map;
import java.util.UUID;
import java.util.concurrent.CompletableFuture;
import java.util.function.Supplier;
import kr.lunaf.cloudislands.common.json.SimpleJson;
import kr.lunaf.cloudislands.coreclient.CoreApiClient;
import kr.lunaf.cloudislands.coreclient.CoreWarehouseQueryClient;
import kr.lunaf.cloudislands.coreclient.WarehouseQueryClient;

public final class IslandWarehouseUseCase {
    private final CoreApiClient coreApiClient;
    private final WarehouseQueryClient warehouseQueries;

    public IslandWarehouseUseCase(CoreApiClient coreApiClient) {
        if (coreApiClient == null) {
            throw new IllegalArgumentException("coreApiClient is required");
        }
        this.coreApiClient = coreApiClient;
        this.warehouseQueries = new CoreWarehouseQueryClient(coreApiClient);
    }

    IslandWarehouseUseCase(CoreApiClient coreApiClient, WarehouseQueryClient warehouseQueries) {
        if (coreApiClient == null) {
            throw new IllegalArgumentException("coreApiClient is required");
        }
        if (warehouseQueries == null) {
            throw new IllegalArgumentException("warehouseQueries is required");
        }
        this.coreApiClient = coreApiClient;
        this.warehouseQueries = warehouseQueries;
    }

    public CompletableFuture<List<WarehouseItemView>> listItems(UUID islandId, int limit) {
        requireIsland(islandId);
        return warehouseQueries.listItems(islandId, limit)
            .thenApply(items -> items.stream()
                .map(item -> new WarehouseItemView(item.materialKey(), item.amount()))
                .toList());
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
