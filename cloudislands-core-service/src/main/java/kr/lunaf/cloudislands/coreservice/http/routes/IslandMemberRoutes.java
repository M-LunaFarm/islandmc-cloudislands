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
import kr.lunaf.cloudislands.api.model.IslandSnapshot;
import kr.lunaf.cloudislands.api.model.PlayerIslandProfile;
import kr.lunaf.cloudislands.common.event.CloudIslandEventType;
import kr.lunaf.cloudislands.common.feature.GameplayParityPolicy;
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
import kr.lunaf.cloudislands.coreservice.role.CoreRoleKeys;
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
        registry.routePost("/v1/islands/members", this::members);
        registry.routePost("/v1/players/islands", this::playerIslands);
        registry.routePost("/v1/islands/members/set", this::setMember);
        registry.routePost("/v1/islands/members/trust-temporary", this::trustTemporary);
        registry.routePost("/v1/islands/transfer", this::transferOwnership);
        registry.routePost("/v1/islands/members/remove", this::removeMember);
        registry.routePost("/v1/admin/islands/members/add", this::adminAddMember);
        registry.routePost("/v1/admin/islands/members/kick", this::adminKickMember);
        registry.routePost("/v1/admin/islands/members/promote", this::adminPromoteMember);
        registry.routePost("/v1/admin/islands/members/demote", this::adminDemoteMember);
        registry.routePost("/v1/admin/islands/members/setleader", this::adminSetLeader);
    }

    private void members(HttpExchange exchange) throws IOException {
        String body = CoreHttpResponses.readBody(exchange);
        CoreHttpResponses.write(exchange, 200, membersJson(metadataRepository.members(JsonFields.uuid(body, "islandId", EMPTY_UUID)), playerProfiles));
    }

    private void playerIslands(HttpExchange exchange) throws IOException {
        String body = CoreHttpResponses.readBody(exchange);
        UUID playerUuid = JsonFields.uuid(body, "playerUuid", EMPTY_UUID);
        List<IslandMemberSnapshot> memberships = new ArrayList<>(metadataRepository.islandsForMember(playerUuid).stream()
            .filter(member -> CoreRoleKeys.memberRole(member.effectiveRoleKey()))
            .toList());
        islandRepository.findByOwner(playerUuid).ifPresent(island -> {
            boolean present = memberships.stream().anyMatch(member -> member.islandId().equals(island.islandId()));
            if (!present) {
                memberships.add(0, new IslandMemberSnapshot(island.islandId(), playerUuid, CoreRoleKeys.OWNER, island.createdAt(), null));
            }
        });
        CoreHttpResponses.write(exchange, 200, playerIslandsJson(memberships));
    }

    private void setMember(HttpExchange exchange) throws IOException {
        String body = CoreHttpResponses.readBody(exchange);
        UUID islandId = JsonFields.uuid(body, "islandId", EMPTY_UUID);
        UUID playerUuid = JsonFields.uuid(body, "playerUuid", EMPTY_UUID);
        String roleKey = roleKey(body, CoreRoleKeys.MEMBER);
        UUID actorUuid = JsonFields.uuid(body, "actorUuid", EMPTY_UUID);
        if (!requireIslandPermission(exchange, islandId, actorUuid, IslandPermission.MANAGE_ROLES)) {
            return;
        }
        List<IslandMemberSnapshot> members = metadataRepository.members(islandId);
        IslandMemberSnapshot currentMember = member(members, playerUuid);
        String currentRoleKey = currentMember == null ? "" : currentMember.effectiveRoleKey();
        if (roleKey.equals(CoreRoleKeys.OWNER) || currentRoleKey.equals(CoreRoleKeys.OWNER) || isOwner(islandId, playerUuid)) {
            CoreHttpResponses.write(exchange, 409, ApiResponses.error("OWNER_ROLE_PROTECTED", "Island ownership must be changed through ownership transfer"));
            return;
        }
        if (!IslandRoleRepository.editableRoleKey(roleKey)) {
            CoreHttpResponses.write(exchange, 409, ApiResponses.error("MEMBER_ROLE_UNAVAILABLE", "Visitor and banned roles are not managed as island members"));
            return;
        }
        boolean existingMember = currentMember != null;
        if (roleKey.equals(CoreRoleKeys.TRUSTED) && existingMember && !currentRoleKey.equals(CoreRoleKeys.TRUSTED)) {
            CoreHttpResponses.write(exchange, 409, ApiResponses.error("ALREADY_ISLAND_MEMBER", "Island members cannot also be co-op players"));
            return;
        }
        if (addsTeamMember(currentRoleKey, roleKey) && teamMemberCount(members) >= limitValue(islandId, "MEMBERS", 3L)) {
            CoreHttpResponses.write(exchange, 409, ApiResponses.error("MEMBER_LIMIT", "Island member limit was reached"));
            return;
        }
        if (roleLimitReached(islandId, members, currentRoleKey, roleKey)) {
            CoreHttpResponses.write(exchange, 409, ApiResponses.error("ROLE_LIMIT", "Island role limit was reached"));
            return;
        }
        if (addsTeamMember(currentRoleKey, roleKey)) {
            String applied = metadataRepository.upsertMemberKeyAndInitializePrimary(
                islandId, playerUuid, roleKey,
                limitValue(islandId, "MEMBERS", 3L),
                limitValue(islandId, GameplayParityPolicy.roleLimitKey(roleKey), defaultRoleLimit(roleKey))
            );
            if (!"APPLIED".equals(applied)) {
                CoreHttpResponses.write(exchange, 409, ApiResponses.error(applied, applied.equals("ROLE_LIMIT") ? "Island role limit was reached" : "Island member limit was reached"));
                return;
            }
            initializePrimaryIslandIfEmpty(islandId, playerUuid);
        } else {
            String applied = metadataRepository.upsertMemberKeyWithRoleLimit(
                islandId, playerUuid, roleKey, null,
                limitValue(islandId, GameplayParityPolicy.roleLimitKey(roleKey), defaultRoleLimit(roleKey))
            );
            if (!"APPLIED".equals(applied)) {
                CoreHttpResponses.write(exchange, 409, ApiResponses.error(applied, applied.equals("ROLE_LIMIT") ? "Island role limit was reached" : "Island was not found"));
                return;
            }
        }
        audit.log(actorUuid, "PLAYER", "ISLAND_MEMBER_SET", "ISLAND", islandId.toString(), Map.of("playerUuid", playerUuid.toString(), "role", roleKey, "roleKey", roleKey));
        islandLogs.append(islandId, actorUuid, "ISLAND_MEMBER_SET", Map.of("playerUuid", playerUuid.toString(), "role", roleKey, "roleKey", roleKey));
        events.publish(existingMember ? CloudIslandEventType.ISLAND_MEMBER_ROLE_CHANGED.name() : CloudIslandEventType.ISLAND_MEMBER_JOINED.name(), existingMember
            ? Map.of("islandId", islandId.toString(), "playerUuid", playerUuid.toString(), "oldRole", currentRoleKey, "oldRoleKey", currentRoleKey, "newRole", roleKey, "newRoleKey", roleKey)
            : Map.of("islandId", islandId.toString(), "playerUuid", playerUuid.toString(), "role", roleKey, "roleKey", roleKey));
        publishCoopTransition(islandId, playerUuid, currentRoleKey, roleKey);
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
        if (currentRoleKey.equals(CoreRoleKeys.OWNER) || isOwner(islandId, playerUuid)) {
            CoreHttpResponses.write(exchange, 409, ApiResponses.error("OWNER_ROLE_PROTECTED", "Island owner cannot be temporary trusted"));
            return;
        }
        boolean existingMember = currentMember != null;
        boolean renewingTemporaryTrust = existingMember
            && currentRoleKey.equals(CoreRoleKeys.TRUSTED)
            && currentMember.expiresAt() != null;
        if (existingMember && !renewingTemporaryTrust) {
            CoreHttpResponses.write(exchange, 409, ApiResponses.error("ALREADY_ISLAND_MEMBER", "Permanent island members cannot be converted to temporary trusted members"));
            return;
        }
        if (!renewingTemporaryTrust && roleLimitReached(islandId, members, currentRoleKey, CoreRoleKeys.TRUSTED)) {
            CoreHttpResponses.write(exchange, 409, ApiResponses.error("ROLE_LIMIT", "Island role limit was reached"));
            return;
        }
        Instant expiresAt = Instant.now().plusSeconds(seconds);
        String applied = metadataRepository.upsertMemberKeyWithRoleLimit(
            islandId, playerUuid, CoreRoleKeys.TRUSTED, expiresAt,
            limitValue(islandId, GameplayParityPolicy.roleLimitKey(CoreRoleKeys.TRUSTED), defaultRoleLimit(CoreRoleKeys.TRUSTED))
        );
        if (!"APPLIED".equals(applied)) {
            CoreHttpResponses.write(exchange, 409, ApiResponses.error(applied, applied.equals("ROLE_LIMIT") ? "Island role limit was reached" : "Island was not found"));
            return;
        }
        Map<String, String> fields = Map.of(
            "playerUuid", playerUuid.toString(),
            "role", CoreRoleKeys.TRUSTED,
            "expiresAt", expiresAt.toString(),
            "durationSeconds", Long.toString(seconds)
        );
        audit.log(actorUuid, "PLAYER", "ISLAND_MEMBER_TEMP_TRUST", "ISLAND", islandId.toString(), fields);
        islandLogs.append(islandId, actorUuid, "ISLAND_MEMBER_TEMP_TRUST", fields);
        events.publish(CloudIslandEventType.ISLAND_MEMBER_ROLE_CHANGED.name(), Map.of(
            "islandId", islandId.toString(),
            "playerUuid", playerUuid.toString(),
            "oldRole", currentMember == null ? CoreRoleKeys.VISITOR : currentRoleKey,
            "oldRoleKey", currentMember == null ? CoreRoleKeys.VISITOR : currentRoleKey,
            "newRole", CoreRoleKeys.TRUSTED,
            "newRoleKey", CoreRoleKeys.TRUSTED,
            "expiresAt", expiresAt.toString()
        ));
        publishCoopTransition(islandId, playerUuid, currentRoleKey, CoreRoleKeys.TRUSTED);
        events.publish(CloudIslandEventType.ISLAND_MEMBER_CHANGED.name(), Map.of("islandId", islandId.toString(), "playerUuid", playerUuid.toString(), "role", CoreRoleKeys.TRUSTED, "roleKey", CoreRoleKeys.TRUSTED, "expiresAt", expiresAt.toString()));
        CoreHttpResponses.write(exchange, 202, temporaryTrustJson(islandId, playerUuid, expiresAt, seconds));
    }

    private void transferOwnership(HttpExchange exchange) throws IOException {
        String body = CoreHttpResponses.readBody(exchange);
        UUID islandId = JsonFields.uuid(body, "islandId", EMPTY_UUID);
        UUID actorUuid = JsonFields.uuid(body, "actorUuid", EMPTY_UUID);
        UUID targetUuid = JsonFields.uuid(body, "targetUuid", EMPTY_UUID);
        boolean transferred = islandRepository.transferOwnership(islandId, actorUuid, targetUuid);
        if (transferred) {
            metadataRepository.upsertMemberKey(islandId, actorUuid, CoreRoleKeys.CO_OWNER);
            metadataRepository.upsertMemberKey(islandId, targetUuid, CoreRoleKeys.OWNER);
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
        IslandMemberSnapshot member = member(metadataRepository.members(islandId), playerUuid);
        if (isOwner(islandId, playerUuid) || member != null && member.effectiveRoleKey().equals(CoreRoleKeys.OWNER)) {
            CoreHttpResponses.write(exchange, 409, ApiResponses.error("OWNER_ROLE_PROTECTED", "Island owner cannot be removed as a member"));
            return;
        }
        if (!actorUuid.equals(playerUuid) && !requireIslandPermission(exchange, islandId, actorUuid, IslandPermission.MANAGE_MEMBERS)) {
            return;
        }
        metadataRepository.removeMemberAndClearPrimary(islandId, playerUuid);
        clearPrimaryIslandIfSelected(islandId, playerUuid);
        audit.log(actorUuid, "PLAYER", "ISLAND_MEMBER_REMOVE", "ISLAND", islandId.toString(), Map.of("playerUuid", playerUuid.toString()));
        islandLogs.append(islandId, actorUuid, "ISLAND_MEMBER_REMOVE", Map.of("playerUuid", playerUuid.toString()));
        publishCoopTransition(islandId, playerUuid, member == null ? "" : member.effectiveRoleKey(), "");
        events.publish(CloudIslandEventType.ISLAND_MEMBER_LEFT.name(), Map.of("islandId", islandId.toString(), "playerUuid", playerUuid.toString()));
        events.publish(CloudIslandEventType.ISLAND_MEMBER_CHANGED.name(), Map.of("islandId", islandId.toString(), "playerUuid", playerUuid.toString()));
        CoreHttpResponses.write(exchange, 202, ApiResponses.ok(true));
    }

    private void adminAddMember(HttpExchange exchange) throws IOException {
        String body = CoreHttpResponses.readBody(exchange);
        UUID islandId = JsonFields.uuid(body, "islandId", EMPTY_UUID);
        UUID playerUuid = JsonFields.uuid(body, "playerUuid", EMPTY_UUID);
        String roleKey = roleKey(body, CoreRoleKeys.MEMBER);
        if (islandRepository.findById(islandId).isEmpty()) {
            CoreHttpResponses.write(exchange, 404, ApiResponses.error("ISLAND_NOT_FOUND", "Island was not found"));
            return;
        }
        if (roleKey.equals(CoreRoleKeys.OWNER) || !IslandRoleRepository.editableRoleKey(roleKey)) {
            CoreHttpResponses.write(exchange, 409, ApiResponses.error("MEMBER_ROLE_UNAVAILABLE", "Admin member add requires an editable island role"));
            return;
        }
        List<IslandMemberSnapshot> members = metadataRepository.members(islandId);
        IslandMemberSnapshot current = member(members, playerUuid);
        String oldRoleKey = current == null ? "" : current.effectiveRoleKey();
        if (oldRoleKey.equals(CoreRoleKeys.OWNER) || isOwner(islandId, playerUuid)) {
            CoreHttpResponses.write(exchange, 409, ApiResponses.error("OWNER_ROLE_PROTECTED", "Island owner role is protected"));
            return;
        }
        if (addsTeamMember(oldRoleKey, roleKey) && teamMemberCount(members) >= limitValue(islandId, "MEMBERS", 3L)) {
            CoreHttpResponses.write(exchange, 409, ApiResponses.error("MEMBER_LIMIT", "Island member limit was reached"));
            return;
        }
        if (roleLimitReached(islandId, members, oldRoleKey, roleKey)) {
            CoreHttpResponses.write(exchange, 409, ApiResponses.error("ROLE_LIMIT", "Island role limit was reached"));
            return;
        }
        if (addsTeamMember(oldRoleKey, roleKey)) {
            String applied = metadataRepository.upsertMemberKeyAndInitializePrimary(
                islandId, playerUuid, roleKey,
                limitValue(islandId, "MEMBERS", 3L),
                limitValue(islandId, GameplayParityPolicy.roleLimitKey(roleKey), defaultRoleLimit(roleKey))
            );
            if (!"APPLIED".equals(applied)) {
                CoreHttpResponses.write(exchange, 409, ApiResponses.error(applied, applied.equals("ROLE_LIMIT") ? "Island role limit was reached" : "Island member limit was reached"));
                return;
            }
            initializePrimaryIslandIfEmpty(islandId, playerUuid);
        } else {
            String applied = metadataRepository.upsertMemberKeyWithRoleLimit(
                islandId, playerUuid, roleKey, null,
                limitValue(islandId, GameplayParityPolicy.roleLimitKey(roleKey), defaultRoleLimit(roleKey))
            );
            if (!"APPLIED".equals(applied)) {
                CoreHttpResponses.write(exchange, 409, ApiResponses.error(applied, applied.equals("ROLE_LIMIT") ? "Island role limit was reached" : "Island was not found"));
                return;
            }
        }
        adminMemberAudit(islandId, playerUuid, "ISLAND_MEMBER_ADMIN_ADD", Map.of("oldRoleKey", oldRoleKey, "newRoleKey", roleKey));
        publishAdminMemberSet(islandId, playerUuid, oldRoleKey, roleKey, current != null);
        publishCoopTransition(islandId, playerUuid, oldRoleKey, roleKey);
        CoreHttpResponses.write(exchange, 202, memberActionJson("MEMBER_ADDED"));
    }

    private void adminKickMember(HttpExchange exchange) throws IOException {
        String body = CoreHttpResponses.readBody(exchange);
        UUID islandId = JsonFields.uuid(body, "islandId", EMPTY_UUID);
        UUID playerUuid = JsonFields.uuid(body, "playerUuid", EMPTY_UUID);
        if (islandRepository.findById(islandId).isEmpty()) {
            CoreHttpResponses.write(exchange, 404, ApiResponses.error("ISLAND_NOT_FOUND", "Island was not found"));
            return;
        }
        IslandMemberSnapshot current = member(metadataRepository.members(islandId), playerUuid);
        if (isOwner(islandId, playerUuid) || current != null && current.effectiveRoleKey().equals(CoreRoleKeys.OWNER)) {
            CoreHttpResponses.write(exchange, 409, ApiResponses.error("OWNER_ROLE_PROTECTED", "Island owner cannot be removed as a member"));
            return;
        }
        metadataRepository.removeMemberAndClearPrimary(islandId, playerUuid);
        clearPrimaryIslandIfSelected(islandId, playerUuid);
        adminMemberAudit(islandId, playerUuid, "ISLAND_MEMBER_ADMIN_KICK", Map.of("oldRoleKey", current == null ? "" : current.effectiveRoleKey()));
        publishCoopTransition(islandId, playerUuid, current == null ? "" : current.effectiveRoleKey(), "");
        events.publish(CloudIslandEventType.ISLAND_MEMBER_LEFT.name(), Map.of("islandId", islandId.toString(), "playerUuid", playerUuid.toString()));
        events.publish(CloudIslandEventType.ISLAND_MEMBER_CHANGED.name(), Map.of("islandId", islandId.toString(), "playerUuid", playerUuid.toString()));
        CoreHttpResponses.write(exchange, 202, memberActionJson("MEMBER_KICKED"));
    }

    private void adminPromoteMember(HttpExchange exchange) throws IOException {
        adminShiftMemberRole(exchange, "promote");
    }

    private void adminDemoteMember(HttpExchange exchange) throws IOException {
        adminShiftMemberRole(exchange, "demote");
    }

    private void adminShiftMemberRole(HttpExchange exchange, String direction) throws IOException {
        String body = CoreHttpResponses.readBody(exchange);
        UUID islandId = JsonFields.uuid(body, "islandId", EMPTY_UUID);
        UUID playerUuid = JsonFields.uuid(body, "playerUuid", EMPTY_UUID);
        if (islandRepository.findById(islandId).isEmpty()) {
            CoreHttpResponses.write(exchange, 404, ApiResponses.error("ISLAND_NOT_FOUND", "Island was not found"));
            return;
        }
        List<IslandMemberSnapshot> members = metadataRepository.members(islandId);
        IslandMemberSnapshot current = member(members, playerUuid);
        if (current == null) {
            CoreHttpResponses.write(exchange, 404, ApiResponses.error("MEMBER_NOT_FOUND", "Island member was not found"));
            return;
        }
        String oldRoleKey = current.effectiveRoleKey();
        if (oldRoleKey.equals(CoreRoleKeys.OWNER) || isOwner(islandId, playerUuid)) {
            CoreHttpResponses.write(exchange, 409, ApiResponses.error("OWNER_ROLE_PROTECTED", "Island owner role is protected"));
            return;
        }
        String newRoleKey = direction.equals("promote") ? promotedRole(oldRoleKey) : demotedRole(oldRoleKey);
        if (newRoleKey.isBlank()) {
            CoreHttpResponses.write(exchange, 409, ApiResponses.error("MEMBER_ROLE_UNAVAILABLE", "No legacy admin role step is available"));
            return;
        }
        if (addsTeamMember(oldRoleKey, newRoleKey) && teamMemberCount(members) >= limitValue(islandId, "MEMBERS", 3L)) {
            CoreHttpResponses.write(exchange, 409, ApiResponses.error("MEMBER_LIMIT", "Island member limit was reached"));
            return;
        }
        if (roleLimitReached(islandId, members, oldRoleKey, newRoleKey)) {
            CoreHttpResponses.write(exchange, 409, ApiResponses.error("ROLE_LIMIT", "Island role limit was reached"));
            return;
        }
        String applied;
        if (addsTeamMember(oldRoleKey, newRoleKey)) {
            applied = metadataRepository.upsertMemberKeyAndInitializePrimary(
                islandId, playerUuid, newRoleKey,
                limitValue(islandId, "MEMBERS", 3L),
                limitValue(islandId, GameplayParityPolicy.roleLimitKey(newRoleKey), defaultRoleLimit(newRoleKey))
            );
        } else {
            applied = metadataRepository.upsertMemberKeyWithRoleLimit(
                islandId, playerUuid, newRoleKey, null,
                limitValue(islandId, GameplayParityPolicy.roleLimitKey(newRoleKey), defaultRoleLimit(newRoleKey))
            );
        }
        if (!"APPLIED".equals(applied)) {
            CoreHttpResponses.write(exchange, 409, ApiResponses.error(applied, applied.equals("ROLE_LIMIT") ? "Island role limit was reached" : applied.equals("MEMBER_LIMIT") ? "Island member limit was reached" : "Island was not found"));
            return;
        }
        adminMemberAudit(islandId, playerUuid, direction.equals("promote") ? "ISLAND_MEMBER_ADMIN_PROMOTE" : "ISLAND_MEMBER_ADMIN_DEMOTE", Map.of("oldRoleKey", oldRoleKey, "newRoleKey", newRoleKey));
        publishAdminMemberSet(islandId, playerUuid, oldRoleKey, newRoleKey, true);
        publishCoopTransition(islandId, playerUuid, oldRoleKey, newRoleKey);
        CoreHttpResponses.write(exchange, 202, memberActionJson(direction.equals("promote") ? "MEMBER_PROMOTED" : "MEMBER_DEMOTED"));
    }

    private void adminSetLeader(HttpExchange exchange) throws IOException {
        String body = CoreHttpResponses.readBody(exchange);
        UUID islandId = JsonFields.uuid(body, "islandId", EMPTY_UUID);
        UUID targetUuid = JsonFields.uuid(body, "playerUuid", JsonFields.uuid(body, "targetUuid", EMPTY_UUID));
        IslandSnapshot island = islandRepository.findById(islandId).orElse(null);
        if (island == null) {
            CoreHttpResponses.write(exchange, 404, ApiResponses.error("ISLAND_NOT_FOUND", "Island was not found"));
            return;
        }
        UUID oldOwner = island.ownerUuid();
        boolean transferred = islandRepository.transferOwnership(islandId, oldOwner, targetUuid);
        if (transferred) {
            metadataRepository.upsertMemberKey(islandId, oldOwner, CoreRoleKeys.CO_OWNER);
            metadataRepository.upsertMemberKey(islandId, targetUuid, CoreRoleKeys.OWNER);
            playerProfiles.setPrimaryIsland(targetUuid, islandId);
        }
        adminMemberAudit(islandId, targetUuid, "ISLAND_MEMBER_ADMIN_SETLEADER", Map.of("oldOwnerUuid", oldOwner.toString(), "transferred", Boolean.toString(transferred)));
        if (transferred) {
            events.publish(CloudIslandEventType.ISLAND_OWNERSHIP_CHANGED.name(), Map.of("islandId", islandId.toString(), "actorUuid", EMPTY_UUID.toString(), "targetUuid", targetUuid.toString()));
            events.publish(CloudIslandEventType.ISLAND_MEMBER_CHANGED.name(), Map.of("islandId", islandId.toString(), "actorUuid", EMPTY_UUID.toString(), "targetUuid", targetUuid.toString()));
        }
        CoreHttpResponses.write(exchange, transferred ? 202 : 409, transferred ? memberActionJson("LEADER_SET") : ApiResponses.error("OWNERSHIP_TRANSFER_DENIED", "Target player already owns an island or transfer was denied"));
    }

    private void adminMemberAudit(UUID islandId, UUID playerUuid, String action, Map<String, String> payload) {
        LinkedHashMap<String, String> fields = new LinkedHashMap<>();
        fields.put("playerUuid", playerUuid.toString());
        fields.putAll(payload);
        audit.log(EMPTY_UUID, "ADMIN", action, "ISLAND", islandId.toString(), fields);
        islandLogs.append(islandId, EMPTY_UUID, action, fields);
    }

    private void publishAdminMemberSet(UUID islandId, UUID playerUuid, String oldRoleKey, String newRoleKey, boolean existingMember) {
        events.publish(existingMember ? CloudIslandEventType.ISLAND_MEMBER_ROLE_CHANGED.name() : CloudIslandEventType.ISLAND_MEMBER_JOINED.name(), existingMember
            ? Map.of("islandId", islandId.toString(), "playerUuid", playerUuid.toString(), "oldRole", oldRoleKey, "oldRoleKey", oldRoleKey, "newRole", newRoleKey, "newRoleKey", newRoleKey)
            : Map.of("islandId", islandId.toString(), "playerUuid", playerUuid.toString(), "role", newRoleKey, "roleKey", newRoleKey));
        events.publish(CloudIslandEventType.ISLAND_MEMBER_CHANGED.name(), Map.of("islandId", islandId.toString(), "playerUuid", playerUuid.toString(), "role", newRoleKey, "roleKey", newRoleKey));
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

    static String memberRoleKey(List<IslandMemberSnapshot> members, UUID playerUuid) {
        return members.stream()
            .filter(member -> member.playerUuid().equals(playerUuid))
            .map(IslandMemberSnapshot::effectiveRoleKey)
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

    private boolean roleLimitReached(UUID islandId, List<IslandMemberSnapshot> members, String currentRoleKey, String newRoleKey) {
        if (newRoleKey.equals(currentRoleKey)) {
            return false;
        }
        long limit = limitValue(islandId, GameplayParityPolicy.roleLimitKey(newRoleKey), defaultRoleLimit(newRoleKey));
        if (limit == Long.MAX_VALUE) {
            return false;
        }
        long count = members.stream()
            .filter(member -> member.effectiveRoleKey().equals(newRoleKey))
            .count();
        return count >= limit;
    }

    private static long defaultRoleLimit(String roleKey) {
        return CoreRoleKeys.TRUSTED.equals(roleKey) ? 8L : Long.MAX_VALUE;
    }

    private static boolean addsTeamMember(String currentRoleKey, String newRoleKey) {
        return !teamMemberRole(currentRoleKey) && teamMemberRole(newRoleKey);
    }

    private static boolean teamMemberRole(String roleKey) {
        return CoreRoleKeys.teamMemberRole(roleKey);
    }

    private static long teamMemberCount(List<IslandMemberSnapshot> members) {
        return members.stream().filter(member -> teamMemberRole(member.effectiveRoleKey())).count();
    }

    private void publishCoopTransition(UUID islandId, UUID playerUuid, String oldRoleKey, String newRoleKey) {
        boolean wasCoop = CoreRoleKeys.TRUSTED.equals(oldRoleKey);
        boolean isCoop = CoreRoleKeys.TRUSTED.equals(newRoleKey);
        if (wasCoop == isCoop) {
            return;
        }
        events.publish(
            (isCoop ? CloudIslandEventType.ISLAND_COOP_ADDED : CloudIslandEventType.ISLAND_COOP_REMOVED).name(),
            Map.of("islandId", islandId.toString(), "playerUuid", playerUuid.toString(), "roleKey", CoreRoleKeys.TRUSTED)
        );
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

    private String playerIslandsJson(List<IslandMemberSnapshot> memberships) {
        List<Object> renderedIslands = new ArrayList<>();
        for (IslandMemberSnapshot membership : memberships) {
            islandRepository.findById(membership.islandId()).ifPresent(island -> {
                LinkedHashMap<String, Object> rendered = new LinkedHashMap<>(islandMap(island));
                rendered.put("role", membership.effectiveRoleKey());
                rendered.put("roleKey", membership.effectiveRoleKey());
                rendered.put("membershipExpiresAt", membership.expiresAt());
                renderedIslands.add(rendered);
            });
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

    private boolean isOwner(UUID islandId, UUID playerUuid) {
        return islandRepository.findById(islandId)
            .map(island -> island.ownerUuid().equals(playerUuid))
            .orElse(false);
    }

    private void clearPrimaryIslandIfSelected(UUID islandId, UUID playerUuid) {
        if (playerProfiles.find(playerUuid).primaryIslandId().filter(islandId::equals).isPresent()) {
            playerProfiles.clearPrimaryIsland(playerUuid);
        }
    }

    private void initializePrimaryIslandIfEmpty(UUID islandId, UUID playerUuid) {
        if (playerProfiles != null && playerProfiles.find(playerUuid).primaryIslandId().isEmpty()) {
            playerProfiles.setPrimaryIsland(playerUuid, islandId);
        }
    }

    private static String roleKey(String body, String fallback) {
        String value = JsonFields.text(body, "roleKey", "");
        if (value.isBlank()) {
            value = JsonFields.text(body, "role", fallback);
        }
        return IslandRoleRepository.normalizeRoleKey(value);
    }

    private static String memberActionJson(String code) {
        return SimpleJson.stringify(Map.of("accepted", true, "code", code));
    }

    private static String promotedRole(String roleKey) {
        return switch (IslandRoleRepository.normalizeRoleKey(roleKey)) {
            case "TRUSTED" -> "MEMBER";
            case "MEMBER" -> "MODERATOR";
            case "MODERATOR" -> "CO_OWNER";
            default -> "";
        };
    }

    private static String demotedRole(String roleKey) {
        return switch (IslandRoleRepository.normalizeRoleKey(roleKey)) {
            case "CO_OWNER" -> "MODERATOR";
            case "MODERATOR" -> "MEMBER";
            case "MEMBER" -> "TRUSTED";
            default -> "";
        };
    }
}
