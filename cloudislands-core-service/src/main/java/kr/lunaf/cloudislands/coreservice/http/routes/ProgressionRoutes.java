package kr.lunaf.cloudislands.coreservice.http.routes;

import com.sun.net.httpserver.HttpExchange;
import java.io.IOException;
import java.util.Map;
import java.util.UUID;
import kr.lunaf.cloudislands.api.model.IslandPermission;
import kr.lunaf.cloudislands.api.model.IslandRole;
import kr.lunaf.cloudislands.common.event.CloudIslandEventType;
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
        registry.route("/v1/rankings/level", exchange -> {
            String body = CoreHttpResponses.readBody(exchange);
            CoreHttpResponses.write(exchange, 200, rankingsJson(rankingRepository.topByLevel(queryInteger(exchange, "limit", JsonFields.integer(body, "limit", 10), 1, 100))));
        });
        registry.route("/v1/rankings/worth", exchange -> {
            String body = CoreHttpResponses.readBody(exchange);
            CoreHttpResponses.write(exchange, 200, rankingsJson(rankingRepository.topByWorth(queryInteger(exchange, "limit", JsonFields.integer(body, "limit", 10), 1, 100))));
        });
        registry.route("/v1/upgrades/rules", exchange -> CoreHttpResponses.write(exchange, 200, upgradeRulesJson(upgradePolicy.list())));
        registry.route("/v1/admin/block-values/list", exchange -> CoreHttpResponses.write(exchange, 200, blockValuesJson(levelRepository.blockValues())));
        registry.route("/v1/islands/missions", exchange -> {
            String body = CoreHttpResponses.readBody(exchange);
            CoreHttpResponses.write(exchange, 200, missionsJson(missionRepository.list(JsonFields.uuid(body, "islandId", new UUID(0L, 0L)), JsonFields.text(body, "kind", "MISSION"))));
        });
        registry.route("/v1/islands/missions/complete", exchange -> {
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
        registry.route("/v1/islands/missions/progress", exchange -> {
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
        registry.route("/v1/islands/limits", exchange -> {
            String body = CoreHttpResponses.readBody(exchange);
            CoreHttpResponses.write(exchange, 200, limitsJson(limitRepository.list(JsonFields.uuid(body, "islandId", new UUID(0L, 0L)))));
        });
        registry.route("/v1/islands/limits/set", exchange -> {
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
            .anyMatch(member -> member.playerUuid().equals(actorUuid) && permissionRules.allowed(islandId, actorUuid, member.role(), permission));
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

    private static String escape(String value) {
        return value == null ? "" : value.replace("\\", "\\\\").replace("\"", "\\\"");
    }

    private static String levelJson(kr.lunaf.cloudislands.coreservice.ranking.IslandRankSnapshot snapshot) {
        return "{\"islandId\":\"" + snapshot.islandId() + "\",\"level\":" + snapshot.level() + ",\"worth\":\"" + snapshot.worth().toPlainString() + "\",\"calculatedAt\":\"" + snapshot.updatedAt() + "\"}";
    }

    private static String rankingsJson(java.util.List<kr.lunaf.cloudislands.coreservice.ranking.IslandRankSnapshot> rankings) {
        StringBuilder builder = new StringBuilder("{\"rankings\":[");
        boolean first = true;
        for (kr.lunaf.cloudislands.coreservice.ranking.IslandRankSnapshot ranking : rankings) {
            if (!first) {
                builder.append(',');
            }
            first = false;
            builder.append(levelJson(ranking));
        }
        return builder.append("]}").toString();
    }

    private static String upgradeRulesJson(java.util.List<UpgradeRule> rules) {
        StringBuilder builder = new StringBuilder("{\"rules\":[");
        boolean first = true;
        for (UpgradeRule rule : rules) {
            if (!first) {
                builder.append(',');
            }
            first = false;
            builder.append('{')
                .append("\"upgradeKey\":\"").append(rule.upgradeKey()).append("\",")
                .append("\"type\":\"").append(rule.type()).append("\",")
                .append("\"maxLevel\":").append(rule.maxLevel()).append(',')
                .append("\"baseCost\":\"").append(rule.baseCost().toPlainString()).append("\",")
                .append("\"multiplier\":\"").append(rule.multiplier().toPlainString()).append("\"")
                .append('}');
        }
        return builder.append("]}").toString();
    }

    private static String blockValuesJson(Map<String, RankingRecalculationService.BlockValue> values) {
        StringBuilder builder = new StringBuilder("{\"values\":[");
        boolean first = true;
        for (Map.Entry<String, RankingRecalculationService.BlockValue> entry : values.entrySet()) {
            if (!first) {
                builder.append(',');
            }
            first = false;
            RankingRecalculationService.BlockValue value = entry.getValue();
            builder.append('{')
                .append("\"materialKey\":\"").append(escape(entry.getKey())).append("\",")
                .append("\"worth\":\"").append(value.worth().toPlainString()).append("\",")
                .append("\"levelPoints\":").append(value.levelPoints()).append(',')
                .append("\"limit\":").append(value.limit())
                .append('}');
        }
        return builder.append("]}").toString();
    }

    static String missionsJson(java.util.List<kr.lunaf.cloudislands.api.model.IslandMissionSnapshot> missions) {
        StringBuilder builder = new StringBuilder("{\"missions\":[");
        boolean first = true;
        for (kr.lunaf.cloudislands.api.model.IslandMissionSnapshot mission : missions) {
            if (!first) {
                builder.append(',');
            }
            first = false;
            builder.append(missionJson(mission));
        }
        return builder.append("]}").toString();
    }

    static String missionJson(kr.lunaf.cloudislands.api.model.IslandMissionSnapshot mission) {
        return "{\"islandId\":\"" + mission.islandId()
            + "\",\"missionKey\":\"" + escape(mission.missionKey())
            + "\",\"kind\":\"" + escape(mission.kind())
            + "\",\"title\":\"" + escape(mission.title())
            + "\",\"progress\":" + mission.progress()
            + ",\"goal\":" + mission.goal()
            + ",\"completed\":" + mission.completed()
            + ",\"reward\":\"" + escape(mission.reward())
            + "\",\"updatedAt\":\"" + mission.updatedAt()
            + "\"}";
    }

    static String limitsJson(java.util.List<kr.lunaf.cloudislands.api.model.IslandLimitSnapshot> limits) {
        StringBuilder builder = new StringBuilder("{\"limits\":[");
        boolean first = true;
        for (kr.lunaf.cloudislands.api.model.IslandLimitSnapshot limit : limits) {
            if (!first) {
                builder.append(',');
            }
            first = false;
            builder.append(limitJson(limit));
        }
        return builder.append("]}").toString();
    }

    static String limitJson(kr.lunaf.cloudislands.api.model.IslandLimitSnapshot limit) {
        return "{\"islandId\":\"" + limit.islandId()
            + "\",\"limitKey\":\"" + escape(limit.limitKey())
            + "\",\"value\":" + limit.value()
            + ",\"updatedBy\":\"" + limit.updatedBy()
            + "\",\"updatedAt\":\"" + limit.updatedAt()
            + "\"}";
    }
}
