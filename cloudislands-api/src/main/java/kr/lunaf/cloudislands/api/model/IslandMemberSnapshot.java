package kr.lunaf.cloudislands.api.model;

import java.time.Instant;
import java.util.UUID;

public record IslandMemberSnapshot(UUID islandId, UUID playerUuid, IslandRole role, Instant joinedAt, Instant expiresAt, String roleKey) {
    public IslandMemberSnapshot {
        roleKey = RoleId.normalize(roleKey, role == null ? IslandRole.VISITOR.name() : role.name());
        if (role == null) {
            role = parseRole(roleKey);
        }
    }

    public IslandMemberSnapshot(UUID islandId, UUID playerUuid, IslandRole role, Instant joinedAt) {
        this(islandId, playerUuid, role, joinedAt, null);
    }

    public IslandMemberSnapshot(UUID islandId, UUID playerUuid, IslandRole role, Instant joinedAt, Instant expiresAt) {
        this(islandId, playerUuid, role, joinedAt, expiresAt, RoleId.of(role, IslandRole.VISITOR.name()).value());
    }

    public IslandMemberSnapshot(UUID islandId, UUID playerUuid, String roleKey, Instant joinedAt, Instant expiresAt) {
        this(islandId, playerUuid, parseRole(roleKey), joinedAt, expiresAt, RoleId.normalize(roleKey, IslandRole.VISITOR.name()));
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

    private static IslandRole parseRole(String roleKey) {
        try {
            return IslandRole.valueOf(RoleId.normalize(roleKey, IslandRole.VISITOR.name()));
        } catch (IllegalArgumentException exception) {
            return null;
        }
    }
}
