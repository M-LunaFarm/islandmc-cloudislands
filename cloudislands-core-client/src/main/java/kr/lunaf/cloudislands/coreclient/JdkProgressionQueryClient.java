package kr.lunaf.cloudislands.coreclient;

import java.util.List;
import java.util.Locale;
import java.util.Map;
import java.util.UUID;
import java.util.concurrent.CompletableFuture;

public final class JdkProgressionQueryClient implements ProgressionQueryClient {
    private final JdkCoreApiClient core;

    public JdkProgressionQueryClient(JdkCoreApiClient core) {
        if (core == null) {
            throw new IllegalArgumentException("core is required");
        }
        this.core = core;
    }

    @Override
    public CompletableFuture<CoreGuiViews.IslandInfoView> islandInfo(UUID islandId) {
        requireIsland(islandId);
        return core.islands().getIsland(islandId);
    }

    @Override
    public CompletableFuture<LevelView> level(UUID islandId) {
        requireIsland(islandId);
        return core.getBody("/v1/islands/" + islandId + "/level")
            .thenApply(CoreResponseBody::value)
            .thenApply(JdkProgressionQueryClient::levelView);
    }

    @Override
    public CompletableFuture<ProgressionBlockDetailsView> blockDetails(UUID islandId, int limit) {
        requireIsland(islandId);
        return core.postBody("/v1/islands/blocks", CoreJsonPayload.object("islandId", islandId, "limit", boundedLimit(limit)))
            .thenApply(CoreResponseBody::value)
            .thenApply(JdkProgressionQueryClient::blockDetailsView);
    }

    @Override
    public CompletableFuture<CoreGuiViews.RankingData> rankings(int limit) {
        CompletableFuture<List<ProgressionRankingEntryView>> levels = topLevel(limit);
        CompletableFuture<List<ProgressionRankingEntryView>> worths = topWorth(limit);
        CompletableFuture<List<ProgressionReviewRankingEntryView>> reviews = topReviews(limit);
        return levels.thenCombine(worths, (levelViews, worthViews) -> new CoreGuiViews.RankingData(
                rankingViews(levelViews, "level"),
                rankingViews(worthViews, "worth"),
                List.of()
            ))
            .thenCombine(reviews, (data, reviewViews) -> new CoreGuiViews.RankingData(data.levels(), data.worths(), reviewRankingViews(reviewViews)));
    }

    @Override
    public CompletableFuture<List<ProgressionRankingEntryView>> topWorth(int limit) {
        return core.postBody("/v1/rankings/worth", CoreJsonPayload.object("limit", boundedLimit(limit)))
            .thenApply(CoreResponseBody::value)
            .thenApply(body -> rankingViews(body, "worth"));
    }

    @Override
    public CompletableFuture<List<ProgressionRankingEntryView>> topLevel(int limit) {
        return core.postBody("/v1/rankings/level", CoreJsonPayload.object("limit", boundedLimit(limit)))
            .thenApply(CoreResponseBody::value)
            .thenApply(body -> rankingViews(body, "level"));
    }

    @Override
    public CompletableFuture<List<ProgressionReviewRankingEntryView>> topReviews(int limit) {
        return core.postBody("/v1/rankings/reviews", CoreJsonPayload.object("limit", boundedLimit(limit)))
            .thenApply(CoreResponseBody::value)
            .thenApply(JdkProgressionQueryClient::reviewRankingViews);
    }

    @Override
    public CompletableFuture<List<CoreGuiViews.UpgradeView>> upgrades(UUID islandId) {
        requireIsland(islandId);
        return core.postBody("/v1/islands/upgrades", CoreJsonPayload.object("islandId", islandId))
            .thenApply(CoreResponseBody::value)
            .thenApply(JdkProgressionQueryClient::upgradeViews);
    }

    @Override
    public CompletableFuture<List<UpgradeRuleView>> upgradeRules() {
        return core.postBody("/v1/upgrades/rules", "{}")
            .thenApply(CoreResponseBody::value)
            .thenApply(JdkProgressionQueryClient::upgradeRuleViews);
    }

    @Override
    public CompletableFuture<List<CoreGuiViews.MissionView>> missions(UUID islandId, String kind) {
        requireIsland(islandId);
        return core.postBody("/v1/islands/missions", CoreJsonPayload.object("islandId", islandId, "kind", kind == null || kind.isBlank() ? "MISSION" : kind))
            .thenApply(CoreResponseBody::value)
            .thenApply(JdkProgressionQueryClient::missionViews);
    }

    static LevelView levelView(String body) {
        Map<?, ?> root = CoreJson.object(body);
        return new LevelView(
            CoreJson.text(root, "islandId"),
            CoreJson.number(root, "level"),
            CoreJson.text(root, "worth"),
            CoreJson.text(root, "calculatedAt")
        );
    }

