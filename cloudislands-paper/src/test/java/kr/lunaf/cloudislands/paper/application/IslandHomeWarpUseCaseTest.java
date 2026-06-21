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
                    yield CompletableFuture.completedFuture(homesJson());
                }
                case "listIslandWarps" -> {
                    calls.add("listIslandWarps");
                    yield CompletableFuture.completedFuture(warpsJson(UUID.fromString("00000000-0000-0000-0000-000000000060")));
                }
                case "islandInfo" -> {
                    calls.add("islandInfo");
                    yield CompletableFuture.completedFuture(infoJson(UUID.fromString("00000000-0000-0000-0000-000000000060")));
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
