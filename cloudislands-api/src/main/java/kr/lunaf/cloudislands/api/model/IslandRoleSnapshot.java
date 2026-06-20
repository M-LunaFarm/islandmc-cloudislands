package kr.lunaf.cloudislands.api.model;

import java.util.UUID;

public record IslandRoleSnapshot(
    UUID islandId,
    IslandRole role,
    int weight,
    String displayName,
    String roleKey
) {
    public IslandRoleSnapshot {
        roleKey = role == null ? RoleId.of(roleKey).value() : RoleId.of(roleKey, role.name()).value();
        if (role == null) {
            role = parseRole(roleKey);
        }
    }

    public IslandRoleSnapshot(UUID islandId, IslandRole role, int weight, String displayName) {
        this(islandId, role, weight, displayName, RoleId.of(role, IslandRole.MEMBER.name()).value());
    }

    public IslandRoleSnapshot(UUID islandId, String roleKey, int weight, String displayName) {
        this(islandId, parseRole(roleKey), weight, displayName, RoleId.normalize(roleKey, IslandRole.MEMBER.name()));
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
            return IslandRole.valueOf(RoleId.normalize(roleKey, IslandRole.MEMBER.name()));
        } catch (IllegalArgumentException exception) {
            return null;
        }
    }
}
