package kr.lunaf.cloudislands.paper.application;

import java.util.List;
import java.util.Map;
import java.util.UUID;
import java.util.concurrent.CompletableFuture;
import java.util.function.Supplier;
import kr.lunaf.cloudislands.common.json.SimpleJson;
import kr.lunaf.cloudislands.coreclient.CoreGuiViews;
import kr.lunaf.cloudislands.coreclient.CoreApiClient;
import kr.lunaf.cloudislands.paper.application.view.PaperGuiViews;
import kr.lunaf.cloudislands.paper.application.view.PaperGuiViews.MissionView;
import kr.lunaf.cloudislands.paper.application.view.PaperGuiViews.UpgradeView;

public final class IslandProgressionUseCase {
    private final CoreApiClient coreApiClient;

    public IslandProgressionUseCase(CoreApiClient coreApiClient) {
        if (coreApiClient == null) {
            throw new IllegalArgumentException("coreApiClient is required");
        }
        this.coreApiClient = coreApiClient;
    }

    private CompletableFuture<String> islandInfoBody(UUID islandId) {
        requireIsland(islandId);
        return coreApiClient.islandInfo(islandId);
    }

    public CompletableFuture<IslandLevelView> islandLevel(UUID islandId) {
        return islandInfoBody(islandId).thenApply(IslandProgressionUseCase::levelView);
    }

    private CompletableFuture<String> blockDetailsBody(UUID islandId, int limit) {
        requireIsland(islandId);
        return coreApiClient.islandBlockDetails(islandId, boundedLimit(limit));
    }

    public CompletableFuture<BlockDetailsView> blockDetailsView(UUID islandId, int limit) {
        return blockDetailsBody(islandId, limit).thenApply(IslandProgressionUseCase::blockDetailsView);
    }

    private CompletableFuture<String> topIslandsByWorthBody(int limit) {
        return coreApiClient.topIslandsByWorth(boundedLimit(limit));
    }

    public CompletableFuture<List<RankingEntryView>> topWorthViews(int limit) {
        return topIslandsByWorthBody(limit).thenApply(body -> rankingViews(body, "worth"));
    }

    private CompletableFuture<String> topIslandsByLevelBody(int limit) {
        return coreApiClient.topIslandsByLevel(boundedLimit(limit));
    }

    public CompletableFuture<List<RankingEntryView>> topLevelViews(int limit) {
        return topIslandsByLevelBody(limit).thenApply(body -> rankingViews(body, "level"));
    }

    private CompletableFuture<String> topIslandsByReviewsBody(int limit) {
        return coreApiClient.topIslandsByReviews(boundedLimit(limit));
    }

    public CompletableFuture<List<ReviewRankingEntryView>> topReviewViews(int limit) {
        return topIslandsByReviewsBody(limit).thenApply(IslandProgressionUseCase::reviewRankingViews);
    }

    private CompletableFuture<String> recalculateLevelBody(UUID islandId, UUID actorUuid) {
        requireIsland(islandId);
        requireActor(actorUuid);
        return coreApiClient.recalculateIslandLevel(islandId, actorUuid);
    }

    public CompletableFuture<IslandLevelView> recalculateLevelView(UUID islandId, UUID actorUuid) {
        return recalculateLevelBody(islandId, actorUuid).thenApply(IslandProgressionUseCase::levelView);
    }

    public CompletableFuture<List<UpgradeView>> upgradeViews(UUID islandId) {
        requireIsland(islandId);
        return PaperGuiViews.islandUpgrades(coreApiClient, islandId);
    }

    private CompletableFuture<String> purchaseUpgradeBody(UUID islandId, UUID actorUuid, String upgradeKey, IdempotentMutationRunner runner) {
        requireIsland(islandId);
        requireActor(actorUuid);
        requireIdempotentRunner(runner);
        return runner.mutateIdempotent("island.upgrade.purchase", () -> coreApiClient.purchaseIslandUpgrade(islandId, actorUuid, upgradeKey == null ? "" : upgradeKey));
    }

    public CompletableFuture<UpgradePurchaseResult> purchaseUpgradeResult(UUID islandId, UUID actorUuid, String upgradeKey, IdempotentMutationRunner runner) {
        return purchaseUpgradeBody(islandId, actorUuid, upgradeKey, runner)
            .thenApply(body -> upgradePurchaseResult(body, upgradeKey));
    }

