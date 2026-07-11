package kr.lunaf.cloudislands.api.model;

@SuppressWarnings("deprecation")
public record PermissionResult(boolean allowed, String reason, IslandRole effectiveRole, RoleId effectiveRoleId) {
    public PermissionResult {
        reason = reason == null || reason.isBlank() ? (allowed ? "ALLOW" : "DENY") : reason;
        effectiveRoleId = LegacyRoleSupport.canonicalRoleId(effectiveRoleId, effectiveRole, "VISITOR");
        effectiveRole = LegacyRoleSupport.legacyRole(effectiveRoleId.value());
    }

    public PermissionResult(boolean allowed, String reason, IslandRole effectiveRole) {
        this(allowed, reason, effectiveRole, RoleId.of(effectiveRole, IslandRole.VISITOR.name()));
    }

    public static PermissionResult allow(IslandRole role) {
        return allow(RoleId.of(role, IslandRole.VISITOR.name()));
    }

    public static PermissionResult allow(RoleId roleId) {
        return new PermissionResult(true, "ALLOW", null, roleId);
    }

    public static PermissionResult deny(String reason, IslandRole role) {
        return deny(reason, RoleId.of(role, IslandRole.VISITOR.name()));
    }

    public static PermissionResult deny(String reason, RoleId roleId) {
        return new PermissionResult(false, reason, null, roleId);
    }
}
