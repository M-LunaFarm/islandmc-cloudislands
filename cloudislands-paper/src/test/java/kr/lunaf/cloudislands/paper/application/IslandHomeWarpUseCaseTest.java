package kr.lunaf.cloudislands.paper.application;

import static org.junit.jupiter.api.Assertions.assertEquals;

import java.lang.reflect.Proxy;
import java.util.ArrayList;
import java.util.List;
import java.util.UUID;
import java.util.concurrent.CompletableFuture;
import kr.lunaf.cloudislands.api.model.IslandLocation;
import kr.lunaf.cloudislands.coreclient.CoreApiClient;
import kr.lunaf.cloudislands.coreclient.CoreGuiViews;
import kr.lunaf.cloudislands.coreclient.HomeWarpActionView;
import kr.lunaf.cloudislands.coreclient.HomeWarpCommandClient;
import kr.lunaf.cloudislands.coreclient.HomeWarpQueryClient;
import org.junit.jupiter.api.Test;

class IslandHomeWarpUseCaseTest {
    @Test
    void homeWarpOperationsRunBehindApplicationBoundary() {
        List<String> calls = new ArrayList<>();
        IslandHomeWarpUseCase useCase = new IslandHomeWarpUseCase(client(calls));
        UUID islandId = uuid("00000000-0000-0000-0000-000000000060");
        UUID actorUuid = uuid("00000000-0000-0000-0000-000000000001");
        IslandLocation location = new IslandLocation("world", 1.0d, 2.0d, 3.0d, 90.0f, 0.0f);

        assertEquals("HOME_SET", useCase.setHomeAction(islandId, actorUuid, "home", location, mutationRunner(calls)).join().code());
        assertEquals("WARP_SET", useCase.setWarpAction(islandId, actorUuid, "spawn", location, false, mutationRunner(calls)).join().code());
        assertEquals("home", useCase.homeViews(islandId).join().getFirst().name());
        assertEquals("spawn", useCase.warpViews(islandId).join().getFirst().name());
        assertEquals("Island", useCase.islandInfoView(islandId).join().name());
        assertEquals("WARP_DELETED", useCase.deleteWarpAction(islandId, actorUuid, "spawn", idempotentMutationRunner(calls)).join().code());
        assertEquals("WARP_PUBLIC", useCase.setWarpPublicAccessAction(islandId, actorUuid, "spawn", true, mutationRunner(calls)).join().code());
        assertEquals("spawn", useCase.publicWarpViews(200, null, null).join().getFirst().name());

        assertEquals(List.of(
            "audit:island.home.set",
            "setIslandHomeResult:home",
            "audit:island.warp.set",
            "setIslandWarpResult:spawn:false",
            "listIslandHomes",
            "listIslandWarps",
            "islandInfo",
            "audit:island.warp.delete",
            "deleteIslandWarpResult:spawn",
            "audit:island.warp.public-access.set",
            "setIslandWarpPublicAccessResult:spawn:true",
            "listPublicWarps:100::"
        ), calls);
    }

