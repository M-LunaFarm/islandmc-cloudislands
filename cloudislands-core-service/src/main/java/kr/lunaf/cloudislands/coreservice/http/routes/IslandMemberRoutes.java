package kr.lunaf.cloudislands.coreservice.http.routes;

import com.sun.net.httpserver.HttpExchange;
import java.io.IOException;
import java.time.Instant;
import java.util.ArrayList;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import java.util.UUID;
import kr.lunaf.cloudislands.api.model.IslandMemberSnapshot;
import kr.lunaf.cloudislands.api.model.IslandPermission;
import kr.lunaf.cloudislands.api.model.IslandRole;
import kr.lunaf.cloudislands.api.model.IslandSnapshot;
import kr.lunaf.cloudislands.api.model.PlayerIslandProfile;
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
import kr.lunaf.cloudislands.coreservice.permission.IslandPermissionRuleRepository;
import kr.lunaf.cloudislands.coreservice.profile.PlayerProfileRepository;
import kr.lunaf.cloudislands.coreservice.repository.IslandMetadataRepository;
import kr.lunaf.cloudislands.coreservice.repository.IslandRepository;
import kr.lunaf.cloudislands.coreservice.role.IslandRoleRepository;

public final class IslandMemberRoutes implements RouteGroup {
    private static final UUID EMPTY_UUID = new UUID(0L, 0L);

    private final IslandRepository islandRepository;
    private final IslandMetadataRepository metadataRepository;
    private final IslandLimitRepository limitRepository;
    private final IslandPermissionRuleRepository permissionRules;
    private final PlayerProfileRepository playerProfiles;
    private final IslandLogRepository islandLogs;
    private final AuditLogger audit;
    private final GlobalEventPublisher events;

    public IslandMemberRoutes(
            IslandRepository islandRepository,
            IslandMetadataRepository metadataRepository,
            IslandLimitRepository limitRepository,
            IslandPermissionRuleRepository permissionRules,
            PlayerProfileRepository playerProfiles,
            IslandLogRepository islandLogs,
            AuditLogger audit,
            GlobalEventPublisher events) {
        this.islandRepository = islandRepository;
        this.metadataRepository = metadataRepository;
        this.limitRepository = limitRepository;
        this.permissionRules = permissionRules;
        this.playerProfiles = playerProfiles;
        this.islandLogs = islandLogs;
        this.audit = audit;
        this.events = events;
    }

    @Override
    public void register(CoreRouteRegistry registry) {
        registry.route("/v1/islands/members", this::members);
        registry.route("/v1/players/islands", this::playerIslands);
        registry.route("/v1/islands/members/set", this::setMember);
        registry.route("/v1/islands/members/trust-temporary", this::trustTemporary);
        registry.route("/v1/islands/transfer", this::transferOwnership);
        registry.route("/v1/islands/members/remove", this::removeMember);
    }

    private void members(HttpExchange exchange) throws IOException {
        String body = CoreHttpResponses.readBody(exchange);
        CoreHttpResponses.write(exchange, 200, membersJson(metadataRepository.members(JsonFields.uuid(body, "islandId", EMPTY_UUID)), playerProfiles));
    }

    private void playerIslands(HttpExchange exchange) throws IOException {
        String body = CoreHttpResponses.readBody(exchange);
        ArrayList<IslandSnapshot> islands = new ArrayList<>();
        for (IslandMemberSnapshot member : metadataRepository.islandsForMember(JsonFields.uuid(body, "playerUuid", EMPTY_UUID))) {
            islandRepository.findById(member.islandId()).ifPresent(islands::add);
        }
        CoreHttpResponses.write(exchange, 200, islandsJson(islands));
    }

