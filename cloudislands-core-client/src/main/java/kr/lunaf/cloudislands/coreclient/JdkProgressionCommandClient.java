package kr.lunaf.cloudislands.coreclient;

import java.time.Instant;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import java.util.UUID;
import java.util.concurrent.CompletableFuture;
import kr.lunaf.cloudislands.api.model.MissionProviderDefinitionSnapshot;
import kr.lunaf.cloudislands.common.json.SimpleJson;

public final class JdkProgressionCommandClient implements ProgressionCommandClient {
    private final JdkCoreApiClient core;

    public JdkProgressionCommandClient(JdkCoreApiClient core) {
        if (core == null) {
            throw new IllegalArgumentException("core is required");
        }
        this.core = core;
    }

    @Override
    public CompletableFuture<LevelView> recalculateLevel(UUID islandId, UUID actorUuid) {
        requireId(islandId, "islandId");
        requireId(actorUuid, "actorUuid");
        return core.postBody("/v1/islands/level/recalculate", CoreJsonPayload.object("islandId", islandId, "actorUuid", actorUuid))
            .thenApply(CoreResponseBody::value)
            .thenApply(JdkProgressionCommandClient::levelView);
    }

    @Override
    public CompletableFuture<ProgressionUpgradePurchaseView> purchaseUpgrade(UUID islandId, UUID actorUuid, String upgradeKey) {
        requireId(islandId, "islandId");
        requireId(actorUuid, "actorUuid");
        return core.postResultBody("/v1/islands/upgrades/purchase", CoreJsonPayload.object("islandId", islandId, "actorUuid", actorUuid, "upgradeKey", upgradeKey == null ? "" : upgradeKey))
            .thenApply(CoreResponseBody::value)
            .thenApply(body -> upgradePurchaseResult(body, upgradeKey));
    }

    @Override
    public CompletableFuture<ProgressionMissionCompletionView> completeMission(UUID islandId, UUID actorUuid, String missionKey, String kind) {
        requireId(islandId, "islandId");
        requireId(actorUuid, "actorUuid");
        String normalizedKind = kind == null || kind.isBlank() ? "MISSION" : kind;
        return core.postResultBody("/v1/islands/missions/complete", CoreJsonPayload.object("islandId", islandId, "actorUuid", actorUuid, "missionKey", missionKey == null ? "" : missionKey, "kind", normalizedKind))
            .thenApply(CoreResponseBody::value)
            .thenApply(body -> missionCompletionResult(body, islandId, missionKey, normalizedKind));
    }

    @Override
    public CompletableFuture<ProgressionMissionCompletionView> progressMission(UUID islandId, UUID actorUuid, String missionKey, String kind, long amount) {
        requireId(islandId, "islandId");
        requireId(actorUuid, "actorUuid");
        String normalizedKind = kind == null || kind.isBlank() ? "MISSION" : kind;
        return core.postResultBody("/v1/islands/missions/progress", CoreJsonPayload.object("islandId", islandId, "actorUuid", actorUuid, "missionKey", missionKey == null ? "" : missionKey, "kind", normalizedKind, "amount", Math.max(0L, amount)))
            .thenApply(CoreResponseBody::value)
            .thenApply(body -> missionCompletionResult(body, islandId, missionKey, normalizedKind));
    }

    @Override
    public CompletableFuture<List<MissionProviderDefinitionSnapshot>> registerMissionProvider(String providerId, List<MissionProviderDefinitionSnapshot> definitions) {
        String normalizedProviderId = providerId == null || providerId.isBlank() ? "cloudislands" : providerId.trim();
        return core.postResultBody("/v1/addons/missions/register", CoreJsonPayload.object("providerId", normalizedProviderId, "missions", missionDefinitionsPayload(definitions)))
            .thenApply(CoreResponseBody::value)
            .thenApply(JdkProgressionCommandClient::missionDefinitions);
    }

