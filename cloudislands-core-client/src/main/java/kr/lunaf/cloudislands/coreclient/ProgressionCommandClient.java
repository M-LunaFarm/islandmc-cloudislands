package kr.lunaf.cloudislands.coreclient;

import java.util.UUID;
import java.util.concurrent.CompletableFuture;

public interface ProgressionCommandClient {
    CompletableFuture<CoreGuiViews.IslandInfoView> recalculateLevel(UUID islandId, UUID actorUuid);

    CompletableFuture<ProgressionUpgradePurchaseView> purchaseUpgrade(UUID islandId, UUID actorUuid, String upgradeKey);

    CompletableFuture<ProgressionMissionCompletionView> completeMission(UUID islandId, UUID actorUuid, String missionKey, String kind);
}
