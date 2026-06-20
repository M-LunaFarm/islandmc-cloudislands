package kr.lunaf.cloudislands.coreservice.http.routes;

import com.sun.net.httpserver.HttpExchange;
import java.io.IOException;
import java.util.Map;
import java.util.UUID;
import kr.lunaf.cloudislands.api.model.IslandPermission;
import kr.lunaf.cloudislands.api.model.IslandPermissionOverrideSnapshot;
import kr.lunaf.cloudislands.api.model.IslandPermissionRuleSnapshot;
import kr.lunaf.cloudislands.api.model.IslandRole;
import kr.lunaf.cloudislands.api.model.IslandRoleSnapshot;
import kr.lunaf.cloudislands.common.event.CloudIslandEventType;
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
import kr.lunaf.cloudislands.coreservice.role.IslandRoleRepository;

public final class PermissionRoleRoutes implements RouteGroup {
    private static final UUID EMPTY_UUID = new UUID(0L, 0L);

    private final IslandRepository islandRepository;
    private final IslandMetadataRepository metadataRepository;
    private final IslandPermissionRuleRepository permissionRules;
    private final IslandRoleRepository roleRepository;
    private final IslandLogRepository islandLogs;
    private final AuditLogger audit;
    private final GlobalEventPublisher events;

    public PermissionRoleRoutes(
            IslandRepository islandRepository,
            IslandMetadataRepository metadataRepository,
            IslandPermissionRuleRepository permissionRules,
            IslandRoleRepository roleRepository,
            IslandLogRepository islandLogs,
            AuditLogger audit,
            GlobalEventPublisher events) {
        this.islandRepository = islandRepository;
        this.metadataRepository = metadataRepository;
        this.permissionRules = permissionRules;
        this.roleRepository = roleRepository;
        this.islandLogs = islandLogs;
        this.audit = audit;
        this.events = events;
    }

    @Override
    public void register(CoreRouteRegistry registry) {
        registry.route("/v1/islands/permissions", this::permissions);
        registry.route("/v1/islands/permissions/set", this::setPermission);
        registry.route("/v1/islands/permissions/overrides/set", this::setPermissionOverride);
        registry.route("/v1/islands/roles", this::roles);
        registry.route("/v1/islands/roles/upsert", this::upsertRole);
        registry.route("/v1/islands/roles/reset", this::resetRole);
    }

    private void permissions(HttpExchange exchange) throws IOException {
        String body = CoreHttpResponses.readBody(exchange);
        UUID islandId = JsonFields.uuid(body, "islandId", EMPTY_UUID);
        CoreHttpResponses.write(exchange, 200, permissionsJson(permissionRules.list(islandId), permissionRules.listPlayerOverrides(islandId)));
    }

    private void setPermission(HttpExchange exchange) throws IOException {
        String body = CoreHttpResponses.readBody(exchange);
        UUID islandId = JsonFields.uuid(body, "islandId", EMPTY_UUID);
        String roleKey = roleKey(body, IslandRole.MEMBER.name());
        IslandPermission permission = JsonFields.enumValue(IslandPermission.class, body, "permission", IslandPermission.BUILD);
        boolean allowed = JsonFields.bool(body, "allowed", false);
        UUID actorUuid = JsonFields.uuid(body, "actorUuid", EMPTY_UUID);
        if (!requireIslandPermission(exchange, islandId, actorUuid, IslandPermission.MANAGE_ROLES)) {
            return;
        }
        permissionRules.putRoleKey(islandId, roleKey, permission, allowed);
        audit.log(actorUuid, "PLAYER", "ISLAND_PERMISSION_SET", "ISLAND", islandId.toString(), Map.of("role", roleKey, "roleKey", roleKey, "permission", permission.name(), "allowed", Boolean.toString(allowed)));
        islandLogs.append(islandId, actorUuid, "ISLAND_PERMISSION_SET", Map.of("role", roleKey, "roleKey", roleKey, "permission", permission.name(), "allowed", Boolean.toString(allowed)));
        events.publish(CloudIslandEventType.ISLAND_PERMISSION_CHANGED.name(), Map.of("islandId", islandId.toString(), "role", roleKey, "roleKey", roleKey, "permission", permission.name(), "allowed", Boolean.toString(allowed)));
        CoreHttpResponses.write(exchange, 202, ApiResponses.ok(true));
    }

