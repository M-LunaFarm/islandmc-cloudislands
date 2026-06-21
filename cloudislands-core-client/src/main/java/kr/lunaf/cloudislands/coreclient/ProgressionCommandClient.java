package kr.lunaf.cloudislands.coreclient;

import java.util.List;
import java.util.UUID;
import java.util.concurrent.CompletableFuture;
import kr.lunaf.cloudislands.api.model.MissionProviderDefinitionSnapshot;

public interface ProgressionCommandClient {
    CompletableFuture<LevelView> recalculateLevel(UUID islandId, UUID actorUuid);

    CompletableFuture<ProgressionUpgradePurchaseView> purchaseUpgrade(UUID islandId, UUID actorUuid, String upgradeKey);

    CompletableFuture<ProgressionMissionCompletionView> completeMission(UUID islandId, UUID actorUuid, String missionKey, String kind);

    CompletableFuture<ProgressionMissionCompletionView> progressMission(UUID islandId, UUID actorUuid, String missionKey, String kind, long amount);

    CompletableFuture<List<MissionProviderDefinitionSnapshot>> registerMissionProvider(String providerId, List<MissionProviderDefinitionSnapshot> definitions);
}
