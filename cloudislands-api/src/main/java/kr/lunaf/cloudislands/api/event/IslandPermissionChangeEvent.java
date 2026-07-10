package kr.lunaf.cloudislands.api.event;

import java.time.Instant;
import java.util.UUID;
import kr.lunaf.cloudislands.api.model.IslandPermission;
import kr.lunaf.cloudislands.api.model.IslandRole;
import kr.lunaf.cloudislands.api.model.RoleId;

@SuppressWarnings("deprecation")
public record IslandPermissionChangeEvent(UUID islandId, IslandRole role, IslandPermission permission, Boolean allowed, Instant occurredAt, String roleKey) implements CloudIslandEvent {
    public IslandPermissionChangeEvent {
        roleKey = EventRoleSupport.canonicalRoleKey(roleKey, role, "MEMBER");
        role = EventRoleSupport.legacyRole(roleKey);
    }

    public IslandPermissionChangeEvent(UUID islandId, IslandRole role, IslandPermission permission, Boolean allowed, Instant occurredAt) {
        this(islandId, role, permission, allowed, occurredAt, "");
    }

    public IslandPermissionChangeEvent(UUID islandId, String roleKey, IslandPermission permission, Boolean allowed, Instant occurredAt) {
        this(islandId, null, permission, allowed, occurredAt, roleKey);
    }

    public RoleId roleId() {
        return RoleId.of(roleKey);
    }
}