    private void setMember(HttpExchange exchange) throws IOException {
        String body = CoreHttpResponses.readBody(exchange);
        UUID islandId = JsonFields.uuid(body, "islandId", EMPTY_UUID);
        UUID playerUuid = JsonFields.uuid(body, "playerUuid", EMPTY_UUID);
        String roleKey = roleKey(body, IslandRole.MEMBER.name());
        UUID actorUuid = JsonFields.uuid(body, "actorUuid", EMPTY_UUID);
        if (!requireIslandPermission(exchange, islandId, actorUuid, IslandPermission.MANAGE_ROLES)) {
            return;
        }
        List<IslandMemberSnapshot> members = metadataRepository.members(islandId);
        IslandMemberSnapshot currentMember = member(members, playerUuid);
        String currentRoleKey = currentMember == null ? "" : currentMember.effectiveRoleKey();
        if (roleKey.equals(IslandRole.OWNER.name()) || currentRoleKey.equals(IslandRole.OWNER.name())) {
            CoreHttpResponses.write(exchange, 409, ApiResponses.error("OWNER_ROLE_PROTECTED", "Island ownership must be changed through ownership transfer"));
            return;
        }
        if (!IslandRoleRepository.editableRoleKey(roleKey)) {
            CoreHttpResponses.write(exchange, 409, ApiResponses.error("MEMBER_ROLE_UNAVAILABLE", "Visitor and banned roles are not managed as island members"));
            return;
        }
        boolean existingMember = currentMember != null;
        if (!existingMember && members.size() >= limitValue(islandId, "MEMBERS", 3L)) {
            CoreHttpResponses.write(exchange, 409, ApiResponses.error("MEMBER_LIMIT", "Island member limit was reached"));
            return;
        }
        metadataRepository.upsertMemberKey(islandId, playerUuid, roleKey);
        audit.log(actorUuid, "PLAYER", "ISLAND_MEMBER_SET", "ISLAND", islandId.toString(), Map.of("playerUuid", playerUuid.toString(), "role", roleKey, "roleKey", roleKey));
        islandLogs.append(islandId, actorUuid, "ISLAND_MEMBER_SET", Map.of("playerUuid", playerUuid.toString(), "role", roleKey, "roleKey", roleKey));
        events.publish(existingMember ? CloudIslandEventType.ISLAND_MEMBER_ROLE_CHANGED.name() : CloudIslandEventType.ISLAND_MEMBER_JOINED.name(), existingMember
            ? Map.of("islandId", islandId.toString(), "playerUuid", playerUuid.toString(), "oldRole", currentRoleKey, "oldRoleKey", currentRoleKey, "newRole", roleKey, "newRoleKey", roleKey)
            : Map.of("islandId", islandId.toString(), "playerUuid", playerUuid.toString(), "role", roleKey, "roleKey", roleKey));
        events.publish(CloudIslandEventType.ISLAND_MEMBER_CHANGED.name(), Map.of("islandId", islandId.toString(), "playerUuid", playerUuid.toString(), "role", roleKey, "roleKey", roleKey));
        CoreHttpResponses.write(exchange, 202, ApiResponses.ok(true));
    }

    private void trustTemporary(HttpExchange exchange) throws IOException {
        String body = CoreHttpResponses.readBody(exchange);
        UUID islandId = JsonFields.uuid(body, "islandId", EMPTY_UUID);
        UUID playerUuid = JsonFields.uuid(body, "playerUuid", EMPTY_UUID);
        UUID actorUuid = JsonFields.uuid(body, "actorUuid", EMPTY_UUID);
        long seconds = Math.max(60L, Math.min(JsonFields.longValue(body, "durationSeconds", 3600L), 2_592_000L));
        if (!requireIslandPermission(exchange, islandId, actorUuid, IslandPermission.MANAGE_ROLES)) {
            return;
        }
        List<IslandMemberSnapshot> members = metadataRepository.members(islandId);
        IslandMemberSnapshot currentMember = member(members, playerUuid);
        String currentRoleKey = currentMember == null ? "" : currentMember.effectiveRoleKey();
        if (currentRoleKey.equals(IslandRole.OWNER.name())) {
            CoreHttpResponses.write(exchange, 409, ApiResponses.error("OWNER_ROLE_PROTECTED", "Island owner cannot be temporary trusted"));
            return;
        }
        boolean existingMember = currentMember != null;
        if (!existingMember && members.size() >= limitValue(islandId, "MEMBERS", 3L)) {
            CoreHttpResponses.write(exchange, 409, ApiResponses.error("MEMBER_LIMIT", "Island member limit was reached"));
            return;
        }
        Instant expiresAt = Instant.now().plusSeconds(seconds);
        metadataRepository.upsertMember(islandId, playerUuid, IslandRole.TRUSTED, expiresAt);
        Map<String, String> fields = Map.of(
            "playerUuid", playerUuid.toString(),
            "role", IslandRole.TRUSTED.name(),
            "expiresAt", expiresAt.toString(),
            "durationSeconds", Long.toString(seconds)
        );
        audit.log(actorUuid, "PLAYER", "ISLAND_MEMBER_TEMP_TRUST", "ISLAND", islandId.toString(), fields);
        islandLogs.append(islandId, actorUuid, "ISLAND_MEMBER_TEMP_TRUST", fields);
        events.publish(CloudIslandEventType.ISLAND_MEMBER_ROLE_CHANGED.name(), Map.of(
            "islandId", islandId.toString(),
            "playerUuid", playerUuid.toString(),
            "oldRole", currentMember == null ? IslandRole.VISITOR.name() : currentRoleKey,
            "oldRoleKey", currentMember == null ? IslandRole.VISITOR.name() : currentRoleKey,
            "newRole", IslandRole.TRUSTED.name(),
            "newRoleKey", IslandRole.TRUSTED.name(),
            "expiresAt", expiresAt.toString()
        ));
        events.publish(CloudIslandEventType.ISLAND_MEMBER_CHANGED.name(), Map.of("islandId", islandId.toString(), "playerUuid", playerUuid.toString(), "role", IslandRole.TRUSTED.name(), "roleKey", IslandRole.TRUSTED.name(), "expiresAt", expiresAt.toString()));
        CoreHttpResponses.write(exchange, 202, temporaryTrustJson(islandId, playerUuid, expiresAt, seconds));
    }

