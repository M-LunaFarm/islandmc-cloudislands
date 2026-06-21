package kr.lunaf.cloudislands.coreclient;

import java.util.UUID;
import java.util.concurrent.CompletableFuture;

public interface AdminIslandQueryClient {
    CompletableFuture<CoreGuiViews.IslandInfoView> info(UUID lookupUuid);

    CompletableFuture<CoreGuiViews.IslandInfoView> infoByName(String islandName);

    CompletableFuture<AdminIslandRuntimeView> runtime(UUID islandId);
}
