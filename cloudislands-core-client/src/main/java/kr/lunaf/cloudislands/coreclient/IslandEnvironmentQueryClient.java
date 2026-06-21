package kr.lunaf.cloudislands.coreclient;

import java.util.List;
import java.util.Map;
import java.util.UUID;
import java.util.concurrent.CompletableFuture;
import kr.lunaf.cloudislands.api.model.IslandBiomeSnapshot;
import kr.lunaf.cloudislands.api.model.IslandFlag;
import kr.lunaf.cloudislands.api.model.IslandFlagsSnapshot;
import kr.lunaf.cloudislands.api.model.IslandLimitSnapshot;

public interface IslandEnvironmentQueryClient {
    CompletableFuture<IslandBiomeSnapshot> biome(UUID islandId);

    CompletableFuture<CoreGuiViews.IslandInfoView> getIsland(UUID islandId);

    CompletableFuture<IslandFlagsSnapshot> flags(UUID islandId);

    CompletableFuture<List<IslandLimitSnapshot>> limits(UUID islandId);

    default CompletableFuture<CoreGuiViews.BiomeView> islandBiome(UUID islandId) {
        return biome(islandId).thenApply(CoreEnvironmentJson::biomeView);
    }

    default CompletableFuture<Map<IslandFlag, String>> flagValues(UUID islandId) {
        return flags(islandId).thenApply(IslandFlagsSnapshot::values);
    }

    default CompletableFuture<List<CoreGuiViews.LimitView>> limitViews(UUID islandId) {
        return limits(islandId).thenApply(values -> values.stream()
            .map(CoreEnvironmentJson::limitView)
            .toList());
    }
}