    private void transferOwnership(HttpExchange exchange) throws IOException {
        String body = CoreHttpResponses.readBody(exchange);
        UUID islandId = JsonFields.uuid(body, "islandId", EMPTY_UUID);
        UUID actorUuid = JsonFields.uuid(body, "actorUuid", EMPTY_UUID);
        UUID targetUuid = JsonFields.uuid(body, "targetUuid", EMPTY_UUID);
        boolean transferred = islandRepository.transferOwnership(islandId, actorUuid, targetUuid);
        if (transferred) {
            metadataRepository.upsertMember(islandId, actorUuid, IslandRole.CO_OWNER);
            metadataRepository.upsertMember(islandId, targetUuid, IslandRole.OWNER);
            playerProfiles.clearPrimaryIsland(actorUuid);
            playerProfiles.setPrimaryIsland(targetUuid, islandId);
        }
        audit.log(actorUuid, "PLAYER", "ISLAND_OWNERSHIP_TRANSFER", "ISLAND", islandId.toString(), Map.of("targetUuid", targetUuid.toString(), "transferred", Boolean.toString(transferred)));
        islandLogs.append(islandId, actorUuid, "ISLAND_OWNERSHIP_TRANSFER", Map.of("targetUuid", targetUuid.toString(), "transferred", Boolean.toString(transferred)));
        if (transferred) {
            events.publish(CloudIslandEventType.ISLAND_OWNERSHIP_CHANGED.name(), Map.of("islandId", islandId.toString(), "actorUuid", actorUuid.toString(), "targetUuid", targetUuid.toString()));
            events.publish(CloudIslandEventType.ISLAND_MEMBER_CHANGED.name(), Map.of("islandId", islandId.toString(), "actorUuid", actorUuid.toString(), "targetUuid", targetUuid.toString()));
        }
        CoreHttpResponses.write(exchange, transferred ? 202 : 409, transferred ? ApiResponses.ok(true) : ApiResponses.error("OWNERSHIP_TRANSFER_DENIED", "Only the current owner can transfer to a player without an island"));
    }