    private void setPermissionOverride(HttpExchange exchange) throws IOException {
        String body = CoreHttpResponses.readBody(exchange);
        UUID islandId = JsonFields.uuid(body, "islandId", EMPTY_UUID);
        UUID playerUuid = JsonFields.uuid(body, "playerUuid", EMPTY_UUID);
        IslandPermission permission = JsonFields.enumValue(IslandPermission.class, body, "permission", IslandPermission.BUILD);
        boolean allowed = JsonFields.bool(body, "allowed", false);
        UUID actorUuid = JsonFields.uuid(body, "actorUuid", EMPTY_UUID);
        if (!requireIslandPermission(exchange, islandId, actorUuid, IslandPermission.MANAGE_ROLES)) {
            return;
        }
        permissionRules.putPlayerOverride(islandId, playerUuid, permission, allowed);
        audit.log(actorUuid, "PLAYER", "ISLAND_PERMISSION_OVERRIDE_SET", "ISLAND", islandId.toString(), Map.of("playerUuid", playerUuid.toString(), "permission", permission.name(), "allowed", Boolean.toString(allowed)));
        islandLogs.append(islandId, actorUuid, "ISLAND_PERMISSION_OVERRIDE_SET", Map.of("playerUuid", playerUuid.toString(), "permission", permission.name(), "allowed", Boolean.toString(allowed)));
        events.publish(CloudIslandEventType.ISLAND_PERMISSION_CHANGED.name(), Map.of("islandId", islandId.toString(), "playerUuid", playerUuid.toString(), "permission", permission.name(), "allowed", Boolean.toString(allowed), "scope", "PLAYER"));
        CoreHttpResponses.write(exchange, 202, "{\"accepted\":true,\"islandId\":\"" + islandId + "\",\"playerUuid\":\"" + playerUuid + "\",\"permission\":\"" + permission.name() + "\",\"allowed\":" + allowed + "}");
    }

    private void roles(HttpExchange exchange) throws IOException {
        String body = CoreHttpResponses.readBody(exchange);
        CoreHttpResponses.write(exchange, 200, rolesJson(roleRepository.list(JsonFields.uuid(body, "islandId", EMPTY_UUID))));
    }

    private void upsertRole(HttpExchange exchange) throws IOException {
        String body = CoreHttpResponses.readBody(exchange);
        UUID islandId = JsonFields.uuid(body, "islandId", EMPTY_UUID);
        UUID actorUuid = JsonFields.uuid(body, "actorUuid", EMPTY_UUID);
        String roleKey = roleKey(body, IslandRole.CUSTOM_1.name());
        if (!IslandRoleRepository.editableRoleKey(roleKey)) {
            CoreHttpResponses.write(exchange, 409, ApiResponses.error("ROLE_NOT_EDITABLE", "Only island member roles can be customized"));
            return;
        }
        if (!requireIslandPermission(exchange, islandId, actorUuid, IslandPermission.MANAGE_ROLES)) {
            return;
        }
        int weight = JsonFields.integer(body, "weight", defaultWeight(roleKey));
        String displayName = JsonFields.text(body, "displayName", roleKey);
        IslandRoleSnapshot snapshot = roleRepository.upsertKey(islandId, roleKey, weight, displayName);
        audit.log(actorUuid, "PLAYER", "ISLAND_ROLE_UPSERT", "ISLAND", islandId.toString(), Map.of("role", roleKey, "roleKey", roleKey, "weight", Integer.toString(weight), "displayName", displayName));
        islandLogs.append(islandId, actorUuid, "ISLAND_ROLE_UPSERT", Map.of("role", roleKey, "roleKey", roleKey, "weight", Integer.toString(weight), "displayName", displayName));
        events.publish(CloudIslandEventType.ISLAND_ROLE_CHANGED.name(), Map.of("islandId", islandId.toString(), "role", roleKey, "roleKey", roleKey, "operation", "ROLE_UPSERT"));
        CoreHttpResponses.write(exchange, 202, roleJson(snapshot));
    }

