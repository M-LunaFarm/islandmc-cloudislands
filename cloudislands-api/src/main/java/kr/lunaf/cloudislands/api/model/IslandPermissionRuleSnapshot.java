package kr.lunaf.cloudislands.api.model;

import java.util.UUID;

public record IslandPermissionRuleSnapshot(
    UUID islandId,
    IslandRole role,
    IslandPermission permission,
    boolean allowed,
    String roleKey
) {
    public IslandPermissionRuleSnapshot {
        roleKey = RoleId.normalize(roleKey, role == null ? IslandRole.MEMBER.name() : role.name());
        if (role == null) {
            role = parseRole(roleKey);
        }
    }

    public IslandPermissionRuleSnapshot(UUID islandId, IslandRole role, IslandPermission permission, boolean allowed) {
        this(islandId, role, permission, allowed, RoleId.of(role, IslandRole.MEMBER.name()).value());
    }

    public IslandPermissionRuleSnapshot(UUID islandId, String roleKey, IslandPermission permission, boolean allowed) {
        this(islandId, parseRole(roleKey), permission, allowed, RoleId.normalize(roleKey, IslandRole.MEMBER.name()));
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
