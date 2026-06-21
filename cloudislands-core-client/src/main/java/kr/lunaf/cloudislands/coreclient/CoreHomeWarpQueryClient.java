package kr.lunaf.cloudislands.coreclient;

import java.util.List;
import java.util.UUID;
import java.util.concurrent.CompletableFuture;

public final class CoreHomeWarpQueryClient implements HomeWarpQueryClient {
    private final CoreApiClient delegate;

    public CoreHomeWarpQueryClient(CoreApiClient delegate) {
        if (delegate == null) {
            throw new IllegalArgumentException("delegate is required");
        }
        this.delegate = delegate;
    }

    @Override
    public CompletableFuture<List<CoreGuiViews.HomeView>> homes(UUID islandId) {
        requireIsland(islandId);
        return CoreGuiViews.islandHomes(delegate, islandId);
    }

    @Override
    public CompletableFuture<List<CoreGuiViews.WarpView>> warps(UUID islandId) {
        requireIsland(islandId);
        return CoreGuiViews.islandWarps(delegate, islandId);
    }

    @Override
    public CompletableFuture<CoreGuiViews.IslandInfoView> islandInfo(UUID islandId) {
        requireIsland(islandId);
        return CoreGuiViews.islandInfo(delegate, islandId);
    }

    @Override
    public CompletableFuture<List<CoreGuiViews.WarpView>> publicWarps(int limit, String category, String query) {
        int safeLimit = Math.max(1, Math.min(limit, 100));
        return CoreGuiViews.publicWarps(delegate, safeLimit, category == null ? "" : category, query == null ? "" : query);
    }

    private static void requireIsland(UUID islandId) {
        if (islandId == null) {
            throw new IllegalArgumentException("islandId is required");
        }
    }
}
