package kr.lunaf.cloudislands.coreservice.http.routes;

import com.sun.net.httpserver.HttpExchange;
import java.io.IOException;
import java.util.ArrayList;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import java.util.UUID;
import kr.lunaf.cloudislands.api.model.MissionProviderDefinitionSnapshot;
import kr.lunaf.cloudislands.api.model.IslandPermission;
import kr.lunaf.cloudislands.api.model.IslandRole;
import kr.lunaf.cloudislands.common.event.CloudIslandEventType;
import kr.lunaf.cloudislands.common.json.SimpleJson;
import kr.lunaf.cloudislands.coreservice.audit.AuditLogger;
import kr.lunaf.cloudislands.coreservice.event.GlobalEventPublisher;
import kr.lunaf.cloudislands.coreservice.http.ApiResponses;
import kr.lunaf.cloudislands.coreservice.http.CoreHttpResponses;
import kr.lunaf.cloudislands.coreservice.http.CoreRouteRegistry;
import kr.lunaf.cloudislands.coreservice.http.JsonFields;
import kr.lunaf.cloudislands.coreservice.http.RouteGroup;
import kr.lunaf.cloudislands.coreservice.islandlog.IslandLogRepository;
import kr.lunaf.cloudislands.coreservice.limit.IslandLimitRepository;
import kr.lunaf.cloudislands.coreservice.mission.IslandMissionRepository;
import kr.lunaf.cloudislands.coreservice.permission.IslandPermissionRuleRepository;
import kr.lunaf.cloudislands.coreservice.ranking.IslandLevelRepository;
import kr.lunaf.cloudislands.coreservice.ranking.RankingRecalculationService;
import kr.lunaf.cloudislands.coreservice.ranking.RankingRepository;
import kr.lunaf.cloudislands.coreservice.repository.IslandMetadataRepository;
import kr.lunaf.cloudislands.coreservice.repository.IslandRepository;
import kr.lunaf.cloudislands.coreservice.upgrade.UpgradePolicy;
import kr.lunaf.cloudislands.coreservice.upgrade.UpgradeRule;

public final class ProgressionRoutes implements RouteGroup {
    private final RankingRepository rankingRepository;
    private final UpgradePolicy upgradePolicy;
    private final IslandLevelRepository levelRepository;
    private final IslandMissionRepository missionRepository;
    private final IslandLimitRepository limitRepository;
    private final IslandRepository islandRepository;
    private final IslandMetadataRepository metadataRepository;
    private final IslandPermissionRuleRepository permissionRules;
    private final IslandLogRepository islandLogs;
    private final AuditLogger audit;
    private final GlobalEventPublisher events;

    public ProgressionRoutes(
            RankingRepository rankingRepository,
            UpgradePolicy upgradePolicy,
            IslandLevelRepository levelRepository,
            IslandMissionRepository missionRepository,
            IslandLimitRepository limitRepository,
            IslandRepository islandRepository,
            IslandMetadataRepository metadataRepository,
            IslandPermissionRuleRepository permissionRules,
            IslandLogRepository islandLogs,
            AuditLogger audit,
            GlobalEventPublisher events) {
        this.rankingRepository = rankingRepository;
        this.upgradePolicy = upgradePolicy;
        this.levelRepository = levelRepository;
        this.missionRepository = missionRepository;
        this.limitRepository = limitRepository;
        this.islandRepository = islandRepository;
        this.metadataRepository = metadataRepository;
        this.permissionRules = permissionRules;
        this.islandLogs = islandLogs;
        this.audit = audit;
        this.events = events;
    }

