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
import kr.lunaf.cloudislands.coreclient.IslandLifecycleActionView;
import kr.lunaf.cloudislands.coreclient.IslandLifecycleCommandClient;
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
            "resetIsland:player-reset"
        ), calls);
    }

    private static CoreApiClient client(List<String> calls) {
        return (CoreApiClient) Proxy.newProxyInstance(
            CoreApiClient.class.getClassLoader(),
            new Class<?>[] {CoreApiClient.class, IslandLifecycleCommandClient.class},
            (_proxy, method, args) -> switch (method.getName()) {
                case "lifecycle" -> (IslandLifecycleCommandClient) _proxy;
                case "createIsland" -> {
                    String templateId = args[1] == null || args[1].toString().isBlank() ? "default" : args[1].toString().trim();
                    calls.add("createIsland:" + templateId);
                    yield CompletableFuture.completedFuture(new CreateIslandResult(true, "CREATED", null, null));
                }
                case "deleteIsland" -> {
                    calls.add("deleteIsland:" + args[1]);
                    yield CompletableFuture.completedFuture(new DeleteIslandResult(true, "DELETED", (UUID) args[1]));
                }
                case "resetIsland" -> {
                    String reason = args[2] == null || args[2].toString().isBlank() ? "player-reset" : args[2].toString().trim();
                    calls.add("resetIsland:" + reason);
                    yield CompletableFuture.completedFuture(new IslandLifecycleActionView(true, "RESET_QUEUED", args[0].toString(), 0L, ""));
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
