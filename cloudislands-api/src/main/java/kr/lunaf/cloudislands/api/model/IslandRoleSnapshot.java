package kr.lunaf.cloudislands.api.model;

import java.util.UUID;

@SuppressWarnings("deprecation")
public record IslandRoleSnapshot(
    UUID islandId,
    IslandRole role,
    int weight,
    String displayName,
    String roleKey
) {
    public IslandRoleSnapshot {
        roleKey = LegacyRoleSupport.canonicalRoleKey(roleKey, role);
        role = LegacyRoleSupport.legacyRole(roleKey);
    }

    public IslandRoleSnapshot(UUID islandId, IslandRole role, int weight, String displayName) {
        this(islandId, role, weight, displayName, RoleId.of(role, IslandRole.MEMBER.name()).value());
    }

    public IslandRoleSnapshot(UUID islandId, String roleKey, int weight, String displayName) {
        this(islandId, null, weight, displayName, RoleId.normalize(roleKey, IslandRole.MEMBER.name()));
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
