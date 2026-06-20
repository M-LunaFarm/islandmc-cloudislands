package kr.lunaf.cloudislands.coreservice.http.routes;

import com.sun.net.httpserver.HttpExchange;
import java.io.IOException;
import java.net.URLDecoder;
import java.nio.charset.StandardCharsets;
import java.util.List;
import java.util.Map;
import java.util.UUID;
import kr.lunaf.cloudislands.api.model.IslandFlag;
import kr.lunaf.cloudislands.api.model.IslandHomeSnapshot;
import kr.lunaf.cloudislands.api.model.IslandLocation;
import kr.lunaf.cloudislands.api.model.IslandPermission;
import kr.lunaf.cloudislands.api.model.IslandWarpSnapshot;
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
import kr.lunaf.cloudislands.coreservice.permission.IslandPermissionRuleRepository;
import kr.lunaf.cloudislands.coreservice.repository.IslandMetadataRepository;
import kr.lunaf.cloudislands.coreservice.repository.IslandRepository;

public final class IslandWarpRoutes implements RouteGroup {
    private static final UUID EMPTY_UUID = new UUID(0L, 0L);

    private final IslandRepository islandRepository;
    private final IslandMetadataRepository metadataRepository;
    private final IslandLimitRepository limitRepository;
    private final IslandPermissionRuleRepository permissionRules;
    private final IslandLogRepository islandLogs;
    private final AuditLogger audit;
    private final GlobalEventPublisher events;

    public IslandWarpRoutes(
            IslandRepository islandRepository,
            IslandMetadataRepository metadataRepository,
            IslandLimitRepository limitRepository,
            IslandPermissionRuleRepository permissionRules,
            IslandLogRepository islandLogs,
            AuditLogger audit,
            GlobalEventPublisher events) {
        this.islandRepository = islandRepository;
        this.metadataRepository = metadataRepository;
        this.limitRepository = limitRepository;
        this.permissionRules = permissionRules;
        this.islandLogs = islandLogs;
        this.audit = audit;
        this.events = events;
    }

    @Override
    public void register(CoreRouteRegistry registry) {
        registry.route("/v1/islands/warps", this::warps);
        registry.route("/v1/islands/public-warps", this::publicWarps);
        registry.route("/v1/islands/homes", this::homes);
        registry.route("/v1/islands/homes/set", this::setHome);
        registry.route("/v1/islands/warps/set", this::setWarp);
        registry.route("/v1/islands/warps/delete", this::deleteWarp);
        registry.route("/v1/islands/warps/access", this::setWarpAccess);
    }

    private void warps(HttpExchange exchange) throws IOException {
        String body = CoreHttpResponses.readBody(exchange);
        CoreHttpResponses.write(exchange, 200, warpsJson(metadataRepository.warps(JsonFields.uuid(body, "islandId", EMPTY_UUID))));
    }

    private void publicWarps(HttpExchange exchange) throws IOException {
        String body = CoreHttpResponses.readBody(exchange);
        int limit = queryInteger(exchange, "limit", JsonFields.integer(body, "limit", 27), 1, 54);
        String category = queryText(exchange, "category", JsonFields.text(body, "category", ""));
        String query = queryText(exchange, "query", JsonFields.text(body, "query", ""));
        List<IslandWarpSnapshot> visibleWarps = metadataRepository.publicWarps(500, category, query).stream()
            .filter(warp -> metadataRepository.isPublicAccess(warp.islandId()))
            .filter(warp -> !metadataRepository.isLocked(warp.islandId()))
            .filter(warp -> islandFlagEnabled(warp.islandId(), IslandFlag.PUBLIC_WARPS))
            .limit(limit)
            .toList();
        CoreHttpResponses.write(exchange, 200, warpsJson(visibleWarps));
    }

    private void homes(HttpExchange exchange) throws IOException {
        String body = CoreHttpResponses.readBody(exchange);
        CoreHttpResponses.write(exchange, 200, homesJson(metadataRepository.homes(JsonFields.uuid(body, "islandId", EMPTY_UUID))));
    }

    private void setHome(HttpExchange exchange) throws IOException {
        String body = CoreHttpResponses.readBody(exchange);
        UUID islandId = JsonFields.uuid(body, "islandId", EMPTY_UUID);
        String name = JsonFields.text(body, "name", "default").toLowerCase();
        UUID actorUuid = JsonFields.uuid(body, "actorUuid", EMPTY_UUID);
        if (!requireIslandPermission(exchange, islandId, actorUuid, IslandPermission.SET_HOME)) {
            return;
        }
        metadataRepository.upsertHome(islandId, name, location(body), actorUuid);
        audit.log(actorUuid, "PLAYER", "ISLAND_HOME_SET", "ISLAND", islandId.toString(), Map.of("name", name));
        islandLogs.append(islandId, actorUuid, "ISLAND_HOME_SET", Map.of("name", name));
        events.publish(CloudIslandEventType.ISLAND_HOME_CHANGED.name(), Map.of("islandId", islandId.toString(), "name", name));
        CoreHttpResponses.write(exchange, 202, ApiResponses.ok(true));
    }

