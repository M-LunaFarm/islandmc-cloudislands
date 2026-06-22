package kr.lunaf.cloudislands.coreclient;

import java.util.List;
import java.util.Map;
import java.util.UUID;
import java.util.concurrent.CompletableFuture;

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
        return core.postResultBody("/v1/islands/visitors/stats", CoreJsonPayload.object("islandId", islandId, "limit", Math.max(1, Math.min(recentLimit, 100))))
            .thenApply(CoreResponseBody::value)
            .thenApply(JdkIslandVisitorStatsQueryClient::stats);
    }

    static IslandVisitorStatsView stats(String body) {
        Map<?, ?> root = CoreJson.object(body);
        List<IslandVisitorStatsView.RecentVisitorView> recent = CoreJson.objects(root, "recentVisitors").stream()
            .map(visitor -> new IslandVisitorStatsView.RecentVisitorView(CoreJson.text(visitor, "visitorUuid"), CoreJson.text(visitor, "lastVisitedAt")))
            .toList();
        return new IslandVisitorStatsView(
            CoreJson.text(root, "islandId"),
            CoreJson.number(root, "totalVisits"),
            CoreJson.number(root, "uniqueVisitors"),
            recent
        );
    }
}