    private static CoreApiClient client(List<String> calls) {
        return (CoreApiClient) Proxy.newProxyInstance(
            CoreApiClient.class.getClassLoader(),
	            new Class<?>[] {CoreApiClient.class, HomeWarpQueryClient.class, HomeWarpCommandClient.class},
	            (_proxy, method, args) -> switch (method.getName()) {
	                case "homeWarps" -> (HomeWarpQueryClient) _proxy;
	                case "homeWarpCommands" -> (HomeWarpCommandClient) _proxy;
	                case "setHome" -> {
                    calls.add("setIslandHomeResult:" + args[2]);
                    yield CompletableFuture.completedFuture(new HomeWarpActionView(true, "HOME_SET"));
                }
                case "setWarp" -> {
                    calls.add("setIslandWarpResult:" + args[2] + ":" + args[4]);
                    yield CompletableFuture.completedFuture(new HomeWarpActionView(true, "WARP_SET"));
                }
                case "homes" -> {
                    calls.add("listIslandHomes");
                    yield CompletableFuture.completedFuture(List.of(new CoreGuiViews.HomeView("", "home", 1.0D, 2.0D, 3.0D, "", "now")));
                }
                case "warps" -> {
                    calls.add("listIslandWarps");
                    yield CompletableFuture.completedFuture(List.of(new CoreGuiViews.WarpView("00000000-0000-0000-0000-000000000060", "spawn", 1.0D, 2.0D, 3.0D, true, "default")));
                }
                case "islandInfo" -> {
                    calls.add("islandInfo");
                    yield CompletableFuture.completedFuture(new CoreGuiViews.IslandInfoView("Island", "ACTIVE", "00000000-0000-0000-0000-000000000060", 3L, "12.5", true, false, 100L, 100L, "00000000-0000-0000-0000-000000000001"));
                }
                case "deleteWarp" -> {
                    calls.add("deleteIslandWarpResult:" + args[2]);
                    yield CompletableFuture.completedFuture(new HomeWarpActionView(true, "WARP_DELETED"));
                }
                case "setWarpPublicAccess" -> {
                    calls.add("setIslandWarpPublicAccessResult:" + args[2] + ":" + args[3]);
                    yield CompletableFuture.completedFuture(new HomeWarpActionView(true, args[3] instanceof Boolean value && value ? "WARP_PUBLIC" : "WARP_PRIVATE"));
                }
                case "publicWarps" -> {
                    int limit = Math.max(1, Math.min((int) args[0], 100));
                    calls.add("listPublicWarps:" + limit + ":" + (args[1] == null ? "" : args[1]) + ":" + (args[2] == null ? "" : args[2]));
                    yield CompletableFuture.completedFuture(List.of(new CoreGuiViews.WarpView("00000000-0000-0000-0000-000000000060", "spawn", 1.0D, 2.0D, 3.0D, true, "market")));
                }
                default -> throw new UnsupportedOperationException(method.getName());
            });
    }

    private static IslandHomeWarpUseCase.MutationRunner mutationRunner(List<String> calls) {
        return new IslandHomeWarpUseCase.MutationRunner() {
            @Override
            public <T> CompletableFuture<T> mutate(String auditAction, java.util.function.Supplier<CompletableFuture<T>> operation) {
                calls.add("audit:" + auditAction);
                return operation.get();
            }
        };
    }

    private static IslandHomeWarpUseCase.IdempotentMutationRunner idempotentMutationRunner(List<String> calls) {
        return new IslandHomeWarpUseCase.IdempotentMutationRunner() {
            @Override
            public <T> CompletableFuture<T> mutateIdempotent(String auditAction, java.util.function.Supplier<CompletableFuture<T>> operation) {
                calls.add("audit:" + auditAction);
                return operation.get();
            }
        };
    }

    private static UUID uuid(String value) {
        return UUID.fromString(value);
    }

    private static String publicWarpsJson(UUID islandId) {
        return "{\"warps\":[{\"islandId\":\"" + islandId + "\",\"name\":\"spawn\",\"location\":{\"worldName\":\"world\",\"x\":1.0,\"y\":2.0,\"z\":3.0},\"publicAccess\":true,\"category\":\"market\"}]}";
    }

    private static String homesJson() {
        return "{\"homes\":[{\"name\":\"home\",\"location\":{\"worldName\":\"world\",\"x\":1.0,\"y\":2.0,\"z\":3.0},\"createdAt\":\"now\"}]}";
    }

    private static String warpsJson(UUID islandId) {
        return "{\"warps\":[{\"islandId\":\"" + islandId + "\",\"name\":\"spawn\",\"location\":{\"worldName\":\"world\",\"x\":1.0,\"y\":2.0,\"z\":3.0},\"publicAccess\":true,\"category\":\"default\"}]}";
    }

    private static String infoJson(UUID islandId) {
        return "{\"islandId\":\"" + islandId + "\",\"name\":\"Island\",\"state\":\"ACTIVE\",\"level\":3,\"worth\":\"12.5\",\"publicAccess\":true,\"locked\":false,\"size\":100,\"border\":100,\"ownerUuid\":\"00000000-0000-0000-0000-000000000001\"}";
    }
}
