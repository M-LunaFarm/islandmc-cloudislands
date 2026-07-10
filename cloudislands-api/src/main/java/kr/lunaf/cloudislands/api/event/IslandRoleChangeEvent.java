package kr.lunaf.cloudislands.api.event;

import java.time.Instant;
import java.util.UUID;
import kr.lunaf.cloudislands.api.model.IslandRole;
import kr.lunaf.cloudislands.api.model.RoleId;

@SuppressWarnings("deprecation")
public record IslandRoleChangeEvent(UUID islandId, UUID playerUuid, IslandRole oldRole, IslandRole newRole, Instant occurredAt, String oldRoleKey, String newRoleKey) implements CloudIslandEvent {
    public IslandRoleChangeEvent {
        oldRoleKey = EventRoleSupport.canonicalRoleKey(oldRoleKey, oldRole, "VISITOR");
        newRoleKey = EventRoleSupport.canonicalRoleKey(newRoleKey, newRole, "MEMBER");
        oldRole = EventRoleSupport.legacyRole(oldRoleKey);
        newRole = EventRoleSupport.legacyRole(newRoleKey);
    }

    public IslandRoleChangeEvent(UUID islandId, UUID playerUuid, IslandRole oldRole, IslandRole newRole, Instant occurredAt) {
        this(islandId, playerUuid, oldRole, newRole, occurredAt, "", "");
    }

    public IslandRoleChangeEvent(UUID islandId, UUID playerUuid, String oldRoleKey, String newRoleKey, Instant occurredAt) {
        this(islandId, playerUuid, null, null, occurredAt, oldRoleKey, newRoleKey);
    }

    public RoleId oldRoleId() {
        return RoleId.of(oldRoleKey);
    }

    public RoleId newRoleId() {
        return RoleId.of(newRoleKey);
    }
}
