package kr.lunaf.cloudislands.api.model;

import java.util.UUID;

public record IslandRoleSnapshot(
    UUID islandId,
    IslandRole role,
    int weight,
    String displayName,
    String roleKey
) {
    public IslandRoleSnapshot(UUID islandId, IslandRole role, int weight, String displayName) {
        this(islandId, role, weight, displayName, role == null ? "" : role.name());
    }

    public IslandRoleSnapshot(UUID islandId, String roleKey, int weight, String displayName) {
        this(islandId, parseRole(roleKey), weight, displayName, normalizedRoleKey(roleKey));
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
