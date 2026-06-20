package kr.lunaf.cloudislands.api.model;

import java.util.UUID;

public record IslandPermissionRuleSnapshot(
    UUID islandId,
    IslandRole role,
    IslandPermission permission,
    boolean allowed,
    String roleKey
) {
    public IslandPermissionRuleSnapshot(UUID islandId, IslandRole role, IslandPermission permission, boolean allowed) {
        this(islandId, role, permission, allowed, role == null ? "" : role.name());
    }

    public IslandPermissionRuleSnapshot(UUID islandId, String roleKey, IslandPermission permission, boolean allowed) {
        this(islandId, parseRole(roleKey), permission, allowed, normalizedRoleKey(roleKey));
    }

    public String effectiveRoleKey() {
        return roleKey == null || roleKey.isBlank() ? role.name() : roleKey;
    }

    private static IslandRole parseRole(String roleKey) {
        try {
            return IslandRole.valueOf(normalizedRoleKey(roleKey));
        } catch (IllegalArgumentException exception) {
            return null;
        }
    }

    private static String normalizedRoleKey(String roleKey) {
        return roleKey == null ? "" : roleKey.trim().toUpperCase(java.util.Locale.ROOT).replace('-', '_');
    }
}
