package kr.lunaf.cloudislands.coreservice.http.routes;

import com.sun.net.httpserver.HttpExchange;
import java.io.IOException;
import java.util.List;
import java.util.Map;
import java.util.Optional;
import java.util.UUID;
import kr.lunaf.cloudislands.api.model.IslandBanSnapshot;
import kr.lunaf.cloudislands.api.model.IslandInviteSnapshot;
import kr.lunaf.cloudislands.api.model.IslandMemberSnapshot;
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
import kr.lunaf.cloudislands.coreservice.permission.IslandPermissionRuleRepository;
import kr.lunaf.cloudislands.coreservice.repository.IslandMetadataRepository;
import kr.lunaf.cloudislands.coreservice.repository.IslandRepository;

public final class IslandVisitorRoutes implements RouteGroup {
    private static final UUID EMPTY_UUID = new UUID(0L, 0L);

    private final IslandRepository islandRepository;
    private final IslandMetadataRepository metadataRepository;
    private final IslandLimitRepository limitRepository;
    private final IslandPermissionRuleRepository permissionRules;
    private final IslandLogRepository islandLogs;
    private final AuditLogger audit;
    private final GlobalEventPublisher events;

    public IslandVisitorRoutes(
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
        registry.route("/v1/islands/invites", this::createInvite);
        registry.route("/v1/players/invites", this::playerInvites);
        registry.route("/v1/islands/invites/accept", this::acceptInvite);
        registry.route("/v1/islands/invites/decline", this::declineInvite);
        registry.route("/v1/islands/bans/set", this::setBan);
        registry.route("/v1/islands/bans", this::bans);
        registry.route("/v1/islands/bans/remove", this::removeBan);
        registry.route("/v1/islands/visitors/kick", this::kickVisitor);
    }

    private void createInvite(HttpExchange exchange) throws IOException {
        String body = CoreHttpResponses.readBody(exchange);
        UUID islandId = JsonFields.uuid(body, "islandId", EMPTY_UUID);
        UUID inviterUuid = JsonFields.uuid(body, "inviterUuid", EMPTY_UUID);
        UUID targetUuid = JsonFields.uuid(body, "targetUuid", EMPTY_UUID);
        if (!requireIslandPermission(exchange, islandId, inviterUuid, IslandPermission.MANAGE_MEMBERS)) {
            return;
        }
        boolean existingMember = metadataRepository.members(islandId).stream().anyMatch(member -> member.playerUuid().equals(targetUuid));
        if (existingMember) {
            CoreHttpResponses.write(exchange, 409, ApiResponses.error("ALREADY_MEMBER", "Player is already an island member"));
            return;
        }
        if (metadataRepository.members(islandId).size() >= limitValue(islandId, "MEMBERS", 3L)) {
            CoreHttpResponses.write(exchange, 409, ApiResponses.error("MEMBER_LIMIT", "Island member limit was reached"));
            return;
        }
        var invite = metadataRepository.createInvite(islandId, inviterUuid, targetUuid);
        audit.log(inviterUuid, "PLAYER", "ISLAND_INVITE_CREATE", "ISLAND", islandId.toString(), Map.of("targetUuid", targetUuid.toString(), "inviteId", invite.inviteId().toString()));
        islandLogs.append(islandId, inviterUuid, "ISLAND_INVITE_CREATE", Map.of("targetUuid", targetUuid.toString(), "inviteId", invite.inviteId().toString()));
        events.publish(CloudIslandEventType.ISLAND_INVITE_CHANGED.name(), Map.of("islandId", islandId.toString(), "targetUuid", targetUuid.toString(), "inviteId", invite.inviteId().toString(), "state", invite.state()));
        CoreHttpResponses.write(exchange, 202, inviteAcceptedJson(invite));
    }

    private void playerInvites(HttpExchange exchange) throws IOException {
        String body = CoreHttpResponses.readBody(exchange);
        CoreHttpResponses.write(exchange, 200, invitesJson(metadataRepository.pendingInvites(JsonFields.uuid(body, "playerUuid", EMPTY_UUID))));
    }

