package kr.lunaf.cloudislands.coreclient;

import java.util.UUID;
import java.util.concurrent.CompletableFuture;

public interface IslandQueryClient {
    CompletableFuture<CoreGuiViews.IslandInfoView> getIsland(UUID islandId);

    CompletableFuture<MemberPage> listMembers(UUID islandId, MemberCursor cursor);
}
