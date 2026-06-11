package kr.lunaf.cloudislands.coreservice.repository;

import java.util.Optional;
import java.util.UUID;
import kr.lunaf.cloudislands.api.model.IslandRuntimeSnapshot;
import kr.lunaf.cloudislands.api.model.IslandState;

public interface IslandRuntimeRepository {
    Optional<IslandRuntimeSnapshot> find(UUID islandId);
    IslandRuntimeSnapshot markActivating(UUID islandId, String targetNode, String targetWorld, int cellX, int cellZ);
    IslandRuntimeSnapshot markActive(UUID islandId, String nodeId, String worldName, int cellX, int cellZ, long fencingToken);
    IslandRuntimeSnapshot markSaving(UUID islandId);
    IslandRuntimeSnapshot markInactive(UUID islandId);
    IslandRuntimeSnapshot markMigrating(UUID islandId, String targetNode);
    IslandRuntimeSnapshot markQuarantined(UUID islandId, String reason);
    IslandRuntimeSnapshot setState(UUID islandId, IslandState state);
}