    private void removeMember(HttpExchange exchange) throws IOException {
        String body = CoreHttpResponses.readBody(exchange);
        UUID islandId = JsonFields.uuid(body, "islandId", EMPTY_UUID);
        UUID playerUuid = JsonFields.uuid(body, "playerUuid", EMPTY_UUID);
        UUID actorUuid = JsonFields.uuid(body, "actorUuid", EMPTY_UUID);
        if (!requireIslandPermission(exchange, islandId, actorUuid, IslandPermission.MANAGE_MEMBERS)) {
            return;
        }
        IslandMemberSnapshot member = member(metadataRepository.members(islandId), playerUuid);
        if (member != null && member.effectiveRoleKey().equals(IslandRole.OWNER.name())) {
            CoreHttpResponses.write(exchange, 409, ApiResponses.error("OWNER_ROLE_PROTECTED", "Island owner cannot be removed as a member"));
            return;
        }
        metadataRepository.removeMember(islandId, playerUuid);
        audit.log(actorUuid, "PLAYER", "ISLAND_MEMBER_REMOVE", "ISLAND", islandId.toString(), Map.of("playerUuid", playerUuid.toString()));
        islandLogs.append(islandId, actorUuid, "ISLAND_MEMBER_REMOVE", Map.of("playerUuid", playerUuid.toString()));
        events.publish(CloudIslandEventType.ISLAND_MEMBER_LEFT.name(), Map.of("islandId", islandId.toString(), "playerUuid", playerUuid.toString()));
        events.publish(CloudIslandEventType.ISLAND_MEMBER_CHANGED.name(), Map.of("islandId", islandId.toString(), "playerUuid", playerUuid.toString()));
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

    static IslandRole memberRole(List<IslandMemberSnapshot> members, UUID playerUuid) {
        return members.stream()
            .filter(member -> member.playerUuid().equals(playerUuid))
            .filter(member -> member.role() != null)
            .map(IslandMemberSnapshot::role)
            .findFirst()
            .orElse(null);
    }

    private static IslandMemberSnapshot member(List<IslandMemberSnapshot> members, UUID playerUuid) {
        return members.stream()
            .filter(candidate -> candidate.playerUuid().equals(playerUuid))
            .findFirst()
            .orElse(null);
    }

    private long limitValue(UUID islandId, String limitKey, long fallback) {
        return limitRepository.list(islandId).stream()
            .filter(limit -> limit.limitKey().equalsIgnoreCase(limitKey))
            .findFirst()
            .map(kr.lunaf.cloudislands.api.model.IslandLimitSnapshot::value)
            .orElse(fallback);
    }

    static String membersJson(List<IslandMemberSnapshot> members) {
        return membersJson(members, null);
    }

    static String membersJson(List<IslandMemberSnapshot> members, PlayerProfileRepository playerProfiles) {
        List<Object> renderedMembers = new ArrayList<>();
        for (IslandMemberSnapshot member : members) {
            LinkedHashMap<String, Object> rendered = memberMap(member);
            if (playerProfiles != null) {
                rendered.putAll(profileMap(playerProfiles.find(member.playerUuid())));
            }
            renderedMembers.add(rendered);
        }
        return SimpleJson.stringify(Map.of("members", renderedMembers));
    }

    private static Map<String, Object> profileMap(PlayerIslandProfile profile) {
        String lastSeen = profile.lastSeenAt() == null || profile.lastSeenAt().equals(Instant.EPOCH) ? "" : profile.lastSeenAt().toString();
        LinkedHashMap<String, Object> values = new LinkedHashMap<>();
        values.put("playerName", profile.lastName());
        values.put("lastSeenAt", lastSeen);
        values.put("presenceState", lastSeen.isBlank() ? "UNKNOWN" : "RECENT_ACTIVITY");
        values.put("presenceSource", "CORE_PLAYER_PROFILE");
        return values;
    }

    static String islandsJson(List<IslandSnapshot> islands) {
        List<Object> renderedIslands = new ArrayList<>();
        for (IslandSnapshot island : islands) {
            renderedIslands.add(islandMap(island));
        }
        return SimpleJson.stringify(Map.of("islands", renderedIslands));
    }

    static String temporaryTrustJson(UUID islandId, UUID playerUuid, Instant expiresAt, long seconds) {
        LinkedHashMap<String, Object> values = new LinkedHashMap<>();
        values.put("accepted", true);
        values.put("islandId", islandId);
        values.put("playerUuid", playerUuid);
        values.put("role", "TRUSTED");
        values.put("roleKey", "TRUSTED");
        values.put("expiresAt", expiresAt);
        values.put("durationSeconds", seconds);
        return SimpleJson.stringify(values);
    }

    private static LinkedHashMap<String, Object> memberMap(IslandMemberSnapshot member) {
        LinkedHashMap<String, Object> values = new LinkedHashMap<>();
        values.put("islandId", member.islandId());
        values.put("playerUuid", member.playerUuid());
        values.put("role", member.effectiveRoleKey());
        values.put("roleKey", member.effectiveRoleKey());
        values.put("joinedAt", member.joinedAt());
        values.put("expiresAt", member.expiresAt());
        return values;
    }

    private static Map<String, Object> islandMap(IslandSnapshot island) {
        LinkedHashMap<String, Object> values = new LinkedHashMap<>();
        values.put("islandId", island.islandId());
        values.put("ownerUuid", island.ownerUuid());
        values.put("name", island.name());
        values.put("state", island.state());
        values.put("size", island.size());
        values.put("border", island.size());
        values.put("level", island.level());
        values.put("worth", island.worth());
        values.put("publicAccess", island.publicAccess());
        values.put("createdAt", island.createdAt());
        values.put("updatedAt", island.updatedAt());
        return values;
    }

    private static String roleKey(String body, String fallback) {
        String value = JsonFields.text(body, "roleKey", "");
        if (value.isBlank()) {
            value = JsonFields.text(body, "role", fallback);
        }
        return IslandRoleRepository.normalizeRoleKey(value);
    }
}
