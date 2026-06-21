package kr.lunaf.cloudislands.coreservice.http.routes;

import com.sun.net.httpserver.HttpExchange;
import java.io.IOException;
import java.util.ArrayList;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import java.util.UUID;
import kr.lunaf.cloudislands.api.model.IslandPermission;
import kr.lunaf.cloudislands.api.model.IslandPermissionOverrideSnapshot;
import kr.lunaf.cloudislands.api.model.IslandPermissionRuleSnapshot;
import kr.lunaf.cloudislands.api.model.IslandRole;
import kr.lunaf.cloudislands.api.model.IslandRoleSnapshot;
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
import kr.lunaf.cloudislands.coreservice.permission.IslandPermissionRuleRepository;
import kr.lunaf.cloudislands.coreservice.repository.IslandMetadataRepository;
import kr.lunaf.cloudislands.coreservice.repository.IslandRepository;
import kr.lunaf.cloudislands.coreservice.role.IslandRoleRepository;

public final class PermissionRoleRoutes implements RouteGroup {
    private static final String DEFAULT_CUSTOM_ROLE_KEY = "BUILDER";
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
        if (!requirePermissionVersion(exchange, body, islandId)) {
            return;
        }
        permissionRules.putRoleKey(islandId, roleKey, permission, allowed);
        audit.log(actorUuid, "PLAYER", "ISLAND_PERMISSION_SET", "ISLAND", islandId.toString(), Map.of("role", roleKey, "roleKey", roleKey, "permission", permission.name(), "allowed", Boolean.toString(allowed)));
        islandLogs.append(islandId, actorUuid, "ISLAND_PERMISSION_SET", Map.of("role", roleKey, "roleKey", roleKey, "permission", permission.name(), "allowed", Boolean.toString(allowed)));
        events.publish(CloudIslandEventType.ISLAND_PERMISSION_CHANGED.name(), Map.of("islandId", islandId.toString(), "role", roleKey, "roleKey", roleKey, "permission", permission.name(), "allowed", Boolean.toString(allowed)));
        CoreHttpResponses.write(exchange, 202, permissionSetJson(permissionVersion(islandId)));
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
        if (!requirePermissionVersion(exchange, body, islandId)) {
            return;
        }
        permissionRules.putPlayerOverride(islandId, playerUuid, permission, allowed);
        audit.log(actorUuid, "PLAYER", "ISLAND_PERMISSION_OVERRIDE_SET", "ISLAND", islandId.toString(), Map.of("playerUuid", playerUuid.toString(), "permission", permission.name(), "allowed", Boolean.toString(allowed)));
        islandLogs.append(islandId, actorUuid, "ISLAND_PERMISSION_OVERRIDE_SET", Map.of("playerUuid", playerUuid.toString(), "permission", permission.name(), "allowed", Boolean.toString(allowed)));
        events.publish(CloudIslandEventType.ISLAND_PERMISSION_CHANGED.name(), Map.of("islandId", islandId.toString(), "playerUuid", playerUuid.toString(), "permission", permission.name(), "allowed", Boolean.toString(allowed), "scope", "PLAYER"));
        CoreHttpResponses.write(exchange, 202, permissionOverrideSetJson(islandId, playerUuid, permission, allowed, permissionVersion(islandId)));
    }

    private void roles(HttpExchange exchange) throws IOException {
        String body = CoreHttpResponses.readBody(exchange);
        CoreHttpResponses.write(exchange, 200, rolesJson(roleRepository.list(JsonFields.uuid(body, "islandId", EMPTY_UUID))));
    }

    private void upsertRole(HttpExchange exchange) throws IOException {
        String body = CoreHttpResponses.readBody(exchange);
        UUID islandId = JsonFields.uuid(body, "islandId", EMPTY_UUID);
        UUID actorUuid = JsonFields.uuid(body, "actorUuid", EMPTY_UUID);
        String roleKey = roleKey(body, DEFAULT_CUSTOM_ROLE_KEY);
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
        String roleKey = roleKey(body, DEFAULT_CUSTOM_ROLE_KEY);
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
        CoreHttpResponses.write(exchange, 202, roleResetJson(roleKey, removed));
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

    private boolean requirePermissionVersion(HttpExchange exchange, String body, UUID islandId) throws IOException {
        String expectedVersion = JsonFields.text(body, "expectedVersion", "");
        if (expectedVersion.isBlank()) {
            return true;
        }
        String currentVersion = permissionVersion(islandId);
        if (expectedVersion.equals(currentVersion)) {
            return true;
        }
        CoreHttpResponses.write(exchange, 409, ApiResponses.error("PERMISSION_VERSION_CONFLICT", "Island permissions changed; reload before saving", Map.of("currentVersion", currentVersion)));
        return false;
    }

    static String permissionsJson(List<IslandPermissionRuleSnapshot> rules) {
        return permissionsJson(rules, List.of());
    }

    static String permissionsJson(List<IslandPermissionRuleSnapshot> rules, List<IslandPermissionOverrideSnapshot> overrides) {
        List<Object> renderedRules = new ArrayList<>();
        for (IslandPermissionRuleSnapshot rule : rules) {
            renderedRules.add(permissionRuleMap(rule));
        }
        List<Object> renderedOverrides = new ArrayList<>();
        for (IslandPermissionOverrideSnapshot override : overrides) {
            renderedOverrides.add(permissionOverrideMap(override));
        }
        LinkedHashMap<String, Object> root = new LinkedHashMap<>();
        root.put("version", permissionVersion(rules, overrides));
        root.put("rules", renderedRules);
        root.put("overrides", renderedOverrides);
        return SimpleJson.stringify(root);
    }

    private String permissionVersion(UUID islandId) {
        return permissionVersion(permissionRules.list(islandId), permissionRules.listPlayerOverrides(islandId));
    }

    static String permissionVersion(List<IslandPermissionRuleSnapshot> rules, List<IslandPermissionOverrideSnapshot> overrides) {
        List<String> entries = new ArrayList<>();
        for (IslandPermissionRuleSnapshot rule : rules) {
            entries.add("role:" + rule.islandId() + ":" + rule.effectiveRoleKey() + ":" + rule.permission().name() + ":" + rule.allowed());
        }
        for (IslandPermissionOverrideSnapshot override : overrides) {
            entries.add("player:" + override.islandId() + ":" + override.playerUuid() + ":" + override.permission().name() + ":" + override.allowed());
        }
        java.util.Collections.sort(entries);
        return Long.toUnsignedString(Integer.toUnsignedLong(String.join("|", entries).hashCode()), 36);
    }

    static String rolesJson(List<IslandRoleSnapshot> roles) {
        List<Object> renderedRoles = new ArrayList<>();
        for (IslandRoleSnapshot role : roles) {
            renderedRoles.add(roleMap(role));
        }
        return SimpleJson.stringify(Map.of("roles", renderedRoles));
    }

    static String roleJson(IslandRoleSnapshot role) {
        return SimpleJson.stringify(roleMap(role));
    }

    private static String permissionSetJson(String version) {
        LinkedHashMap<String, Object> values = new LinkedHashMap<>();
        values.put("accepted", true);
        values.put("version", version);
        return SimpleJson.stringify(values);
    }

    private static String permissionOverrideSetJson(UUID islandId, UUID playerUuid, IslandPermission permission, boolean allowed, String version) {
        LinkedHashMap<String, Object> values = new LinkedHashMap<>();
        values.put("accepted", true);
        values.put("islandId", islandId);
        values.put("playerUuid", playerUuid);
        values.put("permission", permission.name());
        values.put("allowed", allowed);
        values.put("version", version);
        return SimpleJson.stringify(values);
    }

    private static String roleResetJson(String roleKey, boolean removed) {
        LinkedHashMap<String, Object> values = new LinkedHashMap<>();
        values.put("accepted", true);
        values.put("code", "ROLE_RESET");
        values.put("role", roleKey);
        values.put("roleKey", roleKey);
        values.put("removed", removed);
        return SimpleJson.stringify(values);
    }

    private static Map<String, Object> permissionRuleMap(IslandPermissionRuleSnapshot rule) {
        LinkedHashMap<String, Object> values = new LinkedHashMap<>();
        values.put("islandId", rule.islandId());
        values.put("role", rule.effectiveRoleKey());
        values.put("roleKey", rule.effectiveRoleKey());
        values.put("permission", rule.permission().name());
        values.put("allowed", rule.allowed());
        return values;
    }

    private static Map<String, Object> permissionOverrideMap(IslandPermissionOverrideSnapshot override) {
        LinkedHashMap<String, Object> values = new LinkedHashMap<>();
        values.put("islandId", override.islandId());
        values.put("playerUuid", override.playerUuid());
        values.put("permission", override.permission().name());
        values.put("allowed", override.allowed());
        return values;
    }

    private static Map<String, Object> roleMap(IslandRoleSnapshot role) {
        LinkedHashMap<String, Object> values = new LinkedHashMap<>();
        values.put("islandId", role.islandId());
        values.put("role", role.effectiveRoleKey());
        values.put("roleKey", role.effectiveRoleKey());
        values.put("weight", role.weight());
        values.put("displayName", role.displayName());
        return values;
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

}
