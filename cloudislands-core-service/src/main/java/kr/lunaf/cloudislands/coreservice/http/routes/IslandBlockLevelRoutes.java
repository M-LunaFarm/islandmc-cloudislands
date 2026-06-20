package kr.lunaf.cloudislands.coreservice.http.routes;

import com.sun.net.httpserver.HttpExchange;
import java.io.IOException;
import java.math.BigDecimal;
import java.util.LinkedHashMap;
import java.util.Map;
import java.util.UUID;
import kr.lunaf.cloudislands.api.model.IslandPermission;
import kr.lunaf.cloudislands.common.event.CloudIslandEventType;
import kr.lunaf.cloudislands.coreservice.audit.AuditLogger;
import kr.lunaf.cloudislands.coreservice.event.GlobalEventPublisher;
import kr.lunaf.cloudislands.coreservice.http.ApiResponses;
import kr.lunaf.cloudislands.coreservice.http.CoreHttpResponses;
import kr.lunaf.cloudislands.coreservice.http.CoreRouteRegistry;
import kr.lunaf.cloudislands.coreservice.http.JsonFields;
import kr.lunaf.cloudislands.coreservice.http.RouteGroup;
import kr.lunaf.cloudislands.coreservice.permission.IslandPermissionRuleRepository;
import kr.lunaf.cloudislands.coreservice.ranking.IslandLevelRepository;
import kr.lunaf.cloudislands.coreservice.ranking.IslandRankSnapshot;
import kr.lunaf.cloudislands.coreservice.ranking.RankingRecalculationService;
import kr.lunaf.cloudislands.coreservice.ranking.RankingRepository;
import kr.lunaf.cloudislands.coreservice.repository.IslandMetadataRepository;
import kr.lunaf.cloudislands.coreservice.repository.IslandRepository;

public final class IslandBlockLevelRoutes implements RouteGroup {
    private static final UUID EMPTY_UUID = new UUID(0L, 0L);

    private final IslandLevelRepository levelRepository;
    private final RankingRepository rankingRepository;
    private final RankingRecalculationService levelRecalculation;
    private final IslandRepository islandRepository;
    private final IslandMetadataRepository metadataRepository;
    private final IslandPermissionRuleRepository permissionRules;
    private final AuditLogger audit;
    private final GlobalEventPublisher events;

    public IslandBlockLevelRoutes(
            IslandLevelRepository levelRepository,
            RankingRepository rankingRepository,
            RankingRecalculationService levelRecalculation,
            IslandRepository islandRepository,
            IslandMetadataRepository metadataRepository,
            IslandPermissionRuleRepository permissionRules,
            AuditLogger audit,
            GlobalEventPublisher events) {
        this.levelRepository = levelRepository;
        this.rankingRepository = rankingRepository;
        this.levelRecalculation = levelRecalculation;
        this.islandRepository = islandRepository;
        this.metadataRepository = metadataRepository;
        this.permissionRules = permissionRules;
        this.audit = audit;
        this.events = events;
    }

    @Override
    public void register(CoreRouteRegistry registry) {
        registry.route("/v1/admin/block-values", this::setBlockValue);
        registry.route("/v1/admin/block-values/list", this::blockValues);
        registry.route("/v1/islands/blocks", this::blocks);
        registry.route("/v1/islands/blocks/delta", this::blockDelta);
        registry.route("/v1/islands/blocks/replace", this::replaceBlocks);
        registry.route("/v1/islands/level/recalculate", this::recalculateLevel);
    }

