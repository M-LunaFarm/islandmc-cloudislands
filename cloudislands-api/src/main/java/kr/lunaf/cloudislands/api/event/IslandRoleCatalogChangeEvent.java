package kr.lunaf.cloudislands.api.event;

import java.time.Instant;
import java.util.UUID;
import kr.lunaf.cloudislands.api.model.IslandRole;
import kr.lunaf.cloudislands.api.model.RoleId;

@SuppressWarnings("deprecation")
public record IslandRoleCatalogChangeEvent(UUID islandId, IslandRole role, String operation, Instant occurredAt, String roleKey) implements CloudIslandEvent {
    public IslandRoleCatalogChangeEvent {
        roleKey = EventRoleSupport.canonicalRoleKey(roleKey, role, "MEMBER");
        role = EventRoleSupport.legacyRole(roleKey);
    }

    public IslandRoleCatalogChangeEvent(UUID islandId, IslandRole role, String operation, Instant occurredAt) {
        this(islandId, role, operation, occurredAt, "");
    }

    public IslandRoleCatalogChangeEvent(UUID islandId, String roleKey, String operation, Instant occurredAt) {
        this(islandId, null, operation, occurredAt, roleKey);
    }

    public RoleId roleId() {
        return RoleId.of(roleKey);
    }
}
