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

class IslandEnvironmentUseCaseTest {
    @Test
    void environmentOperationsRunBehindApplicationBoundary() {
        List<String> calls = new ArrayList<>();
        IslandEnvironmentUseCase useCase = new IslandEnvironmentUseCase(client(calls));
        UUID islandId = uuid("00000000-0000-0000-0000-000000000070");
        UUID actorUuid = uuid("00000000-0000-0000-0000-000000000001");

        assertEquals("biome", useCase.islandBiome(islandId).join());
        assertEquals("biome-set", useCase.setBiome(islandId, actorUuid, "PLAINS", mutationRunner(calls)).join());
        assertEquals("info", useCase.islandInfo(islandId).join());
        assertEquals("flags", useCase.listFlags(islandId).join());
        assertEquals("flag-set", useCase.setFlag(islandId, actorUuid, IslandFlag.BORDER_VISIBLE, "true", mutationRunner(calls)).join());
        assertEquals("limits", useCase.listLimits(islandId).join());
        assertEquals("limit-set", useCase.setLimit(islandId, actorUuid, "HOPPER", 64L, mutationRunner(calls)).join());

        assertEquals(List.of(
            "islandBiome",
            "audit:island.biome.set",
            "setIslandBiomeResult:PLAINS",
            "islandInfo",
            "listIslandFlags",
            "audit:island.flag.set",
            "setIslandFlagResult:BORDER_VISIBLE:true",
            "listIslandLimits",
            "audit:island.limit.set",
            "setIslandLimit:HOPPER:64"
        ), calls);
    }

    private static CoreApiClient client(List<String> calls) {
        return (CoreApiClient) Proxy.newProxyInstance(
            CoreApiClient.class.getClassLoader(),
            new Class<?>[] {CoreApiClient.class},
            (_proxy, method, args) -> switch (method.getName()) {
                case "islandBiome" -> {
                    calls.add("islandBiome");
                    yield CompletableFuture.completedFuture("biome");
                }
                case "setIslandBiomeResult" -> {
                    calls.add("setIslandBiomeResult:" + args[2]);
                    yield CompletableFuture.completedFuture("biome-set");
                }
                case "islandInfo" -> {
                    calls.add("islandInfo");
                    yield CompletableFuture.completedFuture("info");
                }
                case "listIslandFlags" -> {
                    calls.add("listIslandFlags");
                    yield CompletableFuture.completedFuture("flags");
                }
                case "setIslandFlagResult" -> {
                    calls.add("setIslandFlagResult:" + args[2] + ":" + args[3]);
                    yield CompletableFuture.completedFuture("flag-set");
                }
                case "listIslandLimits" -> {
                    calls.add("listIslandLimits");
                    yield CompletableFuture.completedFuture("limits");
                }
                case "setIslandLimit" -> {
                    calls.add("setIslandLimit:" + args[2] + ":" + args[3]);
                    yield CompletableFuture.completedFuture("limit-set");
                }
                default -> throw new UnsupportedOperationException(method.getName());
            });
    }

    private static IslandEnvironmentUseCase.MutationRunner mutationRunner(List<String> calls) {
        return (auditAction, operation) -> {
            calls.add("audit:" + auditAction);
            return operation.get();
        };
    }

    private static UUID uuid(String value) {
        return UUID.fromString(value);
    }
}
