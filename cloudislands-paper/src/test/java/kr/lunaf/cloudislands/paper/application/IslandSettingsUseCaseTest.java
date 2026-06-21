package kr.lunaf.cloudislands.paper.application;

import static org.junit.jupiter.api.Assertions.assertEquals;

import java.lang.reflect.Proxy;
import java.util.ArrayList;
import java.util.List;
import java.util.Map;
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

        Map<IslandFlag, String> flags = useCase.flagValues(islandId).join();
        assertEquals("true", flags.get(IslandFlag.PVP));
        assertEquals("PUBLIC_ACCESS_ENABLED", useCase.setPublicAccessAction(islandId, actorUuid, true, mutationRunner(calls)).join().code());
        assertEquals("ISLAND_UNLOCKED", useCase.setLockedAction(islandId, actorUuid, false, mutationRunner(calls)).join().code());
        assertEquals("ISLAND_RENAMED", useCase.setNameAction(islandId, actorUuid, "My Island", mutationRunner(calls)).join().code());
        assertEquals("FLAG_SET", useCase.setFlagAction(islandId, actorUuid, IslandFlag.PVP, "false", mutationRunner(calls)).join().code());

        assertEquals(List.of(
            "listIslandFlags",
            "audit:island.public-access.set",
            "setIslandPublicAccessResult:true",
            "audit:island.locked.set",
            "setIslandLockedResult:false",
            "audit:island.name.set",
            "setIslandNameResult:My Island",
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
                    yield CompletableFuture.completedFuture("{\"accepted\":true,\"code\":\"PUBLIC_ACCESS_ENABLED\"}");
                }
                case "setIslandLockedResult" -> {
                    calls.add("setIslandLockedResult:" + args[2]);
                    yield CompletableFuture.completedFuture("{\"accepted\":true,\"code\":\"ISLAND_UNLOCKED\"}");
                }
                case "setIslandNameResult" -> {
                    calls.add("setIslandNameResult:" + args[2]);
                    yield CompletableFuture.completedFuture("{\"accepted\":true,\"code\":\"ISLAND_RENAMED\"}");
                }
                case "listIslandFlags" -> {
                    calls.add("listIslandFlags");
                    yield CompletableFuture.completedFuture("{\"flags\":{\"PVP\":\"true\",\"FLY\":\"false\"}}");
                }
                case "setIslandFlagResult" -> {
                    calls.add("setIslandFlagResult:" + args[2] + ":" + args[3]);
                    yield CompletableFuture.completedFuture("{\"accepted\":true,\"code\":\"FLAG_SET\",\"flag\":\"PVP\"}");
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
