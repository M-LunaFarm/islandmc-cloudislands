package kr.lunaf.cloudislands.coreclient;

import java.util.UUID;
import java.util.List;
import java.util.concurrent.CompletableFuture;

public interface IslandQueryClient {
    CompletableFuture<CoreGuiViews.IslandInfoView> getIsland(UUID islandId);

    CompletableFuture<CoreGuiViews.IslandInfoView> getIslandByOwner(UUID ownerUuid);

    CompletableFuture<CoreGuiViews.IslandInfoView> findIslandByName(String islandName);

    CompletableFuture<List<CoreGuiViews.MemberView>> listMembers(UUID islandId);

    CompletableFuture<MemberPage> listMembers(UUID islandId, MemberCursor cursor);
}