    @Override
    public void register(CoreRouteRegistry registry) {
        registry.routePost("/v1/rankings/level", exchange -> {
            String body = CoreHttpResponses.readBody(exchange);
            CoreHttpResponses.write(exchange, 200, rankingsJson(rankingRepository.topByLevel(queryInteger(exchange, "limit", JsonFields.integer(body, "limit", 10), 1, 100))));
        });
        registry.routePost("/v1/rankings/worth", exchange -> {
            String body = CoreHttpResponses.readBody(exchange);
            CoreHttpResponses.write(exchange, 200, rankingsJson(rankingRepository.topByWorth(queryInteger(exchange, "limit", JsonFields.integer(body, "limit", 10), 1, 100))));
        });
        registry.routePost("/v1/upgrades/rules", exchange -> CoreHttpResponses.write(exchange, 200, upgradeRulesJson(upgradePolicy.list())));
        registry.routePost("/v1/islands/missions", exchange -> {
            String body = CoreHttpResponses.readBody(exchange);
            CoreHttpResponses.write(exchange, 200, missionsJson(missionRepository.list(JsonFields.uuid(body, "islandId", new UUID(0L, 0L)), JsonFields.text(body, "kind", "MISSION"))));
        });
        registry.routePost("/v1/addons/missions/register", exchange -> {
            String body = CoreHttpResponses.readBody(exchange);
            String providerId = JsonFields.text(body, "providerId", "");
            java.util.List<MissionProviderDefinitionSnapshot> definitions = missionDefinitions(body, providerId);
            if (providerId.isBlank() || definitions.isEmpty()) {
                CoreHttpResponses.write(exchange, 400, ApiResponses.error("INVALID_MISSION_PROVIDER", "Provider id and at least one mission definition are required"));
                return;
            }
            java.util.List<MissionProviderDefinitionSnapshot> registered = missionRepository.registerProviderDefinitions(providerId, definitions);
            audit.log(new UUID(0L, 0L), "API", "MISSION_PROVIDER_REGISTER", "ADDON", providerId, Map.of("missions", Integer.toString(registered.size())));
            events.publish(CloudIslandEventType.CORE_CACHE_CLEARED.name(), Map.of("cacheTargets", "ISLAND_MISSIONS", "providerId", providerId));
            CoreHttpResponses.write(exchange, 202, missionDefinitionsJson(registered));
        });
        registry.routePost("/v1/islands/missions/complete", exchange -> {
            String body = CoreHttpResponses.readBody(exchange);
            UUID islandId = JsonFields.uuid(body, "islandId", new UUID(0L, 0L));
            UUID actorUuid = JsonFields.uuid(body, "actorUuid", new UUID(0L, 0L));
            String missionKey = JsonFields.text(body, "missionKey", "");
            String kind = JsonFields.text(body, "kind", "MISSION");
            if (!requireMember(exchange, islandRepository, metadataRepository, islandId, actorUuid)) {
                return;
            }
            java.util.Optional<kr.lunaf.cloudislands.api.model.IslandMissionSnapshot> completed = missionRepository.complete(islandId, actorUuid, missionKey, kind);
            completed.ifPresent(snapshot -> {
                audit.log(actorUuid, "PLAYER", "ISLAND_MISSION_COMPLETE", "ISLAND", islandId.toString(), Map.of("missionKey", snapshot.missionKey(), "kind", snapshot.kind()));
                islandLogs.append(islandId, actorUuid, "ISLAND_MISSION_COMPLETE", Map.of("missionKey", snapshot.missionKey(), "kind", snapshot.kind(), "reward", snapshot.reward()));
                events.publish(CloudIslandEventType.ISLAND_MISSION_COMPLETED.name(), Map.of("islandId", islandId.toString(), "missionKey", snapshot.missionKey(), "kind", snapshot.kind()));
            });
            CoreHttpResponses.write(exchange, completed.isPresent() ? 202 : 404, completed.map(ProgressionRoutes::missionJson).orElseGet(() -> ApiResponses.error("MISSION_NOT_FOUND", "Mission was not found")));
        });
        registry.routePost("/v1/islands/missions/progress", exchange -> {
            String body = CoreHttpResponses.readBody(exchange);
            UUID islandId = JsonFields.uuid(body, "islandId", new UUID(0L, 0L));
            UUID actorUuid = JsonFields.uuid(body, "actorUuid", new UUID(0L, 0L));
            String missionKey = JsonFields.text(body, "missionKey", "");
            String kind = JsonFields.text(body, "kind", "MISSION");
            long amount = Math.max(0L, JsonFields.longValue(body, "amount", 1L));
            if (!requireMember(exchange, islandRepository, metadataRepository, islandId, actorUuid)) {
                return;
            }
            java.util.Optional<kr.lunaf.cloudislands.api.model.IslandMissionSnapshot> progressed = missionRepository.progress(islandId, actorUuid, missionKey, kind, amount);
            progressed.ifPresent(snapshot -> events.publish(CloudIslandEventType.ISLAND_MISSION_PROGRESS.name(), Map.of(
                "islandId", islandId.toString(),
                "missionKey", snapshot.missionKey(),
                "kind", snapshot.kind(),
                "progress", Long.toString(snapshot.progress()),
                "goal", Long.toString(snapshot.goal()),
                "amount", Long.toString(amount),
                "completed", Boolean.toString(snapshot.completed())
            )));
            progressed.filter(kr.lunaf.cloudislands.api.model.IslandMissionSnapshot::completed).ifPresent(snapshot -> {
                audit.log(actorUuid, "PLAYER", "ISLAND_MISSION_COMPLETE", "ISLAND", islandId.toString(), Map.of("missionKey", snapshot.missionKey(), "kind", snapshot.kind()));
                islandLogs.append(islandId, actorUuid, "ISLAND_MISSION_COMPLETE", Map.of("missionKey", snapshot.missionKey(), "kind", snapshot.kind(), "reward", snapshot.reward()));
                events.publish(CloudIslandEventType.ISLAND_MISSION_COMPLETED.name(), Map.of("islandId", islandId.toString(), "missionKey", snapshot.missionKey(), "kind", snapshot.kind()));
            });
            CoreHttpResponses.write(exchange, progressed.isPresent() ? 202 : 404, progressed.map(ProgressionRoutes::missionJson).orElseGet(() -> ApiResponses.error("MISSION_NOT_FOUND", "Mission was not found")));
        });
        registry.routePost("/v1/islands/limits", exchange -> {
            String body = CoreHttpResponses.readBody(exchange);
            CoreHttpResponses.write(exchange, 200, limitsJson(limitRepository.list(JsonFields.uuid(body, "islandId", new UUID(0L, 0L)))));
        });
        registry.routePost("/v1/islands/limits/set", exchange -> {
            String body = CoreHttpResponses.readBody(exchange);
            UUID islandId = JsonFields.uuid(body, "islandId", new UUID(0L, 0L));
            UUID actorUuid = JsonFields.uuid(body, "actorUuid", new UUID(0L, 0L));
            String limitKey = JsonFields.text(body, "limitKey", "HOPPER");
            long value = JsonFields.longValue(body, "value", 0L);
            if (!requireIslandPermission(exchange, islandRepository, metadataRepository, permissionRules, events, islandId, actorUuid, IslandPermission.MANAGE_UPGRADES)) {
                return;
            }
            kr.lunaf.cloudislands.api.model.IslandLimitSnapshot snapshot = limitRepository.set(islandId, limitKey, value, actorUuid);
            audit.log(actorUuid, "PLAYER", "ISLAND_LIMIT_SET", "ISLAND", islandId.toString(), Map.of("limitKey", snapshot.limitKey(), "value", Long.toString(snapshot.value())));
            islandLogs.append(islandId, actorUuid, "ISLAND_LIMIT_SET", Map.of("limitKey", snapshot.limitKey(), "value", Long.toString(snapshot.value())));
            events.publish(CloudIslandEventType.ISLAND_LIMIT_CHANGED.name(), Map.of("islandId", islandId.toString(), "limitKey", snapshot.limitKey(), "value", Long.toString(snapshot.value())));
            CoreHttpResponses.write(exchange, 202, limitJson(snapshot));
        });
    }