    public CompletableFuture<List<MissionView>> missionViews(UUID islandId, String kind) {
        requireIsland(islandId);
        return PaperGuiViews.islandMissions(coreApiClient, islandId, normalizeKind(kind));
    }

    private CompletableFuture<String> completeMissionBody(UUID islandId, UUID actorUuid, String missionKey, String kind, IdempotentMutationRunner runner) {
        requireIsland(islandId);
        requireActor(actorUuid);
        requireIdempotentRunner(runner);
        return runner.mutateIdempotent("island.mission.complete", () -> coreApiClient.completeIslandMission(islandId, actorUuid, missionKey == null ? "" : missionKey, normalizeKind(kind)));
    }

    public CompletableFuture<MissionCompletionResult> completeMissionResult(UUID islandId, UUID actorUuid, String missionKey, String kind, IdempotentMutationRunner runner) {
        return completeMissionBody(islandId, actorUuid, missionKey, kind, runner)
            .thenApply(body -> missionCompletionResult(body, missionKey));
    }

    private static IslandLevelView levelView(String body) {
        CoreGuiViews.IslandInfoView info = CoreGuiViews.islandInfoView(body);
        return new IslandLevelView(info.level(), info.worth().isBlank() ? "0" : info.worth());
    }

    private static BlockDetailsView blockDetailsView(String body) {
        Map<?, ?> root = SimpleJson.object(SimpleJson.parse(body));
        Map<?, ?> summary = SimpleJson.object(root.get("summary"));
        List<BlockDetailView> blocks = SimpleJson.list(root.get("blocks")).stream()
            .map(SimpleJson::object)
            .map(block -> new BlockDetailView(
                text(block, "materialKey"),
                SimpleJson.number(block.get("count")),
                text(block, "totalWorth"),
                SimpleJson.number(block.get("levelPoints"))
            ))
            .filter(block -> !block.materialKey().isBlank())
            .toList();
        return new BlockDetailsView(
            text(summary, "totalWorth"),
            SimpleJson.number(summary.get("totalLevelPoints")),
            blocks
        );
    }

    private static List<RankingEntryView> rankingViews(String body, String valueKey) {
        return entries(body).stream()
            .map(object -> new RankingEntryView(
                text(object, "islandId"),
                text(object, "name"),
                SimpleJson.number(object.get("level")),
                text(object, "worth"),
                valueKey
            ))
            .filter(entry -> !entry.islandId().isBlank())
            .toList();
    }

    private static List<ReviewRankingEntryView> reviewRankingViews(String body) {
        return entries(body).stream()
            .map(object -> new ReviewRankingEntryView(
                text(object, "islandId"),
                doubleValue(object.get("averageRating")),
                SimpleJson.number(object.get("reviewCount"))
            ))
            .filter(entry -> !entry.islandId().isBlank())
            .toList();
    }

    private static UpgradePurchaseResult upgradePurchaseResult(String body, String fallbackKey) {
        Map<?, ?> root = SimpleJson.object(SimpleJson.parse(body));
        Map<?, ?> upgrade = SimpleJson.object(root.get("upgrade"));
        boolean accepted = accepted(root);
        String code = text(root, "code");
        String upgradeKey = text(upgrade, "upgradeKey");
        if (upgradeKey.isBlank()) {
            upgradeKey = text(root, "upgradeKey");
        }
        if (upgradeKey.isBlank()) {
            upgradeKey = fallbackKey == null ? "" : fallbackKey;
        }
        return new UpgradePurchaseResult(accepted, code, upgradeKey, SimpleJson.number(upgrade.get("level")), text(root, "cost"));
    }

    private static MissionCompletionResult missionCompletionResult(String body, String fallbackKey) {
        Map<?, ?> root = SimpleJson.object(SimpleJson.parse(body));
        boolean accepted = accepted(root);
        String code = text(root, "code");
        return new MissionCompletionResult(
            accepted,
            code,
            text(root, "missionKey").isBlank() ? fallbackKey : text(root, "missionKey"),
            text(root, "title"),
            text(root, "reward")
        );
    }

