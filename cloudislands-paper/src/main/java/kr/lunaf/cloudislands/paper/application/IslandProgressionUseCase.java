package kr.lunaf.cloudislands.paper.application;

import java.util.List;
import java.util.Map;
import java.util.UUID;
import java.util.concurrent.CompletableFuture;
import java.util.function.Supplier;
import kr.lunaf.cloudislands.coreclient.CoreApiClient;
import kr.lunaf.cloudislands.coreclient.CoreGuiViews;
import kr.lunaf.cloudislands.coreclient.CoreProgressionCommandClient;
import kr.lunaf.cloudislands.coreclient.CoreProgressionQueryClient;
import kr.lunaf.cloudislands.coreclient.ProgressionCommandClient;
import kr.lunaf.cloudislands.coreclient.ProgressionQueryClient;
import kr.lunaf.cloudislands.paper.application.view.PaperGuiViews.MissionView;
import kr.lunaf.cloudislands.paper.application.view.PaperGuiViews.UpgradeView;

public final class IslandProgressionUseCase {
    private final ProgressionQueryClient progressionQueries;
    private final ProgressionCommandClient progressionCommands;

    public IslandProgressionUseCase(CoreApiClient coreApiClient) {
        if (coreApiClient == null) {
            throw new IllegalArgumentException("coreApiClient is required");
        }
        this.progressionQueries = new CoreProgressionQueryClient(coreApiClient);
        this.progressionCommands = new CoreProgressionCommandClient(coreApiClient);
    }

    IslandProgressionUseCase(CoreApiClient coreApiClient, ProgressionQueryClient progressionQueries) {
        this(coreApiClient, progressionQueries, new CoreProgressionCommandClient(coreApiClient));
    }

    IslandProgressionUseCase(CoreApiClient coreApiClient, ProgressionQueryClient progressionQueries, ProgressionCommandClient progressionCommands) {
        if (coreApiClient == null) {
            throw new IllegalArgumentException("coreApiClient is required");
        }
        if (progressionQueries == null) {
            throw new IllegalArgumentException("progressionQueries is required");
        }
        if (progressionCommands == null) {
            throw new IllegalArgumentException("progressionCommands is required");
        }
        this.progressionQueries = progressionQueries;
        this.progressionCommands = progressionCommands;
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

    private CompletableFuture<IslandLevelView> recalculateLevelResult(UUID islandId, UUID actorUuid) {
        requireIsland(islandId);
        requireActor(actorUuid);
        return progressionCommands.recalculateLevel(islandId, actorUuid).thenApply(IslandProgressionUseCase::levelView);
    }

    public CompletableFuture<IslandLevelView> recalculateLevelView(UUID islandId, UUID actorUuid) {
        return recalculateLevelResult(islandId, actorUuid);
    }

    public CompletableFuture<List<UpgradeView>> upgradeViews(UUID islandId) {
        requireIsland(islandId);
        return progressionQueries.upgrades(islandId).thenApply(views -> views.stream().map(IslandProgressionUseCase::upgradeView).toList());
    }

    private CompletableFuture<UpgradePurchaseResult> purchaseUpgradeBody(UUID islandId, UUID actorUuid, String upgradeKey, IdempotentMutationRunner runner) {
        requireIsland(islandId);
        requireActor(actorUuid);
        requireIdempotentRunner(runner);
        return runner.mutateIdempotent("island.upgrade.purchase", () -> progressionCommands.purchaseUpgrade(islandId, actorUuid, upgradeKey))
            .thenApply(result -> new UpgradePurchaseResult(result.accepted(), result.code(), result.upgradeKey(), result.level(), result.cost()));
    }

    public CompletableFuture<UpgradePurchaseResult> purchaseUpgradeResult(UUID islandId, UUID actorUuid, String upgradeKey, IdempotentMutationRunner runner) {
        return purchaseUpgradeBody(islandId, actorUuid, upgradeKey, runner);
    }

    public CompletableFuture<List<MissionView>> missionViews(UUID islandId, String kind) {
        requireIsland(islandId);
        return progressionQueries.missions(islandId, normalizeKind(kind)).thenApply(views -> views.stream().map(IslandProgressionUseCase::missionView).toList());
    }

    private CompletableFuture<MissionCompletionResult> completeMissionBody(UUID islandId, UUID actorUuid, String missionKey, String kind, IdempotentMutationRunner runner) {
        requireIsland(islandId);
        requireActor(actorUuid);
        requireIdempotentRunner(runner);
        return runner.mutateIdempotent("island.mission.complete", () -> progressionCommands.completeMission(islandId, actorUuid, missionKey, normalizeKind(kind)))
            .thenApply(result -> new MissionCompletionResult(result.accepted(), result.code(), result.missionKey(), result.title(), result.reward()));
    }

    public CompletableFuture<MissionCompletionResult> completeMissionResult(UUID islandId, UUID actorUuid, String missionKey, String kind, IdempotentMutationRunner runner) {
        return completeMissionBody(islandId, actorUuid, missionKey, kind, runner);
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
        <T> CompletableFuture<T> mutateIdempotent(String auditAction, Supplier<CompletableFuture<T>> operation);
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
