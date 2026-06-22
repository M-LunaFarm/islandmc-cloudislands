package kr.lunaf.cloudislands.coreclient;

import java.util.List;
import java.util.UUID;
import java.util.concurrent.CompletableFuture;
import kr.lunaf.cloudislands.api.model.IslandBiomeSnapshot;
import kr.lunaf.cloudislands.api.model.IslandFlagsSnapshot;
import kr.lunaf.cloudislands.api.model.IslandLimitSnapshot;

public final class CoreIslandEnvironmentQueryClient implements IslandEnvironmentQueryClient {
    private final CoreApiClient delegate;

    public CoreIslandEnvironmentQueryClient(CoreApiClient delegate) {
        if (delegate == null) {
            throw new IllegalArgumentException("delegate is required");
        }
        this.delegate = delegate;
    }

    @Override
    public CompletableFuture<IslandBiomeSnapshot> biome(UUID islandId) {
        requireIsland(islandId);
        return delegate.islandBiome(islandId)
            .thenApply(body -> CoreEnvironmentJson.biome(islandId, body));
    }

    @Override
    public CompletableFuture<CoreGuiViews.IslandInfoView> getIsland(UUID islandId) {
        requireIsland(islandId);
        return new CoreIslandQueryClient(delegate).getIsland(islandId);
    }

    @Override
    public CompletableFuture<IslandFlagsSnapshot> flags(UUID islandId) {
        requireIsland(islandId);
        return delegate.listIslandFlags(islandId)
            .thenApply(body -> CoreEnvironmentJson.flags(islandId, body));
    }

    @Override
    public CompletableFuture<List<IslandLimitSnapshot>> limits(UUID islandId) {
        requireIsland(islandId);
        return delegate.listIslandLimits(islandId)
            .thenApply(body -> CoreEnvironmentJson.limits(islandId, body));
    }

    private static void requireIsland(UUID islandId) {
        if (islandId == null) {
            throw new IllegalArgumentException("islandId is required");
        }
    }
}
