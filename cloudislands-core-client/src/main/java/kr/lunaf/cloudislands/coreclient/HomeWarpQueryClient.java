package kr.lunaf.cloudislands.coreclient;

import java.util.List;
import java.util.UUID;
import java.util.concurrent.CompletableFuture;
import kr.lunaf.cloudislands.api.model.IslandHomeSnapshot;
import kr.lunaf.cloudislands.api.model.IslandWarpSnapshot;

public interface HomeWarpQueryClient {
    CompletableFuture<List<IslandHomeSnapshot>> homeSnapshots(UUID islandId);

    CompletableFuture<List<IslandWarpSnapshot>> warpSnapshots(UUID islandId);

    CompletableFuture<CoreGuiViews.IslandInfoView> islandInfo(UUID islandId);

    CompletableFuture<List<IslandWarpSnapshot>> publicWarpSnapshots(int limit, String category, String query);

    default CompletableFuture<List<IslandWarpSnapshot>> publicWarpSnapshots(int offset, int limit, String category, String query) {
        int safeOffset = Math.max(0, offset);
        int safeLimit = Math.max(1, limit);
        return publicWarpSnapshots(safeOffset + safeLimit, category, query)
            .thenApply(warps -> warps.stream().skip(safeOffset).limit(safeLimit).toList());
    }

    default CompletableFuture<List<CoreGuiViews.HomeView>> homes(UUID islandId) {
        return homeSnapshots(islandId).thenApply(homes -> homes.stream()
            .map(CoreHomeWarpJson::homeView)
            .toList());
    }

    default CompletableFuture<List<CoreGuiViews.WarpView>> warps(UUID islandId) {
        return warpSnapshots(islandId).thenApply(warps -> warps.stream()
            .map(CoreHomeWarpJson::warpView)
            .toList());
    }

    default CompletableFuture<List<CoreGuiViews.WarpView>> publicWarps(int limit, String category, String query) {
        return publicWarpSnapshots(limit, category, query).thenApply(warps -> warps.stream()
            .map(CoreHomeWarpJson::warpView)
            .toList());
    }

    default CompletableFuture<List<CoreGuiViews.WarpView>> publicWarps(int offset, int limit, String category, String query) {
        return publicWarpSnapshots(offset, limit, category, query).thenApply(warps -> warps.stream()
            .map(CoreHomeWarpJson::warpView)
            .toList());
    }
}
