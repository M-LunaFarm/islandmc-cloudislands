package kr.lunaf.cloudislands.coreclient;

import java.util.List;
import java.util.Map;
import java.util.UUID;
import java.util.concurrent.CompletableFuture;
import kr.lunaf.cloudislands.api.model.IslandFlag;

public interface IslandEnvironmentQueryClient {
    CompletableFuture<CoreGuiViews.BiomeView> islandBiome(UUID islandId);

    CompletableFuture<CoreGuiViews.IslandInfoView> getIsland(UUID islandId);

    CompletableFuture<Map<IslandFlag, String>> flagValues(UUID islandId);

    CompletableFuture<List<CoreGuiViews.LimitView>> limitViews(UUID islandId);
}