    private static int queryInteger(HttpExchange exchange, String key, int fallback, int min, int max) {
        String query = exchange.getRequestURI().getRawQuery();
        if (query == null || query.isBlank()) {
            return Math.max(min, Math.min(fallback, max));
        }
        for (String part : query.split("&")) {
            int separator = part.indexOf('=');
            if (separator <= 0 || !part.substring(0, separator).equals(key)) {
                continue;
            }
            try {
                return Math.max(min, Math.min(Integer.parseInt(part.substring(separator + 1)), max));
            } catch (NumberFormatException ignored) {
                return Math.max(min, Math.min(fallback, max));
            }
        }
        return Math.max(min, Math.min(fallback, max));
    }

    private static boolean requireIslandPermission(HttpExchange exchange, IslandRepository islandRepository, IslandMetadataRepository metadataRepository, IslandPermissionRuleRepository permissionRules, GlobalEventPublisher events, UUID islandId, UUID actorUuid, IslandPermission permission) throws IOException {
        boolean owner = islandRepository.findById(islandId)
            .map(island -> island.ownerUuid().equals(actorUuid))
            .orElse(false);
        boolean allowed = metadataRepository.members(islandId).stream()
            .anyMatch(member -> member.playerUuid().equals(actorUuid) && permissionRules.allowedRoleKey(islandId, actorUuid, member.effectiveRoleKey(), permission));
        boolean accepted = owner || allowed;
        events.publish(CloudIslandEventType.ISLAND_PERMISSION_CHECKED.name(), Map.of(
            "islandId", islandId.toString(),
            "playerUuid", actorUuid.toString(),
            "permission", permission.name(),
            "allowed", Boolean.toString(accepted)
        ));
        if (accepted) {
            return true;
        }
        CoreHttpResponses.write(exchange, 403, ApiResponses.error("ISLAND_PERMISSION_DENIED", "Island permission " + permission.name() + " is required"));
        return false;
    }