    private void setWarp(HttpExchange exchange) throws IOException {
        String body = CoreHttpResponses.readBody(exchange);
        UUID islandId = JsonFields.uuid(body, "islandId", EMPTY_UUID);
        String name = JsonFields.text(body, "name", "default").toLowerCase();
        String category = JsonFields.text(body, "category", "default");
        boolean publicAccess = JsonFields.bool(body, "publicAccess", false);
        UUID actorUuid = JsonFields.uuid(body, "actorUuid", EMPTY_UUID);
        if (!requireIslandPermission(exchange, islandId, actorUuid, IslandPermission.MANAGE_WARPS)) {
            return;
        }
        boolean existingWarp = metadataRepository.warps(islandId).stream().anyMatch(warp -> warp.name().equalsIgnoreCase(name));
        if (!existingWarp && metadataRepository.warps(islandId).size() >= limitValue(islandId, "WARPS", 1L)) {
            CoreHttpResponses.write(exchange, 409, ApiResponses.error("WARP_LIMIT", "Island warp limit was reached"));
            return;
        }
        IslandLocation warpLocation = location(body);
        metadataRepository.upsertWarp(islandId, name, warpLocation, publicAccess, actorUuid, category);
        audit.log(actorUuid, "PLAYER", "ISLAND_WARP_SET", "ISLAND", islandId.toString(), Map.of("name", name, "publicAccess", Boolean.toString(publicAccess), "category", IslandWarpSnapshot.normalizeCategory(category)));
        islandLogs.append(islandId, actorUuid, "ISLAND_WARP_SET", Map.of("name", name, "publicAccess", Boolean.toString(publicAccess), "category", IslandWarpSnapshot.normalizeCategory(category)));
        if (!existingWarp) {
            events.publish(CloudIslandEventType.ISLAND_WARP_CREATED.name(), Map.of(
                "islandId", islandId.toString(),
                "name", name,
                "worldName", warpLocation.worldName(),
                "localX", Double.toString(warpLocation.localX()),
                "localY", Double.toString(warpLocation.localY()),
                "localZ", Double.toString(warpLocation.localZ()),
                "yaw", Float.toString(warpLocation.yaw()),
                "pitch", Float.toString(warpLocation.pitch()),
                "category", IslandWarpSnapshot.normalizeCategory(category)
            ));
        }
        events.publish(CloudIslandEventType.ISLAND_WARP_CHANGED.name(), Map.of("islandId", islandId.toString(), "name", name, "operation", existingWarp ? "WARP_UPDATE" : "WARP_CREATE", "category", IslandWarpSnapshot.normalizeCategory(category)));
        CoreHttpResponses.write(exchange, 202, ApiResponses.ok(true));
    }

    private void deleteWarp(HttpExchange exchange) throws IOException {
        String body = CoreHttpResponses.readBody(exchange);
        UUID islandId = JsonFields.uuid(body, "islandId", EMPTY_UUID);
        String name = JsonFields.text(body, "name", "default").toLowerCase();
        UUID actorUuid = JsonFields.uuid(body, "actorUuid", EMPTY_UUID);
        if (!requireIslandPermission(exchange, islandId, actorUuid, IslandPermission.MANAGE_WARPS)) {
            return;
        }
        metadataRepository.deleteWarp(islandId, name);
        audit.log(actorUuid, "PLAYER", "ISLAND_WARP_DELETE", "ISLAND", islandId.toString(), Map.of("name", name));
        islandLogs.append(islandId, actorUuid, "ISLAND_WARP_DELETE", Map.of("name", name));
        events.publish(CloudIslandEventType.ISLAND_WARP_DELETED.name(), Map.of("islandId", islandId.toString(), "name", name));
        events.publish(CloudIslandEventType.ISLAND_WARP_CHANGED.name(), Map.of("islandId", islandId.toString(), "name", name, "operation", "WARP_DELETE"));
        CoreHttpResponses.write(exchange, 202, ApiResponses.ok(true));
    }

