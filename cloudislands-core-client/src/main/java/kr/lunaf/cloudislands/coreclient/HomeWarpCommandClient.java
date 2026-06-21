package kr.lunaf.cloudislands.coreclient;

import java.util.UUID;
import java.util.concurrent.CompletableFuture;
import kr.lunaf.cloudislands.api.model.IslandLocation;

public interface HomeWarpCommandClient {
    CompletableFuture<HomeWarpActionView> setHome(UUID islandId, UUID actorUuid, String name, IslandLocation location);

    CompletableFuture<HomeWarpActionView> setWarp(UUID islandId, UUID actorUuid, String name, IslandLocation location, boolean publicAccess);

    CompletableFuture<HomeWarpActionView> deleteWarp(UUID islandId, UUID actorUuid, String name);

    CompletableFuture<HomeWarpActionView> setWarpPublicAccess(UUID islandId, UUID actorUuid, String name, boolean publicAccess);
}
