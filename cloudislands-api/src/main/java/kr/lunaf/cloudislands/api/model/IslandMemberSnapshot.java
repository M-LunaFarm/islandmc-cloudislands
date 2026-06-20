package kr.lunaf.cloudislands.api.model;

import java.time.Instant;
import java.util.UUID;

public record IslandMemberSnapshot(UUID islandId, UUID playerUuid, IslandRole role, Instant joinedAt, Instant expiresAt, String roleKey) {
    public IslandMemberSnapshot(UUID islandId, UUID playerUuid, IslandRole role, Instant joinedAt) {
        this(islandId, playerUuid, role, joinedAt, null);
    }

    public IslandMemberSnapshot(UUID islandId, UUID playerUuid, IslandRole role, Instant joinedAt, Instant expiresAt) {
        this(islandId, playerUuid, role, joinedAt, expiresAt, role == null ? "" : role.name());
    }

    public IslandMemberSnapshot(UUID islandId, UUID playerUuid, String roleKey, Instant joinedAt, Instant expiresAt) {
        this(islandId, playerUuid, parseRole(roleKey), joinedAt, expiresAt, normalizedRoleKey(roleKey));
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
