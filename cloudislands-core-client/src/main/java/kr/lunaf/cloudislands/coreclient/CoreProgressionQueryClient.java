package kr.lunaf.cloudislands.coreclient;

import java.util.List;
import java.util.Locale;
import java.util.Map;
import java.util.UUID;
import java.util.concurrent.CompletableFuture;
import kr.lunaf.cloudislands.common.json.SimpleJson;

public final class CoreProgressionQueryClient implements ProgressionQueryClient {
    private final CoreApiClient delegate;

    public CoreProgressionQueryClient(CoreApiClient delegate) {
        if (delegate == null) {
            throw new IllegalArgumentException("delegate is required");
        }
        this.delegate = delegate;
    }

    @Override
    public CompletableFuture<CoreGuiViews.IslandInfoView> islandInfo(UUID islandId) {
        requireIsland(islandId);
        return new CoreIslandQueryClient(delegate).getIsland(islandId);
    }

    @Override
    public CompletableFuture<LevelView> level(UUID islandId) {
        requireIsland(islandId);
        return delegate.getIslandLevel(islandId).thenApply(CoreProgressionQueryClient::levelView);
    }

    @Override
    public CompletableFuture<ProgressionBlockDetailsView> blockDetails(UUID islandId, int limit) {
        requireIsland(islandId);
        return delegate.islandBlockDetails(islandId, boundedLimit(limit)).thenApply(CoreProgressionQueryClient::blockDetailsView);
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
        return delegate.topIslandsByWorth(boundedLimit(limit)).thenApply(body -> rankingViews(body, "worth"));
    }

    @Override
    public CompletableFuture<List<ProgressionRankingEntryView>> topLevel(int limit) {
        return delegate.topIslandsByLevel(boundedLimit(limit)).thenApply(body -> rankingViews(body, "level"));
    }

    @Override
    public CompletableFuture<List<ProgressionReviewRankingEntryView>> topReviews(int limit) {
        return delegate.topIslandsByReviews(boundedLimit(limit)).thenApply(CoreProgressionQueryClient::reviewRankingViews);
    }

    @Override
    public CompletableFuture<List<CoreGuiViews.UpgradeView>> upgrades(UUID islandId) {
        requireIsland(islandId);
        return CoreGuiViews.islandUpgrades(delegate, islandId);
    }

    @Override
    public CompletableFuture<List<UpgradeRuleView>> upgradeRules() {
        return delegate.listUpgradeRules().thenApply(CoreProgressionQueryClient::upgradeRuleViews);
    }

    @Override
    public CompletableFuture<List<CoreGuiViews.MissionView>> missions(UUID islandId, String kind) {
        requireIsland(islandId);
        return CoreGuiViews.islandMissions(delegate, islandId, kind == null || kind.isBlank() ? "MISSION" : kind);
    }

    static LevelView levelView(String body) {
        Map<?, ?> root = CoreJson.object(body);
        return new LevelView(
            text(root, "islandId"),
            SimpleJson.number(root.get("level")),
            text(root, "worth"),
            text(root, "calculatedAt")
        );
    }

    static ProgressionBlockDetailsView blockDetailsView(String body) {
        Map<?, ?> root = CoreJson.object(body);
        Map<?, ?> summary = SimpleJson.object(root.get("summary"));
        List<ProgressionBlockDetailView> blocks = SimpleJson.list(root.get("blocks")).stream()
            .map(SimpleJson::object)
            .map(block -> new ProgressionBlockDetailView(
                text(block, "materialKey"),
                SimpleJson.number(block.get("count")),
                text(block, "totalWorth"),
                SimpleJson.number(block.get("levelPoints"))
            ))
            .filter(block -> !block.materialKey().isBlank())
            .toList();
        return new ProgressionBlockDetailsView(
            text(summary, "totalWorth"),
            SimpleJson.number(summary.get("totalLevelPoints")),
            blocks
        );
    }

    static List<ProgressionRankingEntryView> rankingViews(String body, String valueKey) {
        return entries(body).stream()
            .map(object -> new ProgressionRankingEntryView(
                text(object, "islandId"),
                text(object, "name"),
                SimpleJson.number(object.get("level")),
                text(object, "worth"),
                valueKey
            ))
            .filter(entry -> !entry.islandId().isBlank())
            .toList();
    }

    static List<ProgressionReviewRankingEntryView> reviewRankingViews(String body) {
        return entries(body).stream()
            .map(object -> new ProgressionReviewRankingEntryView(
                text(object, "islandId"),
                doubleValue(object.get("averageRating")),
                SimpleJson.number(object.get("reviewCount"))
            ))
            .filter(entry -> !entry.islandId().isBlank())
            .toList();
    }

    static List<UpgradeRuleView> upgradeRuleViews(String body) {
        return entries(body).stream()
            .map(object -> new UpgradeRuleView(
                text(object, "upgradeKey"),
                text(object, "type"),
                SimpleJson.number(object.get("maxLevel")),
                text(object, "baseCost"),
                text(object, "multiplier")
            ))
            .filter(rule -> !rule.key().isBlank())
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

    private static List<Map<?, ?>> entries(String body) {
        return CoreJson.entries(body);
    }

    private static int boundedLimit(int limit) {
        return Math.max(1, Math.min(limit, 100));
    }

    private static void requireIsland(UUID islandId) {
        if (islandId == null) {
            throw new IllegalArgumentException("islandId is required");
        }
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
}