    private static boolean requireMember(HttpExchange exchange, IslandRepository islandRepository, IslandMetadataRepository metadataRepository, UUID islandId, UUID actorUuid) throws IOException {
        boolean owner = islandRepository.findById(islandId)
            .map(island -> island.ownerUuid().equals(actorUuid))
            .orElse(false);
        boolean member = metadataRepository.members(islandId).stream()
            .anyMatch(record -> record.playerUuid().equals(actorUuid) && record.role() != IslandRole.VISITOR && record.role() != IslandRole.BANNED);
        if (owner || member) {
            return true;
        }
        CoreHttpResponses.write(exchange, 403, ApiResponses.error("ISLAND_PERMISSION_DENIED", "Island member permission is required"));
        return false;
    }

    private static String levelJson(kr.lunaf.cloudislands.coreservice.ranking.IslandRankSnapshot snapshot) {
        LinkedHashMap<String, Object> values = new LinkedHashMap<>();
        values.put("islandId", snapshot.islandId());
        values.put("level", snapshot.level());
        values.put("worth", snapshot.worth().toPlainString());
        values.put("calculatedAt", snapshot.updatedAt());
        return SimpleJson.stringify(values);
    }

    private static String rankingsJson(java.util.List<kr.lunaf.cloudislands.coreservice.ranking.IslandRankSnapshot> rankings) {
        List<Object> renderedRankings = new ArrayList<>();
        for (kr.lunaf.cloudislands.coreservice.ranking.IslandRankSnapshot ranking : rankings) {
            renderedRankings.add(levelMap(ranking));
        }
        return SimpleJson.stringify(Map.of("rankings", renderedRankings));
    }

    private static String upgradeRulesJson(java.util.List<UpgradeRule> rules) {
        List<Object> renderedRules = new ArrayList<>();
        for (UpgradeRule rule : rules) {
            LinkedHashMap<String, Object> rendered = new LinkedHashMap<>();
            rendered.put("upgradeKey", rule.upgradeKey());
            rendered.put("type", rule.type());
            rendered.put("maxLevel", rule.maxLevel());
            rendered.put("baseCost", rule.baseCost().toPlainString());
            rendered.put("multiplier", rule.multiplier().toPlainString());
            renderedRules.add(rendered);
        }
        return SimpleJson.stringify(Map.of("rules", renderedRules));
    }

    private static String blockValuesJson(Map<String, RankingRecalculationService.BlockValue> values) {
        List<Object> renderedValues = new ArrayList<>();
        for (Map.Entry<String, RankingRecalculationService.BlockValue> entry : values.entrySet()) {
            RankingRecalculationService.BlockValue value = entry.getValue();
            LinkedHashMap<String, Object> rendered = new LinkedHashMap<>();
            rendered.put("materialKey", entry.getKey());
            rendered.put("worth", value.worth().toPlainString());
            rendered.put("levelPoints", value.levelPoints());
            rendered.put("limit", value.limit());
            renderedValues.add(rendered);
        }
        return SimpleJson.stringify(Map.of("values", renderedValues));
    }

