package kr.lunaf.cloudislands.paper.application;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertTrue;

import java.lang.reflect.Proxy;
import java.util.ArrayList;
import java.util.List;
import java.util.UUID;
import java.util.concurrent.CompletableFuture;
import kr.lunaf.cloudislands.api.model.IslandFlag;
import kr.lunaf.cloudislands.coreclient.CoreApiClient;
import kr.lunaf.cloudislands.coreclient.CoreGuiViews.LimitView;
import kr.lunaf.cloudislands.paper.application.IslandEnvironmentUseCase.EnvironmentActionResult;
import org.junit.jupiter.api.Test;

class IslandEnvironmentUseCaseTest {
    @Test
    void environmentReadsAndMutationsExposeTypedViews() {
        List<String> calls = new ArrayList<>();
        IslandEnvironmentUseCase useCase = new IslandEnvironmentUseCase(client(calls));
        UUID islandId = uuid("00000000-0000-0000-0000-000000000070");
        UUID actorUuid = uuid("00000000-0000-0000-0000-000000000001");

        assertEquals("PLAINS", useCase.islandBiomeValue(islandId).join().key());
        assertEquals(300L, useCase.islandInfoView(islandId).join().size());
        assertEquals("blue", useCase.flagValues(islandId).join().get(IslandFlag.BORDER_COLOR));
        List<LimitView> limits = useCase.limitViews(islandId).join();
        assertEquals("HOPPER", limits.get(0).key());
        assertEquals(64L, limits.get(0).value());

        EnvironmentActionResult biome = useCase.setBiomeAction(islandId, actorUuid, "PLAINS", mutationRunner(calls)).join();
        assertTrue(biome.accepted());
        assertEquals("BIOME_SET", biome.code());
        assertEquals("PLAINS", biome.key());

        EnvironmentActionResult flag = useCase.setFlagAction(islandId, actorUuid, IslandFlag.BORDER_VISIBLE, "true", mutationRunner(calls)).join();
        assertTrue(flag.accepted());
        assertEquals("BORDER_VISIBLE", flag.key());

        EnvironmentActionResult limit = useCase.setLimitAction(islandId, actorUuid, "HOPPER", 64L, mutationRunner(calls)).join();
        assertTrue(limit.accepted());
        assertEquals("HOPPER", limit.key());
        assertEquals(64L, limit.value());

        assertEquals(List.of(
            "islandBiome",
            "islandInfo",
            "listIslandFlags",
            "listIslandLimits",
            "audit:island.biome.set",
            "setIslandBiomeResult:PLAINS",
            "audit:island.flag.set",
            "setIslandFlagResult:BORDER_VISIBLE:true",
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
                    yield CompletableFuture.completedFuture("{\"biomeKey\":\"PLAINS\"}");
                }
                case "setIslandBiomeResult" -> {
                    calls.add("setIslandBiomeResult:" + args[2]);
                    yield CompletableFuture.completedFuture("{\"accepted\":true,\"code\":\"BIOME_SET\",\"biomeKey\":\"PLAINS\"}");
                }
                case "islandInfo" -> {
                    calls.add("islandInfo");
                    yield CompletableFuture.completedFuture("{\"size\":300,\"border\":310}");
                }
                case "listIslandFlags" -> {
                    calls.add("listIslandFlags");
                    yield CompletableFuture.completedFuture("{\"flags\":{\"BORDER_VISIBLE\":\"true\",\"BORDER_COLOR\":\"blue\"}}");
                }
                case "setIslandFlagResult" -> {
                    calls.add("setIslandFlagResult:" + args[2] + ":" + args[3]);
                    yield CompletableFuture.completedFuture("{\"accepted\":true,\"code\":\"FLAG_SET\",\"flag\":\"BORDER_VISIBLE\"}");
                }
                case "listIslandLimits" -> {
                    calls.add("listIslandLimits");
                    yield CompletableFuture.completedFuture("{\"limits\":[{\"limitKey\":\"HOPPER\",\"value\":64,\"updatedAt\":\"now\"}]}");
                }
                case "setIslandLimit" -> {
                    calls.add("setIslandLimit:" + args[2] + ":" + args[3]);
                    yield CompletableFuture.completedFuture("{\"accepted\":true,\"code\":\"LIMIT_SET\",\"limitKey\":\"HOPPER\",\"value\":64}");
                }
                default -> throw new UnsupportedOperationException(method.getName());
            });
    }

    private static IslandEnvironmentUseCase.MutationRunner mutationRunner(List<String> calls) {
        return new IslandEnvironmentUseCase.MutationRunner() {
            @Override
            public <T> CompletableFuture<T> mutate(String auditAction, java.util.function.Supplier<CompletableFuture<T>> operation) {
                calls.add("audit:" + auditAction);
                return operation.get();
            }
        };
    }

    private static UUID uuid(String value) {
        return UUID.fromString(value);
    }
}
