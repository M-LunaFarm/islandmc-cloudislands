package kr.lunaf.cloudislands.coreclient;

import java.util.List;
import java.util.Map;
import java.util.UUID;
import java.util.concurrent.CompletableFuture;
import kr.lunaf.cloudislands.common.json.SimpleJson;

final class JdkIslandVisitorStatsQueryClient implements IslandVisitorStatsQueryClient {
    private final JdkCoreApiClient core;

    JdkIslandVisitorStatsQueryClient(JdkCoreApiClient core) {
        if (core == null) {
            throw new IllegalArgumentException("core is required");
        }
        this.core = core;
    }

    @Override
    public CompletableFuture<IslandVisitorStatsView> stats(UUID islandId, int recentLimit) {
        if (islandId == null) {
            throw new IllegalArgumentException("islandId is required");
        }
        return core.postWithResultBody("/v1/islands/visitors/stats", JdkCoreApiClient.jsonObject("islandId", islandId, "limit", Math.max(1, Math.min(recentLimit, 100)))).thenApply(JdkIslandVisitorStatsQueryClient::stats);
    }

    static IslandVisitorStatsView stats(String body) {
        Map<?, ?> root = CoreJson.object(body);
        List<IslandVisitorStatsView.RecentVisitorView> recent = SimpleJson.list(root.get("recentVisitors")).stream()
            .map(SimpleJson::object)
            .map(visitor -> new IslandVisitorStatsView.RecentVisitorView(text(visitor, "visitorUuid"), text(visitor, "lastVisitedAt")))
            .toList();
        return new IslandVisitorStatsView(
            text(root, "islandId"),
            SimpleJson.number(root.get("totalVisits")),
            SimpleJson.number(root.get("uniqueVisitors")),
            recent
        );
    }

    private static String text(Map<?, ?> object, String key) {
        return SimpleJson.text(object.get(key));
    }
}
