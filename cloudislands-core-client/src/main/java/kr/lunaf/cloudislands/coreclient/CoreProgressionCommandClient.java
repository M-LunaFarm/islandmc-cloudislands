package kr.lunaf.cloudislands.coreclient;

import java.util.Map;
import java.util.UUID;
import java.util.concurrent.CompletableFuture;
import kr.lunaf.cloudislands.common.json.SimpleJson;

public final class CoreProgressionCommandClient implements ProgressionCommandClient {
    private final CoreApiClient delegate;

    public CoreProgressionCommandClient(CoreApiClient delegate) {
        if (delegate == null) {
            throw new IllegalArgumentException("delegate is required");
        }
        this.delegate = delegate;
    }

    @Override
    public CompletableFuture<CoreGuiViews.IslandInfoView> recalculateLevel(UUID islandId, UUID actorUuid) {
        requireId(islandId, "islandId");
        requireId(actorUuid, "actorUuid");
        return delegate.recalculateIslandLevel(islandId, actorUuid).thenApply(CoreGuiViews::islandInfoView);
    }

    @Override
    public CompletableFuture<ProgressionUpgradePurchaseView> purchaseUpgrade(UUID islandId, UUID actorUuid, String upgradeKey) {
        requireId(islandId, "islandId");
        requireId(actorUuid, "actorUuid");
        return delegate.purchaseIslandUpgrade(islandId, actorUuid, upgradeKey == null ? "" : upgradeKey)
            .thenApply(body -> upgradePurchaseResult(body, upgradeKey));
    }

    @Override
    public CompletableFuture<ProgressionMissionCompletionView> completeMission(UUID islandId, UUID actorUuid, String missionKey, String kind) {
        requireId(islandId, "islandId");
        requireId(actorUuid, "actorUuid");
        String normalizedKind = kind == null || kind.isBlank() ? "MISSION" : kind;
        return delegate.completeIslandMission(islandId, actorUuid, missionKey == null ? "" : missionKey, normalizedKind)
            .thenApply(body -> missionCompletionResult(body, missionKey));
    }

    private static ProgressionUpgradePurchaseView upgradePurchaseResult(String body, String fallbackKey) {
        Map<?, ?> root = SimpleJson.object(SimpleJson.parse(body));
        Map<?, ?> upgrade = SimpleJson.object(root.get("upgrade"));
        boolean accepted = accepted(root);
        String code = text(root, "code");
        String upgradeKey = text(upgrade, "upgradeKey");
        if (upgradeKey.isBlank()) {
            upgradeKey = text(root, "upgradeKey");
        }
        if (upgradeKey.isBlank()) {
            upgradeKey = fallbackKey == null ? "" : fallbackKey;
        }
        return new ProgressionUpgradePurchaseView(accepted, code, upgradeKey, SimpleJson.number(upgrade.get("level")), text(root, "cost"));
    }

    private static ProgressionMissionCompletionView missionCompletionResult(String body, String fallbackKey) {
        Map<?, ?> root = SimpleJson.object(SimpleJson.parse(body));
        boolean accepted = accepted(root);
        String code = text(root, "code");
        return new ProgressionMissionCompletionView(
            accepted,
            code,
            text(root, "missionKey").isBlank() ? fallbackKey : text(root, "missionKey"),
            text(root, "title"),
            text(root, "reward")
        );
    }

    private static boolean accepted(Map<?, ?> root) {
        return !root.containsKey("error")
            && !Boolean.FALSE.equals(root.get("accepted"))
            && !Boolean.FALSE.equals(root.get("applied"));
    }

    private static void requireId(UUID id, String name) {
        if (id == null) {
            throw new IllegalArgumentException(name + " is required");
        }
    }

    private static String text(Map<?, ?> object, String key) {
        return SimpleJson.text(object.get(key));
    }
}
