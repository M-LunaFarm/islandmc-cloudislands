package kr.lunaf.cloudislands.paper.application;

import static org.junit.jupiter.api.Assertions.assertEquals;

import java.lang.reflect.Proxy;
import java.util.ArrayList;
import java.util.List;
import java.util.UUID;
import java.util.concurrent.CompletableFuture;
import kr.lunaf.cloudislands.api.model.IslandLocation;
import kr.lunaf.cloudislands.coreclient.CoreApiClient;
import org.junit.jupiter.api.Test;

class IslandHomeWarpUseCaseTest {
    @Test
    void homeWarpOperationsRunBehindApplicationBoundary() {
        List<String> calls = new ArrayList<>();
        IslandHomeWarpUseCase useCase = new IslandHomeWarpUseCase(client(calls));
        UUID islandId = uuid("00000000-0000-0000-0000-000000000060");
        UUID actorUuid = uuid("00000000-0000-0000-0000-000000000001");
        IslandLocation location = new IslandLocation("world", 1.0d, 2.0d, 3.0d, 90.0f, 0.0f);

        assertEquals("home-set", useCase.setHome(islandId, actorUuid, "home", location, mutationRunner(calls)).join());
        assertEquals("HOME_SET", useCase.setHomeAction(islandId, actorUuid, "home", location, mutationRunner(calls)).join().code());
        assertEquals("warp-set", useCase.setWarp(islandId, actorUuid, "spawn", location, false, mutationRunner(calls)).join());
        assertEquals("WARP_SET", useCase.setWarpAction(islandId, actorUuid, "spawn", location, false, mutationRunner(calls)).join().code());
        assertEquals("homes", useCase.listHomes(islandId).join());
        assertEquals("warps", useCase.listWarps(islandId).join());
        assertEquals("info", useCase.islandInfo(islandId).join());
        assertEquals("deleted", useCase.deleteWarp(islandId, actorUuid, "spawn", idempotentMutationRunner(calls)).join());
        assertEquals("WARP_DELETED", useCase.deleteWarpAction(islandId, actorUuid, "spawn", idempotentMutationRunner(calls)).join().code());
        assertEquals("access", useCase.setWarpPublicAccess(islandId, actorUuid, "spawn", true, mutationRunner(calls)).join());
        assertEquals("WARP_PUBLIC", useCase.setWarpPublicAccessAction(islandId, actorUuid, "spawn", true, mutationRunner(calls)).join().code());
        assertEquals(publicWarpsJson(islandId), useCase.listPublicWarps(200, null, null).join());
        assertEquals("spawn", useCase.publicWarpViews(200, null, null).join().getFirst().name());

        assertEquals(List.of(
            "audit:island.home.set",
            "setIslandHomeResult:home",
            "audit:island.home.set",
            "setIslandHomeResult:home",
            "audit:island.warp.set",
            "setIslandWarpResult:spawn:false",
            "audit:island.warp.set",
            "setIslandWarpResult:spawn:false",
            "listIslandHomes",
            "listIslandWarps",
            "islandInfo",
            "audit:island.warp.delete",
            "deleteIslandWarpResult:spawn",
            "audit:island.warp.delete",
            "deleteIslandWarpResult:spawn",
            "audit:island.warp.public-access.set",
            "setIslandWarpPublicAccessResult:spawn:true",
            "audit:island.warp.public-access.set",
            "setIslandWarpPublicAccessResult:spawn:true",
            "listPublicWarps:100::",
            "listPublicWarps:100::"
        ), calls);
    }

    private static CoreApiClient client(List<String> calls) {
        return (CoreApiClient) Proxy.newProxyInstance(
            CoreApiClient.class.getClassLoader(),
            new Class<?>[] {CoreApiClient.class},
            (_proxy, method, args) -> switch (method.getName()) {
                case "setIslandHomeResult" -> {
                    calls.add("setIslandHomeResult:" + args[2]);
                    yield CompletableFuture.completedFuture("home-set");
                }
                case "setIslandWarpResult" -> {
                    calls.add("setIslandWarpResult:" + args[2] + ":" + args[4]);
                    yield CompletableFuture.completedFuture("warp-set");
                }
                case "listIslandHomes" -> {
                    calls.add("listIslandHomes");
                    yield CompletableFuture.completedFuture("homes");
                }
                case "listIslandWarps" -> {
                    calls.add("listIslandWarps");
                    yield CompletableFuture.completedFuture("warps");
                }
                case "islandInfo" -> {
                    calls.add("islandInfo");
                    yield CompletableFuture.completedFuture("info");
                }
                case "deleteIslandWarpResult" -> {
                    calls.add("deleteIslandWarpResult:" + args[2]);
                    yield CompletableFuture.completedFuture("deleted");
                }
                case "setIslandWarpPublicAccessResult" -> {
                    calls.add("setIslandWarpPublicAccessResult:" + args[2] + ":" + args[3]);
                    yield CompletableFuture.completedFuture("access");
                }
                case "listPublicWarps" -> {
                    calls.add("listPublicWarps:" + args[0] + ":" + args[1] + ":" + args[2]);
                    yield CompletableFuture.completedFuture(publicWarpsJson(UUID.fromString("00000000-0000-0000-0000-000000000060")));
                }
                default -> throw new UnsupportedOperationException(method.getName());
            });
    }

    private static IslandHomeWarpUseCase.MutationRunner mutationRunner(List<String> calls) {
        return (auditAction, operation) -> {
            calls.add("audit:" + auditAction);
            return operation.get();
        };
    }

    private static IslandHomeWarpUseCase.IdempotentMutationRunner idempotentMutationRunner(List<String> calls) {
        return (auditAction, operation) -> {
            calls.add("audit:" + auditAction);
            return operation.get();
        };
    }

    private static UUID uuid(String value) {
        return UUID.fromString(value);
    }

    private static String publicWarpsJson(UUID islandId) {
        return "{\"warps\":[{\"islandId\":\"" + islandId + "\",\"name\":\"spawn\",\"location\":{\"worldName\":\"world\",\"x\":1.0,\"y\":2.0,\"z\":3.0},\"publicAccess\":true,\"category\":\"market\"}]}";
    }
}
