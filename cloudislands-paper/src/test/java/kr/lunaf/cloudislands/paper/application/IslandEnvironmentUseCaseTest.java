package kr.lunaf.cloudislands.paper.application;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertTrue;

import java.lang.reflect.Proxy;
import java.time.Instant;
import java.util.ArrayList;
import java.util.List;
import java.util.Map;
import java.util.UUID;
import java.util.concurrent.CompletableFuture;
import kr.lunaf.cloudislands.api.model.IslandBiomeSnapshot;
import kr.lunaf.cloudislands.api.model.IslandFlag;
import kr.lunaf.cloudislands.api.model.IslandFlagsSnapshot;
import kr.lunaf.cloudislands.api.model.IslandLimitSnapshot;
import kr.lunaf.cloudislands.coreclient.CoreApiClient;
import kr.lunaf.cloudislands.coreclient.CoreGuiViews;
import kr.lunaf.cloudislands.coreclient.CoreGuiViews.LimitView;
import kr.lunaf.cloudislands.coreclient.EnvironmentActionView;
import kr.lunaf.cloudislands.coreclient.IslandEnvironmentCommandClient;
import kr.lunaf.cloudislands.coreclient.IslandEnvironmentQueryClient;
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
            "flags",
            "limits",
            "audit:island.biome.set",
            "setBiome:PLAINS",
            "audit:island.flag.set",
            "setFlag:BORDER_VISIBLE:true",
            "audit:island.limit.set",
            "setLimit:HOPPER:64"
        ), calls);
    }

    private static CoreApiClient client(List<String> calls) {
        return (CoreApiClient) Proxy.newProxyInstance(
            CoreApiClient.class.getClassLoader(),
	            new Class<?>[] {CoreApiClient.class, IslandEnvironmentQueryClient.class, IslandEnvironmentCommandClient.class},
	            (_proxy, method, args) -> switch (method.getName()) {
	                case "environment" -> (IslandEnvironmentQueryClient) _proxy;
	                case "environmentCommands" -> (IslandEnvironmentCommandClient) _proxy;
	                case "biome" -> {
                    calls.add("islandBiome");
                    yield CompletableFuture.completedFuture(new IslandBiomeSnapshot(uuid("00000000-0000-0000-0000-000000000070"), "PLAINS", null, Instant.EPOCH));
                }
                case "islandBiome" -> {
                    calls.add("islandBiome");
                    yield CompletableFuture.completedFuture(new CoreGuiViews.BiomeView("PLAINS", "", ""));
                }
                case "setBiome" -> {
                    calls.add("setBiome:" + args[2]);
                    yield CompletableFuture.completedFuture(new EnvironmentActionView(true, "BIOME_SET", "PLAINS", 0L));
                }
                case "getIsland" -> {
                    calls.add("islandInfo");
                    yield CompletableFuture.completedFuture(new CoreGuiViews.IslandInfoView("", "", "", 0L, "0", false, false, 300L, 310L, "", "", ""));
                }
                case "flags" -> {
                    calls.add("flags");
                    yield CompletableFuture.completedFuture(new IslandFlagsSnapshot(uuid("00000000-0000-0000-0000-000000000070"), Map.of(IslandFlag.BORDER_VISIBLE, "true", IslandFlag.BORDER_COLOR, "blue")));
                }
                case "flagValues" -> {
                    calls.add("flags");
                    yield CompletableFuture.completedFuture(Map.of(IslandFlag.BORDER_VISIBLE, "true", IslandFlag.BORDER_COLOR, "blue"));
                }
                case "setFlag" -> {
                    calls.add("setFlag:" + args[2] + ":" + args[3]);
                    yield CompletableFuture.completedFuture(new EnvironmentActionView(true, "FLAG_SET", "BORDER_VISIBLE", 0L));
                }
                case "limits" -> {
                    calls.add("limits");
                    yield CompletableFuture.completedFuture(List.of(new IslandLimitSnapshot(uuid("00000000-0000-0000-0000-000000000070"), "HOPPER", 64L, null, Instant.EPOCH)));
                }
                case "limitViews" -> {
                    calls.add("limits");
                    yield CompletableFuture.completedFuture(List.of(new CoreGuiViews.LimitView("HOPPER", 64L, "")));
                }
                case "setLimit" -> {
                    calls.add("setLimit:" + args[2] + ":" + args[3]);
                    yield CompletableFuture.completedFuture(new EnvironmentActionView(true, "LIMIT_SET", "HOPPER", 64L));
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
