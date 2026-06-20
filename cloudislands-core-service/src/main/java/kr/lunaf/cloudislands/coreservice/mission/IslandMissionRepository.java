package kr.lunaf.cloudislands.coreservice.mission;

import java.util.List;
import java.util.Optional;
import java.util.UUID;
import kr.lunaf.cloudislands.api.model.IslandMissionSnapshot;
import kr.lunaf.cloudislands.api.model.MissionProviderDefinitionSnapshot;

public interface IslandMissionRepository {
    List<IslandMissionSnapshot> list(UUID islandId, String kind);
    default Optional<IslandMissionSnapshot> complete(UUID islandId, UUID actorUuid, String missionKey) {
        return complete(islandId, actorUuid, missionKey, "MISSION");
    }
    Optional<IslandMissionSnapshot> complete(UUID islandId, UUID actorUuid, String missionKey, String kind);
    Optional<IslandMissionSnapshot> progress(UUID islandId, UUID actorUuid, String missionKey, String kind, long amount);
    IslandMissionSnapshot importCompleted(UUID islandId, UUID actorUuid, String missionKey, String kind);
    List<MissionProviderDefinitionSnapshot> listProviderDefinitions(String providerId);
    List<MissionProviderDefinitionSnapshot> registerProviderDefinitions(String providerId, List<MissionProviderDefinitionSnapshot> definitions);
}
