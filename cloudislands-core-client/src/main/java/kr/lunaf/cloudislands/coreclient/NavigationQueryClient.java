package kr.lunaf.cloudislands.coreclient;

import java.util.List;
import java.util.UUID;
import java.util.concurrent.CompletableFuture;

public interface NavigationQueryClient {
    CompletableFuture<CoreGuiViews.PlayerProfileView> playerProfileByName(String playerName);

    CompletableFuture<List<CoreGuiViews.PublicIslandView>> publicIslands(int limit);

    CompletableFuture<ReviewListView> listReviews(UUID islandId, int limit);
}
