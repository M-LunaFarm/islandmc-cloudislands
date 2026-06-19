package kr.lunaf.cloudislands.coreservice.http.routes;

import com.sun.net.httpserver.HttpExchange;
import java.io.IOException;
import java.util.ArrayList;
import java.util.List;
import java.util.Map;
import java.util.UUID;
import kr.lunaf.cloudislands.api.model.IslandMemberSnapshot;
import kr.lunaf.cloudislands.api.model.IslandPermission;
import kr.lunaf.cloudislands.api.model.IslandRole;
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
import kr.lunaf.cloudislands.coreservice.limit.IslandLimitRepository;
import kr.lunaf.cloudislands.coreservice.permission.IslandPermissionRuleRepository;
import kr.lunaf.cloudislands.coreservice.profile.PlayerProfileRepository;
import kr.lunaf.cloudislands.coreservice.repository.IslandMetadataRepository;
import kr.lunaf.cloudislands.coreservice.repository.IslandRepository;

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
        registry.route("/v1/islands/transfer", this::transferOwnership);
        registry.route("/v1/islands/members/remove", this::removeMember);
    }

    private void members(HttpExchange exchange) throws IOException {
        String body = CoreHttpResponses.readBody(exchange);
        CoreHttpResponses.write(exchange, 200, membersJson(metadataRepository.members(JsonFields.uuid(body, "islandId", EMPTY_UUID))));
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
        IslandRole role = JsonFields.enumValue(IslandRole.class, body, "role", IslandRole.MEMBER);
        UUID actorUuid = JsonFields.uuid(body, "actorUuid", EMPTY_UUID);
        if (!requireIslandPermission(exchange, islandId, actorUuid, IslandPermission.MANAGE_ROLES)) {
            return;
        }
        List<IslandMemberSnapshot> members = metadataRepository.members(islandId);
        IslandRole currentRole = memberRole(members, playerUuid);
        if (role == IslandRole.VISITOR || role == IslandRole.BANNED) {
            CoreHttpResponses.write(exchange, 409, ApiResponses.error("MEMBER_ROLE_UNAVAILABLE", "Visitor and banned roles are not managed as island members"));
            return;
        }
        if (role == IslandRole.OWNER || currentRole == IslandRole.OWNER) {
            CoreHttpResponses.write(exchange, 409, ApiResponses.error("OWNER_ROLE_PROTECTED", "Island ownership must be changed through ownership transfer"));
            return;
        }
        boolean existingMember = currentRole != null;
        if (!existingMember && members.size() >= limitValue(islandId, "MEMBERS", 3L)) {
            CoreHttpResponses.write(exchange, 409, ApiResponses.error("MEMBER_LIMIT", "Island member limit was reached"));
            return;
        }
        metadataRepository.upsertMember(islandId, playerUuid, role);
        audit.log(actorUuid, "PLAYER", "ISLAND_MEMBER_SET", "ISLAND", islandId.toString(), Map.of("playerUuid", playerUuid.toString(), "role", role.name()));
        islandLogs.append(islandId, actorUuid, "ISLAND_MEMBER_SET", Map.of("playerUuid", playerUuid.toString(), "role", role.name()));
        events.publish(existingMember ? CloudIslandEventType.ISLAND_MEMBER_ROLE_CHANGED.name() : CloudIslandEventType.ISLAND_MEMBER_JOINED.name(), existingMember
            ? Map.of("islandId", islandId.toString(), "playerUuid", playerUuid.toString(), "oldRole", currentRole.name(), "newRole", role.name())
            : Map.of("islandId", islandId.toString(), "playerUuid", playerUuid.toString(), "role", role.name()));
        events.publish(CloudIslandEventType.ISLAND_MEMBER_CHANGED.name(), Map.of("islandId", islandId.toString(), "playerUuid", playerUuid.toString(), "role", role.name()));
        CoreHttpResponses.write(exchange, 202, ApiResponses.ok(true));
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
        if (memberRole(metadataRepository.members(islandId), playerUuid) == IslandRole.OWNER) {
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

    static IslandRole memberRole(List<IslandMemberSnapshot> members, UUID playerUuid) {
        return members.stream()
            .filter(member -> member.playerUuid().equals(playerUuid))
            .map(IslandMemberSnapshot::role)
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
        StringBuilder builder = new StringBuilder("{\"members\":[");
        boolean first = true;
        for (IslandMemberSnapshot member : members) {
            if (!first) {
                builder.append(',');
            }
            first = false;
            builder.append('{')
                .append("\"islandId\":\"").append(member.islandId()).append("\",")
                .append("\"playerUuid\":\"").append(member.playerUuid()).append("\",")
                .append("\"role\":\"").append(member.role()).append("\",")
                .append("\"joinedAt\":\"").append(member.joinedAt()).append("\"")
                .append('}');
        }
        return builder.append("]}").toString();
    }

    static String islandsJson(List<IslandSnapshot> islands) {
        StringBuilder builder = new StringBuilder("{\"islands\":[");
        boolean first = true;
        for (IslandSnapshot island : islands) {
            if (!first) {
                builder.append(',');
            }
            first = false;
            builder.append(islandJson(island));
        }
        return builder.append("]}").toString();
    }

    private static String islandJson(IslandSnapshot island) {
        return "{\"islandId\":\"" + island.islandId()
            + "\",\"ownerUuid\":\"" + island.ownerUuid()
            + "\",\"name\":\"" + escape(island.name())
            + "\",\"state\":\"" + island.state()
            + "\",\"size\":" + island.size()
            + ",\"border\":" + island.size()
            + ",\"level\":" + island.level()
            + ",\"worth\":\"" + escape(island.worth())
            + "\",\"publicAccess\":" + island.publicAccess()
            + ",\"createdAt\":\"" + island.createdAt()
            + "\",\"updatedAt\":\"" + island.updatedAt()
            + "\"}";
    }

    private static String escape(String value) {
        return value == null ? "" : value.replace("\\", "\\\\").replace("\"", "\\\"");
    }
}