    private void setWarpAccess(HttpExchange exchange) throws IOException {
        String body = CoreHttpResponses.readBody(exchange);
        UUID islandId = JsonFields.uuid(body, "islandId", EMPTY_UUID);
        String name = JsonFields.text(body, "name", "default").toLowerCase();
        boolean publicAccess = JsonFields.bool(body, "publicAccess", false);
        UUID actorUuid = JsonFields.uuid(body, "actorUuid", EMPTY_UUID);
        if (!requireIslandPermission(exchange, islandId, actorUuid, IslandPermission.MANAGE_WARPS)) {
            return;
        }
        if (metadataRepository.warp(islandId, name).isEmpty()) {
            CoreHttpResponses.write(exchange, 404, ApiResponses.error("WARP_NOT_FOUND", "Island warp was not found"));
            return;
        }
        metadataRepository.setWarpPublicAccess(islandId, name, publicAccess);
        audit.log(actorUuid, "PLAYER", "ISLAND_WARP_ACCESS_SET", "ISLAND", islandId.toString(), Map.of("name", name, "publicAccess", Boolean.toString(publicAccess)));
        islandLogs.append(islandId, actorUuid, "ISLAND_WARP_ACCESS_SET", Map.of("name", name, "publicAccess", Boolean.toString(publicAccess)));
        events.publish(CloudIslandEventType.ISLAND_WARP_CHANGED.name(), Map.of("islandId", islandId.toString(), "name", name, "operation", "WARP_ACCESS_SET", "publicAccess", Boolean.toString(publicAccess)));
        CoreHttpResponses.write(exchange, 202, ApiResponses.ok(true));
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

    private long limitValue(UUID islandId, String limitKey, long fallback) {
        return limitRepository.list(islandId).stream()
            .filter(limit -> limit.limitKey().equalsIgnoreCase(limitKey))
            .findFirst()
            .map(kr.lunaf.cloudislands.api.model.IslandLimitSnapshot::value)
            .orElse(fallback);
    }

    private boolean islandFlagEnabled(UUID islandId, IslandFlag flag) {
        String value = metadataRepository.flags(islandId).values().getOrDefault(flag, "false");
        return value.equalsIgnoreCase("true")
            || value.equalsIgnoreCase("allow")
            || value.equalsIgnoreCase("allowed")
            || value.equalsIgnoreCase("enabled")
            || value.equalsIgnoreCase("on");
    }

    static IslandLocation location(String body) {
        return new IslandLocation(
            JsonFields.text(body, "worldName", ""),
            JsonFields.decimal(body, "localX", 0.5D),
            JsonFields.decimal(body, "localY", 100.0D),
            JsonFields.decimal(body, "localZ", 0.5D),
            (float) JsonFields.decimal(body, "yaw", 0.0D),
            (float) JsonFields.decimal(body, "pitch", 0.0D)
        );
    }

    static String homesJson(List<IslandHomeSnapshot> homes) {
        StringBuilder builder = new StringBuilder("{\"homes\":[");
        boolean first = true;
        for (IslandHomeSnapshot home : homes) {
            if (!first) {
                builder.append(',');
            }
            first = false;
            IslandLocation location = home.location();
            builder.append('{')
                .append("\"islandId\":\"").append(home.islandId()).append("\",")
                .append("\"name\":\"").append(escape(home.name())).append("\",")
                .append("\"worldName\":\"").append(escape(location.worldName())).append("\",")
                .append("\"localX\":").append(location.localX()).append(',')
                .append("\"localY\":").append(location.localY()).append(',')
                .append("\"localZ\":").append(location.localZ()).append(',')
                .append("\"yaw\":").append(location.yaw()).append(',')
                .append("\"pitch\":").append(location.pitch()).append(',')
                .append("\"createdBy\":\"").append(home.createdBy()).append("\",")
                .append("\"createdAt\":\"").append(home.createdAt()).append("\"")
                .append('}');
        }
        return builder.append("]}").toString();
    }

    static String warpsJson(List<IslandWarpSnapshot> warps) {
        StringBuilder builder = new StringBuilder("{\"warps\":[");
        boolean first = true;
        for (IslandWarpSnapshot warp : warps) {
            if (!first) {
                builder.append(',');
            }
            first = false;
            IslandLocation location = warp.location();
            builder.append('{')
                .append("\"islandId\":\"").append(warp.islandId()).append("\",")
                .append("\"name\":\"").append(escape(warp.name())).append("\",")
                .append("\"localX\":").append(location.localX()).append(',')
                .append("\"localY\":").append(location.localY()).append(',')
                .append("\"localZ\":").append(location.localZ()).append(',')
                .append("\"yaw\":").append(location.yaw()).append(',')
                .append("\"pitch\":").append(location.pitch()).append(',')
                .append("\"publicAccess\":").append(warp.publicAccess()).append(',')
                .append("\"category\":\"").append(escape(warp.category())).append("\",")
                .append("\"createdBy\":\"").append(warp.createdBy()).append("\",")
                .append("\"createdAt\":\"").append(warp.createdAt()).append("\"")
                .append('}');
        }
        return builder.append("]}").toString();
    }

    static int queryInteger(HttpExchange exchange, String key, int fallback, int min, int max) {
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

    static String queryText(HttpExchange exchange, String key, String fallback) {
        String query = exchange.getRequestURI().getRawQuery();
        if (query == null || query.isBlank()) {
            return fallback == null ? "" : fallback;
        }
        for (String part : query.split("&")) {
            int separator = part.indexOf('=');
            if (separator <= 0 || !part.substring(0, separator).equals(key)) {
                continue;
            }
            return URLDecoder.decode(part.substring(separator + 1), StandardCharsets.UTF_8);
        }
        return fallback == null ? "" : fallback;
    }

    private static String escape(String value) {
        return value == null ? "" : value.replace("\\", "\\\\").replace("\"", "\\\"");
    }
}
