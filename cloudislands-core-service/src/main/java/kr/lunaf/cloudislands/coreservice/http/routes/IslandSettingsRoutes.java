package kr.lunaf.cloudislands.coreservice.http.routes;

import com.sun.net.httpserver.HttpExchange;
import java.io.IOException;
import java.util.Map;
import java.util.Optional;
import java.util.UUID;
import kr.lunaf.cloudislands.api.model.IslandBiomeSnapshot;
import kr.lunaf.cloudislands.api.model.IslandFlag;
import kr.lunaf.cloudislands.api.model.IslandFlagsSnapshot;
import kr.lunaf.cloudislands.api.model.IslandPermission;
import kr.lunaf.cloudislands.api.model.IslandSnapshot;
import kr.lunaf.cloudislands.common.event.CloudIslandEventType;
import kr.lunaf.cloudislands.common.permission.CachedPermissionSet;
import kr.lunaf.cloudislands.common.permission.defaults.DefaultIslandPermissions;
import kr.lunaf.cloudislands.coreservice.audit.AuditLogger;
import kr.lunaf.cloudislands.coreservice.event.GlobalEventPublisher;
import kr.lunaf.cloudislands.coreservice.http.ApiResponses;
import kr.lunaf.cloudislands.coreservice.http.CoreHttpResponses;
import kr.lunaf.cloudislands.coreservice.http.CoreRouteRegistry;
import kr.lunaf.cloudislands.coreservice.http.JsonFields;
import kr.lunaf.cloudislands.coreservice.http.RouteGroup;
import kr.lunaf.cloudislands.coreservice.islandlog.IslandLogRepository;
import kr.lunaf.cloudislands.coreservice.permission.IslandPermissionRuleRepository;
import kr.lunaf.cloudislands.coreservice.repository.IslandMetadataRepository;
import kr.lunaf.cloudislands.coreservice.repository.IslandRepository;

public final class IslandSettingsRoutes implements RouteGroup {
    private static final UUID EMPTY_UUID = new UUID(0L, 0L);

    private final IslandRepository islandRepository;
    private final IslandMetadataRepository metadataRepository;
    private final IslandPermissionRuleRepository permissionRules;
    private final IslandLogRepository islandLogs;
    private final AuditLogger audit;
    private final GlobalEventPublisher events;

    public IslandSettingsRoutes(
            IslandRepository islandRepository,
            IslandMetadataRepository metadataRepository,
            IslandPermissionRuleRepository permissionRules,
            IslandLogRepository islandLogs,
            AuditLogger audit,
            GlobalEventPublisher events) {
        this.islandRepository = islandRepository;
        this.metadataRepository = metadataRepository;
        this.permissionRules = permissionRules;
        this.islandLogs = islandLogs;
        this.audit = audit;
        this.events = events;
    }

    @Override
    public void register(CoreRouteRegistry registry) {
        registry.route("/v1/islands/lock", this::setLock);
        registry.route("/v1/islands/name", this::rename);
        registry.route("/v1/islands/flags", this::flags);
        registry.route("/v1/islands/biome", this::biome);
        registry.route("/v1/islands/biome/set", this::setBiome);
        registry.route("/v1/islands/flags/set", this::setFlag);
        registry.route("/v1/islands/access", this::setAccess);
    }

    private void setLock(HttpExchange exchange) throws IOException {
        String body = CoreHttpResponses.readBody(exchange);
        UUID islandId = JsonFields.uuid(body, "islandId", EMPTY_UUID);
        boolean locked = JsonFields.bool(body, "locked", false);
        UUID actorUuid = JsonFields.uuid(body, "actorUuid", EMPTY_UUID);
        if (!requireIslandPermission(exchange, islandId, actorUuid, IslandPermission.MANAGE_FLAGS)) {
            return;
        }
        metadataRepository.setLocked(islandId, locked);
        audit.log(actorUuid, "PLAYER", "ISLAND_LOCK_SET", "ISLAND", islandId.toString(), Map.of("locked", Boolean.toString(locked)));
        islandLogs.append(islandId, actorUuid, "ISLAND_LOCK_SET", Map.of("locked", Boolean.toString(locked)));
        events.publish(CloudIslandEventType.ISLAND_ACCESS_CHANGED.name(), Map.of("islandId", islandId.toString(), "locked", Boolean.toString(locked)));
        CoreHttpResponses.write(exchange, 202, ApiResponses.ok(true));
    }

