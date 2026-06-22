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
        return core.post("/v1/islands/level/recalculate", JdkCoreApiClient.jsonObject("islandId", islandId, "actorUuid", actorUuid)).thenApply(JdkProgressionCommandClient::levelView);
    }

    @Override
    public CompletableFuture<ProgressionUpgradePurchaseView> purchaseUpgrade(UUID islandId, UUID actorUuid, String upgradeKey) {
        requireId(islandId, "islandId");
        requireId(actorUuid, "actorUuid");
        return core.postWithResultBody("/v1/islands/upgrades/purchase", JdkCoreApiClient.jsonObject("islandId", islandId, "actorUuid", actorUuid, "upgradeKey", upgradeKey == null ? "" : upgradeKey))
            .thenApply(body -> upgradePurchaseResult(body, upgradeKey));
    }

    @Override
    public CompletableFuture<ProgressionMissionCompletionView> completeMission(UUID islandId, UUID actorUuid, String missionKey, String kind) {
        requireId(islandId, "islandId");
        requireId(actorUuid, "actorUuid");
        String normalizedKind = kind == null || kind.isBlank() ? "MISSION" : kind;
        return core.postWithResultBody("/v1/islands/missions/complete", JdkCoreApiClient.jsonObject("islandId", islandId, "actorUuid", actorUuid, "missionKey", missionKey == null ? "" : missionKey, "kind", normalizedKind))
            .thenApply(body -> missionCompletionResult(body, islandId, missionKey, normalizedKind));
    }

    @Override
    public CompletableFuture<ProgressionMissionCompletionView> progressMission(UUID islandId, UUID actorUuid, String missionKey, String kind, long amount) {
        requireId(islandId, "islandId");
        requireId(actorUuid, "actorUuid");
        String normalizedKind = kind == null || kind.isBlank() ? "MISSION" : kind;
        return core.postWithResultBody("/v1/islands/missions/progress", JdkCoreApiClient.jsonObject("islandId", islandId, "actorUuid", actorUuid, "missionKey", missionKey == null ? "" : missionKey, "kind", normalizedKind, "amount", Math.max(0L, amount)))
            .thenApply(body -> missionCompletionResult(body, islandId, missionKey, normalizedKind));
    }

    @Override
    public CompletableFuture<List<MissionProviderDefinitionSnapshot>> registerMissionProvider(String providerId, List<MissionProviderDefinitionSnapshot> definitions) {
        String normalizedProviderId = providerId == null || providerId.isBlank() ? "cloudislands" : providerId.trim();
        return core.postWithResultBody("/v1/addons/missions/register", JdkCoreApiClient.jsonObject("providerId", normalizedProviderId, "missions", JdkCoreApiClient.rawJson(missionDefinitionsJson(definitions))))
            .thenApply(JdkProgressionCommandClient::missionDefinitions);
    }

    static ProgressionUpgradePurchaseView upgradePurchaseResult(String body, String fallbackKey) {
        Map<?, ?> root = CoreJson.object(body);
        Map<?, ?> upgrade = SimpleJson.object(root.get("upgrade"));
        boolean accepted = CoreJson.accepted(root);
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

    static LevelView levelView(String body) {
        Map<?, ?> root = CoreJson.object(body);
        return new LevelView(
            text(root, "islandId"),
            SimpleJson.number(root.get("level")),
            text(root, "worth"),
            text(root, "calculatedAt").isBlank() ? text(root, "updatedAt") : text(root, "calculatedAt")
        );
    }

    static ProgressionMissionCompletionView missionCompletionResult(String body, UUID fallbackIslandId, String fallbackKey, String fallbackKind) {
        Map<?, ?> root = CoreJson.object(body);
        boolean accepted = CoreJson.accepted(root);
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

    static List<MissionProviderDefinitionSnapshot> missionDefinitions(String body) {
        Object parsed = CoreJson.value(body);
        List<?> entries = parsed instanceof List<?> ? SimpleJson.list(parsed) : SimpleJson.list(SimpleJson.object(parsed).get("missions"));
        return entries.stream()
            .map(entry -> missionDefinition(SimpleJson.object(entry)))
            .toList();
    }

    private static MissionProviderDefinitionSnapshot missionDefinition(Map<?, ?> object) {
        return new MissionProviderDefinitionSnapshot(
            text(object, "providerId"),
            text(object, "missionKey"),
            text(object, "kind").isBlank() ? "MISSION" : text(object, "kind"),
            text(object, "title"),
            SimpleJson.number(object.get("goal")),
            text(object, "reward"),
            bool(object.get("enabled"), true),
            instant(text(object, "updatedAt"))
        );
    }

    static String missionDefinitionsJson(List<MissionProviderDefinitionSnapshot> definitions) {
        return SimpleJson.stringify((definitions == null ? List.<MissionProviderDefinitionSnapshot>of() : definitions).stream()
            .map(JdkProgressionCommandClient::missionDefinitionJson)
            .toList());
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

    private static String text(Map<?, ?> object, String key) {
        return SimpleJson.text(object.get(key));
    }

    private static boolean bool(Object value, boolean fallback) {
        return value instanceof Boolean bool ? bool : (value == null ? fallback : Boolean.parseBoolean(SimpleJson.text(value)));
    }

}
