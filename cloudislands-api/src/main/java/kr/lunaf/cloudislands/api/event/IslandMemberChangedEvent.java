package kr.lunaf.cloudislands.api.event;

import java.time.Instant;
import java.util.UUID;
import kr.lunaf.cloudislands.api.model.IslandRole;
import kr.lunaf.cloudislands.api.model.RoleId;

@SuppressWarnings("deprecation")
public record IslandMemberChangedEvent(UUID islandId, UUID playerUuid, String action, IslandRole oldRole, IslandRole newRole, Instant occurredAt, String oldRoleKey, String newRoleKey) implements CloudIslandEvent {
    public IslandMemberChangedEvent {
        oldRoleKey = EventRoleSupport.canonicalRoleKey(oldRoleKey, oldRole, "VISITOR");
        newRoleKey = EventRoleSupport.canonicalRoleKey(newRoleKey, newRole, "MEMBER");
        oldRole = EventRoleSupport.legacyRole(oldRoleKey);
        newRole = EventRoleSupport.legacyRole(newRoleKey);
    }

    public IslandMemberChangedEvent(UUID islandId, UUID playerUuid, String action, IslandRole oldRole, IslandRole newRole, Instant occurredAt) {
        this(islandId, playerUuid, action, oldRole, newRole, occurredAt, "", "");
    }

    public IslandMemberChangedEvent(UUID islandId, UUID playerUuid, String action, String oldRoleKey, String newRoleKey, Instant occurredAt) {
        this(islandId, playerUuid, action, null, null, occurredAt, oldRoleKey, newRoleKey);
    }

    public RoleId oldRoleId() {
        return RoleId.of(oldRoleKey);
    }

    public RoleId newRoleId() {
        return RoleId.of(newRoleKey);
    }
}