    private void rename(HttpExchange exchange) throws IOException {
        String body = CoreHttpResponses.readBody(exchange);
        UUID islandId = JsonFields.uuid(body, "islandId", EMPTY_UUID);
        UUID actorUuid = JsonFields.uuid(body, "actorUuid", EMPTY_UUID);
        String name = JsonFields.text(body, "name", "").trim();
        if (!requireIslandPermission(exchange, islandId, actorUuid, IslandPermission.MANAGE_FLAGS)) {
            return;
        }
        if (name.length() < 2 || name.length() > 32 || name.chars().anyMatch(Character::isISOControl)) {
            CoreHttpResponses.write(exchange, 400, ApiResponses.error("INVALID_ISLAND_NAME", "Island name must be 2-32 visible characters"));
            return;
        }
        Optional<IslandSnapshot> duplicate = islandRepository.findByName(name);
        if (duplicate.isPresent() && !duplicate.get().islandId().equals(islandId)) {
            CoreHttpResponses.write(exchange, 409, ApiResponses.error("ISLAND_NAME_TAKEN", "Island name is already used"));
            return;
        }
        boolean renamed = islandRepository.rename(islandId, name);
        if (!renamed) {
            CoreHttpResponses.write(exchange, 409, ApiResponses.error("ISLAND_RENAME_DENIED", "Island was not renamed"));
            return;
        }
        audit.log(actorUuid, "PLAYER", "ISLAND_RENAME", "ISLAND", islandId.toString(), Map.of("name", name));
        islandLogs.append(islandId, actorUuid, "ISLAND_RENAME", Map.of("name", name));
        events.publish(CloudIslandEventType.ISLAND_RENAMED.name(), Map.of("islandId", islandId.toString(), "actorUuid", actorUuid.toString(), "name", name));
        CoreHttpResponses.write(exchange, 202, renameJson(islandId, name));
    }

    private void flags(HttpExchange exchange) throws IOException {
        String body = CoreHttpResponses.readBody(exchange);
        CoreHttpResponses.write(exchange, 200, flagsJson(metadataRepository.flags(JsonFields.uuid(body, "islandId", EMPTY_UUID))));
    }

    private void biome(HttpExchange exchange) throws IOException {
        String body = CoreHttpResponses.readBody(exchange);
        CoreHttpResponses.write(exchange, 200, biomeJson(metadataRepository.biome(JsonFields.uuid(body, "islandId", EMPTY_UUID))));
    }

    private void setBiome(HttpExchange exchange) throws IOException {
        String body = CoreHttpResponses.readBody(exchange);
        UUID islandId = JsonFields.uuid(body, "islandId", EMPTY_UUID);
        UUID actorUuid = JsonFields.uuid(body, "actorUuid", EMPTY_UUID);
        String biomeKey = JsonFields.text(body, "biomeKey", "minecraft:plains").toLowerCase();
        if (!requireIslandPermission(exchange, islandId, actorUuid, IslandPermission.SET_BIOME)) {
            return;
        }
        metadataRepository.setBiome(islandId, biomeKey, actorUuid);
        audit.log(actorUuid, "PLAYER", "ISLAND_BIOME_SET", "ISLAND", islandId.toString(), Map.of("biomeKey", biomeKey));
        islandLogs.append(islandId, actorUuid, "ISLAND_BIOME_SET", Map.of("biomeKey", biomeKey));
        events.publish(CloudIslandEventType.ISLAND_BIOME_CHANGED.name(), Map.of("islandId", islandId.toString(), "biomeKey", biomeKey));
        CoreHttpResponses.write(exchange, 202, ApiResponses.ok(true));
    }