    static ProgressionUpgradePurchaseView upgradePurchaseResult(String body, String fallbackKey) {
        Map<?, ?> root = CoreJson.object(body);
        Map<?, ?> upgrade = CoreJson.objectValue(root, "upgrade");
        boolean accepted = CoreJson.accepted(root);
        String code = CoreJson.text(root, "code");
        String upgradeKey = CoreJson.text(upgrade, "upgradeKey");
        if (upgradeKey.isBlank()) {
            upgradeKey = CoreJson.text(root, "upgradeKey");
        }
        if (upgradeKey.isBlank()) {
            upgradeKey = fallbackKey == null ? "" : fallbackKey;
        }
        return new ProgressionUpgradePurchaseView(
            accepted,
            code,
            CoreJson.text(upgrade, "islandId"),
            upgradeKey,
            CoreJson.text(upgrade, "type"),
            CoreJson.number(upgrade, "level"),
            CoreJson.text(root, "cost"),
            CoreJson.text(upgrade, "updatedAt")
        );
    }

    static LevelView levelView(String body) {
        Map<?, ?> root = CoreJson.object(body);
        return new LevelView(
            CoreJson.text(root, "islandId"),
            CoreJson.number(root, "level"),
            CoreJson.text(root, "worth"),
            CoreJson.text(root, "calculatedAt").isBlank() ? CoreJson.text(root, "updatedAt") : CoreJson.text(root, "calculatedAt")
        );
    }

    static ProgressionMissionCompletionView missionCompletionResult(String body, UUID fallbackIslandId, String fallbackKey, String fallbackKind) {
        Map<?, ?> root = CoreJson.object(body);
        boolean accepted = CoreJson.accepted(root);
        String code = CoreJson.text(root, "code");
        String missionKey = CoreJson.text(root, "missionKey");
        if (missionKey.isBlank()) {
            missionKey = fallbackKey == null ? "" : fallbackKey;
        }
        String islandId = CoreJson.text(root, "islandId");
        if (islandId.isBlank() && fallbackIslandId != null) {
            islandId = fallbackIslandId.toString();
        }
        String kind = CoreJson.text(root, "kind");
        if (kind.isBlank()) {
            kind = fallbackKind == null || fallbackKind.isBlank() ? "MISSION" : fallbackKind;
        }
        return new ProgressionMissionCompletionView(
            accepted,
            code,
            islandId,
            missionKey,
            kind,
            CoreJson.text(root, "title"),
            CoreJson.number(root, "progress"),
            CoreJson.number(root, "goal"),
            CoreJson.bool(root, "completed", accepted),
            CoreJson.text(root, "reward"),
            CoreJson.text(root, "updatedAt")
        );
    }

    static List<MissionProviderDefinitionSnapshot> missionDefinitions(String body) {
        return CoreJson.entries(body, "definitions", "missions").stream()
            .map(JdkProgressionCommandClient::missionDefinition)
            .toList();
    }

    private static MissionProviderDefinitionSnapshot missionDefinition(Map<?, ?> object) {
        String kind = CoreJson.text(object, "kind");
        return new MissionProviderDefinitionSnapshot(
            CoreJson.text(object, "providerId"),
            CoreJson.text(object, "missionKey"),
            kind.isBlank() ? "MISSION" : kind,
            CoreJson.text(object, "title"),
            CoreJson.number(object, "goal"),
            CoreJson.text(object, "reward"),
            CoreJson.bool(object, "enabled", true),
            instant(CoreJson.text(object, "updatedAt"))
        );
    }

    static String missionDefinitionsJson(List<MissionProviderDefinitionSnapshot> definitions) {
        return SimpleJson.stringify(missionDefinitionsPayload(definitions));
    }

    static List<Map<String, Object>> missionDefinitionsPayload(List<MissionProviderDefinitionSnapshot> definitions) {
        return (definitions == null ? List.<MissionProviderDefinitionSnapshot>of() : definitions).stream()
            .map(JdkProgressionCommandClient::missionDefinitionJson)
            .toList();
    }

    private static Map<String, Object> missionDefinitionJson(MissionProviderDefinitionSnapshot definition) {
        Map<String, Object> values = new LinkedHashMap<>();
        values.put("missionKey", definition.missionKey());
        values.put("kind", definition.kind());
        values.put("title", definition.title());
        values.put("goal", Math.max(1L, definition.goal()));
        values.put("reward", definition.reward());
        values.put("enabled", definition.enabled());
        return values;
    }

    private static Instant instant(String value) {
        if (value == null || value.isBlank()) {
            return Instant.EPOCH;
        }
        try {
            return Instant.parse(value);
        } catch (RuntimeException ignored) {
            return Instant.EPOCH;
        }
    }

    private static void requireId(UUID id, String name) {
        if (id == null) {
            throw new IllegalArgumentException(name + " is required");
        }
    }

}
