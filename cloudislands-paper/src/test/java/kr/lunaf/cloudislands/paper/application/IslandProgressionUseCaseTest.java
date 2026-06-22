package kr.lunaf.cloudislands.paper.application;

import static org.junit.jupiter.api.Assertions.assertEquals;

import java.lang.reflect.Proxy;
import java.util.ArrayList;
import java.util.List;
import java.util.UUID;
import java.util.concurrent.CompletableFuture;
import kr.lunaf.cloudislands.coreclient.CoreApiClient;
import kr.lunaf.cloudislands.coreclient.CoreGuiViews;
import kr.lunaf.cloudislands.coreclient.LevelView;
import kr.lunaf.cloudislands.coreclient.ProgressionBlockDetailView;
import kr.lunaf.cloudislands.coreclient.ProgressionBlockDetailsView;
import kr.lunaf.cloudislands.coreclient.ProgressionCommandClient;
import kr.lunaf.cloudislands.coreclient.ProgressionMissionCompletionView;
import kr.lunaf.cloudislands.coreclient.ProgressionQueryClient;
import kr.lunaf.cloudislands.coreclient.ProgressionRankingEntryView;
import kr.lunaf.cloudislands.coreclient.ProgressionReviewRankingEntryView;
import kr.lunaf.cloudislands.coreclient.ProgressionUpgradePurchaseView;
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
            "islandBlockDetails:500",
            "topWorth:500",
            "topLevel:0",
            "topReviews:10",
            "recalculateLevel",
            "listIslandUpgrades",
            "audit:island.upgrade.purchase",
            "purchaseUpgrade:generator",
            "listIslandMissions:MISSION",
            "audit:island.mission.complete",
            "completeMission:starter:CHALLENGE"
        ), calls);
    }

    private static CoreApiClient client(List<String> calls) {
        return (CoreApiClient) Proxy.newProxyInstance(
            CoreApiClient.class.getClassLoader(),
            new Class<?>[] {CoreApiClient.class, ProgressionQueryClient.class, ProgressionCommandClient.class},
            (_proxy, method, args) -> switch (method.getName()) {
                case "progression" -> (ProgressionQueryClient) _proxy;
                case "progressionCommands" -> (ProgressionCommandClient) _proxy;
                case "islandInfo" -> {
                    calls.add("islandInfo");
                    yield CompletableFuture.completedFuture(new CoreGuiViews.IslandInfoView("Base", "ACTIVE", "00000000-0000-0000-0000-000000000080", 7L, "12.50", true, false, 100L, 100L, "00000000-0000-0000-0000-000000000001", "", ""));
                }
                case "blockDetails" -> {
                    calls.add("islandBlockDetails:" + args[1]);
                    yield CompletableFuture.completedFuture(new ProgressionBlockDetailsView("2000.00", 20L, List.of(new ProgressionBlockDetailView("minecraft:diamond_block", 2L, "2000.00", 20L))));
                }
                case "topWorth" -> {
                    calls.add("topWorth:" + args[0]);
                    yield CompletableFuture.completedFuture(List.of(new ProgressionRankingEntryView("00000000-0000-0000-0000-000000000080", "Base", 7L, "12.50", "worth")));
                }
                case "topLevel" -> {
                    calls.add("topLevel:" + args[0]);
                    yield CompletableFuture.completedFuture(List.of(new ProgressionRankingEntryView("00000000-0000-0000-0000-000000000080", "Base", 7L, "12.50", "level")));
                }
                case "topReviews" -> {
                    calls.add("topReviews:" + args[0]);
                    yield CompletableFuture.completedFuture(List.of(new ProgressionReviewRankingEntryView("00000000-0000-0000-0000-000000000080", 4.5D, 2L)));
                }
                case "recalculateLevel" -> {
                    calls.add("recalculateLevel");
                    yield CompletableFuture.completedFuture(new LevelView("00000000-0000-0000-0000-000000000080", 8L, "14.00", "2026-01-02T03:04:05Z"));
                }
                case "upgrades" -> {
                    calls.add("listIslandUpgrades");
                    yield CompletableFuture.completedFuture(List.of(new CoreGuiViews.UpgradeView("generator:ore", "GENERATOR", 3, "")));
                }
                case "purchaseUpgrade" -> {
                    calls.add("purchaseUpgrade:" + args[2]);
                    yield CompletableFuture.completedFuture(new ProgressionUpgradePurchaseView(true, "UPGRADED", "00000000-0000-0000-0000-000000000080", "generator:ore", "GENERATOR", 3L, "10.00", "2026-01-02T03:04:05Z"));
                }
                case "missions" -> {
                    calls.add("listIslandMissions:" + args[1]);
                    yield CompletableFuture.completedFuture(List.of(new CoreGuiViews.MissionView("starter", "Starter", 1L, 2L, false, "10")));
                }
                case "completeMission" -> {
                    calls.add("completeMission:" + args[2] + ":" + args[3]);
                    yield CompletableFuture.completedFuture(new ProgressionMissionCompletionView(true, "MISSION_COMPLETED", "00000000-0000-0000-0000-000000000080", "starter", "CHALLENGE", "Starter", 2L, 2L, true, "10", ""));
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
