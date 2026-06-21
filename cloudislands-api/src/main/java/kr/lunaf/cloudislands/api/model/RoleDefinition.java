package kr.lunaf.cloudislands.api.model;

import java.util.List;

public record RoleDefinition(
    RoleId roleId,
    int weight,
    String displayName,
    SystemRole systemRole,
    boolean memberRole,
    boolean editable
) {
    private static final List<RoleDefinition> DEFAULT_MEMBER_ROLES = List.of(
        member("CO_OWNER", 1, "CO_OWNER"),
        member("MODERATOR", 2, "MODERATOR"),
        member("MEMBER", 3, "MEMBER"),
        member("TRUSTED", 4, "TRUSTED")
    );

    public RoleDefinition {
        if (roleId == null) {
            throw new IllegalArgumentException("roleId is required");
        }
        displayName = displayName == null || displayName.isBlank() ? roleId.value() : displayName.trim();
        systemRole = systemRole == null ? roleId.asSystemRole() : systemRole;
    }

    public static RoleDefinition member(String roleKey, int weight, String displayName) {
        RoleId roleId = RoleId.of(roleKey);
        return new RoleDefinition(roleId, weight, displayName, roleId.asSystemRole(), true, editable(roleId));
    }

    public static List<RoleDefinition> defaultMemberRoles() {
        return DEFAULT_MEMBER_ROLES;
    }

    public static boolean editable(RoleId roleId) {
        return roleId != null && !roleId.systemRole();
    }
}
