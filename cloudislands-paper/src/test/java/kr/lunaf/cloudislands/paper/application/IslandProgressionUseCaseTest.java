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

        assertEquals(7L, useCase.islandLevel(islandId).join().level());
        assertEquals("2000.00", useCase.blockDetailsView(islandId, 500).join().totalWorth());
        assertEquals("12.50", useCase.topWorthViews(500).join().getFirst().worth());
        assertEquals(7L, useCase.topLevelViews(0).join().getFirst().level());
        assertEquals(2L, useCase.topReviewViews(10).join().getFirst().reviewCount());
        assertEquals(8L, useCase.recalculateLevelView(islandId, actorUuid).join().level());
        assertEquals("generator:ore", useCase.upgradeViews(islandId).join().getFirst().key());
        assertEquals("UPGRADED", useCase.purchaseUpgradeResult(islandId, actorUuid, "generator", idempotentRunner(calls)).join().code());
        assertEquals("starter", useCase.missionViews(islandId, null).join().getFirst().key());
        assertEquals("Starter", useCase.completeMissionResult(islandId, actorUuid, "starter", "CHALLENGE", idempotentRunner(calls)).join().title());

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
                    yield CompletableFuture.completedFuture(islandInfoJson());
                }
                case "islandBlockDetails" -> {
                    calls.add("islandBlockDetails:" + args[1]);
                    yield CompletableFuture.completedFuture(blockDetailsJson());
                }
                case "topIslandsByWorth" -> {
                    calls.add("topIslandsByWorth:" + args[0]);
                    yield CompletableFuture.completedFuture(rankingsJson());
                }
                case "topIslandsByLevel" -> {
                    calls.add("topIslandsByLevel:" + args[0]);
                    yield CompletableFuture.completedFuture(rankingsJson());
                }
                case "topIslandsByReviews" -> {
                    calls.add("topIslandsByReviews:" + args[0]);
                    yield CompletableFuture.completedFuture(reviewRankingsJson());
                }
                case "recalculateIslandLevel" -> {
                    calls.add("recalculateIslandLevel");
                    yield CompletableFuture.completedFuture("{\"islandId\":\"00000000-0000-0000-0000-000000000080\",\"level\":8,\"worth\":\"14.00\",\"calculatedAt\":\"2026-01-02T03:04:05Z\"}");
                }
                case "listIslandUpgrades" -> {
                    calls.add("listIslandUpgrades");
                    yield CompletableFuture.completedFuture(upgradesJson());
                }
                case "purchaseIslandUpgrade" -> {
                    calls.add("purchaseIslandUpgrade:" + args[2]);
                    yield CompletableFuture.completedFuture("{\"accepted\":true,\"code\":\"UPGRADED\",\"cost\":\"10.00\",\"upgrade\":{\"islandId\":\"00000000-0000-0000-0000-000000000080\",\"upgradeKey\":\"generator:ore\",\"type\":\"GENERATOR\",\"level\":3,\"updatedAt\":\"2026-01-02T03:04:05Z\"}}");
                }
                case "listIslandMissions" -> {
                    calls.add("listIslandMissions:" + args[1]);
                    yield CompletableFuture.completedFuture(missionsJson());
                }
                case "completeIslandMission" -> {
                    calls.add("completeIslandMission:" + args[2] + ":" + args[3]);
                    yield CompletableFuture.completedFuture("{\"islandId\":\"00000000-0000-0000-0000-000000000080\",\"missionKey\":\"starter\",\"kind\":\"CHALLENGE\",\"title\":\"Starter\",\"progress\":2,\"goal\":2,\"completed\":true,\"reward\":\"10\"}");
                }
                default -> throw new UnsupportedOperationException(method.getName());
            });
    }

    private static IslandProgressionUseCase.IdempotentMutationRunner idempotentRunner(List<String> calls) {
        return new IslandProgressionUseCase.IdempotentMutationRunner() {
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

    private static String islandInfoJson() {
        return "{\"islandId\":\"00000000-0000-0000-0000-000000000080\",\"ownerUuid\":\"00000000-0000-0000-0000-000000000001\",\"name\":\"Base\",\"state\":\"ACTIVE\",\"size\":100,\"border\":100,\"level\":7,\"worth\":\"12.50\",\"publicAccess\":true}";
    }

    private static String blockDetailsJson() {
        return "{\"islandId\":\"00000000-0000-0000-0000-000000000080\",\"blocks\":[{\"materialKey\":\"minecraft:diamond_block\",\"count\":2,\"unitWorth\":\"1000.00\",\"totalWorth\":\"2000.00\",\"levelPoints\":20,\"limit\":5000}],\"summary\":{\"totalWorth\":\"2000.00\",\"totalLevelPoints\":20}}";
    }

    private static String rankingsJson() {
        return "{\"rankings\":[{\"islandId\":\"00000000-0000-0000-0000-000000000080\",\"name\":\"Base\",\"level\":7,\"worth\":\"12.50\"}]}";
    }

    private static String reviewRankingsJson() {
        return "{\"rankings\":[{\"islandId\":\"00000000-0000-0000-0000-000000000080\",\"averageRating\":4.50,\"reviewCount\":2,\"updatedAt\":\"2026-01-03T04:05:06Z\"}]}";
    }

    private static String upgradesJson() {
        return "{\"upgrades\":[{\"islandId\":\"00000000-0000-0000-0000-000000000080\",\"upgradeKey\":\"generator:ore\",\"type\":\"GENERATOR\",\"level\":3,\"updatedAt\":\"2026-01-02T03:04:05Z\"}]}";
    }

    private static String missionsJson() {
        return "{\"missions\":[{\"islandId\":\"00000000-0000-0000-0000-000000000080\",\"missionKey\":\"starter\",\"kind\":\"MISSION\",\"title\":\"Starter\",\"progress\":1,\"goal\":2,\"completed\":false,\"reward\":\"10\"}]}";
    }
}
