package kr.lunaf.cloudislands.coreclient;

import java.util.List;
import java.util.Map;
import java.util.UUID;
import java.util.concurrent.CompletableFuture;
import kr.lunaf.cloudislands.api.model.IslandFlag;

public final class CoreIslandEnvironmentQueryClient implements IslandEnvironmentQueryClient {
    private final CoreApiClient delegate;

    public CoreIslandEnvironmentQueryClient(CoreApiClient delegate) {
        if (delegate == null) {
            throw new IllegalArgumentException("delegate is required");
        }
        this.delegate = delegate;
    }

    @Override
    public CompletableFuture<CoreGuiViews.BiomeView> islandBiome(UUID islandId) {
        requireIsland(islandId);
        return CoreGuiViews.islandBiome(delegate, islandId);
    }

    @Override
    public CompletableFuture<CoreGuiViews.IslandInfoView> getIsland(UUID islandId) {
        requireIsland(islandId);
        return CoreGuiViews.islandInfo(delegate, islandId);
    }

    @Override
    public CompletableFuture<Map<IslandFlag, String>> flagValues(UUID islandId) {
        requireIsland(islandId);
        return CoreGuiViews.islandFlags(delegate, islandId);
    }

    @Override
    public CompletableFuture<List<CoreGuiViews.LimitView>> limitViews(UUID islandId) {
        requireIsland(islandId);
        return CoreGuiViews.islandLimits(delegate, islandId);
    }

    private static void requireIsland(UUID islandId) {
        if (islandId == null) {
            throw new IllegalArgumentException("islandId is required");
        }
    }
}
