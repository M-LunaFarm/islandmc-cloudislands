package kr.lunaf.cloudislands.coreclient;

import java.util.List;
import java.util.UUID;
import java.util.concurrent.CompletableFuture;

public interface ProgressionQueryClient {
    CompletableFuture<CoreGuiViews.IslandInfoView> islandInfo(UUID islandId);

    CompletableFuture<LevelView> level(UUID islandId);

    CompletableFuture<ProgressionBlockDetailsView> blockDetails(UUID islandId, int limit);

    CompletableFuture<CoreGuiViews.RankingData> rankings(int limit);

    CompletableFuture<List<ProgressionRankingEntryView>> topWorth(int limit);

    CompletableFuture<List<ProgressionRankingEntryView>> topLevel(int limit);

    CompletableFuture<List<ProgressionReviewRankingEntryView>> topReviews(int limit);

    CompletableFuture<List<CoreGuiViews.UpgradeView>> upgrades(UUID islandId);

    CompletableFuture<List<UpgradeRuleView>> upgradeRules();

    CompletableFuture<List<CoreGuiViews.MissionView>> missions(UUID islandId, String kind);
}
