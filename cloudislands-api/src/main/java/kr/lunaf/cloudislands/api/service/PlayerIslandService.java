package kr.lunaf.cloudislands.api.service;

import java.util.List;
import java.util.Optional;
import java.util.UUID;
import java.util.concurrent.CompletableFuture;
import kr.lunaf.cloudislands.api.model.IslandSnapshot;
import kr.lunaf.cloudislands.api.model.PlayerIslandProfile;

public interface PlayerIslandService {
    CompletableFuture<Optional<PlayerIslandProfile>> getProfile(UUID playerUuid);
    CompletableFuture<Optional<PlayerIslandProfile>> setPrimaryIsland(UUID playerUuid, UUID islandId);
    CompletableFuture<Optional<PlayerIslandProfile>> clearPrimaryIsland(UUID playerUuid);
    CompletableFuture<Optional<UUID>> getOwnedIslandId(UUID playerUuid);
    CompletableFuture<List<IslandSnapshot>> getJoinedIslands(UUID playerUuid);
    CompletableFuture<Boolean> hasIsland(UUID playerUuid);
}