    private void resetRole(HttpExchange exchange) throws IOException {
        String body = CoreHttpResponses.readBody(exchange);
        UUID islandId = JsonFields.uuid(body, "islandId", EMPTY_UUID);
        UUID actorUuid = JsonFields.uuid(body, "actorUuid", EMPTY_UUID);
        String roleKey = roleKey(body, IslandRole.CUSTOM_1.name());
        if (!IslandRoleRepository.editableRoleKey(roleKey)) {
            CoreHttpResponses.write(exchange, 409, ApiResponses.error("ROLE_NOT_EDITABLE", "Only island member roles can be reset"));
            return;
        }
        if (!requireIslandPermission(exchange, islandId, actorUuid, IslandPermission.MANAGE_ROLES)) {
            return;
        }
        boolean removed = roleRepository.resetKey(islandId, roleKey);
        audit.log(actorUuid, "PLAYER", "ISLAND_ROLE_RESET", "ISLAND", islandId.toString(), Map.of("role", roleKey, "roleKey", roleKey, "removed", Boolean.toString(removed)));
        islandLogs.append(islandId, actorUuid, "ISLAND_ROLE_RESET", Map.of("role", roleKey, "roleKey", roleKey, "removed", Boolean.toString(removed)));
        events.publish(CloudIslandEventType.ISLAND_ROLE_CHANGED.name(), Map.of("islandId", islandId.toString(), "role", roleKey, "roleKey", roleKey, "operation", "ROLE_RESET"));
        CoreHttpResponses.write(exchange, 202, "{\"accepted\":true,\"code\":\"ROLE_RESET\",\"role\":\"" + roleKey + "\",\"roleKey\":\"" + roleKey + "\",\"removed\":" + removed + "}");
    }

    private boolean requireIslandPermission(HttpExchange exchange, UUID islandId, UUID actorUuid, IslandPermission permission) throws IOException {
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

    static String permissionsJson(java.util.List<IslandPermissionRuleSnapshot> rules) {
        return permissionsJson(rules, java.util.List.of());
    }

    static String permissionsJson(java.util.List<IslandPermissionRuleSnapshot> rules, java.util.List<IslandPermissionOverrideSnapshot> overrides) {
        StringBuilder builder = new StringBuilder("{\"rules\":[");
        boolean first = true;
        for (IslandPermissionRuleSnapshot rule : rules) {
            if (!first) {
                builder.append(',');
            }
            first = false;
            builder.append('{')
                .append("\"islandId\":\"").append(rule.islandId()).append("\",")
                .append("\"role\":\"").append(rule.effectiveRoleKey()).append("\",")
                .append("\"roleKey\":\"").append(rule.effectiveRoleKey()).append("\",")
                .append("\"permission\":\"").append(rule.permission().name()).append("\",")
                .append("\"allowed\":").append(rule.allowed())
                .append('}');
        }
        builder.append("],\"overrides\":[");
        first = true;
        for (IslandPermissionOverrideSnapshot override : overrides) {
            if (!first) {
                builder.append(',');
            }
            first = false;
            builder.append('{')
                .append("\"islandId\":\"").append(override.islandId()).append("\",")
                .append("\"playerUuid\":\"").append(override.playerUuid()).append("\",")
                .append("\"permission\":\"").append(override.permission().name()).append("\",")
                .append("\"allowed\":").append(override.allowed())
                .append('}');
        }
        return builder.append("]}").toString();
    }

    static String rolesJson(java.util.List<IslandRoleSnapshot> roles) {
        StringBuilder builder = new StringBuilder("{\"roles\":[");
        boolean first = true;
        for (IslandRoleSnapshot role : roles) {
            if (!first) {
                builder.append(',');
            }
            first = false;
            builder.append(roleJson(role));
        }
        return builder.append("]}").toString();
    }

    static String roleJson(IslandRoleSnapshot role) {
        return "{\"islandId\":\"" + role.islandId()
            + "\",\"role\":\"" + role.effectiveRoleKey()
            + "\",\"roleKey\":\"" + role.effectiveRoleKey()
            + "\",\"weight\":" + role.weight()
            + ",\"displayName\":\"" + escape(role.displayName())
            + "\"}";
    }

    private static String roleKey(String body, String fallback) {
        String value = JsonFields.text(body, "roleKey", "");
        if (value.isBlank()) {
            value = JsonFields.text(body, "role", fallback);
        }
        return IslandRoleRepository.normalizeRoleKey(value);
    }

    private static int defaultWeight(String roleKey) {
        try {
            return IslandRole.valueOf(roleKey).ordinal();
        } catch (IllegalArgumentException exception) {
            return 100;
        }
    }

    private static String escape(String value) {
        return value == null ? "" : value.replace("\\", "\\\\").replace("\"", "\\\"");
    }
}
