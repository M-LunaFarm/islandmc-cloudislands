package kr.lunaf.cloudislands.api.model;

import java.util.UUID;

@SuppressWarnings("deprecation")
public record IslandPermissionRuleSnapshot(
    UUID islandId,
    IslandRole role,
    IslandPermission permission,
    boolean allowed,
    String roleKey
) {
    public IslandPermissionRuleSnapshot {
        roleKey = LegacyRoleSupport.canonicalRoleKey(roleKey, role);
        role = LegacyRoleSupport.legacyRole(roleKey);
    }

    public IslandPermissionRuleSnapshot(UUID islandId, IslandRole role, IslandPermission permission, boolean allowed) {
        this(islandId, role, permission, allowed, RoleId.of(role, IslandRole.MEMBER.name()).value());
    }

    public IslandPermissionRuleSnapshot(UUID islandId, String roleKey, IslandPermission permission, boolean allowed) {
        this(islandId, null, permission, allowed, RoleId.normalize(roleKey, IslandRole.MEMBER.name()));
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