    private void acceptInvite(HttpExchange exchange) throws IOException {
        String body = CoreHttpResponses.readBody(exchange);
        UUID inviteId = JsonFields.uuid(body, "inviteId", EMPTY_UUID);
        UUID playerUuid = JsonFields.uuid(body, "playerUuid", EMPTY_UUID);
        Optional<IslandInviteSnapshot> invite = metadataRepository.pendingInvites(playerUuid).stream().filter(value -> value.inviteId().equals(inviteId)).findFirst();
        String islandId = invite.map(value -> value.islandId().toString()).orElse("");
        if (invite.isPresent()) {
            UUID inviteIslandId = invite.get().islandId();
            boolean existingMember = metadataRepository.members(inviteIslandId).stream().anyMatch(member -> member.playerUuid().equals(playerUuid));
            if (!existingMember && metadataRepository.members(inviteIslandId).size() >= limitValue(inviteIslandId, "MEMBERS", 3L)) {
                CoreHttpResponses.write(exchange, 409, ApiResponses.error("MEMBER_LIMIT", "Island member limit was reached"));
                return;
            }
        }
        boolean accepted = metadataRepository.acceptInvite(inviteId, playerUuid);
        audit.log(playerUuid, "PLAYER", "ISLAND_INVITE_ACCEPT", "INVITE", inviteId.toString(), Map.of("accepted", Boolean.toString(accepted)));
        events.publish(CloudIslandEventType.ISLAND_INVITE_CHANGED.name(), Map.of("inviteId", inviteId.toString(), "islandId", islandId, "playerUuid", playerUuid.toString(), "accepted", Boolean.toString(accepted)));
        if (accepted) {
            invite.ifPresent(value -> islandLogs.append(value.islandId(), playerUuid, "ISLAND_INVITE_ACCEPT", Map.of("inviteId", inviteId.toString(), "accepted", "true")));
            events.publish(CloudIslandEventType.ISLAND_MEMBER_JOINED.name(), Map.of("inviteId", inviteId.toString(), "islandId", islandId, "playerUuid", playerUuid.toString(), "role", IslandRole.MEMBER.name()));
            events.publish(CloudIslandEventType.ISLAND_MEMBER_CHANGED.name(), Map.of("inviteId", inviteId.toString(), "islandId", islandId, "playerUuid", playerUuid.toString()));
        }
        CoreHttpResponses.write(exchange, accepted ? 202 : 409, accepted ? ApiResponses.ok(true) : ApiResponses.error("INVITE_UNAVAILABLE", "Invite is missing, expired, or not pending"));
    }

    private void declineInvite(HttpExchange exchange) throws IOException {
        String body = CoreHttpResponses.readBody(exchange);
        UUID inviteId = JsonFields.uuid(body, "inviteId", EMPTY_UUID);
        UUID playerUuid = JsonFields.uuid(body, "playerUuid", EMPTY_UUID);
        Optional<IslandInviteSnapshot> invite = metadataRepository.pendingInvites(playerUuid).stream().filter(value -> value.inviteId().equals(inviteId)).findFirst();
        String islandId = invite.map(value -> value.islandId().toString()).orElse("");
        boolean declined = metadataRepository.declineInvite(inviteId, playerUuid);
        audit.log(playerUuid, "PLAYER", "ISLAND_INVITE_DECLINE", "INVITE", inviteId.toString(), Map.of("declined", Boolean.toString(declined)));
        if (declined) {
            invite.ifPresent(value -> islandLogs.append(value.islandId(), playerUuid, "ISLAND_INVITE_DECLINE", Map.of("inviteId", inviteId.toString(), "declined", "true")));
        }
        events.publish(CloudIslandEventType.ISLAND_INVITE_CHANGED.name(), Map.of("inviteId", inviteId.toString(), "islandId", islandId, "playerUuid", playerUuid.toString(), "declined", Boolean.toString(declined)));
        CoreHttpResponses.write(exchange, declined ? 202 : 409, declined ? ApiResponses.ok(true) : ApiResponses.error("INVITE_UNAVAILABLE", "Invite is missing or not pending"));
    }

    private void setBan(HttpExchange exchange) throws IOException {
        String body = CoreHttpResponses.readBody(exchange);
        UUID islandId = JsonFields.uuid(body, "islandId", EMPTY_UUID);
        UUID actorUuid = JsonFields.uuid(body, "actorUuid", EMPTY_UUID);
        UUID playerUuid = JsonFields.uuid(body, "playerUuid", EMPTY_UUID);
        String reason = JsonFields.text(body, "reason", "island ban");
        if (!requireIslandPermission(exchange, islandId, actorUuid, IslandPermission.BAN_VISITOR)) {
            return;
        }
        IslandRole targetRole = memberRole(metadataRepository.members(islandId), playerUuid);
        if (targetRole != null && targetRole != IslandRole.VISITOR && targetRole != IslandRole.BANNED) {
            CoreHttpResponses.write(exchange, 409, ApiResponses.error("VISITOR_BAN_DENIED", "Island members cannot be handled through visitor bans"));
            return;
        }
        metadataRepository.banVisitor(islandId, actorUuid, playerUuid, reason);
        metadataRepository.removeMember(islandId, playerUuid);
        audit.log(actorUuid, "PLAYER", "ISLAND_VISITOR_BAN", "ISLAND", islandId.toString(), Map.of("playerUuid", playerUuid.toString(), "reason", reason));
        islandLogs.append(islandId, actorUuid, "ISLAND_VISITOR_BAN", Map.of("playerUuid", playerUuid.toString(), "reason", reason));
        events.publish(CloudIslandEventType.ISLAND_VISITOR_BAN_CHANGED.name(), Map.of("islandId", islandId.toString(), "playerUuid", playerUuid.toString(), "banned", Boolean.toString(true)));
        CoreHttpResponses.write(exchange, 202, ApiResponses.ok(true));
    }

