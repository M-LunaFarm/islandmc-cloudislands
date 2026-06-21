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
    public CompletableFuture<LevelView> recalculateLevel(UUID islandId, UUID actorUuid) {
        requireId(islandId, "islandId");
        requireId(actorUuid, "actorUuid");
        return delegate.recalculateIslandLevel(islandId, actorUuid).thenApply(CoreProgressionCommandClient::levelView);
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
            .thenApply(body -> missionCompletionResult(body, islandId, missionKey, normalizedKind));
    }

    @Override
    public CompletableFuture<ProgressionMissionCompletionView> progressMission(UUID islandId, UUID actorUuid, String missionKey, String kind, long amount) {
        requireId(islandId, "islandId");
        requireId(actorUuid, "actorUuid");
        String normalizedKind = kind == null || kind.isBlank() ? "MISSION" : kind;
        return delegate.progressIslandMission(islandId, actorUuid, missionKey == null ? "" : missionKey, normalizedKind, amount)
            .thenApply(body -> missionCompletionResult(body, islandId, missionKey, normalizedKind));
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
        return new ProgressionUpgradePurchaseView(
            accepted,
            code,
            text(upgrade, "islandId"),
            upgradeKey,
            text(upgrade, "type"),
            SimpleJson.number(upgrade.get("level")),
            text(root, "cost"),
            text(upgrade, "updatedAt")
        );
    }

    private static LevelView levelView(String body) {
        Map<?, ?> root = SimpleJson.object(SimpleJson.parse(body));
        return new LevelView(
            text(root, "islandId"),
            SimpleJson.number(root.get("level")),
            text(root, "worth"),
            text(root, "calculatedAt").isBlank() ? text(root, "updatedAt") : text(root, "calculatedAt")
        );
    }

    private static ProgressionMissionCompletionView missionCompletionResult(String body, UUID fallbackIslandId, String fallbackKey, String fallbackKind) {
        Map<?, ?> root = SimpleJson.object(SimpleJson.parse(body));
        boolean accepted = accepted(root);
        String code = text(root, "code");
        String missionKey = text(root, "missionKey");
        if (missionKey.isBlank()) {
            missionKey = fallbackKey == null ? "" : fallbackKey;
        }
        String islandId = text(root, "islandId");
        if (islandId.isBlank() && fallbackIslandId != null) {
            islandId = fallbackIslandId.toString();
        }
        String kind = text(root, "kind");
        if (kind.isBlank()) {
            kind = fallbackKind == null || fallbackKind.isBlank() ? "MISSION" : fallbackKind;
        }
        return new ProgressionMissionCompletionView(
            accepted,
            code,
            islandId,
            missionKey,
            kind,
            text(root, "title"),
            SimpleJson.number(root.get("progress")),
            SimpleJson.number(root.get("goal")),
            bool(root.get("completed"), accepted),
            text(root, "reward"),
            text(root, "updatedAt")
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

    private static boolean bool(Object value, boolean fallback) {
        return value instanceof Boolean bool ? bool : (value == null ? fallback : Boolean.parseBoolean(SimpleJson.text(value)));
    }
}
