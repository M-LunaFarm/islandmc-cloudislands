package kr.lunaf.cloudislands.paper.application;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertThrows;

import java.lang.reflect.Proxy;
import java.util.ArrayList;
import java.util.List;
import java.util.UUID;
import java.util.concurrent.CompletableFuture;
import kr.lunaf.cloudislands.coreclient.CoreApiClient;
import kr.lunaf.cloudislands.coreclient.WarehouseCommandClient;
import kr.lunaf.cloudislands.coreclient.WarehouseItemView;
import kr.lunaf.cloudislands.coreclient.WarehouseMutationView;
import kr.lunaf.cloudislands.coreclient.WarehouseQueryClient;
import org.junit.jupiter.api.Test;

class IslandWarehouseUseCaseTest {
    @Test
    void warehouseOperationsRunBehindApplicationBoundary() {
        List<String> calls = new ArrayList<>();
        IslandWarehouseUseCase useCase = new IslandWarehouseUseCase(client(calls));
        UUID islandId = uuid("00000000-0000-0000-0000-000000000030");
        UUID actorUuid = uuid("00000000-0000-0000-0000-000000000001");

        List<IslandWarehouseUseCase.WarehouseItemView> items = useCase.listItems(islandId, 500).join();
        assertEquals("STONE", items.get(0).materialKey());
        assertEquals(12L, items.get(0).amount());
        IslandWarehouseUseCase.WarehouseOperationResult deposit = useCase.deposit(islandId, actorUuid, "STONE", 12L, mutationRunner(calls)).join();
        IslandWarehouseUseCase.WarehouseOperationResult withdraw = useCase.withdraw(islandId, actorUuid, "DIRT", 7L, mutationRunner(calls)).join();

        assertEquals(true, deposit.accepted());
        assertEquals("STONE", deposit.materialKey());
        assertEquals(12L, deposit.amount());
        assertEquals(false, withdraw.accepted());
        assertEquals("NO_STOCK", withdraw.code());
        assertEquals(List.of(
            "islandWarehouse:500",
            "audit:island.warehouse.deposit",
            "depositIslandWarehouse:STONE:12",
            "audit:island.warehouse.withdraw",
            "withdrawIslandWarehouse:DIRT:7"
        ), calls);
    }

    @Test
    void rejectsInvalidMutationInputBeforeCoreCall() {
        List<String> calls = new ArrayList<>();
        IslandWarehouseUseCase useCase = new IslandWarehouseUseCase(client(calls));
        UUID islandId = UUID.randomUUID();
        UUID actorUuid = UUID.randomUUID();

        assertThrows(IllegalArgumentException.class, () -> useCase.deposit(islandId, actorUuid, "", 1L, mutationRunner(calls)));
        assertThrows(IllegalArgumentException.class, () -> useCase.withdraw(islandId, actorUuid, "STONE", 0L, mutationRunner(calls)));
        assertEquals(List.of(), calls);
    }

    private static CoreApiClient client(List<String> calls) {
        return (CoreApiClient) Proxy.newProxyInstance(
            CoreApiClient.class.getClassLoader(),
            new Class<?>[] {CoreApiClient.class, WarehouseQueryClient.class, WarehouseCommandClient.class},
            (_proxy, method, args) -> switch (method.getName()) {
                case "warehouse" -> (WarehouseQueryClient) _proxy;
                case "warehouseCommands" -> (WarehouseCommandClient) _proxy;
                case "listItems" -> {
                    calls.add("islandWarehouse:" + args[1]);
                    yield CompletableFuture.completedFuture(List.of(
                        new WarehouseItemView("00000000-0000-0000-0000-000000000030", "STONE", 12L, ""),
                        new WarehouseItemView("00000000-0000-0000-0000-000000000030", "DIRT", 7L, "")
                    ));
                }
                case "deposit" -> {
                    calls.add("depositIslandWarehouse:" + args[2] + ":" + args[3]);
                    yield CompletableFuture.completedFuture(new WarehouseMutationView(true, "", "STONE", 12L));
                }
                case "withdraw" -> {
                    calls.add("withdrawIslandWarehouse:" + args[2] + ":" + args[3]);
                    yield CompletableFuture.completedFuture(new WarehouseMutationView(false, "NO_STOCK", "DIRT", 7L));
                }
                default -> throw new UnsupportedOperationException(method.getName());
            });
    }

    private static IslandWarehouseUseCase.MutationRunner mutationRunner(List<String> calls) {
        return (auditAction, operation) -> {
            calls.add("audit:" + auditAction);
            return operation.get();
        };
    }

    private static UUID uuid(String value) {
        return UUID.fromString(value);
    }
}
