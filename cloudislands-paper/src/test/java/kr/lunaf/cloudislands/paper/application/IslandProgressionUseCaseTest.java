package kr.lunaf.cloudislands.paper.application;

import static org.junit.jupiter.api.Assertions.assertEquals;

import java.lang.reflect.Proxy;
import java.util.ArrayList;
import java.util.List;
import java.util.UUID;
import java.util.concurrent.CompletableFuture;
import kr.lunaf.cloudislands.coreclient.CoreApiClient;
import org.junit.jupiter.api.Test;

class IslandProgressionUseCaseTest {
    @Test
    void progressionOperationsRunBehindApplicationBoundary() {
        List<String> calls = new ArrayList<>();
        IslandProgressionUseCase useCase = new IslandProgressionUseCase(client(calls));
        UUID islandId = uuid("00000000-0000-0000-0000-000000000080");
        UUID actorUuid = uuid("00000000-0000-0000-0000-000000000001");

        assertEquals("info", useCase.islandInfo(islandId).join());
        assertEquals("blocks", useCase.blockDetails(islandId, 500).join());
        assertEquals("worth", useCase.topIslandsByWorth(500).join());
        assertEquals("level", useCase.topIslandsByLevel(0).join());
        assertEquals("reviews", useCase.topIslandsByReviews(10).join());
        assertEquals("recalculated", useCase.recalculateLevel(islandId, actorUuid).join());
        assertEquals("upgrades", useCase.listUpgrades(islandId).join());
        assertEquals("purchased", useCase.purchaseUpgrade(islandId, actorUuid, "generator", idempotentRunner(calls)).join());
        assertEquals("missions", useCase.listMissions(islandId, null).join());
        assertEquals("completed", useCase.completeMission(islandId, actorUuid, "starter", "CHALLENGE", idempotentRunner(calls)).join());

        assertEquals(List.of(
            "islandInfo",
            "islandBlockDetails:100",
            "topIslandsByWorth:100",
            "topIslandsByLevel:1",
            "topIslandsByReviews:10",
            "recalculateIslandLevel",
            "listIslandUpgrades",
            "audit:island.upgrade.purchase",
            "purchaseIslandUpgrade:generator",
            "listIslandMissions:MISSION",
            "audit:island.mission.complete",
            "completeIslandMission:starter:CHALLENGE"
        ), calls);
    }

    private static CoreApiClient client(List<String> calls) {
        return (CoreApiClient) Proxy.newProxyInstance(
            CoreApiClient.class.getClassLoader(),
            new Class<?>[] {CoreApiClient.class},
            (_proxy, method, args) -> switch (method.getName()) {
                case "islandInfo" -> {
                    calls.add("islandInfo");
                    yield CompletableFuture.completedFuture("info");
                }
                case "islandBlockDetails" -> {
                    calls.add("islandBlockDetails:" + args[1]);
                    yield CompletableFuture.completedFuture("blocks");
                }
                case "topIslandsByWorth" -> {
                    calls.add("topIslandsByWorth:" + args[0]);
                    yield CompletableFuture.completedFuture("worth");
                }
                case "topIslandsByLevel" -> {
                    calls.add("topIslandsByLevel:" + args[0]);
                    yield CompletableFuture.completedFuture("level");
                }
                case "topIslandsByReviews" -> {
                    calls.add("topIslandsByReviews:" + args[0]);
                    yield CompletableFuture.completedFuture("reviews");
                }
                case "recalculateIslandLevel" -> {
                    calls.add("recalculateIslandLevel");
                    yield CompletableFuture.completedFuture("recalculated");
                }
                case "listIslandUpgrades" -> {
                    calls.add("listIslandUpgrades");
                    yield CompletableFuture.completedFuture("upgrades");
                }
                case "purchaseIslandUpgrade" -> {
                    calls.add("purchaseIslandUpgrade:" + args[2]);
                    yield CompletableFuture.completedFuture("purchased");
                }
                case "listIslandMissions" -> {
                    calls.add("listIslandMissions:" + args[1]);
                    yield CompletableFuture.completedFuture("missions");
                }
                case "completeIslandMission" -> {
                    calls.add("completeIslandMission:" + args[2] + ":" + args[3]);
                    yield CompletableFuture.completedFuture("completed");
                }
                default -> throw new UnsupportedOperationException(method.getName());
            });
    }

    private static IslandProgressionUseCase.IdempotentMutationRunner idempotentRunner(List<String> calls) {
        return (auditAction, operation) -> {
            calls.add("audit:" + auditAction);
            return operation.get();
        };
    }

    private static UUID uuid(String value) {
        return UUID.fromString(value);
    }
}
