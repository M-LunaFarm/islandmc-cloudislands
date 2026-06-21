package kr.lunaf.cloudislands.paper.application;

import java.util.List;
import java.util.Map;
import java.util.UUID;
import java.util.concurrent.CompletableFuture;
import java.util.function.Supplier;
import kr.lunaf.cloudislands.common.json.SimpleJson;
import kr.lunaf.cloudislands.coreclient.CoreApiClient;
import kr.lunaf.cloudislands.coreclient.CoreGuiViews;
import kr.lunaf.cloudislands.coreclient.CoreProgressionQueryClient;
import kr.lunaf.cloudislands.coreclient.ProgressionQueryClient;
import kr.lunaf.cloudislands.paper.application.view.PaperGuiViews.MissionView;
import kr.lunaf.cloudislands.paper.application.view.PaperGuiViews.UpgradeView;

public final class IslandProgressionUseCase {
    private final CoreApiClient coreApiClient;
    private final ProgressionQueryClient progressionQueries;

    public IslandProgressionUseCase(CoreApiClient coreApiClient) {
        if (coreApiClient == null) {
            throw new IllegalArgumentException("coreApiClient is required");
        }
        this.coreApiClient = coreApiClient;
        this.progressionQueries = new CoreProgressionQueryClient(coreApiClient);
    }

    IslandProgressionUseCase(CoreApiClient coreApiClient, ProgressionQueryClient progressionQueries) {
        if (coreApiClient == null) {
            throw new IllegalArgumentException("coreApiClient is required");
        }
        if (progressionQueries == null) {
            throw new IllegalArgumentException("progressionQueries is required");
        }
        this.coreApiClient = coreApiClient;
        this.progressionQueries = progressionQueries;
    }

    public CompletableFuture<IslandLevelView> islandLevel(UUID islandId) {
        requireIsland(islandId);
        return progressionQueries.islandInfo(islandId).thenApply(IslandProgressionUseCase::levelView);
    }

    public CompletableFuture<BlockDetailsView> blockDetailsView(UUID islandId, int limit) {
        requireIsland(islandId);
        return progressionQueries.blockDetails(islandId, limit).thenApply(IslandProgressionUseCase::blockDetailsView);
    }

    public CompletableFuture<List<RankingEntryView>> topWorthViews(int limit) {
        return progressionQueries.topWorth(limit).thenApply(views -> views.stream().map(IslandProgressionUseCase::rankingEntryView).toList());
    }

    public CompletableFuture<List<RankingEntryView>> topLevelViews(int limit) {
        return progressionQueries.topLevel(limit).thenApply(views -> views.stream().map(IslandProgressionUseCase::rankingEntryView).toList());
    }

    public CompletableFuture<List<ReviewRankingEntryView>> topReviewViews(int limit) {
        return progressionQueries.topReviews(limit).thenApply(views -> views.stream().map(IslandProgressionUseCase::reviewRankingEntryView).toList());
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
        return progressionQueries.upgrades(islandId).thenApply(views -> views.stream().map(IslandProgressionUseCase::upgradeView).toList());
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
        return progressionQueries.missions(islandId, normalizeKind(kind)).thenApply(views -> views.stream().map(IslandProgressionUseCase::missionView).toList());
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
        return levelView(info);
    }

    private static IslandLevelView levelView(CoreGuiViews.IslandInfoView info) {
        return new IslandLevelView(info.level(), info.worth().isBlank() ? "0" : info.worth());
    }

    private static BlockDetailsView blockDetailsView(kr.lunaf.cloudislands.coreclient.ProgressionBlockDetailsView view) {
        return new BlockDetailsView(
            view.totalWorth(),
            view.totalLevelPoints(),
            view.blocks().stream()
                .map(block -> new BlockDetailView(block.materialKey(), block.count(), block.totalWorth(), block.levelPoints()))
                .toList()
        );
    }

    private static RankingEntryView rankingEntryView(kr.lunaf.cloudislands.coreclient.ProgressionRankingEntryView view) {
        return new RankingEntryView(view.islandId(), view.name(), view.level(), view.worth(), view.valueKey());
    }

    private static ReviewRankingEntryView reviewRankingEntryView(kr.lunaf.cloudislands.coreclient.ProgressionReviewRankingEntryView view) {
        return new ReviewRankingEntryView(view.islandId(), view.averageRating(), view.reviewCount());
    }

    private static UpgradeView upgradeView(CoreGuiViews.UpgradeView view) {
        return new UpgradeView(view.key(), view.type(), view.level());
    }

    private static MissionView missionView(CoreGuiViews.MissionView view) {
        return new MissionView(view.key(), view.title(), view.progress(), view.goal(), view.completed(), view.reward());
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