    private static boolean accepted(Map<?, ?> root) {
        return !root.containsKey("error")
            && !Boolean.FALSE.equals(root.get("accepted"))
            && !Boolean.FALSE.equals(root.get("applied"));
    }

    private static List<Map<?, ?>> entries(String body) {
        Object parsed = SimpleJson.parse(body);
        if (parsed instanceof List<?>) {
            return SimpleJson.list(parsed).stream()
                .map(SimpleJson::object)
                .filter(map -> !map.isEmpty())
                .toList();
        }
        Map<?, ?> root = SimpleJson.object(parsed);
        for (Object value : root.values()) {
            if (value instanceof List<?>) {
                return SimpleJson.list(value).stream()
                    .map(SimpleJson::object)
                    .filter(map -> !map.isEmpty())
                    .toList();
            }
        }
        return root.isEmpty() ? List.of() : List.of(root);
    }

    private static String text(Map<?, ?> object, String key) {
        return SimpleJson.text(object.get(key));
    }

    private static double doubleValue(Object value) {
        if (value instanceof Number number) {
            return number.doubleValue();
        }
        if (value instanceof String text) {
            try {
                return Double.parseDouble(text);
            } catch (NumberFormatException ignored) {
                return 0D;
            }
        }
        return 0D;
    }

    private static int boundedLimit(int limit) {
        return Math.max(1, Math.min(limit, 100));
    }

    private static String normalizeKind(String kind) {
        return kind == null || kind.isBlank() ? "MISSION" : kind;
    }

    private static void requireIsland(UUID islandId) {
        if (islandId == null) {
            throw new IllegalArgumentException("islandId is required");
        }
    }

    private static void requireActor(UUID actorUuid) {
        if (actorUuid == null) {
            throw new IllegalArgumentException("actorUuid is required");
        }
    }

    private static void requireIdempotentRunner(IdempotentMutationRunner runner) {
        if (runner == null) {
            throw new IllegalArgumentException("runner is required");
        }
    }

    @FunctionalInterface
    public interface IdempotentMutationRunner {
        CompletableFuture<String> mutateIdempotent(String auditAction, Supplier<CompletableFuture<String>> operation);
    }

    public record IslandLevelView(long level, String worth) {
        public IslandLevelView {
            worth = worth == null || worth.isBlank() ? "0" : worth;
        }
    }

    public record BlockDetailsView(String totalWorth, long totalLevelPoints, List<BlockDetailView> blocks) {
        public BlockDetailsView {
            totalWorth = totalWorth == null || totalWorth.isBlank() ? "0" : totalWorth;
            blocks = blocks == null ? List.of() : List.copyOf(blocks);
        }
    }

    public record BlockDetailView(String materialKey, long count, String totalWorth, long levelPoints) {
        public BlockDetailView {
            materialKey = materialKey == null ? "" : materialKey;
            totalWorth = totalWorth == null || totalWorth.isBlank() ? "0" : totalWorth;
        }
    }

    public record RankingEntryView(String islandId, String name, long level, String worth, String valueKey) {
        public RankingEntryView {
            islandId = islandId == null ? "" : islandId;
            name = name == null || name.isBlank() ? "이름 없는 섬" : name;
            worth = worth == null || worth.isBlank() ? "0" : worth;
            valueKey = valueKey == null ? "" : valueKey;
        }
    }

    public record ReviewRankingEntryView(String islandId, double averageRating, long reviewCount) {
        public ReviewRankingEntryView {
            islandId = islandId == null ? "" : islandId;
        }
    }

    public record UpgradePurchaseResult(boolean accepted, String code, String upgradeKey, long level, String cost) {
        public UpgradePurchaseResult {
            code = code == null ? "" : code;
            upgradeKey = upgradeKey == null ? "" : upgradeKey;
            cost = cost == null || cost.isBlank() ? "0" : cost;
        }
    }

    public record MissionCompletionResult(boolean accepted, String code, String missionKey, String title, String reward) {
        public MissionCompletionResult {
            code = code == null ? "" : code;
            missionKey = missionKey == null ? "" : missionKey;
            title = title == null ? "" : title;
            reward = reward == null ? "" : reward;
        }
    }
}
