package kr.lunaf.cloudislands.coreclient;

import java.util.List;
import java.util.UUID;
import java.util.concurrent.CompletableFuture;
import kr.lunaf.cloudislands.api.model.IslandBiomeSnapshot;
import kr.lunaf.cloudislands.api.model.IslandFlagsSnapshot;
import kr.lunaf.cloudislands.api.model.IslandLimitSnapshot;

public final class JdkIslandEnvironmentQueryClient implements IslandEnvironmentQueryClient {
    private final JdkCoreApiClient core;

    public JdkIslandEnvironmentQueryClient(JdkCoreApiClient core) {
        if (core == null) {
            throw new IllegalArgumentException("core is required");
        }
        this.core = core;
    }

    @Override
    public CompletableFuture<IslandBiomeSnapshot> biome(UUID islandId) {
        requireIsland(islandId);
        return core.get("/v1/islands/" + islandId + "/biome")
            .thenApply(body -> CoreEnvironmentJson.biome(islandId, body));
    }

    @Override
    public CompletableFuture<CoreGuiViews.IslandInfoView> getIsland(UUID islandId) {
        requireIsland(islandId);
        return core.islands().getIsland(islandId);
    }

    @Override
    public CompletableFuture<IslandFlagsSnapshot> flags(UUID islandId) {
        requireIsland(islandId);
        return core.get("/v1/islands/" + islandId + "/flags")
            .thenApply(body -> CoreEnvironmentJson.flags(islandId, body));
    }

    @Override
    public CompletableFuture<List<IslandLimitSnapshot>> limits(UUID islandId) {
        requireIsland(islandId);
        return core.post("/v1/islands/limits", JdkCoreApiClient.jsonObject("islandId", islandId))
            .thenApply(body -> CoreEnvironmentJson.limits(islandId, body));
    }

    private static void requireIsland(UUID islandId) {
        if (islandId == null) {
            throw new IllegalArgumentException("islandId is required");
        }
    }

}