    static ProgressionBlockDetailsView blockDetailsView(String body) {
        Map<?, ?> root = CoreJson.object(body);
        Map<?, ?> summary = CoreJson.objectValue(root, "summary");
        List<ProgressionBlockDetailView> blocks = CoreJson.objects(root, "blocks").stream()
            .map(block -> new ProgressionBlockDetailView(
                CoreJson.text(block, "materialKey"),
                CoreJson.number(block, "count"),
                CoreJson.text(block, "totalWorth"),
                CoreJson.number(block, "levelPoints")
            ))
            .filter(block -> !block.materialKey().isBlank())
            .toList();
        return new ProgressionBlockDetailsView(
            CoreJson.text(summary, "totalWorth"),
            CoreJson.number(summary, "totalLevelPoints"),
            blocks
        );
    }

    static List<ProgressionRankingEntryView> rankingViews(String body, String valueKey) {
        return entries(body, "rankings", "islands").stream()
            .map(object -> new ProgressionRankingEntryView(
                CoreJson.text(object, "islandId"),
                CoreJson.text(object, "name"),
                CoreJson.number(object, "level"),
                CoreJson.text(object, "worth"),
                valueKey
            ))
            .filter(entry -> !entry.islandId().isBlank())
            .toList();
    }

    static List<ProgressionReviewRankingEntryView> reviewRankingViews(String body) {
        return entries(body, "rankings", "reviews").stream()
            .map(object -> new ProgressionReviewRankingEntryView(
                CoreJson.text(object, "islandId"),
                CoreJson.decimal(object, "averageRating"),
                CoreJson.number(object, "reviewCount")
            ))
            .filter(entry -> !entry.islandId().isBlank())
            .toList();
    }

    static List<UpgradeRuleView> upgradeRuleViews(String body) {
        return entries(body, "rules", "upgrades").stream()
            .map(object -> new UpgradeRuleView(
                CoreJson.text(object, "upgradeKey"),
                CoreJson.text(object, "type"),
                CoreJson.number(object, "maxLevel"),
                CoreJson.text(object, "baseCost"),
                CoreJson.text(object, "multiplier")
            ))
            .filter(rule -> !rule.key().isBlank())
            .toList();
    }

    static List<CoreGuiViews.UpgradeView> upgradeViews(String body) {
        return entries(body, "upgrades").stream()
            .map(object -> {
                String key = CoreJson.firstText(object, "key", "upgradeKey");
                return new CoreGuiViews.UpgradeView(key, CoreJson.text(object, "type"), intValue(object, "level"), CoreJson.text(object, "generatorKey"));
            })
            .filter(view -> !view.key().isBlank())
            .toList();
    }

    static List<CoreGuiViews.MissionView> missionViews(String body) {
        return entries(body, "missions").stream()
            .map(object -> {
                String key = CoreJson.firstText(object, "key", "missionKey");
                return new CoreGuiViews.MissionView(
                    key,
                    CoreJson.text(object, "title"),
                    CoreJson.number(object, "progress"),
                    CoreJson.number(object, "goal"),
                    CoreJson.bool(object, "completed"),
                    CoreJson.text(object, "reward"),
                    CoreJson.text(object, "category"),
                    CoreJson.text(object, "description"),
                    CoreJson.text(object, "triggerType"),
                    CoreJson.text(object, "targetKey"),
                    CoreJson.text(object, "rewardType"),
                    CoreJson.bool(object, "repeatable"),
                    CoreJson.bool(object, "dailyReset")
                );
            })
            .filter(view -> !view.key().isBlank())
            .toList();
    }

    private static List<CoreGuiViews.RankingView> rankingViews(List<ProgressionRankingEntryView> entries, String label) {
        List<ProgressionRankingEntryView> safeEntries = entries == null ? List.of() : entries;
        List<CoreGuiViews.RankingView> rankings = new java.util.ArrayList<>();
        for (ProgressionRankingEntryView entry : safeEntries) {
            if (!entry.islandId().isBlank()) {
                rankings.add(new CoreGuiViews.RankingView(rankings.size() + 1, label, entry.islandId(), entry.level(), entry.worth()));
            }
        }
        return rankings;
    }

    private static List<CoreGuiViews.RankingView> reviewRankingViews(List<ProgressionReviewRankingEntryView> entries) {
        List<ProgressionReviewRankingEntryView> safeEntries = entries == null ? List.of() : entries;
        List<CoreGuiViews.RankingView> rankings = new java.util.ArrayList<>();
        for (ProgressionReviewRankingEntryView entry : safeEntries) {
            if (!entry.islandId().isBlank()) {
                rankings.add(new CoreGuiViews.RankingView(
                    rankings.size() + 1,
                    "reviews",
                    entry.islandId(),
                    entry.reviewCount(),
                    String.format(Locale.ROOT, "%.2f", entry.averageRating())
                ));
            }
        }
        return rankings;
    }

    private static List<Map<?, ?>> entries(String body, String... keys) {
        return CoreJson.entries(body, keys);
    }

    private static int boundedLimit(int limit) {
        return Math.max(1, Math.min(limit, 100));
    }

    private static void requireIsland(UUID islandId) {
        if (islandId == null) {
            throw new IllegalArgumentException("islandId is required");
        }
    }

    private static int intValue(Map<?, ?> object, String key) {
        return (int) CoreJson.number(object, key);
    }

}
