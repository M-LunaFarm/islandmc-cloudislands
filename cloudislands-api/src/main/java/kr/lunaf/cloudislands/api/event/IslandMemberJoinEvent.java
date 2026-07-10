package kr.lunaf.cloudislands.api.event;

import java.time.Instant;
import java.util.UUID;
import kr.lunaf.cloudislands.api.model.IslandRole;
import kr.lunaf.cloudislands.api.model.RoleId;

@SuppressWarnings("deprecation")
public record IslandMemberJoinEvent(UUID islandId, UUID playerUuid, IslandRole role, Instant occurredAt, String roleKey) implements CloudIslandEvent {
    public IslandMemberJoinEvent {
        roleKey = EventRoleSupport.canonicalRoleKey(roleKey, role, "MEMBER");
        role = EventRoleSupport.legacyRole(roleKey);
    }

    public IslandMemberJoinEvent(UUID islandId, UUID playerUuid, IslandRole role, Instant occurredAt) {
        this(islandId, playerUuid, role, occurredAt, "");
    }

    public IslandMemberJoinEvent(UUID islandId, UUID playerUuid, String roleKey, Instant occurredAt) {
        this(islandId, playerUuid, null, occurredAt, roleKey);
    }

    public RoleId roleId() {
        return RoleId.of(roleKey);
    }
}
