package kr.lunaf.cloudislands.coreclient;

import java.util.List;
import java.util.UUID;
import java.util.concurrent.CompletableFuture;
import kr.lunaf.cloudislands.api.model.IslandHomeSnapshot;
import kr.lunaf.cloudislands.api.model.IslandWarpSnapshot;

public final class JdkHomeWarpQueryClient implements HomeWarpQueryClient {
    private final JdkCoreApiClient core;

    public JdkHomeWarpQueryClient(JdkCoreApiClient core) {
        if (core == null) {
            throw new IllegalArgumentException("core is required");
        }
        this.core = core;
    }

    @Override
    public CompletableFuture<List<IslandHomeSnapshot>> homeSnapshots(UUID islandId) {
        requireIsland(islandId);
        return core.getBody("/v1/islands/" + islandId + "/homes")
            .thenApply(CoreResponseBody::value)
            .thenApply(body -> CoreHomeWarpJson.homes(islandId, body));
    }

    @Override
    public CompletableFuture<List<IslandWarpSnapshot>> warpSnapshots(UUID islandId) {
        requireIsland(islandId);
        return core.getBody("/v1/islands/" + islandId + "/warps")
            .thenApply(CoreResponseBody::value)
            .thenApply(body -> CoreHomeWarpJson.warps(islandId, body));
    }

    @Override
    public CompletableFuture<CoreGuiViews.IslandInfoView> islandInfo(UUID islandId) {
        requireIsland(islandId);
        return islandQueries().getIsland(islandId);
    }

    @Override
    public CompletableFuture<List<IslandWarpSnapshot>> publicWarpSnapshots(int limit, String category, String query) {
        int safeLimit = Math.max(1, Math.min(limit, 100));
        String safeCategory = category == null ? "" : category;
        String safeQuery = query == null ? "" : query;
        String payload = safeCategory.isBlank() && safeQuery.isBlank()
            ? CoreJsonPayload.object("limit", safeLimit)
            : CoreJsonPayload.object("limit", safeLimit, "category", safeCategory, "query", safeQuery);
        return core.postBody("/v1/islands/public-warps", payload)
            .thenApply(CoreResponseBody::value)
            .thenApply(body -> CoreHomeWarpJson.warps(null, body));
    }

    private static void requireIsland(UUID islandId) {
        if (islandId == null) {
            throw new IllegalArgumentException("islandId is required");
        }
    }

    private IslandQueryClient islandQueries() {
        return core.islands();
    }
}
