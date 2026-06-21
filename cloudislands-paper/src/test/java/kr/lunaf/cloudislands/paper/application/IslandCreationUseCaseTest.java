package kr.lunaf.cloudislands.paper.application;

import static org.junit.jupiter.api.Assertions.assertEquals;

import java.lang.reflect.Proxy;
import java.util.ArrayList;
import java.util.List;
import java.util.UUID;
import java.util.concurrent.CompletableFuture;
import kr.lunaf.cloudislands.api.model.CreateIslandResult;
import kr.lunaf.cloudislands.api.model.DeleteIslandResult;
import kr.lunaf.cloudislands.coreclient.CoreApiClient;
import kr.lunaf.cloudislands.coreclient.CoreIslandLifecycleCommandClient;
import org.junit.jupiter.api.Test;

class IslandCreationUseCaseTest {
    @Test
    void lifecycleMutationsRunBehindApplicationAuditBoundaries() {
        List<String> calls = new ArrayList<>();
        IslandCreationUseCase useCase = new IslandCreationUseCase(client(calls));
        UUID playerUuid = uuid("00000000-0000-0000-0000-000000000001");
        UUID islandId = uuid("00000000-0000-0000-0000-000000000020");

        assertEquals("CREATED", useCase.create(playerUuid, "", mutationRunner(calls)).join().code());
        assertEquals(islandId, useCase.delete(playerUuid, islandId, idempotentRunner(calls)).join().islandId());
        assertEquals("RESET_QUEUED", useCase.resetAction(islandId, playerUuid, "", idempotentRunner(calls)).join().code());

        assertEquals(List.of(
            "audit:island.create",
            "createIsland:default",
            "audit-idempotent:island.delete",
            "deleteIsland:" + islandId,
            "audit-idempotent:island.reset",
            "resetIslandResult:player-reset"
        ), calls);
    }

    private static CoreApiClient client(List<String> calls) {
        return (CoreApiClient) Proxy.newProxyInstance(
            CoreApiClient.class.getClassLoader(),
            new Class<?>[] {CoreApiClient.class},
            (_proxy, method, args) -> switch (method.getName()) {
                case "lifecycle" -> new CoreIslandLifecycleCommandClient((CoreApiClient) _proxy);
                case "createIsland" -> {
                    calls.add("createIsland:" + args[1]);
                    yield CompletableFuture.completedFuture(new CreateIslandResult(true, "CREATED", null, null));
                }
                case "deleteIsland" -> {
                    calls.add("deleteIsland:" + args[1]);
                    yield CompletableFuture.completedFuture(new DeleteIslandResult(true, "DELETED", (UUID) args[1]));
                }
                case "resetIslandResult" -> {
                    calls.add("resetIslandResult:" + args[2]);
                    yield CompletableFuture.completedFuture("{\"accepted\":true,\"code\":\"RESET_QUEUED\"}");
                }
                default -> throw new UnsupportedOperationException(method.getName());
            });
    }

    private static IslandCreationUseCase.MutationRunner mutationRunner(List<String> calls) {
        return new IslandCreationUseCase.MutationRunner() {
            @Override
            public <T> CompletableFuture<T> mutate(String auditAction, java.util.function.Supplier<CompletableFuture<T>> operation) {
                calls.add("audit:" + auditAction);
                return operation.get();
            }
        };
    }

    private static IslandCreationUseCase.IdempotentMutationRunner idempotentRunner(List<String> calls) {
        return new IslandCreationUseCase.IdempotentMutationRunner() {
            @Override
            public <T> CompletableFuture<T> mutateIdempotent(String auditAction, java.util.function.Supplier<CompletableFuture<T>> operation) {
                calls.add("audit-idempotent:" + auditAction);
                return operation.get();
            }
        };
    }

    private static UUID uuid(String value) {
        return UUID.fromString(value);
    }
}
