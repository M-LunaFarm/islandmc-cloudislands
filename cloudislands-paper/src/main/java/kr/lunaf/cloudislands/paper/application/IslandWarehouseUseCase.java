package kr.lunaf.cloudislands.paper.application;

import java.util.List;
import java.util.UUID;
import java.util.concurrent.CompletableFuture;
import java.util.function.Supplier;
import kr.lunaf.cloudislands.coreclient.CoreApiClient;
import kr.lunaf.cloudislands.coreclient.WarehouseCommandClient;
import kr.lunaf.cloudislands.coreclient.WarehouseMutationView;
import kr.lunaf.cloudislands.coreclient.WarehouseQueryClient;

public final class IslandWarehouseUseCase {
    private final CoreApiClient coreApiClient;
    private final WarehouseQueryClient warehouseQueries;
    private final WarehouseCommandClient warehouseCommands;

    public IslandWarehouseUseCase(CoreApiClient coreApiClient) {
        if (coreApiClient == null) {
            throw new IllegalArgumentException("coreApiClient is required");
        }
        this.coreApiClient = coreApiClient;
        this.warehouseQueries = coreApiClient.warehouse();
        this.warehouseCommands = coreApiClient.warehouseCommands();
    }

    IslandWarehouseUseCase(CoreApiClient coreApiClient, WarehouseQueryClient warehouseQueries) {
        this(coreApiClient, warehouseQueries, coreApiClient.warehouseCommands());
    }

    IslandWarehouseUseCase(CoreApiClient coreApiClient, WarehouseQueryClient warehouseQueries, WarehouseCommandClient warehouseCommands) {
        if (coreApiClient == null) {
            throw new IllegalArgumentException("coreApiClient is required");
        }
        if (warehouseQueries == null) {
            throw new IllegalArgumentException("warehouseQueries is required");
        }
        if (warehouseCommands == null) {
            throw new IllegalArgumentException("warehouseCommands is required");
        }
        this.coreApiClient = coreApiClient;
        this.warehouseQueries = warehouseQueries;
        this.warehouseCommands = warehouseCommands;
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
        return runner.mutateIdempotent("island.warehouse.deposit", () -> warehouseCommands.deposit(islandId, actorUuid, materialKey, amount))
            .thenApply(WarehouseOperationResult::fromMutation);
    }

    public CompletableFuture<WarehouseOperationResult> withdraw(UUID islandId, UUID actorUuid, String materialKey, long amount, MutationRunner runner) {
        requireIsland(islandId);
        requireActor(actorUuid);
        requireMaterial(materialKey);
        requireAmount(amount);
        requireRunner(runner);
        return runner.mutateIdempotent("island.warehouse.withdraw", () -> warehouseCommands.withdraw(islandId, actorUuid, materialKey, amount))
            .thenApply(WarehouseOperationResult::fromMutation);
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

    @FunctionalInterface
    public interface MutationRunner {
        CompletableFuture<WarehouseMutationView> mutateIdempotent(String auditAction, Supplier<CompletableFuture<WarehouseMutationView>> operation);
    }

    public record WarehouseOperationResult(boolean accepted, String code, String materialKey, long amount) {
        private static WarehouseOperationResult fromMutation(WarehouseMutationView mutation) {
            return new WarehouseOperationResult(mutation.accepted(), mutation.code(), mutation.materialKey(), mutation.amount());
        }
    }

    public record WarehouseItemView(String materialKey, long amount) {
        public WarehouseItemView {
            materialKey = materialKey == null ? "" : materialKey;
        }
    }
}
