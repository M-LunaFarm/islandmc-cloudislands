package kr.lunaf.cloudislands.paper.application;

import static org.junit.jupiter.api.Assertions.assertEquals;

import java.lang.reflect.Proxy;
import java.util.ArrayList;
import java.util.List;
import java.util.UUID;
import java.util.concurrent.CompletableFuture;
import kr.lunaf.cloudislands.api.model.IslandFlag;
import kr.lunaf.cloudislands.coreclient.CoreApiClient;
import org.junit.jupiter.api.Test;

class IslandSettingsUseCaseTest {
    @Test
    void settingsOperationsRunBehindApplicationBoundary() {
        List<String> calls = new ArrayList<>();
        IslandSettingsUseCase useCase = new IslandSettingsUseCase(client(calls));
        UUID islandId = uuid("00000000-0000-0000-0000-000000000040");
        UUID actorUuid = uuid("00000000-0000-0000-0000-000000000001");

        assertEquals("public", useCase.setPublicAccess(islandId, actorUuid, true, mutationRunner(calls)).join());
        assertEquals("locked", useCase.setLocked(islandId, actorUuid, false, mutationRunner(calls)).join());
        assertEquals("named", useCase.setName(islandId, actorUuid, "My Island", mutationRunner(calls)).join());
        assertEquals("flags", useCase.listFlags(islandId).join());
        assertEquals("flagged", useCase.setFlag(islandId, actorUuid, IslandFlag.PVP, "false", mutationRunner(calls)).join());

        assertEquals(List.of(
            "audit:island.public-access.set",
            "setIslandPublicAccessResult:true",
            "audit:island.locked.set",
            "setIslandLockedResult:false",
            "audit:island.name.set",
            "setIslandNameResult:My Island",
            "listIslandFlags",
            "audit:island.flag.set",
            "setIslandFlagResult:PVP:false"
        ), calls);
    }

    private static CoreApiClient client(List<String> calls) {
        return (CoreApiClient) Proxy.newProxyInstance(
            CoreApiClient.class.getClassLoader(),
            new Class<?>[] {CoreApiClient.class},
            (_proxy, method, args) -> switch (method.getName()) {
                case "setIslandPublicAccessResult" -> {
                    calls.add("setIslandPublicAccessResult:" + args[2]);
                    yield CompletableFuture.completedFuture("public");
                }
                case "setIslandLockedResult" -> {
                    calls.add("setIslandLockedResult:" + args[2]);
                    yield CompletableFuture.completedFuture("locked");
                }
                case "setIslandNameResult" -> {
                    calls.add("setIslandNameResult:" + args[2]);
                    yield CompletableFuture.completedFuture("named");
                }
                case "listIslandFlags" -> {
                    calls.add("listIslandFlags");
                    yield CompletableFuture.completedFuture("flags");
                }
                case "setIslandFlagResult" -> {
                    calls.add("setIslandFlagResult:" + args[2] + ":" + args[3]);
                    yield CompletableFuture.completedFuture("flagged");
                }
                default -> throw new UnsupportedOperationException(method.getName());
            });
    }

    private static IslandSettingsUseCase.MutationRunner mutationRunner(List<String> calls) {
        return (auditAction, operation) -> {
            calls.add("audit:" + auditAction);
            return operation.get();
        };
    }

    private static UUID uuid(String value) {
        return UUID.fromString(value);
    }
}
