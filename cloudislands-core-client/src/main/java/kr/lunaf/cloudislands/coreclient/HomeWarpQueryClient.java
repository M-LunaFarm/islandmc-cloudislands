package kr.lunaf.cloudislands.coreclient;

import java.util.List;
import java.util.UUID;
import java.util.concurrent.CompletableFuture;

public interface HomeWarpQueryClient {
    CompletableFuture<List<CoreGuiViews.HomeView>> homes(UUID islandId);

    CompletableFuture<List<CoreGuiViews.WarpView>> warps(UUID islandId);

    CompletableFuture<CoreGuiViews.IslandInfoView> islandInfo(UUID islandId);

    CompletableFuture<List<CoreGuiViews.WarpView>> publicWarps(int limit, String category, String query);
}