    private void bans(HttpExchange exchange) throws IOException {
        String body = CoreHttpResponses.readBody(exchange);
        CoreHttpResponses.write(exchange, 200, bansJson(metadataRepository.bans(JsonFields.uuid(body, "islandId", EMPTY_UUID))));
    }

    private void removeBan(HttpExchange exchange) throws IOException {
        String body = CoreHttpResponses.readBody(exchange);
        UUID islandId = JsonFields.uuid(body, "islandId", EMPTY_UUID);
        UUID playerUuid = JsonFields.uuid(body, "playerUuid", EMPTY_UUID);
        UUID actorUuid = JsonFields.uuid(body, "actorUuid", EMPTY_UUID);
        if (!requireIslandPermission(exchange, islandId, actorUuid, IslandPermission.BAN_VISITOR)) {
            return;
        }
        metadataRepository.pardonVisitor(islandId, playerUuid);
        audit.log(actorUuid, "PLAYER", "ISLAND_VISITOR_PARDON", "ISLAND", islandId.toString(), Map.of("playerUuid", playerUuid.toString()));
        islandLogs.append(islandId, actorUuid, "ISLAND_VISITOR_PARDON", Map.of("playerUuid", playerUuid.toString()));
        events.publish(CloudIslandEventType.ISLAND_VISITOR_BAN_CHANGED.name(), Map.of("islandId", islandId.toString(), "playerUuid", playerUuid.toString(), "banned", Boolean.toString(false)));
        CoreHttpResponses.write(exchange, 202, ApiResponses.ok(true));
    }

    private void kickVisitor(HttpExchange exchange) throws IOException {
        String body = CoreHttpResponses.readBody(exchange);
        UUID islandId = JsonFields.uuid(body, "islandId", EMPTY_UUID);
        UUID actorUuid = JsonFields.uuid(body, "actorUuid", EMPTY_UUID);
        UUID playerUuid = JsonFields.uuid(body, "playerUuid", EMPTY_UUID);
        if (!requireIslandPermission(exchange, islandId, actorUuid, IslandPermission.KICK_VISITOR)) {
            return;
        }
        audit.log(actorUuid, "PLAYER", "ISLAND_VISITOR_KICK", "ISLAND", islandId.toString(), Map.of("playerUuid", playerUuid.toString()));
        islandLogs.append(islandId, actorUuid, "ISLAND_VISITOR_KICK", Map.of("playerUuid", playerUuid.toString()));
        events.publish(CloudIslandEventType.ISLAND_VISITOR_KICKED.name(), Map.of("islandId", islandId.toString(), "playerUuid", playerUuid.toString(), "actorUuid", actorUuid.toString()));
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

    static String inviteAcceptedJson(IslandInviteSnapshot invite) {
        return "{\"accepted\":true,\"inviteId\":\"" + invite.inviteId() + "\",\"islandId\":\"" + invite.islandId() + "\",\"inviterUuid\":\"" + invite.inviterUuid() + "\",\"targetUuid\":\"" + invite.targetUuid() + "\",\"state\":\"" + invite.state() + "\",\"createdAt\":\"" + invite.createdAt() + "\",\"expiresAt\":\"" + invite.expiresAt() + "\"}";
    }

    static String invitesJson(List<IslandInviteSnapshot> invites) {
        StringBuilder builder = new StringBuilder("{\"invites\":[");
        boolean first = true;
        for (IslandInviteSnapshot invite : invites) {
            if (!first) {
                builder.append(',');
            }
            first = false;
            builder.append('{')
                .append("\"inviteId\":\"").append(invite.inviteId()).append("\",")
                .append("\"islandId\":\"").append(invite.islandId()).append("\",")
                .append("\"inviterUuid\":\"").append(invite.inviterUuid()).append("\",")
                .append("\"targetUuid\":\"").append(invite.targetUuid()).append("\",")
                .append("\"state\":\"").append(invite.state()).append("\",")
                .append("\"createdAt\":\"").append(invite.createdAt()).append("\",")
                .append("\"expiresAt\":\"").append(invite.expiresAt()).append("\"")
                .append('}');
        }
        return builder.append("]}").toString();
    }

    static String bansJson(List<IslandBanSnapshot> bans) {
        StringBuilder builder = new StringBuilder("{\"bans\":[");
        boolean first = true;
        for (IslandBanSnapshot ban : bans) {
            if (!first) {
                builder.append(',');
            }
            first = false;
            builder.append('{')
                .append("\"islandId\":\"").append(ban.islandId()).append("\",")
                .append("\"bannedUuid\":\"").append(ban.bannedUuid()).append("\",")
                .append("\"actorUuid\":\"").append(ban.actorUuid()).append("\",")
                .append("\"reason\":\"").append(escape(ban.reason())).append("\",")
                .append("\"createdAt\":\"").append(ban.createdAt()).append("\",")
                .append("\"expiresAt\":").append(ban.expiresAt() == null ? "null" : "\"" + ban.expiresAt() + "\"")
                .append('}');
        }
        return builder.append("]}").toString();
    }

    private static String escape(String value) {
        return value == null ? "" : value.replace("\\", "\\\\").replace("\"", "\\\"");
    }
}