    private void setBlockValue(HttpExchange exchange) throws IOException {
        String body = CoreHttpResponses.readBody(exchange);
        String materialKey = JsonFields.text(body, "materialKey", "minecraft:stone");
        BigDecimal worth = new BigDecimal(JsonFields.text(body, "worth", Double.toString(JsonFields.decimal(body, "worth", 0.0D))));
        long levelPoints = JsonFields.longValue(body, "levelPoints", 0L);
        long limit = JsonFields.longValue(body, "limit", 0L);
        levelRepository.putBlockValue(materialKey, new RankingRecalculationService.BlockValue(worth, levelPoints, limit));
        audit.log(JsonFields.uuid(body, "actorUuid", EMPTY_UUID), "ADMIN", "BLOCK_VALUE_SET", "MATERIAL", materialKey, Map.of("worth", worth.toPlainString(), "levelPoints", Long.toString(levelPoints)));
        events.publish(CloudIslandEventType.ISLAND_BLOCK_VALUE_CHANGED.name(), Map.of("materialKey", materialKey, "worth", worth.toPlainString(), "levelPoints", Long.toString(levelPoints), "limit", Long.toString(limit), "cacheTargets", "BLOCKS,LEVEL,SUMMARY"));
        CoreHttpResponses.write(exchange, 202, ApiResponses.ok(true));
    }

    private void blockValues(HttpExchange exchange) throws IOException {
        CoreHttpResponses.readBody(exchange);
        CoreHttpResponses.write(exchange, 200, blockValuesJson(levelRepository.blockValues()));
    }

    private void blocks(HttpExchange exchange) throws IOException {
        String body = CoreHttpResponses.readBody(exchange);
        UUID islandId = JsonFields.uuid(body, "islandId", EMPTY_UUID);
        int limit = Math.max(1, Math.min(JsonFields.integer(body, "limit", 50), 200));
        CoreHttpResponses.write(exchange, 200, blockDetailsJson(islandId, levelRepository.blockCounts(islandId), levelRepository.blockValues(), limit));
    }

    private void blockDelta(HttpExchange exchange) throws IOException {
        String body = CoreHttpResponses.readBody(exchange);
        UUID islandId = JsonFields.uuid(body, "islandId", EMPTY_UUID);
        String materialKey = JsonFields.text(body, "materialKey", "minecraft:air");
        long delta = JsonFields.longValue(body, "delta", 0L);
        levelRepository.addBlockDelta(islandId, materialKey, delta);
        rankingRepository.markDirty(islandId);
        events.publish(CloudIslandEventType.ISLAND_BLOCKS_CHANGED.name(), Map.of("islandId", islandId.toString(), "materialKey", materialKey, "delta", Long.toString(delta)));
        CoreHttpResponses.write(exchange, 202, ApiResponses.ok(true));
    }

    private void replaceBlocks(HttpExchange exchange) throws IOException {
        String body = CoreHttpResponses.readBody(exchange);
        UUID islandId = JsonFields.uuid(body, "islandId", EMPTY_UUID);
        Map<String, Long> counts = parseCountsPayload(JsonFields.text(body, "counts", ""));
        levelRepository.replaceBlockCounts(islandId, counts);
        rankingRepository.markDirty(islandId);
        events.publish(CloudIslandEventType.ISLAND_BLOCKS_CHANGED.name(), Map.of("islandId", islandId.toString(), "materialKey", "*", "delta", "rescan"));
        CoreHttpResponses.write(exchange, 202, ApiResponses.ok(true));
    }

    private void recalculateLevel(HttpExchange exchange) throws IOException {
        String body = CoreHttpResponses.readBody(exchange);
        UUID islandId = JsonFields.uuid(body, "islandId", EMPTY_UUID);
        UUID actorUuid = JsonFields.uuid(body, "actorUuid", EMPTY_UUID);
        if (!requireIslandPermission(exchange, islandId, actorUuid, IslandPermission.START_LEVEL_CALC)) {
            return;
        }
        var snapshot = levelRecalculation.recalculate(islandId, levelRepository.blockCounts(islandId), levelRepository.blockValues(), metadataRepository.members(islandId).size());
        CoreHttpResponses.write(exchange, 202, levelJson(snapshot));
    }

