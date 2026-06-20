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
        roleKey = RoleId.normalize(roleKey, role == null ? IslandRole.MEMBER.name() : role.name());
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

    private static IslandRole parseRole(String roleKey) {
        try {
            return IslandRole.valueOf(RoleId.normalize(roleKey, IslandRole.MEMBER.name()));
        } catch (IllegalArgumentException exception) {
            return null;
        }
    }
}
