package kr.lunaf.cloudislands.coreclient;

import java.util.UUID;
import java.util.concurrent.CompletableFuture;
import kr.lunaf.cloudislands.api.model.IslandLocation;

public interface HomeWarpCommandClient {
    CompletableFuture<HomeWarpActionView> setHome(UUID islandId, UUID actorUuid, String name, IslandLocation location);

    default CompletableFuture<HomeWarpActionView> setWarp(UUID islandId, UUID actorUuid, String name, IslandLocation location, boolean publicAccess) {
        return setWarp(islandId, actorUuid, name, location, publicAccess, "");
    }

    CompletableFuture<HomeWarpActionView> setWarp(UUID islandId, UUID actorUuid, String name, IslandLocation location, boolean publicAccess, String category);

    CompletableFuture<HomeWarpActionView> deleteWarp(UUID islandId, UUID actorUuid, String name);

    CompletableFuture<HomeWarpActionView> setWarpPublicAccess(UUID islandId, UUID actorUuid, String name, boolean publicAccess);
}