    static String missionsJson(java.util.List<kr.lunaf.cloudislands.api.model.IslandMissionSnapshot> missions) {
        List<Object> renderedMissions = new ArrayList<>();
        for (kr.lunaf.cloudislands.api.model.IslandMissionSnapshot mission : missions) {
            renderedMissions.add(missionMap(mission));
        }
        return SimpleJson.stringify(Map.of("missions", renderedMissions));
    }

    static String missionJson(kr.lunaf.cloudislands.api.model.IslandMissionSnapshot mission) {
        return SimpleJson.stringify(missionMap(mission));
    }

    static java.util.List<MissionProviderDefinitionSnapshot> missionDefinitions(String json, String providerId) {
        java.util.List<MissionProviderDefinitionSnapshot> definitions = new java.util.ArrayList<>();
        for (String object : JsonFields.objects(json, "missions")) {
            MissionProviderDefinitionSnapshot definition = new MissionProviderDefinitionSnapshot(
                providerId,
                JsonFields.text(object, "missionKey", ""),
                JsonFields.text(object, "kind", "MISSION"),
                JsonFields.text(object, "title", ""),
                JsonFields.longValue(object, "goal", 1L),
                JsonFields.text(object, "reward", ""),
                JsonFields.bool(object, "enabled", true),
                java.time.Instant.EPOCH
            );
            if (!definition.missionKey().isBlank()) {
                definitions.add(definition);
            }
        }
        return java.util.List.copyOf(definitions);
    }

    static String missionDefinitionsJson(java.util.List<MissionProviderDefinitionSnapshot> definitions) {
        List<Object> renderedDefinitions = new ArrayList<>();
        for (MissionProviderDefinitionSnapshot definition : definitions) {
            renderedDefinitions.add(definitionMap(definition));
        }
        return SimpleJson.stringify(Map.of("missions", renderedDefinitions));
    }

    static String limitsJson(java.util.List<kr.lunaf.cloudislands.api.model.IslandLimitSnapshot> limits) {
        List<Object> renderedLimits = new ArrayList<>();
        for (kr.lunaf.cloudislands.api.model.IslandLimitSnapshot limit : limits) {
            renderedLimits.add(limitMap(limit));
        }
        return SimpleJson.stringify(Map.of("limits", renderedLimits));
    }

    static String limitJson(kr.lunaf.cloudislands.api.model.IslandLimitSnapshot limit) {
        return SimpleJson.stringify(limitMap(limit));
    }

    private static Map<String, Object> levelMap(kr.lunaf.cloudislands.coreservice.ranking.IslandRankSnapshot snapshot) {
        LinkedHashMap<String, Object> values = new LinkedHashMap<>();
        values.put("islandId", snapshot.islandId());
        values.put("level", snapshot.level());
        values.put("worth", snapshot.worth().toPlainString());
        values.put("calculatedAt", snapshot.updatedAt());
        return values;
    }

    private static Map<String, Object> missionMap(kr.lunaf.cloudislands.api.model.IslandMissionSnapshot mission) {
        LinkedHashMap<String, Object> values = new LinkedHashMap<>();
        values.put("islandId", mission.islandId());
        values.put("missionKey", mission.missionKey());
        values.put("kind", mission.kind());
        values.put("title", mission.title());
        values.put("progress", mission.progress());
        values.put("goal", mission.goal());
        values.put("completed", mission.completed());
        values.put("reward", mission.reward());
        values.put("updatedAt", mission.updatedAt());
        return values;
    }

    private static Map<String, Object> definitionMap(MissionProviderDefinitionSnapshot definition) {
        LinkedHashMap<String, Object> values = new LinkedHashMap<>();
        values.put("providerId", definition.providerId());
        values.put("missionKey", definition.missionKey());
        values.put("kind", definition.kind());
        values.put("title", definition.title());
        values.put("goal", definition.goal());
        values.put("reward", definition.reward());
        values.put("enabled", definition.enabled());
        values.put("updatedAt", definition.updatedAt());
        return values;
    }

    private static Map<String, Object> limitMap(kr.lunaf.cloudislands.api.model.IslandLimitSnapshot limit) {
        LinkedHashMap<String, Object> values = new LinkedHashMap<>();
        values.put("islandId", limit.islandId());
        values.put("limitKey", limit.limitKey());
        values.put("value", limit.value());
        values.put("updatedBy", limit.updatedBy());
        values.put("updatedAt", limit.updatedAt());
        return values;
    }
}
