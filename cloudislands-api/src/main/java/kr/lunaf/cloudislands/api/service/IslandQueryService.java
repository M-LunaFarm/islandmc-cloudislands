package kr.lunaf.cloudislands.api.service;

import java.util.List;
import java.util.Optional;
import java.util.UUID;
import java.util.concurrent.CompletableFuture;
import kr.lunaf.cloudislands.api.model.IslandFlagsSnapshot;
import kr.lunaf.cloudislands.api.model.IslandLevelSnapshot;
import kr.lunaf.cloudislands.api.model.IslandMemberSnapshot;
import kr.lunaf.cloudislands.api.model.IslandRuntimeSnapshot;
import kr.lunaf.cloudislands.api.model.IslandSnapshot;
import kr.lunaf.cloudislands.api.model.IslandWarpSnapshot;

public interface IslandQueryService {
    CompletableFuture<Optional<IslandSnapshot>> getIsland(UUID islandId);
    CompletableFuture<Optional<IslandSnapshot>> getIslandByOwner(UUID ownerUuid);
    CompletableFuture<List<IslandMemberSnapshot>> getMembers(UUID islandId);
    CompletableFuture<List<IslandWarpSnapshot>> getWarps(UUID islandId);
    CompletableFuture<IslandFlagsSnapshot> getFlags(UUID islandId);
    CompletableFuture<IslandLevelSnapshot> getLevel(UUID islandId);
    CompletableFuture<IslandRuntimeSnapshot> getRuntime(UUID islandId);
}
