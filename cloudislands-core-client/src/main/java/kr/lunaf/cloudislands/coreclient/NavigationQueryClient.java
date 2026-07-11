package kr.lunaf.cloudislands.coreclient;

import java.util.List;
import java.util.UUID;
import java.util.concurrent.CompletableFuture;

public interface NavigationQueryClient {
    CompletableFuture<CoreGuiViews.PlayerProfileView> playerProfileByName(String playerName);

    CompletableFuture<List<CoreGuiViews.PlayerIslandView>> playerIslands(UUID playerUuid);

    CompletableFuture<List<CoreGuiViews.PublicIslandView>> publicIslands(int limit);

    default CompletableFuture<List<CoreGuiViews.PublicIslandView>> publicIslands(int offset, int limit) {
        int safeOffset = Math.max(0, offset);
        return publicIslands(Math.min(100, safeOffset + Math.max(1, limit)))
            .thenApply(islands -> islands.stream().skip(safeOffset).limit(Math.max(1, limit)).toList());
    }

    CompletableFuture<ReviewListView> listReviews(UUID islandId, int limit);
}
