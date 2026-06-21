package kr.lunaf.cloudislands.coreclient;

import java.util.List;
import java.util.Map;
import java.util.UUID;
import java.util.concurrent.CompletableFuture;
import kr.lunaf.cloudislands.common.json.SimpleJson;

public final class CoreIslandVisitorStatsQueryClient implements IslandVisitorStatsQueryClient {
    private final CoreApiClient delegate;

    public CoreIslandVisitorStatsQueryClient(CoreApiClient delegate) {
        if (delegate == null) {
            throw new IllegalArgumentException("delegate is required");
        }
        this.delegate = delegate;
    }

    @Override
    public CompletableFuture<IslandVisitorStatsView> stats(UUID islandId, int recentLimit) {
        if (islandId == null) {
            throw new IllegalArgumentException("islandId is required");
        }
        return delegate.islandVisitorStats(islandId, Math.max(1, Math.min(recentLimit, 100))).thenApply(CoreIslandVisitorStatsQueryClient::stats);
    }

    private static IslandVisitorStatsView stats(String body) {
        Map<?, ?> root = SimpleJson.object(SimpleJson.parse(body == null || body.isBlank() ? "{}" : body));
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
