package kr.lunaf.cloudislands.coreservice.http.routes;

import com.sun.net.httpserver.HttpExchange;
import java.io.IOException;
import java.util.LinkedHashMap;
import java.util.Map;
import java.util.Optional;
import java.util.UUID;
import kr.lunaf.cloudislands.api.environment.IslandBiomePolicy;
import kr.lunaf.cloudislands.api.model.IslandBiomeSnapshot;
import kr.lunaf.cloudislands.api.model.IslandFlag;
import kr.lunaf.cloudislands.api.model.IslandFlagsSnapshot;
import kr.lunaf.cloudislands.api.model.IslandPermission;
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
        registry.routePost("/v1/islands/lock", this::setLock);
        registry.routePost("/v1/islands/name", this::rename);
        registry.routePost("/v1/admin/islands/name", this::adminRename);
        registry.routePost("/v1/islands/flags", this::flags);
        registry.routePost("/v1/islands/biome", this::biome);
        registry.routePost("/v1/islands/biome/set", this::setBiome);
        registry.routePost("/v1/admin/islands/biome/set", this::adminSetBiome);
        registry.routePost("/v1/islands/flags/set", this::setFlag);
        registry.routePost("/v1/admin/islands/flags/set", this::adminSetFlag);
        registry.routePost("/v1/admin/islands/flags/reset", this::adminResetFlags);
        registry.routePost("/v1/islands/access", this::setAccess);
    }

    private void setLock(HttpExchange exchange) throws IOException {
        String body = CoreHttpResponses.readBody(exchange);
        UUID islandId = JsonFields.uuid(body, "islandId", EMPTY_UUID);
        boolean locked = JsonFields.bool(body, "locked", false);
        UUID actorUuid = JsonFields.uuid(body, "actorUuid", EMPTY_UUID);
        if (!requireIslandPermission(exchange, islandId, actorUuid, IslandPermission.MANAGE_FLAGS)) {
            return;
        }
        String result = metadataRepository.setLockedMutationResult(islandId, locked);
        if (result.equals("ISLAND_NOT_FOUND")) {
            CoreHttpResponses.write(exchange, 404, ApiResponses.error("ISLAND_NOT_FOUND", "Island was not found"));
            return;
        }
        if (result.equals("UNCHANGED")) {
            CoreHttpResponses.write(exchange, 200, settingsActionJson("ISLAND_LOCK_UNCHANGED"));
            return;
        }
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
        String result = islandRepository.renameResult(islandId, name);
        if (result.equals("UNCHANGED")) {
            CoreHttpResponses.write(exchange, 200, renameJson(islandId, name, "ISLAND_NAME_UNCHANGED"));
            return;
        }
        if (!result.equals("APPLIED")) {
            int status = result.equals("ISLAND_NOT_FOUND") ? 404 : 409;
            String message = result.equals("ISLAND_NAME_TAKEN") ? "Island name is already used" : result.equals("ISLAND_NOT_FOUND") ? "Island was not found" : "Island was not renamed";
            CoreHttpResponses.write(exchange, status, ApiResponses.error(result.equals("RENAME_DENIED") ? "ISLAND_RENAME_DENIED" : result, message));
            return;
        }
        audit.log(actorUuid, "PLAYER", "ISLAND_RENAME", "ISLAND", islandId.toString(), Map.of("name", name));
        islandLogs.append(islandId, actorUuid, "ISLAND_RENAME", Map.of("name", name));
        events.publish(CloudIslandEventType.ISLAND_RENAMED.name(), Map.of("islandId", islandId.toString(), "actorUuid", actorUuid.toString(), "name", name));
        CoreHttpResponses.write(exchange, 202, renameJson(islandId, name));
    }

    private void adminRename(HttpExchange exchange) throws IOException {
        String body = CoreHttpResponses.readBody(exchange);
        UUID islandId = JsonFields.uuid(body, "islandId", EMPTY_UUID);
        String name = JsonFields.text(body, "name", "").trim();
        if (islandRepository.findById(islandId).isEmpty()) {
            CoreHttpResponses.write(exchange, 404, ApiResponses.error("ISLAND_NOT_FOUND", "Island was not found"));
            return;
        }
        if (name.length() < 2 || name.length() > 32 || name.chars().anyMatch(Character::isISOControl)) {
            CoreHttpResponses.write(exchange, 400, ApiResponses.error("INVALID_ISLAND_NAME", "Island name must be 2-32 visible characters"));
            return;
        }
        String result = islandRepository.renameResult(islandId, name);
        if (result.equals("UNCHANGED")) {
            CoreHttpResponses.write(exchange, 200, renameJson(islandId, name, "ISLAND_NAME_UNCHANGED"));
            return;
        }
        if (!result.equals("APPLIED")) {
            int status = result.equals("ISLAND_NOT_FOUND") ? 404 : 409;
            String message = result.equals("ISLAND_NAME_TAKEN") ? "Island name is already used" : result.equals("ISLAND_NOT_FOUND") ? "Island was not found" : "Island was not renamed";
            CoreHttpResponses.write(exchange, status, ApiResponses.error(result.equals("RENAME_DENIED") ? "ISLAND_RENAME_DENIED" : result, message));
            return;
        }
        audit.log(EMPTY_UUID, "ADMIN", "ISLAND_ADMIN_RENAME", "ISLAND", islandId.toString(), Map.of("name", name));
        islandLogs.append(islandId, EMPTY_UUID, "ISLAND_ADMIN_RENAME", Map.of("name", name));
        events.publish(CloudIslandEventType.ISLAND_RENAMED.name(), Map.of("islandId", islandId.toString(), "actorType", "ADMIN", "name", name));
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
        Optional<String> biomeKey = IslandBiomePolicy.normalize(JsonFields.text(body, "biomeKey", "minecraft:plains"));
        if (biomeKey.isEmpty()) {
            CoreHttpResponses.write(exchange, 400, ApiResponses.error("INVALID_BIOME_KEY", "Unsupported island biome"));
            return;
        }
        if (!requireIslandPermission(exchange, islandId, actorUuid, IslandPermission.SET_BIOME)) {
            return;
        }
        String result = metadataRepository.setBiomeResult(islandId, biomeKey.get(), actorUuid);
        if ("ISLAND_NOT_FOUND".equals(result)) {
            CoreHttpResponses.write(exchange, 404, ApiResponses.error("ISLAND_NOT_FOUND", "Island was not found"));
            return;
        }
        if ("UNCHANGED".equals(result)) {
            CoreHttpResponses.write(exchange, 202, biomeSetJson(islandId, actorUuid, biomeKey.get(), "BIOME_UNCHANGED"));
            return;
        }
        audit.log(actorUuid, "PLAYER", "ISLAND_BIOME_SET", "ISLAND", islandId.toString(), Map.of("biomeKey", biomeKey.get()));
        islandLogs.append(islandId, actorUuid, "ISLAND_BIOME_SET", Map.of("biomeKey", biomeKey.get()));
        events.publish(CloudIslandEventType.ISLAND_BIOME_CHANGED.name(), Map.of("islandId", islandId.toString(), "biomeKey", biomeKey.get()));
        CoreHttpResponses.write(exchange, 202, biomeSetJson(islandId, actorUuid, biomeKey.get(), "BIOME_SET"));
    }

    private void adminSetBiome(HttpExchange exchange) throws IOException {
        String body = CoreHttpResponses.readBody(exchange);
        UUID islandId = JsonFields.uuid(body, "islandId", EMPTY_UUID);
        Optional<String> biomeKey = IslandBiomePolicy.normalize(JsonFields.text(body, "biomeKey", "minecraft:plains"));
        if (biomeKey.isEmpty()) {
            CoreHttpResponses.write(exchange, 400, ApiResponses.error("INVALID_BIOME_KEY", "Unsupported island biome"));
            return;
        }
        if (islandRepository.findById(islandId).isEmpty()) {
            CoreHttpResponses.write(exchange, 404, ApiResponses.error("ISLAND_NOT_FOUND", "Island was not found"));
            return;
        }
        String result = metadataRepository.setBiomeResult(islandId, biomeKey.get(), EMPTY_UUID);
        if ("ISLAND_NOT_FOUND".equals(result)) {
            CoreHttpResponses.write(exchange, 404, ApiResponses.error("ISLAND_NOT_FOUND", "Island was not found"));
            return;
        }
        if ("UNCHANGED".equals(result)) {
            CoreHttpResponses.write(exchange, 202, biomeSetJson(islandId, EMPTY_UUID, biomeKey.get(), "BIOME_UNCHANGED"));
            return;
        }
        audit.log(EMPTY_UUID, "ADMIN", "ISLAND_BIOME_ADMIN_SET", "ISLAND", islandId.toString(), Map.of("biomeKey", biomeKey.get()));
        islandLogs.append(islandId, EMPTY_UUID, "ISLAND_BIOME_ADMIN_SET", Map.of("biomeKey", biomeKey.get()));
        events.publish(CloudIslandEventType.ISLAND_BIOME_CHANGED.name(), Map.of("islandId", islandId.toString(), "actorType", "ADMIN", "biomeKey", biomeKey.get()));
        CoreHttpResponses.write(exchange, 202, biomeSetJson(islandId, EMPTY_UUID, biomeKey.get(), "BIOME_SET"));
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
        String result = metadataRepository.setFlagResult(islandId, flag, value);
        if (result.equals("ISLAND_NOT_FOUND")) {
            CoreHttpResponses.write(exchange, 404, ApiResponses.error("ISLAND_NOT_FOUND", "Island was not found"));
            return;
        }
        if (result.equals("UNCHANGED")) {
            CoreHttpResponses.write(exchange, 200, settingsActionJson("ISLAND_FLAG_UNCHANGED"));
            return;
        }
        audit.log(actorUuid, "PLAYER", "ISLAND_FLAG_SET", "ISLAND", islandId.toString(), Map.of("flag", flag.name(), "value", value));
        islandLogs.append(islandId, actorUuid, "ISLAND_FLAG_SET", Map.of("flag", flag.name(), "value", value));
        events.publish(CloudIslandEventType.ISLAND_FLAG_CHANGED.name(), Map.of("islandId", islandId.toString(), "flag", flag.name(), "value", value));
        CoreHttpResponses.write(exchange, 202, ApiResponses.ok(true));
    }

    private void adminSetFlag(HttpExchange exchange) throws IOException {
        String body = CoreHttpResponses.readBody(exchange);
        UUID islandId = JsonFields.uuid(body, "islandId", EMPTY_UUID);
        IslandFlag flag = JsonFields.enumValue(IslandFlag.class, body, "flag", IslandFlag.VISITOR_INTERACT);
        String value = JsonFields.text(body, "value", "false");
        if (islandRepository.findById(islandId).isEmpty()) {
            CoreHttpResponses.write(exchange, 404, ApiResponses.error("ISLAND_NOT_FOUND", "Island was not found"));
            return;
        }
        String result = metadataRepository.setFlagResult(islandId, flag, value);
        if (result.equals("UNCHANGED")) {
            CoreHttpResponses.write(exchange, 200, settingsActionJson("ISLAND_FLAG_UNCHANGED"));
            return;
        }
        if (result.equals("ISLAND_NOT_FOUND")) {
            CoreHttpResponses.write(exchange, 404, ApiResponses.error("ISLAND_NOT_FOUND", "Island was not found"));
            return;
        }
        audit.log(EMPTY_UUID, "ADMIN", "ISLAND_FLAG_ADMIN_SET", "ISLAND", islandId.toString(), Map.of("flag", flag.name(), "value", value));
        islandLogs.append(islandId, EMPTY_UUID, "ISLAND_FLAG_ADMIN_SET", Map.of("flag", flag.name(), "value", value));
        events.publish(CloudIslandEventType.ISLAND_FLAG_CHANGED.name(), Map.of("islandId", islandId.toString(), "actorType", "ADMIN", "flag", flag.name(), "value", value));
        CoreHttpResponses.write(exchange, 202, ApiResponses.ok(true));
    }

    private void adminResetFlags(HttpExchange exchange) throws IOException {
        String body = CoreHttpResponses.readBody(exchange);
        UUID islandId = JsonFields.uuid(body, "islandId", EMPTY_UUID);
        if (islandRepository.findById(islandId).isEmpty()) {
            CoreHttpResponses.write(exchange, 404, ApiResponses.error("ISLAND_NOT_FOUND", "Island was not found"));
            return;
        }
        boolean removed = metadataRepository.resetFlags(islandId);
        if (!removed) {
            CoreHttpResponses.write(exchange, 200, settingsActionJson("ISLAND_FLAGS_UNCHANGED"));
            return;
        }
        audit.log(EMPTY_UUID, "ADMIN", "ISLAND_FLAGS_ADMIN_RESET", "ISLAND", islandId.toString(), Map.of("removed", Boolean.toString(removed)));
        islandLogs.append(islandId, EMPTY_UUID, "ISLAND_FLAGS_ADMIN_RESET", Map.of("removed", Boolean.toString(removed)));
        events.publish(CloudIslandEventType.ISLAND_FLAG_CHANGED.name(), Map.of("islandId", islandId.toString(), "actorType", "ADMIN", "operation", "FLAGS_RESET", "removed", Boolean.toString(removed)));
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
        String result = islandRepository.setPublicAccessMutationResult(islandId, publicAccess);
        if (result.equals("ISLAND_NOT_FOUND")) {
            CoreHttpResponses.write(exchange, 404, ApiResponses.error("ISLAND_NOT_FOUND", "Island was not found"));
            return;
        }
        metadataRepository.setPublicAccess(islandId, publicAccess);
        if (result.equals("UNCHANGED")) {
            CoreHttpResponses.write(exchange, 200, settingsActionJson("ISLAND_ACCESS_UNCHANGED"));
            return;
        }
        audit.log(actorUuid, "PLAYER", "ISLAND_ACCESS_SET", "ISLAND", islandId.toString(), Map.of("publicAccess", Boolean.toString(publicAccess)));
        islandLogs.append(islandId, actorUuid, "ISLAND_ACCESS_SET", Map.of("publicAccess", Boolean.toString(publicAccess)));
        events.publish(CloudIslandEventType.ISLAND_ACCESS_CHANGED.name(), Map.of("islandId", islandId.toString(), "publicAccess", Boolean.toString(publicAccess)));
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

    static String renameJson(UUID islandId, String name) {
        return renameJson(islandId, name, "ISLAND_RENAMED");
    }

    static String renameJson(UUID islandId, String name, String code) {
        LinkedHashMap<String, Object> values = new LinkedHashMap<>();
        values.put("accepted", true);
        values.put("code", code);
        values.put("islandId", islandId);
        values.put("name", name);
        return SimpleJson.stringify(values);
    }

    static String settingsActionJson(String code) {
        return SimpleJson.stringify(Map.of("accepted", true, "code", code));
    }

    static String flagsJson(IslandFlagsSnapshot flags) {
        LinkedHashMap<String, Object> values = new LinkedHashMap<>();
        LinkedHashMap<String, Object> flagValues = new LinkedHashMap<>();
        for (Map.Entry<IslandFlag, String> entry : flags.values().entrySet()) {
            flagValues.put(entry.getKey().name(), entry.getValue());
        }
        values.put("islandId", flags.islandId());
        values.put("flags", flagValues);
        return SimpleJson.stringify(values);
    }

    static String biomeJson(IslandBiomeSnapshot biome) {
        LinkedHashMap<String, Object> values = new LinkedHashMap<>();
        values.put("islandId", biome.islandId());
        values.put("biomeKey", biome.biomeKey());
        values.put("updatedBy", biome.updatedBy());
        values.put("updatedAt", biome.updatedAt());
        return SimpleJson.stringify(values);
    }

    static String biomeSetJson(UUID islandId, UUID actorUuid, String biomeKey) {
        return biomeSetJson(islandId, actorUuid, biomeKey, "BIOME_SET");
    }

    static String biomeSetJson(UUID islandId, UUID actorUuid, String biomeKey, String code) {
        LinkedHashMap<String, Object> values = new LinkedHashMap<>();
        values.put("accepted", true);
        values.put("code", code);
        values.put("islandId", islandId);
        values.put("biomeKey", biomeKey);
        values.put("updatedBy", actorUuid);
        return SimpleJson.stringify(values);
    }
}
