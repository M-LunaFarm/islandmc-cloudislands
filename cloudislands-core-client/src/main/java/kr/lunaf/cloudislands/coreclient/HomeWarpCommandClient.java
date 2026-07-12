package kr.lunaf.cloudislands.coreclient;

import java.util.UUID;
import java.util.concurrent.CompletableFuture;
import kr.lunaf.cloudislands.api.model.IslandLocation;

public interface HomeWarpCommandClient {
    CompletableFuture<HomeWarpActionView> setHome(UUID islandId, UUID actorUuid, String name, IslandLocation location);

    default CompletableFuture<HomeWarpActionView> deleteHome(UUID islandId, UUID actorUuid, String name) {
        return CompletableFuture.failedFuture(new UnsupportedOperationException("home deletion is not supported by this client"));
    }

    default CompletableFuture<HomeWarpActionView> adminDeleteHome(UUID islandId, String name) {
        return CompletableFuture.failedFuture(new UnsupportedOperationException("admin home deletion is not supported by this client"));
    }

    default CompletableFuture<HomeWarpActionView> setWarp(UUID islandId, UUID actorUuid, String name, IslandLocation location, boolean publicAccess) {
        return setWarp(islandId, actorUuid, name, location, publicAccess, "");
    }

    CompletableFuture<HomeWarpActionView> setWarp(UUID islandId, UUID actorUuid, String name, IslandLocation location, boolean publicAccess, String category);

    CompletableFuture<HomeWarpActionView> deleteWarp(UUID islandId, UUID actorUuid, String name);

    CompletableFuture<HomeWarpActionView> adminDeleteWarp(UUID islandId, String name);

    CompletableFuture<HomeWarpActionView> setWarpPublicAccess(UUID islandId, UUID actorUuid, String name, boolean publicAccess);
}
