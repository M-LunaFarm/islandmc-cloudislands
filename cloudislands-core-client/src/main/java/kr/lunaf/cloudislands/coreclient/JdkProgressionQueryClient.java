package kr.lunaf.cloudislands.coreclient;

import java.util.List;
import java.util.Locale;
import java.util.Map;
import java.util.UUID;
import java.util.concurrent.CompletableFuture;
import kr.lunaf.cloudislands.common.json.SimpleJson;

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
        return core.get("/v1/islands/" + islandId + "/level").thenApply(JdkProgressionQueryClient::levelView);
    }

    @Override
    public CompletableFuture<ProgressionBlockDetailsView> blockDetails(UUID islandId, int limit) {
        requireIsland(islandId);
        return core.post("/v1/islands/blocks", JdkCoreApiClient.jsonObject("islandId", islandId, "limit", boundedLimit(limit))).thenApply(JdkProgressionQueryClient::blockDetailsView);
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
        return core.post("/v1/rankings/worth", JdkCoreApiClient.jsonObject("limit", boundedLimit(limit))).thenApply(body -> rankingViews(body, "worth"));
    }

    @Override
    public CompletableFuture<List<ProgressionRankingEntryView>> topLevel(int limit) {
        return core.post("/v1/rankings/level", JdkCoreApiClient.jsonObject("limit", boundedLimit(limit))).thenApply(body -> rankingViews(body, "level"));
    }

    @Override
    public CompletableFuture<List<ProgressionReviewRankingEntryView>> topReviews(int limit) {
        return core.post("/v1/rankings/reviews", JdkCoreApiClient.jsonObject("limit", boundedLimit(limit))).thenApply(JdkProgressionQueryClient::reviewRankingViews);
    }

    @Override
    public CompletableFuture<List<CoreGuiViews.UpgradeView>> upgrades(UUID islandId) {
        requireIsland(islandId);
        return core.post("/v1/islands/upgrades", JdkCoreApiClient.jsonObject("islandId", islandId)).thenApply(JdkProgressionQueryClient::upgradeViews);
    }

    @Override
    public CompletableFuture<List<UpgradeRuleView>> upgradeRules() {
        return core.post("/v1/upgrades/rules", "{}").thenApply(JdkProgressionQueryClient::upgradeRuleViews);
    }

    @Override
    public CompletableFuture<List<CoreGuiViews.MissionView>> missions(UUID islandId, String kind) {
        requireIsland(islandId);
        return core.post("/v1/islands/missions", JdkCoreApiClient.jsonObject("islandId", islandId, "kind", kind == null || kind.isBlank() ? "MISSION" : kind))
            .thenApply(JdkProgressionQueryClient::missionViews);
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

    static List<CoreGuiViews.UpgradeView> upgradeViews(String body) {
        return entries(body).stream()
            .map(object -> {
                String key = text(object, "key");
                if (key.isBlank()) {
                    key = text(object, "upgradeKey");
                }
                return new CoreGuiViews.UpgradeView(key, text(object, "type"), intValue(object, "level"), text(object, "generatorKey"));
            })
            .filter(view -> !view.key().isBlank())
            .toList();
    }

    static List<CoreGuiViews.MissionView> missionViews(String body) {
        return entries(body).stream()
            .map(object -> {
                String key = text(object, "key");
                if (key.isBlank()) {
                    key = text(object, "missionKey");
                }
                return new CoreGuiViews.MissionView(
                    key,
                    text(object, "title"),
                    SimpleJson.number(object.get("progress")),
                    SimpleJson.number(object.get("goal")),
                    bool(object, "completed"),
                    text(object, "reward")
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

    private static int intValue(Map<?, ?> object, String key) {
        return (int) SimpleJson.number(object.get(key));
    }

    private static boolean bool(Map<?, ?> object, String key) {
        Object value = object.get(key);
        return value instanceof Boolean bool ? bool : Boolean.parseBoolean(SimpleJson.text(value));
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
