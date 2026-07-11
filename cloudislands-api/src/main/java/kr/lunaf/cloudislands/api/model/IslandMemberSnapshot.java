package kr.lunaf.cloudislands.api.model;

import java.time.Instant;
import java.util.UUID;

@SuppressWarnings("deprecation")
public record IslandMemberSnapshot(UUID islandId, UUID playerUuid, IslandRole role, Instant joinedAt, Instant expiresAt, String roleKey) {
    public IslandMemberSnapshot {
        roleKey = LegacyRoleSupport.canonicalRoleKey(roleKey, role);
        role = LegacyRoleSupport.legacyRole(roleKey);
    }

    public IslandMemberSnapshot(UUID islandId, UUID playerUuid, IslandRole role, Instant joinedAt) {
        this(islandId, playerUuid, role, joinedAt, null);
    }

    public IslandMemberSnapshot(UUID islandId, UUID playerUuid, IslandRole role, Instant joinedAt, Instant expiresAt) {
        this(islandId, playerUuid, role, joinedAt, expiresAt, RoleId.of(role, IslandRole.VISITOR.name()).value());
    }

    public IslandMemberSnapshot(UUID islandId, UUID playerUuid, String roleKey, Instant joinedAt, Instant expiresAt) {
        this(islandId, playerUuid, null, joinedAt, expiresAt, RoleId.normalize(roleKey, IslandRole.VISITOR.name()));
    }

    public String effectiveRoleKey() {
        return roleKey;
    }

    public RoleId roleId() {
        return RoleId.of(roleKey);
    }

    public SystemRole systemRole() {
        return roleId().asSystemRole();
    }
}