    private void setFlag(HttpExchange exchange) throws IOException {
        String body = CoreHttpResponses.readBody(exchange);
        UUID islandId = JsonFields.uuid(body, "islandId", EMPTY_UUID);
        IslandFlag flag = JsonFields.enumValue(IslandFlag.class, body, "flag", IslandFlag.VISITOR_INTERACT);
        String value = JsonFields.text(body, "value", "false");
        UUID actorUuid = JsonFields.uuid(body, "actorUuid", EMPTY_UUID);
        if (!requireIslandPermission(exchange, islandId, actorUuid, IslandPermission.MANAGE_FLAGS)) {
            return;
        }
        metadataRepository.setFlag(islandId, flag, value);
        audit.log(actorUuid, "PLAYER", "ISLAND_FLAG_SET", "ISLAND", islandId.toString(), Map.of("flag", flag.name(), "value", value));
        islandLogs.append(islandId, actorUuid, "ISLAND_FLAG_SET", Map.of("flag", flag.name(), "value", value));
        events.publish(CloudIslandEventType.ISLAND_FLAG_CHANGED.name(), Map.of("islandId", islandId.toString(), "flag", flag.name(), "value", value));
        CoreHttpResponses.write(exchange, 202, ApiResponses.ok(true));
    }

    private void setAccess(HttpExchange exchange) throws IOException {
        String body = CoreHttpResponses.readBody(exchange);
        UUID islandId = JsonFields.uuid(body, "islandId", EMPTY_UUID);
        boolean publicAccess = JsonFields.bool(body, "publicAccess", false);
        UUID actorUuid = JsonFields.uuid(body, "actorUuid", EMPTY_UUID);
        if (!requireIslandPermission(exchange, islandId, actorUuid, IslandPermission.MANAGE_FLAGS)) {
            return;
        }
        metadataRepository.setPublicAccess(islandId, publicAccess);
        audit.log(actorUuid, "PLAYER", "ISLAND_ACCESS_SET", "ISLAND", islandId.toString(), Map.of("publicAccess", Boolean.toString(publicAccess)));
        islandLogs.append(islandId, actorUuid, "ISLAND_ACCESS_SET", Map.of("publicAccess", Boolean.toString(publicAccess)));
        events.publish(CloudIslandEventType.ISLAND_ACCESS_CHANGED.name(), Map.of("islandId", islandId.toString(), "publicAccess", Boolean.toString(publicAccess)));
        CoreHttpResponses.write(exchange, 202, ApiResponses.ok(true));
    }

    private boolean requireIslandPermission(HttpExchange exchange, UUID islandId, UUID actorUuid, IslandPermission permission) throws IOException {
        boolean owner = islandRepository.findById(islandId)
            .map(island -> island.ownerUuid().equals(actorUuid))
            .orElse(false);
        CachedPermissionSet permissions = DefaultIslandPermissions.create();
        for (var rule : permissionRules.list(islandId)) {
            permissions.put(rule.role(), rule.permission(), rule.allowed());
        }
        boolean allowed = metadataRepository.members(islandId).stream()
            .anyMatch(member -> member.playerUuid().equals(actorUuid) && permissions.allowed(member.role(), permission));
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

    static String renameJson(UUID islandId, String name) {
        return "{\"accepted\":true,\"islandId\":\"" + islandId + "\",\"name\":\"" + escape(name) + "\"}";
    }

    static String flagsJson(IslandFlagsSnapshot flags) {
        StringBuilder builder = new StringBuilder("{\"islandId\":\"").append(flags.islandId()).append("\",\"flags\":{");
        boolean first = true;
        for (Map.Entry<IslandFlag, String> entry : flags.values().entrySet()) {
            if (!first) {
                builder.append(',');
            }
            first = false;
            builder.append("\"").append(entry.getKey().name()).append("\":\"").append(escape(entry.getValue())).append("\"");
        }
        return builder.append("}}").toString();
    }

    static String biomeJson(IslandBiomeSnapshot biome) {
        return "{\"islandId\":\"" + biome.islandId()
            + "\",\"biomeKey\":\"" + escape(biome.biomeKey())
            + "\",\"updatedBy\":\"" + biome.updatedBy()
            + "\",\"updatedAt\":\"" + biome.updatedAt()
            + "\"}";
    }

    private static String escape(String value) {
        return value == null ? "" : value.replace("\\", "\\\\").replace("\"", "\\\"");
    }
}
