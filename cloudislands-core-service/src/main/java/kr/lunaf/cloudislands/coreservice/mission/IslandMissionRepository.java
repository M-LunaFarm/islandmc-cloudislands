package kr.lunaf.cloudislands.coreservice.mission;

import java.util.List;
import java.util.Optional;
import java.util.UUID;
import kr.lunaf.cloudislands.api.model.IslandMissionSnapshot;

public interface IslandMissionRepository {
    List<IslandMissionSnapshot> list(UUID islandId, String kind);
    Optional<IslandMissionSnapshot> complete(UUID islandId, UUID actorUuid, String missionKey);
}
