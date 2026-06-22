package kr.lunaf.cloudislands.coreclient;

import java.util.List;
import java.util.UUID;
import java.util.concurrent.CompletableFuture;
import kr.lunaf.cloudislands.api.model.IslandHomeSnapshot;
import kr.lunaf.cloudislands.api.model.IslandWarpSnapshot;

public final class CoreHomeWarpQueryClient implements HomeWarpQueryClient {
    private final CoreApiClient delegate;

    public CoreHomeWarpQueryClient(CoreApiClient delegate) {
        if (delegate == null) {
            throw new IllegalArgumentException("delegate is required");
        }
        this.delegate = delegate;
    }

    @Override
    public CompletableFuture<List<IslandHomeSnapshot>> homeSnapshots(UUID islandId) {
        requireIsland(islandId);
        return delegate.listIslandHomes(islandId).thenApply(body -> CoreHomeWarpJson.homes(islandId, body));
    }

    @Override
    public CompletableFuture<List<IslandWarpSnapshot>> warpSnapshots(UUID islandId) {
        requireIsland(islandId);
        return delegate.listIslandWarps(islandId).thenApply(body -> CoreHomeWarpJson.warps(islandId, body));
    }

    @Override
    public CompletableFuture<CoreGuiViews.IslandInfoView> islandInfo(UUID islandId) {
        requireIsland(islandId);
        return islandQueries().getIsland(islandId);
    }

    @Override
    public CompletableFuture<List<IslandWarpSnapshot>> publicWarpSnapshots(int limit, String category, String query) {
        int safeLimit = Math.max(1, Math.min(limit, 100));
        return delegate.listPublicWarps(safeLimit, category == null ? "" : category, query == null ? "" : query)
            .thenApply(body -> CoreHomeWarpJson.warps(null, body));
    }

    private static void requireIsland(UUID islandId) {
        if (islandId == null) {
            throw new IllegalArgumentException("islandId is required");
        }
    }

    private IslandQueryClient islandQueries() {
        return delegate instanceof IslandQueryClient queries ? queries : delegate.islands();
    }
}