    private boolean requireIslandPermission(HttpExchange exchange, UUID islandId, UUID actorUuid, IslandPermission permission) throws IOException {
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

    static Map<String, Long> parseCountsPayload(String payload) {
        Map<String, Long> counts = new LinkedHashMap<>();
        if (payload == null || payload.isBlank()) {
            return counts;
        }
        for (String entry : payload.split(",")) {
            String[] parts = entry.split("=");
            if (parts.length != 2) {
                continue;
            }
            try {
                counts.put(parts[0].trim(), Long.parseLong(parts[1].trim()));
            } catch (NumberFormatException ignored) {
                counts.put(parts[0].trim(), 0L);
            }
        }
        return counts;
    }

    static String levelJson(IslandRankSnapshot snapshot) {
        return "{\"islandId\":\"" + snapshot.islandId() + "\",\"level\":" + snapshot.level() + ",\"worth\":\"" + snapshot.worth().toPlainString() + "\",\"calculatedAt\":\"" + snapshot.updatedAt() + "\"}";
    }

    static String blockDetailsJson(UUID islandId, Map<String, Long> counts, Map<String, RankingRecalculationService.BlockValue> values, int limit) {
        StringBuilder builder = new StringBuilder("{\"islandId\":\"").append(islandId).append("\",\"blocks\":[");
        final BigDecimal[] totalWorth = {BigDecimal.ZERO};
        final long[] totalLevelPoints = {0L};
        boolean[] first = {true};
        counts.entrySet().stream()
            .filter(entry -> entry.getValue() != null && entry.getValue() > 0L)
            .sorted((left, right) -> Long.compare(right.getValue(), left.getValue()))
            .limit(Math.max(1, Math.min(limit, 200)))
            .forEach(entry -> {
                RankingRecalculationService.BlockValue value = values.getOrDefault(entry.getKey(), new RankingRecalculationService.BlockValue(BigDecimal.ZERO, 0L, 0L));
                BigDecimal rowWorth = value.worth().multiply(BigDecimal.valueOf(entry.getValue()));
                long rowLevelPoints = Math.multiplyExact(value.levelPoints(), entry.getValue());
                totalWorth[0] = totalWorth[0].add(rowWorth);
                totalLevelPoints[0] += rowLevelPoints;
                if (!first[0]) {
                    builder.append(',');
                }
                first[0] = false;
                builder.append("{\"materialKey\":\"").append(escape(entry.getKey())).append("\",")
                    .append("\"count\":").append(entry.getValue()).append(',')
                    .append("\"unitWorth\":\"").append(value.worth().toPlainString()).append("\",")
                    .append("\"totalWorth\":\"").append(rowWorth.toPlainString()).append("\",")
                    .append("\"levelPoints\":").append(rowLevelPoints).append(',')
                    .append("\"limit\":").append(value.limit())
                    .append('}');
            });
        return builder.append("],\"summary\":{\"totalWorth\":\"")
            .append(totalWorth[0].toPlainString())
            .append("\",\"totalLevelPoints\":")
            .append(totalLevelPoints[0])
            .append("}}")
            .toString();
    }

    static String blockValuesJson(Map<String, RankingRecalculationService.BlockValue> values) {
        StringBuilder builder = new StringBuilder("{\"values\":[");
        boolean first = true;
        for (var entry : values.entrySet().stream().sorted(Map.Entry.comparingByKey()).toList()) {
            if (!first) {
                builder.append(',');
            }
            first = false;
            builder.append("{\"materialKey\":\"").append(escape(entry.getKey())).append("\",")
                .append("\"worth\":\"").append(entry.getValue().worth().toPlainString()).append("\",")
                .append("\"levelPoints\":").append(entry.getValue().levelPoints()).append(',')
                .append("\"limit\":").append(entry.getValue().limit())
                .append('}');
        }
        return builder.append("]}").toString();
    }

    private static String escape(String value) {
        return value == null ? "" : value.replace("\\", "\\\\").replace("\"", "\\\"");
    }
}
